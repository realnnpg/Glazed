//1.21.5
package com.nnpg.glazed;

import net.minecraft.SharedConstants;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

public class VersionUtil {

    // -------------------------------
    // AutoFirework
    // -------------------------------

    public static ItemStack getArmorStack(ClientPlayerEntity player, int slot) {
        return player.getInventory().getStack(38); // Chest slot is 38 in 1.21.5
    }

    public static int getSelectedSlot(ClientPlayerEntity player) {
        return player.getInventory().getSelectedSlot();
    }

    public static void setSelectedSlot(ClientPlayerEntity player, int slot) {
        player.getInventory().setSelectedSlot(slot);
    }
}

