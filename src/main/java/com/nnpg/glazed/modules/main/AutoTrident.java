package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class AutoTrident extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> chargeLevels = sgGeneral.add(new IntSetting.Builder()
        .name("charge-levels")
        .description("How many levels to charge the trident before releasing. Each level is ~10 ticks.")
        .defaultValue(1)
        .min(1)
        .max(10)
        .sliderMin(1)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> extraTicks = sgGeneral.add(new IntSetting.Builder()
        .name("extra-ticks")
        .description("Extra ticks to hold after reaching the level threshold.")
        .defaultValue(0)
        .min(0)
        .max(60)
        .sliderMin(0)
        .sliderMax(60)
        .build()
    );

    private final Setting<Boolean> lockPitch = sgGeneral.add(new BoolSetting.Builder()
        .name("lock-pitch")
        .description("Lock pitch while charging to throw straight.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> pitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch")
        .description("Pitch to lock to when charging (0 = straight forward, -90 = straight up, 90 = straight down).")
        .defaultValue(0.0)
        .min(-90.0)
        .max(90.0)
        .sliderMin(-90.0)
        .sliderMax(90.0)
        .visible(lockPitch::get)
        .build()
    );

    private final Setting<Boolean> autoSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-swap")
        .description("Automatically swap to a trident in your hotbar when enabled.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> repeat = sgGeneral.add(new BoolSetting.Builder()
        .name("repeat")
        .description("Continuously charge and release (useful for Riptide in rain/water).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> repeatCooldown = sgGeneral.add(new IntSetting.Builder()
        .name("repeat-cooldown")
        .description("Cooldown in ticks between throws when repeat is enabled.")
        .defaultValue(1)
        .min(1)
        .max(60)
        .sliderMin(0)
        .sliderMax(60)
        .visible(repeat::get)
        .build()
    );

    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-disable")
        .description("Automatically disable the module after one throw when repeat is off.")
        .defaultValue(true)
        .build()
    );

    private boolean isCharging = false;
    private boolean isUseKeyHeld = false;
    private int chargeTicks = 0;
    private int cooldownTicks = 0;

    public AutoTrident() {
        super(GlazedAddon.CATEGORY, "AutoTrident", "Charge and release a trident straight with configurable charge levels.");
    }

    @Override
    public void onActivate() {
        isCharging = false;
        isUseKeyHeld = false;
        chargeTicks = 0;
        cooldownTicks = 0;

        if (mc.player == null) return;

        if (!isHoldingTrident() && autoSwap.get()) {
            FindItemResult trident = InvUtils.findInHotbar(Items.TRIDENT);
            if (trident.found()) {
                InvUtils.swap(trident.slot(), false);
            }
        }
    }

    @Override
    public void onDeactivate() {
        releaseUseKey();
        isCharging = false;
        chargeTicks = 0;
        cooldownTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return; // Don't act in GUIs

        if (!isHoldingTrident()) {
            if (autoSwap.get()) {
                FindItemResult trident = InvUtils.findInHotbar(Items.TRIDENT);
                if (trident.found()) InvUtils.swap(trident.slot(), false);
            }
            if (!isHoldingTrident()) {
                ChatUtils.warning("AutoTrident: No trident in hand.");
                if (autoDisable.get()) toggle();
                return;
            }
        }

        if (lockPitch.get()) {
            mc.player.setPitch(pitch.get().floatValue());
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        int neededTicks = chargeLevels.get() * 10 + extraTicks.get();
        if (!isCharging) {
            pressUseKey();
            isCharging = true;
            chargeTicks = 0;
        } else {
            chargeTicks++;
            if (chargeTicks >= neededTicks) {
                releaseUseKey();
                isCharging = false;
                chargeTicks = 0;

                if (repeat.get()) {
                    cooldownTicks = repeatCooldown.get();
                } else if (autoDisable.get()) {
                    toggle();
                }
            }
        }
    }

    private boolean isHoldingTrident() {
        ItemStack main = mc.player.getMainHandStack();
        return main != null && main.getItem() == Items.TRIDENT;
    }

    private void pressUseKey() {
        if (!isUseKeyHeld && mc.options != null) {
            KeyBinding.setKeyPressed(mc.options.useKey.getDefaultKey(), true);
            isUseKeyHeld = true;
        }
    }

    private void releaseUseKey() {
        if (isUseKeyHeld && mc.options != null) {
            KeyBinding.setKeyPressed(mc.options.useKey.getDefaultKey(), false);
            isUseKeyHeld = false;
        }
    }

    @Override
    public String getInfoString() {
        if (isCharging) {
            int needed = chargeLevels.get() * 10 + extraTicks.get();
            return "Charging " + Math.min(chargeTicks, needed) + "/" + needed + " ticks";
        }
        if (cooldownTicks > 0) return "Cooldown: " + cooldownTicks;
        return repeat.get() ? "Waiting" : null;
    }
}
