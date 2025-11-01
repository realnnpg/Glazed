package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public class DoubleAnchorMacro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> activateKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("activate-key")
        .description("Key that starts double anchoring.")
        .defaultValue(Keybind.none())
        .build()
    );

    private final Setting<Double> switchDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("switch-delay")
        .description("Delay between steps.")
        .defaultValue(2.0)
        .min(0.0)
        .max(20.0)
        .sliderMax(20.0)
        .build()
    );

    private final Setting<Integer> totemSlot = sgGeneral.add(new IntSetting.Builder()
        .name("totem-slot")
        .description("Hotbar slot to return to after anchoring (1-9).")
        .defaultValue(1)
        .min(1)
        .max(9)
        .sliderMax(9)
        .build()
    );

    private int delayCounter = 0;
    private int step = 0;
    private boolean isAnchoring = false;
    private boolean wasPressed = false;

    public DoubleAnchorMacro() {
        super(GlazedAddon.pvp, "double-anchor-macro", "Automatically places and charges 2 anchors.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.currentScreen != null) return;
        if (!hasRequiredItems()) return;

        boolean keyPressed = activateKey.get().isPressed();

        if (!isAnchoring && keyPressed && !wasPressed) {
            isAnchoring = true;
            step = 0;
            delayCounter = 0;
        }

        wasPressed = keyPressed;

        if (!isAnchoring) return;

        HitResult target = mc.crosshairTarget;
        if (!(target instanceof BlockHitResult bhr) || mc.world.getBlockState(bhr.getBlockPos()).isOf(Blocks.AIR)) {
            isAnchoring = false;
            resetState();
            return;
        }

        if (delayCounter < switchDelay.get().intValue()) {
            delayCounter++;
            return;
        }

        switch (step) {
            case 0 -> swapTo(Items.RESPAWN_ANCHOR);
            case 1 -> mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, (BlockHitResult) target);
            case 2 -> swapTo(Items.GLOWSTONE);
            case 3 -> mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, (BlockHitResult) target);
            case 4 -> swapTo(Items.RESPAWN_ANCHOR);
            case 5 -> {
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, (BlockHitResult) target);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, (BlockHitResult) target);
            }
            case 6 -> swapTo(Items.GLOWSTONE);
            case 7 -> mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, (BlockHitResult) target);
            case 8 -> InvUtils.swap(totemSlot.get() - 1, true);
            case 9 -> mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, (BlockHitResult) target);
            case 10 -> {
                isAnchoring = false;
                step = 0;
                resetState();
                return;
            }
        }

        step++;
        delayCounter = 0;
    }

    private void swapTo(net.minecraft.item.Item item) {
        int slot = InvUtils.find(item).slot();
        if (slot != -1) InvUtils.swap(slot, true);
    }

    private boolean hasRequiredItems() {
        boolean hasAnchor = false, hasGlow = false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.RESPAWN_ANCHOR)) hasAnchor = true;
            if (stack.isOf(Items.GLOWSTONE)) hasGlow = true;
        }
        return hasAnchor && hasGlow;
    }

    private void resetState() {
        delayCounter = 0;
    }

    public boolean isAnchoringActive() {
        return isAnchoring;
    }
}
