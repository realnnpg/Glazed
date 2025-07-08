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
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;

public class ExtraESP extends Module {
    // General
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final Setting<Boolean> detectExtraespDeepslate = sgGeneral.add(new BoolSetting.Builder()
            .name("detect-rotated-deepslate")
            .description("Enable rotated deepslate ESP")
            .defaultValue(true)
            .build());
    private final Setting<Boolean> detectExtraespKelp = sgGeneral.add(new BoolSetting.Builder()
            .name("detect-kelp-esp")
            .description("Enable kelp chunk ESP")
            .defaultValue(true)
            .build());

    // Rotated Deepslate ESP settings
    private final SettingGroup sgExtraespDeepslate = settings.createGroup("Rotated Deepslate ESP");
    private final Setting<SettingColor> extraespDeepslateColor = sgExtraespDeepslate.add(new ColorSetting.Builder()
            .name("esp-color")
            .description("Deepslate box color")
            .defaultValue(new SettingColor(255, 0, 255, 100))
            .build());
    private final Setting<ShapeMode> extraespDeepslateShapeMode = sgExtraespDeepslate.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("Deepslate box render mode")
            .defaultValue(ShapeMode.Both)
            .build());
    private final Setting<Boolean> extraespDeepslateChat = sgExtraespDeepslate.add(new BoolSetting.Builder()
            .name("chat-feedback")
            .description("Announce rotated deepslate in chat")
            .defaultValue(true)
            .build());
    private final Set<BlockPos> extraespRotatedDeepslate = new HashSet<>();

    // Kelp ESP settings
    private final SettingGroup sgExtraespKelp = settings.createGroup("Kelp ESP");
    private final Setting<SettingColor> extraespKelpColor = sgExtraespKelp.add(new ColorSetting.Builder()
            .name("esp-color")
            .description("KelpESP box color")
            .defaultValue(new SettingColor(0, 255, 0, 100))
            .build());
    private final Setting<ShapeMode> extraespKelpShapeMode = sgExtraespKelp.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("KelpESP box render mode")
            .defaultValue(ShapeMode.Lines)
            .build());
    private final Setting<Boolean> extraespKelpChat = sgExtraespKelp.add(new BoolSetting.Builder()
            .name("chat-feedback")
            .description("Announce flagged chunks in chat")
            .defaultValue(true)
            .build());
    private final Set<ChunkPos> extraespFlaggedChunks = new HashSet<>();

    public ExtraESP() {
        super(GlazedAddon.CATEGORY, "ExtraESP", "ESP for more items");
    }

    @Override
    public void onActivate() {
        if (mc.world == null) return;
        extraespRotatedDeepslate.clear();
        extraespFlaggedChunks.clear();
        for (Chunk chunk : Utils.chunks()) {
            if (chunk instanceof WorldChunk) scanChunk((WorldChunk) chunk);
        }
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        scanChunk(event.chunk());
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        BlockPos pos = event.pos;

        if (detectExtraespDeepslate.get()) {
            BlockState state = event.newState;
            boolean isRot = isRotatedDeepslate(state, pos.getY());
            if (isRot && extraespRotatedDeepslate.add(pos) && extraespDeepslateChat.get())
                info("§dExtraESP§f: Rotated deepslate at §a" + pos.toShortString());
            else if (!isRot) extraespRotatedDeepslate.remove(pos);
        }

        if (detectExtraespKelp.get()) {
            Chunk chunk = mc.world.getChunk(pos);
            if (chunk instanceof WorldChunk) scanChunkForExtraespKelp((WorldChunk) chunk);
            if (chunk instanceof WorldChunk worldChunk) {
                scanChunkForExtraespKelp(worldChunk);
            }        }
    }

    private void scanChunk(WorldChunk chunk) {
        if (detectExtraespDeepslate.get()) scanChunkForRotatedDeepslate(chunk);
        if (detectExtraespKelp.get()) scanChunkForExtraespKelp(chunk);
    }

    private void scanChunkForRotatedDeepslate(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        int x0 = cpos.getStartX();
        int z0 = cpos.getStartZ();
        int ymin = chunk.getBottomY();
        int ymax = Math.min(128, ymin + chunk.getHeight());

        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                for (int y = ymin; y < ymax; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isRotatedDeepslate(chunk.getBlockState(pos), y)) {
                        if (extraespRotatedDeepslate.add(pos) && extraespDeepslateChat.get())
                            info("§dExtraESP§f: Rotated deepslate at §a" + pos.toShortString());
                    }
                }
            }
        }
    }

    private void scanChunkForExtraespKelp(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        extraespFlaggedChunks.remove(cpos);

        int x0 = cpos.getStartX(), z0 = cpos.getStartZ();
        int ymin = chunk.getBottomY(), ymax = ymin + chunk.getHeight();

        int columns = 0, tops = 0;
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                int bottom = -1, top = -1;
                for (int y = ymin; y < ymax; y++) {
                    BlockState state = chunk.getBlockState(new BlockPos(x, y, z));
                    Block block = state.getBlock();
                    if (block instanceof KelpBlock || block instanceof KelpPlantBlock) {
                        if (bottom < 0) bottom = y;
                        top = y;
                    }
                }
                if (bottom >= 0 && top - bottom + 1 >= 8) {
                    columns++;
                    if (top == 62) tops++;
                }
            }
        }
        if (columns >= 10 && (double) tops / columns >= 0.6) {
            extraespFlaggedChunks.add(cpos);
            if (extraespKelpChat.get())
                info("§aExtraESP§f: Chunk " + cpos + " flagged: " + tops + "/" + columns + " kelp tops at Y=62");
        }
    }

    private boolean isRotatedDeepslate(BlockState state, int y) {
        return y < 128
                && state.getBlock() == Blocks.POLISHED_DEEPSLATE
                && state.contains(Properties.AXIS)
                && state.get(Properties.AXIS) != Axis.Y;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (detectExtraespDeepslate.get()) {
            Color side = new Color(extraespDeepslateColor.get());
            Color outline = new Color(extraespDeepslateColor.get());
            for (BlockPos pos : extraespRotatedDeepslate) {
                event.renderer.box(pos, side, outline, extraespDeepslateShapeMode.get(), 0);
            }
        }

        if (detectExtraespKelp.get()) {
            Color side = new Color(extraespKelpColor.get());
            Color outline = new Color(extraespKelpColor.get());
            for (ChunkPos pos : extraespFlaggedChunks) {
                event.renderer.box(
                        pos.getStartX(), 63, pos.getStartZ(),
                        pos.getStartX() + 16, 63.1, pos.getStartZ() + 16,
                        side, outline, extraespKelpShapeMode.get(), 0
                );
            }
        }
    }
}
