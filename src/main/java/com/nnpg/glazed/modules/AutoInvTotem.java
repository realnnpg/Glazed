package com.nnpg.glazed.modules;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class AutoInvTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks before moving totem (1-20 ticks)")
        .defaultValue(3)
        .min(1)
        .max(20)
        .sliderMin(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> moveFromHotbar = sgGeneral.add(new BoolSetting.Builder()
        .name("move-from-hotbar")
        .description("Also move totems from hotbar slots")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableLogs = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-logs")
        .description("Disable chat messages about totem movements")
        .defaultValue(true)
        .build()
    );

    private boolean needsTotem = false;
    private int delayTicks = 0;
    private boolean hadTotemInOffhand = false;

    public AutoInvTotem() {
        super(GlazedAddon.CATEGORY, "Auto Inv Totem", "Automatically moves totems to offhand when inventory is opened after totem pop.");
    }

    @Override
    public void onActivate() {
        if (mc.player != null) {
            hadTotemInOffhand = hasTotemInOffhand();
            needsTotem = false;
            delayTicks = 0;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        boolean currentlyHasTotem = hasTotemInOffhand();

        if (hadTotemInOffhand && !currentlyHasTotem) {
            needsTotem = true;
            if (!disableLogs.get()) {
                info("Totem popped! Open inventory to auto-equip a new one.");
            }

            if (mc.currentScreen instanceof InventoryScreen) {
                if (!disableLogs.get()) {
                    info("Inventory already open - moving totem immediately!");
                }
                delayTicks = delay.get();
            }
        }

        hadTotemInOffhand = currentlyHasTotem;

        if (currentlyHasTotem && needsTotem) {
            needsTotem = false;
            delayTicks = 0;
        }
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!(event.screen instanceof InventoryScreen) || !needsTotem || mc.player == null) return;

        delayTicks = delay.get();
    }

    @EventHandler
    private void onTickDelayed(TickEvent.Post event) {
        if (delayTicks <= 0 || mc.player == null) return;

        delayTicks--;

        if (delayTicks == 0) {
            moveTotemToOffhand();
        }
    }

    private void moveTotemToOffhand() {
        int totemSlot = findTotemSlot();
        if (totemSlot == -1) {
            if (!disableLogs.get()) {
                info("No totem found in inventory!");
            }
            return;
        }

        try {
            int containerSlot = totemSlot;
            if (totemSlot < 9) {
                containerSlot = totemSlot + 36;
            }

            if (!disableLogs.get()) {
                info("Found totem in slot %d (container slot %d)", totemSlot, containerSlot);
            }

            ItemStack offhandStack = mc.player.getOffHandStack();

            if (offhandStack.isEmpty()) {
                mc.interactionManager.clickSlot(0, containerSlot, 40, SlotActionType.SWAP, mc.player);
                if (!disableLogs.get()) {
                    info("Swapped totem to empty offhand");
                }
            } else {
                mc.interactionManager.clickSlot(0, containerSlot, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(0, 45, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(0, containerSlot, 0, SlotActionType.PICKUP, mc.player);
                if (!disableLogs.get()) {
                    info("3-click swapped totem to offhand");
                }
            }

            needsTotem = false;

        } catch (Exception e) {
            if (!disableLogs.get()) {
                error("Failed to move totem: " + e.getMessage());
            }
        }
    }

    private int findTotemSlot() {
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                return i;
            }
        }

        if (moveFromHotbar.get()) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                    return i;
                }
            }
        }

        return -1;
    }

    private boolean hasTotemInOffhand() {
        if (mc.player == null) return false;
        ItemStack offhandStack = mc.player.getOffHandStack();
        return !offhandStack.isEmpty() && offhandStack.getItem() == Items.TOTEM_OF_UNDYING;
    }
}
