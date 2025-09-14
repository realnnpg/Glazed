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
import java.util.ArrayList;
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

    public enum SnipeMode {
        SINGLE("Single"),
        MULTI("Multi-Snipe");

        private final String title;

        SnipeMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

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

    public static class SnipeItemConfig {
        public Item item;
        public String maxPrice;
        public PriceMode priceMode;
        public List<String> enchantments;
        public boolean exactEnchantments;

        public SnipeItemConfig(Item item, String maxPrice, PriceMode priceMode) {
            this.item = item;
            this.maxPrice = maxPrice;
            this.priceMode = priceMode;
            this.enchantments = new ArrayList<>();
            this.exactEnchantments = false;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMultiSnipe = settings.createGroup("Multi-Snipe Items");
    private final SettingGroup sgEnchantments = settings.createGroup("Enchantments");
    private final SettingGroup sgWebhook = settings.createGroup("Discord Webhook");

    private final Setting<SnipeMode> snipeMode = sgGeneral.add(new EnumSetting.Builder<SnipeMode>()
        .name("snipe-mode")
        .description("Choose between single item sniping or multi-item sniping.")
        .defaultValue(SnipeMode.SINGLE)
        .build()
    );

    private final Setting<Item> snipingItem = sgGeneral.add(new ItemSetting.Builder()
        .name("sniping-item")
        .description("The item to snipe from auctions (Single mode).")
        .defaultValue(Items.AIR)
        .visible(() -> snipeMode.get() == SnipeMode.SINGLE)
        .build()
    );

    private final Setting<String> maxPrice = sgGeneral.add(new StringSetting.Builder()
        .name("max-price")
        .description("Maximum price to pay (Single mode).")
        .defaultValue("1k")
        .visible(() -> snipeMode.get() == SnipeMode.SINGLE)
        .build()
    );

    private final Setting<PriceMode> priceMode = sgGeneral.add(new EnumSetting.Builder<PriceMode>()
        .name("price-mode")
        .description("Whether max price is per individual item or per full stack (Single mode).")
        .defaultValue(PriceMode.PER_STACK)
        .visible(() -> snipeMode.get() == SnipeMode.SINGLE)
        .build()
    );

    private final Setting<Item> multiItem1 = sgMultiSnipe.add(new ItemSetting.Builder()
        .name("item-1")
        .description("First item to snipe.")
        .defaultValue(Items.AIR)
        .visible(() -> snipeMode.get() == SnipeMode.MULTI)
        .build()
    );

    private final Setting<String> multiPrice1 = sgMultiSnipe.add(new StringSetting.Builder()
        .name("max-price-1")
        .description("Max price for item 1.")
        .defaultValue("1k")
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem1.get() != Items.AIR)
        .build()
    );

    private final Setting<PriceMode> multiPriceMode1 = sgMultiSnipe.add(new EnumSetting.Builder<PriceMode>()
        .name("price-mode-1")
        .description("Price mode for item 1.")
        .defaultValue(PriceMode.PER_STACK)
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem1.get() != Items.AIR)
        .build()
    );

    private final Setting<List<String>> multiEnchantments1 = sgMultiSnipe.add(new StringListSetting.Builder()
        .name("enchantments-1")
        .description("Required enchantments for item 1 (e.g., 'sharpness 5').")
        .defaultValue(new ArrayList<>())
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem1.get() != Items.AIR)
        .build()
    );

    private final Setting<Boolean> multiExactEnchantments1 = sgMultiSnipe.add(new BoolSetting.Builder()
        .name("exact-enchantments-1")
        .description("Require exact enchantments for item 1.")
        .defaultValue(false)
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem1.get() != Items.AIR && !multiEnchantments1.get().isEmpty())
        .build()
    );

    private final Setting<Item> multiItem2 = sgMultiSnipe.add(new ItemSetting.Builder()
        .name("item-2")
        .description("Second item to snipe.")
        .defaultValue(Items.AIR)
        .visible(() -> snipeMode.get() == SnipeMode.MULTI)
        .build()
    );

    private final Setting<String> multiPrice2 = sgMultiSnipe.add(new StringSetting.Builder()
        .name("max-price-2")
        .description("Max price for item 2.")
        .defaultValue("1k")
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem2.get() != Items.AIR)
        .build()
    );

    private final Setting<PriceMode> multiPriceMode2 = sgMultiSnipe.add(new EnumSetting.Builder<PriceMode>()
        .name("price-mode-2")
        .description("Price mode for item 2.")
        .defaultValue(PriceMode.PER_STACK)
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem2.get() != Items.AIR)
        .build()
    );

    private final Setting<List<String>> multiEnchantments2 = sgMultiSnipe.add(new StringListSetting.Builder()
        .name("enchantments-2")
        .description("Required enchantments for item 2 (e.g., 'sharpness 5').")
        .defaultValue(new ArrayList<>())
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem2.get() != Items.AIR)
        .build()
    );

    private final Setting<Boolean> multiExactEnchantments2 = sgMultiSnipe.add(new BoolSetting.Builder()
        .name("exact-enchantments-2")
        .description("Require exact enchantments for item 2.")
        .defaultValue(false)
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem2.get() != Items.AIR && !multiEnchantments2.get().isEmpty())
        .build()
    );

    private final Setting<Item> multiItem3 = sgMultiSnipe.add(new ItemSetting.Builder()
        .name("item-3")
        .description("Third item to snipe.")
        .defaultValue(Items.AIR)
        .visible(() -> snipeMode.get() == SnipeMode.MULTI)
        .build()
    );

    private final Setting<String> multiPrice3 = sgMultiSnipe.add(new StringSetting.Builder()
        .name("max-price-3")
        .description("Max price for item 3.")
        .defaultValue("1k")
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem3.get() != Items.AIR)
        .build()
    );

    private final Setting<PriceMode> multiPriceMode3 = sgMultiSnipe.add(new EnumSetting.Builder<PriceMode>()
        .name("price-mode-3")
        .description("Price mode for item 3.")
        .defaultValue(PriceMode.PER_STACK)
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem3.get() != Items.AIR)
        .build()
    );

    private final Setting<List<String>> multiEnchantments3 = sgMultiSnipe.add(new StringListSetting.Builder()
        .name("enchantments-3")
        .description("Required enchantments for item 3 (e.g., 'sharpness 5').")
        .defaultValue(new ArrayList<>())
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem3.get() != Items.AIR)
        .build()
    );

    private final Setting<Boolean> multiExactEnchantments3 = sgMultiSnipe.add(new BoolSetting.Builder()
        .name("exact-enchantments-3")
        .description("Require exact enchantments for item 3.")
        .defaultValue(false)
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem3.get() != Items.AIR && !multiEnchantments3.get().isEmpty())
        .build()
    );

    private final Setting<Item> multiItem4 = sgMultiSnipe.add(new ItemSetting.Builder()
        .name("item-4")
        .description("Fourth item to snipe.")
        .defaultValue(Items.AIR)
        .visible(() -> snipeMode.get() == SnipeMode.MULTI)
        .build()
    );

    private final Setting<String> multiPrice4 = sgMultiSnipe.add(new StringSetting.Builder()
        .name("max-price-4")
        .description("Max price for item 4.")
        .defaultValue("1k")
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem4.get() != Items.AIR)
        .build()
    );

    private final Setting<PriceMode> multiPriceMode4 = sgMultiSnipe.add(new EnumSetting.Builder<PriceMode>()
        .name("price-mode-4")
        .description("Price mode for item 4.")
        .defaultValue(PriceMode.PER_STACK)
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem4.get() != Items.AIR)
        .build()
    );

    private final Setting<List<String>> multiEnchantments4 = sgMultiSnipe.add(new StringListSetting.Builder()
        .name("enchantments-4")
        .description("Required enchantments for item 4 (e.g., 'sharpness 5').")
        .defaultValue(new ArrayList<>())
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem4.get() != Items.AIR)
        .build()
    );

    private final Setting<Boolean> multiExactEnchantments4 = sgMultiSnipe.add(new BoolSetting.Builder()
        .name("exact-enchantments-4")
        .description("Require exact enchantments for item 4.")
        .defaultValue(false)
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem4.get() != Items.AIR && !multiEnchantments4.get().isEmpty())
        .build()
    );

    private final Setting<Item> multiItem5 = sgMultiSnipe.add(new ItemSetting.Builder()
        .name("item-5")
        .description("Fifth item to snipe.")
        .defaultValue(Items.AIR)
        .visible(() -> snipeMode.get() == SnipeMode.MULTI)
        .build()
    );

    private final Setting<String> multiPrice5 = sgMultiSnipe.add(new StringSetting.Builder()
        .name("max-price-5")
        .description("Max price for item 5.")
        .defaultValue("1k")
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem5.get() != Items.AIR)
        .build()
    );

    private final Setting<PriceMode> multiPriceMode5 = sgMultiSnipe.add(new EnumSetting.Builder<PriceMode>()
        .name("price-mode-5")
        .description("Price mode for item 5.")
        .defaultValue(PriceMode.PER_STACK)
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem5.get() != Items.AIR)
        .build()
    );

    private final Setting<List<String>> multiEnchantments5 = sgMultiSnipe.add(new StringListSetting.Builder()
        .name("enchantments-5")
        .description("Required enchantments for item 5 (e.g., 'sharpness 5').")
        .defaultValue(new ArrayList<>())
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem5.get() != Items.AIR)
        .build()
    );

    private final Setting<Boolean> multiExactEnchantments5 = sgMultiSnipe.add(new BoolSetting.Builder()
        .name("exact-enchantments-5")
        .description("Require exact enchantments for item 5.")
        .defaultValue(false)
        .visible(() -> snipeMode.get() == SnipeMode.MULTI && multiItem5.get() != Items.AIR && !multiEnchantments5.get().isEmpty())
        .build()
    );

    private final int refreshDelayTicks = 1;
    private final int buyDelayTicks = 0;
    private final int confirmDelayTicks = 1;
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

    private final Setting<Boolean> enchantmentMode = sgEnchantments.add(new BoolSetting.Builder()
        .name("enchantment-mode")
        .description("Enable enchantment filtering for sniping specific enchanted items.")
        .defaultValue(false)
        .visible(() -> snipeMode.get() == SnipeMode.SINGLE)
        .build()
    );

    private final Setting<List<String>> requiredEnchantments = sgEnchantments.add(new StringListSetting.Builder()
        .name("required-enchantments")
        .description("List of required enchantments with levels (e.g., 'sharpness 5', 'protection 4').")
        .defaultValue(new ArrayList<>())
        .visible(() -> snipeMode.get() == SnipeMode.SINGLE && enchantmentMode.get())
        .build()
    );

    private final Setting<Boolean> exactEnchantments = sgEnchantments.add(new BoolSetting.Builder()
        .name("exact-enchantments")
        .description("If true, item must have EXACTLY the enchantments listed (no more, no less).")
        .defaultValue(false)
        .visible(() -> snipeMode.get() == SnipeMode.SINGLE && enchantmentMode.get())
        .build()
    );

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
    private String attemptedEnchantments = "";
    private boolean commandSent = false;
    private boolean hasSetSort = false;

    private int previousItemCount = 0;
    private int inventoryCheckTicks = 0;
    private final int MAX_INVENTORY_CHECK_TICKS = 50;
    private final int MIN_INVENTORY_CHECK_TICKS = 10;

    private int delayCounter = 0;
    private boolean isProcessing = false;
    private boolean hasClickedBuy = false;
    private boolean hasClickedConfirm = false;

    private int confirmDelayCounter = 0;
    private boolean waitingToConfirm = false;
    private int navigationDelayCounter = 0;
    private boolean waitingToNavigate = false;

    private List<SnipeItemConfig> multiSnipeConfigs = new ArrayList<>();
    private Item currentSnipedItem = null;

    private int lastClickedSlot = -1;
    private boolean pageJustRefreshed = false;

    private int purchaseTimeoutTicks = 0;
    private final int MAX_PURCHASE_TIMEOUT_TICKS = 100;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public AHSniper() {
        super(GlazedAddon.CATEGORY, "AH-Sniper", "Automatically snipes items from auction house for cheap prices.");
    }

    @Override
    public void onActivate() {
        if (snipeMode.get() == SnipeMode.MULTI) {
            multiSnipeConfigs.clear();

            if (multiItem1.get() != Items.AIR) {
                SnipeItemConfig config = new SnipeItemConfig(multiItem1.get(), multiPrice1.get(), multiPriceMode1.get());
                config.enchantments = new ArrayList<>(multiEnchantments1.get());
                config.exactEnchantments = multiExactEnchantments1.get();
                multiSnipeConfigs.add(config);
            }
            if (multiItem2.get() != Items.AIR) {
                SnipeItemConfig config = new SnipeItemConfig(multiItem2.get(), multiPrice2.get(), multiPriceMode2.get());
                config.enchantments = new ArrayList<>(multiEnchantments2.get());
                config.exactEnchantments = multiExactEnchantments2.get();
                multiSnipeConfigs.add(config);
            }
            if (multiItem3.get() != Items.AIR) {
                SnipeItemConfig config = new SnipeItemConfig(multiItem3.get(), multiPrice3.get(), multiPriceMode3.get());
                config.enchantments = new ArrayList<>(multiEnchantments3.get());
                config.exactEnchantments = multiExactEnchantments3.get();
                multiSnipeConfigs.add(config);
            }
            if (multiItem4.get() != Items.AIR) {
                SnipeItemConfig config = new SnipeItemConfig(multiItem4.get(), multiPrice4.get(), multiPriceMode4.get());
                config.enchantments = new ArrayList<>(multiEnchantments4.get());
                config.exactEnchantments = multiExactEnchantments4.get();
                multiSnipeConfigs.add(config);
            }
            if (multiItem5.get() != Items.AIR) {
                SnipeItemConfig config = new SnipeItemConfig(multiItem5.get(), multiPrice5.get(), multiPriceMode5.get());
                config.enchantments = new ArrayList<>(multiEnchantments5.get());
                config.exactEnchantments = multiExactEnchantments5.get();
                multiSnipeConfigs.add(config);
            }

            if (multiSnipeConfigs.isEmpty()) {
                if (notifications.get()) {
                    ChatUtils.error("No items configured for multi-snipe!");
                }
                toggle();
                return;
            }

            if (notifications.get()) {
                info("Multi-Snipe activated! Monitoring %d items", multiSnipeConfigs.size());
                for (SnipeItemConfig config : multiSnipeConfigs) {
                    info("  • %s - Max: %s (%s)",
                        config.item.getName().getString(),
                        config.maxPrice,
                        config.priceMode.toString());
                }
            }
        } else {
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

            if (notifications.get()) {
                info("Single-Snipe activated! Sniping %s for max %s (%s)",
                    snipingItem.get().getName().getString(), maxPrice.get(), priceMode.get().toString());
            }
        }

        resetState();

        if (debugMode.get()) {
            info("Debug: Webhook enabled: " + webhookEnabled.get());
            info("Debug: Webhook URL set: " + (!webhookUrl.get().isEmpty()));
            testWebhook();
        }
    }

    @Override
    public void onDeactivate() {
        resetState();
        multiSnipeConfigs.clear();
    }

    private void resetState() {
        delayCounter = 0;
        confirmDelayCounter = 0;
        navigationDelayCounter = 0;
        purchaseTimeoutTicks = 0;

        isProcessing = false;
        waitingForConfirmation = false;
        waitingToConfirm = false;
        waitingToNavigate = false;
        itemPickedUp = false;
        purchaseAttempted = false;
        hasClickedBuy = false;
        hasClickedConfirm = false;
        commandSent = false;
        hasSetSort = false;
        pageJustRefreshed = false;

        inventoryCheckTicks = 0;
        previousItemCount = countItemInInventory();
        attemptedEnchantments = "";
        currentSnipedItem = null;
        lastClickedSlot = -1;

        if (debugMode.get()) {
            info("Debug: State reset completed");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (purchaseAttempted) {
            purchaseTimeoutTicks++;
            if (purchaseTimeoutTicks >= MAX_PURCHASE_TIMEOUT_TICKS) {
                if (debugMode.get()) {
                    info("Debug: Purchase timeout reached, resetting state");
                }
                purchaseAttempted = false;
                purchaseTimeoutTicks = 0;
                inventoryCheckTicks = 0;
                hasClickedBuy = false;
                hasClickedConfirm = false;
                waitingForConfirmation = false;
                waitingToConfirm = false;

                if (notifications.get()) {
                    info("Purchase timed out, continuing to snipe...");
                }
            }
        }

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
            if (navigationDelayCounter == 0) {
                pageJustRefreshed = false;
            }
            return;
        }

        if (purchaseAttempted) {
            handlePurchaseCheck();
        }

        ScreenHandler screenHandler = mc.player.currentScreenHandler;

        if (isConfirmationGUI(screenHandler)) {
            handleConfirmationGUI((GenericContainerScreenHandler) screenHandler);
            return;
        }

        if (screenHandler instanceof GenericContainerScreenHandler containerHandler) {
            commandSent = true;

            if (snipeMode.get() == SnipeMode.MULTI) {
                if (containerHandler.getRows() == 6) {
                    processMultiSnipeAuction(containerHandler);
                }
            } else {
                if (containerHandler.getRows() == 6) {
                    processSixRowAuction(containerHandler);
                } else if (containerHandler.getRows() == 3) {
                    processThreeRowAuction(containerHandler);
                }
            }
        } else {
            if (commandSent && !isProcessing && !purchaseAttempted) {
                if (debugMode.get()) {
                    info("Debug: Not in auction house, resetting command state");
                }
                commandSent = false;
                hasSetSort = false;
            }

            if (!commandSent) {
                openAuctionHouse();
                commandSent = true;
            }
        }
    }

    private void processMultiSnipeAuction(GenericContainerScreenHandler handler) {
        if (!hasSetSort) {
            ItemStack sortItem = handler.getSlot(47).getStack();
            if (!sortItem.isEmpty()) {
                Item.TooltipContext tooltipContext = Item.TooltipContext.create(mc.world);
                List<Text> tooltip = sortItem.getTooltip(tooltipContext, mc.player, TooltipType.BASIC);

                boolean isLastListed = false;
                for (Text line : tooltip) {
                    String text = line.getString().toLowerCase();
                    if (text.contains("last listed") || text.contains("recently listed")) {
                        isLastListed = true;
                        break;
                    }
                }

                if (!isLastListed) {
                    mc.interactionManager.clickSlot(handler.syncId, 47, 0, SlotActionType.PICKUP, mc.player);

                    delayCounter = 10;
                    if (notifications.get()) {
                        info("Setting sort to Last Listed...");
                    }
                    return;
                } else {
                    hasSetSort = true;
                    if (notifications.get()) {
                        info("Sort is set to Last Listed");
                    }
                }
            }
        }

        if (pageJustRefreshed) {
            return;
        }

        if (purchaseAttempted || waitingForConfirmation || hasClickedBuy) {
            return;
        }

        for (int i = 0; i < 44; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;

            for (SnipeItemConfig config : multiSnipeConfigs) {
                if (stack.isOf(config.item)) {
                    double currentItemPrice = getActualPrice(stack);

                    if (isValidMultiSnipeItem(stack, config) && currentItemPrice != -1.0) {
                        if (config.priceMode == PriceMode.PER_STACK && stack.getCount() < getExpectedStackSize(stack.getItem())) {
                            if (notifications.get()) {
                                info("Skipping %s - not a full stack (%d/%d)",
                                    config.item.getName().getString(),
                                    stack.getCount(),
                                    getExpectedStackSize(stack.getItem()));
                            }
                            continue;
                        }

                        if (isProcessing) {
                            currentSnipedItem = config.item;
                            attemptedItemName = stack.getItem().getName().getString();
                            attemptedActualPrice = currentItemPrice;
                            attemptedQuantity = stack.getCount();
                            attemptedEnchantments = getEnchantmentsString(stack);

                            if (debugMode.get()) {
                                info("Debug: Captured data - Item: %s, Quantity: %d, Price: %s",
                                    attemptedItemName, attemptedQuantity, formatPrice(attemptedActualPrice));
                            }

                            mc.interactionManager.clickSlot(handler.syncId, i, 1, SlotActionType.QUICK_MOVE, mc.player);

                            isProcessing = false;
                            hasClickedBuy = true;
                            lastClickedSlot = i;

                            purchaseAttempted = true;
                            purchaseTimestamp = System.currentTimeMillis();
                            inventoryCheckTicks = 0;
                            purchaseTimeoutTicks = 0;

                            if (notifications.get()) {
                                info("Attempting to buy %dx %s!", attemptedQuantity, attemptedItemName);
                            }
                            return;
                        }

                        isProcessing = true;
                        delayCounter = buyDelayTicks;

                        if (notifications.get() && buyDelayTicks > 0) {
                            info("Found valid %s! Waiting %d ticks before buying...",
                                config.item.getName().getString(), buyDelayTicks);
                        }
                        return;
                    }
                }
            }
        }

        if (!isProcessing && !pageJustRefreshed && !purchaseAttempted && !hasClickedBuy) {
            mc.interactionManager.clickSlot(handler.syncId, 49, 1, SlotActionType.QUICK_MOVE, mc.player);
            navigationDelayCounter = refreshDelayTicks;
            hasClickedBuy = false;
            lastClickedSlot = -1;
            pageJustRefreshed = true;

            if (notifications.get() && refreshDelayTicks > 0) {
                info("Refreshing to next page in %d ticks...", refreshDelayTicks);
            }
        }
    }

    private boolean isValidMultiSnipeItem(ItemStack stack, SnipeItemConfig config) {
        if (stack.isEmpty() || !stack.isOf(config.item)) {
            return false;
        }

        if (!config.enchantments.isEmpty()) {
            if (!hasValidEnchantmentsForConfig(stack, config)) {
                return false;
            }
        }

        double itemPrice = getActualPrice(stack);
        double maxPriceValue = parsePrice(config.maxPrice);

        if (maxPriceValue == -1.0 || itemPrice == -1.0) {
            return false;
        }

        double comparisonPrice;
        if (config.priceMode == PriceMode.PER_ITEM) {
            comparisonPrice = itemPrice / stack.getCount();
        } else {
            comparisonPrice = itemPrice;
        }

        boolean willBuy = comparisonPrice <= maxPriceValue;

        if (notifications.get() && willBuy) {
            String mode = config.priceMode == PriceMode.PER_ITEM ? "per item" : "per stack";
            info("Found: %dx %s | Price: %s (%s) | Max: %s | Buying!",
                stack.getCount(),
                config.item.getName().getString(),
                formatPrice(comparisonPrice),
                mode,
                formatPrice(maxPriceValue)
            );
        }

        return willBuy;
    }

    private boolean hasValidEnchantmentsForConfig(ItemStack stack, SnipeItemConfig config) {
        if (config.enchantments.isEmpty()) return true;

        List<String> itemEnchantments = getItemEnchantments(stack);

        if (config.exactEnchantments) {
            return hasExactEnchantmentsForConfig(itemEnchantments, config.enchantments);
        } else {
            for (String requiredEnchant : config.enchantments) {
                if (matchesEnchantment(itemEnchantments, requiredEnchant)) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean hasExactEnchantmentsForConfig(List<String> itemEnchantments, List<String> requiredEnchantments) {
        if (itemEnchantments.size() != requiredEnchantments.size()) {
            return false;
        }

        for (String requiredEnchant : requiredEnchantments) {
            if (!matchesEnchantment(itemEnchantments, requiredEnchant)) {
                return false;
            }
        }

        return true;
    }

    private int getExpectedStackSize(Item item) {
        return item.getMaxCount();
    }

    private void handleConfirmationGUI(GenericContainerScreenHandler handler) {
        if (!autoConfirm.get()) {
            if (notifications.get()) {
                info("Confirmation GUI detected but auto-confirm is disabled.");
            }
            return;
        }

        if (hasClickedConfirm) return;

        if (!waitingToConfirm) {
            waitingToConfirm = true;
            confirmDelayCounter = confirmDelayTicks;

            if (notifications.get()) {
                info("Confirmation GUI detected! Waiting %d ticks before confirming...", confirmDelayTicks);
            }
            return;
        }

        if (waitingToConfirm && confirmDelayCounter == 0) {
            mc.interactionManager.clickSlot(handler.syncId, 15, 1, SlotActionType.QUICK_MOVE, mc.player);

            waitingForConfirmation = false;
            waitingToConfirm = false;
            hasClickedConfirm = true;

            if (notifications.get()) {
                info("Purchase confirmed after delay!");
            }
        }
    }

    private void openAuctionHouse() {
        String command = buildAuctionCommand();

        if (debugMode.get()) {
            info("Debug: Sending command: %s", command);
        }

        mc.getNetworkHandler().sendChatCommand(command);
        navigationDelayCounter = navigationDelayTicks;
    }

    private String buildAuctionCommand() {
        if (snipeMode.get() == SnipeMode.MULTI) {
            return "ah";
        } else {
            StringBuilder command = new StringBuilder("ah ");
            String itemName = getFormattedItemName(snipingItem.get());
            command.append(itemName);

            if (enchantmentMode.get() && !requiredEnchantments.get().isEmpty()) {
                for (String enchantment : requiredEnchantments.get()) {
                    command.append(" ").append(enchantment);
                }
            }

            return command.toString();
        }
    }

    private void processSixRowAuction(GenericContainerScreenHandler handler) {
        if (pageJustRefreshed) {
            return;
        }

        if (purchaseAttempted || waitingForConfirmation || hasClickedBuy) {
            return;
        }

        for (int i = 0; i < 44; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isOf(snipingItem.get())) {
                double currentItemPrice = getActualPrice(stack);
                if (isValidAuctionItem(stack) && currentItemPrice != -1.0) {
                    if (priceMode.get() == PriceMode.PER_STACK && stack.getCount() < getExpectedStackSize(stack.getItem())) {
                        if (notifications.get()) {
                            info("Skipping %s - not a full stack (%d/%d)",
                                snipingItem.get().getName().getString(),
                                stack.getCount(),
                                getExpectedStackSize(stack.getItem()));
                        }
                        continue;
                    }

                    if (isProcessing) {
                        attemptedItemName = snipingItem.get().getName().getString();
                        attemptedActualPrice = currentItemPrice;
                        attemptedQuantity = stack.getCount();
                        attemptedEnchantments = getEnchantmentsString(stack);

                        mc.interactionManager.clickSlot(handler.syncId, i, 1, SlotActionType.QUICK_MOVE, mc.player);

                        isProcessing = false;
                        hasClickedBuy = true;
                        lastClickedSlot = i;

                        purchaseAttempted = true;
                        purchaseTimestamp = System.currentTimeMillis();
                        inventoryCheckTicks = 0;
                        purchaseTimeoutTicks = 0;

                        if (notifications.get()) {
                            info("Attempting to buy %dx %s!", attemptedQuantity, attemptedItemName);
                        }
                        return;
                    }

                    isProcessing = true;
                    delayCounter = buyDelayTicks;

                    if (notifications.get() && buyDelayTicks > 0) {
                        info("Found valid item! Waiting %d ticks before buying...", buyDelayTicks);
                    }
                    return;
                }
            }
        }

        if (!isProcessing && !pageJustRefreshed && !purchaseAttempted && !hasClickedBuy) {
            mc.interactionManager.clickSlot(handler.syncId, 49, 1, SlotActionType.QUICK_MOVE, mc.player);
            navigationDelayCounter = refreshDelayTicks;
            hasClickedBuy = false;
            lastClickedSlot = -1;
            pageJustRefreshed = true;

            if (notifications.get() && refreshDelayTicks > 0) {
                info("Refreshing to next page in %d ticks...", refreshDelayTicks);
            }
        }
    }

    private void processThreeRowAuction(GenericContainerScreenHandler handler) {
        if (purchaseAttempted || waitingForConfirmation || hasClickedBuy) {
            return;
        }

        ItemStack auctionItem = handler.getSlot(13).getStack();
        if (auctionItem.isOf(snipingItem.get())) {
            double currentItemPrice = getActualPrice(auctionItem);
            if (isValidAuctionItem(auctionItem) && currentItemPrice != -1.0) {
                if (priceMode.get() == PriceMode.PER_STACK && auctionItem.getCount() < getExpectedStackSize(auctionItem.getItem())) {
                    if (notifications.get()) {
                        info("Skipping %s - not a full stack (%d/%d)",
                            snipingItem.get().getName().getString(),
                            auctionItem.getCount(),
                            getExpectedStackSize(auctionItem.getItem()));
                    }
                    return;
                }

                attemptedItemName = auctionItem.getItem().getName().getString();
                attemptedActualPrice = currentItemPrice;
                attemptedQuantity = auctionItem.getCount();
                attemptedEnchantments = getEnchantmentsString(auctionItem);

                if (buyDelayTicks > 0 && !isProcessing) {
                    delayCounter = buyDelayTicks;
                    if (notifications.get()) {
                        info("Ready to buy! Waiting %d ticks...", buyDelayTicks);
                    }
                    isProcessing = true;
                    return;
                }

                mc.interactionManager.clickSlot(handler.syncId, 15, 1, SlotActionType.QUICK_MOVE, mc.player);

                hasClickedBuy = true;
                purchaseAttempted = true;
                purchaseTimestamp = System.currentTimeMillis();
                inventoryCheckTicks = 0;
                purchaseTimeoutTicks = 0;
                isProcessing = false;

                if (notifications.get()) {
                    info("Buying %dx %s!", auctionItem.getCount(), attemptedItemName);
                }
            }
        }
    }

    private boolean isValidAuctionItem(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(snipingItem.get())) {
            return false;
        }

        if (enchantmentMode.get() && !requiredEnchantments.get().isEmpty()) {
            if (!hasValidEnchantments(stack)) {
                return false;
            }
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
            return false;
        }

        double comparisonPrice;
        if (priceMode.get() == PriceMode.PER_ITEM) {
            comparisonPrice = itemPrice / stack.getCount();
        } else {
            comparisonPrice = itemPrice;
        }

        if (notifications.get()) {
            String mode = priceMode.get() == PriceMode.PER_ITEM ? "per item" : "per stack";
            String priceStr = formatPrice(comparisonPrice);
            String maxStr = formatPrice(maxPriceValue);
            boolean willBuy = comparisonPrice <= maxPriceValue;

            info("Item: %dx %s | Price: %s (%s) | Max: %s | Will buy: %s",
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

    private boolean hasValidEnchantments(ItemStack stack) {
        if (requiredEnchantments.get().isEmpty()) return true;

        List<String> itemEnchantments = getItemEnchantments(stack);

        if (exactEnchantments.get()) {
            return hasExactEnchantments(itemEnchantments);
        } else {
            for (String requiredEnchant : requiredEnchantments.get()) {
                if (matchesEnchantment(itemEnchantments, requiredEnchant)) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean hasExactEnchantments(List<String> itemEnchantments) {
        if (itemEnchantments.size() != requiredEnchantments.get().size()) {
            return false;
        }

        for (String requiredEnchant : requiredEnchantments.get()) {
            if (!matchesEnchantment(itemEnchantments, requiredEnchant)) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesEnchantment(List<String> itemEnchantments, String requiredEnchant) {
        String[] parts = requiredEnchant.trim().split("\\s+");
        String enchantName = parts[0].toLowerCase();
        Integer requiredLevel = null;

        if (parts.length > 1) {
            try {
                requiredLevel = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                requiredLevel = parseRomanNumeral(parts[1]);
            }
        }

        for (String itemEnchant : itemEnchantments) {
            String itemEnchantLower = itemEnchant.toLowerCase();

            if (itemEnchantLower.contains(enchantName)) {
                if (requiredLevel == null) {
                    return true;
                } else {
                    int itemLevel = getEnchantmentLevel(itemEnchant);
                    return itemLevel >= requiredLevel;
                }
            }
        }

        return false;
    }

    private List<String> getItemEnchantments(ItemStack stack) {
        List<String> enchantments = new ArrayList<>();

        Item.TooltipContext tooltipContext = Item.TooltipContext.create(mc.world);
        List<Text> tooltip = stack.getTooltip(tooltipContext, mc.player, TooltipType.BASIC);

        for (Text line : tooltip) {
            String text = line.getString();
            if (text.matches(".*\\b(Sharpness|Protection|Efficiency|Fortune|Silk Touch|Unbreaking|Mending|Power|Punch|Flame|Infinity|Looting|Knockback|Fire Aspect|Smite|Bane of Arthropods|Sweeping Edge|Thorns|Respiration|Aqua Affinity|Depth Strider|Frost Walker|Feather Falling|Blast Protection|Projectile Protection|Fire Protection).*")) {
                enchantments.add(text.trim());
            }
        }

        return enchantments;
    }

    private String getEnchantmentsString(ItemStack stack) {
        List<String> enchants = getItemEnchantments(stack);

        if (debugMode.get()) {
            info("Debug: Found %d enchantments for %s", enchants.size(), stack.getItem().getName().getString());
            for (String enchant : enchants) {
                info("Debug: Enchantment: %s", enchant);
            }
        }

        if (enchants.isEmpty()) return "None";

        return String.join("\n", enchants);
    }

    private int getEnchantmentLevel(String enchantmentText) {
        Pattern levelPattern = Pattern.compile(".*(\\b(?:[IVX]+|\\d+))\\s*$");
        Matcher matcher = levelPattern.matcher(enchantmentText);

        if (matcher.find()) {
            String levelStr = matcher.group(1);

            return switch (levelStr) {
                case "I" -> 1;
                case "II" -> 2;
                case "III" -> 3;
                case "IV" -> 4;
                case "V" -> 5;
                case "VI" -> 6;
                case "VII" -> 7;
                case "VIII" -> 8;
                case "IX" -> 9;
                case "X" -> 10;
                default -> {
                    try {
                        yield Integer.parseInt(levelStr);
                    } catch (NumberFormatException e) {
                        yield 1;
                    }
                }
            };
        }

        return 1;
    }

    private Integer parseRomanNumeral(String roman) {
        return switch (roman.toUpperCase()) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            case "V" -> 5;
            case "VI" -> 6;
            case "VII" -> 7;
            case "VIII" -> 8;
            case "IX" -> 9;
            case "X" -> 10;
            default -> null;
        };
    }

    private double getActualPrice(ItemStack stack) {
        Item.TooltipContext tooltipContext = Item.TooltipContext.create(mc.world);
        List<Text> tooltip = stack.getTooltip(tooltipContext, mc.player, TooltipType.BASIC);
        return parseTooltipPrice(tooltip);
    }

    private int countItemInInventory() {
        if (mc.player == null) return 0;

        int count = 0;
        Item targetItem = snipeMode.get() == SnipeMode.SINGLE ? snipingItem.get() : currentSnipedItem;

        if (targetItem == null || targetItem == Items.AIR) return 0;

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(targetItem)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void handlePurchaseCheck() {
        inventoryCheckTicks++;

        if (inventoryCheckTicks < MIN_INVENTORY_CHECK_TICKS) {
            return;
        }

        int currentItemCount = countItemInInventory();

        if (currentItemCount > previousItemCount) {
            itemPickedUp = true;
            purchaseAttempted = false;
            purchaseTimeoutTicks = 0;
            hasClickedBuy = false;
            hasClickedConfirm = false;
            waitingForConfirmation = false;
            waitingToConfirm = false;

            if (notifications.get()) {
                info("Purchase successful! Got %dx %s for %s",
                    attemptedQuantity, attemptedItemName, formatPrice(attemptedActualPrice));
            }

            sendSuccessWebhook(attemptedItemName, attemptedActualPrice, attemptedQuantity, attemptedEnchantments);

            if (mc.player != null && mc.world != null) {
                mc.world.playSound(mc.player, mc.player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 1.0f, 1.0f);
            }

            previousItemCount = currentItemCount;
            inventoryCheckTicks = 0;

        } else if (inventoryCheckTicks >= MAX_INVENTORY_CHECK_TICKS) {
            purchaseAttempted = false;
            inventoryCheckTicks = 0;
            purchaseTimeoutTicks = 0;
            hasClickedBuy = false;
            hasClickedConfirm = false;
            waitingForConfirmation = false;
            waitingToConfirm = false;

            if (notifications.get()) {
                info("Purchase may have failed or item was outbid.");
            }
        }
    }

    private boolean isConfirmationGUI(ScreenHandler screenHandler) {
        if (!(screenHandler instanceof GenericContainerScreenHandler)) {
            return false;
        }

        GenericContainerScreenHandler handler = (GenericContainerScreenHandler) screenHandler;

        if (handler.getRows() != 3) {
            return false;
        }

        ItemStack confirmItem = handler.getSlot(15).getStack();
        if (confirmItem.isEmpty()) {
            return false;
        }

        Item.TooltipContext tooltipContext = Item.TooltipContext.create(mc.world);
        List<Text> tooltip = confirmItem.getTooltip(tooltipContext, mc.player, TooltipType.BASIC);

        for (Text line : tooltip) {
            String text = line.getString().toLowerCase();
            if (text.contains("confirm") || text.contains("buy") || text.contains("purchase")) {
                return true;
            }
        }

        return false;
    }

    public void info(String message, Object... args) {
        ChatUtils.info(String.format(message, args));
    }

    private void sendSuccessWebhook(String itemName, double actualPrice, int quantity, String enchantments) {
        if (!webhookEnabled.get() || webhookUrl.get().isEmpty()) {
            if (debugMode.get()) {
                info("Debug: Webhook not sent - Enabled: %s, URL set: %s",
                    webhookEnabled.get(), !webhookUrl.get().isEmpty());
            }
            return;
        }

        if (debugMode.get()) {
            info("Debug: Creating webhook payload...");
            info("Debug: Item: %s, Quantity: %d, Price: %s, Enchants: %s",
                itemName, quantity, formatPrice(actualPrice), enchantments);
        }

        String jsonPayload = createSuccessEmbed(itemName, actualPrice, quantity, enchantments);
        sendWebhookMessage(jsonPayload, "Success");
    }

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

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 204 || response.statusCode() == 200) {
                if (debugMode.get()) {
                    info("%s webhook sent successfully - Status: %d", messageType, response.statusCode());
                }
            } else {
                if (debugMode.get() || notifications.get()) {
                    ChatUtils.error("%s webhook failed - Status: %d", messageType, response.statusCode());
                }
                if (debugMode.get()) {
                    info("Debug: Response body: %s", response.body());
                }
            }

        } catch (Exception e) {
            if (debugMode.get() || notifications.get()) {
                ChatUtils.error("%s webhook error: %s", messageType, e.getMessage());
            }
            e.printStackTrace();
        }
    }

    private String createSuccessEmbed(String itemName, double actualPrice, int quantity, String enchantments) {
        String playerName = mc.player != null ? mc.player.getName().getString() : "Unknown";
        long timestamp = System.currentTimeMillis() / 1000;

        if (debugMode.get()) {
            info("Debug: Creating embed with - Item: %s, Quantity: %d, Price: %s, Enchants: %s",
                itemName, quantity, formatPrice(actualPrice), enchantments);
        }

        String maxPriceStr;
        String priceModeStr;
        double maxPriceValue;

        if (snipeMode.get() == SnipeMode.MULTI && currentSnipedItem != null) {
            SnipeItemConfig config = null;
            for (SnipeItemConfig c : multiSnipeConfigs) {
                if (c.item == currentSnipedItem) {
                    config = c;
                    break;
                }
            }
            if (config != null) {
                maxPriceValue = parsePrice(config.maxPrice);
                maxPriceStr = formatPrice(maxPriceValue);
                priceModeStr = config.priceMode.toString();
            } else {
                maxPriceValue = actualPrice;
                maxPriceStr = formatPrice(actualPrice);
                priceModeStr = "Unknown";
            }
        } else {
            maxPriceValue = parsePrice(maxPrice.get());
            maxPriceStr = formatPrice(maxPriceValue);
            priceModeStr = priceMode.get().toString();
        }

        String actualPriceStr = formatPrice(actualPrice);
        double savings = maxPriceValue - actualPrice;
        String savingsStr = formatPrice(Math.abs(savings));
        String savingsPercentage = String.format("%.1f%%", (savings / maxPriceValue) * 100);

        String webhookUsernameHardcoded = "Glazed AH Sniper";
        String webhookAvatarUrlHardcoded = "https://i.imgur.com/OL2y1cr.png";
        String webhookThumbnailUrlHardcoded = "https://i.imgur.com/OL2y1cr.png";

        String messageContent = String.format("🎯 **%s** sniped **%dx %s** for **%s**!",
            playerName, quantity, itemName, actualPriceStr);

        String description = String.format("💸 **Savings** of %s (**%s**)", savingsStr, savingsPercentage);

        String enchantValue;
        if (enchantments.equals("None") || enchantments.isEmpty()) {
            enchantValue = "None";
        } else {
            enchantValue = enchantments.trim();

            if (debugMode.get()) {
                info("Debug: Final enchant value: %s", enchantValue);
            }
        }

        String modeText = snipeMode.get() == SnipeMode.MULTI ? "Multi-Snipe" : "Single-Snipe";

        return String.format(
            "{\"content\":\"%s\"," +
                "\"username\":\"%s\"," +
                "\"avatar_url\":\"%s\"," +
                "\"embeds\":[{" +
                "\"title\":\"Glazed AH Sniper Alert [%s]\"," +
                "\"description\":\"%s\"," +
                "\"color\":8388736," +
                "\"thumbnail\":{\"url\":\"%s\"}," +
                "\"fields\":[" +
                "{\"name\":\"📦 Item\",\"value\":\"%s x%d\",\"inline\":true}," +
                "{\"name\":\"💰 Purchase Price\",\"value\":\"%s\",\"inline\":true}," +
                "{\"name\":\"💵 Max Price\",\"value\":\"%s (%s)\",\"inline\":true}," +
                "{\"name\":\"✨ Enchantments\",\"value\":\"%s\",\"inline\":false}," +
                "{\"name\":\"⏰ Time\",\"value\":\"<t:%d:R>\",\"inline\":true}" +
                "]," +
                "\"footer\":{\"text\":\"Glazed AH Sniper V2\"}," +
                "\"timestamp\":\"%s\"" +
                "}]}",
            escapeJson(messageContent),
            escapeJson(webhookUsernameHardcoded),
            escapeJson(webhookAvatarUrlHardcoded),
            modeText,
            escapeJson(description),
            escapeJson(webhookThumbnailUrlHardcoded),
            escapeJson(itemName), quantity,
            escapeJson(actualPriceStr),
            escapeJson(maxPriceStr), escapeJson(priceModeStr.toLowerCase()),
            escapeJson(enchantValue),
            timestamp,
            Instant.now().toString()
        );
    }

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
        String webhookUsernameHardcoded = "Glazed AH Sniper";

        return String.format("""
            {
                "content": "Webhook Test - AH Sniper is working for **%s**!",
                "username": "%s"
            }
            """,
            escapeJson(playerName),
            escapeJson(webhookUsernameHardcoded)
        );
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

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

    private double parseTooltipPrice(List<Text> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            return -1.0;
        }

        Pattern[] pricePatterns = {
            Pattern.compile("\\$([\\d,]+(?:\\.[\\d]+)?)([kmb])?", Pattern.CASE_INSENSITIVE),
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
        String displayName = item.getName().getString();

        if (displayName != null && !displayName.isEmpty() && !displayName.startsWith("item.") && !displayName.startsWith("block.")) {
            return displayName.toLowerCase();
        }

        String translationKey = item.getTranslationKey();
        String[] parts = translationKey.split("\\.");
        String itemName = parts[parts.length - 1];

        return itemName.replace("_", " ").toLowerCase();
    }
}
