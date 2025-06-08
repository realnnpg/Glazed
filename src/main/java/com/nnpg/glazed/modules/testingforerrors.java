package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

public class RTPBaseFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<RTPRegion> rtpRegion = sgGeneral.add(new EnumSetting.Builder<RTPRegion>()
        .name("rtp-region")
        .description("The region to RTP to.")
        .defaultValue(RTPRegion.EU_CENTRAL)
        .build());

    private final Setting<Integer> postRtpDelaySeconds = sgGeneral.add(new IntSetting.Builder()
        .name("post-rtp-delay-seconds")
        .description("Time to wait after RTP before resuming.")
        .defaultValue(5)
        .min(1)
        .sliderMax(60)
        .build());

    private int rtpStage = 0;
    private long rtpStageStart;
    private BlockPos lastXZPos;
    private long lastXZCheck;

    public RTPBaseFinder() {
        super(AddonTemplate.CATEGORY, "IWWI RTP base finder", "RTPs, mines to Y -5, and detects bases using Baritone.");
    }

    @Override
    public void onActivate() {
        rtpStage = 0;
        rtpStageStart = System.currentTimeMillis();
        if (mc.player != null) {
            lastXZPos = mc.player.getBlockPos();
            lastXZCheck = System.currentTimeMillis();
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) ChatUtils.sendPlayerMsg("#stop");
    }

    private void startGoto() {
        BlockPos pos = mc.player.getBlockPos();
        ChatUtils.sendPlayerMsg("#goto " + pos.getX() + " -5 " + pos.getZ());
    }

    private boolean checkForBaseNearby() {
        BlockPos playerPos = mc.player.getBlockPos();

        for (int x = -8; x <= 8; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -8; z <= 8; z++) {
                    BlockPos checkPos = playerPos.add(x, y, z);
                    Block block = mc.world.getBlockState(checkPos).getBlock();
                    if (block == Blocks.SPAWNER ||
                        block == Blocks.CHEST ||
                        block == Blocks.TRAPPED_CHEST ||
                        block == Blocks.BARREL ||
                        block == Blocks.SHULKER_BOX ||
                        block == Blocks.ENDER_CHEST) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        BlockPos currentXZ = new BlockPos(mc.player.getBlockPos().getX(), 0, mc.player.getBlockPos().getZ());

        if (lastXZPos != null && currentXZ.equals(new BlockPos(lastXZPos.getX(), 0, lastXZPos.getZ()))) {
            if (now - lastXZCheck >= 5000) {
                ChatUtils.info("[IWWI] Stuck for 5s, re-RTPing...");
                ChatUtils.sendPlayerMsg("/rtp " + rtpRegion.get().getCommandPart());
                rtpStage = 3;
                rtpStageStart = now;
                lastXZCheck = now;
            }
        } else {
            lastXZPos = currentXZ;
            lastXZCheck = now;
        }

        switch (rtpStage) {
            case 0:
                startGoto();
                rtpStage = 1;
                break;

            case 1:
                if (mc.player.getY() <= -5) {
                    ChatUtils.sendPlayerMsg("#stop");
                    rtpStage = 2;
                    rtpStageStart = now;
                } else if (checkForBaseNearby()) {
                    ChatUtils.info("[IWWI] Base detected! Disconnecting.");
                    mc.player.networkHandler.getConnection().disconnect("Base detected.");
                    toggle();
                }
                break;

            case 2:
                if (now - rtpStageStart >= 1000) {
                    ChatUtils.sendPlayerMsg("/rtp " + rtpRegion.get().getCommandPart());
                    rtpStage = 3;
                    rtpStageStart = now;
                }
                break;

            case 3:
                if (now - rtpStageStart >= postRtpDelaySeconds.get() * 1000L) {
                    rtpStage = 4;
                    rtpStageStart = now;
                }
                break;

            case 4:
                if (now - rtpStageStart >= 1000) {
                    rtpStage = 0;
                }
                break;
        }
    }

    public enum RTPRegion {
        ASIA("asia"),
        EAST("east"),
        EU_CENTRAL("eu central"),
        EU_WEST("eu west"),
        OCEANIA("oceania"),
        WEST("west");

        private final String commandPart;

        RTPRegion(String commandPart) {
            this.commandPart = commandPart;
        }

        public String getCommandPart() {
            return commandPart;
        }
    }
}
