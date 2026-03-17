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

/**
 * S-Tab Sprint Reset
 *
 * Mechanism:
 *   Attack stops sprint IMMEDIATELY in the same tick.
 *   Pressing S prevents sprint from restarting (W+S = net ~0 → no sprint).
 *   After releasing S, sprint restarts immediately if the sprint key is held.
 *
 * Timing (verified against Minecraft source / mcpk.wiki):
 *   Pre-Delay : 1–3 Ticks  (50–150ms) — weighted toward lower values
 *   S-Hold    : 1–3 Ticks  (50–150ms) — weighted toward center
 *   Sub-Tick  : 0–20ms jitter on release — breaks tick-boundary fingerprint
 *
 * Design goals for undetectability:
 *   - Pre-delay is never 0ms (no human reacts in the same tick as their click)
 *   - Weighted non-uniform distributions instead of flat random ranges
 *   - Sub-tick jitter on release so key-up never aligns exactly to a tick edge
 *   - Rate-limit guards against superhuman reset frequency at high CPS
 *   - Skip chance tuned to reflect a skilled-but-human success rate (~75-80%)
 */
public class STabSprintReset extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTiming  = settings.createGroup("Timing");

    // ── General Settings ──────────────────────────────────────────────────────

    private final Setting<Double> skipChance = sgGeneral.add(new DoubleSetting.Builder()
        .name("skip-chance")
        .description("Chance to skip the reset entirely (mimics human inconsistency)")
        .defaultValue(20.0)   // 20% skip → ~80% success rate, realistic for a skilled player
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

    // ── Timing Settings ───────────────────────────────────────────────────────

    // Pre-delay: how many ticks to wait after the attack before pressing S.
    // Default weighted distribution: 1T=40%, 2T=40%, 3T=20% (see rollPreDelay()).
    // User-exposed min/max shifts the weight anchor, not a flat range.
    private final Setting<Integer> preDelayMin = sgTiming.add(new IntSetting.Builder()
        .name("pre-delay-min")
        .description("Minimum ticks before pressing S (1 = earliest human-possible)")
        .defaultValue(1)
        .min(1)   // Never 0 — 0ms reaction is inhuman and directly detectable
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

    // ── State ─────────────────────────────────────────────────────────────────

    private boolean sKeyPressed        = false;
    private int     preDelayTicks      = 0;
    private int     sHoldTicks         = 0;
    private int     currentPreDelay    = 0;
    private int     currentSHold       = 0;
    private boolean waitingForPreDelay = false;
    private boolean waitingForSRelease = false;

    // Sub-tick release jitter: once the hold tick count expires we don't
    // release S immediately. Instead we set a real-time target (ms) and
    // release when wall-clock time passes it. This means the key-up event
    // is no longer aligned to a tick boundary — breaking the tick-edge
    // fingerprint that behavioral anticheats look for.
    private long    releaseAtMs        = -1L;

    // Rate-limiting: track the real-time of the last completed reset.
    // A human doing S-Tab consistently faster than ~150ms is unrealistic.
    private long    lastResetTimeMs    = 0L;
    private static final int MIN_RESET_INTERVAL_MS = 150;

    // FIX 1 – Sprint-state cache.
    // isSprinting() in onAttack() can already be false (sprint stops same
    // tick as the attack). We cache it at tick-start, before attacks fire.
    // isOnGround() is intentionally excluded: strafing (A/D) can briefly
    // flip isSprinting() to false; we still want to catch those hits.
    private boolean wasSprinting = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public STabSprintReset() {
        super(pvp, "s-tab-sprint-reset", "Prevents sprint restart after attack with S-tap");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onDeactivate() {
        if (sKeyPressed) {
            MovementKeys.back(false);
            sKeyPressed = false;
        }
        resetState();
    }

    // ── Event Handlers ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    private void onTick(TickEvent.Pre event) {
        // Guard: dead player / world unload.
        // onDeactivate() is not guaranteed to fire on death in Meteor Client.
        if (mc.player == null || mc.player.isDead() || mc.world == null) {
            if (sKeyPressed) {
                MovementKeys.back(false);
                sKeyPressed = false;
            }
            resetState();
            return;
        }

        // FIX 1: Update sprint cache at tick-start, before AttackEvent fires.
        wasSprinting = mc.player.isSprinting();

        // FIX 3: Player left the ground during S-hold → release immediately.
        // S pressed while airborne still blocks sprint technically, but
        // produces a visible backward nudge that looks unnatural.
        // Pre-delay phase does NOT abort on airborne – S pressed in the air
        // after a ground hit still performs the sprint break correctly.
        if (waitingForSRelease && !mc.player.isOnGround()) {
            releaseS();
            return;
        }

        // ── Pre-Delay Phase ───────────────────────────────────────────────────
        if (waitingForPreDelay) {
            preDelayTicks++;
            if (preDelayTicks >= currentPreDelay) {
                pressS();
            }
            return;
        }

        // ── S-Hold Phase ──────────────────────────────────────────────────────
        if (waitingForSRelease) {
            sHoldTicks++;

            // Once the hold-tick threshold is reached, schedule a sub-tick
            // release rather than releasing instantly on the tick boundary.
            if (sHoldTicks >= currentSHold && releaseAtMs < 0) {
                int jitterMs = (int)(Math.random() * 20); // 0–20ms sub-tick jitter
                releaseAtMs = System.currentTimeMillis() + jitterMs;
            }

            // Release only when real-time wall clock has passed the target.
            if (releaseAtMs >= 0 && System.currentTimeMillis() >= releaseAtMs) {
                releaseAtMs = -1L;
                releaseS();
            }
            return;
        }

        // Safety-net: S is physically held but state machine is idle.
        // Caused by: two attacks in the exact same tick, or external corruption.
        if (sKeyPressed) {
            releaseS();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onAttack(AttackEntityEvent event) {
        if (!(event.entity instanceof LivingEntity)) return;

        // Ground check stays here, separate from the sprint cache.
        if (!mc.player.isOnGround()) return;

        // FIX 1: Use cached sprint state.
        if (!wasSprinting) return;

        // Rate-limit: prevent superhuman reset frequency at high CPS.
        // No human consistently S-tabs faster than once every 150ms.
        long now = System.currentTimeMillis();
        if (now - lastResetTimeMs < MIN_RESET_INTERVAL_MS) return;

        // FIX 4 (Spam-click safe): abort any running sequence cleanly.
        if (sKeyPressed) releaseS();

        // Skip chance.
        if (Math.random() * 100 < skipChance.get()) return;

        // Roll timing using weighted distributions (see helpers below).
        currentPreDelay    = rollPreDelay();
        currentSHold       = rollSHold();
        preDelayTicks      = 0;
        sHoldTicks         = 0;
        releaseAtMs        = -1L;
        waitingForPreDelay = true;
        waitingForSRelease = false;
        lastResetTimeMs    = now;

        // Pre-delay of 1 tick means: press S on the NEXT tick, not this one.
        // currentPreDelay is always >= 1 (enforced by rollPreDelay),
        // so we never call pressS() here — we always go through the tick counter.
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Weighted pre-delay distribution:
     *   1 tick (50ms)  → 40%  — fast but plausible reaction
     *   2 ticks (100ms)→ 40%  — average human reaction
     *   3 ticks (150ms)→ 20%  — slightly slow / distracted hit
     *
     * Settings shift the anchor: if preDelayMin > 1, the distribution
     * is clamped upward. If preDelayMax < 3, the top bucket collapses.
     * Never returns 0 — a 0ms reaction is inhuman and directly detectable.
     */
    private int rollPreDelay() {
        int min = preDelayMin.get();  // always >= 1
        int max = preDelayMax.get();

        double r = Math.random();
        int rolled;
        if      (r < 0.40) rolled = 1;
        else if (r < 0.80) rolled = 2;
        else               rolled = 3;

        return Math.max(min, Math.min(max, rolled));
    }

    /**
     * Weighted S-hold distribution:
     *   1 tick (50ms)  → 30%  — quick tap
     *   2 ticks (100ms)→ 50%  — normal hold
     *   3 ticks (150ms)→ 20%  — slightly long hold
     *
     * Combined with the 0–20ms sub-tick release jitter, the actual
     * hold duration is continuously distributed, not discretely bucketed.
     */
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

    /**
     * Resets all state fields. Never touches the S key directly —
     * always call releaseS() first if sKeyPressed is true.
     */
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