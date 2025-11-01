package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.nio.charset.StandardCharsets;

public class AHSniper extends Module {

    public enum PriceMode {
        PER_ITEM("Per Item"),
        PER_STACK("Per Stack");

        private final String title;

        PriceMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWebhook = settings.createGroup("Discord Webhook");

    private final Setting<Item> snipingItem = sgGeneral.add(new ItemSetting.Builder()
        .name("sniping-item")
        .description("The item to snipe from auctions.")
        .defaultValue(Items.AIR)
        .build()
    );

    private final Setting<String> targetItemName = sgGeneral.add(new StringSetting.Builder()
        .name("Item Name")
        .description("Name of the item to order")
        .defaultValue("diamonds")
        .build()
    );

    private final Setting<String> maxPrice = sgGeneral.add(new StringSetting.Builder()
        .name("max-price")
        .description("Maximum price to pay (supports K, M, B suffixes).")
        .defaultValue("1k")
        .build()
    );

    private final Setting<PriceMode> priceMode = sgGeneral.add(new EnumSetting.Builder<PriceMode>()
        .name("price-mode")
        .description("Whether max price is per individual item or per full stack.")
        .defaultValue(PriceMode.PER_STACK)
        .build()
    );

    // Tick-based delays
    private final int refreshDelayTicks = 2; //changed-nnpg was 3
    private final int buyDelayTicks = 0;
    private final int confirmDelayTicks = 5; //changed-nnpg was 10
    private final int navigationDelayTicks = 0;

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show chat notifications.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoConfirm = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-confirm")
        .description("Automatically confirm purchases in the confirmation GUI.")
        .defaultValue(true)
        .build()
    );

    // Discord Webhook Settings
    private final Setting<Boolean> webhookEnabled = sgWebhook.add(new BoolSetting.Builder()
        .name("webhook-enabled")
        .description("Enable Discord webhook notifications.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL.")
        .defaultValue("")
        .visible(webhookEnabled::get)
        .build()
    );

    private final Setting<String> webhookUsername = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-username")
        .description("Username for webhook messages.")
        .defaultValue("AH Sniper Bot")
        .visible(webhookEnabled::get)
        .build()
    );

    private final Setting<String> webhookThumbnailUrl = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-thumbnail-url")
        .description("URL for the thumbnail image in webhook messages.")
        .defaultValue("")
        .visible(webhookEnabled::get)
        .build()
    );

    // Add debug setting
    private final Setting<Boolean> debugMode = sgWebhook.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Enable debug logging for webhook issues.")
        .defaultValue(false)
        .visible(webhookEnabled::get)
        .build()
    );

    private boolean waitingForConfirmation = false;
    private boolean itemPickedUp = false;
    private boolean purchaseAttempted = false;
    private String attemptedItemName = "";
    private double attemptedActualPrice = 0.0;
    private int attemptedQuantity = 0;
    private long purchaseTimestamp = 0;
    private String attemptedSellerName = "Unknown"; // Track seller name

    // Enhanced inventory tracking
    private int previousItemCount = 0;
    private int inventoryCheckTicks = 0;
    private final int MAX_INVENTORY_CHECK_TICKS = 50; // 2.5 seconds  //changed-nnpg was 100
    private final int MIN_INVENTORY_CHECK_TICKS = 10; // 0.5 second minimum before checking  //changed-nnpg was 20

    private int delayCounter = 0;
    private boolean isProcessing = false;
    private boolean hasClickedBuy = false; // Keep your existing spam prevention
    private boolean hasClickedConfirm = false; // Keep your existing spam prevention

    // New delay tracking variables
    private int confirmDelayCounter = 0;
    private boolean waitingToConfirm = false;
    private int navigationDelayCounter = 0;
    private boolean waitingToNavigate = false;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public AHSniper() {
        super(GlazedAddon.CATEGORY, "AH-Sniper", "Automatically snipes items from auction house for cheap prices.");
    }

    @Override
    public void onActivate() {
        double parsedPrice = parsePrice(maxPrice.get());
        if (parsedPrice == -1.0) {
            if (notifications.get()) {
                ChatUtils.error("Invalid price format!");
            }
            toggle();
            return;
        }

        if (snipingItem.get() == Items.AIR) {
            if (notifications.get()) {
                ChatUtils.error("Please select an item to snipe!");
            }
            toggle();
            return;
        }

        delayCounter = 0;
        confirmDelayCounter = 0;
        navigationDelayCounter = 0;
        isProcessing = false;
        waitingForConfirmation = false;
        waitingToConfirm = false;
        waitingToNavigate = false;
        itemPickedUp = false;
        purchaseAttempted = false;
        inventoryCheckTicks = 0;
        previousItemCount = countItemInInventory();
        hasClickedBuy = false;
        hasClickedConfirm = false;

        int maxStackSize = snipingItem.get().getMaxCount();
        String modeDescription = priceMode.get() == PriceMode.PER_ITEM ? "per item" : "per stack";

        if (notifications.get()) {
            info("🎯 Auction Sniper activated! Sniping %s for max %s (%s)",
                snipingItem.get().getName().getString(), maxPrice.get(), modeDescription);
            info("⏱️  Fixed Delays: Refresh=%d ticks, Buy=%d ticks, Confirm=%d ticks, Nav=%d ticks",
                refreshDelayTicks, buyDelayTicks, confirmDelayTicks, navigationDelayTicks);
        }

        // Debug webhook settings
        if (debugMode.get()) {
            info("Debug: Webhook enabled: " + webhookEnabled.get());
            info("Debug: Webhook URL set: " + (!webhookUrl.get().isEmpty()));
            // Test webhook on activation
            testWebhook();
        }
    }

    @Override
    public void onDeactivate() {
        isProcessing = false;
        waitingForConfirmation = false;
        waitingToConfirm = false;
        waitingToNavigate = false;
        itemPickedUp = false;
        purchaseAttempted = false;
        inventoryCheckTicks = 0;
        hasClickedBuy = false;
        hasClickedConfirm = false;
        delayCounter = 0;
        confirmDelayCounter = 0;
        navigationDelayCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        // Handle all delay counters first
        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        if (confirmDelayCounter > 0) {
            confirmDelayCounter--;
            return;
        }

        if (navigationDelayCounter > 0) {
            navigationDelayCounter--;
            return;
        }

        // Check inventory for successful purchases - only send webhook if item actually appears in inventory
        if (purchaseAttempted) {
            inventoryCheckTicks++;
            int currentItemCount = countItemInInventory();

            if (debugMode.get()) {
                if (inventoryCheckTicks == MIN_INVENTORY_CHECK_TICKS) {
                    info("Debug: Checking inventory - Previous: %d, Current: %d", previousItemCount, currentItemCount);
                }
            }

            // Only check after minimum time has passed to allow for lag
            if (inventoryCheckTicks >= MIN_INVENTORY_CHECK_TICKS) {
                if (currentItemCount > previousItemCount) {
                    // Item was successfully added to inventory - ONLY NOW send webhook
                    int actualQuantity = currentItemCount - previousItemCount;

                    // Only send webhook if item is actually in inventory or hotbar
                    if (isItemInInventoryOrHotbar()) {
                        if (debugMode.get()) {
                            info("Debug: Item found in inventory! Quantity: %d", actualQuantity);
                            info("Debug: Attempting to send webhook...");
                        }

                        sendSuccessWebhook(attemptedItemName, attemptedActualPrice, actualQuantity, attemptedSellerName);

                        if (notifications.get()) {
                            info("✅ Successfully sniped %dx %s!", actualQuantity, attemptedItemName);
                        }
                    } else {
                        if (notifications.get()) {
                            ChatUtils.warning("⚠️ Purchase attempted but item not found in inventory");
                        }
                    }

                    // Reset purchase tracking
                    purchaseAttempted = false;
                    inventoryCheckTicks = 0;
                    previousItemCount = currentItemCount;
                    hasClickedBuy = false; // Reset buy click flag
                    hasClickedConfirm = false; // Reset confirm click flag
                } else if (inventoryCheckTicks >= MAX_INVENTORY_CHECK_TICKS) {
                    // Timeout - purchase likely failed, no webhook sent
                    if (notifications.get()) {
                        ChatUtils.warning("⏰ Purchase timeout - item was not acquired");
                    }

                    if (debugMode.get()) {
                        info("Debug: Purchase timeout reached");
                    }

                    // Reset without sending webhook
                    purchaseAttempted = false;
                    inventoryCheckTicks = 0;
                    previousItemCount = currentItemCount;
                    hasClickedBuy = false; // Reset buy click flag
                    hasClickedConfirm = false; // Reset confirm click flag
                }
            }
        }

        ScreenHandler screenHandler = mc.player.currentScreenHandler;

        // Check if we're in a confirmation GUI - handle with delay
        if (isConfirmationGUI(screenHandler)) {
            handleConfirmationGUI((GenericContainerScreenHandler) screenHandler);
            return;
        }

        // Check if we're in an auction GUI
        if (screenHandler instanceof GenericContainerScreenHandler containerHandler) {
            if (containerHandler.getRows() == 6) {
                processSixRowAuction(containerHandler);
            } else if (containerHandler.getRows() == 3) {
                processThreeRowAuction(containerHandler);
            }
        } else {
            openAuctionHouse();
        }
    }

    // Add test webhook method
    private void testWebhook() {
        if (!webhookEnabled.get() || webhookUrl.get().isEmpty()) {
            info("Debug: Cannot test webhook - not enabled or URL empty");
            return;
        }

        String testPayload = createSimpleTestMessage();
        sendWebhookMessage(testPayload, "Test");
    }

    private String createSimpleTestMessage() {
        String playerName = mc.player != null ? mc.player.getName().getString() : "Unknown";

        return String.format("""
            {
                "content": "🧪 **Webhook Test** - AH Sniper is working for **%s**!",
                "username": "%s"
            }
            """,
            escapeJson(playerName),
            escapeJson(webhookUsername.get())
        );
    }

    private int countItemInInventory() {
        if (mc.player == null) return 0;

        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(snipingItem.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    // Check if the target item is actually in inventory or hotbar
    private boolean isItemInInventoryOrHotbar() {
        if (mc.player == null) return false;

        // Check main inventory (slots 9-35)
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(snipingItem.get()) && !stack.isEmpty()) {
                return true;
            }
        }

        // Check hotbar (slots 0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(snipingItem.get()) && !stack.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private boolean isConfirmationGUI(ScreenHandler screenHandler) {
        if (!(screenHandler instanceof GenericContainerScreenHandler containerHandler)) {
            return false;
        }

        // Check if the GUI has the confirmation title
        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            Text title = screen.getTitle();
            if (title != null) {
                String titleText = title.getString();
                // Check for the confirmation GUI title
                if (titleText.contains("ᴄᴏɴꜰɪʀᴍ ᴘᴜʀᴄʜᴀѕᴇ") ||
                    titleText.toLowerCase().contains("confirm purchase") ||
                    titleText.toLowerCase().contains("confirm") && titleText.toLowerCase().contains("purchase")) {
                    return true;
                }
            }
        }

        // Alternative check: look for confirmation-related items in the GUI
        for (int i = 0; i < containerHandler.slots.size(); i++) {
            ItemStack stack = containerHandler.getSlot(i).getStack();
            if (!stack.isEmpty()) {
                Item.TooltipContext tooltipContext = Item.TooltipContext.create(mc.world);
                List<Text> tooltip = stack.getTooltip(tooltipContext, mc.player, TooltipType.BASIC);
                for (Text line : tooltip) {
                    String text = line.getString().toLowerCase();
                    if (text.contains("confirm") && text.contains("purchase")) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void handleConfirmationGUI(GenericContainerScreenHandler handler) {
        if (!autoConfirm.get()) {
            if (notifications.get()) {
                info("Confirmation GUI detected but auto-confirm is disabled.");
            }
            return;
        }

        if (hasClickedConfirm) return; // Prevent spamming confirm click

        // If we're not already waiting to confirm, start the delay
        if (!waitingToConfirm) {
            waitingToConfirm = true;
            confirmDelayCounter = confirmDelayTicks; // Direct tick assignment

            if (notifications.get()) {
                info("⏳ Confirmation GUI detected! Waiting %d ticks before confirming...", confirmDelayTicks);
            }
            return;
        }

        // If delay is finished, confirm the purchase
        if (waitingToConfirm && confirmDelayCounter == 0) {
            mc.interactionManager.clickSlot(handler.syncId, 15, 1, SlotActionType.QUICK_MOVE, mc.player);
            waitingForConfirmation = false;
            waitingToConfirm = false;
            itemPickedUp = false;
            hasClickedConfirm = true; // Set flag after clicking

            if (notifications.get()) {
                info("✅ Purchase confirmed after delay!");
            }
        }
    }

    private void openAuctionHouse() {
        String itemName = getFormattedItemName(snipingItem.get());
        mc.getNetworkHandler().sendChatCommand("ah " + targetItemName);
        navigationDelayCounter = navigationDelayTicks; // Direct tick assignment
    }

    private void processSixRowAuction(GenericContainerScreenHandler handler) {
        ItemStack recentlyListedButton = handler.getSlot(47).getStack();
        if (!recentlyListedButton.isEmpty()) {
            Item.TooltipContext tooltipContext = Item.TooltipContext.create(mc.world);
            List<Text> tooltip = recentlyListedButton.getTooltip(tooltipContext, mc.player, TooltipType.BASIC);
            for (Text line : tooltip) {
                String text = line.getString();
                if (text.contains("Recently Listed") &&
                    (line.getStyle().toString().contains("white") || text.contains("white"))) {
                    mc.interactionManager.clickSlot(handler.syncId, 47, 1, SlotActionType.QUICK_MOVE, mc.player);
                    navigationDelayCounter = navigationDelayTicks; // Direct tick assignment
                    hasClickedBuy = false; // Reset buy click flag when navigating
                    return;
                }
            }
        }

        for (int i = 0; i < 44; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isOf(snipingItem.get())) { // Check item type first
                double currentItemPrice = getActualPrice(stack);
                if (isValidAuctionItem(stack) && currentItemPrice != -1.0) { // Only proceed if valid and price is parsed
                    if (isProcessing) {
                        if (hasClickedBuy) return; // Prevent spamming buy click

                        mc.interactionManager.clickSlot(handler.syncId, i, 1, SlotActionType.QUICK_MOVE, mc.player);
                        isProcessing = false;
                        hasClickedBuy = true; // Set flag after clicking

                        // Start tracking this purchase attempt
                        attemptedItemName = snipingItem.get().getName().getString();
                        attemptedActualPrice = currentItemPrice; // Assign the valid price
                        attemptedQuantity = stack.getCount();
                        attemptedSellerName = getSellerNameFromTooltip(stack); // Store seller name
                        purchaseAttempted = true;
                        purchaseTimestamp = System.currentTimeMillis();
                        inventoryCheckTicks = 0;

                        if (notifications.get()) {
                            info("🛒 Attempting to buy %dx %s!", stack.getCount(), attemptedItemName);
                        }

                        if (debugMode.get()) {
                            info("Debug: Purchase attempt - Item: %s, Price: %s, Seller: %s",
                                attemptedItemName, formatPrice(attemptedActualPrice), attemptedSellerName);
                        }
                        return;
                    }
                    isProcessing = true;
                    delayCounter = buyDelayTicks; // Direct tick assignment

                    if (notifications.get() && buyDelayTicks > 0) {
                        info("⏳ Found valid item! Waiting %d ticks before buying...", buyDelayTicks);
                    }
                    return;
                }
            }
        }

        mc.interactionManager.clickSlot(handler.syncId, 49, 1, SlotActionType.QUICK_MOVE, mc.player);
        navigationDelayCounter = refreshDelayTicks; // Direct tick assignment
        hasClickedBuy = false; // Reset buy click flag when navigating

        if (notifications.get() && refreshDelayTicks > 0) {
            info("🔄 Refreshing to next page in %d ticks...", refreshDelayTicks);
        }
    }

    private void processThreeRowAuction(GenericContainerScreenHandler handler) {
        ItemStack auctionItem = handler.getSlot(13).getStack();
        if (auctionItem.isOf(snipingItem.get())) { // Check item type first
            double currentItemPrice = getActualPrice(auctionItem);
            if (isValidAuctionItem(auctionItem) && currentItemPrice != -1.0) { // Only proceed if valid and price is parsed
                if (hasClickedBuy) return; // Prevent spamming buy click

                // Apply buy delay even for single item view
                if (buyDelayTicks > 0) {
                    delayCounter = buyDelayTicks; // Direct tick assignment
                    if (notifications.get()) {
                        info("⏳ Ready to buy! Waiting %d ticks...", buyDelayTicks);
                    }
                    isProcessing = true;
                    return;
                }

                mc.interactionManager.clickSlot(handler.syncId, 15, 1, SlotActionType.QUICK_MOVE, mc.player);
                hasClickedBuy = true; // Set flag after clicking

                // Start tracking this purchase attempt
                attemptedItemName = auctionItem.getItem().getName().getString();
                attemptedActualPrice = currentItemPrice; // Assign the valid price
                attemptedQuantity = auctionItem.getCount();
                attemptedSellerName = getSellerNameFromTooltip(auctionItem); // Store seller name
                purchaseAttempted = true;
                purchaseTimestamp = System.currentTimeMillis();
                inventoryCheckTicks = 0;

                if (notifications.get()) {
                    info("⚡ Buying %dx %s after delay!", auctionItem.getCount(), attemptedItemName);
                }

                if (debugMode.get()) {
                    info("Debug: Single item purchase - Item: %s, Price: %s, Seller: %s",
                        attemptedItemName, formatPrice(attemptedActualPrice), attemptedSellerName);
                }
            }
        }
    }

    private double getActualPrice(ItemStack stack) {
        Item.TooltipContext tooltipContext = Item.TooltipContext.create(mc.world);
        List<Text> tooltip = stack.getTooltip(tooltipContext, mc.player, TooltipType.BASIC);

        return parseTooltipPrice(tooltip);
    }

    private String getSellerNameFromTooltip(ItemStack stack) {
        Item.TooltipContext tooltipContext = Item.TooltipContext.create(mc.world);
        List<Text> tooltip = stack.getTooltip(tooltipContext, mc.player, TooltipType.BASIC);

        Pattern sellerPattern = Pattern.compile("(?i)Seller:\\s*(\\w+)");

        for (Text line : tooltip) {
            String text = line.getString();
            Matcher matcher = sellerPattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "Unknown";
    }

    // Send success webhook - only called when item is confirmed in inventory
    private void sendSuccessWebhook(String itemName, double actualPrice, int quantity, String sellerName) {
        if (!webhookEnabled.get() || webhookUrl.get().isEmpty()) {
            if (debugMode.get()) {
                info("Debug: Webhook not sent - Enabled: %s, URL set: %s",
                    webhookEnabled.get(), !webhookUrl.get().isEmpty());
            }
            return;
        }

        if (debugMode.get()) {
            info("Debug: Creating webhook payload...");
        }

        String jsonPayload = createSuccessEmbed(itemName, actualPrice, quantity, sellerName);
        sendWebhookMessage(jsonPayload, "Success");
    }

    // Improved webhook sending method with better error handling
    private void sendWebhookMessage(String jsonPayload, String messageType) {
        try {
            if (debugMode.get()) {
                info("Debug: Sending %s webhook request...", messageType);
                info("Debug: Payload preview: %s", jsonPayload.substring(0, Math.min(jsonPayload.length(), 200)) + "...");
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl.get()))
                .header("Content-Type", "application/json")
                .header("User-Agent", "AH-Sniper/1.0")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

            // Use synchronous request for better debugging
            if (debugMode.get()) {
                try {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    info("Debug: %s webhook sent - Status: %d", messageType, response.statusCode());
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        ChatUtils.error("%s webhook failed - Status: %d", messageType, response.statusCode());
                        info("Debug: Response body: %s", response.body());
                    }
                } catch (Exception e) {
                    ChatUtils.error("%s webhook error: %s", messageType, e.getMessage());
                    e.printStackTrace();
                }
            } else {
                // Use async for production
                CompletableFuture<HttpResponse<String>> responseFuture = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

                responseFuture.whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        if (notifications.get()) {
                            ChatUtils.error("%s webhook error: %s", messageType, throwable.getMessage());
                        }
                        System.err.println(messageType + " webhook error: " + throwable.getMessage());
                    } else {
                        System.out.println(messageType + " webhook sent - Status: " + response.statusCode());
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            if (notifications.get()) {
                                ChatUtils.error("%s webhook failed - Status: %d", messageType, response.statusCode());
                            }
                            System.err.println(messageType + " webhook failed - Response: " + response.body());
                        }
                    }
                });
            }

        } catch (Exception e) {
            if (debugMode.get() || notifications.get()) {
                ChatUtils.error("%s webhook creation error: %s", messageType, e.getMessage());
            }
            System.err.println(messageType + " webhook creation error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Create success embed with seller information
    private String createSuccessEmbed(String itemName, double actualPrice, int quantity, String sellerName) {
        String playerName = mc.player != null ? mc.player.getName().getString() : "Unknown";
        long timestamp = System.currentTimeMillis() / 1000; // Unix timestamp

        String maxPriceStr = formatPrice(parsePrice(maxPrice.get()));
        String actualPriceStr = formatPrice(actualPrice);
        String priceModeStr = priceMode.get().toString();

        // Calculate savings
        double maxPriceValue = parsePrice(maxPrice.get());
        double savings = maxPriceValue - actualPrice;
        String savingsStr = formatPrice(Math.abs(savings));
        String savingsPercentage = String.format("%.1f%%", (savings / maxPriceValue) * 100);

        String content = String.format("🎯 **%s** successfully sniped **%dx %s** for **%s**!",
            playerName, quantity, itemName, actualPriceStr);

        String thumbnailUrl = webhookThumbnailUrl.get();
        if (thumbnailUrl.isEmpty()) {
            thumbnailUrl = "https://i.scdn.co/image/ab6761610000517415fda24afae244cbd5c2dfac";
        }

        return String.format("""
            {
                "content": "%s",
                "username": "%s",
                "embeds": [
                    {
                        "title": "🎯 Auction Sniped Successfully!",
                        "color": 65280,
                        "fields": [
                            {
                                "name": "📦 Item",
                                "value": "%s x%d",
                                "inline": true
                            },
                            {
                                "name": "💰 Purchase Price",
                                "value": "%s (total)",
                                "inline": true
                            },
                            {
                                "name": "💵 Max Price",
                                "value": "%s (%s)",
                                "inline": true
                            },
                            {
                                "name": "💸 Savings",
                                "value": "%s (%s)",
                                "inline": true
                            },
                            {
                                "name": "🏪 Seller",
                                "value": "%s",
                                "inline": true
                            },
                            {
                                "name": "⏰ Time",
                                "value": "<t:%d:R>",
                                "inline": true
                            }
                        ],
                        "thumbnail": {
                            "url": "%s"
                        },
                        "footer": {
                            "text": "Auction Sniper Bot"
                        },
                        "timestamp": "%s"
                    }
                ]
            }
            """,
            escapeJson(content),
            escapeJson(webhookUsername.get()),
            escapeJson(itemName), quantity,
            escapeJson(actualPriceStr),
            escapeJson(maxPriceStr), escapeJson(priceModeStr.toLowerCase()),
            escapeJson(savingsStr), escapeJson(savingsPercentage),
            escapeJson(sellerName),
            timestamp,
            escapeJson(thumbnailUrl),
            Instant.now().toString()
        );
    }

    // Helper method to escape JSON strings
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    // Helper method to format prices nicely
    private String formatPrice(double price) {
        if (price >= 1_000_000_000) {
            return String.format("%.1fB", price / 1_000_000_000.0);
        } else if (price >= 1_000_000) {
            return String.format("%.1fM", price / 1_000_000.0);
        } else if (price >= 1_000) {
            return String.format("%.1fK", price / 1_000.0);
        } else {
            return String.format("%.0f", price);
        }
    }

    private boolean isValidAuctionItem(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(snipingItem.get())) {
            return false;
        }

        Item.TooltipContext tooltipContext = Item.TooltipContext.create(mc.world);
        List<Text> tooltip = stack.getTooltip(tooltipContext, mc.player, TooltipType.BASIC);
        double itemPrice = parseTooltipPrice(tooltip);
        double maxPriceValue = parsePrice(maxPrice.get());

        if (maxPriceValue == -1.0) {
            if (notifications.get()) {
                ChatUtils.error("Invalid max price format!");
            }
            toggle();
            return false;
        }

        if (itemPrice == -1.0) {
            // Do not send warning here, as it might be a legitimate case where price isn't found
            return false;
        }

        // Calculate comparison price based on user's preference
        double comparisonPrice;
        if (priceMode.get() == PriceMode.PER_ITEM) {
            // Compare per-item prices
            comparisonPrice = itemPrice / stack.getCount();
        } else {
            // Compare total auction price directly against max price
            comparisonPrice = itemPrice;
        }

        // Debug logging to help troubleshoot
        if (notifications.get()) {
            String mode = priceMode.get() == PriceMode.PER_ITEM ? "per item" : "per stack";
            String priceStr = formatPrice(comparisonPrice);
            String maxStr = formatPrice(maxPriceValue);
            boolean willBuy = comparisonPrice <= maxPriceValue;

            info("🔍 Item: %dx %s | Price: %s (%s) | Max: %s | Will buy: %s",
                stack.getCount(),
                stack.getItem().getName().getString(),
                priceStr,
                mode,
                maxStr,
                willBuy ? "YES" : "NO"
            );
        }

        return comparisonPrice <= maxPriceValue;
    }

    private double parseTooltipPrice(List<Text> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            return -1.0;
        }

        // Updated regex patterns to specifically handle '$10M' and similar formats
        Pattern[] pricePatterns = {
            Pattern.compile("\\$([\\d,]+(?:\\.[\\d]+)?)([kmb])?", Pattern.CASE_INSENSITIVE), // Handles $10M, $10k, $10
            Pattern.compile("(?i)price\\s*:\\s*([\\d,]+(?:\\.[\\d]+)?)([kmb])?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)buy\\s+for\\s*:\\s*([\\d,]+(?:\\.[\\d]+)?)([kmb])?", Pattern.CASE_INSENSITIVE),
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

    private String getFormattedItemName(Item item) {
        String translationKey = item.getTranslationKey();
        String[] parts = translationKey.split("\\.");
        String itemName = parts[parts.length - 1];

        String[] words = itemName.replace("_", " ").split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase())
                    .append(" ");
            }
        }

        return result.toString().trim();
    }
}
