package com.nnpg.glazed.utils.glazed;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.state.property.Property;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Objects;
import java.util.stream.Stream;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class BlockUtil {
    public static Stream<WorldChunk> getLoadedChunks() {
        int radius = Math.max(2, mc.options.getClampedViewDistance()) + 3;
        int diameter = radius * 2 + 1;

        ChunkPos center = mc.player.getChunkPos();
        ChunkPos min = new ChunkPos(center.x - radius, center.z - radius);
        ChunkPos max = new ChunkPos(center.x + radius, center.z + radius);

        return Stream.iterate(min, pos -> {
                int x = pos.x;
                int z = pos.z;
                x++;
                if (x > max.x) {
                    x = min.x;
                    z++;
                }
                if (z > max.z)
                    throw new IllegalStateException("Stream limit didn't work.");

                return new ChunkPos(x, z);

            }).limit((long) diameter * diameter)
            .filter(c -> mc.world.isChunkLoaded(c.x, c.z))
            .map(c -> mc.world.getChunk(c.x, c.z)).filter(Objects::nonNull);
    }

    public static boolean isBlockAtPosition(final BlockPos blockPos, final Block block) {
        return mc.world.getBlockState(blockPos).getBlock() == block;
    }

    public static boolean isRespawnAnchorCharged(final BlockPos blockPos) {
        return isBlockAtPosition(blockPos, Blocks.RESPAWN_ANCHOR) &&
            (int) mc.world.getBlockState(blockPos).get((Property) RespawnAnchorBlock.CHARGES) != 0;
    }

    public static boolean isRespawnAnchorUncharged(final BlockPos blockPos) {
        return isBlockAtPosition(blockPos, Blocks.RESPAWN_ANCHOR) &&
            (int) mc.world.getBlockState(blockPos).get((Property) RespawnAnchorBlock.CHARGES) == 0;
    }

    public static void interactWithBlock(final BlockHitResult blockHitResult, final boolean shouldSwingHand) {
        final ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHitResult);
        if (result.isAccepted() && shouldSwingHand) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }
}
