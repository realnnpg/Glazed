package com.nnpg.glazed.modules;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
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

    private int tickCounter = 0;
    private boolean hasClickedSlots = false;
    private int warningCooldown = 0;
    private int currentStep = 0; // 0: click 50, 1: click 53, 2: wait and check, 3: click 50 again, 4: click 45, 5: wait and check
    private int checkDelayCounter = 0;
    private static final int CHECK_DELAY = 3; // Wait 3 ticks before checking slot 0
    private static final boolean shouldrepeat = true;

    public SpawnerDropper() {
        super(GlazedAddon.CATEGORY, "SpawnerDropper", "Drops all items from spawners");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Decrement warning cooldown
        if (warningCooldown > 0) {
            warningCooldown--;
        }

        // Check if we're on a screen
        if (!(mc.currentScreen instanceof HandledScreen)) {
            // If module gets toggled and player isn't on a screen, notify them with 1 second cooldown
            if (isActive() && !hasClickedSlots && warningCooldown == 0) {
                warning("You need to be on the spawner screen to use this module.");
                warningCooldown = 20; // 1 second = 20 ticks
            }
            return;
        }

        HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;

        // Handle delayed slot checking
        if (currentStep == 2 || currentStep == 5) {
            checkDelayCounter++;
            if (checkDelayCounter >= CHECK_DELAY) {
                if (screen.getScreenHandler().getSlot(0).getStack().isEmpty()) {
                    info("Dropped all items from spawner.");
                    toggle();
                    return;
                } else {
                    // Items still remain, continue the cycle
                    if (currentStep == 2) {
                        currentStep = 3; // Continue to click 50 again
                    } else if (currentStep == 5) {
                        currentStep = 0; // Loop back to start (click 50)
                    }
                }
                checkDelayCounter = 0;
            }
            return;
        }

        // Increment tick counter
        tickCounter++;

        // Only perform actions if enough ticks have passed (delay)
        if (tickCounter < delay.get()) {
            return;
        }

        // Reset tick counter
        tickCounter = 0;
        hasClickedSlots = true;

        // Execute clicks step by step with delay between each
        switch (currentStep) {
            case 0:
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, 50, 0, SlotActionType.PICKUP, mc.player);
                currentStep = 1;
                break;
            case 1:
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, 53, 0, SlotActionType.PICKUP, mc.player);
                currentStep = 2; // Go to waiting/checking step
                checkDelayCounter = 0;
                break;
            case 3:
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, 50, 0, SlotActionType.PICKUP, mc.player);
                currentStep = 4;
                break;
            case 4:
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, 45, 0, SlotActionType.PICKUP, mc.player);
                currentStep = 5; // Go to waiting/checking step
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
