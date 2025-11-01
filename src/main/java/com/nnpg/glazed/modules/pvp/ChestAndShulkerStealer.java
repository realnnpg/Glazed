package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class ChestAndShulkerStealer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("steal-delay")
        .description("Delay in ticks between stealing items.")
        .defaultValue(4)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private int tickCounter = 0;
    private int currentSlot = 0;

    public ChestAndShulkerStealer() {
        super(GlazedAddon.pvp, "chest-and-shulker-stealer", "Steals items from chests and shulkers.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        currentSlot = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.currentScreen == null) return;

        int containerSize = 0;

        if (mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler) {
            containerSize = 27;
        } else if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) {
            containerSize = handler.getInventory().size();
        } else {
            return;
        }

        if (++tickCounter < Math.max(1, delay.get())) return;
        tickCounter = 0;

        while (currentSlot < containerSize) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(currentSlot).getStack();
            if (!stack.isEmpty()) {
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    currentSlot,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
                );
                currentSlot++;
                return;
            }
            currentSlot++;
        }

        currentSlot = 0;
    }
}
