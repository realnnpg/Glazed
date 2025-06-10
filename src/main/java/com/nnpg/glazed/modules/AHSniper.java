/**
 *
 *

package com.nnpg.glazed.modules;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AHSniper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAPI = settings.createGroup("API");
    private final SettingGroup sgDelays = settings.createGroup("Delays");
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");

    // General Settings
    private final Setting<Item> snipingItem = sgGeneral.add(new ItemSetting.Builder()
        .name("sniping-item")
        .description("The item to snipe from auctions.")
        .defaultValue(Items.AIR)
        .build()
    );

    private final Setting<String> price = sgGeneral.add(new StringSetting.Builder()
        .name("max-price")
        .description("Maximum price to pay for the item (supports K, M, B suffixes).")
        .defaultValue("1k")
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Manual is faster but API doesn't require auction GUI opened all the time.")
        .defaultValue(Mode.MANUAL)
        .build()
    );

    // API Settings
    private final Setting<String> apiKey = sgAPI.add(new StringSetting.Builder()
        .name("api-key")
        .description("API key for auction house access. Get it by typing /api in chat.")
        .defaultValue("")
        .visible(() -> mode.get() == Mode.API)
        .build()
    );

    private final Setting<Integer> apiRefreshRate = sgAPI.add(new IntSetting.Builder()
        .name("api-refresh-rate")
        .description("How often to query the API (in milliseconds).")
        .defaultValue(250)
        .min(10)
        .max(5000)
        .sliderMax(1000)
        .visible(() -> mode.get() == Mode.API)
        .build()
    );

    // Delay Settings
    private final Setting<Integer> refreshDelay = sgDelays.add(new IntSetting.Builder()
        .name("refresh-delay")
        .description("Delay between auction page refreshes (in ticks).")
        .defaultValue(2)
        .min(0)
        .max(100)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> buyDelay = sgDelays.add(new IntSetting.Builder()
        .name("buy-delay")
        .description("Delay before buying an item (in ticks).")
        .defaultValue(2)
        .min(0)
        .max(100)
        .sliderMax(20)
        .build()
    );

    // Notification Settings
    private final Setting<Boolean> showApiNotifications = sgNotifications.add(new BoolSetting.Builder()
        .name("show-api-notifications")
        .description("Show chat notifications for API actions.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showFoundItems = sgNotifications.add(new BoolSetting.Builder()
        .name("show-found-items")
        .description("Notify when items are found below price threshold.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> debugMode = sgNotifications.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Show debug information in chat.")
        .defaultValue(false)
        .build()
    );

    // Internal state
    private int delayCounter = 0;
    private boolean isProcessing = false;
    private final HttpClient httpClient;
    private final Gson gson;
    private long lastApiCallTimestamp = 0L;
    private final Map<String, Double> snipingItems = new HashMap<>();
    private boolean isApiQueryInProgress = false;
    private boolean isAuctionSniping = false;
    private int auctionPageCounter = -1;
    private String currentSellerName = "";

    public AuctionSniper() {
        super(Categories.Misc, "auction-sniper", "Automatically snipes items from auction houses for cheap prices.");
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5L))
            .build();
        this.gson = new Gson();
    }

    @Override
    public void onActivate() {
        double maxPrice = parsePrice(price.get());
        if (maxPrice == -1.0) {
            error("Invalid price format!");
            toggle();
            return;
        }

        if (snipingItem.get() != Items.AIR) {
            snipingItems.put(snipingItem.get().toString(), maxPrice);
        }

        lastApiCallTimestamp = 0L;
        isApiQueryInProgress = false;
        isAuctionSniping = false;
        currentSellerName = "";
        delayCounter = 0;
        isProcessing = false;

        info("Started sniping %s for max price %s",
            getItemDisplayName(snipingItem.get()),
            formatPrice(maxPrice));
    }

    @Override
    public void onDeactivate() {
        isAuctionSniping = false;
        snipingItems.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Handle delay counter
        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        switch (mode.get()) {
            case API -> handleApiMode();
            case MANUAL -> handleManualMode();
        }
    }

    private void handleApiMode() {
        if (isAuctionSniping) {
            ScreenHandler screenHandler = mc.player.currentScreenHandler;
            if (screenHandler instanceof GenericContainerScreenHandler containerHandler) {
                auctionPageCounter = -1;
                if (containerHandler.getRows() == 6) {
                    processSixRowAuction(containerHandler);
                } else if (containerHandler.getRows() == 3) {
                    processThreeRowAuction(containerHandler);
                }
            } else {
                if (auctionPageCounter == -1) {
                    sendCommand("ah " + currentSellerName);
                    auctionPageCounter = 0;
                } else if (auctionPageCounter > 40) {
                    isAuctionSniping = false;
                    currentSellerName = "";
                } else {
                    auctionPageCounter++;
                }
            }
        } else {
            // Close auction pages when not sniping
            if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler &&
                mc.currentScreen != null &&
                mc.currentScreen.getTitle().getString().contains("Page")) {
                mc.player.closeHandledScreen();
                delayCounter = 20;
                return;
            }

            // API querying logic
            if (!isApiQueryInProgress) {
                long currentTime = System.currentTimeMillis();
                long timeDiff = currentTime - lastApiCallTimestamp;

                if (timeDiff > apiRefreshRate.get()) {
                    lastApiCallTimestamp = currentTime;
                    if (apiKey.get().isEmpty()) {
                        if (showApiNotifications.get()) {
                            error("API key is not set. Set it using /api in-game.");
                        }
                        return;
                    }

                    isApiQueryInProgress = true;
                    queryApi().thenAccept(this::processApiResponse);
                }
            }
        }
    }

    private void handleManualMode() {
        ScreenHandler screenHandler = mc.player.currentScreenHandler;
        if (screenHandler instanceof GenericContainerScreenHandler containerHandler) {
            if (containerHandler.getRows() == 6) {
                processSixRowAuction(containerHandler);
            } else if (containerHandler.getRows() == 3) {
                processThreeRowAuction(containerHandler);
            }
        } else {
            // Open auction house for the item
            String itemName = getItemDisplayName(snipingItem.get());
            sendCommand("ah " + itemName);
            delayCounter = 20;
        }
    }

    private CompletableFuture<List<JsonObject>> queryApi() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = "https://api.donutsmp.net/v1/auction/list/1";
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiKey.get())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"sort\": \"recently_listed\"}"))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    if (showApiNotifications.get()) {
                        error("API Error: " + response.statusCode());
                    }
                    return new ArrayList<>();
                }

                JsonArray jsonArray = gson.fromJson(response.body(), JsonObject.class)
                    .getAsJsonArray("result");
                List<JsonObject> auctions = new ArrayList<>();

                for (JsonElement element : jsonArray) {
                    auctions.add(element.getAsJsonObject());
                }

                return auctions;
            } catch (Exception e) {
                if (debugMode.get()) {
                    error("API query failed: " + e.getMessage());
                }
                return new ArrayList<>();
            } finally {
                isApiQueryInProgress = false;
            }
        });
    }

    private void processApiResponse(List<JsonObject> auctions) {
        for (JsonObject auction : auctions) {
            try {
                String itemId = auction.getAsJsonObject("item").get("id").getAsString();
                long price = auction.get("price").getAsLong();
                String sellerName = auction.getAsJsonObject("seller").get("name").getAsString();

                for (Map.Entry<String, Double> entry : snipingItems.entrySet()) {
                    String targetItem = entry.getKey();
                    double maxPrice = entry.getValue();

                    if (itemId.contains(targetItem) && price <= maxPrice) {
                        if (showFoundItems.get()) {
                            info("Found %s for %s (threshold: %s) from seller: %s",
                                itemId, formatPrice(price), formatPrice(maxPrice), sellerName);
                        }

                        isAuctionSniping = true;
                        currentSellerName = sellerName;
                        return;
                    }
                }
            } catch (Exception e) {
                if (debugMode.get()) {
                    error("Error processing auction: " + e.getMessage());
                }
            }
        }
    }

    private void processSixRowAuction(GenericContainerScreenHandler containerHandler) {
        // Check refresh button
        ItemStack refreshButton = containerHandler.getSlot(47).getStack();
        if (!refreshButton.isOf(Items.AIR)) {
            List<Text> tooltip = refreshButton.getTooltip(Item.TooltipContext.DEFAULT, mc.player, TooltipType.BASIC);
            for (Text line : tooltip) {
                String text = line.getString();
                if (text.contains("Recently Listed") &&
                    (line.getStyle().getColor() != null && line.getStyle().getColor().equals(Formatting.WHITE.getColorValue()))) {
                    clickSlot(containerHandler, 47);
                    delayCounter = 5;
                    return;
                }
            }
        }

        // Check for target items
        for (int i = 0; i < 44; i++) {
            ItemStack stack = containerHandler.getSlot(i).getStack();
            if (stack.isOf(snipingItem.get()) && isValidAuctionItem(stack)) {
                if (isProcessing) {
                    clickSlot(containerHandler, i);
                    isProcessing = false;
                    if (showFoundItems.get()) {
                        info("Purchased item from slot " + i);
                    }
                    return;
                }
                isProcessing = true;
                delayCounter = buyDelay.get();
                return;
            }
        }

        // No items found, refresh or close
        if (isAuctionSniping) {
            isAuctionSniping = false;
            currentSellerName = "";
            mc.player.closeHandledScreen();
        } else {
            clickSlot(containerHandler, 49); // Next page
            delayCounter = refreshDelay.get();
        }
    }

    private void processThreeRowAuction(GenericContainerScreenHandler containerHandler) {
        ItemStack centerItem = containerHandler.getSlot(13).getStack();
        if (isValidAuctionItem(centerItem)) {
            clickSlot(containerHandler, 15); // Buy button
            delayCounter = 20;
            if (showFoundItems.get()) {
                info("Purchased item from auction view");
            }
        }

        if (isAuctionSniping) {
            isAuctionSniping = false;
            currentSellerName = "";
        }
    }

    private boolean isValidAuctionItem(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(snipingItem.get())) {
            return false;
        }

        List<Text> tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, mc.player, TooltipType.BASIC);
        double itemPrice = parseTooltipPrice(tooltip) / stack.getCount();
        double maxPrice = parsePrice(price.get());

        if (maxPrice == -1.0) {
            error("Invalid max price format");
            toggle();
            return false;
        }

        if (itemPrice == -1.0) {
            if (debugMode.get()) {
                error("Could not parse item price from tooltip");
                for (int i = 0; i < tooltip.size(); i++) {
                    System.out.println(i + ". " + tooltip.get(i).getString());
                }
            }
            return false;
        }

        return itemPrice <= maxPrice;
    }

    private double parseTooltipPrice(List<Text> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            return -1.0;
        }

        for (Text line : tooltip) {
            String text = line.getString();
            if (text.matches("(?i).*price\\s*:\\s*\\$.*")) {
                String cleanText = text.replaceAll("[,$]", "");
                Matcher matcher = Pattern.compile("([\\d]+(?:\\.[\\d]+)?)\\s*([KMB])?", Pattern.CASE_INSENSITIVE)
                    .matcher(cleanText);

                if (matcher.find()) {
                    String number = matcher.group(1);
                    String suffix = matcher.group(2) != null ? matcher.group(2).toUpperCase() : "";
                    return parsePrice(number + suffix);
                }
            }
        }
        return -1.0;
    }

    private double parsePrice(String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) {
            return -1.0;
        }

        String cleanStr = priceStr.trim().toUpperCase();
        double multiplier = 1.0;

        if (cleanStr.endsWith("B")) {
            multiplier = 1_000_000_000.0;
            cleanStr = cleanStr.substring(0, cleanStr.length() - 1);
        } else if (cleanStr.endsWith("M")) {
            multiplier = 1_000_000.0;
            cleanStr = cleanStr.substring(0, cleanStr.length() - 1);
        } else if (cleanStr.endsWith("K")) {
            multiplier = 1_000.0;
            cleanStr = cleanStr.substring(0, cleanStr.length() - 1);
        }

        try {
            return Double.parseDouble(cleanStr) * multiplier;
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }

    private String formatPrice(double price) {
        if (price >= 1_000_000_000) {
            return String.format("%.2fB", price / 1_000_000_000);
        } else if (price >= 1_000_000) {
            return String.format("%.2fM", price / 1_000_000);
        } else if (price >= 1_000) {
            return String.format("%.2fK", price / 1_000);
        } else {
            return String.format("%.2f", price);
        }
    }

    private String getItemDisplayName(Item item) {
        if (item == Items.AIR) return "Air";

        String[] parts = item.getTranslationKey().split("\\.");
        String itemName = parts[parts.length - 1];

        return Arrays.stream(itemName.replace("_", " ").split(" "))
            .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
            .collect(Collectors.joining(" "));
    }

    private void clickSlot(GenericContainerScreenHandler handler, int slot) {
        if (mc.interactionManager != null) {
            mc.interactionManager.clickSlot(handler.syncId, slot, 1, SlotActionType.QUICK_MOVE, mc.player);
        }
    }

    private void sendCommand(String command) {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendChatCommand(command);
        }
    }

    @Override
    public String getInfoString() {
        if (snipingItem.get() == Items.AIR) {
            return "No Item";
        }

        String itemName = getItemDisplayName(snipingItem.get());
        String maxPrice = formatPrice(parsePrice(price.get()));

        if (isAuctionSniping) {
            return String.format("Sniping %s - %s", itemName, currentSellerName);
        } else {
            return String.format("%s ≤ %s", itemName, maxPrice);
        }
    }

    public enum Mode {
        API("API"),
        MANUAL("Manual");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}

 **/
