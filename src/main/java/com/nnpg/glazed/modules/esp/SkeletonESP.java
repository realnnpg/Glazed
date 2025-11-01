package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class SkeletonESP extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of the skeleton ESP")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<Boolean> distanceColors = sgGeneral.add(new BoolSetting.Builder()
        .name("distance-colors")
        .description("Change skeleton color based on distance")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> verticalOffset = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-offset")
        .description("Fixed vertical offset for skeleton placement.")
        .defaultValue(1.35)
        .min(1.0)
        .max(1.6)
        .sliderRange(1.0, 1.6)
        .build()
    );

    private final Setting<Double> forwardOffset = sgGeneral.add(new DoubleSetting.Builder()
        .name("forward-offset")
        .description("Forward/back offset of skeleton from chest center.")
        .defaultValue(0.0)
        .min(-0.3)
        .max(0.3)
        .sliderRange(-0.3, 0.3)
        .build()
    );

    private final Setting<Double> horizontalOffset = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-offset")
        .description("Horizontal width of shoulders/arms.")
        .defaultValue(0.25)
        .min(0.0)
        .max(0.5)
        .sliderRange(0.0, 0.5)
        .build()
    );

    public SkeletonESP() {
        super(GlazedAddon.esp, "skeleton-esp", "Renders player skeletons inside players with correct offsets & rotation (no legs).");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        List<AbstractClientPlayerEntity> players = mc.world.getPlayers();

        for (AbstractClientPlayerEntity player : players) {
            // Skip self in first-person
            if (mc.options.getPerspective() == Perspective.FIRST_PERSON && player == mc.player) continue;

            Vec3d basePos = player.getLerpedPos(event.tickDelta);
            Color skeletonColor = distanceColors.get() ? getColorFromDistance(basePos) : new Color(color.get());

            // Rotation
            double yawRad = Math.toRadians(-player.bodyYaw);

            // Base chest position with fixed vertical offset
            Vec3d chestBase = basePos.add(0, verticalOffset.get(), 0);

            if (player.isSneaking()) {
                chestBase = chestBase.add(0, -0.2, 0);
            }

            // Apply forward offset rotated with player
            Vec3d forwardVec = new Vec3d(0, 0, forwardOffset.get()).rotateY((float) yawRad);
            chestBase = chestBase.add(forwardVec);

            // Shoulder positions
            Vec3d leftShoulder = chestBase.add(new Vec3d(-horizontalOffset.get(), 0, 0).rotateY((float) yawRad));
            Vec3d rightShoulder = chestBase.add(new Vec3d(horizontalOffset.get(), 0, 0).rotateY((float) yawRad));

            // Arm endpoints
            Vec3d leftArmEnd = leftShoulder.add(0, -0.6, 0);
            Vec3d rightArmEnd = rightShoulder.add(0, -0.6, 0);

            // Spine start (hips)
            Vec3d spineStart = basePos.add(forwardVec);

            // Spine end (shoulder center)
            Vec3d spineEnd = chestBase;

            // Head
            Vec3d headTop = chestBase.add(0, 0.25, 0);

            // Draw spine
            event.renderer.line(spineStart.x, spineStart.y, spineStart.z,
                spineEnd.x, spineEnd.y, spineEnd.z, skeletonColor);

            // Shoulders
            event.renderer.line(leftShoulder.x, leftShoulder.y, leftShoulder.z,
                rightShoulder.x, rightShoulder.y, rightShoulder.z, skeletonColor);

            // Arms
            event.renderer.line(leftShoulder.x, leftShoulder.y, leftShoulder.z,
                leftArmEnd.x, leftArmEnd.y, leftArmEnd.z, skeletonColor);
            event.renderer.line(rightShoulder.x, rightShoulder.y, rightShoulder.z,
                rightArmEnd.x, rightArmEnd.y, rightArmEnd.z, skeletonColor);

            // Head
            event.renderer.line(spineEnd.x, spineEnd.y, spineEnd.z,
                headTop.x, headTop.y, headTop.z, skeletonColor);
        }
    }

    private Color getColorFromDistance(Vec3d pos) {
        double distance = mc.player.getPos().distanceTo(pos);
        double percent = Math.min(1.0, distance / 60.0);

        int r, g;

        // Reverse gradient: Green (close) → Yellow → Orange → Red (far)
        if (percent < 0.33) {
            // Green → Yellow
            r = (int)(percent / 0.33 * 255);
            g = 255;
        } else if (percent < 0.66) {
            // Yellow → Orange
            r = 255;
            g = 255 - (int)((percent - 0.33) / 0.33 * 90);
        } else {
            // Orange → Red
            r = 255;
            g = 165 - (int)((percent - 0.66) / 0.34 * 165);
        }

        return new Color(r, g, 0, 255);
    }
}
