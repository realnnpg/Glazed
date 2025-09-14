package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Queue;

public class ChunkFinder extends Module {
    private final SettingGroup sgDetection = settings.createGroup("Detection");
    private final SettingGroup sgRange = settings.createGroup("Range");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgBlockHighlight = settings.createGroup("Block Highlighting");
    private final SettingGroup sgPerformance = settings.createGroup("Performance");
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");

    // Detection settings
    private final Setting<Boolean> detectDeepslate = sgDetection.add(new BoolSetting.Builder()
        .name("detect-deepslate")
        .description("Flag chunks with suspicious deepslate patterns (Y ≥ 8)")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> detectCobbledDeepslate = sgDetection.add(new BoolSetting.Builder()
        .name("detect-cobbled-deepslate")
        .description("Find cobbled deepslate blocks")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> detectRotatedDeepslate = sgDetection.add(new BoolSetting.Builder()
        .name("detect-rotated-deepslate")
        .description("Find rotated deepslate blocks")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> detectOneByOneHoles = sgDetection.add(new BoolSetting.Builder()
        .name("detect-air-pockets")
        .description("Locate artificial air pockets in solid stone")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> detectEnclosedOres = sgDetection.add(new BoolSetting.Builder()
        .name("detect-enclosed-ores")
        .description("Find ores completely surrounded by stone")
        .defaultValue(false)
        .build());

    // Thresholds
    private final Setting<Integer> deepslateThreshold = sgDetection.add(new IntSetting.Builder()
        .name("deepslate-threshold")
        .description("Min deepslate count to flag chunk")
        .defaultValue(3)
        .range(1, 25)
        .sliderRange(1, 25)
        .visible(detectDeepslate::get)
        .build());

    private final Setting<Integer> cobbledDeepslateThreshold = sgDetection.add(new IntSetting.Builder()
        .name("cobbled-deepslate-threshold")
        .description("Min cobbled deepslate to flag chunk")
        .defaultValue(1)
        .range(1, 15)
        .sliderRange(1, 15)
        .visible(detectCobbledDeepslate::get)
        .build());

    private final Setting<Integer> rotatedDeepslateThreshold = sgDetection.add(new IntSetting.Builder()
        .name("rotated-threshold")
        .description("Min rotated deepslate to flag chunk")
        .defaultValue(2)
        .range(1, 20)
        .sliderRange(1, 20)
        .visible(detectRotatedDeepslate::get)
        .build());

    private final Setting<Integer> airPocketThreshold = sgDetection.add(new IntSetting.Builder()
        .name("air-pocket-threshold")
        .description("Min air pockets to flag chunk")
        .defaultValue(1)
        .range(1, 8)
        .sliderRange(1, 8)
        .visible(detectOneByOneHoles::get)
        .build());

    private final Setting<Integer> enclosedOreThreshold = sgDetection.add(new IntSetting.Builder()
        .name("enclosed-ore-threshold")
        .description("Min enclosed ores to flag chunk")
        .defaultValue(2)
        .range(1, 15)
        .sliderRange(1, 15)
        .visible(detectEnclosedOres::get)
        .build());

    // Range settings
    private final Setting<Integer> minScanY = sgRange.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level for scanning")
        .defaultValue(-20)
        .range(-64, 64)
        .sliderRange(-64, 64)
        .build());

    private final Setting<Integer> maxScanY = sgRange.add(new IntSetting.Builder()
        .name("max-y")
        .description("Maximum Y level for scanning")
        .defaultValue(25)
        .range(-32, 128)
        .sliderRange(-32, 128)
        .build());

    // Chunk render settings
    private final Setting<Double> renderY = sgRender.add(new DoubleSetting.Builder()
        .name("render-height")
        .description("Height to render chunk highlights")
        .defaultValue(64.0)
        .range(-64.0, 320.0)
        .sliderRange(-64.0, 320.0)
        .build());

    private final Setting<ShapeMode> renderMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("render-mode")
        .description("How to render highlighted chunks")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<SettingColor> chunkColor = sgRender.add(new ColorSetting.Builder()
        .name("chunk-color")
        .description("Color for suspicious chunks")
        .defaultValue(new SettingColor(255, 215, 0, 120))
        .build());

    private final Setting<Double> thickness = sgRender.add(new DoubleSetting.Builder()
        .name("thickness")
        .description("Thickness of highlight box")
        .defaultValue(0.3)
        .range(0.1, 2.0)
        .sliderRange(0.1, 2.0)
        .build());

    private final Setting<Boolean> drawTracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw lines to suspicious chunks")
        .defaultValue(false)
        .build());

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color for tracer lines")
        .defaultValue(new SettingColor(255, 69, 0, 180))
        .visible(drawTracers::get)
        .build());

    // Block highlighting settings
    private final Setting<Boolean> highlightBlocks = sgBlockHighlight.add(new BoolSetting.Builder()
        .name("highlight-blocks")
        .description("Highlight individual suspicious blocks")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxBlocksToRender = sgBlockHighlight.add(new IntSetting.Builder()
        .name("max-blocks-render")
        .description("Maximum number of blocks to highlight (performance)")
        .defaultValue(200)
        .range(50, 1000)
        .sliderRange(50, 1000)
        .visible(highlightBlocks::get)
        .build());

    private final Setting<ShapeMode> blockRenderMode = sgBlockHighlight.add(new EnumSetting.Builder<ShapeMode>()
        .name("block-render-mode")
        .description("How to render individual blocks")
        .defaultValue(ShapeMode.Lines)
        .visible(highlightBlocks::get)
        .build());

    private final Setting<SettingColor> deepslateBlockColor = sgBlockHighlight.add(new ColorSetting.Builder()
        .name("deepslate-color")
        .description("Color for deepslate blocks")
        .defaultValue(new SettingColor(100, 100, 100, 200))
        .visible(highlightBlocks::get)
        .build());

    private final Setting<SettingColor> cobbledDeepslateBlockColor = sgBlockHighlight.add(new ColorSetting.Builder()
        .name("cobbled-deepslate-color")
        .description("Color for cobbled deepslate blocks")
        .defaultValue(new SettingColor(80, 80, 80, 200))
        .visible(highlightBlocks::get)
        .build());

    private final Setting<SettingColor> rotatedDeepslateBlockColor = sgBlockHighlight.add(new ColorSetting.Builder()
        .name("rotated-deepslate-color")
        .description("Color for rotated deepslate blocks")
        .defaultValue(new SettingColor(120, 0, 120, 200))
        .visible(highlightBlocks::get)
        .build());

    private final Setting<SettingColor> airPocketBlockColor = sgBlockHighlight.add(new ColorSetting.Builder()
        .name("air-pocket-color")
        .description("Color for artificial air pockets")
        .defaultValue(new SettingColor(255, 255, 255, 100))
        .visible(highlightBlocks::get)
        .build());

    private final Setting<SettingColor> enclosedOreBlockColor = sgBlockHighlight.add(new ColorSetting.Builder()
        .name("enclosed-ore-color")
        .description("Color for enclosed ores")
        .defaultValue(new SettingColor(0, 255, 0, 200))
        .visible(highlightBlocks::get)
        .build());

    // Performance settings
    private final Setting<Boolean> useMultiThreading = sgPerformance.add(new BoolSetting.Builder()
        .name("threading")
        .description("Use background threads for scanning")
        .defaultValue(true)
        .build());

    private final Setting<Integer> threadCount = sgPerformance.add(new IntSetting.Builder()
        .name("thread-count")
        .description("Number of worker threads")
        .defaultValue(Math.max(1, Runtime.getRuntime().availableProcessors() / 2))
        .range(1, 4)
        .sliderRange(1, 4)
        .visible(useMultiThreading::get)
        .build());

    private final Setting<Integer> scanInterval = sgPerformance.add(new IntSetting.Builder()
        .name("scan-delay")
        .description("Milliseconds between scans")
        .defaultValue(100)
        .range(50, 2000)
        .sliderRange(50, 2000)
        .build());

    private final Setting<Integer> maxConcurrentScans = sgPerformance.add(new IntSetting.Builder()
        .name("max-concurrent-scans")
        .description("Max chunks scanned simultaneously")
        .defaultValue(3)
        .range(1, 8)
        .sliderRange(1, 8)
        .build());

    private final Setting<Integer> cleanupInterval = sgPerformance.add(new IntSetting.Builder()
        .name("cleanup-interval")
        .description("Seconds between distant chunk cleanup")
        .defaultValue(30)
        .range(15, 300)
        .sliderRange(15, 300)
        .build());

    // Notification settings
    private final Setting<Boolean> playSound = sgNotifications.add(new BoolSetting.Builder()
        .name("sound-alerts")
        .description("Play sound when suspicious chunks found")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> chatAlerts = sgNotifications.add(new BoolSetting.Builder()
        .name("chat-alerts")
        .description("Send chat notifications")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxAlerts = sgNotifications.add(new IntSetting.Builder()
        .name("max-alerts")
        .description("Max alerts per minute")
        .defaultValue(5)
        .range(1, 20)
        .sliderRange(1, 20)
        .build());

    // Data structures
    private final Set<ChunkPos> flaggedChunks = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<ChunkPos, ChunkAnalysis> chunkData = new ConcurrentHashMap<>();
    private final Set<ChunkPos> scannedChunks = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<ChunkPos, Long> notificationTimes = new ConcurrentHashMap<>();
    private final Queue<Long> recentAlerts = new ConcurrentLinkedQueue<>();
    private final AtomicLong activeScanCount = new AtomicLong(0);

    private final Map<BlockPos, SuspiciousBlock> suspiciousBlocks = new ConcurrentHashMap<>();

    private ExecutorService scannerPool;
    private volatile boolean shouldScan = false;
    private long lastCleanup = 0;

    public ChunkFinder() {
        super(GlazedAddon.esp, "ChunkFinder", "ChunkFinderV3 Made by Potato same shit but better");
    }

    @Override
    public void onActivate() {
        if (mc.world == null) return;

        clearAll();
        shouldScan = true;
        lastCleanup = System.currentTimeMillis();

        if (useMultiThreading.get()) {
            scannerPool = Executors.newFixedThreadPool(threadCount.get(), r -> {
                Thread t = new Thread(r, "ChunkFinder-Worker");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });
            startInitialScan();
        } else {
            startInitialScan();
        }
    }

    @Override
    public void onDeactivate() {
        shouldScan = false;

        if (scannerPool != null) {
            scannerPool.shutdownNow();
            scannerPool = null;
        }

        clearAll();
    }

    private void clearAll() {
        flaggedChunks.clear();
        chunkData.clear();
        scannedChunks.clear();
        notificationTimes.clear();
        recentAlerts.clear();
        suspiciousBlocks.clear();
        activeScanCount.set(0);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();

        while (!recentAlerts.isEmpty() && now - recentAlerts.peek() > 60000) {
            recentAlerts.poll();
        }

        if (now - lastCleanup > cleanupInterval.get() * 1000L) {
            performCleanup();
            lastCleanup = now;
        }
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (!shouldScan || activeScanCount.get() >= maxConcurrentScans.get()) return;

        ChunkPos pos = event.chunk().getPos();
        if (!scannedChunks.contains(pos)) {
            scheduleChunkScan(event.chunk());
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!shouldScan) return;

        BlockPos blockPos = event.pos;
        if (blockPos.getY() < minScanY.get() || blockPos.getY() > maxScanY.get()) return;

        BlockState newState = event.newState;
        if (isRelevantBlock(newState)) {
            ChunkPos chunkPos = new ChunkPos(blockPos);
            WorldChunk chunk = (WorldChunk) mc.world.getChunk(chunkPos.x, chunkPos.z);
            scheduleChunkScan(chunk);
        }
    }

    private boolean isRelevantBlock(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.DEEPSLATE ||
            block == Blocks.COBBLED_DEEPSLATE ||
            block == Blocks.POLISHED_DEEPSLATE ||
            block == Blocks.DEEPSLATE_BRICKS ||
            block == Blocks.DEEPSLATE_TILES ||
            block == Blocks.CHISELED_DEEPSLATE ||
            block == Blocks.AIR ||
            isValuableOre(block);
    }

    private void startInitialScan() {
        Runnable initialScanTask = () -> {
            try {
                for (Chunk chunk : Utils.chunks()) {
                    if (!shouldScan) break;
                    if (chunk instanceof WorldChunk worldChunk) {
                        if (activeScanCount.get() < maxConcurrentScans.get()) {
                            if (useMultiThreading.get() && scannerPool != null) {
                                scannerPool.submit(() -> analyzeChunk(worldChunk));
                            } else {
                                analyzeChunk(worldChunk);
                            }
                        }
                        Thread.sleep(scanInterval.get());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        if (useMultiThreading.get() && scannerPool != null) {
            scannerPool.submit(initialScanTask);
        } else {
            new Thread(initialScanTask, "ChunkFinder-Initial").start();
        }
    }

    private void scheduleChunkScan(WorldChunk chunk) {
        if (activeScanCount.get() >= maxConcurrentScans.get()) return;

        Runnable scanTask = () -> {
            try {
                Thread.sleep(scanInterval.get() / 2);
                analyzeChunk(chunk);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        if (useMultiThreading.get() && scannerPool != null) {
            scannerPool.submit(scanTask);
        } else {
            new Thread(scanTask, "ChunkFinder-Scan").start();
        }
    }

    private void analyzeChunk(WorldChunk chunk) {
        if (!shouldScan || chunk == null) return;

        ChunkPos pos = chunk.getPos();
        if (scannedChunks.contains(pos)) return;

        activeScanCount.incrementAndGet();
        try {
            scannedChunks.add(pos);

            int startX = pos.getStartX();
            int startZ = pos.getStartZ();
            int minY = Math.max(chunk.getBottomY(), minScanY.get());
            int maxY = Math.min(chunk.getBottomY() + chunk.getHeight(), maxScanY.get());

            ChunkAnalysis analysis = new ChunkAnalysis();

            scanChunkSections(chunk, analysis, minY, maxY);

            chunkData.put(pos, analysis);
            evaluateChunk(pos, analysis);
        } finally {
            activeScanCount.decrementAndGet();
        }
    }

    private void scanChunkSections(WorldChunk chunk, ChunkAnalysis analysis, int minY, int maxY) {
        ChunkSection[] sections = chunk.getSectionArray();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            if (!shouldScan) return;

            ChunkSection section = sections[sectionIndex];
            if (section.isEmpty()) continue;

            int sectionY = chunk.getBottomY() + sectionIndex * 16;
            int startY = Math.max(0, minY - sectionY);
            int endY = Math.min(15, maxY - sectionY);

            if (startY > 15 || endY < 0) continue;

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = startY; y <= endY; y++) {
                        if (!shouldScan) return;

                        BlockState state = section.getBlockState(x, y, z);
                        int worldY = sectionY + y;
                        BlockPos blockPos = new BlockPos(chunk.getPos().getStartX() + x, worldY, chunk.getPos().getStartZ() + z);

                        analyzeBlock(blockPos, state, worldY, analysis);
                    }
                }
            }
        }
    }

    private void analyzeBlock(BlockPos blockPos, BlockState state, int worldY, ChunkAnalysis analysis) {
        SuspiciousBlockType blockType = null;

        // Only detect deepslate at Y level 8 and above
        if (detectDeepslate.get() && isNormalDeepslate(state) && worldY >= 8) {
            analysis.deepslateCount++;
            blockType = SuspiciousBlockType.DEEPSLATE;
        }

        if (detectCobbledDeepslate.get() && isCobbledDeepslate(state)) {
            analysis.cobbledDeepslateCount++;
            blockType = SuspiciousBlockType.COBBLED_DEEPSLATE;
        }

        if (detectRotatedDeepslate.get() && isRotatedDeepslateBlock(state)) {
            analysis.rotatedDeepslateCount++;
            blockType = SuspiciousBlockType.ROTATED_DEEPSLATE;
        }

        if (activeScanCount.get() <= 2) {
            if (detectOneByOneHoles.get() && isArtificialAirPocket(blockPos)) {
                analysis.airPocketCount++;
                blockType = SuspiciousBlockType.AIR_POCKET;
            }

            if (detectEnclosedOres.get() && isSuspiciousOre(state, blockPos)) {
                analysis.enclosedOreCount++;
                blockType = SuspiciousBlockType.ENCLOSED_ORE;
            }
        }

        // Add block to suspicious blocks map if it's suspicious
        if (blockType != null && highlightBlocks.get()) {
            suspiciousBlocks.put(blockPos, new SuspiciousBlock(blockType, System.currentTimeMillis()));
        }
    }

    private void evaluateChunk(ChunkPos pos, ChunkAnalysis analysis) {
        boolean suspicious = false;
        StringBuilder reasons = new StringBuilder();

        if (detectDeepslate.get() && analysis.deepslateCount >= deepslateThreshold.get()) {
            suspicious = true;
            reasons.append("Deepslate[").append(analysis.deepslateCount).append("] ");
        }

        if (detectCobbledDeepslate.get() && analysis.cobbledDeepslateCount >= cobbledDeepslateThreshold.get()) {
            suspicious = true;
            reasons.append("CobbledDeepslate[").append(analysis.cobbledDeepslateCount).append("] ");
        }

        if (detectRotatedDeepslate.get() && analysis.rotatedDeepslateCount >= rotatedDeepslateThreshold.get()) {
            suspicious = true;
            reasons.append("Rotated[").append(analysis.rotatedDeepslateCount).append("] ");
        }

        if (detectOneByOneHoles.get() && analysis.airPocketCount >= airPocketThreshold.get()) {
            suspicious = true;
            reasons.append("AirPockets[").append(analysis.airPocketCount).append("] ");
        }

        if (detectEnclosedOres.get() && analysis.enclosedOreCount >= enclosedOreThreshold.get()) {
            suspicious = true;
            reasons.append("EnclosedOres[").append(analysis.enclosedOreCount).append("] ");
        }

        if (suspicious) {
            if (flaggedChunks.add(pos)) {
                notifyChunkFound(pos, reasons.toString().trim());
            }
        } else {
            flaggedChunks.remove(pos);
            notificationTimes.remove(pos);
        }
    }

    // Block detection methods
    private boolean isNormalDeepslate(BlockState state) {
        return state.getBlock() == Blocks.DEEPSLATE;
    }

    private boolean isCobbledDeepslate(BlockState state) {
        return state.getBlock() == Blocks.COBBLED_DEEPSLATE;
    }

    private boolean isRotatedDeepslateBlock(BlockState state) {
        if (!state.contains(Properties.AXIS)) return false;

        Direction.Axis axis = state.get(Properties.AXIS);
        if (axis == Direction.Axis.Y) return false;

        Block block = state.getBlock();
        return block == Blocks.DEEPSLATE ||
            block == Blocks.POLISHED_DEEPSLATE ||
            block == Blocks.DEEPSLATE_BRICKS ||
            block == Blocks.DEEPSLATE_TILES ||
            block == Blocks.CHISELED_DEEPSLATE;
    }

    private boolean isArtificialAirPocket(BlockPos pos) {
        if (mc.world == null) return false;

        BlockState center = mc.world.getBlockState(pos);
        if (center.getBlock() != Blocks.AIR) return false;
        if (pos.getY() <= 2 || pos.getY() > maxScanY.get()) return false;

        int solidNeighbors = 0;
        for (Direction dir : Direction.values()) {
            BlockState neighbor = mc.world.getBlockState(pos.offset(dir));
            if (neighbor.isSolidBlock(mc.world, pos.offset(dir))) {
                solidNeighbors++;
            }
        }

        if (solidNeighbors < 6) return false;

        int nearbyAirBlocks = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    BlockPos checkPos = pos.add(dx, dy, dz);
                    if (mc.world.getBlockState(checkPos).getBlock() == Blocks.AIR) {
                        nearbyAirBlocks++;
                    }
                }
            }
        }

        if (nearbyAirBlocks > 0) return false;

        int naturalBlocks = 0;
        int totalChecked = 0;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1 && Math.abs(dz) <= 1) continue;

                    BlockPos checkPos = pos.add(dx, dy, dz);
                    BlockState checkState = mc.world.getBlockState(checkPos);
                    Block block = checkState.getBlock();
                    totalChecked++;

                    if (block == Blocks.STONE || block == Blocks.DEEPSLATE ||
                        block == Blocks.COBBLESTONE || block == Blocks.COBBLED_DEEPSLATE) {
                        naturalBlocks++;
                    }
                }
            }
        }

        return totalChecked > 0 && (naturalBlocks * 100 / totalChecked) >= 80;
    }

    private boolean isSuspiciousOre(BlockState state, BlockPos pos) {
        if (!isValuableOre(state.getBlock())) return false;
        return isCompletelyEnclosed(pos);
    }

    private boolean isValuableOre(Block block) {
        return block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE ||
            block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE ||
            block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE ||
            block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE ||
            block == Blocks.ANCIENT_DEBRIS;
    }

    private boolean isCompletelyEnclosed(BlockPos pos) {
        if (mc.world == null) return false;

        for (Direction dir : Direction.values()) {
            BlockPos adjacent = pos.offset(dir);
            BlockState adjacentState = mc.world.getBlockState(adjacent);

            if (adjacentState.isAir() || !adjacentState.isOpaque()) {
                return false;
            }
        }
        return true;
    }

    private void notifyChunkFound(ChunkPos pos, String details) {
        long now = System.currentTimeMillis();

        if (recentAlerts.size() >= maxAlerts.get()) return;

        Long lastNotification = notificationTimes.get(pos);
        if (lastNotification != null && now - lastNotification < 45000) return;

        mc.execute(() -> {
            if (chatAlerts.get() && mc.player != null) {
                String message = String.format("Suspicious chunk detected at [%d, %d] - %s",
                    pos.x, pos.z, details);
                mc.player.sendMessage(Text.literal("§6[ChunkFinder] §e" + message), false);
            }

            if (playSound.get()) {
                mc.getSoundManager().play(PositionedSoundInstance.master(
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f));
            }

            recentAlerts.offer(now);
            notificationTimes.put(pos, now);
        });
    }

    private void performCleanup() {
        if (mc.player == null) return;

        int viewDist = mc.options.getViewDistance().getValue();
        int playerChunkX = (int) mc.player.getX() / 16;
        int playerChunkZ = (int) mc.player.getZ() / 16;

        flaggedChunks.removeIf(pos -> {
            int dx = Math.abs(pos.x - playerChunkX);
            int dz = Math.abs(pos.z - playerChunkZ);
            boolean tooFar = dx > viewDist + 5 || dz > viewDist + 5;

            if (tooFar) {
                chunkData.remove(pos);
                notificationTimes.remove(pos);
            }
            return tooFar;
        });

        scannedChunks.removeIf(pos -> {
            int dx = Math.abs(pos.x - playerChunkX);
            int dz = Math.abs(pos.z - playerChunkZ);
            return dx > viewDist + 3 || dz > viewDist + 3;
        });

        // Clean up suspicious blocks that are too far away
        suspiciousBlocks.entrySet().removeIf(entry -> {
            BlockPos blockPos = entry.getKey();
            double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(blockPos));
            return distance > viewDist * 16 + 80; // Clean up blocks beyond view distance + buffer
        });
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null) return;

        // Render chunk highlights
        if (!flaggedChunks.isEmpty()) {
            Color highlight = new Color(chunkColor.get());
            int rendered = 0;
            for (ChunkPos pos : flaggedChunks) {
                if (rendered++ > 50) break;
                renderChunkHighlight(event, pos, highlight);
            }
        }

        // Render individual suspicious blocks
        if (highlightBlocks.get() && !suspiciousBlocks.isEmpty()) {
            renderSuspiciousBlocks(event);
        }
    }

    private void renderChunkHighlight(Render3DEvent event, ChunkPos pos, Color color) {
        int startX = pos.getStartX();
        int startZ = pos.getStartZ();
        int endX = pos.getEndX();
        int endZ = pos.getEndZ();

        double y = renderY.get();
        double h = thickness.get();

        Box box = new Box(startX, y, startZ, endX + 1, y + h, endZ + 1);
        event.renderer.box(box, color, color, renderMode.get(), 0);

        if (drawTracers.get()) {
            Vec3d playerPos = mc.player.getEyePos();
            Vec3d chunkCenter = new Vec3d(startX + 8, y + h / 2, startZ + 8);
            Color tracerCol = new Color(tracerColor.get());

            event.renderer.line(playerPos.x, playerPos.y, playerPos.z,
                chunkCenter.x, chunkCenter.y, chunkCenter.z, tracerCol);
        }
    }

    private void renderSuspiciousBlocks(Render3DEvent event) {
        int rendered = 0;

        for (Map.Entry<BlockPos, SuspiciousBlock> entry : suspiciousBlocks.entrySet()) {
            if (rendered >= maxBlocksToRender.get()) break;

            BlockPos pos = entry.getKey();
            SuspiciousBlock suspiciousBlock = entry.getValue();

            // Skip if too far away
            double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(pos));
            if (distance > mc.options.getViewDistance().getValue() * 16) continue;

            Color blockColor = getColorForBlockType(suspiciousBlock.type);
            if (blockColor != null) {
                Box box = new Box(pos);
                event.renderer.box(box, blockColor, blockColor, blockRenderMode.get(), 0);
                rendered++;
            }
        }
    }

    private Color getColorForBlockType(SuspiciousBlockType type) {
        return switch (type) {
            case DEEPSLATE -> new Color(deepslateBlockColor.get());
            case COBBLED_DEEPSLATE -> new Color(cobbledDeepslateBlockColor.get());
            case ROTATED_DEEPSLATE -> new Color(rotatedDeepslateBlockColor.get());
            case AIR_POCKET -> new Color(airPocketBlockColor.get());
            case ENCLOSED_ORE -> new Color(enclosedOreBlockColor.get());
        };
    }

    @Override
    public String getInfoString() {
        if (highlightBlocks.get()) {
            return String.format("C:%d B:%d", flaggedChunks.size(), suspiciousBlocks.size());
        }
        return String.valueOf(flaggedChunks.size());
    }

    // Data classes
    private static class ChunkAnalysis {
        int deepslateCount = 0;
        int cobbledDeepslateCount = 0;
        int rotatedDeepslateCount = 0;
        int airPocketCount = 0;
        int enclosedOreCount = 0;
    }

    private static class SuspiciousBlock {
        final SuspiciousBlockType type;
        final long detectedTime;

        SuspiciousBlock(SuspiciousBlockType type, long detectedTime) {
            this.type = type;
            this.detectedTime = detectedTime;
        }
    }

    private enum SuspiciousBlockType {
        DEEPSLATE,
        COBBLED_DEEPSLATE,
        ROTATED_DEEPSLATE,
        AIR_POCKET,
        ENCLOSED_ORE
    }
}
