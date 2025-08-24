package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.item.Items;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChunkFinder extends Module {
    public enum Mode {
        Chat,
        Toast,
        Both
    }

    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgDetection = settings.createGroup("Detection");
    private final SettingGroup sgRange = settings.createGroup("Range");
    private final SettingGroup sgThreading = settings.createGroup("Threading");

    private final Setting<SettingColor> chunkHighlightColor = sgGeneral.add(new ColorSetting.Builder()
        .name("chunk-highlight-color")
        .description("Color for highlighting chunks with suspicious activity")
        .defaultValue(new SettingColor(255, 255, 0, 66))
        .build());

    private final Setting<ShapeMode> chunkShapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("chunk-shape-mode")
        .description("Render mode for chunk highlighting")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<Double> surfaceThickness = sgGeneral.add(new DoubleSetting.Builder()
        .name("surface-thickness")
        .description("Thickness of the surface highlight in blocks")
        .defaultValue(0.1)
        .min(0.05)
        .max(2.0)
        .sliderRange(0.05, 2.0)
        .build());

    private final Setting<Mode> notificationMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("How to notify when suspicious chunks are detected")
        .defaultValue(Mode.Both)
        .build());

    private final Setting<Boolean> detectDeepslate = sgDetection.add(new BoolSetting.Builder()
        .name("detect-deepslate")
        .description("Detect regular deepslate blocks")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> detectRotatedDeepslate = sgDetection.add(new BoolSetting.Builder()
        .name("detect-rotated-deepslate")
        .description("Detect rotated deepslate variants")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> detectOneByOneHoles = sgDetection.add(new BoolSetting.Builder()
        .name("detect-1x1-holes")
        .description("Detect 1x1x1 air holes (player-made)")
        .defaultValue(true)
        .build());

    private final Setting<Integer> minY = sgRange.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level to scan")
        .defaultValue(9)
        .min(-64)
        .max(128)
        .sliderRange(-64, 128)
        .visible(() -> false)
        .build());

    private final Setting<Integer> maxY = sgRange.add(new IntSetting.Builder()
        .name("max-y")
        .description("Maximum Y level to scan")
        .defaultValue(15)
        .min(-64)
        .max(320)
        .sliderRange(-64, 320)
        .build());

    private final Setting<Double> highlightLayer = sgGeneral.add(new DoubleSetting.Builder()
        .name("highlight-layer")
        .description("Y level where the chunk highlight will be rendered")
        .defaultValue(52.0)
        .min(-64.0)
        .max(320.0)
        .sliderRange(-64.0, 320.0)
        .build());

    // Configurable thresholds
    private final Setting<Integer> deepslateThreshold = sgDetection.add(new IntSetting.Builder()
        .name("deepslate-threshold")
        .description("Minimum deepslate blocks needed to mark chunk as suspicious")
        .defaultValue(2)
        .min(1)
        .max(20)
        .sliderRange(1, 20)
        .visible(detectDeepslate::get)
        .build());

    private final Setting<Integer> rotatedDeepslateThreshold = sgDetection.add(new IntSetting.Builder()
        .name("rotated-deepslate-threshold")
        .description("Minimum rotated deepslate blocks needed to mark chunk as suspicious")
        .defaultValue(2)
        .min(1)
        .max(20)
        .sliderRange(1, 20)
        .visible(detectRotatedDeepslate::get)
        .build());

    private final Setting<Integer> oneByOneHoleThreshold = sgDetection.add(new IntSetting.Builder()
        .name("1x1-hole-threshold")
        .description("Minimum 1x1x1 holes needed to mark chunk as suspicious")
        .defaultValue(1)
        .min(1)
        .max(10)
        .sliderRange(1, 10)
        .visible(detectOneByOneHoles::get)
        .build());

    private final Setting<Boolean> useThreading = sgThreading.add(new BoolSetting.Builder()
        .name("enable-threading")
        .description("Use multi-threading for chunk scanning (better performance)")
        .defaultValue(true)
        .build());

    private final Setting<Integer> threadPoolSize = sgThreading.add(new IntSetting.Builder()
        .name("thread-pool-size")
        .description("Number of threads to use for scanning")
        .defaultValue(1)
        .min(1)
        .max(4)
        .sliderRange(1, 4)
        .visible(useThreading::get)
        .build());

    private final Setting<Integer> scanDelay = sgThreading.add(new IntSetting.Builder()
        .name("scan-delay")
        .description("Delay between chunk scans in milliseconds (prevents lag)")
        .defaultValue(1)
        .min(0)
        .max(500)
        .sliderRange(0, 500)
        .build());

    private final Set<ChunkPos> suspiciousChunks = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<ChunkPos, ChunkScanResult> chunkResults = new ConcurrentHashMap<>();
    private final Set<ChunkPos> processedChunks = ConcurrentHashMap.newKeySet();

    private ExecutorService threadPool;
    private volatile boolean isScanning = false;

    public ChunkFinder() {
        super(GlazedAddon.esp, "ChunkFinder", "Finds and highlights chunks with deepslate, rotated deepslate, and 1x1x1 holes around Y level 10.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null) return;

        if (useThreading.get()) {
            threadPool = Executors.newFixedThreadPool(threadPoolSize.get());
        }

        suspiciousChunks.clear();
        chunkResults.clear();
        processedChunks.clear();
        isScanning = true;

        if (useThreading.get()) {
            threadPool.submit(() -> {
                try {
                    for (Chunk chunk : Utils.chunks()) {
                        if (chunk instanceof WorldChunk worldChunk && isScanning) {
                            threadPool.submit(() -> scanChunkForSuspiciousActivity(worldChunk));
                            Thread.sleep(scanDelay.get());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        } else {
            new Thread(() -> {
                try {
                    for (Chunk chunk : Utils.chunks()) {
                        if (chunk instanceof WorldChunk worldChunk && isScanning) {
                            scanChunkForSuspiciousActivity(worldChunk);
                            Thread.sleep(scanDelay.get());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }

    @Override
    public void onDeactivate() {
        isScanning = false;

        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
            threadPool = null;
        }

        suspiciousChunks.clear();
        chunkResults.clear();
        processedChunks.clear();
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (!isScanning) return;

        ChunkPos chunkPos = event.chunk().getPos();
        if (processedChunks.contains(chunkPos)) return;

        if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
            threadPool.submit(() -> {
                try {
                    Thread.sleep(scanDelay.get());
                    scanChunkForSuspiciousActivity(event.chunk());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        } else {
            new Thread(() -> {
                try {
                    Thread.sleep(scanDelay.get());
                    scanChunkForSuspiciousActivity(event.chunk());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!isScanning) return;

        BlockPos pos = event.pos;
        ChunkPos chunkPos = new ChunkPos(pos);

        if (pos.getY() < minY.get() || pos.getY() > maxY.get()) return;

        WorldChunk chunk = (WorldChunk) mc.world.getChunk(chunkPos.x, chunkPos.z);

        Runnable updateTask = () -> {
            try {
                Thread.sleep(scanDelay.get() / 4);
                scanChunkForSuspiciousActivity(chunk);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
            threadPool.submit(updateTask);
        } else {
            new Thread(updateTask).start();
        }
    }

    private void scanChunkForSuspiciousActivity(WorldChunk chunk) {
        if (!isScanning) return;

        ChunkPos cpos = chunk.getPos();

        if (processedChunks.contains(cpos)) return;
        processedChunks.add(cpos);

        int xStart = cpos.getStartX();
        int zStart = cpos.getStartZ();
        int yMin = Math.max(chunk.getBottomY(), minY.get());
        int yMax = Math.min(chunk.getBottomY() + chunk.getHeight(), maxY.get());

        ChunkScanResult result = new ChunkScanResult();
        int step = 1;

        for (int x = xStart; x < xStart + 16; x += step) {
            for (int z = zStart; z < zStart + 16; z += step) {
                for (int y = yMin; y < yMax; y += step) {
                    if (!isScanning) return;

                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);

                    if (detectDeepslate.get() && isRegularDeepslate(state, y)) {
                        result.deepslateCount++;
                    }

                    if (detectRotatedDeepslate.get() && isRotatedDeepslate(state, y)) {
                        result.rotatedDeepslateCount++;
                    }

                    if (detectOneByOneHoles.get() && y % 2 == 0 && isOneByOneHole(pos)) {
                        result.oneByOneHoleCount++;
                    }
                }
            }
        }

        chunkResults.put(cpos, result);

        boolean shouldHighlight = false;
        StringBuilder reasons = new StringBuilder();

        if (detectDeepslate.get() && result.deepslateCount >= deepslateThreshold.get()) {
            shouldHighlight = true;
            reasons.append("Deepslate: ").append(result.deepslateCount).append(" ");
        }

        if (detectRotatedDeepslate.get() && result.rotatedDeepslateCount >= rotatedDeepslateThreshold.get()) {
            shouldHighlight = true;
            reasons.append("Rotated Deepslate: ").append(result.rotatedDeepslateCount).append(" ");
        }

        if (detectOneByOneHoles.get() && result.oneByOneHoleCount >= oneByOneHoleThreshold.get()) {
            shouldHighlight = true;
            reasons.append("1x1 Holes: ").append(result.oneByOneHoleCount).append(" ");
        }

        if (shouldHighlight) {
            boolean wasNewChunk = suspiciousChunks.add(cpos);
            if (wasNewChunk) {
                String message = "Suspicious base at " + cpos.x + "," + cpos.z + " - " + reasons.toString().trim();

                switch (notificationMode.get()) {
                    case Chat -> info("§6[§eChunkFinder§6] §e" + message);
                    case Toast -> mc.getToastManager().add(new MeteorToast(Items.DEEPSLATE, title, message));
                    case Both -> {
                        info("§6[§eChunkFinder§6] §e" + message);
                        mc.getToastManager().add(new MeteorToast(Items.DEEPSLATE, title, message));
                    }
                }
            }
        } else {
            suspiciousChunks.remove(cpos);
        }
    }

    private boolean isRegularDeepslate(BlockState state, int y) {
        return y >= minY.get() && y <= maxY.get() && state.getBlock() == Blocks.DEEPSLATE;
    }

    private boolean isRotatedDeepslate(BlockState state, int y) {
        if (y < minY.get() || y > maxY.get()) return false;
        if (!state.contains(Properties.AXIS)) return false;

        Direction.Axis axis = state.get(Properties.AXIS);
        if (axis == Direction.Axis.Y) return false;

        return state.isOf(Blocks.DEEPSLATE) ||
            state.isOf(Blocks.POLISHED_DEEPSLATE) ||
            state.isOf(Blocks.DEEPSLATE_BRICKS) ||
            state.isOf(Blocks.DEEPSLATE_TILES) ||
            state.isOf(Blocks.CHISELED_DEEPSLATE);
    }

    // Improved 1x1x1 hole detection (copied from your OneByOneHoles module)
    private boolean isOneByOneHole(BlockPos pos) {
        if (mc.world == null) return false;

        if (pos.getY() < minY.get() || pos.getY() > maxY.get()) return false;

        BlockState selfState = mc.world.getBlockState(pos);

        // Only highlight holes above Y level 1
        if (pos.getY() <= 1) return false;
        if (selfState.getBlock() != Blocks.AIR) return false;

        // Check if all 6 sides are solid blocks
        for (Direction direction : Direction.values()) {
            BlockState neighborState = mc.world.getBlockState(pos.offset(direction));
            if (!neighborState.isSolidBlock(mc.world, pos.offset(direction))) {
                return false;
            }
        }


        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    BlockPos checkPos = pos.add(x, y, z);
                    BlockState checkState = mc.world.getBlockState(checkPos);


                    if (!checkState.isSolidBlock(mc.world, checkPos)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        Color chunkColor = new Color(chunkHighlightColor.get());

        for (ChunkPos chunkPos : suspiciousChunks) {
            int xStart = chunkPos.getStartX();
            int zStart = chunkPos.getStartZ();
            int xEnd = chunkPos.getEndX();
            int zEnd = chunkPos.getEndZ();

            double surfaceY = highlightLayer.get(); // layer from um  the highligt esp shi

            double thickness = surfaceThickness.get();
            Box surfaceBox = new Box(
                xStart,
                surfaceY,
                zStart,
                xEnd + 1,
                surfaceY + thickness,
                zEnd + 1
            );

            event.renderer.box(surfaceBox, chunkColor, chunkColor, chunkShapeMode.get(), 0);
        }
    }

    private static class ChunkScanResult {
        int deepslateCount = 0;
        int rotatedDeepslateCount = 0;
        int oneByOneHoleCount = 0; // Added 1x1 hole count
    }
}
