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
import net.minecraft.block.PointedDripstoneBlock;
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
import java.util.concurrent.Future;

public class DripstoneESP extends Module {
    private final SettingGroup sgStalactite = settings.createGroup("Stalactite ESP");

    private final Setting<SettingColor> stalactiteColor = sgStalactite.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Stalactite ESP box color")
        .defaultValue(new SettingColor(100, 255, 200, 100))
        .build());

    private final Setting<ShapeMode> stalactiteShapeMode = sgStalactite.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Stalactite box render mode")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<Boolean> stalactiteTracers = sgStalactite.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to stalactites")
        .defaultValue(false)
        .build());

    private final Setting<SettingColor> stalactiteTracerColor = sgStalactite.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Stalactite tracer color")
        .defaultValue(new SettingColor(100, 255, 200, 200))
        .visible(stalactiteTracers::get)
        .build());

    private final Setting<Boolean> stalactiteChat = sgStalactite.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Announce long dripstone stalactites in chat")
        .defaultValue(true)
        .build());

    private final Setting<Integer> stalactiteMinLength = sgStalactite.add(new IntSetting.Builder()
        .name("min-length")
        .description("Minimum length for stalactite to show ESP")
        .defaultValue(4)
        .min(4)
        .max(16)
        .sliderRange(4, 16)
        .build());

    private final SettingGroup sgStalagmite = settings.createGroup("Stalagmite ESP");

    private final Setting<SettingColor> stalagmiteColor = sgStalagmite.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Stalagmite ESP box color")
        .defaultValue(new SettingColor(255, 150, 100, 100))
        .build());

    private final Setting<ShapeMode> stalagmiteShapeMode = sgStalagmite.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Stalagmite box render mode")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<Boolean> stalagmiteTracers = sgStalagmite.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to stalagmites")
        .defaultValue(false)
        .build());

    private final Setting<SettingColor> stalagmiteTracerColor = sgStalagmite.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Stalagmite tracer color")
        .defaultValue(new SettingColor(255, 150, 100, 200))
        .visible(stalagmiteTracers::get)
        .build());

    private final Setting<Boolean> stalagmiteChat = sgStalagmite.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Announce long dripstone stalagmites in chat")
        .defaultValue(true)
        .build());

    private final Setting<Integer> stalagmiteMinLength = sgStalagmite.add(new IntSetting.Builder()
        .name("min-length")
        .description("Minimum length for stalagmite to show ESP")
        .defaultValue(8)
        .min(4)
        .max(16)
        .sliderRange(4, 16)
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

    // Thread-safe collections
    private final Set<BlockPos> longStalactiteBottoms = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> longStalagmiteTops = ConcurrentHashMap.newKeySet();

    // Threading
    private ExecutorService threadPool;

    public DripstoneESP() {
        super(GlazedAddon.esp, "DripstoneESP", "ESP for long dripstone stalactites and stalagmites with threading support.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null) return;

        if (useThreading.get()) {
            threadPool = Executors.newFixedThreadPool(threadPoolSize.get());
        }

        longStalactiteBottoms.clear();
        longStalagmiteTops.clear();

        if (useThreading.get()) {
            for (Chunk chunk : Utils.chunks()) {
                if (chunk instanceof WorldChunk worldChunk) {
                    threadPool.submit(() -> scanChunk(worldChunk));
                }
            }
        } else {
            for (Chunk chunk : Utils.chunks()) {
                if (chunk instanceof WorldChunk worldChunk) scanChunk(worldChunk);
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
            threadPool = null;
        }

        longStalactiteBottoms.clear();
        longStalagmiteTops.clear();
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

        Runnable scanTask = () -> {
            if (state.isOf(Blocks.POINTED_DRIPSTONE)) {
                // Scan the area around this position for stalactites and stalagmites
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dy = -16; dy <= 16; dy++) {
                            BlockPos scanPos = pos.add(dx, dy, dz);
                            BlockState scanState = mc.world.getBlockState(scanPos);

                            if (isDripstoneTipDown(scanState)) {
                                StalactiteInfo stalactiteInfo = getStalactiteInfo(scanPos);
                                if (stalactiteInfo != null && stalactiteInfo.length >= stalactiteMinLength.get()) {
                                    if (longStalactiteBottoms.add(stalactiteInfo.bottomPos) && stalactiteChat.get()) {
                                        info("§5[§dDripstone Esp§5] §3Dripstone Esp§5: §3Long stalactite at " + scanPos.toShortString() + " (length " + stalactiteInfo.length + ")");
                                    }
                                } else if (stalactiteInfo != null) {
                                    longStalactiteBottoms.remove(stalactiteInfo.bottomPos);
                                }
                            }

                            if (isDripstoneTipUp(scanState)) {
                                StalagmiteInfo stalagmiteInfo = getStalagmiteInfo(scanPos);
                                if (stalagmiteInfo != null && stalagmiteInfo.length >= stalagmiteMinLength.get()) {
                                    if (longStalagmiteTops.add(stalagmiteInfo.topPos) && stalagmiteChat.get()) {
                                        info("§5[§dDripstoneUp§5] §6DripstoneUp§5: §6Long stalagmite at " + scanPos.toShortString() + " (length " + stalagmiteInfo.length + ")");
                                    }
                                } else if (stalagmiteInfo != null) {
                                    longStalagmiteTops.remove(stalagmiteInfo.topPos);
                                }
                            }
                        }
                    }
                }
            } else {
                longStalagmiteTops.removeIf(topPos -> {
                    BlockState topState = mc.world.getBlockState(topPos);
                    return !topState.isOf(Blocks.POINTED_DRIPSTONE) || !isDripstoneTipUp(topState);
                });
            }
        };

        if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
            threadPool.submit(scanTask);
        } else {
            scanTask.run();
        }
    }

    private void scanChunk(WorldChunk chunk) {
        scanChunkForStalactites(chunk);
        scanChunkForStalagmites(chunk);
    }

    private void scanChunkForStalactites(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        int xStart = cpos.getStartX();
        int zStart = cpos.getStartZ();
        int yMin = chunk.getBottomY();
        int yMax = yMin + chunk.getHeight();

        Set<BlockPos> chunkBottoms = new HashSet<>();

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = yMin; y < yMax; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (isDripstoneTipDown(state)) {
                        StalactiteInfo info = getStalactiteInfo(pos);
                        if (info != null && info.length >= stalactiteMinLength.get()) {
                            chunkBottoms.add(info.bottomPos);
                            if (!longStalactiteBottoms.contains(info.bottomPos) && stalactiteChat.get()) {
                                info("§5[§dDripstone§5] §3Dripstone§5: §3Long stalactite at " + pos.toShortString() + " (length " + info.length + ")");
                            }
                        }
                    }
                }
            }
        }

        longStalactiteBottoms.removeIf(pos -> {
            ChunkPos tipChunk = new ChunkPos(pos);
            return tipChunk.equals(cpos) && !chunkBottoms.contains(pos);
        });

        // Add new bottoms
        longStalactiteBottoms.addAll(chunkBottoms);
    }

    private void scanChunkForStalagmites(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        int xStart = cpos.getStartX();
        int zStart = cpos.getStartZ();
        int yMin = chunk.getBottomY();
        int yMax = yMin + chunk.getHeight();

        Set<BlockPos> chunkTops = new HashSet<>();

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = yMin; y < yMax; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (isDripstoneTipUp(state)) {
                        int length = getStalagmiteLength(pos);
                        if (length >= stalagmiteMinLength.get()) {
                            chunkTops.add(pos);
                            if (!longStalagmiteTops.contains(pos) && stalagmiteChat.get()) {
                                info("§5[§dDripstoneUp§5] §6DripstoneUp§5: §6Long stalagmite at " + pos.toShortString() + " (length " + length + ")");
                            }
                        }
                    }
                }
            }
        }

        // Remove tops that are no longer valid for this chunk
        longStalagmiteTops.removeIf(pos -> {
            ChunkPos tipChunk = new ChunkPos(pos);
            return tipChunk.equals(cpos) && !chunkTops.contains(pos);
        });

        // Add new tops
        longStalagmiteTops.addAll(chunkTops);
    }

    private boolean isDripstoneTipDown(BlockState state) {
        return state.isOf(Blocks.POINTED_DRIPSTONE)
            && state.contains(PointedDripstoneBlock.VERTICAL_DIRECTION)
            && state.get(PointedDripstoneBlock.VERTICAL_DIRECTION) == Direction.DOWN;
    }

    private boolean isDripstoneTipUp(BlockState state) {
        return state.isOf(Blocks.POINTED_DRIPSTONE)
            && state.contains(PointedDripstoneBlock.VERTICAL_DIRECTION)
            && state.get(PointedDripstoneBlock.VERTICAL_DIRECTION) == Direction.UP;
    }

    private static class StalactiteInfo {
        final int length;
        final BlockPos bottomPos;

        StalactiteInfo(int length, BlockPos bottomPos) {
            this.length = length;
            this.bottomPos = bottomPos;
        }
    }

    private StalactiteInfo getStalactiteInfo(BlockPos tipPos) {
        if (mc.world == null) return null;

        int length = 0;
        BlockPos currentPos = tipPos;
        BlockPos bottomPos = tipPos;

        // Count downward from the tip
        while (currentPos.getY() >= mc.world.getBottomY()) {
            BlockState state = mc.world.getBlockState(currentPos);

            if (!state.isOf(Blocks.POINTED_DRIPSTONE)) {
                break;
            }

            if (!state.contains(PointedDripstoneBlock.VERTICAL_DIRECTION)) {
                break;
            }

            Direction tipDirection = state.get(PointedDripstoneBlock.VERTICAL_DIRECTION);
            if (tipDirection != Direction.DOWN) {
                break;
            }

            length++;
            bottomPos = currentPos;
            currentPos = currentPos.down();
        }

        return length > 0 ? new StalactiteInfo(length, bottomPos) : null;
    }

    private static class StalagmiteInfo {
        final int length;
        final BlockPos topPos;

        StalagmiteInfo(int length, BlockPos topPos) {
            this.length = length;
            this.topPos = topPos;
        }
    }

    private StalagmiteInfo getStalagmiteInfo(BlockPos tipPos) {
        if (mc.world == null) return null;

        int length = 0;
        BlockPos currentPos = tipPos;
        BlockPos topPos = tipPos;

        // Count upward from the tip
        while (currentPos.getY() < 320) {
            BlockState state = mc.world.getBlockState(currentPos);

            if (!state.isOf(Blocks.POINTED_DRIPSTONE)) {
                break;
            }

            if (!state.contains(PointedDripstoneBlock.VERTICAL_DIRECTION)) {
                break;
            }

            Direction tipDirection = state.get(PointedDripstoneBlock.VERTICAL_DIRECTION);
            if (tipDirection != Direction.UP) {
                break;
            }

            length++;
            topPos = currentPos;
            currentPos = currentPos.up();
        }

        return length > 0 ? new StalagmiteInfo(length, topPos) : null;
    }

    private int getStalagmiteLength(BlockPos tipPos) {
        StalagmiteInfo info = getStalagmiteInfo(tipPos);
        return info != null ? info.length : 0;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d playerPos = mc.player.getLerpedPos(event.tickDelta);

        Color stalactiteSide = new Color(stalactiteColor.get());
        Color stalactiteOutline = new Color(stalactiteColor.get());
        Color stalactiteTracer = new Color(stalactiteTracerColor.get());

        for (BlockPos pos : longStalactiteBottoms) {
            event.renderer.box(pos, stalactiteSide, stalactiteOutline, stalactiteShapeMode.get(), 0);

            if (stalactiteTracers.get()) {
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
                    // Third person: use normal eye position
                    startPos = new Vec3d(
                        playerPos.x,
                        playerPos.y + mc.player.getEyeHeight(mc.player.getPose()),
                        playerPos.z
                    );
                }

                event.renderer.line(startPos.x, startPos.y, startPos.z,
                    blockCenter.x, blockCenter.y, blockCenter.z, stalactiteTracer);
            }
        }

        Color stalagmiteSide = new Color(stalagmiteColor.get());
        Color stalagmiteOutline = new Color(stalagmiteColor.get());
        Color stalagmiteTracer = new Color(stalagmiteTracerColor.get());

        for (BlockPos pos : longStalagmiteTops) {
            event.renderer.box(pos, stalagmiteSide, stalagmiteOutline, stalagmiteShapeMode.get(), 0);

            if (stalagmiteTracers.get()) {
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
                    blockCenter.x, blockCenter.y, blockCenter.z, stalagmiteTracer);
            }
        }
    }
}
