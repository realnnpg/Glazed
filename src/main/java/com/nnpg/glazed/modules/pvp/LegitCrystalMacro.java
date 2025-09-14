package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.VersionUtil;
import com.nnpg.glazed.utils.glazed.BlockUtil;
import com.nnpg.glazed.utils.glazed.KeyUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

public class LegitCrystalMacro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> activateKey = sgGeneral.add(new IntSetting.Builder()
        .name("activate-key")
        .description("Key that does the crystalling.")
        .defaultValue(1)
        .min(-1)
        .max(400)
        .build()
    );

    private final Setting<Double> placeDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-delay")
        .description("The delay in ticks between placing crystals.")
        .defaultValue(0.0)
        .min(0.0)
        .max(20.0)
        .sliderMax(20.0)
        .build()
    );

    private final Setting<Double> breakDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("break-delay")
        .description("The delay in ticks between breaking crystals.")
        .defaultValue(0.0)
        .min(0.0)
        .max(20.0)
        .sliderMax(20.0)
        .build()
    );

    private final Setting<Boolean> placeObi = sgGeneral.add(new BoolSetting.Builder()
        .name("place-obi")
        .description("Automatically places obsidian if not looking at obsidian or bedrock.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> obiSwitchDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("obi-switch-delay")
        .description("The delay in ticks when switching to/from obsidian.")
        .defaultValue(0.0)
        .min(0.0)
        .max(20.0)
        .sliderMax(20.0)
        .visible(() -> placeObi.get())
        .build()
    );

    private final Setting<Boolean> pauseOnKill = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-kill")
        .description("Temporarily pauses the module when a player is killed to prevent blowing up the items.")
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

    private int placeDelayCounter;
    private int breakDelayCounter;
    private int obiSwitchDelayCounter;
    private int pauseCounter;
    private boolean isPlacingObi;
    private BlockHitResult pendingObiPlacement;
    public boolean isActive;
    private List<PlayerEntity> deadPlayers = new ArrayList<>();

    public LegitCrystalMacro() {
        super(GlazedAddon.pvp, "LegitCystalMacro", "Automatically crystals fast for you");
    }

    @Override
    public void onActivate() {
        this.resetCounters();
        this.isActive = false;
        this.isPlacingObi = false;
        this.pendingObiPlacement = null;
        this.pauseCounter = 0;
        this.deadPlayers = new ArrayList<>();
    }

    @Override
    public void onDeactivate() {
        this.resetCounters();
        this.isActive = false;
        this.isPlacingObi = false;
        this.pendingObiPlacement = null;
        this.pauseCounter = 0;
        this.deadPlayers.clear();
    }

    private void resetCounters() {
        this.placeDelayCounter = 0;
        this.breakDelayCounter = 0;
        this.obiSwitchDelayCounter = 0;
    }

    @EventHandler
    private void onTick(final TickEvent.Pre tickEvent) {
        if (mc.currentScreen != null) {
            return;
        }
        this.updateCounters();
        if (mc.player.isUsingItem()) {
            return;
        }

        // Check for dead players and pause if needed
        if (this.pauseOnKill.get() && this.checkForDeadPlayers()) {
            this.pauseCounter = (int)(pauseDelay.get() * 20); // Convert seconds to ticks
            if (mc.player != null) {
                mc.player.sendMessage(net.minecraft.text.Text.literal("§7[§bLegitCrystalMacro§7] §ePaused for " + pauseDelay.get() + " seconds due to player death"), false);
            }
        }

        // Check if module is paused due to player death
        if (this.pauseCounter > 0) {
            return;
        }

        if (!this.isKeyActive()) {
            return;
        }

        if (this.isPlacingObi && this.obiSwitchDelayCounter <= 0 && this.pendingObiPlacement != null) {
            this.finishObsidianPlacement();
            return;
        }

        if (mc.player.getMainHandStack().getItem() != Items.END_CRYSTAL) {
            return;
        }
        this.handleInteraction();
    }

    private void updateCounters() {
        if (this.placeDelayCounter > 0) {
            --this.placeDelayCounter;
        }
        if (this.breakDelayCounter > 0) {
            --this.breakDelayCounter;
        }
        if (this.obiSwitchDelayCounter > 0) {
            --this.obiSwitchDelayCounter;
        }
        if (this.pauseCounter > 0) {
            --this.pauseCounter;
        }
    }

    private boolean isKeyActive() {
        final int d = this.activateKey.get();
        if (d != -1 && !KeyUtils.isKeyPressed(d)) {
            this.resetCounters();
            this.isPlacingObi = false;
            this.pendingObiPlacement = null;
            return this.isActive = false;
        }
        return this.isActive = true;
    }

    private void handleInteraction() {
        final HitResult crosshairTarget = this.mc.crosshairTarget;
        if (this.mc.crosshairTarget instanceof BlockHitResult) {
            this.handleBlockInteraction((BlockHitResult) crosshairTarget);
        } else if (this.mc.crosshairTarget instanceof final EntityHitResult entityHitResult) {
            this.handleEntityInteraction(entityHitResult);
        }
    }

    private void handleBlockInteraction(final BlockHitResult blockHitResult) {
        if (blockHitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }
        if (this.placeDelayCounter > 0 || this.isPlacingObi) {
            return;
        }
        final BlockPos blockPos = blockHitResult.getBlockPos();

        if (this.placeObi.get() && !BlockUtil.isBlockAtPosition(blockPos, Blocks.OBSIDIAN) && !BlockUtil.isBlockAtPosition(blockPos, Blocks.BEDROCK)) {
            if (this.startObsidianPlacement(blockHitResult)) {
                return;
            }
        } else if ((BlockUtil.isBlockAtPosition(blockPos, Blocks.OBSIDIAN) || BlockUtil.isBlockAtPosition(blockPos, Blocks.BEDROCK)) && this.isValidCrystalPlacement(blockPos)) {
            BlockUtil.interactWithBlock(blockHitResult, true);
            this.placeDelayCounter = this.placeDelay.get().intValue();
        }
    }

    private boolean startObsidianPlacement(final BlockHitResult blockHitResult) {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        int obiSlot = -1;

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.OBSIDIAN) {
                obiSlot = i;
                break;
            }
        }

        if (obiSlot == -1) {
            return false;
        }

        int currentSlot = VersionUtil.getSelectedSlot(mc.player);
        int hotbarIndex = 36 + currentSlot;

        if (obiSlot >= 0 && obiSlot <= 8) {
            VersionUtil.setSelectedSlot(mc.player, obiSlot);
        } else {
            int invSlot = obiSlot;

            mc.interactionManager.clickSlot(0, invSlot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(0, hotbarIndex, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(0, invSlot, 0, SlotActionType.PICKUP, mc.player);
        }

        this.isPlacingObi = true;
        this.pendingObiPlacement = blockHitResult;
        this.obiSwitchDelayCounter = this.obiSwitchDelay.get().intValue();

        return true;
    }

    private void finishObsidianPlacement() {
        if (this.pendingObiPlacement == null) {
            this.isPlacingObi = false;
            return;
        }

        BlockUtil.interactWithBlock(this.pendingObiPlacement, true);

        int currentSlot = VersionUtil.getSelectedSlot(mc.player);

        int crystalSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.END_CRYSTAL) {
                crystalSlot = i;
                break;
            }
        }

        if (crystalSlot != -1) {
            VersionUtil.setSelectedSlot(mc.player, crystalSlot);
        }

        final BlockPos blockPos = this.pendingObiPlacement.getBlockPos();
        if (this.isValidCrystalPlacement(blockPos)) {
            BlockUtil.interactWithBlock(this.pendingObiPlacement, true);
            this.placeDelayCounter = this.placeDelay.get().intValue();
        }

        this.isPlacingObi = false;
        this.pendingObiPlacement = null;
    }

    private void handleEntityInteraction(final EntityHitResult entityHitResult) {
        if (this.breakDelayCounter > 0 || this.isPlacingObi) {
            return;
        }
        final Entity entity = entityHitResult.getEntity();
        if (!(entity instanceof EndCrystalEntity) && !(entity instanceof SlimeEntity)) {
            return;
        }
        this.mc.interactionManager.attackEntity(this.mc.player, entity);
        this.mc.player.swingHand(Hand.MAIN_HAND);
        this.breakDelayCounter = this.breakDelay.get().intValue();
    }

    private boolean isValidCrystalPlacement(final BlockPos blockPos) {
        final BlockPos up = blockPos.up();
        if (!this.mc.world.isAir(up)) {
            return false;
        }
        final int getX = up.getX();
        final int getY = up.getY();
        final int compareTo = up.getZ();
        return this.mc.world.getOtherEntities(null, new Box(getX, getY, compareTo, getX + 1.0, getY + 2.0, compareTo + 1.0)).isEmpty();
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
}
