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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Set;

public class OneByOneHoles extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");
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

    private final Set<BlockPos> oneByOneHoles = new HashSet<>();

    public OneByOneHoles() {
        super(GlazedAddon.esp, "1x1x1-holes", "Highlights 1x1x1 air holes that are likely player-made.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null) return;
        oneByOneHoles.clear();
        for (Chunk chunk : Utils.chunks()) {                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          // made by temredd aka kxusk and made for Glazed , kez dont skid this :D
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
        if (isOneByOneHole(pos)) {
            oneByOneHoles.add(pos);
            mc.player.sendMessage(Text.of("1x1x1 hole detected at: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
        }

        // Also check surrounding blocks as their update might create/destroy a 1x1x1 hole
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.offset(direction);
            if (isOneByOneHole(neighborPos)) {
                oneByOneHoles.add(neighborPos);
            } else {
                oneByOneHoles.remove(neighborPos);
            }
        }
    }

    private void scanChunk(WorldChunk chunk) {
        for (int x = chunk.getPos().getStartX(); x < chunk.getPos().getEndX(); x++) {
            for (int z = chunk.getPos().getStartZ(); z < chunk.getPos().getEndZ(); z++) {
                for (int y = chunk.getBottomY(); y < chunk.getBottomY() + chunk.getHeight(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isOneByOneHole(pos)) {
                        oneByOneHoles.add(pos);
                        mc.player.sendMessage(Text.of("1x1x1 hole detected at: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
                    }
                }
            }
        }
    }

    private boolean isOneByOneHole(BlockPos pos) {
        if (mc.world == null) return false;
        BlockState selfState = mc.world.getBlockState(pos);

        // Only highlight holes above Y level 1
        if (pos.getY() <= 1) return false;

        if (selfState.getBlock() != Blocks.AIR) return false;

        // Check if all 6 sides are solid blocks
        for (Direction direction : Direction.values()) {
            BlockState neighborState = mc.world.getBlockState(pos.offset(direction));
            if (!neighborState.isSolidBlock(mc.world, pos.offset(direction))) {
                return false;
            }
        }

        // Refined check for natural generation (caves) within a 5-block radius
        // Iterate through a 5x5x5 cube around the potential 1x1x1 hole
        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    if (x == 0 && y == 0 && z == 0) continue; // Skip the current block (the 1x1x1 hole itself)

                    BlockPos checkPos = pos.add(x, y, z);
                    BlockState checkState = mc.world.getBlockState(checkPos);

                    // If any non-solid block is found within the 5-block radius, it's considered part of a larger natural formation.
                    // This is a stricter check: any non-solid block within the radius means it's natural.
                    if (!checkState.isSolidBlock(mc.world, checkPos)) {
                        return false; // This 1x1x1 hole is too close to a natural air pocket (cave/mine)
                    }
                }
            }
        }

        return true;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        Color side = new Color(holeColor.get());
        Color outline = new Color(holeColor.get());
        for (BlockPos pos : oneByOneHoles) {
            event.renderer.box(pos, side, outline, shapeMode.get(), 0);
        }
    }
}
