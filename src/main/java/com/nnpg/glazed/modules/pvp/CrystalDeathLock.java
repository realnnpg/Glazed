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

/**
 * CrystalDeathLock
 *
 * After detecting a nearby player's death, temporarily blocks:
 *   1. End crystal placement   — cancels PlayerInteractBlockC2SPacket
 *                                when END_CRYSTAL is in the active hand
 *   2. End crystal attacks     — cancels AttackEntityEvent for EndCrystalEntity
 *   3. Respawn anchor interact — cancels PlayerInteractBlockC2SPacket
 *                                when the target block is RESPAWN_ANCHOR
 *
 * Death detection uses PacketEvent.Receive on the Netty IO-thread,
 * which fires before Minecraft's main thread processes the packet.
 * The lock timestamp is written as a volatile long, making it
 * immediately visible to the main thread without synchronisation overhead.
 *
 * Blocking is done via PacketEvent.Send (main thread) and AttackEntityEvent.
 * These are the same cancellation hooks used by NoBlockInteract.java.
 */
public class CrystalDeathLock extends Module {

    // ── Settings ──────────────────────────────────────────────────────────────

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

    // ── State ─────────────────────────────────────────────────────────────────

    /**
     * Timestamp (System.nanoTime()) until which inputs are locked.
     * Written on the Netty IO-thread, read on the main thread.
     * volatile ensures the write is immediately visible across threads
     * without any additional synchronisation.
     * 0L = no active lock.
     */
    private volatile long lockUntilNano = 0L;

    // ── Constructor ───────────────────────────────────────────────────────────

    public CrystalDeathLock() {
        super(GlazedAddon.pvp, "crystal-death-lock",
            "Blocks end crystal and respawn anchor inputs for a configurable window after a nearby player dies.");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onDeactivate() {
        lockUntilNano = 0L;
    }

    // ── Death detection (Netty IO-thread) ─────────────────────────────────────

    /**
     * Fires on the Netty IO-thread, before the main thread sees the packet.
     * EntityStatusS2CPacket status 3 = living entity death.
     *
     * We only write lockUntilNano here — no MC state mutation, which keeps
     * this handler safe to run off the main thread.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityStatusS2CPacket statusPacket)) return;
        if (statusPacket.getStatus() != 3) return; // 3 = death

        if (mc.player == null || mc.world == null) return;

        // Resolve entity — reading the entity map from the Netty thread is safe
        // (read-only access to a ConcurrentHashMap-backed structure).
        Entity entity = statusPacket.getEntity(mc.world);
        if (!(entity instanceof PlayerEntity dead)) return;
        if (dead == mc.player) return; // don't lock on self-death

        // Distance check — reading position fields is safe (volatile in Entity)
        double dist = mc.player.getPos().distanceTo(dead.getPos());
        if (dist > detectionRange.get()) return;

        // Activate the lock.
        // nanoTime gives higher precision than currentTimeMillis and is
        // not affected by system clock adjustments.
        lockUntilNano = System.nanoTime() + (lockDurationMs.get() * 1_000_000L);

        if (notifications.get()) {
            // mc.execute() schedules on the main thread — safe from Netty thread
            String playerName = dead.getName().getString();
            mc.execute(() -> ChatUtils.info(
                String.format("[CrystalDeathLock] §cLocked §r— %s died (%.1f blocks away). "
                    + "Blocking for §e%dms§r.",
                    playerName, dist, lockDurationMs.get())));
        }
    }

    // ── Block crystal placement + anchor interact (main thread) ───────────────

    /**
     * Intercepts outgoing PlayerInteractBlockC2SPacket before it reaches the server.
     *
     * Cancels the packet (and thus the action) when:
     *   - Lock is active AND
     *   - Player is holding END_CRYSTAL (would place a crystal)  →  blockCrystalPlace
     *   - OR the target block is RESPAWN_ANCHOR                  →  blockAnchorInteract
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPacketSend(PacketEvent.Send event) {
        if (!isLocked()) return;
        if (mc.player == null || mc.world == null) return;
        if (!(event.packet instanceof PlayerInteractBlockC2SPacket packet)) return;

        BlockPos pos   = packet.getBlockHitResult().getBlockPos();
        var      block = mc.world.getBlockState(pos).getBlock();

        // Crystal placement: player right-clicks a block holding END_CRYSTAL
        if (blockCrystalPlace.get()) {
            boolean holdsCrystal =
                mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL
                || mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL;

            if (holdsCrystal) {
                event.cancel();
                return;
            }
        }

        // Respawn anchor: right-clicking to charge or interact
        if (blockAnchorInteract.get() && block == Blocks.RESPAWN_ANCHOR) {
            event.cancel();
        }
    }

    // ── Block crystal attacks (main thread) ───────────────────────────────────

    /**
     * AttackEntityEvent fires on the main thread before the attack packet is sent.
     * Cancelling it prevents both the client-side animation and the server packet.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onAttackEntity(AttackEntityEvent event) {
        if (!blockCrystalAttack.get()) return;
        if (!isLocked()) return;
        if (event.entity instanceof EndCrystalEntity) {
            event.cancel();
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Returns true if the lock is currently active.
     * Reads lockUntilNano (volatile) — always sees the latest value
     * written by the Netty thread.
     *
     * Also handles lock expiry notification: the first call after the lock
     * expires logs the "unlocked" message exactly once.
     */
    private boolean isLocked() {
        long now = System.nanoTime();
        if (lockUntilNano == 0L) return false;

        if (now < lockUntilNano) return true;

        // Lock just expired — reset and notify
        if (lockUntilNano != 0L) {
            lockUntilNano = 0L;
            if (notifications.get()) {
                ChatUtils.info("[CrystalDeathLock] §aUnlocked §r— inputs restored.");
            }
        }
        return false;
    }
}
