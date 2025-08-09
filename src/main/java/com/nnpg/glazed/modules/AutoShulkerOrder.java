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
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoShulkerOrder extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private enum Stage {NONE, SHOP, SHOP_END, SHOP_SHULKER, SHOP_CONFIRM, SHOP_CHECK_FULL, SHOP_EXIT, WAIT, ORDERS, ORDERS_SELECT, ORDERS_EXIT, ORDERS_CONFIRM, ORDERS_FINAL_EXIT, CYCLE_PAUSE}

    private Stage stage = Stage.NONE;
    private long stageStart = 0;
    private static final long WAIT_TIME_MS = 50; // Reduced from 500ms to 50ms
    private int shulkerMoveIndex = 0;
    private long lastShulkerMoveTime = 0;
    private int exitCount = 0;
    private int finalExitCount = 0;
    private long finalExitStart = 0;

    // Batch processing for faster shulker buying
    private int bulkBuyCount = 0;
    private static final int MAX_BULK_BUY = 5; // Buy multiple shulkers per click

    // Settings
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> minPrice = sgGeneral.add(new StringSetting.Builder()
        .name("min-price")
        .description("Minimum price to deliver shulkers for (supports K, M, B suffixes).")
        .defaultValue("850")
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show detailed price checking notifications.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> speedMode = sgGeneral.add(new BoolSetting.Builder()
        .name("speed-mode")
        .description("Maximum speed mode - removes most delays (may be unstable).")
        .defaultValue(true)
        .build()
    );

    public AutoShulkerOrder() {
        super(GlazedAddon.CATEGORY, "AutoShulkerOrder", "Automatically buys shulkers and sells them in orders for profit (FAST MODE)");
    }

    @Override
    public void onActivate() {
        double parsedPrice = parsePrice(minPrice.get());
        if (parsedPrice == -1.0) {
            if (notifications.get()) {
                ChatUtils.error("Invalid minimum price format!");
            }
            toggle();
            return;
        }

        stage = Stage.SHOP;
        stageStart = System.currentTimeMillis();
        shulkerMoveIndex = 0;
        lastShulkerMoveTime = 0;
        exitCount = 0;
        finalExitCount = 0;
        bulkBuyCount = 0;

        if (notifications.get()) {
            info("🚀 FAST AutoShulkerOrder activated! Minimum: %s", minPrice.get());
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
                        if (!stack.isEmpty() && isEndStone(stack)) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.SHOP_SHULKER;
                            stageStart = now;
                            bulkBuyCount = 0;
                            return;
                        }
                    }
                    if (now - stageStart > (speedMode.get() ? 1000 : 3000)) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP;
                        stageStart = now;
                    }
                }
            }
            case SHOP_SHULKER -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    boolean foundShulker = false;

                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && isShulkerBox(stack)) {
                            // NORMAL BUY - always use normal speed for buying to avoid issues
                            int clickCount = 27; // Always use normal buying speed
                            for (int i = 0; i < clickCount; i++) {
                                mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            }
                            foundShulker = true;
                            bulkBuyCount++;
                            break;
                        }
                    }

                    if (foundShulker) {
                        stage = Stage.SHOP_CONFIRM;
                        stageStart = now;
                        return;
                    }
                    if (now - stageStart > (speedMode.get() ? 300 : 1000)) {
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
                            // RAPID CONFIRM - multiple clicks
                            for (int i = 0; i < (speedMode.get() ? 10 : 5); i++) {
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
                    if (now - stageStart > (speedMode.get() ? 100 : 500)) {
                        stage = Stage.SHOP_SHULKER;
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
            }
            case SHOP_EXIT -> {
                if (mc.currentScreen == null) {
                    stage = Stage.WAIT;
                    stageStart = now;
                }
                if (now - stageStart > (speedMode.get() ? 1000 : 5000)) {
                    mc.player.closeHandledScreen();
                    stage = Stage.SHOP;
                    stageStart = now;
                }
            }
            case WAIT -> {
                long waitTime = speedMode.get() ? 25 : WAIT_TIME_MS; // Ultra fast wait
                if (now - stageStart >= waitTime) {
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
                            double orderPrice = getOrderPrice(stack);
                            double minPriceValue = parsePrice(minPrice.get());

                            if (orderPrice >= minPriceValue) {
                                if (notifications.get()) {
                                    info("✅ Found order: %s", formatPrice(orderPrice));
                                }
                                mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                                stage = Stage.ORDERS_SELECT;
                                stageStart = now;
                                shulkerMoveIndex = 0;
                                lastShulkerMoveTime = 0;
                                return;
                            }
                        }
                    }
                    if (now - stageStart > (speedMode.get() ? 2000 : 5000)) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP;
                        stageStart = now;
                    }
                }
            }
            case ORDERS_SELECT -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();

                    if (shulkerMoveIndex >= 36) {
                        mc.player.closeHandledScreen();
                        stage = Stage.ORDERS_CONFIRM;
                        stageStart = now;
                        shulkerMoveIndex = 0;
                        return;
                    }

                    // ULTRA FAST TRANSFER - minimal delay
                    long moveDelay = speedMode.get() ? 10 : 100; // 10ms vs 100ms
                    if (now - lastShulkerMoveTime >= moveDelay) {
                        // BATCH TRANSFER - move multiple items per tick
                        int batchSize = speedMode.get() ? 3 : 1;

                        for (int batch = 0; batch < batchSize && shulkerMoveIndex < 36; batch++) {
                            ItemStack stack = mc.player.getInventory().getStack(shulkerMoveIndex);
                            if (isShulkerBox(stack)) {
                                int playerSlotId = -1;
                                for (Slot slot : handler.slots) {
                                    if (slot.inventory == mc.player.getInventory() && slot.getIndex() == shulkerMoveIndex) {
                                        playerSlotId = slot.id;
                                        break;
                                    }
                                }

                                if (playerSlotId != -1) {
                                    mc.interactionManager.clickSlot(handler.syncId, playerSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                                }
                            }
                            shulkerMoveIndex++;
                        }
                        lastShulkerMoveTime = now;
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
                            // RAPID CONFIRM
                            for (int i = 0; i < (speedMode.get() ? 15 : 5); i++) {
                                mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            }
                            stage = Stage.ORDERS_FINAL_EXIT;
                            stageStart = now;
                            finalExitCount = 0;
                            finalExitStart = now;
                            return;
                        }
                    }
                    if (now - stageStart > (speedMode.get() ? 2000 : 5000)) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP;
                        stageStart = now;
                    }
                }
            }
            case ORDERS_FINAL_EXIT -> {
                long exitDelay = speedMode.get() ? 50 : 200; // Much faster exits

                if (finalExitCount == 0) {
                    if (System.currentTimeMillis() - finalExitStart >= exitDelay) {
                        mc.player.closeHandledScreen();
                        finalExitCount++;
                        finalExitStart = System.currentTimeMillis();
                    }
                } else if (finalExitCount == 1) {
                    if (System.currentTimeMillis() - finalExitStart >= exitDelay) {
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
                long cycleWait = speedMode.get() ? 25 : WAIT_TIME_MS; // Ultra fast cycling
                if (now - stageStart >= cycleWait) {
                    stage = Stage.SHOP;
                    stageStart = now;
                }
            }
            case NONE -> {
            }
        }
    }

    // Price parsing methods (unchanged for stability)
    private double getOrderPrice(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1.0;
        }

        Item.TooltipContext tooltipContext = Item.TooltipContext.create(mc.world);
        List<Text> tooltip = stack.getTooltip(tooltipContext, mc.player, TooltipType.BASIC);

        return parseTooltipPrice(tooltip);
    }

    private double parseTooltipPrice(List<Text> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            return -1.0;
        }

        Pattern[] pricePatterns = {
            Pattern.compile("\\$([\\d,]+(?:\\.[\\d]+)?)([kmb])?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)price\\s*:\\s*([\\d,]+(?:\\.[\\d]+)?)([kmb])?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)pay\\s*:\\s*([\\d,]+(?:\\.[\\d]+)?)([kmb])?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)reward\\s*:\\s*([\\d,]+(?:\\.[\\d]+)?)([kmb])?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([\\d,]+(?:\\.[\\d]+)?)([kmb])?\\s*coins?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b([\\d,]+(?:\\.[\\d]+)?)([kmb])\\b", Pattern.CASE_INSENSITIVE)
        };

        for (Text line : tooltip) {
            String text = line.getString();

            for (Pattern pattern : pricePatterns) {
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    String numberStr = matcher.group(1).replace(",", "");
                    String suffix = "";
                    if (matcher.groupCount() >= 2 && matcher.group(2) != null) {
                        suffix = matcher.group(2).toLowerCase();
                    }

                    try {
                        double basePrice = Double.parseDouble(numberStr);
                        double multiplier = 1.0;

                        switch (suffix) {
                            case "k" -> multiplier = 1_000.0;
                            case "m" -> multiplier = 1_000_000.0;
                            case "b" -> multiplier = 1_000_000_000.0;
                        }

                        return basePrice * multiplier;
                    } catch (NumberFormatException e) {
                        // Continue to next pattern
                    }
                }
            }
        }

        return -1.0;
    }

    private double parsePrice(String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) {
            return -1.0;
        }

        String cleaned = priceStr.trim().toLowerCase().replace(",", "");
        double multiplier = 1.0;

        if (cleaned.endsWith("b")) {
            multiplier = 1_000_000_000.0;
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        } else if (cleaned.endsWith("m")) {
            multiplier = 1_000_000.0;
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        } else if (cleaned.endsWith("k")) {
            multiplier = 1_000.0;
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        try {
            return Double.parseDouble(cleaned) * multiplier;
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }

    private String formatPrice(double price) {
        if (price >= 1_000_000_000) {
            return String.format("$%.1fB", price / 1_000_000_000.0);
        } else if (price >= 1_000_000) {
            return String.format("$%.1fM", price / 1_000_000.0);
        } else if (price >= 1_000) {
            return String.format("$%.1fK", price / 1_000.0);
        } else {
            return String.format("$%.0f", price);
        }
    }

    // Helper methods (unchanged)
    private boolean isEndStone(ItemStack stack) {
        return stack.getItem() == Items.END_STONE || stack.getName().getString().toLowerCase(Locale.ROOT).contains("end");
    }

    private boolean isShulkerBox(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem().getName().getString().toLowerCase(Locale.ROOT).contains("shulker box");
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
