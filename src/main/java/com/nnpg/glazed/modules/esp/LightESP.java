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

public class LightESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgOptimization = settings.createGroup("Optimization");

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

    private final Setting<Boolean> enableOptimization = sgOptimization.add(new BoolSetting.Builder()
        .name("enable-optimization")
        .description("Remove weak light sources (level 6-7) that are likely propagated light.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> optimizationInterval = sgOptimization.add(new IntSetting.Builder()
        .name("optimization-interval")
        .description("How often to run optimization (in milliseconds).")
        .defaultValue(1000)
        .min(100)
        .max(5000)
        .sliderMax(5000)
        .visible(enableOptimization::get)
        .build()
    );

    private final Set<BlockPos> blocksToSkip = ConcurrentHashMap.newKeySet();
    private long lastOptimizationTime = 0L;

    public LightESP() {
        super(GlazedAddon.esp, "LightESP", "Highlights blocks with light levels above a threshold.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        long currentTime = System.currentTimeMillis();
        if (enableOptimization.get() && currentTime - lastOptimizationTime >= optimizationInterval.get()) {
            optimizeWeakLights();
            lastOptimizationTime = currentTime;
        }

        ChunkPos playerChunkPos = mc.player.getChunkPos();
        List<BlockPos> lightsToRender = new ArrayList<>();

        int radius = chunkRadius.get();
        for (int chunkX = playerChunkPos.x - radius; chunkX <= playerChunkPos.x + radius; chunkX++) {
            for (int chunkZ = playerChunkPos.z - radius; chunkZ <= playerChunkPos.z + radius; chunkZ++) {
                Chunk chunk = mc.world.getChunk(chunkX, chunkZ);
                if (chunk != null && chunk.getStatus().isAtLeast(ChunkStatus.FULL)) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = minY.get(); y <= maxY.get(); y++) {
                                BlockPos pos = new BlockPos(chunkX * 16 + x, y, chunkZ * 16 + z);
                                
                                if (blocksToSkip.contains(pos)) continue;
                                
                                int blockLight = mc.world.getLightLevel(LightType.BLOCK, pos);
                                int skyLight = mc.world.getLightLevel(LightType.SKY, pos);
                                
                                if (blockLight >= minLightLevel.get() && blockLight > skyLight) {
                                    lightsToRender.add(pos);
                                }
                            }
                        }
                    }
                }
            }
        }

        renderLights(event, lightsToRender);
    }

    private void renderLights(Render3DEvent event, List<BlockPos> lightPositions) {
        Set<BlockPos> allLightPositions = new HashSet<>(lightPositions);
        
        for (BlockPos pos : lightPositions) {
            int lightLevel = mc.world.getLightLevel(LightType.BLOCK, pos);
            
            if (lightLevel < 15 && !shouldRenderBlock(pos, allLightPositions)) continue;

            SettingColor sColor, lColor;
            if (thermalColors.get()) {
                float[] thermal = getThermalColor(lightLevel);
                sColor = new SettingColor((int)(thermal[0] * 255), (int)(thermal[1] * 255), 
                                          (int)(thermal[2] * 255), (int)(thermal[3] * 255));
                lColor = new SettingColor((int)(thermal[0] * 255), (int)(thermal[1] * 255), 
                                          (int)(thermal[2] * 255), 255);
            } else {
                sColor = sideColor.get();
                lColor = lineColor.get();
            }

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
        
        // Alpha calculation
        if (lightLevel <= 5) {
            color[3] = 0.25f + (lightLevel / 5.0f) * 0.25f;
        } else if (lightLevel <= 10) {
            color[3] = 0.3f + ((lightLevel - 5) / 5.0f) * 0.3f;
        } else if (lightLevel <= 14) {
            color[3] = 0.5f + ((lightLevel - 10) / 4.0f) * 0.35f;
        } else {
            color[3] = 1.0f;
        }

        // RGB calculation
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

    private void optimizeWeakLights() {
        if (mc.world == null || mc.player == null) return;

        clearOldCacheEntries();
        ChunkPos playerChunkPos = mc.player.getChunkPos();
        Set<BlockPos> weakLightSources = new HashSet<>();
        Set<BlockPos> visited = new HashSet<>();

        int radius = chunkRadius.get();
        for (int chunkX = playerChunkPos.x - radius; chunkX <= playerChunkPos.x + radius; chunkX++) {
            for (int chunkZ = playerChunkPos.z - radius; chunkZ <= playerChunkPos.z + radius; chunkZ++) {
                Chunk chunk = mc.world.getChunk(chunkX, chunkZ);
                if (chunk != null && chunk.getStatus().isAtLeast(ChunkStatus.FULL)) {
                    int startX = chunkX * 16;
                    int startZ = chunkZ * 16;

                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = minY.get(); y <= maxY.get(); y++) {
                                BlockPos pos = new BlockPos(startX + x, y, startZ + z);
                                int blockLight = mc.world.getLightLevel(LightType.BLOCK, pos);
                                int skyLight = mc.world.getLightLevel(LightType.SKY, pos);
                                
                                if ((blockLight == 6 || blockLight == 7) && blockLight > skyLight) {
                                    weakLightSources.add(pos);
                                }
                            }
                        }
                    }
                }
            }
        }

        for (BlockPos weakSource : weakLightSources) {
            if (!visited.contains(weakSource)) {
                int sourceLevel = mc.world.getLightLevel(LightType.BLOCK, weakSource);
                int requiredLevel = sourceLevel + 1;
                
                if (!hasHigherLevelLightIn3x3Area(weakSource, requiredLevel)) {
                    propagateDeletionWithLevelFilter(weakSource, visited, sourceLevel);
                }
            }
        }
    }

    private boolean hasHigherLevelLightIn3x3Area(BlockPos centerPos, int requiredLevel) {
        for (int x = centerPos.getX() - 1; x <= centerPos.getX() + 1; x++) {
            for (int y = Math.max(minY.get(), centerPos.getY() - 1); y <= Math.min(maxY.get(), centerPos.getY() + 1); y++) {
                for (int z = centerPos.getZ() - 1; z <= centerPos.getZ() + 1; z++) {
                    if (x == centerPos.getX() && y == centerPos.getY() && z == centerPos.getZ()) continue;
                    
                    BlockPos checkPos = new BlockPos(x, y, z);
                    int blockLight = mc.world.getLightLevel(LightType.BLOCK, checkPos);
                    int skyLight = mc.world.getLightLevel(LightType.SKY, checkPos);
                    
                    if (blockLight >= requiredLevel && blockLight > skyLight) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void propagateDeletionWithLevelFilter(BlockPos startPos, Set<BlockPos> visited, int maxLevel) {
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(startPos);
        visited.add(startPos);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            blocksToSkip.add(current);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 1) continue;

                        BlockPos neighbor = current.add(dx, dy, dz);
                        
                        if (!visited.contains(neighbor) && !blocksToSkip.contains(neighbor)) {
                            int neighborLight = mc.world.getLightLevel(LightType.BLOCK, neighbor);
                            int neighborSkyLight = mc.world.getLightLevel(LightType.SKY, neighbor);
                            
                            if (neighborLight >= minLightLevel.get() && neighborLight > neighborSkyLight && neighborLight <= maxLevel) {
                                visited.add(neighbor);
                                queue.add(neighbor);
                            }
                        }
                    }
                }
            }
        }
    }

    private void clearOldCacheEntries() {
        if (mc.player == null) {
            blocksToSkip.clear();
            return;
        }

        ChunkPos playerChunk = mc.player.getChunkPos();
        blocksToSkip.removeIf(pos -> {
            ChunkPos posChunk = new ChunkPos(pos);
            return Math.abs(posChunk.x - playerChunk.x) > 3 || Math.abs(posChunk.z - playerChunk.z) > 3;
        });
    }
}