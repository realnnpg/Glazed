package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class SpawnerDropper extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between clicks in ticks.")
        .defaultValue(5)
        .min(1)
        .max(20)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> boneOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("bone-only")
        .description("Only drop bones. Stops when arrows are detected in slots 0-44.")
        .defaultValue(false)
        .build()
    );

    private int tickCounter = 0;
    private boolean hasClickedSlots = false;
    private int warningCooldown = 0;
    private int currentStep = 0;
    private int checkDelayCounter = 0;
    private static final int CHECK_DELAY = 3;
    private static final boolean shouldrepeat = true;

    public SpawnerDropper() {
        super(GlazedAddon.CATEGORY, "SpawnerDropper", "Drops all items from spawners");
    }

    private boolean hasArrowsInInventory(HandledScreen<?> screen) {
        for (int i = 0; i <= 44; i++) {
            if (!screen.getScreenHandler().getSlot(i).getStack().isEmpty() &&
                screen.getScreenHandler().getSlot(i).getStack().getItem() == Items.ARROW) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.interactionManager == null) return;

        if (warningCooldown > 0) {
            warningCooldown--;
        }

        if (!(mc.currentScreen instanceof HandledScreen)) {
            if (isActive() && !hasClickedSlots && warningCooldown == 0) {
                warning("You need to be on the spawner screen to use this module.");
                warningCooldown = 20;
            }
            return;
        }

        HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;

        if (boneOnly.get()) {
            if ((currentStep == 0 && tickCounter == 0) ||
                (currentStep == 2 && checkDelayCounter == 0) ||
                (currentStep == 5 && checkDelayCounter == 0)) {

                if (hasArrowsInInventory(screen)) {
                    info("Found arrows in inventory - all bones have been dropped.");
                    toggle();
                    return;
                }
            }
        }

        if (currentStep == 2 || currentStep == 5) {
            checkDelayCounter++;
            if (checkDelayCounter >= CHECK_DELAY) {
                if (screen.getScreenHandler().getSlot(0).getStack().isEmpty()) {
                    info("Dropped all items from spawner.");
                    toggle();
                    return;
                } else {
                    if (currentStep == 2) {
                        currentStep = 3;
                    } else if (currentStep == 5) {
                        currentStep = 0;
                    }
                }
                checkDelayCounter = 0;
            }
            return;
        }

        tickCounter++;

        if (tickCounter < delay.get()) {
            return;
        }

        tickCounter = 0;
        hasClickedSlots = true;

        switch (currentStep) {
            case 0:
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, 50, 0, SlotActionType.PICKUP, mc.player);
                currentStep = 1;
                break;
            case 1:
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, 53, 0, SlotActionType.PICKUP, mc.player);
                currentStep = 2;
                checkDelayCounter = 0;
                break;
            case 3:
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, 50, 0, SlotActionType.PICKUP, mc.player);
                currentStep = 4;
                break;
            case 4:
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, 53, 0, SlotActionType.PICKUP, mc.player);
                currentStep = 5;
                checkDelayCounter = 0;
                break;
        }
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        hasClickedSlots = false;
        warningCooldown = 0;
        currentStep = 0;
        checkDelayCounter = 0;
    }

    @Override
    public void onDeactivate() {
        currentStep = 0;
        checkDelayCounter = 0;
        mc.setScreen(null);
    }
}
