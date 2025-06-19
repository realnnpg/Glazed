package com.nnpg.glazed.modules;

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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AHSniper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Item> snipingItem = sgGeneral.add(new ItemSetting.Builder()
        .name("sniping-item")
        .description("The item to snipe from auctions.")
        .defaultValue(Items.AIR)
        .build()
    );

    private final Setting<String> maxPrice = sgGeneral.add(new StringSetting.Builder()
        .name("max-price")
        .description("Maximum price to pay (supports K, M, B suffixes). Price is per max stack size.")
        .defaultValue("1k")
        .build()
    );

    private final Setting<Integer> refreshDelay = sgGeneral.add(new IntSetting.Builder()
        .name("refresh-delay")
        .description("Delay between auction page refreshes (in ticks).")
        .defaultValue(2)
        .min(0)
        .max(100)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> buyDelay = sgGeneral.add(new IntSetting.Builder()
        .name("buy-delay")
        .description("Delay before buying an item (in ticks).")
        .defaultValue(2)
        .min(0)
        .max(100)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show chat notifications.")
        .defaultValue(true)
        .build()
    );

    private int delayCounter = 0;
    private boolean isProcessing = false;

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
        isProcessing = false;

        int maxStackSize = snipingItem.get().getMaxCount();
        String stackInfo = maxStackSize == 1 ? "per item" :
            maxStackSize == 16 ? "per 16 items" :
                "per 64 items";

        if (notifications.get()) {
            ChatUtils.info("Auction Sniper activated! Sniping %s for max %s (%s)",
                snipingItem.get().getName().getString(), maxPrice.get(), stackInfo);
        }
    }

    @Override
    public void onDeactivate() {
        isProcessing = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        ScreenHandler screenHandler = mc.player.currentScreenHandler;

        // Check if we're in an auction GUI
        if (screenHandler instanceof GenericContainerScreenHandler containerHandler) {
            if (containerHandler.getRows() == 6) {
                processSixRowAuction(containerHandler);
            } else if (containerHandler.getRows() == 3) {
                processThreeRowAuction(containerHandler);
            }
        } else {
            // Not in auction GUI, open auction house
            openAuctionHouse();
        }
    }

    private void openAuctionHouse() {
        String itemName = getFormattedItemName(snipingItem.get());
        mc.getNetworkHandler().sendChatCommand("ah " + itemName);
        delayCounter = 20;
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
                    delayCounter = 5;
                    return;
                }
            }
        }

        for (int i = 0; i < 44; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isOf(snipingItem.get()) && isValidAuctionItem(stack)) {
                if (isProcessing) {
                    mc.interactionManager.clickSlot(handler.syncId, i, 1, SlotActionType.QUICK_MOVE, mc.player);
                    isProcessing = false;
                    if (notifications.get()) {
                        ChatUtils.info("Attempting to buy item!");
                    }
                    delayCounter = refreshDelay.get() + 10;
                    return;
                }
                isProcessing = true;
                delayCounter = buyDelay.get();
                return;
            }
        }

        mc.interactionManager.clickSlot(handler.syncId, 49, 1, SlotActionType.QUICK_MOVE, mc.player);
        delayCounter = refreshDelay.get();
    }

    private void processThreeRowAuction(GenericContainerScreenHandler handler) {
        ItemStack auctionItem = handler.getSlot(13).getStack();
        if (isValidAuctionItem(auctionItem)) {
            mc.interactionManager.clickSlot(handler.syncId, 15, 1, SlotActionType.QUICK_MOVE, mc.player);
            delayCounter = 20;
            if (notifications.get()) {
                Item.TooltipContext tooltipContext = Item.TooltipContext.create(mc.world);
                ChatUtils.info("Buying item for " + formatPrice(parseTooltipPrice(auctionItem.getTooltip(tooltipContext, mc.player, TooltipType.BASIC))));
            }
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
            if (notifications.get()) {
                ChatUtils.warning("Could not parse auction item price");
            }
            return false;
        }

        double effectivePrice = calculateEffectivePrice(itemPrice, stack.getCount(), snipingItem.get().getMaxCount());

        boolean isValid = effectivePrice <= maxPriceValue;

        if (notifications.get()) {
            ChatUtils.info("Item: %s, Auction Price: %s, Effective Price: %s, Max Price: %s, Valid: %s",
                stack.getItem().getName().getString(),
                formatPrice(itemPrice),
                formatPrice(effectivePrice),
                formatPrice(maxPriceValue),
                isValid ? "Yes" : "No"
            );
        }

        return isValid;
    }

    private double calculateEffectivePrice(double auctionPrice, int stackCount, int maxStackSize) {
        double pricePerItem = auctionPrice / stackCount;

        if (maxStackSize == 1) {
            return pricePerItem;
        } else if (maxStackSize == 16) {
            return pricePerItem * 16;
        } else {
            return pricePerItem * 64;
        }
    }

    private double parseTooltipPrice(List<Text> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            return -1.0;
        }

        for (Text line : tooltip) {
            String text = line.getString();
            if (text.matches("(?i).*price\\s*:\\s*\\$.*")) {
                String cleanedText = text.replaceAll("[,$]", "");

                Matcher matcher = Pattern.compile("([\\d]+(?:\\.[\\d]+)?)\\s*([KMB])?", Pattern.CASE_INSENSITIVE)
                    .matcher(cleanedText);

                if (matcher.find()) {
                    String numberStr = matcher.group(1);
                    String suffix = matcher.group(2) != null ? matcher.group(2).toUpperCase() : "";
                    return parsePrice(numberStr + suffix);
                }
            }
        }
        return -1.0;
    }

    private double parsePrice(String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) {
            return -1.0;
        }

        String cleaned = priceStr.trim().toUpperCase();
        double multiplier = 1.0;

        if (cleaned.endsWith("B")) {
            multiplier = 1_000_000_000.0;
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        } else if (cleaned.endsWith("M")) {
            multiplier = 1_000_000.0;
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        } else if (cleaned.endsWith("K")) {
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
            return String.format("%.2fB", price / 1_000_000_000);
        } else if (price >= 1_000_000) {
            return String.format("%.2fM", price / 1_000_000);
        } else if (price >= 1_000) {
            return String.format("%.2fK", price / 1_000);
        } else {
            return String.format("%.2f", price);
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

        String finalName = result.toString().trim();

        return finalName;
    }
}
