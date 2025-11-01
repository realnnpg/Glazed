package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;

public class WindPearlMacro extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("wind-delay")
        .description("Delay between pearl throw and wind charge.")
        .defaultValue(8)
        .min(1)
        .max(20)
        .sliderMax(20)
        .build());

    private final Setting<Boolean> autoRotate = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-rotate")
        .description("Rotate toward movement direction before using wind charge.")
        .defaultValue(true)
        .build());

    private int tickCounter = 0;
    private boolean throwingPearl = false;
    private int previousSlot = -1;

    public WindPearlMacro() {
        super(GlazedAddon.pvp, "pearl-wind-macro", "Throws pearl first, then uses wind charge after delay.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        int pearlSlot = findPearlSlot();
        if (pearlSlot == -1) {
            error("No Ender Pearl found in hotbar.");
            toggle();
            return;
        }

        previousSlot = getSelectedSlotReflectively();
        if (autoRotate.get()) rotateTowardMovement();

        InvUtils.swap(pearlSlot, true);
        throwPearl();
        throwingPearl = true;
        tickCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!throwingPearl || mc.player == null || mc.world == null) return;

        tickCounter++;
        if (tickCounter >= delayTicks.get()) {
            int windSlot = findWindChargeSlot();
            if (windSlot == -1) {
                error("No Wind Charge found in hotbar.");
                toggle();
                return;
            }

            InvUtils.swap(windSlot, true);
            useWindCharge();

            if (previousSlot != -1) InvUtils.swap(previousSlot, true);
            toggle(); // Auto-disable after combo
        }
    }

    private void throwPearl() {
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
    }

    private void useWindCharge() {
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
    }

    private int findPearlSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.ENDER_PEARL)) return i;
        }
        return -1;
    }

    private int findWindChargeSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.WIND_CHARGE)) return i;
        }
        return -1;
    }

    private int getSelectedSlotReflectively() {
        try {
            Field field = mc.player.getInventory().getClass().getDeclaredField("selectedSlot");
            field.setAccessible(true);
            return field.getInt(mc.player.getInventory());
        } catch (Exception e) {
            return -1;
        }
    }

    private void rotateTowardMovement() {
        Vec3d vel = mc.player.getVelocity();
        if (vel.lengthSquared() < 0.01) return;

        float yaw = (float) Math.toDegrees(Math.atan2(vel.z, vel.x)) - 90f;
        mc.player.setYaw(yaw);
    }
}
