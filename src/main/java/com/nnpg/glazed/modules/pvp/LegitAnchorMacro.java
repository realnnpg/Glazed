package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.VersionUtil;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import com.nnpg.glazed.utils.glazed.KeyUtils;
import com.nnpg.glazed.utils.glazed.BlockUtil;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.hit.BlockHitResult;
import org.lwjgl.glfw.GLFW;

public class LegitAnchorMacro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> switchDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("switch-delay")
        .description("Delay in ticks before switching items.")
        .defaultValue(0.0)
        .min(0.0)
        .max(20.0)
        .sliderMax(20.0)
        .build()
    );

    private final Setting<Double> glowstoneDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("glowstone-delay")
        .description("Delay in ticks before placing glowstone.")
        .defaultValue(0.0)
        .min(0.0)
        .max(20.0)
        .sliderMax(20.0)
        .build()
    );

    private final Setting<Double> explodeDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("explode-delay")
        .description("Delay in ticks before exploding the anchor.")
        .defaultValue(0.0)
        .min(0.0)
        .max(20.0)
        .sliderMax(20.0)
        .build()
    );

    private final Setting<Integer> totemSlot = sgGeneral.add(new IntSetting.Builder()
        .name("totem-slot")
        .description("Hotbar slot to switch to when exploding (1-9).")
        .defaultValue(1)
        .min(1)
        .max(9)
        .build()
    );

    private int keybindCounter;
    private int glowstoneDelayCounter;
    private int explodeDelayCounter;
    private boolean hasPlacedGlowstone = false;
    private boolean hasExplodedAnchor = false;
    private BlockHitResult lastBlockHitResult = null;

    public LegitAnchorMacro() {
        super(GlazedAddon.pvp, "LegitAnchorMacro", "Automatically charges and explodes respawn anchors.");
    }

    @Override
    public void onActivate() {
        resetCounters();
        hasPlacedGlowstone = false;
        hasExplodedAnchor = false;
        lastBlockHitResult = null;
    }

    @Override
    public void onDeactivate() {
        resetCounters();
        hasPlacedGlowstone = false;
        hasExplodedAnchor = false;
        lastBlockHitResult = null;
    }

    private void resetCounters() {
        keybindCounter = 0;
        glowstoneDelayCounter = 0;
        explodeDelayCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.currentScreen != null) {
            return;
        }
        if (isShieldOrFoodActive()) {
            return;
        }
        if (KeyUtils.isKeyPressed(1)) { // Right mouse button
            handleAnchorInteraction();
        } else {
            // Reset state when key is released
            hasPlacedGlowstone = false;
            hasExplodedAnchor = false;
            lastBlockHitResult = null;
        }
    }

    private boolean isShieldOrFoodActive() {
        final boolean isFood = mc.player.getMainHandStack().getItem().getComponents().contains(DataComponentTypes.FOOD) ||
            mc.player.getOffHandStack().getItem().getComponents().contains(DataComponentTypes.FOOD);
        final boolean isShield = mc.player.getMainHandStack().getItem() instanceof ShieldItem ||
            mc.player.getOffHandStack().getItem() instanceof ShieldItem;
        final boolean isRightClickPressed = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), 1) == 1;
        return (isFood || isShield) && isRightClickPressed;
    }

    private void handleAnchorInteraction() {
        if (!(mc.crosshairTarget instanceof BlockHitResult blockHitResult)) {
            return;
        }

        lastBlockHitResult = blockHitResult;

        if (!BlockUtil.isBlockAtPosition(blockHitResult.getBlockPos(), Blocks.RESPAWN_ANCHOR)) {
            return;
        }

        mc.options.useKey.setPressed(false);

        if (BlockUtil.isRespawnAnchorUncharged(blockHitResult.getBlockPos()) && !hasPlacedGlowstone) {
            placeGlowstone(blockHitResult);
        }
        else if (BlockUtil.isRespawnAnchorCharged(blockHitResult.getBlockPos()) && !hasExplodedAnchor) {
            explodeAnchor(blockHitResult);
        }
    }

    private void placeGlowstone(final BlockHitResult blockHitResult) {
        if (!mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
            if (keybindCounter < switchDelay.get().intValue()) {
                ++keybindCounter;
                return;
            }
            keybindCounter = 0;
            swapToItem(Items.GLOWSTONE);
            return;
        }

        if (mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
            if (glowstoneDelayCounter < glowstoneDelay.get().intValue()) {
                ++glowstoneDelayCounter;
                return;
            }
            glowstoneDelayCounter = 0;
            BlockUtil.interactWithBlock(blockHitResult, true);
            hasPlacedGlowstone = true;
        }
    }

    private void explodeAnchor(final BlockHitResult blockHitResult) {
        final int selectedSlot = totemSlot.get() - 1;

        if (VersionUtil.getSelectedSlot(mc.player) != selectedSlot) {
            if (keybindCounter < switchDelay.get().intValue()) {
                ++keybindCounter;
                return;
            }
            keybindCounter = 0;

            VersionUtil.setSelectedSlot(mc.player, selectedSlot);
            return;
        }

        if (VersionUtil.getSelectedSlot(mc.player) == selectedSlot) {
            if (explodeDelayCounter < explodeDelay.get().intValue()) {
                ++explodeDelayCounter;
                return;
            }
            explodeDelayCounter = 0;
            BlockUtil.interactWithBlock(blockHitResult, true);
            hasExplodedAnchor = true;
        }
    }

    private void swapToItem(net.minecraft.item.Item item) {
        FindItemResult result = InvUtils.findInHotbar(item);
        if (result.found()) {
            mc.player.getInventory().setSelectedSlot(result.slot());
        }
    }
}
