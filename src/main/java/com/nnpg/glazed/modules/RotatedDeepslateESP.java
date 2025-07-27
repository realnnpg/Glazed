package com.nnpg.glazed.modules;

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
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RotatedDeepslateESP extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<SettingColor> deepslateColor = sgGeneral.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Rotated deepslate box color")
        .defaultValue(new SettingColor(255, 0, 255, 100))
        .build());

    private final Setting<ShapeMode> deepslateShapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Rotated deepslate box render mode")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to rotated deepslate blocks")
        .defaultValue(false)
        .build());

    private final Setting<SettingColor> tracerColor = sgGeneral.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Rotated deepslate tracer color")
        .defaultValue(new SettingColor(255, 0, 255, 200))
        .visible(tracers::get)
        .build());

    private final Setting<Boolean> deepslateChat = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Announce rotated deepslate in chat")
        .defaultValue(true)
        .build());

    private final SettingGroup sgFiltering = settings.createGroup("Block Types");

    private final Setting<Boolean> includeRegularDeepslate = sgFiltering.add(new BoolSetting.Builder()
        .name("regular-deepslate")
        .description("Include rotated regular deepslate blocks")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> includePolishedDeepslate = sgFiltering.add(new BoolSetting.Builder()
        .name("polished-deepslate")
        .description("Include rotated polished deepslate blocks")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> includeDeepslateBricks = sgFiltering.add(new BoolSetting.Builder()
        .name("deepslate-bricks")
        .description("Include rotated deepslate bricks")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> includeDeepslateTiles = sgFiltering.add(new BoolSetting.Builder()
        .name("deepslate-tiles")
        .description("Include rotated deepslate tiles")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> includeChiseledDeepslate = sgFiltering.add(new BoolSetting.Builder()
        .name("chiseled-deepslate")
        .description("Include rotated chiseled deepslate")
        .defaultValue(true)
        .build());

    private final SettingGroup sgRange = settings.createGroup("Range");

    private final Setting<Integer> minY = sgRange.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level to scan for rotated deepslate")
        .defaultValue(-64)
        .min(-64)
        .max(128)
        .sliderRange(-64, 128)
        .build());

    private final Setting<Integer> maxY = sgRange.add(new IntSetting.Builder()
        .name("max-y")
        .description("Maximum Y level to scan for rotated deepslate")
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
    private final Set<BlockPos> rotatedDeepslatePositions = ConcurrentHashMap.newKeySet();

    // Threading
    private ExecutorService threadPool;

    public RotatedDeepslateESP() {
        super(GlazedAddon.esp, "RotatedDeepslateESP", "ESP for rotated deepslate blocks with threading and tracer support.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null) return;

        // Initialize thread pool
        if (useThreading.get()) {
            threadPool = Executors.newFixedThreadPool(threadPoolSize.get());
        }

        rotatedDeepslatePositions.clear();

        if (useThreading.get()) {
            // Scan chunks asynchronously
            for (Chunk chunk : Utils.chunks()) {
                if (chunk instanceof WorldChunk worldChunk) {
                    threadPool.submit(() -> scanChunkForRotatedDeepslate(worldChunk));
                }
            }
        } else {
            // Scan chunks synchronously
            for (Chunk chunk : Utils.chunks()) {
                if (chunk instanceof WorldChunk worldChunk) scanChunkForRotatedDeepslate(worldChunk);
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

        rotatedDeepslatePositions.clear();
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
            threadPool.submit(() -> scanChunkForRotatedDeepslate(event.chunk()));
        } else {
            scanChunkForRotatedDeepslate(event.chunk());
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        BlockPos pos = event.pos;
        BlockState state = event.newState;

        // Create a task for block update processing
        Runnable updateTask = () -> {
            boolean isRotated = isRotatedDeepslate(state, pos.getY());
            if (isRotated) {
                boolean wasAdded = rotatedDeepslatePositions.add(pos);
                if (wasAdded && deepslateChat.get() && (!useThreading.get() || !limitChatSpam.get())) {
                    String blockType = getBlockTypeName(state);
                    info("§5[§dRotated Deepslate§5] §dRotated Deepslate§5: §d" + blockType + " at " + pos.toShortString());
                }
            } else {
                rotatedDeepslatePositions.remove(pos);
            }
        };

        if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
            threadPool.submit(updateTask);
        } else {
            updateTask.run();
        }
    }

    private void scanChunkForRotatedDeepslate(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        int xStart = cpos.getStartX();
        int zStart = cpos.getStartZ();
        int yMin = Math.max(chunk.getBottomY(), minY.get());
        int yMax = Math.min(chunk.getBottomY() + chunk.getHeight(), maxY.get());

        Set<BlockPos> chunkRotatedDeepslate = new HashSet<>();
        int foundCount = 0;

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = yMin; y < yMax; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (isRotatedDeepslate(state, y)) {
                        chunkRotatedDeepslate.add(pos);
                        foundCount++;
                    }
                }
            }
        }

        // Remove old rotated deepslate positions from this chunk
        rotatedDeepslatePositions.removeIf(pos -> {
            ChunkPos blockChunk = new ChunkPos(pos);
            return blockChunk.equals(cpos) && !chunkRotatedDeepslate.contains(pos);
        });

        // Add new rotated deepslate positions
        int newBlocks = 0;
        for (BlockPos pos : chunkRotatedDeepslate) {
            if (rotatedDeepslatePositions.add(pos)) {
                newBlocks++;
            }
        }

        // Provide chunk-level feedback to reduce spam
        if (deepslateChat.get() && foundCount > 0) {
            if (useThreading.get() && limitChatSpam.get()) {
                if (newBlocks > 0) {
                    info("§5[§dRotated Deepslate§5] §dChunk " + cpos.x + "," + cpos.z + "§5: §d" + newBlocks + " new rotated deepslate blocks found");
                }
            } else {
                for (BlockPos pos : chunkRotatedDeepslate) {
                    if (!rotatedDeepslatePositions.contains(pos)) {
                        BlockState state = chunk.getBlockState(pos);
                        String blockType = getBlockTypeName(state);
                        info("§5[§dRotated Deepslate§5] §dRotated Deepslate§5: §d" + blockType + " at " + pos.toShortString());
                    }
                }
            }
        }
    }

    private boolean isRotatedDeepslate(BlockState state, int y) {
        if (y < minY.get() || y > maxY.get()) return false;

        if (!state.contains(Properties.AXIS)) return false;

        Direction.Axis axis = state.get(Properties.AXIS);
        if (axis == Direction.Axis.Y) return false; // Not rotated if on Y axis

        // Check for regular deepslate blocks that are rotated (non-Y axis)
        if (includeRegularDeepslate.get() && state.isOf(Blocks.DEEPSLATE)) {
            return true;
        }

        // Also check for polished deepslate
        if (includePolishedDeepslate.get() && state.isOf(Blocks.POLISHED_DEEPSLATE)) {
            return true;
        }

        // Check for deepslate bricks
        if (includeDeepslateBricks.get() && state.isOf(Blocks.DEEPSLATE_BRICKS)) {
            return true;
        }

        // Check for deepslate tiles
        if (includeDeepslateTiles.get() && state.isOf(Blocks.DEEPSLATE_TILES)) {
            return true;
        }

        // Check for chiseled deepslate
        if (includeChiseledDeepslate.get() && state.isOf(Blocks.CHISELED_DEEPSLATE)) {
            return true;
        }

        return false;
    }

    private String getBlockTypeName(BlockState state) {
        if (state.isOf(Blocks.DEEPSLATE)) {
            return "Rotated Deepslate";
        } else if (state.isOf(Blocks.POLISHED_DEEPSLATE)) {
            return "Rotated Polished Deepslate";
        } else if (state.isOf(Blocks.DEEPSLATE_BRICKS)) {
            return "Rotated Deepslate Bricks";
        } else if (state.isOf(Blocks.DEEPSLATE_TILES)) {
            return "Rotated Deepslate Tiles";
        } else if (state.isOf(Blocks.CHISELED_DEEPSLATE)) {
            return "Rotated Chiseled Deepslate";
        }
        return "Rotated Deepslate Block";
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d playerPos = mc.player.getPos();
        Color side = new Color(deepslateColor.get());
        Color outline = new Color(deepslateColor.get());
        Color tracerColorValue = new Color(tracerColor.get());

        for (BlockPos pos : rotatedDeepslatePositions) {
            // Render ESP box
            event.renderer.box(pos, side, outline, deepslateShapeMode.get(), 0);

            // Render tracer if enabled
            if (tracers.get()) {
                Vec3d blockCenter = Vec3d.ofCenter(pos);
                event.renderer.line(playerPos.x, playerPos.y, playerPos.z,
                    blockCenter.x, blockCenter.y, blockCenter.z, tracerColorValue);
            }
        }
    }
}
