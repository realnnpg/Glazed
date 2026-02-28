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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OneByOneHoles extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgThreading = settings.createGroup("Threading");

    private final Setting<SettingColor> holeColor = sgGeneral.add(new ColorSetting.Builder()
        .name("hole-color")
        .description("Color for 1x1x1 holes")
        .defaultValue(new SettingColor(255, 0, 0, 100))
        .build());

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Render mode for 1x1x1 holes")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to 1x1x1 holes")
        .defaultValue(false)
        .build());

    private final Setting<SettingColor> tracerColor = sgGeneral.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("1x1x1 hole tracer color")
        .defaultValue(new SettingColor(255, 0, 0, 200))
        .visible(tracers::get)
        .build());

    private final Setting<Boolean> chatNotifications = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-notifications")
        .description("Send chat messages when 1x1x1 holes are found")
        .defaultValue(false)
        .build());

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

    private final Set<BlockPos> oneByOneHoles = ConcurrentHashMap.newKeySet();
    private ExecutorService threadPool;

    public OneByOneHoles() {
        super(GlazedAddon.esp, "1x1x1-holes", "Highlights 1x1x1 air holes that are likely player-made.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null) return;
        
        if (useThreading.get()) {
            threadPool = Executors.newFixedThreadPool(threadPoolSize.get());
        }
        
        oneByOneHoles.clear();
        
        if (useThreading.get() && threadPool != null) {
            for (Chunk chunk : Utils.chunks()) {
                if (chunk instanceof WorldChunk worldChunk) {
                    threadPool.submit(() -> scanChunk(worldChunk));
                }
            }
        } else {
            for (Chunk chunk : Utils.chunks()) {
                if (chunk instanceof WorldChunk worldChunk) {
                    scanChunk(worldChunk);
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
            threadPool = null;
        }
        oneByOneHoles.clear();
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
        
        Runnable updateTask = () -> {
            if (isOneByOneHole(pos)) {
                boolean wasAdded = oneByOneHoles.add(pos);
                if (wasAdded && chatNotifications.get() && (!useThreading.get() || !limitChatSpam.get())) {
                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.sendMessage(Text.of("1x1x1 hole detected at: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
                        }
                    });
                }
            } else {
                oneByOneHoles.remove(pos);
            }

            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = pos.offset(direction);
                if (isOneByOneHole(neighborPos)) {
                    oneByOneHoles.add(neighborPos);
                } else {
                    oneByOneHoles.remove(neighborPos);
                }
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
        Set<BlockPos> chunkHoles = new HashSet<>();
        int foundCount = 0;

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = chunk.getBottomY(); y < chunk.getBottomY() + chunk.getHeight(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isOneByOneHole(pos)) {
                        chunkHoles.add(pos);
                        foundCount++;
                    }
                }
            }
        }

        oneByOneHoles.removeIf(pos -> {
            ChunkPos blockChunk = new ChunkPos(pos);
            return blockChunk.equals(cpos) && !chunkHoles.contains(pos);
        });

        int newHoles = 0;
        for (BlockPos pos : chunkHoles) {
            if (oneByOneHoles.add(pos)) {
                newHoles++;
            }
        }

        if (chatNotifications.get() && foundCount > 0) {
            if (useThreading.get() && limitChatSpam.get()) {
                if (newHoles > 0) {
                    final int finalNewHoles = newHoles;
                    final ChunkPos finalCpos = cpos;
                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.sendMessage(Text.of("1x1x1 holes: " + finalNewHoles + " new in chunk " + finalCpos.x + "," + finalCpos.z), false);
                        }
                    });
                }
            } else {
                for (BlockPos pos : chunkHoles) {
                    if (oneByOneHoles.contains(pos)) {
                        final BlockPos finalPos = pos;
                        mc.execute(() -> {
                            if (mc.player != null) {
                                mc.player.sendMessage(Text.of("1x1x1 hole detected at: " + finalPos.getX() + ", " + finalPos.getY() + ", " + finalPos.getZ()), false);
                            }
                        });
                    }
                }
            }
        }
    }

    private boolean isOneByOneHole(BlockPos pos) {
        if (mc.world == null) return false;
        BlockState selfState = mc.world.getBlockState(pos);

        if (pos.getY() <= 1) return false;

        if (selfState.getBlock() != Blocks.AIR) return false;

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.offset(direction);
            BlockState neighborState = mc.world.getBlockState(neighborPos);
            if (!neighborState.isSolidBlock(mc.world, neighborPos)) {
                return false;
            }
        }

        for (int radius = 1; radius <= 5; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (Math.abs(x) != radius && Math.abs(y) != radius && Math.abs(z) != radius) {
                            continue;
                        }
                        
                        if (x == 0 && y == 0 && z == 0) continue;

                        BlockPos checkPos = pos.add(x, y, z);
                        BlockState checkState = mc.world.getBlockState(checkPos);

                        if (!checkState.isSolidBlock(mc.world, checkPos)) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d playerPos = mc.player.getLerpedPos(event.tickDelta);
        Color side = new Color(holeColor.get());
        Color outline = new Color(holeColor.get());
        Color tracerColorValue = new Color(tracerColor.get());

        for (BlockPos pos : oneByOneHoles) {
            event.renderer.box(pos, side, outline, shapeMode.get(), 0);

            if (tracers.get()) {
                Vec3d blockCenter = Vec3d.ofCenter(pos);

                Vec3d startPos;
                if (mc.options.getPerspective().isFirstPerson()) {
                    Vec3d lookDirection = mc.player.getRotationVector();
                    startPos = new Vec3d(
                        playerPos.x + lookDirection.x * 0.5,
                        playerPos.y + mc.player.getEyeHeight(mc.player.getPose()) + lookDirection.y * 0.5,
                        playerPos.z + lookDirection.z * 0.5
                    );
                } else {
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