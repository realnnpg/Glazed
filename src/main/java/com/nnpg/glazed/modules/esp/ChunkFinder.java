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
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Queue;

public class ChunkFinder extends Module {
    public enum Mode {
        Chat,
        Toast,
        Both
    }

    private final SettingGroup sgDetection = settings.createGroup("Detection");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgBlockHighlight = settings.createGroup("Block Highlighting");
    private final SettingGroup sgPerformance = settings.createGroup("Performance");
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");

    private final Setting<Boolean> detectDeepslate = sgDetection.add(new BoolSetting.Builder()
        .name("detect-deepslate")
        .description("Find deepslate blocks")
        .defaultValue(false)
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

    private final Setting<Boolean> detectEndStone = sgDetection.add(new BoolSetting.Builder()
        .name("detect-end-stone")
        .description("Find end stone blocks (disabled in The End dimension)")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> ignoreExposed = sgDetection.add(new BoolSetting.Builder()
        .name("ignore-exposed")
        .description("Ignore suspicious blocks that are exposed to air or fluid (treats water/lava like air)")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> ignoreTrialChambers = sgDetection.add(new BoolSetting.Builder()
        .name("ignore-trial-chambers")
        .description("Ignore chunks containing trial chambers (based on waxed copper blocks and tuff bricks)")
        .defaultValue(true)
        .build());

    private final Setting<Integer> trialChamberThreshold = sgDetection.add(new IntSetting.Builder()
        .name("trial-chamber-threshold")
        .description("Minimum waxed copper or tuff brick blocks to identify a trial chamber")
        .defaultValue(50)
        .range(1, 50)
        .sliderRange(1, 50)
        .visible(ignoreTrialChambers::get)
        .build());

    private final Setting<Integer> deepslateThreshold = sgDetection.add(new IntSetting.Builder()
        .name("deepslate-threshold")
        .description("Min deepslate to flag chunk")
        .defaultValue(1)
        .range(1, 15)
        .sliderRange(1, 15)
        .visible(detectDeepslate::get)
        .build());

    private final Setting<Integer> cobbledDeepslateThreshold = sgDetection.add(new IntSetting.Builder()
        .name("cobbled-deepslate-threshold")
        .description("Min cobbled deepslate to flag chunk")
        .defaultValue(4)
        .range(1, 15)
        .sliderRange(1, 15)
        .visible(detectCobbledDeepslate::get)
        .build());

    private final Setting<Integer> rotatedDeepslateThreshold = sgDetection.add(new IntSetting.Builder()
        .name("rotated-threshold")
        .description("Min rotated deepslate to flag chunk")
        .defaultValue(3)
        .range(1, 20)
        .sliderRange(1, 20)
        .visible(detectRotatedDeepslate::get)
        .build());

    private final Setting<Integer> endStoneThreshold = sgDetection.add(new IntSetting.Builder()
        .name("end-stone-threshold")
        .description("Min end stone count to flag chunk")
        .defaultValue(2)
        .range(1, 15)
        .sliderRange(1, 15)
        .visible(detectEndStone::get)
        .build());

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

    private final Setting<SettingColor> endStoneBlockColor = sgBlockHighlight.add(new ColorSetting.Builder()
        .name("end-stone-color")
        .description("Color for end stone blocks")
        .defaultValue(new SettingColor(255, 255, 200, 200))
        .visible(highlightBlocks::get)
        .build());

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

    private final Setting<Mode> notificationMode = sgNotifications.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("How to notify when suspicious chunks are detected")
        .defaultValue(Mode.Both)
        .build());

    private final Setting<Boolean> playSound = sgNotifications.add(new BoolSetting.Builder()
        .name("sound-alerts")
        .description("Play sound when suspicious chunks or blocks are found")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> chatAlerts = sgNotifications.add(new BoolSetting.Builder()
        .name("chat-alerts")
        .description("Send chat notifications for suspicious chunks or blocks")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> trialChamberAlerts = sgNotifications.add(new BoolSetting.Builder()
        .name("trial-chamber-alerts")
        .description("Send chat notifications for trial chambers")
        .defaultValue(false)
        .build());

    private final Setting<Integer> maxAlerts = sgNotifications.add(new IntSetting.Builder()
        .name("max-alerts")
        .description("Max alerts per minute")
        .defaultValue(5)
        .range(1, 20)
        .sliderRange(1, 20)
        .build());

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
        super(GlazedAddon.esp, "ChunkFinder", "ChunkFinderV4");
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
        if (blockPos.getY() < 0 || blockPos.getY() > 128) return;

        BlockState newState = event.newState;
        if (isRelevantBlock(newState) || newState.isAir()) {
            ChunkPos chunkPos = new ChunkPos(blockPos);
            WorldChunk chunk = (WorldChunk) mc.world.getChunk(chunkPos.x, chunkPos.z);
            scheduleChunkScan(chunk);
        }
    }

    private boolean isRelevantBlock(BlockState state) {
        Block block = state.getBlock();
        return
            block == Blocks.DEEPSLATE ||
                block == Blocks.COBBLED_DEEPSLATE ||
                block == Blocks.POLISHED_DEEPSLATE ||
                block == Blocks.DEEPSLATE_BRICKS ||
                block == Blocks.DEEPSLATE_TILES ||
                block == Blocks.CHISELED_DEEPSLATE ||
                block == Blocks.END_STONE ||
                block == Blocks.WAXED_COPPER_BLOCK ||
                block == Blocks.WAXED_OXIDIZED_COPPER ||
                block == Blocks.TUFF_BRICKS;
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

    private void scheduleChunkScan(Chunk chunk) {
        if (!(chunk instanceof WorldChunk worldChunk)) return;
        if (activeScanCount.get() >= maxConcurrentScans.get()) return;

        Runnable scanTask = () -> {
            try {
                Thread.sleep(scanInterval.get() / 2);
                analyzeChunk(worldChunk);
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

            int minY = 0; // Hardcode minimum Y to 0
            int maxY = Math.min(chunk.getBottomY() + chunk.getHeight(), 128); // Hardcode maximum Y to 128

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
            if (section == null || section.isEmpty()) continue;

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

        // Count trial chamber blocks
        if (ignoreTrialChambers.get() && isTrialChamberBlock(state)) {
            analysis.trialChamberCount++;
        }

        // If ignoreExposed enabled, detect exposure (air or fluid) around the block
        boolean exposed = false;
        if (ignoreExposed.get()) {
            exposed = isExposedToAirOrFluid(blockPos);
        }

        // Detect suspicious blocks
        if (detectDeepslate.get() && isNormalDeepslate(state) && !exposed && !isInLargeDeepslateLine(blockPos, worldY)) {
            analysis.deepslateCount++;
            blockType = SuspiciousBlockType.DEEPSLATE;
        }

        if (detectRotatedDeepslate.get() && isRotatedDeepslateBlock(state) && !exposed) {
            analysis.rotatedDeepslateCount++;
            blockType = SuspiciousBlockType.ROTATED_DEEPSLATE;
        }

        if (detectCobbledDeepslate.get() && isCobbledDeepslate(state) && !exposed) {
            analysis.cobbledDeepslateCount++;
            blockType = SuspiciousBlockType.COBBLED_DEEPSLATE;
        }

        if (detectEndStone.get() && isEndStone(state) && mc.world.getRegistryKey() != World.END && !exposed) {
            analysis.endStoneCount++;
            blockType = SuspiciousBlockType.END_STONE;
        }

        // Add block to suspicious blocks map if it's suspicious and highlighting enabled
        if (blockType != null && highlightBlocks.get()) {
            suspiciousBlocks.put(blockPos, new SuspiciousBlock(blockType, System.currentTimeMillis()));
        }
    }

    private boolean isValidBlockPos(BlockPos pos) {
        return pos.getY() >= mc.world.getBottomY() && pos.getY() < mc.world.getHeight();
    }

    private boolean isExposedToAirOrFluid(BlockPos pos) {
        if (mc.world == null) return false;

        for (Direction dir : Direction.values()) {
            BlockPos offset = pos.offset(dir);
            if (!isValidBlockPos(offset)) continue;
            BlockState neighbor = mc.world.getBlockState(offset);
            if (neighbor.isAir()) return true;

            FluidState f = neighbor.getFluidState();
            if (f != null && !f.isEmpty()) return true;
        }
        return false;
    }

    private boolean isInLargeDeepslateLine(BlockPos pos, int worldY) {
        if (mc.world == null) return false;

        // Adjust threshold based on Y-level: stricter above deepslate layer
        final int lineThreshold = worldY > -8 ? 50 : 20;

        // Check X-axis
        int xCount = 1; // Count the current block
        // Forward (positive X)
        for (int i = 1; i < lineThreshold; i++) {
            BlockPos next = pos.offset(Direction.EAST, i);
            if (!isValidBlockPos(next) || !isNormalDeepslate(mc.world.getBlockState(next))) break;
            xCount++;
        }
        // Backward (negative X)
        for (int i = 1; i < lineThreshold; i++) {
            BlockPos prev = pos.offset(Direction.WEST, i);
            if (!isValidBlockPos(prev) || !isNormalDeepslate(mc.world.getBlockState(prev))) break;
            xCount++;
        }
        if (xCount >= lineThreshold) return true;

        // Check Z-axis
        int zCount = 1;
        // Forward (positive Z)
        for (int i = 1; i < lineThreshold; i++) {
            BlockPos next = pos.offset(Direction.SOUTH, i);
            if (!isValidBlockPos(next) || !isNormalDeepslate(mc.world.getBlockState(next))) break;
            zCount++;
        }
        // Backward (negative Z)
        for (int i = 1; i < lineThreshold; i++) {
            BlockPos prev = pos.offset(Direction.NORTH, i);
            if (!isValidBlockPos(prev) || !isNormalDeepslate(mc.world.getBlockState(prev))) break;
            zCount++;
        }
        if (zCount >= lineThreshold) return true;

        // Skip Y-axis check in deepslate levels (Y <= 0) to avoid over-filtering
        if (worldY > 0) {
            int yCount = 1;
            // Up (positive Y)
            for (int i = 1; i < lineThreshold; i++) {
                BlockPos up = pos.offset(Direction.UP, i);
                if (!isValidBlockPos(up) || !isNormalDeepslate(mc.world.getBlockState(up))) break;
                yCount++;
            }
            // Down (negative Y)
            for (int i = 1; i < lineThreshold; i++) {
                BlockPos down = pos.offset(Direction.DOWN, i);
                if (!isValidBlockPos(down) || !isNormalDeepslate(mc.world.getBlockState(down))) break;
                yCount++;
            }
            if (yCount >= lineThreshold) return true;
        }

        return false;
    }

    private void evaluateChunk(ChunkPos pos, ChunkAnalysis analysis) {
        // Check for trial chamber
        if (ignoreTrialChambers.get() && analysis.trialChamberCount >= trialChamberThreshold.get()) {
            if (trialChamberAlerts.get() && mc.player != null) {
                String message = String.format("ChunkFinder [%d, %d] - Trial chamber detected - Copper/Tuff blocks: %d",
                    pos.x, pos.z, analysis.trialChamberCount);
                notifyTrialChamber(message);
            }
            flaggedChunks.remove(pos);
            notificationTimes.remove(pos);
            return;
        }

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
            reasons.append("RotatedDeepslate[").append(analysis.rotatedDeepslateCount).append("] ");
        }

        if (detectEndStone.get() && analysis.endStoneCount >= endStoneThreshold.get()) {
            suspicious = true;
            reasons.append("EndStone[").append(analysis.endStoneCount).append("] ");
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

    private boolean isNormalDeepslate(BlockState state) {
        Block block = state.getBlock();
        if (block != Blocks.DEEPSLATE || !state.contains(Properties.AXIS)) return false;
        Direction.Axis axis = state.get(Properties.AXIS);
        return axis == Direction.Axis.Y;
    }

    private boolean isCobbledDeepslate(BlockState state) {
        return state.getBlock() == Blocks.COBBLED_DEEPSLATE;
    }

    private boolean isRotatedDeepslateBlock(BlockState state) {
        Block block = state.getBlock();
        if (block != Blocks.DEEPSLATE || !state.contains(Properties.AXIS)) return false;
        Direction.Axis axis = state.get(Properties.AXIS);
        return axis != Direction.Axis.Y;
    }

    private boolean isEndStone(BlockState state) {
        return state.getBlock() == Blocks.END_STONE;
    }

    private boolean isTrialChamberBlock(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.WAXED_COPPER_BLOCK ||
            block == Blocks.WAXED_OXIDIZED_COPPER ||
            block == Blocks.TUFF_BRICKS;
    }

    private void notifyChunkFound(ChunkPos pos, String details) {
        long now = System.currentTimeMillis();

        if (recentAlerts.size() >= maxAlerts.get()) return;

        Long lastNotification = notificationTimes.get(pos);
        if (lastNotification != null && now - lastNotification < 45000) return;

        String message = String.format("ChunkFinder [%d, %d] - Suspicious chunk detected - %s", pos.x, pos.z, details);

        mc.execute(() -> {
            switch (notificationMode.get()) {
                case Chat -> {
                    if (chatAlerts.get() && mc.player != null) {
                        mc.player.sendMessage(Text.literal(message), false);
                    }
                }
                case Toast -> {
                    mc.getToastManager().add(new MeteorToast(Items.CHEST, "ChunkFinder", message));
                }
                case Both -> {
                    if (chatAlerts.get() && mc.player != null) {
                        mc.player.sendMessage(Text.literal(message), false);
                    }
                    mc.getToastManager().add(new MeteorToast(Items.CHEST, "ChunkFinder", message));
                }
            }

            if (playSound.get()) {
                mc.getSoundManager().play(PositionedSoundInstance.master(
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f));
            }

            recentAlerts.offer(now);
            notificationTimes.put(pos, now);
        });
    }

    private void notifyTrialChamber(String message) {
        long now = System.currentTimeMillis();

        if (recentAlerts.size() >= maxAlerts.get()) return;

        String[] parts = message.split(" - ", 2);
        String coordsPart = parts[0].replace("ChunkFinder ", "");
        String detailsPart = parts.length > 1 ? parts[1] : "";

        mc.execute(() -> {
            switch (notificationMode.get()) {
                case Chat -> {
                    if (trialChamberAlerts.get() && mc.player != null) {
                        mc.player.sendMessage(Text.literal(message), false);
                    }
                }
                case Toast -> {
                    mc.getToastManager().add(new MeteorToast(Items.CHEST, "ChunkFinder", String.format("%s - %s", coordsPart, detailsPart)));
                }
                case Both -> {
                    if (trialChamberAlerts.get() && mc.player != null) {
                        mc.player.sendMessage(Text.literal(message), false);
                    }
                    mc.getToastManager().add(new MeteorToast(Items.CHEST, "ChunkFinder", String.format("%s - %s", coordsPart, detailsPart)));
                }
            }

            if (playSound.get()) {
                mc.getSoundManager().play(PositionedSoundInstance.master(
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f));
            }

            recentAlerts.offer(now);
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

        suspiciousBlocks.entrySet().removeIf(entry -> {
            BlockPos blockPos = entry.getKey();
            double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(blockPos));
            return distance > viewDist * 16 + 80;
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
        if (highlightBlocks.get()) {
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
    }

    private void renderSuspiciousBlocks(Render3DEvent event) {
        int rendered = 0;

        for (Map.Entry<BlockPos, SuspiciousBlock> entry : suspiciousBlocks.entrySet()) {
            if (rendered >= maxBlocksToRender.get()) break;

            BlockPos pos = entry.getKey();
            SuspiciousBlock suspiciousBlock = entry.getValue();

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
            case END_STONE -> new Color(endStoneBlockColor.get());
            default -> null;
        };
    }

    @Override
    public String getInfoString() {
        if (highlightBlocks.get()) {
            return String.format("C:%d B:%d", flaggedChunks.size(), suspiciousBlocks.size());
        }
        return String.valueOf(flaggedChunks.size());
    }

    private static class ChunkAnalysis {
        int deepslateCount = 0;
        int cobbledDeepslateCount = 0;
        int rotatedDeepslateCount = 0;
        int endStoneCount = 0;
        int trialChamberCount = 0;
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
        END_STONE
    }
}
