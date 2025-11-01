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

public class PistonESP extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<SettingColor> pistonColor = sgGeneral.add(new ColorSetting.Builder()
        .name("piston-color")
        .description("Regular piston box color")
        .defaultValue(new SettingColor(255, 100, 100, 100)) // Red color for pistons
        .build());

    private final Setting<SettingColor> stickyPistonColor = sgGeneral.add(new ColorSetting.Builder()
        .name("sticky-piston-color")
        .description("Sticky piston box color")
        .defaultValue(new SettingColor(100, 255, 100, 100)) // Green color for sticky pistons
        .build());

    private final Setting<ShapeMode> pistonShapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Piston box render mode")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to pistons")
        .defaultValue(false)
        .build());

    private final Setting<SettingColor> tracerColor = sgGeneral.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Piston tracer color")
        .defaultValue(new SettingColor(255, 255, 255, 200))
        .visible(tracers::get)
        .build());

    private final Setting<Boolean> pistonChat = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Announce pistons in chat")
        .defaultValue(true)
        .build());

    private final SettingGroup sgFiltering = settings.createGroup("Piston Types");

    private final Setting<Boolean> includeRegularPistons = sgFiltering.add(new BoolSetting.Builder()
        .name("regular-pistons")
        .description("Include regular pistons")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> includeStickyPistons = sgFiltering.add(new BoolSetting.Builder()
        .name("sticky-pistons")
        .description("Include sticky pistons")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> includeExtendedPistons = sgFiltering.add(new BoolSetting.Builder()
        .name("extended-pistons")
        .description("Include extended piston heads")
        .defaultValue(false)
        .build());

    private final SettingGroup sgRange = settings.createGroup("Range");

    private final Setting<Integer> minY = sgRange.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level to scan for pistons")
        .defaultValue(-64)
        .min(-64)
        .max(128)
        .sliderRange(-64, 128)
        .build());

    private final Setting<Integer> maxY = sgRange.add(new IntSetting.Builder()
        .name("max-y")
        .description("Maximum Y level to scan for pistons")
        .defaultValue(320)
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

    // Thread-safe collections
    private final Set<BlockPos> regularPistonPositions = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> stickyPistonPositions = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> pistonHeadPositions = ConcurrentHashMap.newKeySet();

    // Threading
    private ExecutorService threadPool;

    public PistonESP() {
        super(GlazedAddon.esp, "PistonESP", "ESP for pistons and sticky pistons with threading and tracer support.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null) return;

        // Initialize thread pool
        if (useThreading.get()) {
            threadPool = Executors.newFixedThreadPool(threadPoolSize.get());
        }

        regularPistonPositions.clear();
        stickyPistonPositions.clear();
        pistonHeadPositions.clear();

        if (useThreading.get()) {
            // Scan chunks asynchronously
            for (Chunk chunk : Utils.chunks()) {
                if (chunk instanceof WorldChunk worldChunk) {
                    threadPool.submit(() -> scanChunkForPistons(worldChunk));
                }
            }
        } else {
            // Scan chunks synchronously
            for (Chunk chunk : Utils.chunks()) {
                if (chunk instanceof WorldChunk worldChunk) scanChunkForPistons(worldChunk);
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

        regularPistonPositions.clear();
        stickyPistonPositions.clear();
        pistonHeadPositions.clear();
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
            threadPool.submit(() -> scanChunkForPistons(event.chunk()));
        } else {
            scanChunkForPistons(event.chunk());
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        BlockPos pos = event.pos;
        BlockState state = event.newState;

        // Create a task for block update processing
        Runnable updateTask = () -> {
            PistonType pistonType = getPistonType(state, pos.getY());

            // Remove from all collections first
            regularPistonPositions.remove(pos);
            stickyPistonPositions.remove(pos);
            pistonHeadPositions.remove(pos);

            if (pistonType != PistonType.NONE) {
                boolean wasAdded = false;
                String blockType = "";

                switch (pistonType) {
                    case REGULAR_PISTON:
                        wasAdded = regularPistonPositions.add(pos);
                        blockType = "Regular Piston";
                        break;
                    case STICKY_PISTON:
                        wasAdded = stickyPistonPositions.add(pos);
                        blockType = "Sticky Piston";
                        break;
                    case PISTON_HEAD:
                        wasAdded = pistonHeadPositions.add(pos);
                        blockType = "Piston Head";
                        break;
                }

                if (wasAdded && pistonChat.get() && (!useThreading.get() || !limitChatSpam.get())) {
                    info("§c[§6Piston ESP§c] §6" + blockType + " at " + pos.toShortString());
                }
            }
        };

        if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
            threadPool.submit(updateTask);
        } else {
            updateTask.run();
        }
    }

    private void scanChunkForPistons(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        int xStart = cpos.getStartX();
        int zStart = cpos.getStartZ();
        int yMin = Math.max(chunk.getBottomY(), minY.get());
        int yMax = Math.min(chunk.getBottomY() + chunk.getHeight(), maxY.get());

        Set<BlockPos> chunkRegularPistons = new HashSet<>();
        Set<BlockPos> chunkStickyPistons = new HashSet<>();
        Set<BlockPos> chunkPistonHeads = new HashSet<>();
        int foundCount = 0;

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = yMin; y < yMax; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    PistonType pistonType = getPistonType(state, y);

                    if (pistonType != PistonType.NONE) {
                        switch (pistonType) {
                            case REGULAR_PISTON:
                                chunkRegularPistons.add(pos);
                                break;
                            case STICKY_PISTON:
                                chunkStickyPistons.add(pos);
                                break;
                            case PISTON_HEAD:
                                chunkPistonHeads.add(pos);
                                break;
                        }
                        foundCount++;
                    }
                }
            }
        }

        // Remove old piston positions from this chunk
        regularPistonPositions.removeIf(pos -> {
            ChunkPos blockChunk = new ChunkPos(pos);
            return blockChunk.equals(cpos) && !chunkRegularPistons.contains(pos);
        });
        stickyPistonPositions.removeIf(pos -> {
            ChunkPos blockChunk = new ChunkPos(pos);
            return blockChunk.equals(cpos) && !chunkStickyPistons.contains(pos);
        });
        pistonHeadPositions.removeIf(pos -> {
            ChunkPos blockChunk = new ChunkPos(pos);
            return blockChunk.equals(cpos) && !chunkPistonHeads.contains(pos);
        });

        // Add new piston positions
        int newBlocks = 0;
        for (BlockPos pos : chunkRegularPistons) {
            if (regularPistonPositions.add(pos)) {
                newBlocks++;
            }
        }
        for (BlockPos pos : chunkStickyPistons) {
            if (stickyPistonPositions.add(pos)) {
                newBlocks++;
            }
        }
        for (BlockPos pos : chunkPistonHeads) {
            if (pistonHeadPositions.add(pos)) {
                newBlocks++;
            }
        }

        // Provide chunk-level feedback to reduce spam
        if (pistonChat.get() && foundCount > 0) {
            if (useThreading.get() && limitChatSpam.get()) {
                if (newBlocks > 0) {
                    info("§c[§6Piston ESP§c] §6Chunk " + cpos.x + "," + cpos.z + "§c: §6" + newBlocks + " new pistons found");
                }
            } else {
                // Individual block notifications for non-threaded mode
                for (BlockPos pos : chunkRegularPistons) {
                    if (!regularPistonPositions.contains(pos)) {
                        info("§c[§6Piston ESP§c] §6Regular Piston at " + pos.toShortString());
                    }
                }
                for (BlockPos pos : chunkStickyPistons) {
                    if (!stickyPistonPositions.contains(pos)) {
                        info("§c[§6Piston ESP§c] §6Sticky Piston at " + pos.toShortString());
                    }
                }
                for (BlockPos pos : chunkPistonHeads) {
                    if (!pistonHeadPositions.contains(pos)) {
                        info("§c[§6Piston ESP§c] §6Piston Head at " + pos.toShortString());
                    }
                }
            }
        }
    }

    private enum PistonType {
        NONE,
        REGULAR_PISTON,
        STICKY_PISTON,
        PISTON_HEAD
    }

    private PistonType getPistonType(BlockState state, int y) {
        if (y < minY.get() || y > maxY.get()) return PistonType.NONE;

        if (includeRegularPistons.get() && state.isOf(Blocks.PISTON)) {
            return PistonType.REGULAR_PISTON;
        }

        if (includeStickyPistons.get() && state.isOf(Blocks.STICKY_PISTON)) {
            return PistonType.STICKY_PISTON;
        }

        if (includeExtendedPistons.get() && state.isOf(Blocks.PISTON_HEAD)) {
            return PistonType.PISTON_HEAD;
        }

        return PistonType.NONE;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        // Use interpolated position for smooth movement
        Vec3d playerPos = mc.player.getLerpedPos(event.tickDelta);
        Color regularPistonSide = new Color(pistonColor.get());
        Color regularPistonOutline = new Color(pistonColor.get());
        Color stickyPistonSide = new Color(stickyPistonColor.get());
        Color stickyPistonOutline = new Color(stickyPistonColor.get());
        Color tracerColorValue = new Color(tracerColor.get());

        // Get render distance
        int renderDistance = mc.options.getViewDistance().getValue() * 16;

        // Render regular pistons
        for (BlockPos pos : regularPistonPositions) {
            // Check if within render distance - this applies to both ESP box and tracers
            if (!isWithinRenderDistance(playerPos, pos, renderDistance)) continue;

            // Render ESP box
            event.renderer.box(pos, regularPistonSide, regularPistonOutline, pistonShapeMode.get(), 0);

            // Render tracer if enabled
            if (tracers.get()) {
                renderTracer(event, playerPos, pos, tracerColorValue);
            }
        }

        // Render sticky pistons
        for (BlockPos pos : stickyPistonPositions) {
            // Check if within render distance - this applies to both ESP box and tracers
            if (!isWithinRenderDistance(playerPos, pos, renderDistance)) continue;

            // Render ESP box
            event.renderer.box(pos, stickyPistonSide, stickyPistonOutline, pistonShapeMode.get(), 0);

            // Render tracer if enabled
            if (tracers.get()) {
                renderTracer(event, playerPos, pos, tracerColorValue);
            }
        }

        // Render piston heads with a mix of both colors
        Color pistonHeadSide = new Color((pistonColor.get().r + stickyPistonColor.get().r) / 2,
            (pistonColor.get().g + stickyPistonColor.get().g) / 2,
            (pistonColor.get().b + stickyPistonColor.get().b) / 2,
            pistonColor.get().a);
        Color pistonHeadOutline = new Color(pistonHeadSide);

        for (BlockPos pos : pistonHeadPositions) {
            // Check if within render distance - this applies to both ESP box and tracers
            if (!isWithinRenderDistance(playerPos, pos, renderDistance)) continue;

            // Render ESP box
            event.renderer.box(pos, pistonHeadSide, pistonHeadOutline, pistonShapeMode.get(), 0);

            // Render tracer if enabled
            if (tracers.get()) {
                renderTracer(event, playerPos, pos, tracerColorValue);
            }
        }
    }

    private boolean isWithinRenderDistance(Vec3d playerPos, BlockPos blockPos, int renderDistance) {
        double dx = playerPos.x - blockPos.getX() - 0.5;
        double dz = playerPos.z - blockPos.getZ() - 0.5;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        return horizontalDistance <= renderDistance;
    }

    private void renderTracer(Render3DEvent event, Vec3d playerPos, BlockPos pos, Color tracerColorValue) {
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
