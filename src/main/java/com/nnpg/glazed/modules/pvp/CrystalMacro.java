package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
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
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class CrystalMacro extends Module {
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

    private int placeDelayCounter;
    private int breakDelayCounter;
    public boolean isActive;

    public CrystalMacro() {
        super(GlazedAddon.pvp, "CystalMacro", "Automatically crystals fast for you");
    }

    @Override
    public void onActivate() {
        this.resetCounters();
        this.isActive = false;
    }

    @Override
    public void onDeactivate() {
        this.resetCounters();
        this.isActive = false;
    }

    private void resetCounters() {
        this.placeDelayCounter = 0;
        this.breakDelayCounter = 0;
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

        if (!this.isKeyActive()) {
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
    }

    private boolean isKeyActive() {
        final int d = this.activateKey.get();
        if (d != -1 && !KeyUtils.isKeyPressed(d)) {
            this.resetCounters();
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
        if (this.placeDelayCounter > 0) {
            return;
        }
        final BlockPos blockPos = blockHitResult.getBlockPos();
        if ((BlockUtil.isBlockAtPosition(blockPos, Blocks.OBSIDIAN) || BlockUtil.isBlockAtPosition(blockPos, Blocks.BEDROCK)) && this.isValidCrystalPlacement(blockPos)) {
            BlockUtil.interactWithBlock(blockHitResult, true);
            this.placeDelayCounter = this.placeDelay.get().intValue();
        }
    }

    private void handleEntityInteraction(final EntityHitResult entityHitResult) {
        if (this.breakDelayCounter > 0) {
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
}
