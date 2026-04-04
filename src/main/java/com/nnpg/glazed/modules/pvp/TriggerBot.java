package com.nnpg.glazed.modules.pvp;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.nnpg.glazed.GlazedAddon;

import java.util.Optional;

public class TriggerBot extends Module {

    private final SettingGroup sgFilter = settings.createGroup("Filter");
    private final SettingGroup sgAttack = settings.createGroup("Attack");

    private final Setting<Target> target = sgFilter.add(new EnumSetting.Builder<Target>()
        .name("target")
        .description("Which entities to attack.")
        .defaultValue(Target.Players)
        .build()
    );

    private final Setting<Double> range = sgFilter.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum attack range. Warning: high range is easily detectable!")
        .defaultValue(3.0)
        .min(0.1)
        .sliderMax(4.5)
        .build()
    );
    // test
    private final Setting<Boolean> ignoreFriends = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Won't attack players on your friends list.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreWalls = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-walls")
        .description("Attack entities through walls.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> hitWindowMs = sgAttack.add(new IntSetting.Builder()
        .name("hit-window-ms")
        .description("How long (ms) after the crosshair last touched a target the attack "
                   + "is still allowed. Catches fast flick-overs between ticks. "
                   + "50ms = 1 tick. 0 = disabled.")
        .defaultValue(50)
        .min(0)
        .sliderRange(0, 150)
        .build()
    );

    private final Setting<OnFallMode> onFallMode = sgAttack.add(new EnumSetting.Builder<OnFallMode>()
        .name("on-fall-mode")
        .description("Only attack while falling (for critical hits). "
                   + "None = always attack, Value = fixed velocity threshold, "
                   + "RandomValue = randomised threshold.")
        .defaultValue(OnFallMode.None)
        .build()
    );

    private final Setting<Double> onFallValue = sgAttack.add(new DoubleSetting.Builder()
        .name("on-fall-velocity")
        .description("Minimum downward velocity required to attack. "
                   + "0.1 = just past the jump peak (recommended), "
                   + "0.3 = deeper into the fall.")
        .min(0.0)
        .defaultValue(0.1)
        .sliderRange(0.0, 1.0)
        .visible(() -> onFallMode.get() == OnFallMode.Value)
        .build()
    );

    private final Setting<Double> onFallMinRandomValue = sgAttack.add(new DoubleSetting.Builder()
        .name("on-fall-min-random-velocity")
        .description("Minimum of the randomised downward velocity threshold.")
        .min(0.0)
        .defaultValue(0.1)
        .sliderRange(0.0, 1.0)
        .visible(() -> onFallMode.get() == OnFallMode.RandomValue)
        .build()
    );

    private final Setting<Double> onFallMaxRandomValue = sgAttack.add(new DoubleSetting.Builder()
        .name("on-fall-max-random-velocity")
        .description("Maximum of the randomised downward velocity threshold.")
        .min(0.0)
        .defaultValue(0.3)
        .sliderRange(0.0, 1.0)
        .visible(() -> onFallMode.get() == OnFallMode.RandomValue)
        .build()
    );

    private final Setting<HitSpeedMode> hitSpeedMode = sgAttack.add(new EnumSetting.Builder<HitSpeedMode>()
        .name("hit-speed-mode")
        .description("Minimum attack cooldown required before attacking.")
        .defaultValue(HitSpeedMode.RandomValue)
        .build()
    );

    private final Setting<Double> hitSpeedValue = sgAttack.add(new DoubleSetting.Builder()
        .name("hit-speed-value")
        .description("Cooldown offset passed to getAttackCooldownProgress. 0 = full cooldown required.")
        .defaultValue(0.0)
        .sliderRange(-10, 10)
        .visible(() -> hitSpeedMode.get() == HitSpeedMode.Value)
        .build()
    );

    private final Setting<Double> hitSpeedMinRandomValue = sgAttack.add(new DoubleSetting.Builder()
        .name("hit-speed-min-random-value")
        .description("Minimum randomised cooldown offset value.")
        .defaultValue(-0.1)
        .sliderRange(-10, 10)
        .visible(() -> hitSpeedMode.get() == HitSpeedMode.RandomValue)
        .build()
    );

    private final Setting<Double> hitSpeedMaxRandomValue = sgAttack.add(new DoubleSetting.Builder()
        .name("hit-speed-max-random-value")
        .description("Maximum randomised cooldown offset value.")
        .defaultValue(0.05)
        .sliderRange(-10, 10)
        .visible(() -> hitSpeedMode.get() == HitSpeedMode.RandomValue)
        .build()
    );

    private float randomOnFallFloat   = 0;
    private float randomHitSpeedFloat = 0;
    private Entity bufferedTarget = null;
    private long   lastSeenNano   = 0L;

    public TriggerBot() {
        super(GlazedAddon.pvp, "triggerbot",
            "Attacks the entity you are looking at, optionally only when critting.");
    }

    @Override
    public void onActivate() {
        randomOnFallFloat   = 0;
        randomHitSpeedFloat = 0;
        bufferedTarget      = null;
        lastSeenNano        = 0L;
    }
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;
        Entity found = getTarget();
        if (found != null) {
            bufferedTarget = found;
            lastSeenNano   = System.nanoTime();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.player.isDead()
                || mc.player.getHealth() <= 0 || mc.world == null) return;
        Entity entity = getTarget();

        if (entity == null) {
            long elapsedMs = (System.nanoTime() - lastSeenNano) / 1_000_000L;
            if (bufferedTarget == null || elapsedMs > hitWindowMs.get()) return;
            entity = bufferedTarget;
            if (!entity.isAlive()) { bufferedTarget = null; return; }
            if (mc.player.squaredDistanceTo(entity) > range.get() * range.get()) { bufferedTarget = null; return; }
            if (!entityCheck(entity)) { bufferedTarget = null; return; }
        }
        bufferedTarget = null;
        OnFallMode currOnFallMode = onFallMode.get();
        if (currOnFallMode != OnFallMode.None) {
            float threshold = (currOnFallMode == OnFallMode.Value)
                ? onFallValue.get().floatValue()
                : randomOnFallFloat;

            double vy     = mc.player.getVelocity().y;
            float  jitter = (mc.world.random.nextFloat() * 0.032f) - 0.016f;

            if (vy >= -(threshold + jitter)) return;
            if (mc.player.isOnGround()) return;
            if (mc.player.isTouchingWater()) return;
            if (mc.player.isClimbing()) return;
            if (mc.player.hasVehicle()) return;
        }
        HitSpeedMode currHitSpeedMode = hitSpeedMode.get();
        if (currHitSpeedMode != HitSpeedMode.None) {
            float hitSpeed = (currHitSpeedMode == HitSpeedMode.Value)
                ? hitSpeedValue.get().floatValue()
                : randomHitSpeedFloat;
            if ((mc.player.getAttackCooldownProgress(hitSpeed) * 17.0F) < 16) return;
        }
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
        if (currOnFallMode == OnFallMode.RandomValue) {
            float min = Math.min(onFallMinRandomValue.get().floatValue(), onFallMaxRandomValue.get().floatValue());
            float max = Math.max(onFallMinRandomValue.get().floatValue(), onFallMaxRandomValue.get().floatValue());
            randomOnFallFloat = min + mc.world.random.nextFloat() * (max - min);
        }

        if (currHitSpeedMode == HitSpeedMode.RandomValue) {
            float min = Math.min(hitSpeedMinRandomValue.get().floatValue(), hitSpeedMaxRandomValue.get().floatValue());
            float max = Math.max(hitSpeedMinRandomValue.get().floatValue(), hitSpeedMaxRandomValue.get().floatValue());
            randomHitSpeedFloat = min + mc.world.random.nextFloat() * (max - min);
        }
    }

    private Entity getTarget() {
        if (ignoreWalls.get()) return getTargetThroughWalls();

        if (mc.crosshairTarget == null
                || mc.crosshairTarget.getType() != HitResult.Type.ENTITY) return null;

        Entity entity = ((EntityHitResult) mc.crosshairTarget).getEntity();
        if (mc.player.squaredDistanceTo(entity) > range.get() * range.get()) return null;
        if (!entityCheck(entity)) return null;
        return entity;
    }

    private Entity getTargetThroughWalls() {
        Vec3d eye  = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0F);
        Vec3d end  = eye.add(look.multiply(range.get()));

        Box searchBox = mc.player.getBoundingBox()
            .stretch(look.multiply(range.get()))
            .expand(1.0);

        Entity best  = null;
        double bestD = Double.MAX_VALUE;

        for (Entity candidate : mc.world.getEntitiesByClass(
                LivingEntity.class, searchBox, this::entityCheck)) {
            Optional<Vec3d> hit = candidate.getBoundingBox().raycast(eye, end);
            if (hit.isPresent()) {
                double d = eye.squaredDistanceTo(hit.get());
                if (d < bestD) { bestD = d; best = candidate; }
            }
        }
        return best;
    }

    private boolean entityCheck(Entity entity) {
        if (entity == mc.player || entity == mc.getCameraEntity()) return false;
        if (!entity.isAlive()) return false;
        if (entity instanceof LivingEntity le && (le.isDead() || le.getHealth() <= 0)) return false;

        switch (target.get()) {
            case Players  -> { if (!(entity instanceof PlayerEntity)) return false; }
            case Entities -> { if (  entity instanceof PlayerEntity)  return false; }
            case All      -> {}
        }

        if (entity instanceof PlayerEntity player) {
            if (ignoreFriends.get() && !Friends.get().shouldAttack(player)) return false;
        }

        return true;
    }

    public enum Target       { Players, Entities, All }
    public enum OnFallMode   { None, Value, RandomValue }
    public enum HitSpeedMode { None, Value, RandomValue }
}