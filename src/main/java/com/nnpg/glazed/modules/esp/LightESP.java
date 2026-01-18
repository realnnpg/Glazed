package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LightESP extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Filters");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> chunkRadius = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-radius")
        .description("Radius of chunks to scan around the player.")
        .defaultValue(4)
        .min(1)
        .max(16)
        .sliderMax(16)
        .build()
    );

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level to scan.")
        .defaultValue(-64)
        .min(-64)
        .max(319)
        .sliderMin(-64)
        .sliderMax(319)
        .build()
    );

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y")
        .description("Maximum Y level to scan.")
        .defaultValue(100)
        .min(-64)
        .max(319)
        .sliderMin(-64)
        .sliderMax(319)
        .build()
    );

    private final Setting<Integer> minLightLevel = sgGeneral.add(new IntSetting.Builder()
        .name("min-light-level")
        .description("Minimum light level to display.")
        .defaultValue(8)
        .min(0)
        .max(15)
        .sliderMax(15)
        .build()
    );

    private final Setting<Boolean> onlySourceBlocks = sgFilter.add(new BoolSetting.Builder()
        .name("only-source-blocks")
        .description("Only show actual light emitting blocks not propagated light thanks claude.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> filterNaturalLight = sgFilter.add(new BoolSetting.Builder()
        .name("filter-natural-light")
        .description("Ignore natural light sources (lava magma etc.).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showTorches = sgFilter.add(new BoolSetting.Builder()
        .name("show-torches")
        .description("torches and lanterns.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showGlowstone = sgFilter.add(new BoolSetting.Builder()
        .name("show-glowstone")
        .description("Show glowstone and sea lanterns.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showRedstone = sgFilter.add(new BoolSetting.Builder()
        .name("show-redstone")
        .description("Show redstone lamps and powered blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showBeacons = sgFilter.add(new BoolSetting.Builder()
        .name("show-beacons")
        .description("Show beacons and conduits.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> distanceLimit = sgGeneral.add(new BoolSetting.Builder()
        .name("distance-limit")
        .description("Enable distance-based rendering limit.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxDistance = sgGeneral.add(new IntSetting.Builder()
        .name("max-distance")
        .description("Maximum distance to render lights.")
        .defaultValue(128)
        .min(16)
        .max(512)
        .sliderMax(512)
        .visible(distanceLimit::get)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<Boolean> thermalColors = sgRender.add(new BoolSetting.Builder()
        .name("thermal-colors")
        .description("Use thermal-style colors based on light level.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Side color (used when thermal colors are off).")
        .defaultValue(new SettingColor(255, 255, 0, 75))
        .visible(() -> !thermalColors.get())
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Line color (used when thermal colors are off).")
        .defaultValue(new SettingColor(255, 255, 0, 255))
        .visible(() -> !thermalColors.get())
        .build()
    );

    private final Map<BlockPos, Integer> lightCache = new ConcurrentHashMap<>();
    private long lastScanTime = 0;
    private static final long SCAN_INTERVAL = 500; // Scan every 500ms

    public LightESP() {
        super(GlazedAddon.esp, "LightESP", "Improved light source detection");
    }

    @Override
    public void onActivate() {
        lightCache.clear();
        lastScanTime = 0;
    }

    @Override
    public void onDeactivate() {
        lightCache.clear();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScanTime >= SCAN_INTERVAL) {
            scanForLights();
            lastScanTime = currentTime;
        }

        renderCachedLights(event);
    }

    private void scanForLights() {
        if (mc.world == null || mc.player == null) return;

        ChunkPos playerChunkPos = mc.player.getChunkPos();
        Map<BlockPos, Integer> newCache = new HashMap<>();
        int radius = chunkRadius.get();

        for (int chunkX = playerChunkPos.x - radius; chunkX <= playerChunkPos.x + radius; chunkX++) {
            for (int chunkZ = playerChunkPos.z - radius; chunkZ <= playerChunkPos.z + radius; chunkZ++) {
                Chunk chunk = mc.world.getChunk(chunkX, chunkZ);
                if (chunk != null && chunk.getStatus().isAtLeast(ChunkStatus.FULL)) {
                    scanChunk(chunkX, chunkZ, newCache);
                }
            }
        }

        lightCache.clear();
        lightCache.putAll(newCache);
    }

    private void scanChunk(int chunkX, int chunkZ, Map<BlockPos, Integer> cache) {
        int startX = chunkX * 16;
        int startZ = chunkZ * 16;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY.get(); y <= maxY.get(); y++) {
                    BlockPos pos = new BlockPos(startX + x, y, startZ + z);

                    // Distance check
                    if (distanceLimit.get() && mc.player.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) > maxDistance.get() * maxDistance.get()) {
                        continue;
                    }

                    int blockLight = mc.world.getLightLevel(LightType.BLOCK, pos);
                    int skyLight = mc.world.getLightLevel(LightType.SKY, pos);

                    // Must have sufficient block light and more block light than sky light
                    if (blockLight >= minLightLevel.get() && blockLight > skyLight) {
                        BlockState state = mc.world.getBlockState(pos);
                        Block block = state.getBlock();

                        // If only source blocks, check if this is actually emitting light
                        if (onlySourceBlocks.get()) {
                            if (!isLightSourceBlock(block, state)) {
                                continue;
                            }
                        }

                        // Apply filters
                        if (!passesFilters(block)) {
                            continue;
                        }

                        cache.put(pos, blockLight);
                    }
                }
            }
        }
    }

    private boolean isLightSourceBlock(Block block, BlockState state) {
        // Get the luminance directly from the block
        int luminance = state.getLuminance();
        return luminance > 0;
    }

    private boolean passesFilters(Block block) {
        // Natural light sources
        if (filterNaturalLight.get()) {
            if (block == Blocks.LAVA || 
                block == Blocks.MAGMA_BLOCK || 
                block == Blocks.FIRE ||
                block == Blocks.SOUL_FIRE) {
                return false;
            }
        }

        // Torches and lanterns
        if (!showTorches.get()) {
            if (block == Blocks.TORCH ||
                block == Blocks.WALL_TORCH ||
                block == Blocks.SOUL_TORCH ||
                block == Blocks.SOUL_WALL_TORCH ||
                block == Blocks.LANTERN ||
                block == Blocks.SOUL_LANTERN) {
                return false;
            }
        }

        // Glowstone and sea lanterns
        if (!showGlowstone.get()) {
            if (block == Blocks.GLOWSTONE ||
                block == Blocks.SEA_LANTERN ||
                block == Blocks.SHROOMLIGHT) {
                return false;
            }
        }

        // Redstone lamps
        if (!showRedstone.get()) {
            if (block == Blocks.REDSTONE_LAMP ||
                block == Blocks.REDSTONE_TORCH ||
                block == Blocks.REDSTONE_WALL_TORCH) {
                return false;
            }
        }

        // Beacons and conduits
        if (!showBeacons.get()) {
            if (block == Blocks.BEACON ||
                block == Blocks.CONDUIT) {
                return false;
            }
        }

        return true;
    }

    private void renderCachedLights(Render3DEvent event) {
        for (Map.Entry<BlockPos, Integer> entry : lightCache.entrySet()) {
            BlockPos pos = entry.getKey();
            int lightLevel = entry.getValue();

            SettingColor sColor, lColor;
            if (thermalColors.get()) {
                float[] thermal = getThermalColor(lightLevel);
                sColor = new SettingColor(
                    (int)(thermal[0] * 255), 
                    (int)(thermal[1] * 255),
                    (int)(thermal[2] * 255), 
                    (int)(thermal[3] * 255)
                );
                lColor = new SettingColor(
                    (int)(thermal[0] * 255), 
                    (int)(thermal[1] * 255),
                    (int)(thermal[2] * 255), 
                    255
                );
            } else {
                sColor = sideColor.get();
                lColor = lineColor.get();
            }

            event.renderer.box(pos, sColor, lColor, shapeMode.get(), 0);
        }
    }

    private float[] getThermalColor(int lightLevel) {
        float[] color = new float[4];

        // Progressive alpha based on light level
        float normalized = lightLevel / 15.0f;
        color[3] = 0.3f + (normalized * 0.7f);

        // Color gradient: Dark blue -> Yellow -> Orange -> Red -> White
        if (lightLevel <= 5) {
            // Dark blue to blue
            float t = lightLevel / 5.0f;
            color[0] = 0.0f + t * 0.2f;
            color[1] = 0.0f + t * 0.4f;
            color[2] = 0.4f + t * 0.6f;
        } else if (lightLevel <= 9) {
            // Blue to yellow
            float t = (lightLevel - 5) / 4.0f;
            color[0] = 0.2f + t * 0.8f;
            color[1] = 0.4f + t * 0.6f;
            color[2] = 1.0f - t * 0.8f;
        } else if (lightLevel <= 12) {
            // Yellow to orange/red
            float t = (lightLevel - 9) / 3.0f;
            color[0] = 1.0f;
            color[1] = 1.0f - t * 0.5f;
            color[2] = 0.2f - t * 0.2f;
        } else {
            // Bright red to white (level 15 = pure white)
            float t = (lightLevel - 12) / 3.0f;
            color[0] = 1.0f;
            color[1] = 0.5f + t * 0.5f;
            color[2] = 0.0f + t * 1.0f;
            color[3] = 0.8f + t * 0.2f; // Max alpha for brightest
        }

        return color;
    }
}