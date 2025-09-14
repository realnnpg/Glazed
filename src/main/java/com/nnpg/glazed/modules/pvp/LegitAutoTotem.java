package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public final class LegitAutoTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks before moving totem")
        .defaultValue(1)
        .min(0)
        .max(5)
        .sliderMin(0)
        .sliderMax(5)
        .build()
    );

    private int delayCounter;

    public LegitAutoTotem() {
        super(GlazedAddon.pvp, "Legit Auto Totem", "MIGHT BE DETECTABLE");
    }

    @Override
    public void onActivate() {
        delayCounter = 0;
        warning("Might be detectable");
    }

    @Override
    public void onDeactivate() {
        delayCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) {
            return;
        }

        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            delayCounter = delay.get();
            return;
        }

        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        int slot = findItemSlot(Items.TOTEM_OF_UNDYING);
        if (slot == -1) {
            return;
        }

        int containerSlot = convertSlotIndex(slot);
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, containerSlot, 40, SlotActionType.SWAP, mc.player);

        delayCounter = delay.get();
    }

    private int findItemSlot(Item item) {
        if (mc.player == null) {
            return -1;
        }

        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) {
                return i;
            }
        }
        return -1;
    }

    private static int convertSlotIndex(int slotIndex) {
        if (slotIndex < 9) {
            return 36 + slotIndex;
        }
        return slotIndex;
    }
}
