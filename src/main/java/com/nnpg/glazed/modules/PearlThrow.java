package com.nnpg.glazed.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import com.nnpg.glazed.GlazedAddon;

public class PearlThrow extends Module {

    public PearlThrow() {
        super(GlazedAddon.CATEGORY, "PearlThrow", "When turned on throws an ender pearl(Suggestion: Use a keybind).");
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        int pearlSlot = -1;

        // Search entire inventory (0–35) for ender pearl
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.ENDER_PEARL) {
                pearlSlot = i;
                break;
            }
        }

        if (pearlSlot == -1) {
            info("No ender pearl found in inventory!");
            toggle();
            return;
        }

        int currentSlot = mc.player.getInventory().selectedSlot;
        int hotbarIndex = 36 + currentSlot;

        if (pearlSlot >= 0 && pearlSlot <= 8) {
            // Pearl is in hotbar
            mc.player.getInventory().selectedSlot = pearlSlot;
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.getInventory().selectedSlot = currentSlot;
        } else {
            // Save the current item in hand
            int invSlot = pearlSlot;

            // Pick up the pearl
            mc.interactionManager.clickSlot(0, invSlot, 0, SlotActionType.PICKUP, mc.player);
            // Pick up item in hand to put it in the pearl's old slot
            mc.interactionManager.clickSlot(0, hotbarIndex, 0, SlotActionType.PICKUP, mc.player);
            // Place old item into pearl's original slot
            mc.interactionManager.clickSlot(0, invSlot, 0, SlotActionType.PICKUP, mc.player);

            // Throw the pearl now in hand
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);

            // Wait a tick might be safer, but we immediately reverse:
            // Pick up pearl slot again (now empty or with remaining pearls)
            mc.interactionManager.clickSlot(0, invSlot, 0, SlotActionType.PICKUP, mc.player);
            // Pick up original item from hotbar again
            mc.interactionManager.clickSlot(0, hotbarIndex, 0, SlotActionType.PICKUP, mc.player);
            // Put it back into hotbar
            mc.interactionManager.clickSlot(0, invSlot, 0, SlotActionType.PICKUP, mc.player);
        }

        toggle(); // Disable after throw
    }
}
