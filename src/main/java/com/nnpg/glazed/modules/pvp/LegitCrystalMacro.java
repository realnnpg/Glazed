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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.HashSet;
import java.util.Set;

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

    private final Setting<Boolean> stopOnKill = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-on-kill")
        .description("Pauses the macro when a nearby player dies, then resumes after 5 seconds.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> placeObsidianIfMissing = sgGeneral.add(new BoolSetting.Builder()
        .name("place-obsidian-if-missing")
        .description("Places obsidian if the target block isn't obsidian or bedrock.")
        .defaultValue(true)
        .build()
    );

    private int placeDelayCounter;
    private int breakDelayCounter;

    private final Set<PlayerEntity> deadPlayers = new HashSet<>();
    private boolean paused = false;
    private long resumeTime = 0;

    public LegitCrystalMacro() {
        super(GlazedAddon.pvp, "LegitCrystalMacro", "Automatically crystals fast for you");
    }

    @Override
    public void onActivate() {
        resetCounters();
        deadPlayers.clear();
        paused = false;
        resumeTime = 0;
    }

    @Override
    public void onDeactivate() {
        resetCounters();
        deadPlayers.clear();
        paused = false;
        resumeTime = 0;
    }

    private void resetCounters() {
        placeDelayCounter = 0;
        breakDelayCounter = 0;
    }

    @EventHandler
    private void onTick(final TickEvent.Pre event) {
        if (mc.currentScreen != null) return;

        updateCounters();

        if (paused && System.currentTimeMillis() >= resumeTime) {
            paused = false;
            if (mc.player != null) {
                mc.player.sendMessage(net.minecraft.text.Text.literal(
                    "§7[§bLegitCrystalMacro§7] §aResumed after stop-on-kill"
                ), false);
            }
        }

        if (paused) return;
        if (!isKeyActive()) return;
        if (mc.player.isUsingItem()) return;
        if (mc.player.getMainHandStack().getItem() != Items.END_CRYSTAL) return;

        if (stopOnKill.get() && checkForDeadPlayers()) {
            paused = true;
            resumeTime = System.currentTimeMillis() + 5000;
            if (mc.player != null) {
                mc.player.sendMessage(net.minecraft.text.Text.literal(
                    "§7[§bLegitCrystalMacro§7] §cPaused due to player death (will resume in 5s)"
                ), false);
            }
            return;
        }

        handleInteraction();
    }

    private void updateCounters() {
        if (placeDelayCounter > 0) --placeDelayCounter;
        if (breakDelayCounter > 0) --breakDelayCounter;
    }

    private boolean isKeyActive() {
        int d = activateKey.get();
        return d == -1 || KeyUtils.isKeyPressed(d);
    }

    private void handleInteraction() {
        HitResult crosshairTarget = mc.crosshairTarget;
        if (crosshairTarget instanceof BlockHitResult blockHit) {
            handleBlockInteraction(blockHit);
        } else if (crosshairTarget instanceof EntityHitResult entityHit) {
            handleEntityInteraction(entityHit);
        }
    }

    private void handleBlockInteraction(BlockHitResult blockHitResult) {
        if (blockHitResult.getType() != HitResult.Type.BLOCK) return;
        if (placeDelayCounter > 0) return;

        BlockPos blockPos = blockHitResult.getBlockPos();
        boolean isObsidianOrBedrock = BlockUtil.isBlockAtPosition(blockPos, Blocks.OBSIDIAN) ||
                                      BlockUtil.isBlockAtPosition(blockPos, Blocks.BEDROCK);

        if (!isObsidianOrBedrock && placeObsidianIfMissing.get()) {
            int obsidianSlot = findObsidianSlot();
            int crystalSlot = findCrystalSlot();

            if (obsidianSlot == -1 || crystalSlot == -1) return;

            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(obsidianSlot));
            BlockUtil.interactWithBlock(blockHitResult, true);
            mc.player.swingHand(Hand.MAIN_HAND);
            placeDelayCounter = placeDelay.get().intValue();

            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(crystalSlot));
            return;
        }

        if (isObsidianOrBedrock && isValidCrystalPlacement(blockPos)) {
            BlockUtil.interactWithBlock(blockHitResult, true);
            placeDelayCounter = placeDelay.get().intValue();
        }
    }

    private void handleEntityInteraction(EntityHitResult entityHitResult) {
        if (breakDelayCounter > 0) return;

        Entity entity = entityHitResult.getEntity();
        if (!(entity instanceof EndCrystalEntity) && !(entity instanceof SlimeEntity)) return;

        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
        breakDelayCounter = breakDelay.get().intValue();
    }

    private boolean isValidCrystalPlacement(BlockPos blockPos) {
        BlockPos up = blockPos.up();
        if (!mc.world.isAir(up)) return false;

        int x = up.getX(), y = up.getY(), z = up.getZ();
        return mc.world.getOtherEntities(null, new Box(x, y, z, x + 1.0, y + 2.0, z + 1.0)).isEmpty();
    }

    private boolean checkForDeadPlayers() {
        if (mc.world == null) return false;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;

            String name = player.getGameProfile().getName();
            boolean isBedrock = name.startsWith(".");

            if (player.isDead() || player.getHealth() <= 0) {
                if (!deadPlayers.contains(player)) {
                    deadPlayers.add(player);
                    return true;
                }
            }
        }

        deadPlayers.removeIf(p -> !p.isDead() && p.getHealth() > 0);
        return false;
    }

    private int findObsidianSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.OBSIDIAN)) return i;
        }
        return -1;
    }

    private int findCrystalSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.END_CRYSTAL)) return i;
        }
        return -1;
    }
}
