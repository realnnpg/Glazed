package com.nnpg.glazed;

import net.minecraft.SharedConstants;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

public class VersionUtil {

    public static ItemStack getArmorStack(ClientPlayerEntity player, int slot) {
        return player.getInventory().getStack(38); // Chest slot is 38 in 1.21.5
    }

    public static ItemStack getArmorStackByType(ClientPlayerEntity player, int armorType) {
        // armorType: 0=boots, 1=leggings, 2=chestplate, 3=helmet
        return player.getInventory().getStack(36 + armorType); // Armor slots are 36-39
    }



    public static int getSelectedSlot(ClientPlayerEntity player) {
        return player.getInventory().getSelectedSlot();
    }

    public static void setSelectedSlot(ClientPlayerEntity player, int slot) {
        player.getInventory().setSelectedSlot(slot);
    }

    public static double getPrevX(net.minecraft.entity.Entity entity) {
        return entity.lastRenderX;
    }

    public static double getPrevY(net.minecraft.entity.Entity entity) {
        return entity.lastRenderY;
    }

    public static double getPrevZ(net.minecraft.entity.Entity entity) {
        return entity.lastRenderZ;
    }

    public static DefaultedList<ItemStack> getMainInventory(ClientPlayerEntity player) {
        return player.getInventory().getMainStacks(); // Method name changed in 1.21.5
    }
}
