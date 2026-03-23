package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.utils.glazed.MovementKeys;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.entity.LivingEntity;

import static com.nnpg.glazed.GlazedAddon.pvp;

public class STabSprintReset extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTiming  = settings.createGroup("Timing");

    private final Setting<Double> skipChance = sgGeneral.add(new DoubleSetting.Builder()
        .name("skip-chance")
        .description("Chance to skip the reset entirely (mimics human inconsistency)")
        .defaultValue(20.0)
        .min(0.0)
        .max(100.0)
        .sliderMax(100.0)
        .build()
    );

    private final Setting<Boolean> advancedSettings = sgGeneral.add(new BoolSetting.Builder()
        .name("advanced-settings")
        .description("Show advanced timing settings")
        .defaultValue(false)
        .build()
    );
    private final Setting<Integer> preDelayMin = sgTiming.add(new IntSetting.Builder()
        .name("pre-delay-min")
        .description("Minimum ticks before pressing S (1 = earliest human-possible)")
        .defaultValue(1)
        .min(1)
        .max(5)
        .sliderMax(5)
        .visible(() -> advancedSettings.get())
        .build()
    );

    private final Setting<Integer> preDelayMax = sgTiming.add(new IntSetting.Builder()
        .name("pre-delay-max")
        .description("Maximum ticks before pressing S")
        .defaultValue(3)
        .min(1)
        .max(8)
        .sliderMax(8)
        .visible(() -> advancedSettings.get())
        .build()
    );

    private final Setting<Integer> sHoldMin = sgTiming.add(new IntSetting.Builder()
        .name("s-hold-min")
        .description("Minimum ticks to hold S")
        .defaultValue(1)
        .min(1)
        .max(5)
        .sliderMax(5)
        .visible(() -> advancedSettings.get())
        .build()
    );

    private final Setting<Integer> sHoldMax = sgTiming.add(new IntSetting.Builder()
        .name("s-hold-max")
        .description("Maximum ticks to hold S")
        .defaultValue(3)
        .min(1)
        .max(8)
        .sliderMax(8)
        .visible(() -> advancedSettings.get())
        .build()
    );

    private boolean sKeyPressed        = false;
    private int     preDelayTicks      = 0;
    private int     sHoldTicks         = 0;
    private int     currentPreDelay    = 0;
    private int     currentSHold       = 0;
    private boolean waitingForPreDelay = false;
    private boolean waitingForSRelease = false;
    private long    releaseAtMs        = -1L;
    private long    lastResetTimeMs    = 0L;
    private static final int MIN_RESET_INTERVAL_MS = 150;
    private boolean wasSprinting = false;

    public STabSprintReset() {
        super(pvp, "s-tab-sprint-reset", "Prevents sprint restart after attack with S-tap");
    }

    @Override
    public void onDeactivate() {
        if (sKeyPressed) {
            MovementKeys.back(false);
            sKeyPressed = false;
        }
        resetState();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.player.isDead() || mc.world == null) {
            if (sKeyPressed) {
                MovementKeys.back(false);
                sKeyPressed = false;
            }
            resetState();
            return;
        }
        wasSprinting = mc.player.isSprinting();
        if (waitingForSRelease && !mc.player.isOnGround()) {
            releaseS();
            return;
        }
        if (waitingForPreDelay) {
            preDelayTicks++;
            if (preDelayTicks >= currentPreDelay) {
                pressS();
            }
            return;
        }
        if (waitingForSRelease) {
            sHoldTicks++;
            if (sHoldTicks >= currentSHold && releaseAtMs < 0) {
                int jitterMs = (int)(Math.random() * 20);
                releaseAtMs = System.currentTimeMillis() + jitterMs;
            }
            if (releaseAtMs >= 0 && System.currentTimeMillis() >= releaseAtMs) {
                releaseAtMs = -1L;
                releaseS();
            }
            return;
        }
        if (sKeyPressed) {
            releaseS();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onAttack(AttackEntityEvent event) {
        if (!(event.entity instanceof LivingEntity)) return;
        if (!mc.player.isOnGround()) return;
        if (!wasSprinting) return;
        long now = System.currentTimeMillis();
        if (now - lastResetTimeMs < MIN_RESET_INTERVAL_MS) return;
        if (sKeyPressed) releaseS();
        if (Math.random() * 100 < skipChance.get()) return;
        currentPreDelay    = rollPreDelay();
        currentSHold       = rollSHold();
        preDelayTicks      = 0;
        sHoldTicks         = 0;
        releaseAtMs        = -1L;
        waitingForPreDelay = true;
        waitingForSRelease = false;
        lastResetTimeMs    = now;
    }

    private int rollPreDelay() {
        int min = preDelayMin.get();
        int max = preDelayMax.get();

        double r = Math.random();
        int rolled;
        if      (r < 0.40) rolled = 1;
        else if (r < 0.80) rolled = 2;
        else               rolled = 3;

        return Math.max(min, Math.min(max, rolled));
    }

    private int rollSHold() {
        int min = sHoldMin.get();
        int max = sHoldMax.get();

        double r = Math.random();
        int rolled;
        if      (r < 0.30) rolled = 1;
        else if (r < 0.80) rolled = 2;
        else               rolled = 3;

        return Math.max(min, Math.min(max, rolled));
    }

    private void pressS() {
        MovementKeys.back(true);
        sKeyPressed = true;

        waitingForPreDelay = false;
        waitingForSRelease = true;
        sHoldTicks         = 0;
        releaseAtMs        = -1L;
    }

    private void releaseS() {
        MovementKeys.back(false);
        sKeyPressed = false;
        resetState();
    }

    private void resetState() {
        waitingForPreDelay = false;
        waitingForSRelease = false;
        preDelayTicks      = 0;
        sHoldTicks         = 0;
        currentPreDelay    = 0;
        currentSHold       = 0;
        releaseAtMs        = -1L;
    }
}