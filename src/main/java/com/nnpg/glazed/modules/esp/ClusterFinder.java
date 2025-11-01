package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClusterFinder extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<SettingColor> clusterColor = sgGeneral.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Amethyst cluster box color")
        .defaultValue(new SettingColor(147, 0, 211, 100)) // Purple color for amethyst
        .build());

    private final Setting<ShapeMode> clusterShapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Amethyst cluster box render mode")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to amethyst clusters")
        .defaultValue(false)
        .build());

    private final Setting<SettingColor> tracerColor = sgGeneral.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Amethyst cluster tracer color")
        .defaultValue(new SettingColor(147, 0, 211, 200))
        .visible(tracers::get)
        .build());

    private final Setting<Boolean> clusterChat = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Announce amethyst clusters in chat")
        .defaultValue(true)
        .build());

    private final SettingGroup sgFiltering = settings.createGroup("Cluster Types");

    private final Setting<Boolean> includeSmallBuds = sgFiltering.add(new BoolSetting.Builder()
        .name("small-buds")
        .description("Include small amethyst buds")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> includeMediumBuds = sgFiltering.add(new BoolSetting.Builder()
        .name("medium-buds")
        .description("Include medium amethyst buds")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> includeLargeBuds = sgFiltering.add(new BoolSetting.Builder()
        .name("large-buds")
        .description("Include large amethyst buds")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> includeClusters = sgFiltering.add(new BoolSetting.Builder()
        .name("clusters")
        .description("Include amethyst clusters")
        .defaultValue(true)
        .build());

    private final SettingGroup sgRange = settings.createGroup("Range");

    private final Setting<Integer> minY = sgRange.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level to scan for amethyst clusters")
        .defaultValue(-64)
        .min(-64)
        .max(128)
        .sliderRange(-64, 128)
        .build());

    private final Setting<Integer> maxY = sgRange.add(new IntSetting.Builder()
        .name("max-y")
        .description("Maximum Y level to scan for amethyst clusters")
        .defaultValue(128)
        .min(-64)
        .max(320)
        .sliderRange(-64, 320)
        .build());

    private final SettingGroup sgThreading = settings.createGroup("Threading");

    private final Setting<Boolean> useThreading = sgThreading.add(new BoolSetting.Builder()
        .name("enable-threading")
        .description("Use multi-threading for chunk scanning (better performance)")
        .defaultValue(true)
        .build());

    private final Setting<Integer> threadPoolSize = sgThreading.add(new IntSetting.Builder()
        .name("thread-pool-size")
        .description("Number of threads to use for scanning")
        .defaultValue(2)
        .min(1)
        .max(8)
        .sliderRange(1, 8)
        .visible(useThreading::get)
        .build());

    private final Setting<Boolean> limitChatSpam = sgThreading.add(new BoolSetting.Builder()
        .name("limit-chat-spam")
        .description("Reduce chat spam when using threading")
        .defaultValue(true)
        .visible(useThreading::get)
        .build());

    // Thread-safe collection
    private final Set<BlockPos> clusterPositions = ConcurrentHashMap.newKeySet();

    // Threading
    private ExecutorService threadPool;

    public ClusterFinder() {
        super(GlazedAddon.esp, "ClusterFinder", "ESP for amethyst clusters and buds with threading and tracer support.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null) return;

        // Initialize thread pool
        if (useThreading.get()) {
            threadPool = Executors.newFixedThreadPool(threadPoolSize.get());
        }

        clusterPositions.clear();

        if (useThreading.get()) {
            // Scan chunks asynchronously
            for (Chunk chunk : Utils.chunks()) {
                if (chunk instanceof WorldChunk worldChunk) {
                    threadPool.submit(() -> scanChunkForClusters(worldChunk));
                }
            }
        } else {
            // Scan chunks synchronously
            for (Chunk chunk : Utils.chunks()) {
                if (chunk instanceof WorldChunk worldChunk) scanChunkForClusters(worldChunk);
            }
        }
    }

    @Override
    public void onDeactivate() {
        // Shutdown thread pool
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
            threadPool = null;
        }

        clusterPositions.clear();
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
            threadPool.submit(() -> scanChunkForClusters(event.chunk()));
        } else {
            scanChunkForClusters(event.chunk());
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        BlockPos pos = event.pos;
        BlockState state = event.newState;

        // Create a task for block update processing
        Runnable updateTask = () -> {
            boolean isCluster = isAmethystCluster(state, pos.getY());
            if (isCluster) {
                boolean wasAdded = clusterPositions.add(pos);
                if (wasAdded && clusterChat.get() && (!useThreading.get() || !limitChatSpam.get())) {
                    String blockType = getClusterTypeName(state);
                    info("§5[§dCluster Finder§5] §dAmethyst§5: §d" + blockType + " at " + pos.toShortString());
                }
            } else {
                clusterPositions.remove(pos);
            }
        };

        if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
            threadPool.submit(updateTask);
        } else {
            updateTask.run();
        }
    }

    private void scanChunkForClusters(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        int xStart = cpos.getStartX();
        int zStart = cpos.getStartZ();
        int yMin = Math.max(chunk.getBottomY(), minY.get());
        int yMax = Math.min(chunk.getBottomY() + chunk.getHeight(), maxY.get());

        Set<BlockPos> chunkClusters = new HashSet<>();
        int foundCount = 0;

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = yMin; y < yMax; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (isAmethystCluster(state, y)) {
                        chunkClusters.add(pos);
                        foundCount++;
                    }
                }
            }
        }

        // Remove old cluster positions from this chunk
        clusterPositions.removeIf(pos -> {
            ChunkPos blockChunk = new ChunkPos(pos);
            return blockChunk.equals(cpos) && !chunkClusters.contains(pos);
        });

        // Add new cluster positions
        int newBlocks = 0;
        for (BlockPos pos : chunkClusters) {
            if (clusterPositions.add(pos)) {
                newBlocks++;
            }
        }

        // Provide chunk-level feedback to reduce spam
        if (clusterChat.get() && foundCount > 0) {
            if (useThreading.get() && limitChatSpam.get()) {
                if (newBlocks > 0) {
                    info("§5[§dCluster Finder§5] §dChunk " + cpos.x + "," + cpos.z + "§5: §d" + newBlocks + " new amethyst clusters found");
                }
            } else {
                for (BlockPos pos : chunkClusters) {
                    if (!clusterPositions.contains(pos)) {
                        BlockState state = chunk.getBlockState(pos);
                        String blockType = getClusterTypeName(state);
                        info("§5[§dCluster Finder§5] §dAmethyst§5: §d" + blockType + " at " + pos.toShortString());
                    }
                }
            }
        }
    }

    private boolean isAmethystCluster(BlockState state, int y) {
        if (y < minY.get() || y > maxY.get()) return false;

        if (includeSmallBuds.get() && state.isOf(Blocks.SMALL_AMETHYST_BUD)) {
            return true;
        }

        if (includeMediumBuds.get() && state.isOf(Blocks.MEDIUM_AMETHYST_BUD)) {
            return true;
        }

        if (includeLargeBuds.get() && state.isOf(Blocks.LARGE_AMETHYST_BUD)) {
            return true;
        }

        if (includeClusters.get() && state.isOf(Blocks.AMETHYST_CLUSTER)) {
            return true;
        }

        return false;
    }

    private String getClusterTypeName(BlockState state) {
        if (state.isOf(Blocks.SMALL_AMETHYST_BUD)) {
            return "Small Amethyst Bud";
        } else if (state.isOf(Blocks.MEDIUM_AMETHYST_BUD)) {
            return "Medium Amethyst Bud";
        } else if (state.isOf(Blocks.LARGE_AMETHYST_BUD)) {
            return "Large Amethyst Bud";
        } else if (state.isOf(Blocks.AMETHYST_CLUSTER)) {
            return "Amethyst Cluster";
        }
        return "Amethyst Block";
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        // Use interpolated position for smooth movement
        Vec3d playerPos = mc.player.getLerpedPos(event.tickDelta);
        Color side = new Color(clusterColor.get());
        Color outline = new Color(clusterColor.get());
        Color tracerColorValue = new Color(tracerColor.get());

        for (BlockPos pos : clusterPositions) {
            // Render ESP box
            event.renderer.box(pos, side, outline, clusterShapeMode.get(), 0);

            // Render tracer if enabled
            if (tracers.get()) {
                Vec3d blockCenter = Vec3d.ofCenter(pos);

                // Start tracer from slightly in front of camera to make it visible in first person
                Vec3d startPos;
                if (mc.options.getPerspective().isFirstPerson()) {
                    // First person: start tracer slightly forward from camera
                    Vec3d lookDirection = mc.player.getRotationVector();
                    startPos = new Vec3d(
                        playerPos.x + lookDirection.x * 0.5,
                        playerPos.y + mc.player.getEyeHeight(mc.player.getPose()) + lookDirection.y * 0.5,
                        playerPos.z + lookDirection.z * 0.5
                    );
                } else {
                    // Third person: use normal eye position
                    startPos = new Vec3d(
                        playerPos.x,
                        playerPos.y + mc.player.getEyeHeight(mc.player.getPose()),
                        playerPos.z
                    );
                }

                event.renderer.line(startPos.x, startPos.y, startPos.z,
                    blockCenter.x, blockCenter.y, blockCenter.z, tracerColorValue);
            }
        }
    }
}
