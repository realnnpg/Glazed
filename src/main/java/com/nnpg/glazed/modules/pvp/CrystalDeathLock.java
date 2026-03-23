package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.BlockPos;

public class CrystalDeathLock extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> detectionRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("detection-range")
        .description("Radius around your position in which a player death triggers the lock.")
        .defaultValue(15.0)
        .min(1.0)
        .sliderMax(30.0)
        .build()
    );

    private final Setting<Integer> lockDurationMs = sgGeneral.add(new IntSetting.Builder()
        .name("lock-duration-ms")
        .description("How long (milliseconds) inputs are blocked after the death. Default: 1000 = 1 second.")
        .defaultValue(1000)
        .min(100)
        .sliderRange(100, 5000)
        .build()
    );

    private final Setting<Boolean> blockCrystalPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("block-crystal-place")
        .description("Prevent placing end crystals during the lock window.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blockCrystalAttack = sgGeneral.add(new BoolSetting.Builder()
        .name("block-crystal-attack")
        .description("Prevent left-clicking (attacking) end crystals during the lock window.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blockAnchorInteract = sgGeneral.add(new BoolSetting.Builder()
        .name("block-anchor-interact")
        .description("Prevent right-clicking respawn anchors during the lock window.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show a chat message when the lock activates and expires.")
        .defaultValue(true)
        .build()
    );

    private volatile long lockUntilNano = 0L;

    public CrystalDeathLock() {
        super(GlazedAddon.pvp, "crystal-death-lock",
            "Blocks end crystal and respawn anchor inputs for a configurable window after a nearby player dies.");
    }

    @Override
    public void onDeactivate() {
        lockUntilNano = 0L;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityStatusS2CPacket statusPacket)) return;
        if (statusPacket.getStatus() != 3) return;

        if (mc.player == null || mc.world == null) return;
        Entity entity = statusPacket.getEntity(mc.world);
        if (!(entity instanceof PlayerEntity dead)) return;
        if (dead == mc.player) return;
        double dist = mc.player.getPos().distanceTo(dead.getPos());
        if (dist > detectionRange.get()) return;
        lockUntilNano = System.nanoTime() + (lockDurationMs.get() * 1_000_000L);

        if (notifications.get()) {
            String playerName = dead.getName().getString();
            mc.execute(() -> ChatUtils.info(
                String.format("[CrystalDeathLock] §cLocked §r— %s died (%.1f blocks away). "
                    + "Blocking for §e%dms§r.",
                    playerName, dist, lockDurationMs.get())));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPacketSend(PacketEvent.Send event) {
        if (!isLocked()) return;
        if (mc.player == null || mc.world == null) return;
        if (!(event.packet instanceof PlayerInteractBlockC2SPacket packet)) return;

        BlockPos pos   = packet.getBlockHitResult().getBlockPos();
        var      block = mc.world.getBlockState(pos).getBlock();
        if (blockCrystalPlace.get()) {
            boolean holdsCrystal =
                mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL
                || mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL;

            if (holdsCrystal) {
                event.cancel();
                return;
            }
        }
        if (blockAnchorInteract.get() && block == Blocks.RESPAWN_ANCHOR) {
            event.cancel();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onAttackEntity(AttackEntityEvent event) {
        if (!blockCrystalAttack.get()) return;
        if (!isLocked()) return;
        if (event.entity instanceof EndCrystalEntity) {
            event.cancel();
        }
    }

    private boolean isLocked() {
        long now = System.nanoTime();
        if (lockUntilNano == 0L) return false;

        if (now < lockUntilNano) return true;
        if (lockUntilNano != 0L) {
            lockUntilNano = 0L;
            if (notifications.get()) {
                ChatUtils.info("[CrystalDeathLock] §aUnlocked §r— inputs restored.");
            }
        }
        return false;
    }
}
