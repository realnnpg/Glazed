package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

import java.util.HashSet;
import java.util.Set;

/**
 * CrystalTweaks -- Crystal PvP inventory & combat safety tweaks.
 *
 * ALL interception (slot clicks + block interactions) runs through
 * CrystalTweaksMixin, which hooks ClientPlayerInteractionManager at HEAD
 * BEFORE any client-side changes are applied and BEFORE any packet is sent.
 * Zero visual desync, zero suspicious packet traces.
 *
 * KeyEvent handles Anti-Drop and Anti-Interrupt independently, as those
 * don't touch inventory or block state.
 *
 * Features:
 *  1. Totem Slot Protection -- prevent totem from being removed from offhand
 *                             or configured hotbar slots (always + brief lock after pop)
 *  2. Anti Drop             -- block Q when no screen is open
 *  3. Anti Interrupt        -- require double-tap of T to open chat
 *  4. Cursor Guard          -- disable PICKUP / drag in inventory
 *  5. Hotbar Lock           -- lock hotbar, comma-separated whitelist slots
 *  6. Glowstone Block       -- block right-clicking glowstone on non-anchor blocks
 *  7. Anchor Max Fill       -- prevent charging an anchor past 1 level
 */
public class CrystalTweaks extends Module {

    // =========================================================================
    // Setting Groups
    // =========================================================================

    private final SettingGroup sgTotemProtect  = settings.createGroup("Totem Slot Protection -- prevent accidental totem removal from offhand / backup slot");
    private final SettingGroup sgAntiDrop      = settings.createGroup("Anti Drop -- block Q outside of any open inventory screen");
    private final SettingGroup sgAntiInterrupt = settings.createGroup("Anti Interrupt -- require double-tap of T to open chat");
    private final SettingGroup sgCursorGuard   = settings.createGroup("Cursor Guard -- disable left/right-click item pickup in inventory");
    private final SettingGroup sgHotbarLock    = settings.createGroup("Hotbar Lock -- freeze hotbar slot config, whitelist exceptions");
    private final SettingGroup sgGlowstone     = settings.createGroup("Glowstone Block -- only allow right-clicking glowstone into anchors");
    private final SettingGroup sgAnchorFill    = settings.createGroup("Anchor Max Fill -- limit anchor charging to 1 glowstone level");

    // =========================================================================
    // 1. Totem Slot Protection
    //    Merges old "Offhand Lock" + "Anti-F Reverse" into one coherent feature.
    //
    //    Always active (when enabled):
    //      - F-key (button 40) cannot remove a totem from the offhand
    //      - Number-key cannot swap a totem out of the configured backup slot
    //
    //    After a pop (for popLockTicks):
    //      - Direct clicks on offhand slot (45) and the backup slot are also blocked
    //        to prevent accidental mis-clicks during restock panic
    // =========================================================================

    private final Setting<Boolean> totemProtectEnabled = sgTotemProtect.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Prevent totems from being accidentally removed from the offhand or your backup hotbar slot. "
            + "Also locks those slots briefly after a pop to prevent mis-clicks during restock.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> alwaysProtectOffhandTotem = sgTotemProtect.add(new BoolSetting.Builder()
        .name("always-protect-offhand-totem")
        .description("Always prevent removing a totem from the offhand slot.")
        .defaultValue(false)
        .visible(totemProtectEnabled::get)
        .build());

    private final Setting<Boolean> alwaysProtectHotbarTotem = sgTotemProtect.add(new BoolSetting.Builder()
        .name("always-protect-hotbar-totem")
        .description("Always prevent removing a totem from the configured hotbar slot.")
        .defaultValue(false)
        .visible(totemProtectEnabled::get)
        .build());

    private final Setting<Integer> totemBackupSlot = sgTotemProtect.add(new IntSetting.Builder()
        .name("backup-hotbar-slot")
        .description("Hotbar slot (1-9) you keep a backup totem in. "
            + "Swapping a totem OUT of this slot is always blocked. "
            + "After a pop this slot is also temporarily locked against direct clicks. "
            + "Set to 0 to only protect the offhand.")
        .defaultValue(9)
        .min(0).max(9)
        .visible(totemProtectEnabled::get)
        .build());

    private final Setting<Integer> popLockTicks = sgTotemProtect.add(new IntSetting.Builder()
        .name("pop-lock-ticks")
        .description("How many ticks to lock the offhand + backup slot after a pop (20 ticks ≈ 1000 ms).")
        .defaultValue(20)
        .min(1).max(40).sliderMax(40)
        .visible(totemProtectEnabled::get)
        .build());

    // =========================================================================
    // 2. Anti Drop
    // =========================================================================

    private final Setting<Boolean> antiDropEnabled = sgAntiDrop.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Block Q (drop) when no inventory or container screen is open. Q works normally inside a screen.")
        .defaultValue(false)
        .build());

    // =========================================================================
    // 3. Anti Interrupt
    // =========================================================================

    private final Setting<Boolean> antiInterruptEnabled = sgAntiInterrupt.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Require a double-tap of T (chat) within the configured window. A single accidental press is silently swallowed.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> doubleTapWindowMs = sgAntiInterrupt.add(new IntSetting.Builder()
        .name("double-tap-window-ms")
        .description("Time window (ms) in which a second T press counts as a double-tap.")
        .defaultValue(300)
        .min(100).max(700).sliderMax(600)
        .visible(antiInterruptEnabled::get)
        .build());

    // =========================================================================
    // 4. Cursor Guard
    // =========================================================================

    private final Setting<Boolean> cursorGuardEnabled = sgCursorGuard.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Block PICKUP (left/right-click drag) and QUICK_CRAFT (multi-slot drag) in any open inventory. "
            + "Items can only be moved via hotkey-binds (number keys) or F. "
            + "Shift-click (QUICK_MOVE) still works.")
        .defaultValue(false)
        .build());

    // =========================================================================
    // 5. Hotbar Lock
    // =========================================================================

    private final Setting<Boolean> hotbarLockEnabled = sgHotbarLock.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Lock all hotbar slots against changes. Whitelist specific slots that are allowed to update freely.")
        .defaultValue(false)
        .build());

    private final Setting<String> hotbarWhitelistSlots = sgHotbarLock.add(new StringSetting.Builder()
        .name("whitelist-slots")
        .description("Comma-separated hotbar slots (1-9) that are free to change. "
            + "Example: '1,2' allows slots 1 and 2. Leave empty to lock all slots.")
        .defaultValue("")
        .visible(hotbarLockEnabled::get)
        .build());

    // =========================================================================
    // 6. Glowstone Block
    // =========================================================================

    private final Setting<Boolean> glowstoneBlockEnabled = sgGlowstone.add(new BoolSetting.Builder()
        .name("enabled")
        .description("While holding Glowstone, cancel right-click on any block that is NOT a Respawn Anchor.")
        .defaultValue(false)
        .build());

    // =========================================================================
    // 7. Anchor Max Fill
    // =========================================================================

    private final Setting<Boolean> anchorFillEnabled = sgAnchorFill.add(new BoolSetting.Builder()
        .name("enabled")
        .description("While holding Glowstone, block charging a Respawn Anchor that already has 1+ charges. "
            + "One charge is all you need before exploding.")
        .defaultValue(false)
        .build());

    // =========================================================================
    // State
    // =========================================================================

    /** Ticks remaining in the post-pop lock window. Decremented every tick. */
    private int popLockTimer = 0;

    /** Timestamp of the last T press, for double-tap detection. */
    private long lastChatKeyPressMs = 0L;

    // =========================================================================
    // Constructor
    // =========================================================================

    public CrystalTweaks() {
        super(GlazedAddon.pvp, "crystal-tweaks", "Crystal PvP inventory & combat safety toolkit. Prevents accidental drops, misclicks, interrupted inputs and inventory mistakes during fights.");
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    public void onActivate() {
        popLockTimer       = 0;
        lastChatKeyPressMs = 0L;
    }

    // =========================================================================
    // Tick -- decrement pop-lock timer
    // =========================================================================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (popLockTimer > 0) popLockTimer--;
    }

    // =========================================================================
    // Packet Receive -- detect totem pop via EntityStatus 35
    // =========================================================================

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!totemProtectEnabled.get()) return;
        if (!(event.packet instanceof EntityStatusS2CPacket packet)) return;
        if (mc.player == null || mc.world == null) return;

        if (packet.getStatus() == 35 && packet.getEntity(mc.world) == mc.player) {
            popLockTimer = popLockTicks.get();
        }
    }

    // =========================================================================
    // Key Event -- Anti Drop + Anti Interrupt
    // Fires before Minecraft processes the bind -- fully clean, no packet sent.
    // =========================================================================

    @EventHandler
    private void onKey(KeyEvent event) {
        if (event.action != KeyAction.Press || mc.player == null) return;

        // --- Anti Drop ---
        if (antiDropEnabled.get() && mc.currentScreen == null) {
            if (mc.options.dropKey.matchesKey(event.key, 0)) {
                event.cancel();
                return;
            }
        }

        // --- Anti Interrupt (double-tap chat) ---
        if (antiInterruptEnabled.get() && mc.currentScreen == null) {
            if (mc.options.chatKey.matchesKey(event.key, 0)) {
                long now = System.currentTimeMillis();
                if (lastChatKeyPressMs != 0L && (now - lastChatKeyPressMs) <= doubleTapWindowMs.get()) {
                    lastChatKeyPressMs = 0L; // valid double-tap, let through
                } else {
                    lastChatKeyPressMs = now; // first press, absorb
                    event.cancel();
                }
            }
        }
    }

    // =========================================================================
    // Public API for CrystalTweaksMixin -- shouldBlockSlotClick
    //
    // Called from the clickSlot injection, BEFORE Minecraft applies any
    // client-side change and BEFORE the packet is constructed.
    // =========================================================================

    public boolean shouldBlockSlotClick(int syncId, int slot, int button, SlotActionType actionType) {
        if (mc.player == null) return false;

        // --- Totem Slot Protection ---
        if (totemProtectEnabled.get()) {
            int backupSlot0 = totemBackupSlot.get() - 1; // 0-indexed; -1 if disabled

            // ALWAYS block: F-key swap (button=40) when offhand has totem (if enabled)
            if (alwaysProtectOffhandTotem.get() && actionType == SlotActionType.SWAP && button == 40) {
                if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                    return true;
                }
            }

            // ALWAYS block: number-key swap OUT of backup slot when it has totem (if enabled)
            if (alwaysProtectHotbarTotem.get() && backupSlot0 >= 0 && actionType == SlotActionType.SWAP && button == backupSlot0) {
                if (mc.player.getInventory().getStack(backupSlot0).isOf(Items.TOTEM_OF_UNDYING)) {
                    return true;
                }
            }

            // ALWAYS block direct clicks on offhand if it has totem (if enabled)
            if (alwaysProtectOffhandTotem.get() && syncId == 0 && slot == 45) {
                if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                    return true;
                }
            }

            // ALWAYS block direct clicks on backup slot if it has totem (if enabled)
            if (alwaysProtectHotbarTotem.get() && backupSlot0 >= 0 && syncId == 0 && slot == 36 + backupSlot0) {
                if (mc.player.getInventory().getStack(backupSlot0).isOf(Items.TOTEM_OF_UNDYING)) {
                    return true;
                }
            }

            // POST-POP LOCK: also block direct clicks on offhand (45) and backup
            // container slot (36 + backupSlot0) for the lock window duration.
            // syncId == 0 = player inventory screen.
            if (popLockTimer > 0 && syncId == 0) {
                if (slot == 45) return true;
                if (backupSlot0 >= 0 && slot == 36 + backupSlot0) return true;
            }
        }

        // --- Cursor Guard ---
        // Block PICKUP (grab onto cursor) and QUICK_CRAFT (drag) in any open screen.
        // SWAP (hotkey / F) and QUICK_MOVE (shift-click) are intentionally allowed.
        if (cursorGuardEnabled.get() && mc.currentScreen != null) {
            if (actionType == SlotActionType.PICKUP || actionType == SlotActionType.QUICK_CRAFT) {
                return true;
            }
        }

        // --- Hotbar Lock ---
        // Scoped to player inventory (syncId == 0).
        if (hotbarLockEnabled.get() && syncId == 0) {
            int affected = resolveAffectedHotbarSlot(slot, button, actionType);
            if (affected >= 0) {
                Set<Integer> whitelist = parseWhitelistSlots();
                if (!whitelist.contains(affected)) {
                    return true;
                }
            }
        }

        return false;
    }

    // =========================================================================
    // Public API for CrystalTweaksMixin -- shouldBlockInteractBlock
    //
    // Called from the interactBlock injection, BEFORE the interaction is
    // processed client-side and BEFORE any packet is sent.
    // Returning true causes the mixin to return ActionResult.FAIL.
    // =========================================================================

    public boolean shouldBlockInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult) {
        if (mc.world == null) return false;

        // Only act when the player is holding Glowstone in their main hand
        if (hand != Hand.MAIN_HAND) return false;
        if (!player.getMainHandStack().isOf(Items.GLOWSTONE)) return false;

        var pos   = hitResult.getBlockPos();
        var state = mc.world.getBlockState(pos);

        // --- Glowstone Block ---
        if (glowstoneBlockEnabled.get() && !state.isOf(Blocks.RESPAWN_ANCHOR)) {
            return true;
        }

        // --- Anchor Max Fill ---
        if (anchorFillEnabled.get() && state.isOf(Blocks.RESPAWN_ANCHOR)) {
            if (state.get(RespawnAnchorBlock.CHARGES) >= 1) {
                return true;
            }
        }

        return false;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Returns the 0-indexed hotbar slot that a given click action would modify,
     * or -1 if no hotbar slot is affected.
     */
    private int resolveAffectedHotbarSlot(int slot, int button, SlotActionType actionType) {
        // Number-key swap: button 0-8 directly maps to hotbar slot
        if (actionType == SlotActionType.SWAP && button >= 0 && button <= 8) {
            return button;
        }
        // Direct click on hotbar slots in player inventory: container slots 36-44
        if ((actionType == SlotActionType.PICKUP || actionType == SlotActionType.QUICK_MOVE)
                && slot >= 36 && slot <= 44) {
            return slot - 36;
        }
        return -1;
    }

    /**
     * Parses the whitelist-slots setting ("1,2,5") into a set of 0-indexed slot indices.
     * Invalid entries are silently ignored.
     */
    private Set<Integer> parseWhitelistSlots() {
        Set<Integer> result = new HashSet<>();
        String raw = hotbarWhitelistSlots.get().trim();
        if (raw.isEmpty()) return result;
        for (String part : raw.split(",")) {
            try {
                int slot = Integer.parseInt(part.trim());
                if (slot >= 1 && slot <= 9) result.add(slot - 1); // convert to 0-indexed
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }
}