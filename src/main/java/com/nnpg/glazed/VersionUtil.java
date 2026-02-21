package com.nnpg.glazed;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

public class VersionUtil {

    public static ItemStack getArmorStack(ClientPlayerEntity player, int slot) {
    return player.getInventory().getStack(slot);
    }

    public static ItemStack getArmorStackByType(ClientPlayerEntity player, int armorType) {
    return player.getInventory().getStack(armorType);
    }

    public static int getSelectedSlot(ClientPlayerEntity player) {
    return player.getInventory().selectedSlot;
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
    return player.getInventory().main;
    }
}
