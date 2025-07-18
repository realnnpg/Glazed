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
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.KelpBlock;
import net.minecraft.block.KelpPlantBlock;
import net.minecraft.block.PointedDripstoneBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;

public class ExtraESP extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<Boolean> detectRotatedDeepslate = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-rotated-deepslate")
        .description("Enable rotated deepslate ESP")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> detectNormalDeepslate = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-normal-deepslate")
        .description("Enable normal deepslate ESP (y >= 8)")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> detectKelp = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-kelp-esp")
        .description("Enable kelp chunk ESP")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> detectDripstoneESP = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-dripstone-esp")
        .description("Enable long dripstone stalactite ESP")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> detectDripstoneUpESP = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-dripstone-up-esp")
        .description("Enable long dripstone stalagmite ESP")
        .defaultValue(true)
        .build());

    private final SettingGroup sgDeepslate = settings.createGroup("Deepslate ESP");
    private final Setting<SettingColor> deepslateColor = sgDeepslate.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Deepslate box color")
        .defaultValue(new SettingColor(255, 0, 255, 100))
        .build());

    private final Setting<ShapeMode> deepslateShapeMode = sgDeepslate.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Deepslate box render mode")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<Boolean> deepslateChat = sgDeepslate.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Announce rotated deepslate in chat")
        .defaultValue(true)
        .build());

    private final SettingGroup sgNormalDeepslate = settings.createGroup("Normal Deepslate ESP");
    private final Setting<SettingColor> normalDeepslateColor = sgNormalDeepslate.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Normal deepslate box color")
        .defaultValue(new SettingColor(0, 200, 255, 100))
        .build());

    private final Setting<ShapeMode> normalDeepslateShapeMode = sgNormalDeepslate.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Normal deepslate box render mode")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<Boolean> normalDeepslateChat = sgNormalDeepslate.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Announce normal deepslate in chat")
        .defaultValue(true)
        .build());

    private final SettingGroup sgKelp = settings.createGroup("Kelp ESP");
    private final Setting<SettingColor> kelpColor = sgKelp.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Kelp ESP box color")
        .defaultValue(new SettingColor(0, 255, 0, 100))
        .build());

    private final Setting<ShapeMode> kelpShapeMode = sgKelp.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Kelp ESP box render mode")
        .defaultValue(ShapeMode.Lines)
        .build());

    private final Setting<Boolean> kelpChat = sgKelp.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Announce flagged kelp chunks in chat")
        .defaultValue(true)
        .build());

    private final SettingGroup sgDripstone = settings.createGroup("Dripstone ESP");
    private final Setting<SettingColor> dripstoneColor = sgDripstone.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Dripstone ESP box color")
        .defaultValue(new SettingColor(100, 255, 200, 100))
        .build());

    private final Setting<ShapeMode> dripstoneShapeMode = sgDripstone.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Dripstone box render mode")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<Boolean> dripstoneChat = sgDripstone.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Announce long dripstone stalactites in chat")
        .defaultValue(true)
        .build());

    private final Setting<Integer> dripstoneMinLength = sgDripstone.add(new IntSetting.Builder()
        .name("min-length")
        .description("Minimum length for stalactite to show ESP")
        .defaultValue(4)
        .min(4)
        .max(16)
        .sliderRange(4, 16)
        .build());

    private final SettingGroup sgDripstoneUp = settings.createGroup("Dripstone Up ESP");
    private final Setting<SettingColor> dripstoneUpColor = sgDripstoneUp.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Dripstone Up ESP box color")
        .defaultValue(new SettingColor(255, 150, 100, 100))
        .build());

    private final Setting<ShapeMode> dripstoneUpShapeMode = sgDripstoneUp.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Dripstone Up box render mode")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<Boolean> dripstoneUpChat = sgDripstoneUp.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Announce long dripstone stalagmites in chat")
        .defaultValue(true)
        .build());

    private final Setting<Integer> dripstoneUpMinLength = sgDripstoneUp.add(new IntSetting.Builder()
        .name("min-length")
        .description("Minimum length for stalagmite to show ESP")
        .defaultValue(8)
        .min(4)
        .max(16)
        .sliderRange(4, 16)
        .build());

    // Data holders
    private final Set<BlockPos> rotatedDeepslatePositions = new HashSet<>();
    private final Set<BlockPos> normalDeepslatePositions = new HashSet<>();
    private final Set<ChunkPos> flaggedKelpChunks = new HashSet<>();
    private final Set<BlockPos> longDripstoneBottoms = new HashSet<>();
    private final Set<BlockPos> longDripstoneUpTops = new HashSet<>();

    public ExtraESP() {
        super(GlazedAddon.CATEGORY, "ExtraESP", "ESP for rotated deepslate, normal deepslate, kelp, long dripstone stalactites, and stalagmites.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null) return;
        rotatedDeepslatePositions.clear();
        normalDeepslatePositions.clear();
        flaggedKelpChunks.clear();
        longDripstoneBottoms.clear();
        longDripstoneUpTops.clear();

        for (Chunk chunk : Utils.chunks()) {
            if (chunk instanceof WorldChunk worldChunk) scanChunk(worldChunk);
        }
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        scanChunk(event.chunk());
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        BlockPos pos = event.pos;
        BlockState state = event.newState;

        if (detectRotatedDeepslate.get()) {
            boolean isRotated = isRotatedDeepslate(state, pos.getY());
            if (isRotated && rotatedDeepslatePositions.add(pos) && deepslateChat.get()) {
                info("§5[§dRotated Deepslate§5] §dRotated Deepslate§5: §dRotated deepslate at " + pos.toShortString());
            } else if (!isRotated) {
                rotatedDeepslatePositions.remove(pos);
            }
        }

        if (detectNormalDeepslate.get()) {
            boolean isNormal = isNormalDeepslate(state, pos.getY());
            if (isNormal && normalDeepslatePositions.add(pos) && normalDeepslateChat.get()) {
                info("§5[§dDeepslateESP§5 §bDeepslateESP§5: §bDeepslate at " + pos.toShortString());
            } else if (!isNormal) {
                normalDeepslatePositions.remove(pos);
            }
        }

        if (detectKelp.get()) {
            Chunk chunk = mc.world.getChunk(pos);
            if (chunk instanceof WorldChunk worldChunk) {
                scanChunkForKelp(worldChunk);
            }
        }

        if (detectDripstoneESP.get()) {
            // Check if this position could be part of a stalactite
            if (state.isOf(Blocks.POINTED_DRIPSTONE)) {
                // Scan the area around this position for stalactites
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dy = -16; dy <= 16; dy++) {
                            BlockPos scanPos = pos.add(dx, dy, dz);
                            BlockState scanState = mc.world.getBlockState(scanPos);
                            if (isDripstoneTipDown(scanState)) {
                                StalactiteInfo info = getStalactiteInfo(scanPos);
                                if (info != null && info.length >= dripstoneMinLength.get()) {
                                    if (longDripstoneBottoms.add(info.bottomPos) && dripstoneChat.get()) {
                                        info("§5[§dDripstone Esp§5] §3Dripstone Esp§5: §3Long dripstone at " + scanPos.toShortString() + " (length " + info.length + ")");
                                    }
                                } else if (info != null) {
                                    longDripstoneBottoms.remove(info.bottomPos);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (detectDripstoneUpESP.get()) {
            // Check if this position could be part of a stalagmite
            if (state.isOf(Blocks.POINTED_DRIPSTONE)) {
                // Scan the area around this position for stalagmites
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dy = -16; dy <= 16; dy++) {
                            BlockPos scanPos = pos.add(dx, dy, dz);
                            BlockState scanState = mc.world.getBlockState(scanPos);
                            if (isDripstoneTipUp(scanState)) {
                                StalagmiteInfo info = getStalagmiteInfo(scanPos);
                                if (info != null && info.length >= dripstoneUpMinLength.get()) {
                                    if (longDripstoneUpTops.add(info.topPos) && dripstoneUpChat.get()) {
                                        info("§5[§dDripstoneUp§5] §6DripstoneUp§5: §6Long stalagmite at " + scanPos.toShortString() + " (length " + info.length + ")");
                                    }
                                } else if (info != null) {
                                    longDripstoneUpTops.remove(info.topPos);
                                }
                            }
                        }
                    }
                }
            } else {
                // If the block is not dripstone, remove any ESP markers that might be invalid
                longDripstoneUpTops.removeIf(topPos -> {
                    BlockState topState = mc.world.getBlockState(topPos);
                    return !topState.isOf(Blocks.POINTED_DRIPSTONE) || !isDripstoneTipUp(topState);
                });
            }
        }
    }

    private void scanChunk(WorldChunk chunk) {
        if (detectRotatedDeepslate.get()) scanChunkForRotatedDeepslate(chunk);
        if (detectNormalDeepslate.get()) scanChunkForNormalDeepslate(chunk);
        if (detectKelp.get()) scanChunkForKelp(chunk);
        if (detectDripstoneESP.get()) scanChunkForDripstone(chunk);
        if (detectDripstoneUpESP.get()) scanChunkForDripstoneUp(chunk);
    }

    private void scanChunkForRotatedDeepslate(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        int xStart = cpos.getStartX();
        int zStart = cpos.getStartZ();
        int yMin = chunk.getBottomY();
        int yMax = Math.min(128, yMin + chunk.getHeight());

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = yMin; y < yMax; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isRotatedDeepslate(chunk.getBlockState(pos), y)) {
                        if (rotatedDeepslatePositions.add(pos) && deepslateChat.get()) {
                            info("§5[§dRotated Deepslate§5] §dRotated Deepslate§5: §dRotated deepslate at " + pos.toShortString());
                        }
                    }
                }
            }
        }
    }

    private void scanChunkForNormalDeepslate(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        int xStart = cpos.getStartX();
        int zStart = cpos.getStartZ();
        int yMin = chunk.getBottomY();
        int yMax = Math.min(128, yMin + chunk.getHeight());

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = Math.max(yMin, 8); y < yMax; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isNormalDeepslate(chunk.getBlockState(pos), y)) {
                        if (normalDeepslatePositions.add(pos) && normalDeepslateChat.get()) {
                            info("§5[§dDeepslateESP§5] §bDeepslateESP§5: §bDeepslate at " + pos.toShortString());
                        }
                    }
                }
            }
        }
    }

    private void scanChunkForKelp(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        flaggedKelpChunks.remove(cpos);

        int xStart = cpos.getStartX();
        int zStart = cpos.getStartZ();
        int yMin = chunk.getBottomY();
        int yMax = yMin + chunk.getHeight();

        int kelpColumns = 0;
        int kelpTopsAt62 = 0;

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                int bottom = -1;
                int top = -1;

                for (int y = yMin; y < yMax; y++) {
                    Block block = chunk.getBlockState(new BlockPos(x, y, z)).getBlock();
                    if (block instanceof KelpBlock || block instanceof KelpPlantBlock) {
                        if (bottom < 0) bottom = y;
                        top = y;
                    }
                }

                if (bottom >= 0 && top - bottom + 1 >= 8) {
                    kelpColumns++;
                    if (top == 62) kelpTopsAt62++;
                }
            }
        }

        if (kelpColumns >= 10 && ((double) kelpTopsAt62 / kelpColumns) >= 0.6) {
            flaggedKelpChunks.add(cpos);
            if (kelpChat.get()) {
                info("§5[§dkelpEsp§5] §akelpEsp§5: §aChunk " + cpos + " flagged: " + kelpTopsAt62 + "/" + kelpColumns + " kelp tops at Y=62");
            }
        }
    }

    private void scanChunkForDripstone(WorldChunk chunk) {
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
                        if (info != null && info.length >= dripstoneMinLength.get()) {
                            chunkBottoms.add(info.bottomPos);
                            if (!longDripstoneBottoms.contains(info.bottomPos) && dripstoneChat.get()) {
                                info("§5[§dDripstone§5] §3Dripstone§5: §3Long stalactite at " + pos.toShortString() + " (length " + info.length + ")");
                            }
                        }
                    }
                }
            }
        }

        // Remove bottoms that are no longer valid for this chunk
        longDripstoneBottoms.removeIf(pos -> {
            ChunkPos tipChunk = new ChunkPos(pos);
            return tipChunk.equals(cpos) && !chunkBottoms.contains(pos);
        });

        // Add new bottoms
        longDripstoneBottoms.addAll(chunkBottoms);
    }

    private void scanChunkForDripstoneUp(WorldChunk chunk) {
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
                        if (length >= dripstoneUpMinLength.get()) {
                            chunkTops.add(pos);
                            if (!longDripstoneUpTops.contains(pos) && dripstoneUpChat.get()) {
                                info("§5[§dDripstoneUp§5] §6DripstoneUp§5: §6Long stalagmite at " + pos.toShortString() + " (length " + length + ")");
                            }
                        }
                    }
                }
            }
        }

        // Remove tops that are no longer valid for this chunk
        longDripstoneUpTops.removeIf(pos -> {
            ChunkPos tipChunk = new ChunkPos(pos);
            return tipChunk.equals(cpos) && !chunkTops.contains(pos);
        });

        // Add new tops
        longDripstoneUpTops.addAll(chunkTops);
    }

    private boolean isRotatedDeepslate(BlockState state, int y) {
        // Check for regular deepslate blocks that are rotated (non-Y axis)
        if (y < 128 && state.isOf(Blocks.DEEPSLATE) && state.contains(Properties.AXIS)) {
            Direction.Axis axis = state.get(Properties.AXIS);
            return axis == Direction.Axis.X || axis == Direction.Axis.Z;
        }

        // Also check for polished deepslate (original logic)
        if (y < 128 && state.isOf(Blocks.POLISHED_DEEPSLATE) && state.contains(Properties.AXIS)) {
            Direction.Axis axis = state.get(Properties.AXIS);
            return axis == Direction.Axis.X || axis == Direction.Axis.Z;
        }

        // Check for deepslate bricks, tiles, etc. that might be rotated
        if (y < 128 && state.contains(Properties.AXIS)) {
            Block block = state.getBlock();
            if (block == Blocks.DEEPSLATE_BRICKS ||
                block == Blocks.DEEPSLATE_TILES ||
                block == Blocks.CHISELED_DEEPSLATE) {
                Direction.Axis axis = state.get(Properties.AXIS);
                return axis == Direction.Axis.X || axis == Direction.Axis.Z;
            }
        }

        return false;
    }

    private boolean isNormalDeepslate(BlockState state, int y) {
        return y >= 8 && y < 128 && state.getBlock() == Blocks.DEEPSLATE;
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
            bottomPos = currentPos; // Keep track of the bottommost position
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
        while (currentPos.getY() < 320) { // Use fixed height limit instead of getTopY()
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
            topPos = currentPos; // Keep track of the topmost position
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
        if (detectRotatedDeepslate.get()) {
            Color side = new Color(deepslateColor.get());
            Color outline = new Color(deepslateColor.get());
            for (BlockPos pos : rotatedDeepslatePositions) {
                event.renderer.box(pos, side, outline, deepslateShapeMode.get(), 0);
            }
        }

        if (detectNormalDeepslate.get()) {
            Color side = new Color(normalDeepslateColor.get());
            Color outline = new Color(normalDeepslateColor.get());
            for (BlockPos pos : normalDeepslatePositions) {
                event.renderer.box(pos, side, outline, normalDeepslateShapeMode.get(), 0);
            }
        }

        if (detectKelp.get()) {
            Color side = new Color(kelpColor.get());
            Color outline = new Color(kelpColor.get());
            for (ChunkPos pos : flaggedKelpChunks) {
                event.renderer.box(
                    pos.getStartX(), 63, pos.getStartZ(),
                    pos.getStartX() + 16, 63, pos.getStartZ() + 16,
                    side, outline, kelpShapeMode.get(), 0);
            }
        }

        if (detectDripstoneESP.get()) {
            Color side = new Color(dripstoneColor.get());
            Color outline = new Color(dripstoneColor.get());
            for (BlockPos pos : longDripstoneBottoms) {
                event.renderer.box(pos, side, outline, dripstoneShapeMode.get(), 0);
            }
        }

        if (detectDripstoneUpESP.get()) {
            Color side = new Color(dripstoneUpColor.get());
            Color outline = new Color(dripstoneUpColor.get());
            for (BlockPos pos : longDripstoneUpTops) {
                event.renderer.box(pos, side, outline, dripstoneUpShapeMode.get(), 0);
            }
        }
    }
}
