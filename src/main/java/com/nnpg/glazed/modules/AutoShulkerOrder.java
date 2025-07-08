package com.nnpg.glazed.modules;


import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

public class AutoShulkerOrder extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private enum Stage {NONE, SHOP, SHOP_END, SHOP_SHULKER, SHOP_CONFIRM, SHOP_CHECK_FULL, SHOP_EXIT, WAIT, ORDERS, ORDERS_SELECT, ORDERS_EXIT, ORDERS_CONFIRM, ORDERS_FINAL_EXIT, CYCLE_PAUSE}

    private Stage stage = Stage.NONE;
    private long stageStart = 0;
    private static final long WAIT_TIME_MS = 500;
    private int shulkerMoveIndex = 9;
    private long lastShulkerMoveTime = 0;
    private int exitCount = 0;
    private int finalExitCount = 0;
    private long finalExitStart = 0;
    private long shulkerDelayStart = 0;

    public AutoShulkerOrder() {
        super(GlazedAddon.CATEGORY, "AutoShulkerOrder", "Automatically buys shulker and sells them in orders for profit");

    }

    @Override
    public void onActivate() {
        stage = Stage.SHOP;
        stageStart = System.currentTimeMillis();
        shulkerMoveIndex = 0;
        lastShulkerMoveTime = 0;
        exitCount = 0;
        finalExitCount = 0;
    }

    @Override
    public void onDeactivate() {
        stage = Stage.NONE;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        long now = System.currentTimeMillis();

        switch (stage) {
            case SHOP -> {
                ChatUtils.sendPlayerMsg("/shop");
                stage = Stage.SHOP_END;
                stageStart = now;
            }
            case SHOP_END -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && isEndStone(stack)) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.SHOP_SHULKER;
                            shulkerDelayStart = System.currentTimeMillis();
                            stageStart = now;
                            return;
                        }
                    }
                    if (now - stageStart > 5000) {
                        mc.player.closeHandledScreen();
                        info("Timeout on SHOP_END, restarting cycle.");
                        stage = Stage.SHOP;
                        stageStart = now;
                    }
                }
            }
            case SHOP_SHULKER -> {
                if (System.currentTimeMillis() - shulkerDelayStart < 500) return;
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && isShulkerBox(stack)) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.SHOP_CONFIRM;
                            stageStart = now;
                            return;
                        }
                    }
                    if (now - stageStart > 5000) {
                        mc.player.closeHandledScreen();
                        info("Timeout on SHOP_SHULKER, restarting cycle.");
                        stage = Stage.SHOP;
                        stageStart = now;
                    }
                }
            }
            case SHOP_CONFIRM -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && isGreenGlass(stack)) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.SHOP_CHECK_FULL;
                            stageStart = now;
                            return;
                        }
                    }
                    if (now - stageStart > 5000) {
                        mc.player.closeHandledScreen();
                        info("Timeout on SHOP_CONFIRM, restarting cycle.");
                        stage = Stage.SHOP;
                        stageStart = now;
                    }
                }
            }
            case SHOP_CHECK_FULL -> {
                if (isInventoryFull()) {
                    mc.player.closeHandledScreen();
                    stage = Stage.SHOP_EXIT;
                    stageStart = now;
                } else {
                    stage = Stage.SHOP_SHULKER;
                    stageStart = now;
                }
                if (now - stageStart > 5000) {
                    mc.player.closeHandledScreen();
                    info("Timeout on SHOP_CHECK_FULL, restarting cycle.");
                    stage = Stage.SHOP;
                    stageStart = now;
                }
            }
            case SHOP_EXIT -> {
                if (mc.currentScreen == null) {
                    stage = Stage.WAIT;
                    stageStart = now;
                }
                if (now - stageStart > 5000) {
                    mc.player.closeHandledScreen();
                    info("Timeout on SHOP_EXIT, restarting cycle.");
                    stage = Stage.SHOP;
                    stageStart = now;
                }
            }
            case WAIT -> {
                if (now - stageStart >= WAIT_TIME_MS) {
                    ChatUtils.sendPlayerMsg("/orders shulker");
                    stage = Stage.ORDERS;
                    stageStart = now;
                }
            }
            case ORDERS -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && isShulkerBox(stack) && isPurple(stack)) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.ORDERS_SELECT;
                            stageStart = now;
                            return;
                        }
                    }
                    if (now - stageStart > 5000) {
                        mc.player.closeHandledScreen();
                        info("Timeout on ORDERS, restarting cycle.");
                        stage = Stage.SHOP;
                        stageStart = now;
                    }
                }
            }
            case ORDERS_SELECT -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    if (shulkerMoveIndex > 35) {
                        mc.player.closeHandledScreen();
                        stage = Stage.ORDERS_CONFIRM;
                        stageStart = now;
                        shulkerMoveIndex = 0;
                        return;
                    }
                    if (now - lastShulkerMoveTime >= 50) {
                        ItemStack stack = mc.player.getInventory().getStack(shulkerMoveIndex);
                        if (!stack.isEmpty() && isShulkerBox(stack)) {
                            for (Slot slot : handler.slots) {
                                if (slot.inventory != mc.player.getInventory() && slot.getStack().isEmpty()) {
                                    int invSlot = 36 + shulkerMoveIndex - 9;
                                    if (shulkerMoveIndex < 9) invSlot = shulkerMoveIndex;
                                    mc.interactionManager.clickSlot(handler.syncId, invSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
                                    lastShulkerMoveTime = now;
                                    break;
                                }
                            }
                        }
                        shulkerMoveIndex++;
                    }
                }
            }
            case ORDERS_EXIT -> {
                if (mc.currentScreen == null) {
                    exitCount++;
                    if (exitCount < 2) {
                        mc.player.closeHandledScreen();
                        stageStart = now;
                    } else {
                        exitCount = 0;
                        stage = Stage.ORDERS_CONFIRM;
                        stageStart = now;
                    }
                }
            }
            case ORDERS_CONFIRM -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && isGreenGlass(stack)) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.ORDERS_FINAL_EXIT;
                            stageStart = now;
                            finalExitCount = 0;
                            finalExitStart = now;
                            return;
                        }
                    }
                    if (now - stageStart > 5000) {
                        mc.player.closeHandledScreen();
                        info("Timeout on ORDERS_CONFIRM, restarting cycle.");
                        stage = Stage.SHOP;
                        stageStart = now;
                    }
                }
            }
            case ORDERS_FINAL_EXIT -> {
                if (finalExitCount == 0) {
                    if (System.currentTimeMillis() - finalExitStart >= 500) {
                        mc.player.closeHandledScreen();
                        finalExitCount++;
                        finalExitStart = System.currentTimeMillis();
                    }
                } else if (finalExitCount == 1) {
                    if (System.currentTimeMillis() - finalExitStart >= 400) {
                        mc.player.closeHandledScreen();
                        finalExitCount++;
                        finalExitStart = System.currentTimeMillis();
                    }
                } else {
                    finalExitCount = 0;
                    stage = Stage.CYCLE_PAUSE;
                    stageStart = System.currentTimeMillis();
                }
            }
            case CYCLE_PAUSE -> {
                if (now - stageStart >= WAIT_TIME_MS) {
                    stage = Stage.SHOP;
                    stageStart = now;
                }
            }
            case NONE -> {
            }
        }
    }

    private boolean isEndStone(ItemStack stack) {
        return stack.getItem() == Items.END_STONE || stack.getName().getString().toLowerCase(Locale.ROOT).contains("end");
    }

    private boolean isShulkerBox(ItemStack stack) {
        return stack.getItem().getName().getString().toLowerCase(Locale.ROOT).contains("shulker box");
    }

    private boolean isPurple(ItemStack stack) {
        return stack.getItem() == Items.SHULKER_BOX;
    }

    private boolean isGreenGlass(ItemStack stack) {
        return stack.getItem() == Items.LIME_STAINED_GLASS_PANE || stack.getItem() == Items.GREEN_STAINED_GLASS_PANE;
    }

    private boolean isInventoryFull() {
        for (int i = 9; i <= 35; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) return false;
        }
        return true;
    }
}
