package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class AutoPearlChain extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> switchBack = sgGeneral.add(new BoolSetting.Builder()
        .name("switch-back")
        .description("Switch back to previous slot after throwing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> switchDelay = sgGeneral.add(new IntSetting.Builder()
        .name("switch-delay")
        .description("Delay before switching back (ticks).")
        .defaultValue(4)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Double> teleportThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("teleport-threshold")
        .description("Minimum distance change to detect teleport.")
        .defaultValue(5.0)
        .min(0.1)
        .sliderMax(20.0)
        .build()
    );

    private int prevSlot = -1;
    private int switchCooldown = 0;
    private boolean waitingForTeleport = false;
    private double lastX, lastY, lastZ;

    public AutoPearlChain() {
        super(GlazedAddon.CATEGORY, "auto-pearl-chain", "Chains pearls after teleport detection.");
    }

    @Override
    public void onActivate() {
        reset();
        if (mc.player != null) {
            lastX = mc.player.getX();
            lastY = mc.player.getY();
            lastZ = mc.player.getZ();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.interactionManager == null) return;

        if (!waitingForTeleport) {
            int pearlSlot = findPearlSlot();
            if (pearlSlot == -1) return;

            prevSlot = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().setSelectedSlot(pearlSlot);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);

            waitingForTeleport = true;
            switchCooldown = 0;
        } else {
            double dx = mc.player.getX() - lastX;
            double dy = mc.player.getY() - lastY;
            double dz = mc.player.getZ() - lastZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (dist >= teleportThreshold.get()) {
                if (switchBack.get()) {
                    if (switchCooldown < switchDelay.get()) {
                        switchCooldown++;
                        return;
                    }
                    if (prevSlot != -1) {
                        mc.player.getInventory().setSelectedSlot(prevSlot);
                    }
                }

                lastX = mc.player.getX();
                lastY = mc.player.getY();
                lastZ = mc.player.getZ();
                waitingForTeleport = false;
            }
        }
    }

    private int findPearlSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.ENDER_PEARL) {
                return i;
            }
        }
        return -1;
    }

    private void reset() {
        waitingForTeleport = false;
        switchCooldown = 0;
        prevSlot = -1;
    }
}
