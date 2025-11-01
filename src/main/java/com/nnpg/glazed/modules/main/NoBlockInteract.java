package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.Set;

public class NoBlockInteract extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public NoBlockInteract() {
        super(GlazedAddon.CATEGORY, "no-block-interact", "Lets you pearl through containers by blocking GUI interactions but still throwing pearls.");
    }

    private final Set<Block> blockedBlocks = Set.of(
        Blocks.CHEST,
        Blocks.ENDER_CHEST,
        Blocks.TRAPPED_CHEST,
        Blocks.CRAFTING_TABLE,
        Blocks.ENCHANTING_TABLE,
        Blocks.ANVIL,
        Blocks.LECTERN,
        Blocks.BARREL,
        Blocks.SMITHING_TABLE,
        Blocks.FURNACE,
        Blocks.BLAST_FURNACE,
        Blocks.SMOKER,
        Blocks.SHULKER_BOX,
        Blocks.WHITE_SHULKER_BOX,
        Blocks.ORANGE_SHULKER_BOX,
        Blocks.MAGENTA_SHULKER_BOX,
        Blocks.LIGHT_BLUE_SHULKER_BOX,
        Blocks.YELLOW_SHULKER_BOX,
        Blocks.LIME_SHULKER_BOX,
        Blocks.PINK_SHULKER_BOX,
        Blocks.GRAY_SHULKER_BOX,
        Blocks.LIGHT_GRAY_SHULKER_BOX,
        Blocks.CYAN_SHULKER_BOX,
        Blocks.PURPLE_SHULKER_BOX,
        Blocks.BLUE_SHULKER_BOX,
        Blocks.BROWN_SHULKER_BOX,
        Blocks.GREEN_SHULKER_BOX,
        Blocks.RED_SHULKER_BOX,
        Blocks.BLACK_SHULKER_BOX
    );

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (mc.player == null || mc.world == null) return;

        if (event.packet instanceof PlayerInteractBlockC2SPacket packet) {
            BlockPos pos = packet.getBlockHitResult().getBlockPos();
            Block block = mc.world.getBlockState(pos).getBlock();

            if (!blockedBlocks.contains(block)) return;

            // If holding an ender pearl in main hand
            if (mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL) {
                event.cancel();
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND); // ✅ fixed signature
                mc.player.swingHand(Hand.MAIN_HAND);
            }
            // If holding an ender pearl in off-hand
            else if (mc.player.getOffHandStack().getItem() == Items.ENDER_PEARL) {
                event.cancel();
                mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
                mc.player.swingHand(Hand.OFF_HAND);
            }
            // Otherwise, just cancel the interaction (block GUI)
            else {
                event.cancel();
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }
}
