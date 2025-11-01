package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3d;

public class FreecamMining extends Module {
    private final Freecam freecam = Modules.get().get(Freecam.class);
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public FreecamMining() {
        super(GlazedAddon.CATEGORY, "FreecamMining", "Freecam with real-position mining override.");
    }

    @Override
    public void onActivate() {
        if (!freecam.isActive()) {
            freecam.toggle();
            info("Freecam activated for mining.");
        }
    }

    @Override
    public void onDeactivate() {
        if (freecam.isActive()) {
            freecam.toggle();
            info("Freecam deactivated.");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        PlayerEntity player = mc.player;
        Vec3d eyePos = player.getEyePos();
        float yaw = player.getYaw();
        float pitch = player.getPitch();

        Vector3d lookVec = new Vector3d(
            -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)),
            -Math.sin(Math.toRadians(pitch)),
            Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch))
        );

        double reach = 5.0;
        Vec3d endPos = eyePos.add(new Vec3d(lookVec.x, lookVec.y, lookVec.z).multiply(reach));

        HitResult ray = mc.world.raycast(new RaycastContext(
            eyePos,
            endPos,
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            player
        ));

        if (ray.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) ray;
            BlockPos targetPos = blockHit.getBlockPos();

            player.swingHand(Hand.MAIN_HAND);
            mc.interactionManager.attackBlock(targetPos, blockHit.getSide());
        }
    }
}