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
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
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

public class DeepslateESP extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<SettingColor> deepslateColor = sgGeneral.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Deepslate box color")
        .defaultValue(new SettingColor(0, 200, 255, 100))
        .build());

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Deepslate box render mode")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to deepslate blocks")
        .defaultValue(false)
        .build());

    private final Setting<SettingColor> tracerColor = sgGeneral.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Deepslate tracer color")
        .defaultValue(new SettingColor(0, 200, 255, 200))
        .visible(tracers::get)
        .build());

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Announce deepslate in chat")
        .defaultValue(true)
        .build());

    private final SettingGroup sgFiltering = settings.createGroup("Filtering");

    private final Setting<Integer> minY = sgFiltering.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level to scan for deepslate")
        .defaultValue(8)
        .min(-64)
        .max(128)
        .sliderRange(-64, 128)
        .build());

    private final Setting<Integer> maxY = sgFiltering.add(new IntSetting.Builder()
        .name("max-y")
        .description("Maximum Y level to scan for deepslate")
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
    private final Set<BlockPos> deepslatePositions = ConcurrentHashMap.newKeySet();

    // Threading
    private ExecutorService threadPool;

    public DeepslateESP() {
        super(GlazedAddon.esp, "DeepslateESP", "ESP for deepslate blocks with threading and tracer support.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null) return;

        // Initialize thread pool
        if (useThreading.get()) {
            threadPool = Executors.newFixedThreadPool(threadPoolSize.get());
        }

        deepslatePositions.clear();

        if (useThreading.get()) {
            // Scan chunks asynchronously
            for (Chunk chunk : Utils.chunks()) {
                if (chunk instanceof WorldChunk worldChunk) {
                    threadPool.submit(() -> scanChunk(worldChunk));
                }
            }
        } else {
            // Scan chunks synchronously
            for (Chunk chunk : Utils.chunks()) {
                if (chunk instanceof WorldChunk worldChunk) scanChunk(worldChunk);
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

        deepslatePositions.clear();
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
            threadPool.submit(() -> scanChunk(event.chunk()));
        } else {
            scanChunk(event.chunk());
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        BlockPos pos = event.pos;
        BlockState state = event.newState;

        // Create a task for block update processing
        Runnable updateTask = () -> {
            boolean isDeepslate = isDeepslateInRange(state, pos.getY());
            if (isDeepslate) {
                boolean wasAdded = deepslatePositions.add(pos);
                if (wasAdded && chatFeedback.get() && (!useThreading.get() || !limitChatSpam.get())) {
                    info("§5[§dDeepslateESP§5] §bDeepslateESP§5: §bDeepslate at " + pos.toShortString());
                }
            } else {
                deepslatePositions.remove(pos);
            }
        };

        if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
            threadPool.submit(updateTask);
        } else {
            updateTask.run();
        }
    }

    private void scanChunk(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        int xStart = cpos.getStartX();
        int zStart = cpos.getStartZ();
        int yMin = Math.max(chunk.getBottomY(), minY.get());
        int yMax = Math.min(chunk.getBottomY() + chunk.getHeight(), maxY.get());

        Set<BlockPos> chunkDeepslate = new HashSet<>();
        int foundCount = 0;

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = yMin; y < yMax; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isDeepslateInRange(chunk.getBlockState(pos), y)) {
                        chunkDeepslate.add(pos);
                        foundCount++;
                    }
                }
            }
        }

        // Remove old deepslate positions from this chunk
        deepslatePositions.removeIf(pos -> {
            ChunkPos blockChunk = new ChunkPos(pos);
            return blockChunk.equals(cpos) && !chunkDeepslate.contains(pos);
        });

        // Add new deepslate positions
        int newBlocks = 0;
        for (BlockPos pos : chunkDeepslate) {
            if (deepslatePositions.add(pos)) {
                newBlocks++;
            }
        }

        // Provide chunk-level feedback to reduce spam
        if (chatFeedback.get() && foundCount > 0) {
            if (useThreading.get() && limitChatSpam.get()) {
                if (newBlocks > 0) {
                    info("§5[§dDeepslateESP§5] §bChunk " + cpos.x + "," + cpos.z + "§5: §b" + newBlocks + " new deepslate blocks found");
                }
            } else {
                for (BlockPos pos : chunkDeepslate) {
                    if (!deepslatePositions.contains(pos)) {
                        info("§5[§dDeepslateESP§5] §bDeepslateESP§5: §bDeepslate at " + pos.toShortString());
                    }
                }
            }
        }
    }

    private boolean isDeepslateInRange(BlockState state, int y) {
        return y >= minY.get() && y <= maxY.get() && state.getBlock() == Blocks.DEEPSLATE;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d playerPos = mc.player.getPos();
        Color side = new Color(deepslateColor.get());
        Color outline = new Color(deepslateColor.get());
        Color tracerColorValue = new Color(tracerColor.get());

        for (BlockPos pos : deepslatePositions) {
            // Render ESP box
            event.renderer.box(pos, side, outline, shapeMode.get(), 0);

            // Render tracer if enabled
            if (tracers.get()) {
                Vec3d blockCenter = Vec3d.ofCenter(pos);
                event.renderer.line(playerPos.x, playerPos.y, playerPos.z,
                    blockCenter.x, blockCenter.y, blockCenter.z, tracerColorValue);
            }
        }
    }
}
