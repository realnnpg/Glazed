package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class AutoOrder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("Target Item");

    private final Setting<String> targetItemName = sgTarget.add(new StringSetting.Builder()
        .name("Item Name")
        .description("Name of the item to order")
        .defaultValue("diamond")
        .build()
    );

    private final Setting<Item> snipingItem = sgTarget.add(new ItemSetting.Builder()
        .name("Snipping Item")
        .description("The item to snipe from orders.")
        .defaultValue(Items.AIR)
        .build()
    );

    private final Setting<Integer> moveDelay = sgGeneral.add(new IntSetting.Builder()
        .name("Move delay")
        .description("Delay between item movements")
        .defaultValue(10)
        .min(1)
        .max(100)
        .sliderMax(50)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("Chat Feedback")
        .description("Show chat feedback messages")
        .defaultValue(true)
        .build()
    );

    private static final int WAIT_TIME = 988;
    private static final int CYCLE_DELAY = 500;
    private static final int TIMEOUT = 2596;

    private Stage stage = Stage.IDLE;
    private long stageStart = 0;
    private String currentTarget = "";

    private int itemMoveIndex = 0;
    private long lastItemMoveTime = 0;
    private int exitCount = 0;
    private int finalExitCount = 0;
    private long finalExitStart = 0;

    private int cycleCount = 0;

    public enum Stage {
        IDLE,
        WAIT,
        ORDERS,
        ORDERS_SELECT,
        ORDERS_EXIT,
        ORDERS_CONFIRM,
        ORDERS_FINAL_EXIT,
        CYCLE_PAUSE,
        COMPLETED
    }

    public AutoOrder() {
        super(GlazedAddon.CATEGORY, "auto-order", "Automatically orders items from the server shop.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            error("Cannot activate - player is null");
            toggle();
            return;
        }

        currentTarget = targetItemName.get();

        if (chatFeedback.get()) {
            info("Target item set to: %s", currentTarget);
        }

        stage = Stage.WAIT;
        stageStart = System.currentTimeMillis();
        cycleCount = 0;
        resetMovementState();

        if (chatFeedback.get()) {
            info("AutoOrder started - Target: %s", currentTarget);
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        stage = Stage.IDLE;
        resetMovementState();
        if (chatFeedback.get()) {
            info("AutoOrder stopped");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();

        switch (stage) {
            case WAIT -> {
                if (now - stageStart >= WAIT_TIME) {
                    ChatUtils.sendPlayerMsg("/orders " + currentTarget);
                    stage = Stage.ORDERS;
                    stageStart = now;
                }
            }

            case ORDERS -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && isTargetItem(stack)) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.ORDERS_SELECT;
                            stageStart = now;
                            if (chatFeedback.get()) {
                                info("Found and selected: %s", stack.getName().getString());
                            }
                            return;
                        }
                    }
                    if (now - stageStart > TIMEOUT) {
                        mc.player.closeHandledScreen();
                        if (chatFeedback.get()) {
                            error("Timeout finding %s, restarting cycle", currentTarget);
                        }
                        stage = Stage.WAIT;
                        stageStart = now;
                    }
                }
            }

            case ORDERS_SELECT -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    if (itemMoveIndex > 35) {
                        mc.player.closeHandledScreen();
                        stage = Stage.ORDERS_CONFIRM;
                        stageStart = now;
                        itemMoveIndex = 0;
                        return;
                    }
                    if (now - lastItemMoveTime >= moveDelay.get()) {
                        ItemStack stack = mc.player.getInventory().getStack(itemMoveIndex);
                        if (!stack.isEmpty() && isTargetItem(stack)) {
                            for (Slot slot : handler.slots) {
                                if (slot.inventory != mc.player.getInventory() && slot.getStack().isEmpty()) {
                                    int invSlot = 36 + itemMoveIndex - 9;
                                    if (itemMoveIndex < 9) invSlot = itemMoveIndex;
                                    mc.interactionManager.clickSlot(handler.syncId, invSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
                                    lastItemMoveTime = now;
                                    break;
                                }
                            }
                        }
                        itemMoveIndex++;
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
                        if (!stack.isEmpty() && isConfirmButton(stack)) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.ORDERS_FINAL_EXIT;
                            stageStart = now;
                            finalExitCount = 0;
                            finalExitStart = now;
                            if (chatFeedback.get()) {
                                info("Confirmed order for: %s", currentTarget);
                            }
                            return;
                        }
                    }
                    if (now - stageStart > TIMEOUT) {
                        mc.player.closeHandledScreen();
                        if (chatFeedback.get()) {
                            error("Timeout on confirm for %s, restarting cycle", currentTarget);
                        }
                        stage = Stage.WAIT;
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
                    cycleCount++;

                    // Check if we still have the target item in inventory
                    if (!hasTargetItemInInventory()) {
                        stage = Stage.COMPLETED;
                        if (chatFeedback.get()) {
                            info("Completed all cycles - no more %s in inventory", currentTarget);
                        }
                        toggle();
                    } else {
                        stage = Stage.CYCLE_PAUSE;
                        stageStart = System.currentTimeMillis();
                        if (chatFeedback.get()) {
                            info("Cycle %d completed for: %s", cycleCount, currentTarget);
                        }
                    }
                }
            }

            case CYCLE_PAUSE -> {
                if (now - stageStart >= CYCLE_DELAY) {
                    stage = Stage.WAIT;
                    stageStart = now;
                    resetMovementState();
                    if (chatFeedback.get()) {
                        info("Starting cycle %d", cycleCount + 1);
                    }
                }
            }
        }
    }

    private boolean isTargetItem(ItemStack stack) {
        Item targetItem = snipingItem.get();
        if (targetItem != Items.AIR) {
            return stack.getItem() == targetItem;
        }

        if (currentTarget.isEmpty()) {
            return false;
        }

        String stackName = stack.getName().getString().toLowerCase();
        return stackName.contains(currentTarget.toLowerCase());
    }

    private boolean hasTargetItemInInventory() {
        Item targetItem = snipingItem.get();
        if (targetItem == Items.AIR) {
            return false;
        }

        // Check main inventory only (9-35)
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == targetItem) {
                return true;
            }
        }

        return false;
    }

    private boolean isConfirmButton(ItemStack stack) {
        return stack.getItem() == Items.LIME_STAINED_GLASS_PANE ||
            stack.getItem() == Items.GREEN_STAINED_GLASS_PANE ||
            stack.getName().getString().toLowerCase().contains("confirm");
    }

    private void resetMovementState() {
        itemMoveIndex = 0;
        lastItemMoveTime = 0;
        exitCount = 0;
        finalExitCount = 0;
        finalExitStart = 0;
    }

    @Override
    public String getInfoString() {
        if (!isActive()) return null;
        return String.format("%s (%d cycles)", currentTarget, cycleCount);
    }
}
