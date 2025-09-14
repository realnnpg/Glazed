package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.screen.slot.SlotActionType;

public class AutoInvTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks before moving totem (1-20 ticks)")
        .defaultValue(3)
        .min(1)
        .max(20)
        .sliderMin(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> moveFromHotbar = sgGeneral.add(new BoolSetting.Builder()
        .name("move-from-hotbar")
        .description("Also move totems from hotbar slots")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableLogs = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-logs")
        .description("Disable chat messages about totem movements")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> openInv = sgGeneral.add(new BoolSetting.Builder()
        .name("open-inv")
        .description("Automatically open inventory when totem pops")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> invOpenDelay = sgGeneral.add(new IntSetting.Builder()
        .name("inv-open-delay")
        .description("Ticks to wait before opening inventory after totem pop (1-10 ticks)")
        .defaultValue(2)
        .min(1)
        .max(10)
        .sliderMin(1)
        .sliderMax(10)
        .visible(() -> openInv.get())
        .build()
    );

    private final Setting<Integer> invCloseDelay = sgGeneral.add(new IntSetting.Builder()
        .name("inv-close-delay")
        .description("Ticks to wait before closing inventory after opening (5-20 ticks)")
        .defaultValue(8)
        .min(5)
        .max(20)
        .sliderMin(5)
        .sliderMax(20)
        .visible(() -> openInv.get())
        .build()
    );

    private boolean needsTotem = false;
    private int delayTicks = 0;
    private boolean hadTotemInOffhand = false;

    private boolean shouldOpenInv = false;
    private int invOpenTicks = 0;
    private int invCloseTicks = 0;
    private boolean invAutoOpened = false;

    public AutoInvTotem() {
        super(GlazedAddon.pvp, "Auto Inv Totem", "Automatically moves totems to offhand when inventory is opened after totem pop.");
    }

    @Override
    public void onActivate() {
        resetState();
    }

    @Override
    public void onDeactivate() {
        resetInvAutoState();
    }

    private void resetState() {
        if (mc.player != null) {
            hadTotemInOffhand = hasTotemInOffhand();
            needsTotem = false;
            delayTicks = 0;
            resetInvAutoState();
        }
    }

    private void resetInvAutoState() {
        shouldOpenInv = false;
        invOpenTicks = 0;
        invCloseTicks = 0;
        invAutoOpened = false;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        resetState();
        if (!disableLogs.get()) {
            info("Auto Inv Totem state reset for new world/reconnection");
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof EntityStatusS2CPacket packet) {
            if (packet.getStatus() == 35 && mc.player != null && packet.getEntity(mc.world) == mc.player) {
                if (openInv.get() && mc.currentScreen == null) {
                    shouldOpenInv = true;
                    invOpenTicks = invOpenDelay.get();
                    if (!disableLogs.get()) {
                        info("Player totem pop detected! Will auto-open inventory in %d ticks.", invOpenTicks);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        handleAutoInventory();

        boolean currentlyHasTotem = hasTotemInOffhand();

        if (hadTotemInOffhand && !currentlyHasTotem) {
            needsTotem = true;
            if (!disableLogs.get()) {
                info("Totem popped! Open inventory to auto-equip a new one.");
            }

            if (mc.currentScreen instanceof InventoryScreen) {
                if (!disableLogs.get()) {
                    info("Inventory already open - moving totem immediately!");
                }
                delayTicks = delay.get();
            }
        }

        hadTotemInOffhand = currentlyHasTotem;

        if (currentlyHasTotem && needsTotem) {
            needsTotem = false;
            delayTicks = 0;
        }
    }

    private void handleAutoInventory() {
        if (shouldOpenInv && invOpenTicks > 0) {
            invOpenTicks--;
            if (invOpenTicks == 0 && mc.currentScreen == null) {
                mc.setScreen(new InventoryScreen(mc.player));
                invAutoOpened = true;
                invCloseTicks = invCloseDelay.get();
                shouldOpenInv = false;
                if (!disableLogs.get()) {
                    info("Auto-opened inventory, will close in %d ticks.", invCloseTicks);
                }
            }
        }

        if (invAutoOpened && invCloseTicks > 0) {
            invCloseTicks--;
            if (invCloseTicks == 0 && mc.currentScreen instanceof InventoryScreen) {
                mc.setScreen(null);
                invAutoOpened = false;
                if (!disableLogs.get()) {
                    info("Auto-closed inventory.");
                }
            }
        }

        if (invAutoOpened && !(mc.currentScreen instanceof InventoryScreen)) {
            invAutoOpened = false;
            invCloseTicks = 0;
        }
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!(event.screen instanceof InventoryScreen) || !needsTotem || mc.player == null) return;

        delayTicks = delay.get();
    }

    @EventHandler
    private void onTickDelayed(TickEvent.Post event) {
        if (delayTicks <= 0 || mc.player == null) return;

        delayTicks--;

        if (delayTicks == 0) {
            moveTotemToOffhand();
        }
    }

    private void moveTotemToOffhand() {
        int totemSlot = findTotemSlot();
        if (totemSlot == -1) {
            if (!disableLogs.get()) {
                info("No totem found in inventory!");
            }
            return;
        }

        try {
            int containerSlot = totemSlot;
            if (totemSlot < 9) {
                containerSlot = totemSlot + 36;
            }

            if (!disableLogs.get()) {
                info("Found totem in slot %d (container slot %d)", totemSlot, containerSlot);
            }

            ItemStack offhandStack = mc.player.getOffHandStack();

            if (offhandStack.isEmpty()) {
                mc.interactionManager.clickSlot(0, containerSlot, 40, SlotActionType.SWAP, mc.player);
                if (!disableLogs.get()) {
                    info("Swapped totem to empty offhand");
                }
            } else {
                mc.interactionManager.clickSlot(0, containerSlot, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(0, 45, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(0, containerSlot, 0, SlotActionType.PICKUP, mc.player);
                if (!disableLogs.get()) {
                    info("3-click swapped totem to offhand");
                }
            }

            needsTotem = false;

        } catch (Exception e) {
            if (!disableLogs.get()) {
                error("Failed to move totem: " + e.getMessage());
            }
        }
    }

    private int findTotemSlot() {
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                return i;
            }
        }

        if (moveFromHotbar.get()) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                    return i;
                }
            }
        }

        return -1;
    }

    private boolean hasTotemInOffhand() {
        if (mc.player == null) return false;
        ItemStack offhandStack = mc.player.getOffHandStack();
        return !offhandStack.isEmpty() && offhandStack.getItem() == Items.TOTEM_OF_UNDYING;
    }
}
