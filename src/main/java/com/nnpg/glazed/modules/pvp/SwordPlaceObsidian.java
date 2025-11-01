package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class SwordPlaceObsidian extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private boolean placing = false;
    private int previousSlot = -1;

    public SwordPlaceObsidian() {
        super(GlazedAddon.pvp, "sword-place-obsidian", "Right-click with sword to place obsidian, then switch back.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.options == null) return;

        ItemStack mainHand = mc.player.getMainHandStack();
        if (!isSword(mainHand)) return;

        if (mc.options.useKey.isPressed() && !placing) {
            int obsidianSlot = findObsidianSlot();
            if (obsidianSlot == -1) return;

            HitResult hit = mc.crosshairTarget;
            if (!(hit instanceof BlockHitResult bhr)) return;

            placing = true;
            previousSlot = getCurrentSlot();
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(obsidianSlot));

            Vec3d placePos = bhr.getBlockPos().offset(bhr.getSide()).toCenterPos();
            BlockHitResult placeTarget = new BlockHitResult(placePos, bhr.getSide(), bhr.getBlockPos(), false);

            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeTarget);
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        if (placing && !mc.options.useKey.isPressed()) {
            if (previousSlot != -1) {
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
            }
            placing = false;
        }
    }

    private boolean isSword(ItemStack stack) {
        return stack.isOf(Items.WOODEN_SWORD) || stack.isOf(Items.STONE_SWORD) ||
            stack.isOf(Items.IRON_SWORD) || stack.isOf(Items.GOLDEN_SWORD) ||
            stack.isOf(Items.DIAMOND_SWORD) || stack.isOf(Items.NETHERITE_SWORD);
    }

    private int findObsidianSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.OBSIDIAN)) return i;
        }
        return -1;
    }

    private int getCurrentSlot() {
        ItemStack current = mc.player.getMainHandStack();
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i) == current) return i;
        }
        return -1;
    }
}


