package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class RTPBaseFinder extends Module {
    private BlockPos currentTarget = null;
    private boolean waitingForTeleport = false;
    private long lastTeleportTime = 0;

    public RTPBaseFinder() {
        super(GlazedAddon.CATEGORY, "RTPBaseFinder", "Aimbots downward, holds left click to mine to Y=-58, then runs /rtp east.");
    }

    @Override
    public void onActivate() {
        currentTarget = mc.player.getBlockPos().down();
        waitingForTeleport = false;
        info("RTPBaseFinder activated. Mining straight down.");
    }

    @Override
    public void onDeactivate() {
        releaseLeftClick();
        info("RTPBaseFinder disabled.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.options == null || mc.currentScreen != null) return;

        if (waitingForTeleport) {
            if (System.currentTimeMillis() - lastTeleportTime > 3000) {
                currentTarget = mc.player.getBlockPos().down();
                waitingForTeleport = false;
            }
            return;
        }

        aimDownward();

        if (currentTarget == null || currentTarget.getY() <= -58) {
            triggerTeleport();
            return;
        }

        holdLeftClick();

        if (mc.world.getBlockState(currentTarget).isAir()) {
            currentTarget = currentTarget.down();
        }
    }

    private void aimDownward() {
        mc.player.setPitch(90f); // Look straight down
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetVec = Vec3d.ofCenter(currentTarget);
        Vec3d dir = targetVec.subtract(eyePos).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        mc.player.setYaw(yaw);
    }

    private void holdLeftClick() {
        KeyBinding.setKeyPressed(mc.options.attackKey.getDefaultKey(), true);
    }

    private void releaseLeftClick() {
        KeyBinding.setKeyPressed(mc.options.attackKey.getDefaultKey(), false);
    }

    private void triggerTeleport() {
        releaseLeftClick();
        mc.player.networkHandler.sendChatCommand("rtp east");
        lastTeleportTime = System.currentTimeMillis();
        waitingForTeleport = true;
        info("Reached Y=-58. Teleporting east.");
    }
}
