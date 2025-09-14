package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.mixins.HandledScreenMixin;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class HoverTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("tick-delay")
        .description("Ticks to wait between operations.")
        .defaultValue(0)
        .min(0)
        .max(20)
        .sliderMin(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> hotbarTotem = sgGeneral.add(new BoolSetting.Builder()
        .name("hotbar-totem")
        .description("Also places a totem in your preferred hotbar slot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> hotbarSlot = sgGeneral.add(new IntSetting.Builder()
        .name("hotbar-slot")
        .description("Your preferred hotbar slot for totem (1-9).")
        .defaultValue(1)
        .min(1)
        .max(9)
        .sliderMin(1)
        .sliderMax(9)
        .build()
    );

    private final Setting<Boolean> autoSwitchToTotem = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch-to-totem")
        .description("Automatically switches to totem slot when inventory is opened.")
        .defaultValue(false)
        .build()
    );

    private int remainingDelay;

    public HoverTotem() {
        super(GlazedAddon.pvp, "hover-totem", "Equips a totem in offhand and optionally hotbar when hovering over one in inventory.");
    }

    @Override
    public void onActivate() {
        resetDelay();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        Screen currentScreen = mc.currentScreen;
        if (!(currentScreen instanceof InventoryScreen inventoryScreen)) {
            resetDelay();
            return;
        }

        // Safely get focused slot with proper error handling
        Slot focusedSlot = getFocusedSlotSafe(inventoryScreen);

        if (focusedSlot == null || focusedSlot.getIndex() > 35) return;

        if (autoSwitchToTotem.get()) {
            mc.player.getInventory().setSelectedSlot(hotbarSlot.get() - 1);
        }

        if (!focusedSlot.getStack().isOf(Items.TOTEM_OF_UNDYING)) return;

        if (remainingDelay > 0) {
            remainingDelay--;
            return;
        }

        int slotIndex = focusedSlot.getIndex();
        int syncId = inventoryScreen.getScreenHandler().syncId;

        // Equip totem in offhand if not already there
        if (!mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            equipOffhandTotem(syncId, slotIndex);
            return;
        }

        // Equip totem in hotbar if enabled and not already there
        if (hotbarTotem.get()) {
            int hotbarIndex = hotbarSlot.get() - 1;
            if (!mc.player.getInventory().getStack(hotbarIndex).isOf(Items.TOTEM_OF_UNDYING)) {
                equipHotbarTotem(syncId, slotIndex, hotbarIndex);
            }
        }
    }

    private void equipOffhandTotem(int syncId, int slotIndex) {
        mc.interactionManager.clickSlot(syncId, slotIndex, 40, SlotActionType.SWAP, mc.player);
        resetDelay();
    }

    private void equipHotbarTotem(int syncId, int slotIndex, int hotbarIndex) {
        mc.interactionManager.clickSlot(syncId, slotIndex, hotbarIndex, SlotActionType.SWAP, mc.player);
        resetDelay();
    }

    private void resetDelay() {
        remainingDelay = tickDelay.get();
    }

    private Slot getFocusedSlotSafe(InventoryScreen screen) {
        try {
            // Try using the mixin first
            if (screen instanceof HandledScreenMixin mixin) {
                return mixin.glazed$getFocusedSlot();
            }

            // Fallback to reflection
            return getFocusedSlotReflection(screen);
        } catch (Exception e) {
            // If everything fails, return null
            return null;
        }
    }

    private Slot getFocusedSlotReflection(InventoryScreen screen) {
        try {
            // Try multiple possible field names for different mappings
            String[] fieldNames = {"focusedSlot", "field_7528", "f_96807_"};

            Class<?> handledScreenClass = screen.getClass().getSuperclass();

            for (String fieldName : fieldNames) {
                try {
                    java.lang.reflect.Field field = handledScreenClass.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return (Slot) field.get(screen);
                } catch (NoSuchFieldException ignored) {
                    // Try next field name
                }
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
