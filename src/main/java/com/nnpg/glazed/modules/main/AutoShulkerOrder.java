package com.nnpg.glazed.modules.main;

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

    private enum Stage {NONE, SHOP, SHOP_END, SHOP_SHULKER, SHOP_CONFIRM, SHOP_CHECK_FULL, SHOP_EXIT, WAIT, ORDERS, ORDERS_SELECT, ORDERS_EXIT, ORDERS_CONFIRM, ORDERS_FINAL_EXIT, CYCLE_PAUSE, TARGET_ORDERS}

    private Stage stage = Stage.NONE;
    private long stageStart = 0;
    private static final long WAIT_TIME_MS = 50;
    private int shulkerMoveIndex = 0;
    private long lastShulkerMoveTime = 0;
    private int exitCount = 0;
    private int finalExitCount = 0;
    private long finalExitStart = 0;
    private int bulkBuyCount = 0;
    private static final int MAX_BULK_BUY = 5;

    // Player targeting variables
    private String targetPlayer = "";
    private boolean isTargetingActive = false;

    // Settings
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Player Targeting");

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

    // New targeting settings
    private final Setting<Boolean> enableTargeting = sgTargeting.add(new BoolSetting.Builder()
        .name("enable-targeting")
        .description("Enable targeting a specific player (ignores minimum price).")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> targetPlayerName = sgTargeting.add(new StringSetting.Builder()
        .name("target-player")
        .description("Specific player name to target for orders.")
        .defaultValue("")
        .visible(() -> enableTargeting.get())
        .build()
    );

    private final Setting<Boolean> targetOnlyMode = sgTargeting.add(new BoolSetting.Builder()
        .name("target-only-mode")
        .description("Only look for orders from the targeted player, ignore all others.")
        .defaultValue(false)
        .visible(() -> enableTargeting.get())
        .build()
    );

    public AutoShulkerOrder() {
        super(GlazedAddon.CATEGORY, "AutoShulkerOrder", "Automatically buys shulkers and sells them in orders for profit with player targeting");
    }

    @Override
    public void onActivate() {
        double parsedPrice = parsePrice(minPrice.get());
        if (parsedPrice == -1.0 && !enableTargeting.get()) {
            if (notifications.get()) {
                ChatUtils.error("Invalid minimum price format!");
            }
            toggle();
            return;
        }

        // Setup target player
        updateTargetPlayer();

        stage = Stage.SHOP; // Always start with shop to buy shulkers first
        stageStart = System.currentTimeMillis();
        shulkerMoveIndex = 0;
        lastShulkerMoveTime = 0;
        exitCount = 0;
        finalExitCount = 0;
        bulkBuyCount = 0;

        if (notifications.get()) {
            String modeInfo = isTargetingActive ?
                String.format(" | Targeting: %s", targetPlayer) : "";
            info("🚀 FAST AutoShulkerOrder activated! Minimum: %s%s", minPrice.get(), modeInfo);
        }
    }

    @Override
    public void onDeactivate() {
        stage = Stage.NONE;
    }

    private void updateTargetPlayer() {
        targetPlayer = "";
        isTargetingActive = false;

        if (enableTargeting.get() && !targetPlayerName.get().trim().isEmpty()) {
            targetPlayer = targetPlayerName.get().trim();
            isTargetingActive = true;

            if (notifications.get()) {
                info("🎯 Targeting enabled for player: %s", targetPlayer);
            }
        } else {
            if (notifications.get() && enableTargeting.get()) {
                info("⚠️ Targeting disabled - no player name provided");
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        long now = System.currentTimeMillis();

        switch (stage) {
            case TARGET_ORDERS -> {
                ChatUtils.sendPlayerMsg("/orders " + targetPlayer);
                stage = Stage.ORDERS;
                stageStart = now;

                if (notifications.get()) {
                    info("🔍 Checking orders for: %s", targetPlayer);
                }
            }
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
                            // CONTROLLED BUYING - slower to prevent overshooting
                            int clickCount = speedMode.get() ? 10 : 5; // Reduced from 64/27 to 10/5
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
                    if (now - stageStart > (speedMode.get() ? 500 : 1500)) { // Slightly longer wait
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
                            // CONTROLLED CONFIRM - fewer clicks to prevent over-confirming
                            for (int i = 0; i < (speedMode.get() ? 3 : 2); i++) { // Reduced from 10/5 to 3/2
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
                    if (now - stageStart > (speedMode.get() ? 200 : 800)) { // Slightly longer wait
                        stage = Stage.SHOP_SHULKER;
                        stageStart = now;
                    }
                }
            }
            case SHOP_CHECK_FULL -> {
                // Add a small delay before checking inventory to let transactions process
                if (now - stageStart > (speedMode.get() ? 100 : 200)) {
                    if (isInventoryFull()) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP_EXIT;
                        stageStart = now;
                    } else {
                        // Small pause before buying more to prevent rapid-fire purchases
                        if (now - stageStart > (speedMode.get() ? 200 : 400)) {
                            stage = Stage.SHOP_SHULKER;
                            stageStart = now;
                        }
                    }
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
                long waitTime = speedMode.get() ? 25 : WAIT_TIME_MS;
                if (now - stageStart >= waitTime) {
                    // Only use target orders if targeting is enabled AND we have a valid target
                    if (isTargetingActive && !targetPlayer.isEmpty()) {
                        stage = Stage.TARGET_ORDERS;
                    } else {
                        ChatUtils.sendPlayerMsg("/orders shulker");
                        stage = Stage.ORDERS;
                    }
                    stageStart = now;
                }
            }
            case ORDERS -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    boolean foundOrder = false;

                    // Add delay in speed mode to ensure GUI is fully loaded
                    if (speedMode.get() && now - stageStart < 200) {
                        return; // Wait 200ms for GUI to stabilize in speed mode
                    }

                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && isShulkerBox(stack) && isPurple(stack)) {
                            boolean shouldTakeOrder = false;
                            String orderPlayer = getOrderPlayerName(stack);

                            // Check if this is a targeted order
                            boolean isTargetedOrder = isTargetingActive &&
                                orderPlayer != null &&
                                orderPlayer.equalsIgnoreCase(targetPlayer);

                            if (isTargetedOrder) {
                                shouldTakeOrder = true;
                                if (notifications.get()) {
                                    double orderPrice = getOrderPrice(stack);
                                    info("🎯 Found TARGET order from %s: %s", orderPlayer,
                                        orderPrice > 0 ? formatPrice(orderPrice) : "Unknown price");
                                }
                            } else if (!targetOnlyMode.get()) {
                                // Regular price check for non-targeted orders
                                double orderPrice = getOrderPrice(stack);
                                double minPriceValue = parsePrice(minPrice.get());

                                if (orderPrice >= minPriceValue) {
                                    shouldTakeOrder = true;
                                    if (notifications.get()) {
                                        info("✅ Found order: %s", formatPrice(orderPrice));
                                    }
                                }
                            }

                            if (shouldTakeOrder) {
                                // Click on the order to select it
                                mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);

                                // Wait a moment before moving to selection phase
                                stage = Stage.ORDERS_SELECT;
                                stageStart = now + (speedMode.get() ? 100 : 50); // Small delay to let selection register
                                shulkerMoveIndex = 0;
                                lastShulkerMoveTime = 0;
                                foundOrder = true;

                                if (notifications.get()) {
                                    info("🔄 Selected order, preparing to transfer items...");
                                }
                                return;
                            }
                        }
                    }

                    if (!foundOrder && now - stageStart > (speedMode.get() ? 3000 : 5000)) { // Longer wait in speed mode
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP; // Always go back to shop after checking orders
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

                    long moveDelay = speedMode.get() ? 10 : 100;
                    if (now - lastShulkerMoveTime >= moveDelay) {
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
                            for (int i = 0; i < (speedMode.get() ? 15 : 5); i++) {
                                mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            }
                            stage = Stage.ORDERS_FINAL_EXIT;
                            stageStart = now;
                            finalExitCount = 0;
                            finalExitStart = now;

                            if (notifications.get()) {
                                info("✅ Order completed! Going back to shop to buy more shulkers...");
                            }
                            return;
                        }
                    }
                    if (now - stageStart > (speedMode.get() ? 2000 : 5000)) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP; // Go directly to shop if confirmation fails
                        stageStart = now;
                    }
                }
            }
            case ORDERS_FINAL_EXIT -> {
                long exitDelay = speedMode.get() ? 50 : 200;

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
                long cycleWait = speedMode.get() ? 10 : 25; // Very fast cycle restart
                if (now - stageStart >= cycleWait) {
                    // Always go back to shop to buy more shulkers
                    updateTargetPlayer(); // Refresh target player
                    stage = Stage.SHOP;
                    stageStart = now;
                }
            }
            case NONE -> {
            }
        }
    }

    // New method to extract player name from order tooltip
    private String getOrderPlayerName(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        Item.TooltipContext tooltipContext = Item.TooltipContext.create(mc.world);
        List<Text> tooltip = stack.getTooltip(tooltipContext, mc.player, TooltipType.BASIC);

        for (Text line : tooltip) {
            String text = line.getString();

            // Look for patterns like "Player: PlayerName" or "From: PlayerName" or "By: PlayerName"
            Pattern[] namePatterns = {
                Pattern.compile("(?i)player\\s*:\\s*([a-zA-Z0-9_]+)"),
                Pattern.compile("(?i)from\\s*:\\s*([a-zA-Z0-9_]+)"),
                Pattern.compile("(?i)by\\s*:\\s*([a-zA-Z0-9_]+)"),
                Pattern.compile("(?i)seller\\s*:\\s*([a-zA-Z0-9_]+)"),
                Pattern.compile("(?i)owner\\s*:\\s*([a-zA-Z0-9_]+)"),
                // Generic pattern for username-like strings
                Pattern.compile("\\b([a-zA-Z0-9_]{3,16})\\b")
            };

            for (Pattern pattern : namePatterns) {
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    String playerName = matcher.group(1);
                    // Basic validation for Minecraft usernames
                    if (playerName.length() >= 3 && playerName.length() <= 16 &&
                        playerName.matches("[a-zA-Z0-9_]+")) {
                        return playerName;
                    }
                }
            }
        }

        return null;
    }

    // Price parsing methods (unchanged)
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

    // Utility method to add info messages
    public void info(String message, Object... args) {
        if (notifications.get()) {
            ChatUtils.info(String.format(message, args));
        }
    }
}
