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
public class CrystalTweaks extends Module {

    private final SettingGroup sgTotemProtect  = settings.createGroup("Totem Slot Protection -- prevent accidental totem removal from offhand / backup slot");
    private final SettingGroup sgAntiDrop      = settings.createGroup("Anti Drop -- block Q outside of any open inventory screen");
    private final SettingGroup sgAntiInterrupt = settings.createGroup("Anti Interrupt -- require double-tap of T to open chat");
    private final SettingGroup sgCursorGuard   = settings.createGroup("Cursor Guard -- disable left/right-click item pickup in inventory");
    private final SettingGroup sgHotbarLock    = settings.createGroup("Hotbar Lock -- freeze hotbar slot config, whitelist exceptions");
    private final SettingGroup sgGlowstone     = settings.createGroup("Glowstone Block -- only allow right-clicking glowstone into anchors");
    private final SettingGroup sgAnchorFill    = settings.createGroup("Anchor Max Fill -- limit anchor charging to 1 glowstone level");

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

    private final Setting<Boolean> antiDropEnabled = sgAntiDrop.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Block Q (drop) when no inventory or container screen is open. Q works normally inside a screen.")
        .defaultValue(false)
        .build());

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

    private final Setting<Boolean> cursorGuardEnabled = sgCursorGuard.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Block PICKUP (left/right-click drag) and QUICK_CRAFT (multi-slot drag) in any open inventory. "
            + "Items can only be moved via hotkey-binds (number keys) or F. "
            + "Shift-click (QUICK_MOVE) still works.")
        .defaultValue(false)
        .build());

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

    private final Setting<Boolean> glowstoneBlockEnabled = sgGlowstone.add(new BoolSetting.Builder()
        .name("enabled")
        .description("While holding Glowstone, cancel right-click on any block that is NOT a Respawn Anchor.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> anchorFillEnabled = sgAnchorFill.add(new BoolSetting.Builder()
        .name("enabled")
        .description("While holding Glowstone, block charging a Respawn Anchor that already has 1+ charges. "
            + "One charge is all you need before exploding.")
        .defaultValue(false)
        .build());

    private int popLockTimer = 0;

    private long lastChatKeyPressMs = 0L;

    public CrystalTweaks() {
        super(GlazedAddon.pvp, "crystal-tweaks", "Crystal PvP inventory & combat safety toolkit. Prevents accidental drops, misclicks, interrupted inputs and inventory mistakes during fights.");
    }

    @Override
    public void onActivate() {
        popLockTimer       = 0;
        lastChatKeyPressMs = 0L;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (popLockTimer > 0) popLockTimer--;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!totemProtectEnabled.get()) return;
        if (!(event.packet instanceof EntityStatusS2CPacket packet)) return;
        if (mc.player == null || mc.world == null) return;

        if (packet.getStatus() == 35 && packet.getEntity(mc.world) == mc.player) {
            popLockTimer = popLockTicks.get();
        }
    }

    @EventHandler
    private void onKey(KeyEvent event) {
        if (event.action != KeyAction.Press || mc.player == null) return;
        if (antiDropEnabled.get() && mc.currentScreen == null) {
            if (mc.options.dropKey.matchesKey(event.key, 0)) {
                event.cancel();
                return;
            }
        }
        if (antiInterruptEnabled.get() && mc.currentScreen == null) {
            if (mc.options.chatKey.matchesKey(event.key, 0)) {
                long now = System.currentTimeMillis();
                if (lastChatKeyPressMs != 0L && (now - lastChatKeyPressMs) <= doubleTapWindowMs.get()) {
                    lastChatKeyPressMs = 0L;
                } else {
                    lastChatKeyPressMs = now;
                    event.cancel();
                }
            }
        }
    }

    public boolean shouldBlockSlotClick(int syncId, int slot, int button, SlotActionType actionType) {
        if (mc.player == null) return false;
        if (totemProtectEnabled.get()) {
            int backupSlot0 = totemBackupSlot.get() - 1;
            if (alwaysProtectOffhandTotem.get() && actionType == SlotActionType.SWAP && button == 40) {
                if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                    return true;
                }
            }
            if (alwaysProtectHotbarTotem.get() && backupSlot0 >= 0 && actionType == SlotActionType.SWAP && button == backupSlot0) {
                if (mc.player.getInventory().getStack(backupSlot0).isOf(Items.TOTEM_OF_UNDYING)) {
                    return true;
                }
            }
            if (alwaysProtectOffhandTotem.get() && syncId == 0 && slot == 45) {
                if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                    return true;
                }
            }
            if (alwaysProtectHotbarTotem.get() && backupSlot0 >= 0 && syncId == 0 && slot == 36 + backupSlot0) {
                if (mc.player.getInventory().getStack(backupSlot0).isOf(Items.TOTEM_OF_UNDYING)) {
                    return true;
                }
            }
            if (popLockTimer > 0 && syncId == 0) {
                if (slot == 45) return true;
                if (backupSlot0 >= 0 && slot == 36 + backupSlot0) return true;
            }
        }
        if (cursorGuardEnabled.get() && mc.currentScreen != null) {
            if (actionType == SlotActionType.PICKUP || actionType == SlotActionType.QUICK_CRAFT) {
                return true;
            }
        }
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

    public boolean shouldBlockInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult) {
        if (mc.world == null) return false;
        if (hand != Hand.MAIN_HAND) return false;
        if (!player.getMainHandStack().isOf(Items.GLOWSTONE)) return false;

        var pos   = hitResult.getBlockPos();
        var state = mc.world.getBlockState(pos);
        if (glowstoneBlockEnabled.get() && !state.isOf(Blocks.RESPAWN_ANCHOR)) {
            return true;
        }
        if (anchorFillEnabled.get() && state.isOf(Blocks.RESPAWN_ANCHOR)) {
            if (state.get(RespawnAnchorBlock.CHARGES) >= 1) {
                return true;
            }
        }

        return false;
    }

    private int resolveAffectedHotbarSlot(int slot, int button, SlotActionType actionType) {
        if (actionType == SlotActionType.SWAP && button >= 0 && button <= 8) {
            return button;
        }
        if ((actionType == SlotActionType.PICKUP || actionType == SlotActionType.QUICK_MOVE)
                && slot >= 36 && slot <= 44) {
            return slot - 36;
        }
        return -1;
    }

    private Set<Integer> parseWhitelistSlots() {
        Set<Integer> result = new HashSet<>();
        String raw = hotbarWhitelistSlots.get().trim();
        if (raw.isEmpty()) return result;
        for (String part : raw.split(",")) {
            try {
                int slot = Integer.parseInt(part.trim());
                if (slot >= 1 && slot <= 9) result.add(slot - 1);
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }
}