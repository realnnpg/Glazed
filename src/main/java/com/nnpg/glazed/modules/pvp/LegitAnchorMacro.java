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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.hit.BlockHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

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

    private final Setting<Boolean> switchBackToAnchor = sgGeneral.add(new BoolSetting.Builder()
        .name("switch-back-to-anchor")
        .description("Switch back to respawn anchor after explosion.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> switchBackDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("switch-back-delay")
        .description("Delay in ticks before switching back to anchor.")
        .defaultValue(5.0)
        .min(0.0)
        .max(20.0)
        .sliderMax(20.0)
        .visible(() -> switchBackToAnchor.get())
        .build()
    );

    private final Setting<Boolean> pauseOnKill = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-kill")
        .description("Temporarily pauses the module when a player is killed to prevent destroying their items.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> pauseDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("pause-delay")
        .description("How long to pause the module after a player death (in seconds).")
        .defaultValue(2.0)
        .min(0.5)
        .max(10.0)
        .sliderMax(10.0)
        .visible(() -> pauseOnKill.get())
        .build()
    );

    private int keybindCounter;
    private int glowstoneDelayCounter;
    private int explodeDelayCounter;
    private int switchBackDelayCounter;
    private int pauseCounter;
    private boolean hasPlacedGlowstone = false;
    private boolean hasExplodedAnchor = false;
    private boolean shouldSwitchBack = false;
    private BlockHitResult lastBlockHitResult = null;
    private List<PlayerEntity> deadPlayers = new ArrayList<>();

    public LegitAnchorMacro() {
        super(GlazedAddon.pvp, "LegitAnchorMacro", "Automatically charges and explodes respawn anchors.");
    }

    @Override
    public void onActivate() {
        resetCounters();
        hasPlacedGlowstone = false;
        hasExplodedAnchor = false;
        shouldSwitchBack = false;
        lastBlockHitResult = null;
        pauseCounter = 0;
        deadPlayers = new ArrayList<>();
    }

    @Override
    public void onDeactivate() {
        resetCounters();
        hasPlacedGlowstone = false;
        hasExplodedAnchor = false;
        shouldSwitchBack = false;
        lastBlockHitResult = null;
        pauseCounter = 0;
        deadPlayers.clear();
    }

    private void resetCounters() {
        keybindCounter = 0;
        glowstoneDelayCounter = 0;
        explodeDelayCounter = 0;
        switchBackDelayCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.currentScreen != null) {
            return;
        }

        // Update pause counter
        if (pauseCounter > 0) {
            pauseCounter--;
        }

        // Check for dead players and pause if needed
        if (pauseOnKill.get() && checkForDeadPlayers()) {
            pauseCounter = (int)(pauseDelay.get() * 20); // Convert seconds to ticks
            if (mc.player != null) {
                mc.player.sendMessage(net.minecraft.text.Text.literal("§7[§bLegitAnchorMacro§7] §ePaused for " + pauseDelay.get() + " seconds due to player death"), false);
            }
        }

        // Check if module is paused due to player death
        if (pauseCounter > 0) {
            return;
        }

        if (isShieldOrFoodActive()) {
            return;
        }

        if (shouldSwitchBack && switchBackToAnchor.get()) {
            handleSwitchBackToAnchor();
            return;
        }

        if (KeyUtils.isKeyPressed(1)) {
            handleAnchorInteraction();
        } else {
            hasPlacedGlowstone = false;
            hasExplodedAnchor = false;
            shouldSwitchBack = false;
            lastBlockHitResult = null;
        }
    }

    private boolean checkForDeadPlayers() {
        if (mc.world == null) return false;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player && player.getHealth() <= 0) {
                if (!deadPlayers.contains(player)) {
                    deadPlayers.add(player);
                    return true;
                }
            }
        }

        // Clean up players that are no longer dead
        deadPlayers.removeIf(player -> player.getHealth() > 0);

        return false;
    }

    private boolean isShieldOrFoodActive() {
        final boolean isFood = mc.player.getMainHandStack().getItem().getComponents().contains(DataComponentTypes.FOOD) ||
            mc.player.getOffHandStack().getItem().getComponents().contains(DataComponentTypes.FOOD);
        final boolean isShield = mc.player.getMainHandStack().getItem() instanceof ShieldItem ||
            mc.player.getOffHandStack().getItem() instanceof ShieldItem;
        final boolean isRightClickPressed = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), 1) == 1;
        return (isFood || isShield) && isRightClickPressed;
    }

    private void handleSwitchBackToAnchor() {
        if (switchBackDelayCounter < switchBackDelay.get().intValue()) {
            ++switchBackDelayCounter;
            return;
        }

        switchBackDelayCounter = 0;
        swapToItem(Items.RESPAWN_ANCHOR);
        shouldSwitchBack = false;
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

            if (switchBackToAnchor.get()) {
                shouldSwitchBack = true;
                switchBackDelayCounter = 0;
            }
        }
    }

    private void swapToItem(net.minecraft.item.Item item) {
        FindItemResult result = InvUtils.findInHotbar(item);
        if (result.found()) {
            mc.player.getInventory().setSelectedSlot(result.slot());
        }
    }
}
