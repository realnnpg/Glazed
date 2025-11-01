package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
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
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BedrockVoidESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgPerformance = settings.createGroup("Performance");

    private final Setting<Integer> minVoidSize = sgGeneral.add(new IntSetting.Builder()
        .name("min-void-size")
        .description("Minimum number of blocks to consider an area a void.")
        .defaultValue(2)
        .min(1)
        .sliderMax(50)
        .onChanged(this::onSettingChanged)
        .build()
    );

    private final Setting<Boolean> showEsp = sgGeneral.add(new BoolSetting.Builder()
        .name("show-esp")
        .description("Show the void ESP.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showTracers = sgGeneral.add(new BoolSetting.Builder()
        .name("show-tracers")
        .description("Show tracers to the voids.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Announce voids in chat")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxMessagesPerMinute = sgGeneral.add(new IntSetting.Builder()
        .name("max-messages-per-minute")
        .description("Maximum void messages per minute (0 = unlimited)")
        .defaultValue(10)
        .min(0)
        .max(60)
        .sliderRange(0, 60)
        .visible(chatFeedback::get)
        .build()
    );

    private final Setting<SettingColor> espColor = sgRender.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Color of the void ESP.")
        .defaultValue(new SettingColor(240, 85, 80, 128))
        .visible(showEsp::get)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Shape mode of the void ESP.")
        .defaultValue(ShapeMode.Both)
        .visible(showEsp::get)
        .build()
    );

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color of the void tracers.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(showTracers::get)
        .build()
    );

    private final Setting<Boolean> useThreading = sgPerformance.add(new BoolSetting.Builder()
        .name("enable-threading")
        .description("Use multi-threading for chunk scanning (better performance)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> threadPoolSize = sgPerformance.add(new IntSetting.Builder()
        .name("thread-pool-size")
        .description("Number of threads to use for scanning")
        .defaultValue(4)
        .min(1)
        .max(8)
        .sliderRange(1, 8)
        .visible(useThreading::get)
        .build()
    );

    public BedrockVoidESP() {
        super(GlazedAddon.esp,
            "BedrockVoidESP",
            "Finds voids in bedrock layers. Useful for indicating places" +
                "where spawners may be un-raidable if located there."
        );
    }

    // Bedrock Y-Levels
    private static final List<Integer> OVERWORLD_Y_LEVELS = List.of(-64, -63, -62, -61, -60);
    private static final List<Integer> NETHER_FLOOR_Y_LEVELS = List.of(0, 1, 2, 3, 4);
    private static final List<Integer> NETHER_ROOF_Y_LEVELS = List.of(123, 124, 125, 126, 127);

    private String currentDimension;
    private final Set<BlockPos> voidBlocks = ConcurrentHashMap.newKeySet();

    // Threading
    private ExecutorService threadPool;

    // Chat feedback rate limiting
    private long lastMinuteStart = 0;
    private int messagesThisMinute = 0;

    @Override
    public void onActivate() {
        if (mc.world == null) return;
        currentDimension = mc.world.getRegistryKey().getValue().toString();

        // Initialize thread pool
        if (useThreading.get()) {
            threadPool = Executors.newFixedThreadPool(threadPoolSize.get());
        }

        voidBlocks.clear();

        // Reset chat rate limiting
        lastMinuteStart = 0;
        messagesThisMinute = 0;

        // Scan all currently loaded chunks
        for (net.minecraft.world.chunk.Chunk chunk : Utils.chunks()) {
            if (chunk instanceof WorldChunk worldChunk) {
                if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
                    threadPool.submit(() -> scanChunk(worldChunk));
                } else {
                    scanChunk(worldChunk);
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        // Shutdown thread pool
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdownNow();
            threadPool = null;
        }

        voidBlocks.clear();
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (event.chunk() instanceof WorldChunk worldChunk) {
            if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
                threadPool.submit(() -> scanChunk(worldChunk));
            } else {
                scanChunk(worldChunk);
            }
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        BlockPos pos = event.pos;
        BlockState state = event.newState;

        // Check if this block update affects bedrock layers
        List<Integer> yLevels = getYLevelsForDimension();
        if (!yLevels.contains(pos.getY())) return;

        // Rescan the chunk this block is in
        ChunkPos chunkPos = new ChunkPos(pos);
        net.minecraft.world.chunk.Chunk chunk = mc.world.getChunk(chunkPos.x, chunkPos.z);
        if (chunk instanceof WorldChunk worldChunk) {
            if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
                threadPool.submit(() -> scanChunk(worldChunk));
            } else {
                scanChunk(worldChunk);
            }
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof UnloadChunkS2CPacket packet) {
            ChunkPos chunkPos = packet.pos();

            // Remove void blocks from the unloaded chunk
            voidBlocks.removeIf(blockPos -> new ChunkPos(blockPos).equals(chunkPos));
        }
    }

    private void scanChunk(WorldChunk chunk) {
        if (mc.world == null || chunk == null) return;

        ChunkPos chunkPos = chunk.getPos();

        // Remove old void blocks from this chunk
        voidBlocks.removeIf(blockPos -> new ChunkPos(blockPos).equals(chunkPos));

        List<Integer> yLevels = getYLevelsForDimension();
        if (yLevels.isEmpty()) return;

        findVoidsInChunk(chunk, yLevels);
    }

    private List<Integer> getYLevelsForDimension() {
        return switch (currentDimension) {
            case "minecraft:overworld" -> OVERWORLD_Y_LEVELS;
            case "minecraft:the_nether" -> {
                List<Integer> levels = new ArrayList<>();
                levels.addAll(NETHER_FLOOR_Y_LEVELS);
                levels.addAll(NETHER_ROOF_Y_LEVELS);
                yield levels;
            }
            default -> Collections.emptyList();
        };
    }

    private void findVoidsInChunk(WorldChunk chunk, List<Integer> yLevels) {
        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();

        Set<BlockPos> processed = new HashSet<>();

        // Find all non-bedrock blocks and group them
        for (int y : yLevels) {
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    BlockPos pos = new BlockPos(startX + dx, y, startZ + dz);

                    if (processed.contains(pos)) continue;
                    if (isBedrock(getBlockState(pos))) continue;

                    // Found non-bedrock block, flood fill to find connected group
                    List<BlockPos> group = floodFillVoid(pos, yLevels, processed);

                    if (group.size() >= minVoidSize.get() && isVoidEnclosed(group)) {
                        voidBlocks.addAll(group);
                        if (!group.isEmpty()) {
                            BlockPos firstBlock = group.get(0);
                            sendVoidMessage("§5[§dBedrockVoidESP§5] §bVoid found§5: §b" + group.size() + " blocks at " + firstBlock.toShortString());
                        }
                    }
                }
            }
        }
    }

    private List<BlockPos> floodFillVoid(BlockPos start, List<Integer> yLevels, Set<BlockPos> processed) {
        List<BlockPos> group = new ArrayList<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.offer(start);

        while (!queue.isEmpty() && group.size() < 200) {
            BlockPos current = queue.poll();

            if (processed.contains(current)) continue;
            if (isBedrock(getBlockState(current))) continue;
            if (!yLevels.contains(current.getY())) continue;

            processed.add(current);
            group.add(current);

            // Check 6 neighbors
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.offset(dir);
                if (!processed.contains(neighbor)) {
                    queue.offer(neighbor);
                }
            }
        }

        return group;
    }

    private boolean isVoidEnclosed(List<BlockPos> group) {
        // Check if all blocks around the group are bedrock
        for (BlockPos pos : group) {
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.offset(dir);

                // Skip if neighbour is part of the group
                if (group.contains(neighbor)) continue;

                // If neighbour is not bedrock, group is not enclosed
                if (!isBedrock(getBlockState(neighbor))) {
                    return false;
                }
            }
        }
        return true;
    }

    private BlockState getBlockState(BlockPos pos) {
        if (mc.world == null) return Blocks.BEDROCK.getDefaultState();
        return mc.world.getBlockState(pos);
    }

    private void onSettingChanged(Integer value) {
        // Rescan all chunks when settings change
        if (isActive() && mc.world != null) {
            voidBlocks.clear();

            // Re-scan all loaded chunks
            for (net.minecraft.world.chunk.Chunk chunk : Utils.chunks()) {
                if (chunk instanceof WorldChunk worldChunk) {
                    if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
                        threadPool.submit(() -> scanChunk(worldChunk));
                    } else {
                        scanChunk(worldChunk);
                    }
                }
            }
        }
    }

    private static boolean isBedrock(BlockState state) {
        return state.getBlock() == Blocks.BEDROCK;
    }

    private void sendVoidMessage(String message) {
        if (!chatFeedback.get()) return;

        long currentTime = System.currentTimeMillis();
        long currentMinute = currentTime / 60000;

        // Reset counter if in a new minute
        if (currentMinute != lastMinuteStart) {
            lastMinuteStart = currentMinute;
            messagesThisMinute = 0;
        }

        int maxMessages = maxMessagesPerMinute.get();

        if (maxMessages == 0 || messagesThisMinute < maxMessages) {
            info(message);
            messagesThisMinute++;
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (showEsp.get()) {
            Color color = espColor.get();
            for (BlockPos pos : voidBlocks) {
                event.renderer.box(pos, color, color, shapeMode.get(), 0);
            }
        }

        if (showTracers.get()) {
            Color color = tracerColor.get();
            Vec3d camera = mc.gameRenderer.getCamera().getPos();

            for (BlockPos pos : voidBlocks) {
                Vec3d blockCenter = Vec3d.ofCenter(pos);
                event.renderer.line(camera.x, camera.y, camera.z, blockCenter.x, blockCenter.y, blockCenter.z, color);
            }
        }
    }
}