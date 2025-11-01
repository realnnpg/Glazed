package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Locale;

public class BlazeRodDropper extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private enum Stage {NONE, SHOP, SHOP_END, SHOP_SHULKER, SHOP_CONFIRM, SHOP_CHECK_FULL, SHOP_EXIT, DROP_ITEMS}

    private Stage stage = Stage.NONE;
    private long stageStart = 0;
    private static final long WAIT_TIME_MS = 50;
    private int bulkBuyCount = 0;
    private static final int MAX_BULK_BUY = 5;

    // Settings
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show detailed notifications.")
        .defaultValue(true)
        .build()
    );

    public BlazeRodDropper() {
        super(GlazedAddon.CATEGORY, "BlazeRodDropper", "Automatically buys blaze rods from shop and drops them");
    }

    @Override
    public void onActivate() {
        stage = Stage.SHOP;
        stageStart = System.currentTimeMillis();
        bulkBuyCount = 0;

        if (notifications.get()) {
            info("🚀 BlazeRodDropper activated! Buying and dropping blaze rods...");
        }
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
                        if (!stack.isEmpty() && isNetherrack(stack)) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.SHOP_SHULKER;
                            stageStart = now;
                            bulkBuyCount = 0;
                            return;
                        }
                    }
                    if (now - stageStart > 3000) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP;
                        stageStart = now;
                    }
                }
            }
            case SHOP_SHULKER -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    boolean foundBlazeRod = false;

                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && isBlazeRod(stack)) {
                            int clickCount = 5;
                            for (int i = 0; i < clickCount; i++) {
                                mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            }
                            foundBlazeRod = true;
                            bulkBuyCount++;
                            break;
                        }
                    }

                    if (foundBlazeRod) {
                        stage = Stage.SHOP_CONFIRM;
                        stageStart = now;
                        return;
                    }
                    if (now - stageStart > 1500) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP;
                        stageStart = now;
                    }
                }
            }
            case SHOP_CONFIRM -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    boolean foundGreen = false;
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && isGreenGlass(stack)) {
                            for (int i = 0; i < 64; i++) {
                                mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            }
                            foundGreen = true;
                            break;
                        }
                    }
                    if (foundGreen) {
                        stage = Stage.SHOP_CHECK_FULL;
                        stageStart = now;
                        return;
                    }
                    if (now - stageStart > 800) {
                        stage = Stage.SHOP_SHULKER;
                        stageStart = now;
                    }
                }
            }
            case SHOP_CHECK_FULL -> {
                if (now - stageStart > 200) {
                    if (isInventoryFull() || bulkBuyCount >= MAX_BULK_BUY) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP_EXIT;
                        stageStart = now;
                    } else {
                        if (now - stageStart > 400) {
                            stage = Stage.SHOP_SHULKER;
                            stageStart = now;
                        }
                    }
                }
            }
            case SHOP_EXIT -> {
                if (mc.currentScreen == null) {
                    stage = Stage.DROP_ITEMS;
                    stageStart = now;
                }
                if (now - stageStart > 5000) {
                    mc.player.closeHandledScreen();
                    stage = Stage.SHOP;
                    stageStart = now;
                }
            }
            case DROP_ITEMS -> {
                if (now - stageStart > 200) {
                    boolean dropped = false;
                    for (int i = 0; i < 36; i++) {
                        ItemStack stack = mc.player.getInventory().getStack(i);
                        if (isBlazeRod(stack)) {
                            // Drop the entire stack by using THROW action
                            mc.interactionManager.clickSlot(0, i, 1, SlotActionType.THROW, mc.player);
                            dropped = true;
                        }
                    }
                    if (dropped && notifications.get()) {
                        info("📦 Dropped all blaze rods!");
                    }
                    stage = Stage.SHOP;
                    stageStart = now;
                }
            }
            case NONE -> {
            }
        }
    }

    private boolean isNetherrack(ItemStack stack) {
        return stack.getItem() == Items.NETHERRACK || stack.getName().getString().toLowerCase(Locale.ROOT).contains("netherrack");
    }

    private boolean isBlazeRod(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.BLAZE_ROD;
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

    public void info(String message, Object... args) {
        if (notifications.get()) {
            ChatUtils.info(String.format(message, args));
        }
    }
}
