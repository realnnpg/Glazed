package com.nnpg.glazed.modules;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
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

    public enum PriceMode {
        PER_ITEM,
        FULL_STACK,
        TOTAL
    }

    private final Setting<Item> snipingItem = sgGeneral.add(new ItemSetting.Builder()
            .name("sniping-item")
            .description("The item to snipe from auctions.")
            .defaultValue(Items.AIR)
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
            .description("How to interpret the max price.")
            .defaultValue(PriceMode.FULL_STACK)
            .build()
    );

    private final Setting<Integer> refreshDelay = sgGeneral.add(new IntSetting.Builder()
            .name("refresh-speed")
            .description("Delay between auction page refreshes (in ms).")
            .defaultValue(15)
            .min(0)
            .sliderMax(1000)
            .build()
    );

    private final Setting<Integer> buyDelay = sgGeneral.add(new IntSetting.Builder()
            .name("purchase-speed")
            .description("Delay before buying an item (in ms).")
            .defaultValue(1)
            .min(0)
            .sliderMax(1000)
            .build()
    );

    private final Setting<Boolean> logPurchases = sgGeneral.add(new BoolSetting.Builder()
            .name("log-purchases")
            .description("Log successful purchases in chat.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> logDetails = sgGeneral.add(new BoolSetting.Builder()
            .name("log-details")
            .description("Log all scanned auction items and their prices.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> donutPlus = sgGeneral.add(new BoolSetting.Builder()
            .name("donut-plus")
            .description("Skips confirmation click if enabled.")
            .defaultValue(false)
            .build()
    );

    private long delayUntil = 0;
    private boolean isProcessing = false;

    public AHSniper() {
        super(GlazedAddon.CATEGORY, "AH-Sniper", "Automatically snipes items from auction house for cheap prices.");
    }

    @Override
    public void onActivate() {
        double parsedPrice = parsePrice(maxPrice.get());
        if (parsedPrice == -1.0) {
            if (logPurchases.get()) error("Invalid price format!");
            toggle();
            return;
        }

        if (snipingItem.get() == Items.AIR) {
            if (logPurchases.get()) error("Please select an item to snipe!");
            toggle();
            return;
        }

        delayUntil = 0;
        isProcessing = false;

        String modeInfo = switch (priceMode.get()) {
            case PER_ITEM -> "per item";
            case FULL_STACK -> "per " + snipingItem.get().getMaxCount() + " items";
            case TOTAL -> "total";
        };

        if (logPurchases.get()) {
            info("Auction Sniper activated! Sniping %s for max %s (%s)",
                    snipingItem.get().getName().getString(), maxPrice.get(), modeInfo);
        }
    }

    @Override
    public void onDeactivate() {
        isProcessing = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || System.currentTimeMillis() < delayUntil) return;

        ScreenHandler screenHandler = mc.player.currentScreenHandler;

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

    private void openAuctionHouse() {
        mc.getNetworkHandler().sendChatCommand("ah " + getFormattedItemName(snipingItem.get()));
        delayUntil = System.currentTimeMillis() + 500;
    }

    private void processSixRowAuction(GenericContainerScreenHandler handler) {
        ItemStack recentlyListedButton = handler.getSlot(47).getStack();
        if (!recentlyListedButton.isEmpty()) {
            Item.TooltipContext ctx = Item.TooltipContext.create(mc.world);
            for (Text line : recentlyListedButton.getTooltip(ctx, mc.player, TooltipType.BASIC)) {
                String text = line.getString();
                if (text.contains("Recently Listed") && (line.getStyle().toString().contains("white") || text.contains("white"))) {
                    mc.interactionManager.clickSlot(handler.syncId, 47, 1, SlotActionType.QUICK_MOVE, mc.player);
                    delayUntil = System.currentTimeMillis() + 100;
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
                    if (logPurchases.get()) info("Attempting to buy item!");
                    delayUntil = System.currentTimeMillis() + (refreshDelay.get() + 10);
                    return;
                }
                isProcessing = true;
                delayUntil = System.currentTimeMillis() + buyDelay.get();
                return;
            }
        }

        mc.interactionManager.clickSlot(handler.syncId, 49, 1, SlotActionType.QUICK_MOVE, mc.player);
        delayUntil = System.currentTimeMillis() + refreshDelay.get();
    }

    private void processThreeRowAuction(GenericContainerScreenHandler handler) {
        ItemStack auctionItem = handler.getSlot(13).getStack();
        if (isValidAuctionItem(auctionItem)) {
            if (!donutPlus.get()) {
                mc.interactionManager.clickSlot(handler.syncId, 15, 1, SlotActionType.QUICK_MOVE, mc.player);
            }
            delayUntil = System.currentTimeMillis() + 200;

            if (logPurchases.get()) {
                double price = parseTooltipPrice(auctionItem.getTooltip(Item.TooltipContext.create(mc.world), mc.player, TooltipType.BASIC));
                info("Bought %s ×%d for %s", auctionItem.getName().getString(), auctionItem.getCount(), formatPrice(price));
            }
        }
    }

    private boolean isValidAuctionItem(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(snipingItem.get())) return false;

        if (priceMode.get() == PriceMode.FULL_STACK && stack.getCount() < snipingItem.get().getMaxCount()) {
            if (logDetails.get()) info("Skipped - Not a full stack.");
            return false;
        }

        List<Text> tooltip = stack.getTooltip(Item.TooltipContext.create(mc.world), mc.player, TooltipType.BASIC);
        double itemPrice = parseTooltipPrice(tooltip);
        double maxPriceValue = parsePrice(maxPrice.get());

        if (itemPrice == -1.0 || maxPriceValue == -1.0) return false;

        double effectivePrice = calculateEffectivePrice(itemPrice, stack.getCount(), snipingItem.get().getMaxCount());
        boolean isValid = effectivePrice <= maxPriceValue;

        if (logDetails.get()) {
            info("Checked: %s ×%d | Auction Price: %s | Effective: %s | Max: %s | Valid: %s",
                    stack.getItem().getName().getString(),
                    stack.getCount(),
                    formatPrice(itemPrice),
                    formatPrice(effectivePrice),
                    formatPrice(maxPriceValue),
                    isValid ? "✔" : "✘"
            );
        }

        return isValid;
    }

    private double calculateEffectivePrice(double auctionPrice, int stackCount, int maxStackSize) {
        return switch (priceMode.get()) {
            case PER_ITEM -> auctionPrice / stackCount;
            case FULL_STACK -> (auctionPrice / stackCount) * maxStackSize;
            case TOTAL -> auctionPrice;
        };
    }

    private double parseTooltipPrice(List<Text> tooltip) {
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
        if (priceStr == null || priceStr.isEmpty()) return -1.0;

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
        if (price >= 1_000_000_000) return String.format("%.2fB", price / 1_000_000_000);
        else if (price >= 1_000_000) return String.format("%.2fM", price / 1_000_000);
        else if (price >= 1_000) return String.format("%.2fK", price / 1_000);
        else return String.format("%.2f", price);
    }

    private String getFormattedItemName(Item item) {
        String[] parts = item.getTranslationKey().split("\\.");
        String itemName = parts[parts.length - 1];
        StringBuilder result = new StringBuilder();
        for (String word : itemName.replace("_", " ").split(" ")) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return result.toString().trim();
    }

    @EventHandler
    private void onChatMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString();
        if (msg.contains("Your inventory is full")) {
            if (logPurchases.get()) warning("Inventory full! Disabling AH-Sniper.");
            toggle();
        }
    }
}