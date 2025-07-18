//1.21.4

package com.nnpg.glazed;

import net.minecraft.SharedConstants;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

public class VersionUtil {
    public static ItemStack getArmorStack(ClientPlayerEntity player, int slot) {
        return player.getInventory().getArmorStack(slot);
    }

    public static int getSelectedSlot(ClientPlayerEntity player) {
        return player.getInventory().selectedSlot;
    }

    public static void setSelectedSlot(ClientPlayerEntity player, int slot) {
        player.getInventory().selectedSlot = slot;
    }
}

