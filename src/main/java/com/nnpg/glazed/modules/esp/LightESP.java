package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LightESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgThreading = settings.createGroup("Threading");

    private final Setting<Integer> chunkRadius = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-radius")
        .description("Radius of chunks to scan around the player.")
        .defaultValue(2)
        .min(1)
        .max(8)
        .sliderMax(8)
        .build()
    );

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level to scan.")
        .defaultValue(-63)
        .min(-64)
        .max(319)
        .sliderMin(-64)
        .sliderMax(319)
        .build()
    );

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y")
        .description("Maximum Y level to scan.")
        .defaultValue(0)
        .min(-64)
        .max(319)
        .sliderMin(-64)
        .sliderMax(319)
        .build()
    );

    private final Setting<Integer> minLightLevel = sgGeneral.add(new IntSetting.Builder()
        .name("min-light-level")
        .description("Minimum light level to display.")
        .defaultValue(5)
        .min(0)
        .max(15)
        .sliderMax(15)
        .build()
    );

    private final Setting<Integer> maxLightLevel = sgGeneral.add(new IntSetting.Builder()
        .name("max-light-level")
        .description("Maximum light level to display.")
        .defaultValue(15)
        .min(0)
        .max(15)
        .sliderMax(15)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> useThreading = sgThreading.add(new BoolSetting.Builder()
        .name("enable-threading")
        .description("Use multi-threading for chunk scanning (better performance)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> threadPoolSize = sgThreading.add(new IntSetting.Builder()
        .name("thread-pool-size")
        .description("Number of threads to use for scanning")
        .defaultValue(2)
        .min(1)
        .max(8)
        .sliderRange(1, 8)
        .visible(useThreading::get)
        .build()
    );

    private final Set<BlockPos> lightPositions = ConcurrentHashMap.newKeySet();
    private ExecutorService threadPool;
    private int lastMinLightLevel = -1;
    private int lastMaxLightLevel = -1;
    private int lastChunkRadius = -1;
    private int lastMinY = -1;
    private int lastMaxY = -1;
    private int refreshCounter = 0;
    private static final int REFRESH_RATE = 10; // 10 ticks = ~0.5 seconds at 20 TPS = 2 times per second

    public LightESP() {
        super(GlazedAddon.esp, "LightESP", "Highlights blocks with light levels above a threshold.");
    }

    @Override
    public void onActivate() {
        if (useThreading.get()) {
            threadPool = Executors.newFixedThreadPool(threadPoolSize.get());
        }
        lightPositions.clear();
    }

    @Override
    public void onDeactivate() {
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
            threadPool = null;
        }
        lightPositions.clear();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        refreshCounter++;

        // Check if settings have changed and refresh if needed
        if (lastMinLightLevel != minLightLevel.get() ||
            lastMaxLightLevel != maxLightLevel.get() ||
            lastChunkRadius != chunkRadius.get() ||
            lastMinY != minY.get() ||
            lastMaxY != maxY.get()) {
            lightPositions.clear();
            lastMinLightLevel = minLightLevel.get();
            lastMaxLightLevel = maxLightLevel.get();
            lastChunkRadius = chunkRadius.get();
            lastMinY = minY.get();
            lastMaxY = maxY.get();
            refreshCounter = 0;
        }

        // Only refresh every REFRESH_RATE ticks
        if (refreshCounter >= REFRESH_RATE) {
            refreshCounter = 0;

            ChunkPos playerChunkPos = mc.player.getChunkPos();
            int radius = chunkRadius.get();

            // Clear and rescan all chunks to detect new lights
            lightPositions.clear();

            for (int chunkX = playerChunkPos.x - radius; chunkX <= playerChunkPos.x + radius; chunkX++) {
                for (int chunkZ = playerChunkPos.z - radius; chunkZ <= playerChunkPos.z + radius; chunkZ++) {
                    final int finalChunkX = chunkX;
                    final int finalChunkZ = chunkZ;

                    if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
                        threadPool.submit(() -> scanChunkForLights(finalChunkX, finalChunkZ));
                    } else {
                        scanChunkForLights(chunkX, chunkZ);
                    }
                }
            }
        }

        renderLights(event);
    }

    private void scanChunkForLights(int chunkX, int chunkZ) {
        if (mc.world == null) return;

        Chunk chunk = mc.world.getChunk(chunkX, chunkZ);
        if (chunk == null || !chunk.getStatus().isAtLeast(ChunkStatus.FULL)) return;

        Set<BlockPos> chunkLights = new HashSet<>();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY.get(); y <= maxY.get(); y++) {
                    BlockPos pos = new BlockPos(chunkX * 16 + x, y, chunkZ * 16 + z);

                    int blockLight = mc.world.getLightLevel(LightType.BLOCK, pos);
                    int skyLight = mc.world.getLightLevel(LightType.SKY, pos);

                    if (blockLight >= minLightLevel.get() && blockLight <= maxLightLevel.get() && blockLight > skyLight) {
                        chunkLights.add(pos);
                    }
                }
            }
        }

        lightPositions.addAll(chunkLights);
    }

    private void renderLights(Render3DEvent event) {
        Set<BlockPos> allLightPositions = new HashSet<>(lightPositions);

        for (BlockPos pos : allLightPositions) {
            if (mc.world == null) return;

            int lightLevel = mc.world.getLightLevel(LightType.BLOCK, pos);

            // Filter by current min/max light level settings and check if should render
            if ((lightLevel < minLightLevel.get() || lightLevel > maxLightLevel.get()) ||
                (lightLevel < 15 && !shouldRenderBlock(pos, allLightPositions))) continue;

            float[] thermal = getThermalColor(lightLevel);
            SettingColor sColor = new SettingColor((int)(thermal[0] * 255), (int)(thermal[1] * 255),
                                      (int)(thermal[2] * 255), (int)(thermal[3] * 255));
            SettingColor lColor = new SettingColor((int)(thermal[0] * 255), (int)(thermal[1] * 255),
                                      (int)(thermal[2] * 255), 255);

            event.renderer.box(pos, sColor, lColor, shapeMode.get(), 0);
        }
    }

    private boolean shouldRenderBlock(BlockPos pos, Set<BlockPos> allLightPositions) {
        return !allLightPositions.contains(pos.down()) ||
               !allLightPositions.contains(pos.up()) ||
               !allLightPositions.contains(pos.north()) ||
               !allLightPositions.contains(pos.south()) ||
               !allLightPositions.contains(pos.west()) ||
               !allLightPositions.contains(pos.east());
    }

    private float[] getThermalColor(int lightLevel) {
        float[] color = new float[4];

        if (lightLevel <= 5) {
            color[3] = 0.25f + (lightLevel / 5.0f) * 0.25f;
        } else if (lightLevel <= 10) {
            color[3] = 0.3f + ((lightLevel - 5) / 5.0f) * 0.3f;
        } else if (lightLevel <= 14) {
            color[3] = 0.5f + ((lightLevel - 10) / 4.0f) * 0.35f;
        } else {
            color[3] = 1.0f;
        }

        if (lightLevel <= 5) {
            float intensity = lightLevel / 5.0f;
            color[0] = intensity * 0.4f;
            color[1] = intensity * 0.4f;
            color[2] = intensity * 0.4f;
        } else if (lightLevel <= 10) {
            float intensity = (lightLevel - 5) / 5.0f;
            color[0] = 0.7f + intensity * 0.3f;
            color[1] = intensity * 0.6f;
            color[2] = intensity * 0.1f;
        } else if (lightLevel <= 14) {
            float intensity = (lightLevel - 10) / 4.0f;
            color[0] = 1.0f;
            color[1] = 0.7f + intensity * 0.25f;
            color[2] = 0.2f + intensity * 0.3f;
        } else {
            color[0] = 1.0f;
            color[1] = 1.0f;
            color[2] = 1.0f;
        }

        return color;
    }
}
