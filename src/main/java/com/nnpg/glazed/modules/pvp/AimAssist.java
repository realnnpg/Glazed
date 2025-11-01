package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;
import java.util.Set;

public class AimAssist extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSpeed = settings.createGroup("Aim Speed");
    private final SettingGroup sgBypass = settings.createGroup("Grim Bypass");

    // General Settings
    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to aim at.")
        .defaultValue(Set.of(EntityType.PLAYER))
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("The range at which an entity can be targeted.")
        .defaultValue(5.0)
        .min(0.0)
        .sliderRange(0.0, 10.0)
        .build()
    );

    private final Setting<Double> fov = sgGeneral.add(new DoubleSetting.Builder()
        .name("fov")
        .description("Will only aim entities in the FOV.")
        .defaultValue(360.0)
        .min(0.0)
        .max(360.0)
        .sliderRange(0.0, 360.0)
        .build()
    );

    private final Setting<Boolean> ignoreWalls = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-walls")
        .description("Whether or not to ignore aiming through walls.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to filter targets within range.")
        .defaultValue(SortPriority.LowestHealth)
        .build()
    );

    private final Setting<Target> bodyTarget = sgGeneral.add(new EnumSetting.Builder<Target>()
        .name("aim-target")
        .description("Which part of the entities body to aim at.")
        .defaultValue(Target.Body)
        .build()
    );

    // Aim Speed Settings
    private final Setting<Boolean> instant = sgSpeed.add(new BoolSetting.Builder()
        .name("instant-look")
        .description("Instantly looks at the entity.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> speed = sgSpeed.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast to aim at the entity.")
        .defaultValue(5.0)
        .min(0.0)
        .sliderRange(0.0, 20.0)
        .visible(() -> !instant.get())
        .build()
    );

    // Grim Bypass Settings
    private final Setting<Boolean> randomizeRotation = sgBypass.add(new BoolSetting.Builder()
        .name("randomize-rotation")
        .description("Add random noise to rotations to bypass Grim AC v3.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> randomNoise = sgBypass.add(new DoubleSetting.Builder()
        .name("random-noise")
        .description("Amount of random noise to add to rotations.")
        .defaultValue(0.5)
        .min(0.0)
        .max(3.0)
        .sliderRange(0.0, 3.0)
        .visible(randomizeRotation::get)
        .build()
    );

    private final Setting<Double> maxRotationDelta = sgBypass.add(new DoubleSetting.Builder()
        .name("max-rotation-delta")
        .description("Maximum rotation change per tick to bypass Grim AC v3.")
        .defaultValue(2.5)
        .min(0.5)
        .max(10.0)
        .sliderRange(0.5, 10.0)
        .build()
    );

    private final Random random = new Random();
    private Entity target;

    public AimAssist() {
        super(GlazedAddon.pvp, "aim-assist", "Automatically aims at entities, with Grim AC v3 bypass.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            ChatUtils.error("Cannot activate AimAssist: Player or world is null!");
            toggle();
            return;
        }
        target = null;
        ChatUtils.info("AimAssist activated. Targeting range: " + range.get() + " blocks.");
    }

    @Override
    public void onDeactivate() {
        target = null;
        ChatUtils.info("AimAssist deactivated.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        target = TargetUtils.get(entity -> {
            if (!entity.isAlive()) return false;
            if (!PlayerUtils.isWithin(entity, range.get())) return false;
            if (!ignoreWalls.get() && !PlayerUtils.canSeeEntity(entity)) return false;
            if (entity == mc.player || !entities.get().contains(entity.getType())) return false;
            if (entity instanceof PlayerEntity && !Friends.get().shouldAttack((PlayerEntity) entity)) return false;
            return isInFov(entity, fov.get());
        }, priority.get());
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (target != null) {
            aim(target, (float) event.tickDelta, instant.get());
        }
    }

    private void aim(Entity target, float delta, boolean instant) {
        // Get interpolated position of target
        Vec3d pos = target.getLerpedPos(delta);
        Vec3d targetPos = new Vec3d(pos.x, pos.y, pos.z);

        // Adjust for body part
        switch (bodyTarget.get()) {
            case Head -> targetPos = targetPos.add(0, target.getEyeHeight(target.getPose()), 0);
            case Body -> targetPos = targetPos.add(0, target.getEyeHeight(target.getPose()) / 2, 0);
        }

        // Calculate deltas
        double deltaX = targetPos.x - mc.player.getX();
        double deltaZ = targetPos.z - mc.player.getZ();
        double deltaY = targetPos.y - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));

        // Yaw
        double angle = Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90;
        if (randomizeRotation.get()) {
            angle += (random.nextFloat() - 0.5f) * randomNoise.get() * 2.0;
        }

        if (instant) {
            mc.player.setYaw((float) angle);
        } else {
            double deltaAngle = MathHelper.wrapDegrees(angle - mc.player.getYaw());
            double toRotate = Math.min(Math.abs(deltaAngle), speed.get() * delta);
            toRotate = Math.copySign(toRotate, deltaAngle);
            if (Math.abs(toRotate) > maxRotationDelta.get()) {
                toRotate = Math.copySign(maxRotationDelta.get(), toRotate);
            }
            mc.player.setYaw(mc.player.getYaw() + (float) toRotate);
        }

        // Pitch
        double idk = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        angle = -Math.toDegrees(Math.atan2(deltaY, idk));
        if (randomizeRotation.get()) {
            angle += (random.nextFloat() - 0.5f) * randomNoise.get() * 2.0;
        }

        if (instant) {
            mc.player.setPitch((float) angle);
        } else {
            double deltaAngle = MathHelper.wrapDegrees(angle - mc.player.getPitch());
            double toRotate = Math.min(Math.abs(deltaAngle), speed.get() * delta);
            toRotate = Math.copySign(toRotate, deltaAngle);
            if (Math.abs(toRotate) > maxRotationDelta.get()) {
                toRotate = Math.copySign(maxRotationDelta.get(), toRotate);
            }
            mc.player.setPitch(mc.player.getPitch() + (float) toRotate);
        }
    }

    private boolean isInFov(Entity entity, double fov) {
        if (fov >= 360.0) return true;
        Vec3d entityPos = entity.getPos();
        Vec3d playerPos = mc.player.getEyePos();
        Vec3d direction = entityPos.subtract(playerPos).normalize();
        double yaw = Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90;
        double pitch = -Math.toDegrees(Math.asin(direction.y));
        double yawDiff = MathHelper.wrapDegrees(yaw - mc.player.getYaw());
        double pitchDiff = MathHelper.wrapDegrees(pitch - mc.player.getPitch());
        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff) <= fov / 2.0;
    }

    @Override
    public String getInfoString() {
        return EntityUtils.getName(target);
    }
}
