package com.nnpg.glazed.modules;

import com.nnpg.glazed.VersionUtil;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import com.nnpg.glazed.GlazedAddon;

public class PearlThrow extends Module {

    public PearlThrow() {
        super(GlazedAddon.pvp, "PearlThrow", "When turned on throws an ender pearl(Suggestion: Use a keybind).");
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        int pearlSlot = -1;

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

        //versionutils
        int currentSlot = VersionUtil.getSelectedSlot(mc.player);
        int hotbarIndex = 36 + currentSlot;

        if (pearlSlot >= 0 && pearlSlot <= 8) {
            // Pearl is in hotbar
            //versionutils
            VersionUtil.setSelectedSlot(mc.player, pearlSlot);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            //versionutils
            VersionUtil.setSelectedSlot(mc.player, currentSlot);
        } else {
            int invSlot = pearlSlot;

            mc.interactionManager.clickSlot(0, invSlot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(0, hotbarIndex, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(0, invSlot, 0, SlotActionType.PICKUP, mc.player);

            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);

            mc.interactionManager.clickSlot(0, invSlot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(0, hotbarIndex, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(0, invSlot, 0, SlotActionType.PICKUP, mc.player);
        }

        toggle();
    }
}
