package com.nnpg.glazed.utils;

import net.minecraft.entity.player.PlayerInventory;

import java.lang.reflect.Field;

public final class InventoryUtils {
    private static Field selectedSlotField;

    static {
        try {
            selectedSlotField = PlayerInventory.class.getDeclaredField("selectedSlot");
            selectedSlotField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            selectedSlotField = null;
        }
    }

    private InventoryUtils() {}

    public static int getSelectedSlot(PlayerInventory inv) {
        if (selectedSlotField == null) return 0;
        try {
            return selectedSlotField.getInt(inv);
        } catch (IllegalAccessException e) {
            return 0;
        }
    }

    public static void setSelectedSlot(PlayerInventory inv, int slot) {
        if (selectedSlotField == null) return;
        try {
            selectedSlotField.setInt(inv, slot);
        } catch (IllegalAccessException ignored) {}
    }
}
