package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class CrystalMacro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> breakDelay = sgGeneral.add(new IntSetting.Builder()
        .name("break-delay")
        .description("Delay in ticks before breaking crystal after placement (1-10 ticks)")
        .defaultValue(4)
        .min(1)
        .max(10)
        .sliderMin(1)
        .sliderMax(10)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Range to detect and break crystals")
        .defaultValue(4.5)
        .min(1.0)
        .max(6.0)
        .sliderMin(1.0)
        .sliderMax(6.0)
        .build()
    );

    private final Setting<Boolean> onlyWhenHoldingCrystal = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-holding-crystal")
        .description("Only activate when holding end crystal")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Automatically switch to crystal when right-clicking")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> disableLogs = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-logs")
        .description("Disable chat messages")
        .defaultValue(true)
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

    private int breakTimer = 0;
    private int pauseCounter = 0;
    private boolean wasRightClicking = false;
    private Vec3d lastPlacementPos = null;
    private List<PlayerEntity> deadPlayers = new ArrayList<>();

    public CrystalMacro() {
        super(GlazedAddon.pvp, "Crystal Macro", "Automatically breaks crystals after placement when holding right-click.");
    }

    @Override
    public void onActivate() {
        breakTimer = 0;
        pauseCounter = 0;
        wasRightClicking = false;
        lastPlacementPos = null;
        deadPlayers = new ArrayList<>();
        if (!disableLogs.get()) {
            info("Crystal Macro activated!");
        }
    }

    @Override
    public void onDeactivate() {
        breakTimer = 0;
        pauseCounter = 0;
        wasRightClicking = false;
        lastPlacementPos = null;
        deadPlayers.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Update pause counter
        if (pauseCounter > 0) {
            pauseCounter--;
        }

        // Check for dead players and pause if needed
        if (pauseOnKill.get() && checkForDeadPlayers()) {
            pauseCounter = (int)(pauseDelay.get() * 20); // Convert seconds to ticks
            if (mc.player != null && !disableLogs.get()) {
                info("Paused for %.1f seconds due to player death", pauseDelay.get());
            }
        }

        // Check if module is paused due to player death
        if (pauseCounter > 0) {
            return;
        }

        boolean isRightClicking = mc.options.useKey.isPressed();

        if (onlyWhenHoldingCrystal.get() && !isHoldingCrystal()) {
            return;
        }

        if (autoSwitch.get() && isRightClicking && !isHoldingCrystal()) {
            switchToCrystal();
        }

        if (isRightClicking && !wasRightClicking && isHoldingCrystal()) {
            HitResult hitResult = mc.crosshairTarget;
            if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                lastPlacementPos = hitResult.getPos();
                breakTimer = breakDelay.get();
                if (!disableLogs.get()) {
                    info("Crystal placement detected, breaking in %d ticks", breakTimer);
                }
            }
        }

        if (breakTimer > 0) {
            breakTimer--;
            if (breakTimer == 0) {
                breakNearestCrystal();
            }
        }

        if (isRightClicking && isHoldingCrystal()) {
            breakNearbyNewestCrystal();
        }

        wasRightClicking = isRightClicking;
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

    private void breakNearestCrystal() {
        if (lastPlacementPos == null) return;

        List<EndCrystalEntity> crystals = mc.world.getEntitiesByClass(EndCrystalEntity.class,
            new Box(lastPlacementPos.add(-1, -1, -1), lastPlacementPos.add(1, 2, 1)),
            crystal -> crystal.squaredDistanceTo(mc.player) <= range.get() * range.get());

        if (!crystals.isEmpty()) {
            EndCrystalEntity closest = crystals.get(0);
            for (EndCrystalEntity crystal : crystals) {
                if (crystal.squaredDistanceTo(lastPlacementPos) < closest.squaredDistanceTo(lastPlacementPos)) {
                    closest = crystal;
                }
            }

            attackCrystal(closest);
            lastPlacementPos = null;
        }
    }

    private void breakNearbyNewestCrystal() {
        List<EndCrystalEntity> crystals = mc.world.getEntitiesByClass(EndCrystalEntity.class,
            new Box(mc.player.getPos().add(-range.get(), -range.get(), -range.get()),
                mc.player.getPos().add(range.get(), range.get(), range.get())),
            crystal -> crystal.squaredDistanceTo(mc.player) <= range.get() * range.get());

        if (!crystals.isEmpty()) {
            EndCrystalEntity newest = crystals.get(0);
            for (EndCrystalEntity crystal : crystals) {
                if (crystal.getId() > newest.getId()) {
                    newest = crystal;
                }
            }

            if (mc.player.age % (2 + (int)(Math.random() * 3)) == 0) {
                attackCrystal(newest);
            }
        }
    }

    private void attackCrystal(EndCrystalEntity crystal) {
        if (crystal == null || crystal.isRemoved()) return;

        try {
            Vec3d crystalPos = crystal.getPos().add(0, crystal.getHeight() / 2, 0);

            mc.interactionManager.attackEntity(mc.player, crystal);
            mc.player.swingHand(Hand.MAIN_HAND);

            if (!disableLogs.get()) {
                info("Attacked crystal at %.1f, %.1f, %.1f", crystal.getX(), crystal.getY(), crystal.getZ());
            }

        } catch (Exception e) {
            if (!disableLogs.get()) {
                error("Failed to attack crystal: " + e.getMessage());
            }
        }
    }

    private boolean isHoldingCrystal() {
        if (mc.player == null) return false;
        return mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL ||
            mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL;
    }

    private void switchToCrystal() {
        if (mc.player == null) return;

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.END_CRYSTAL) {
                mc.player.getInventory().setSelectedSlot(i);
                if (!disableLogs.get()) {
                    info("Switched to crystal in slot %d", i + 1);
                }
                break;
            }
        }
    }
}
