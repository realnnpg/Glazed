package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public class AHSniper extends Module {
    private final SettingGroup sgGeneral;
    private final SettingGroup sgMultiSnipe;
    private final SettingGroup sgEnchantments;
    private final SettingGroup sgWebhook;
    private final SettingGroup sgAutoSell;
    private final SettingGroup sgUserFilter;
    private final Setting<SnipeMode> snipeMode;
    private final Setting<Item> snipingItem;
    private final Setting<String> targetItemName;
    private final Setting<String> minPrice;
    private final Setting<String> maxPrice;
    private final Setting<PriceMode> priceMode;
    private final Setting<Boolean> filterLowTime;
    private final Setting<Double> minTimeHours;
    private final Setting<Boolean> topLeftOnly;
    private final Setting<Item> multiItem1;
    private final Setting<String> multiMinPrice1;
    private final Setting<String> multiPrice1;
    private final Setting<PriceMode> multiPriceMode1;
    private final Setting<List<String>> multiEnchantments1;
    private final Setting<Boolean> multiExactEnchantments1;
    private final Setting<Item> multiItem2;
    private final Setting<String> multiMinPrice2;
    private final Setting<String> multiPrice2;
    private final Setting<PriceMode> multiPriceMode2;
    private final Setting<List<String>> multiEnchantments2;
    private final Setting<Boolean> multiExactEnchantments2;
    private final Setting<Item> multiItem3;
    private final Setting<String> multiMinPrice3;
    private final Setting<String> multiPrice3;
    private final Setting<PriceMode> multiPriceMode3;
    private final Setting<List<String>> multiEnchantments3;
    private final Setting<Boolean> multiExactEnchantments3;
    private final Setting<Item> multiItem4;
    private final Setting<String> multiMinPrice4;
    private final Setting<String> multiPrice4;
    private final Setting<PriceMode> multiPriceMode4;
    private final Setting<List<String>> multiEnchantments4;
    private final Setting<Boolean> multiExactEnchantments4;
    private final Setting<Item> multiItem5;
    private final Setting<String> multiMinPrice5;
    private final Setting<String> multiPrice5;
    private final Setting<PriceMode> multiPriceMode5;
    private final Setting<List<String>> multiEnchantments5;
    private final Setting<Boolean> multiExactEnchantments5;
    private final int refreshDelayTicks;
    private final int buyDelayTicks;
    private final int confirmDelayTicks;
    private final int navigationDelayTicks;
    private final Setting<Boolean> notifications;
    private final Setting<Boolean> autoConfirm;
    private final Setting<Boolean> enchantmentMode;
    private final Setting<List<String>> requiredEnchantments;
    private final Setting<Boolean> exactEnchantments;
    private final Setting<Boolean> autoSell;
    private final Setting<String> sellPrice;
    private final Setting<Boolean> webhookEnabled;
    private final Setting<String> webhookUrl;
    private final Setting<Boolean> selfPing;
    private final Setting<String> discordId;
    private final Setting<Boolean> debugMode;
    private final Setting<List<String>> userBlacklist;
    private final Setting<Boolean> useAdminList;
    private boolean waitingForConfirmation;
    private boolean itemPickedUp;
    private boolean purchaseAttempted;
    private String attemptedItemName;
    private double attemptedActualPrice;
    private int attemptedQuantity;
    private long purchaseTimestamp;
    private String attemptedEnchantments;
    private boolean commandSent;
    private boolean hasSetSort;
    private int previousItemCount;
    private int inventoryCheckTicks;
    private final int MAX_INVENTORY_CHECK_TICKS;
    private final int MIN_INVENTORY_CHECK_TICKS;
    private int delayCounter;
    private boolean isProcessing;
    private boolean hasClickedBuy;
    private boolean hasClickedConfirm;
    private int confirmDelayCounter;
    private boolean waitingToConfirm;
    private int navigationDelayCounter;
    private boolean waitingToNavigate;
    private List<SnipeItemConfig> multiSnipeConfigs;
    private Item currentSnipedItem;
    private int lastClickedSlot;
    private boolean pageJustRefreshed;
    private int purchaseTimeoutTicks;
    private final int MAX_PURCHASE_TIMEOUT_TICKS;
    private boolean sellingPhase;
    private int sellingDelayCounter;
    private final int SELL_DELAY_TICKS;
    private int lastActionTicks;
    private final int MAX_STAGNANT_TICKS;
    private final HttpClient httpClient;

    public AHSniper() {
        super(GlazedAddon.CATEGORY, "ah-sniper", "Automatically snipes items from auction house for cheap prices.");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgMultiSnipe = this.settings.createGroup("Multi-Snipe Items");
        this.sgEnchantments = this.settings.createGroup("Enchantments");
        this.sgAutoSell = this.settings.createGroup("Auto Sell");
        this.sgWebhook = this.settings.createGroup("Discord Webhook");
        this.sgUserFilter = this.settings.createGroup("User Filter");

        this.snipeMode = this.sgGeneral.add(new EnumSetting.Builder<SnipeMode>()
            .name("snipe-mode")
            .description("Choose between single item sniping or multi-item sniping.")
            .defaultValue(SnipeMode.SINGLE)
            .build());

        this.snipingItem = this.sgGeneral.add(new ItemSetting.Builder()
        .name("sniping-item")
        .description("The item to snipe from auctions.")
        .defaultValue(Items.AIR)
            .visible(() -> this.snipeMode.get() == SnipeMode.SINGLE)
            .build());

        this.targetItemName = this.sgGeneral.add(new StringSetting.Builder()
            .name("item-name")
            .description("Custom search name for the /ah command.")
            .defaultValue("")
            .visible(() -> this.snipeMode.get() == SnipeMode.SINGLE)
            .build());

        this.minPrice = this.sgGeneral.add(new StringSetting.Builder()
        .name("min-price")
        .description("Minimum price to pay (supports K, M, B suffixes). Set to 0 to disable.")
        .defaultValue("0")
            .visible(() -> this.snipeMode.get() == SnipeMode.SINGLE)
            .build());

        this.maxPrice = this.sgGeneral.add(new StringSetting.Builder()
        .name("max-price")
        .description("Maximum price to pay (supports K, M, B suffixes).")
        .defaultValue("1k")
            .visible(() -> this.snipeMode.get() == SnipeMode.SINGLE)
            .build());

        this.priceMode = this.sgGeneral.add(new EnumSetting.Builder<PriceMode>()
        .name("price-mode")
        .description("Whether max price is per individual item or per full stack.")
        .defaultValue(PriceMode.PER_STACK)
            .visible(() -> this.snipeMode.get() == SnipeMode.SINGLE)
            .build());

        this.filterLowTime = this.sgGeneral.add(new BoolSetting.Builder()
            .name("filter-low-time")
            .description("Skip items with low self-destruct time remaining.")
            .defaultValue(true)
            .build());

        this.minTimeHours = this.sgGeneral.add(new DoubleSetting.Builder()
            .name("min-time-hours")
            .description("Minimum self-destruct time in hours to accept an item.")
            .defaultValue(24.0)
            .min(1.0)
            .sliderMax(72.0)
            .max(72.0)
            .visible(this.filterLowTime::get)
            .build());

        this.topLeftOnly = this.sgGeneral.add(new BoolSetting.Builder()
            .name("top-left-only")
            .description("Only check the top-left slot (slot 0) for faster sniping.")
            .defaultValue(false)
            .build());

        this.multiItem1 = this.sgMultiSnipe.add(new ItemSetting.Builder()
            .name("item-1")
            .description("First item to snipe.")
            .defaultValue(Items.AIR)
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI)
            .build());
        this.multiMinPrice1 = this.sgMultiSnipe.add(new StringSetting.Builder()
            .name("min-price-1")
            .description("Min price for item 1. Set to 0 to disable.")
            .defaultValue("0")
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem1.get() != Items.AIR)
            .build());
        this.multiPrice1 = this.sgMultiSnipe.add(new StringSetting.Builder()
            .name("max-price-1")
            .description("Max price for item 1.")
            .defaultValue("1k")
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem1.get() != Items.AIR)
            .build());
        this.multiPriceMode1 = this.sgMultiSnipe.add(new EnumSetting.Builder<PriceMode>()
            .name("price-mode-1")
            .description("Price mode for item 1.")
            .defaultValue(PriceMode.PER_STACK)
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem1.get() != Items.AIR)
            .build());
        this.multiEnchantments1 = this.sgMultiSnipe.add(new StringListSetting.Builder()
            .name("enchantments-1")
            .description("Required enchantments for item 1 (e.g., 'sharpness 5').")
            .defaultValue(new ArrayList<>())
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem1.get() != Items.AIR)
            .build());
        this.multiExactEnchantments1 = this.sgMultiSnipe.add(new BoolSetting.Builder()
            .name("exact-enchantments-1")
            .description("Require exact enchantments for item 1.")
            .defaultValue(false)
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem1.get() != Items.AIR && !this.multiEnchantments1.get().isEmpty())
            .build());

        this.multiItem2 = this.sgMultiSnipe.add(new ItemSetting.Builder()
            .name("item-2")
            .description("Second item to snipe.")
            .defaultValue(Items.AIR)
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI)
            .build());
        this.multiMinPrice2 = this.sgMultiSnipe.add(new StringSetting.Builder()
            .name("min-price-2")
            .description("Min price for item 2. Set to 0 to disable.")
            .defaultValue("0")
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem2.get() != Items.AIR)
            .build());
        this.multiPrice2 = this.sgMultiSnipe.add(new StringSetting.Builder()
            .name("max-price-2")
            .description("Max price for item 2.")
            .defaultValue("1k")
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem2.get() != Items.AIR)
            .build());
        this.multiPriceMode2 = this.sgMultiSnipe.add(new EnumSetting.Builder<PriceMode>()
            .name("price-mode-2")
            .description("Price mode for item 2.")
            .defaultValue(PriceMode.PER_STACK)
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem2.get() != Items.AIR)
            .build());
        this.multiEnchantments2 = this.sgMultiSnipe.add(new StringListSetting.Builder()
            .name("enchantments-2")
            .description("Required enchantments for item 2 (e.g., 'sharpness 5').")
            .defaultValue(new ArrayList<>())
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem2.get() != Items.AIR)
            .build());
        this.multiExactEnchantments2 = this.sgMultiSnipe.add(new BoolSetting.Builder()
            .name("exact-enchantments-2")
            .description("Require exact enchantments for item 2.")
            .defaultValue(false)
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem2.get() != Items.AIR && !this.multiEnchantments2.get().isEmpty())
            .build());

        this.multiItem3 = this.sgMultiSnipe.add(new ItemSetting.Builder()
            .name("item-3")
            .description("Third item to snipe.")
            .defaultValue(Items.AIR)
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI)
            .build());
        this.multiMinPrice3 = this.sgMultiSnipe.add(new StringSetting.Builder()
            .name("min-price-3")
            .description("Min price for item 3. Set to 0 to disable.")
            .defaultValue("0")
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem3.get() != Items.AIR)
            .build());
        this.multiPrice3 = this.sgMultiSnipe.add(new StringSetting.Builder()
            .name("max-price-3")
            .description("Max price for item 3.")
            .defaultValue("1k")
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem3.get() != Items.AIR)
            .build());
        this.multiPriceMode3 = this.sgMultiSnipe.add(new EnumSetting.Builder<PriceMode>()
            .name("price-mode-3")
            .description("Price mode for item 3.")
            .defaultValue(PriceMode.PER_STACK)
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem3.get() != Items.AIR)
            .build());
        this.multiEnchantments3 = this.sgMultiSnipe.add(new StringListSetting.Builder()
            .name("enchantments-3")
            .description("Required enchantments for item 3 (e.g., 'sharpness 5').")
            .defaultValue(new ArrayList<>())
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem3.get() != Items.AIR)
            .build());
        this.multiExactEnchantments3 = this.sgMultiSnipe.add(new BoolSetting.Builder()
            .name("exact-enchantments-3")
            .description("Require exact enchantments for item 3.")
            .defaultValue(false)
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem3.get() != Items.AIR && !this.multiEnchantments3.get().isEmpty())
            .build());

        this.multiItem4 = this.sgMultiSnipe.add(new ItemSetting.Builder()
            .name("item-4")
            .description("Fourth item to snipe.")
            .defaultValue(Items.AIR)
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI)
            .build());
        this.multiMinPrice4 = this.sgMultiSnipe.add(new StringSetting.Builder()
            .name("min-price-4")
            .description("Min price for item 4. Set to 0 to disable.")
            .defaultValue("0")
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem4.get() != Items.AIR)
            .build());
        this.multiPrice4 = this.sgMultiSnipe.add(new StringSetting.Builder()
            .name("max-price-4")
            .description("Max price for item 4.")
            .defaultValue("1k")
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem4.get() != Items.AIR)
            .build());
        this.multiPriceMode4 = this.sgMultiSnipe.add(new EnumSetting.Builder<PriceMode>()
            .name("price-mode-4")
            .description("Price mode for item 4.")
            .defaultValue(PriceMode.PER_STACK)
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem4.get() != Items.AIR)
            .build());
        this.multiEnchantments4 = this.sgMultiSnipe.add(new StringListSetting.Builder()
            .name("enchantments-4")
            .description("Required enchantments for item 4 (e.g., 'sharpness 5').")
            .defaultValue(new ArrayList<>())
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem4.get() != Items.AIR)
            .build());
        this.multiExactEnchantments4 = this.sgMultiSnipe.add(new BoolSetting.Builder()
            .name("exact-enchantments-4")
            .description("Require exact enchantments for item 4.")
            .defaultValue(false)
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem4.get() != Items.AIR && !this.multiEnchantments4.get().isEmpty())
            .build());

        this.multiItem5 = this.sgMultiSnipe.add(new ItemSetting.Builder()
            .name("item-5")
            .description("Fifth item to snipe.")
            .defaultValue(Items.AIR)
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI)
            .build());
        this.multiMinPrice5 = this.sgMultiSnipe.add(new StringSetting.Builder()
            .name("min-price-5")
            .description("Min price for item 5. Set to 0 to disable.")
            .defaultValue("0")
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem5.get() != Items.AIR)
            .build());
        this.multiPrice5 = this.sgMultiSnipe.add(new StringSetting.Builder()
            .name("max-price-5")
            .description("Max price for item 5.")
            .defaultValue("1k")
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem5.get() != Items.AIR)
            .build());
        this.multiPriceMode5 = this.sgMultiSnipe.add(new EnumSetting.Builder<PriceMode>()
            .name("price-mode-5")
            .description("Price mode for item 5.")
            .defaultValue(PriceMode.PER_STACK)
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem5.get() != Items.AIR)
            .build());
        this.multiEnchantments5 = this.sgMultiSnipe.add(new StringListSetting.Builder()
            .name("enchantments-5")
            .description("Required enchantments for item 5 (e.g., 'sharpness 5').")
            .defaultValue(new ArrayList<>())
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem5.get() != Items.AIR)
            .build());
        this.multiExactEnchantments5 = this.sgMultiSnipe.add(new BoolSetting.Builder()
            .name("exact-enchantments-5")
            .description("Require exact enchantments for item 5.")
            .defaultValue(false)
            .visible(() -> this.snipeMode.get() == SnipeMode.MULTI && this.multiItem5.get() != Items.AIR && !this.multiEnchantments5.get().isEmpty())
            .build());

        this.refreshDelayTicks = 1;
        this.buyDelayTicks = 0;
        this.confirmDelayTicks = 1;
        this.navigationDelayTicks = 0;

        this.notifications = this.sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show chat notifications.")
        .defaultValue(true)
            .build());

        this.autoConfirm = this.sgGeneral.add(new BoolSetting.Builder()
        .name("auto-confirm")
        .description("Automatically confirm purchases in the confirmation GUI.")
        .defaultValue(true)
            .build());

        this.enchantmentMode = this.sgEnchantments.add(new BoolSetting.Builder()
            .name("enchantment-mode")
            .description("Enable enchantment filtering for sniping specific enchanted items.")
            .defaultValue(false)
            .visible(() -> this.snipeMode.get() == SnipeMode.SINGLE)
            .build());

        this.requiredEnchantments = this.sgEnchantments.add(new StringListSetting.Builder()
            .name("required-enchantments")
            .description("List of required enchantments with levels (e.g., 'sharpness 5', 'protection 4').")
            .defaultValue(new ArrayList<>())
            .visible(() -> this.snipeMode.get() == SnipeMode.SINGLE && this.enchantmentMode.get())
            .build());

        this.exactEnchantments = this.sgEnchantments.add(new BoolSetting.Builder()
            .name("exact-enchantments")
            .description("If true, item must have EXACTLY the enchantments listed (no more, no less).")
            .defaultValue(false)
            .visible(() -> this.snipeMode.get() == SnipeMode.SINGLE && this.enchantmentMode.get())
            .build());

        this.autoSell = this.sgAutoSell.add(new BoolSetting.Builder()
            .name("auto-sell")
            .description("Automatically list the sniped item on the AH after purchase.")
            .defaultValue(false)
            .build());

        this.sellPrice = this.sgAutoSell.add(new StringSetting.Builder()
            .name("sell-price")
            .description("Price to list the item at (supports K, M, B suffixes).")
            .defaultValue("14m")
            .visible(this.autoSell::get)
            .build());

        this.webhookEnabled = this.sgWebhook.add(new BoolSetting.Builder()
        .name("webhook-enabled")
        .description("Enable Discord webhook notifications.")
        .defaultValue(false)
            .build());

        this.webhookUrl = this.sgWebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL.")
        .defaultValue("")
            .visible(this.webhookEnabled::get)
            .build());

        this.selfPing = this.sgWebhook.add(new BoolSetting.Builder()
            .name("self-ping")
            .description("Ping yourself in the webhook message.")
            .defaultValue(false)
            .visible(this.webhookEnabled::get)
            .build());

        this.discordId = this.sgWebhook.add(new StringSetting.Builder()
            .name("discord-id")
            .description("Your Discord user ID for pinging.")
        .defaultValue("")
            .visible(() -> this.webhookEnabled.get() && this.selfPing.get())
            .build());

        this.debugMode = this.sgWebhook.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Enable debug logging for webhook issues.")
        .defaultValue(false)
            .visible(this.webhookEnabled::get)
            .build());

        this.userBlacklist = this.sgUserFilter.add(new StringListSetting.Builder()
            .name("user-blacklist")
            .description("List of usernames to avoid buying from.")
            .defaultValue(new ArrayList<>())
            .build());

        this.useAdminList = this.sgUserFilter.add(new BoolSetting.Builder()
            .name("use-admin-list")
            .description("Allow purchases from users in the AdminList module.")
            .defaultValue(true)
            .build());

        this.waitingForConfirmation = false;
        this.itemPickedUp = false;
        this.purchaseAttempted = false;
        this.attemptedItemName = "";
        this.attemptedActualPrice = 0.0;
        this.attemptedQuantity = 0;
        this.purchaseTimestamp = 0L;
        this.attemptedEnchantments = "";
        this.commandSent = false;
        this.hasSetSort = false;
        this.previousItemCount = 0;
        this.inventoryCheckTicks = 0;
        this.MAX_INVENTORY_CHECK_TICKS = 50;
        this.MIN_INVENTORY_CHECK_TICKS = 10;
        this.delayCounter = 0;
        this.isProcessing = false;
        this.hasClickedBuy = false;
        this.hasClickedConfirm = false;
        this.confirmDelayCounter = 0;
        this.waitingToConfirm = false;
        this.navigationDelayCounter = 0;
        this.waitingToNavigate = false;
        this.multiSnipeConfigs = new ArrayList<>();
        this.currentSnipedItem = null;
        this.lastClickedSlot = -1;
        this.pageJustRefreshed = false;
        this.purchaseTimeoutTicks = 0;
        this.MAX_PURCHASE_TIMEOUT_TICKS = 100;
        this.sellingPhase = false;
        this.sellingDelayCounter = 0;
        this.SELL_DELAY_TICKS = 2;
        this.lastActionTicks = 0;
        this.MAX_STAGNANT_TICKS = 2200;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
    }

    @Override
    public void onActivate() {
        if (this.mc.player == null) {
            this.toggle();
            return;
        }

        AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect != null && !autoReconnect.isActive()) {
            autoReconnect.toggle();
        }

        if (this.snipeMode.get() == SnipeMode.MULTI) {
            this.multiSnipeConfigs.clear();
            SnipeItemConfig config;
            if (this.multiItem1.get() != Items.AIR) {
                config = new SnipeItemConfig(this.multiItem1.get(), this.multiMinPrice1.get(), this.multiPrice1.get(), this.multiPriceMode1.get());
                config.enchantments = new ArrayList<>(this.multiEnchantments1.get());
                config.exactEnchantments = this.multiExactEnchantments1.get();
                this.multiSnipeConfigs.add(config);
            }
            if (this.multiItem2.get() != Items.AIR) {
                config = new SnipeItemConfig(this.multiItem2.get(), this.multiMinPrice2.get(), this.multiPrice2.get(), this.multiPriceMode2.get());
                config.enchantments = new ArrayList<>(this.multiEnchantments2.get());
                config.exactEnchantments = this.multiExactEnchantments2.get();
                this.multiSnipeConfigs.add(config);
            }
            if (this.multiItem3.get() != Items.AIR) {
                config = new SnipeItemConfig(this.multiItem3.get(), this.multiMinPrice3.get(), this.multiPrice3.get(), this.multiPriceMode3.get());
                config.enchantments = new ArrayList<>(this.multiEnchantments3.get());
                config.exactEnchantments = this.multiExactEnchantments3.get();
                this.multiSnipeConfigs.add(config);
            }
            if (this.multiItem4.get() != Items.AIR) {
                config = new SnipeItemConfig(this.multiItem4.get(), this.multiMinPrice4.get(), this.multiPrice4.get(), this.multiPriceMode4.get());
                config.enchantments = new ArrayList<>(this.multiEnchantments4.get());
                config.exactEnchantments = this.multiExactEnchantments4.get();
                this.multiSnipeConfigs.add(config);
            }
            if (this.multiItem5.get() != Items.AIR) {
                config = new SnipeItemConfig(this.multiItem5.get(), this.multiMinPrice5.get(), this.multiPrice5.get(), this.multiPriceMode5.get());
                config.enchantments = new ArrayList<>(this.multiEnchantments5.get());
                config.exactEnchantments = this.multiExactEnchantments5.get();
                this.multiSnipeConfigs.add(config);
            }

            if (this.multiSnipeConfigs.isEmpty()) {
                if (this.notifications.get()) {
                    ChatUtils.error("No items configured for multi-snipe!");
                }
                this.toggle();
                return;
            }

            if (this.notifications.get()) {
                this.info("Multi-Snipe activated! Monitoring %d items", this.multiSnipeConfigs.size());
                for (SnipeItemConfig c : this.multiSnipeConfigs) {
                    this.info("  - %s - Max: %s (%s)", c.item.getName().getString(), c.maxPrice, c.priceMode.toString());
                }
            }
        } else {
            double parsedPrice = this.parsePrice(this.maxPrice.get());
        if (parsedPrice == -1.0) {
                if (this.notifications.get()) {
                ChatUtils.error("Invalid price format!");
            }
                this.toggle();
            return;
        }
            if (this.snipingItem.get() == Items.AIR) {
                if (this.notifications.get()) {
                ChatUtils.error("Please select an item to snipe!");
            }
                this.toggle();
            return;
            }
            if (this.notifications.get()) {
                this.info("Single-Snipe activated! Sniping %s for max %s (%s)",
                    this.snipingItem.get().getName().getString(), this.maxPrice.get(), this.priceMode.get().toString());
            }
        }

        this.resetState();
        this.previousItemCount = this.countItemInInventory();

        if (this.debugMode.get()) {
            this.info("Debug: Webhook enabled: " + this.webhookEnabled.get());
            this.info("Debug: Webhook URL set: " + !this.webhookUrl.get().isEmpty());
            this.testWebhook();
        }
    }

    @Override
    public void onDeactivate() {
        this.resetState();
        this.multiSnipeConfigs.clear();
    }

    private void resetState() {
        this.isProcessing = false;
        this.hasClickedBuy = false;
        this.hasClickedConfirm = false;
        this.purchaseAttempted = false;
        this.sellingPhase = false;
        this.waitingForConfirmation = false;
        this.waitingToConfirm = false;
        this.waitingToNavigate = false;
        this.itemPickedUp = false;
        this.commandSent = false;
        this.hasSetSort = false;
        this.pageJustRefreshed = false;
        this.delayCounter = 0;
        this.confirmDelayCounter = 0;
        this.navigationDelayCounter = 0;
        this.purchaseTimeoutTicks = 0;
        this.inventoryCheckTicks = 0;
        this.sellingDelayCounter = 0;
        this.lastActionTicks = 0;
        this.attemptedEnchantments = "";
        this.currentSnipedItem = null;
        this.lastClickedSlot = -1;
        if (this.debugMode.get()) {
            this.info("Debug: State reset completed");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player == null || this.mc.world == null) return;

        ++this.lastActionTicks;
        if (this.lastActionTicks >= MAX_STAGNANT_TICKS) {
            if (this.mc.currentScreen != null) {
                this.mc.currentScreen.close();
            }
            this.resetState();
            this.openAuctionHouse();
            return;
        }

        if (this.delayCounter > 0) {
            --this.delayCounter;
            return;
        }
        if (this.confirmDelayCounter > 0) {
            --this.confirmDelayCounter;
            return;
        }
        if (this.navigationDelayCounter > 0) {
            --this.navigationDelayCounter;
            if (this.navigationDelayCounter == 0) {
                this.pageJustRefreshed = false;
            }
            return;
        }
        if (this.sellingDelayCounter > 0) {
            --this.sellingDelayCounter;
            if (this.sellingDelayCounter == 0) {
                this.performAutoSell();
            }
            return;
        }

        if (this.purchaseAttempted) {
            ++this.purchaseTimeoutTicks;
            if (this.purchaseTimeoutTicks >= 100) {
                if (this.debugMode.get()) {
                    this.info("Debug: Purchase timeout reached, resetting state");
                }
                this.purchaseAttempted = false;
                this.purchaseTimeoutTicks = 0;
                this.inventoryCheckTicks = 0;
                this.hasClickedBuy = false;
                this.hasClickedConfirm = false;
                this.waitingForConfirmation = false;
                this.waitingToConfirm = false;
                if (this.notifications.get()) {
                    this.info("Purchase timed out, continuing to snipe...");
                }
            }

            this.handlePurchaseCheck();
            return;
        }

        if (this.sellingPhase) return;

        ScreenHandler screenHandler = this.mc.player.currentScreenHandler;

        if (this.isConfirmationGUI(screenHandler)) {
            this.handleConfirmationGUI((GenericContainerScreenHandler) screenHandler);
            return;
        }

        if (screenHandler instanceof GenericContainerScreenHandler containerHandler) {
            this.commandSent = true;
            if (this.snipeMode.get() == SnipeMode.MULTI) {
                if (containerHandler.getRows() == 6) {
                    this.processMultiSnipeAuction(containerHandler);
                        }
                    } else {
                if (containerHandler.getRows() == 6) {
                    if (this.topLeftOnly.get()) {
                        this.processMainPageTopLeftOnly(containerHandler);
                    } else {
                        this.processSixRowAuction(containerHandler);
                    }
                } else if (containerHandler.getRows() == 3) {
                    this.processThreeRowAuction(containerHandler);
                }
            }
        } else {
            if (this.commandSent && !this.isProcessing && !this.purchaseAttempted) {
                if (this.debugMode.get()) {
                    this.info("Debug: Not in auction house, resetting command state");
                }
                this.commandSent = false;
                this.hasSetSort = false;
            }
            if (!this.commandSent) {
                this.openAuctionHouse();
                this.commandSent = true;
            }
        }
    }

    private void processMainPageTopLeftOnly(GenericContainerScreenHandler handler) {
        ItemStack sortBtn = handler.getSlot(47).getStack();
        if (!sortBtn.isEmpty() && sortBtn.getName().getString().contains("Recently Listed")) {
            this.mc.interactionManager.clickSlot(handler.syncId, 47, 0, SlotActionType.PICKUP, this.mc.player);
            this.navigationDelayCounter = 1;
            this.lastActionTicks = 0;
            return;
        }

        ItemStack topLeft = handler.getSlot(0).getStack();
        double price = this.getActualPrice(topLeft);
        if (!topLeft.isEmpty() && topLeft.isOf(this.snipingItem.get()) && this.isValidAuctionItem(topLeft) && price != -1.0) {
            this.currentSnipedItem = this.snipingItem.get();
            this.mc.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.PICKUP, this.mc.player);
            this.attemptedItemName = this.snipingItem.get().getName().getString();
            this.attemptedActualPrice = price;
            this.attemptedQuantity = topLeft.getCount();
            this.attemptedEnchantments = this.getEnchantmentsString(topLeft);
            this.hasClickedBuy = true;
            this.lastActionTicks = 0;
        } else {
            this.mc.interactionManager.clickSlot(handler.syncId, 49, 0, SlotActionType.PICKUP, this.mc.player);
            this.navigationDelayCounter = 1;
            this.lastActionTicks = 0;
        }
    }

    private void processMultiSnipeAuction(GenericContainerScreenHandler handler) {
        if (!this.hasSetSort) {
            ItemStack sortItem = handler.getSlot(47).getStack();
            if (!sortItem.isEmpty()) {
                Item.TooltipContext tooltipContext = Item.TooltipContext.create(this.mc.world);
                List<Text> tooltip = sortItem.getTooltip(tooltipContext, this.mc.player, TooltipType.BASIC);
                boolean isLastListed = false;
                for (Text line : tooltip) {
                    String text = line.getString().toLowerCase();
                    if (text.contains("last listed") || text.contains("recently listed")) {
                        isLastListed = true;
                        break;
                    }
                }

                if (!isLastListed) {
                    this.mc.interactionManager.clickSlot(handler.syncId, 47, 0, SlotActionType.PICKUP, this.mc.player);
                    this.delayCounter = 10;
                    this.lastActionTicks = 0;
                    if (this.notifications.get()) {
                        this.info("Setting sort to Last Listed...");
                    }
                    return;
                }
                this.hasSetSort = true;
                if (this.notifications.get()) {
                    this.info("Sort is set to Last Listed");
                }
            }
        }

        if (this.pageJustRefreshed) return;
        if (this.purchaseAttempted || this.waitingForConfirmation || this.hasClickedBuy) return;

        for (int i = 0; i < 44; ++i) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty()) {
                if (this.isShulkerBox(stack)) continue;
                if (this.hasCursedEnchantments(stack)) continue;

                for (SnipeItemConfig config : this.multiSnipeConfigs) {
                    if (stack.isOf(config.item)) {
                        double currentItemPrice = this.getActualPrice(stack);
                        if (this.isValidMultiSnipeItem(stack, config) && currentItemPrice != -1.0) {
                            if (config.priceMode == PriceMode.PER_STACK && stack.getCount() < this.getExpectedStackSize(stack.getItem())) {
                                if (this.notifications.get()) {
                                    this.info("Skipping %s - not a full stack (%d/%d)", config.item.getName().getString(), stack.getCount(), this.getExpectedStackSize(stack.getItem()));
                                }
                                continue;
                            }

                            if (this.isProcessing) {
                                this.currentSnipedItem = config.item;
                                this.attemptedItemName = stack.getItem().getName().getString();
                                this.attemptedActualPrice = currentItemPrice;
                                this.attemptedQuantity = stack.getCount();
                                this.attemptedEnchantments = this.getEnchantmentsString(stack);
                                this.mc.interactionManager.clickSlot(handler.syncId, i, 1, SlotActionType.QUICK_MOVE, this.mc.player);
                                this.isProcessing = false;
                                this.hasClickedBuy = true;
                                this.lastClickedSlot = i;
                                this.purchaseAttempted = true;
                                this.purchaseTimestamp = System.currentTimeMillis();
                                this.inventoryCheckTicks = 0;
                                this.purchaseTimeoutTicks = 0;
                                this.lastActionTicks = 0;
                                if (this.notifications.get()) {
                                    this.info("Attempting to buy %dx %s!", this.attemptedQuantity, this.attemptedItemName);
                                }
                                return;
        } else {
                                this.isProcessing = true;
                                this.delayCounter = 0;
                                return;
                            }
                        }
                    }
                }
            }
        }

        if (!this.isProcessing && !this.pageJustRefreshed && !this.purchaseAttempted && !this.hasClickedBuy) {
            this.mc.interactionManager.clickSlot(handler.syncId, 49, 1, SlotActionType.QUICK_MOVE, this.mc.player);
            this.navigationDelayCounter = 1;
            this.hasClickedBuy = false;
            this.lastClickedSlot = -1;
            this.pageJustRefreshed = true;
            this.lastActionTicks = 0;
            if (this.notifications.get()) {
                this.info("Refreshing to next page in %d ticks...", 1);
            }
        }
    }

    private boolean isValidMultiSnipeItem(ItemStack stack, SnipeItemConfig config) {
        if (stack.isEmpty() || !stack.isOf(config.item)) return false;
        if (this.isShulkerBox(stack)) return false;
        if (this.hasCursedEnchantments(stack)) return false;
        if (this.filterLowTime.get()) {
            double timeLeft = this.parseSelfDestructTime(stack);
            if (timeLeft != -1.0 && timeLeft < this.minTimeHours.get()) return false;
        }
        if (!config.enchantments.isEmpty() && !this.hasValidEnchantmentsForConfig(stack, config)) return false;

        // Check user filter
        Item.TooltipContext tooltipContext = Item.TooltipContext.create(this.mc.world);
        List<Text> tooltip = stack.getTooltip(tooltipContext, this.mc.player, TooltipType.BASIC);
        String sellerName = this.extractSellerName(tooltip);
        if (!this.isSellerAllowed(sellerName)) {
            if (this.notifications.get()) {
                this.info("Skipping item from blacklisted user: %s", sellerName);
            }
            return false;
        }

        double itemPrice = this.getActualPrice(stack);
        double maxPriceValue = this.parsePrice(config.maxPrice);
        double minPriceValue = this.parsePrice(config.minPrice);
        if (maxPriceValue == -1.0 || itemPrice == -1.0) return false;

        double comparisonPrice;
        if (config.priceMode == PriceMode.PER_ITEM) {
            comparisonPrice = itemPrice / (double) stack.getCount();
        } else {
            comparisonPrice = itemPrice;
        }

        // Check minimum price
        if (minPriceValue > 0 && comparisonPrice < minPriceValue) {
            if (this.notifications.get()) {
                String mode = config.priceMode == PriceMode.PER_ITEM ? "per item" : "per stack";
                this.info("Item price %s below minimum %s (%s)", this.formatPrice(comparisonPrice), this.formatPrice(minPriceValue), mode);
            }
            return false;
        }

        boolean willBuy = comparisonPrice <= maxPriceValue;
        if (this.notifications.get() && willBuy) {
            String mode = config.priceMode == PriceMode.PER_ITEM ? "per item" : "per stack";
            this.info("Found: %dx %s | Price: %s (%s) | Max: %s | Buying!",
                stack.getCount(), config.item.getName().getString(),
                this.formatPrice(comparisonPrice), mode, this.formatPrice(maxPriceValue));
        }
        return willBuy;
    }

    private boolean hasValidEnchantmentsForConfig(ItemStack stack, SnipeItemConfig config) {
        if (config.enchantments.isEmpty()) return true;
        List<String> itemEnchantments = this.getItemEnchantments(stack);
        if (config.exactEnchantments) {
            return this.hasExactEnchantmentsForConfig(itemEnchantments, config.enchantments);
        }
        for (String requiredEnchant : config.enchantments) {
            if (this.matchesEnchantment(itemEnchantments, requiredEnchant)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasExactEnchantmentsForConfig(List<String> itemEnchantments, List<String> requiredEnchantments) {
        if (itemEnchantments.size() != requiredEnchantments.size()) return false;
        for (String requiredEnchant : requiredEnchantments) {
            if (!this.matchesEnchantment(itemEnchantments, requiredEnchant)) return false;
        }
                return true;
            }

    private int getExpectedStackSize(Item item) {
        return item.getMaxCount();
    }

    private void handleConfirmationGUI(GenericContainerScreenHandler handler) {
        if (!this.autoConfirm.get()) {
            if (this.notifications.get()) {
                this.info("Confirmation GUI detected but auto-confirm is disabled.");
            }
            return;
        }
        if (this.hasClickedConfirm) return;

        if (this.clickConfirmButton(handler)) {
            this.lastActionTicks = 0;
            this.hasClickedConfirm = true;
            this.purchaseAttempted = true;
            this.inventoryCheckTicks = 0;
            this.previousItemCount = this.countItemInInventory();
            this.waitingForConfirmation = false;
            this.waitingToConfirm = false;
            if (this.notifications.get()) {
                this.info("Purchase confirmed!");
            }
        }
    }

    private boolean clickConfirmButton(GenericContainerScreenHandler handler) {
        for (int i = 0; i < handler.slots.size(); ++i) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (this.isConfirmButton(stack)) {
                this.mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, this.mc.player);
                return true;
            }
        }
            return false;
        }

    private boolean isConfirmButton(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String name = stack.getName().getString().toLowerCase();
        if (name.contains("confirm") || name.contains("buy") || name.contains("yes") || name.contains("accept")) {
                    return true;
                }
        Item item = stack.getItem();
        return item == Items.LIME_WOOL || item == Items.LIME_DYE ||
            item == Items.GREEN_CONCRETE || item == Items.GREEN_CONCRETE_POWDER ||
            item == Items.LIME_STAINED_GLASS || item == Items.EMERALD ||
            item == Items.LIME_TERRACOTTA || item == Items.LIME_STAINED_GLASS_PANE;
    }

    private void openAuctionHouse() {
        if (this.mc.getNetworkHandler() == null) return;
        String command = this.buildAuctionCommand();
        if (this.debugMode.get()) {
            this.info("Debug: Sending command: %s", command);
        }
        this.mc.getNetworkHandler().sendChatCommand(command);
        this.navigationDelayCounter = 10;
        this.lastActionTicks = 0;
    }

    private String buildAuctionCommand() {
        if (this.snipeMode.get() == SnipeMode.MULTI) {
            return "ah";
        }
        String customName = this.targetItemName.get();
        if (customName != null && !customName.trim().isEmpty()) {
            return "ah " + customName.trim();
        }
        StringBuilder command = new StringBuilder("ah ");
        String itemName = this.getFormattedItemName(this.snipingItem.get());
        command.append(itemName);
        if (this.enchantmentMode.get() && !this.requiredEnchantments.get().isEmpty()) {
            for (String enchantment : this.requiredEnchantments.get()) {
                command.append(" ").append(enchantment);
            }
        }
        return command.toString();
    }

    private void processSixRowAuction(GenericContainerScreenHandler handler) {
        if (this.pageJustRefreshed) return;
        if (this.purchaseAttempted || this.waitingForConfirmation || this.hasClickedBuy) return;

        for (int i = 0; i < 44; ++i) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isOf(this.snipingItem.get())) {
                if (this.isShulkerBox(stack)) continue;
                if (this.hasCursedEnchantments(stack)) continue;

                double currentItemPrice = this.getActualPrice(stack);
                if (this.isValidAuctionItem(stack) && currentItemPrice != -1.0) {
                    if (this.priceMode.get() == PriceMode.PER_STACK && stack.getCount() < this.getExpectedStackSize(stack.getItem())) {
                        if (this.notifications.get()) {
                            this.info("Skipping %s - not a full stack (%d/%d)", this.snipingItem.get().getName().getString(), stack.getCount(), this.getExpectedStackSize(stack.getItem()));
                        }
                        continue;
                    }

                    if (this.isProcessing) {
                        this.currentSnipedItem = this.snipingItem.get();
                        this.attemptedItemName = this.snipingItem.get().getName().getString();
                        this.attemptedActualPrice = currentItemPrice;
                        this.attemptedQuantity = stack.getCount();
                        this.attemptedEnchantments = this.getEnchantmentsString(stack);
                        this.mc.interactionManager.clickSlot(handler.syncId, i, 1, SlotActionType.QUICK_MOVE, this.mc.player);
                        this.isProcessing = false;
                        this.hasClickedBuy = true;
                        this.lastClickedSlot = i;
                        this.purchaseAttempted = true;
                        this.purchaseTimestamp = System.currentTimeMillis();
                        this.inventoryCheckTicks = 0;
                        this.purchaseTimeoutTicks = 0;
                        this.lastActionTicks = 0;
                        if (this.notifications.get()) {
                            this.info("Attempting to buy %dx %s!", this.attemptedQuantity, this.attemptedItemName);
            }
            return;
                    } else {
                        this.isProcessing = true;
                        this.delayCounter = 0;
                        return;
                    }
                }
            }
        }

        if (!this.isProcessing && !this.pageJustRefreshed && !this.purchaseAttempted && !this.hasClickedBuy) {
            this.mc.interactionManager.clickSlot(handler.syncId, 49, 1, SlotActionType.QUICK_MOVE, this.mc.player);
            this.navigationDelayCounter = 1;
            this.hasClickedBuy = false;
            this.lastClickedSlot = -1;
            this.pageJustRefreshed = true;
            this.lastActionTicks = 0;
            if (this.notifications.get()) {
                this.info("Refreshing to next page in %d ticks...", 1);
            }
        }
    }

    private void processThreeRowAuction(GenericContainerScreenHandler handler) {
        if (this.purchaseAttempted || this.waitingForConfirmation || this.hasClickedBuy) return;

        ItemStack auctionItem = handler.getSlot(13).getStack();
        if (auctionItem.isOf(this.snipingItem.get())) {
            if (this.isShulkerBox(auctionItem)) return;
            if (this.hasCursedEnchantments(auctionItem)) return;

            double currentItemPrice = this.getActualPrice(auctionItem);
            if (this.isValidAuctionItem(auctionItem) && currentItemPrice != -1.0) {
                if (this.priceMode.get() == PriceMode.PER_STACK && auctionItem.getCount() < this.getExpectedStackSize(auctionItem.getItem())) {
                    if (this.notifications.get()) {
                        this.info("Skipping %s - not a full stack (%d/%d)", this.snipingItem.get().getName().getString(), auctionItem.getCount(), this.getExpectedStackSize(auctionItem.getItem()));
            }
            return;
        }

                this.currentSnipedItem = this.snipingItem.get();
                this.attemptedItemName = auctionItem.getItem().getName().getString();
                this.attemptedActualPrice = currentItemPrice;
                this.attemptedQuantity = auctionItem.getCount();
                this.attemptedEnchantments = this.getEnchantmentsString(auctionItem);
                this.mc.interactionManager.clickSlot(handler.syncId, 15, 1, SlotActionType.QUICK_MOVE, this.mc.player);
                this.hasClickedBuy = true;
                this.purchaseAttempted = true;
                this.purchaseTimestamp = System.currentTimeMillis();
                this.inventoryCheckTicks = 0;
                this.purchaseTimeoutTicks = 0;
                this.isProcessing = false;
                this.lastActionTicks = 0;
                if (this.notifications.get()) {
                    this.info("Buying %dx %s!", auctionItem.getCount(), this.attemptedItemName);
                }
            }
        }
    }

    private boolean isShulkerBox(ItemStack stack) {
        String itemName = stack.getItem().getName().getString().toLowerCase();
        if (itemName.contains("shulker")) return true;
        Item item = stack.getItem();
        return item == Items.SHULKER_BOX || item == Items.WHITE_SHULKER_BOX ||
            item == Items.ORANGE_SHULKER_BOX || item == Items.MAGENTA_SHULKER_BOX ||
            item == Items.LIGHT_BLUE_SHULKER_BOX || item == Items.YELLOW_SHULKER_BOX ||
            item == Items.LIME_SHULKER_BOX || item == Items.PINK_SHULKER_BOX ||
            item == Items.GRAY_SHULKER_BOX || item == Items.LIGHT_GRAY_SHULKER_BOX ||
            item == Items.CYAN_SHULKER_BOX || item == Items.PURPLE_SHULKER_BOX ||
            item == Items.BLUE_SHULKER_BOX || item == Items.BROWN_SHULKER_BOX ||
            item == Items.GREEN_SHULKER_BOX || item == Items.RED_SHULKER_BOX ||
            item == Items.BLACK_SHULKER_BOX;
    }

    private boolean hasCursedEnchantments(ItemStack stack) {
        String enchantStr = stack.getEnchantments().toString().toLowerCase();
        return enchantStr.contains("vanishing_curse") || enchantStr.contains("binding_curse");
    }

    private double parseSelfDestructTime(ItemStack stack) {
        List<Text> tooltip = stack.getTooltip(Item.TooltipContext.create(this.mc.world), this.mc.player, TooltipType.BASIC);
        StringBuilder full = new StringBuilder();
            for (Text line : tooltip) {
            full.append("\n").append(line.getString());
        }
        String fullStr = full.toString().toLowerCase();
        Pattern p = Pattern.compile("self\\s*destruct[:\\s]*([\\d\\s+d+h+m+s]+)");
        Matcher m = p.matcher(fullStr);
        if (m.find()) {
            return this.parseTimeString(m.group(1).trim());
        }
        return -1.0;
    }

    private double parseTimeString(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) return -1.0;
        double totalHours = 0.0;
        timeStr = timeStr.toLowerCase();
        boolean foundComponent = false;
        Matcher days = Pattern.compile("(\\d+)\\s*d").matcher(timeStr);
        if (days.find()) {
            totalHours += Double.parseDouble(days.group(1)) * 24.0;
            foundComponent = true;
        }
        Matcher hrs = Pattern.compile("(\\d+)\\s*h").matcher(timeStr);
        if (hrs.find()) {
            totalHours += Double.parseDouble(hrs.group(1));
            foundComponent = true;
        }
        return foundComponent ? totalHours : -1.0;
    }

    private void performAutoSell() {
        double price = this.parsePrice(this.sellPrice.get());
        if (price <= 0.0) {
            this.sellingPhase = false;
            return;
        }
        
        // Find the purchased item in inventory and move it to hand
        if (this.mc.player != null && this.currentSnipedItem != null) {
            int itemSlot = -1;
            // Search in inventory for the item
            for (int i = 0; i < this.mc.player.getInventory().size(); ++i) {
                ItemStack stack = this.mc.player.getInventory().getStack(i);
                if (stack.isOf(this.currentSnipedItem)) {
                    itemSlot = i;
                    break;
                }
            }
            
            if (itemSlot != -1) {
                // Swap item to main hand
                int handSlot = this.mc.player.getInventory().selectedSlot;
                if (itemSlot != handSlot && itemSlot < 9) {
                    // Item is in hotbar, just select it
                    this.mc.player.getInventory().selectedSlot = itemSlot;
                } else if (itemSlot >= 9) {
                    // Item is in inventory, swap with current hotbar slot
                    this.mc.interactionManager.clickSlot(0, itemSlot, handSlot, SlotActionType.SWAP, this.mc.player);
                }
            }
        }
        
        // Execute the sell command
        this.mc.getNetworkHandler().sendChatCommand(String.format("ah sell %d", (int) price));
        this.sellingPhase = false;
        this.navigationDelayCounter = 5;
        this.lastActionTicks = 0;
    }

    private boolean isValidAuctionItem(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(this.snipingItem.get())) return false;
        if (this.isShulkerBox(stack)) return false;
        if (this.hasCursedEnchantments(stack)) return false;

        if (this.filterLowTime.get()) {
            double timeLeft = this.parseSelfDestructTime(stack);
            if (timeLeft != -1.0 && timeLeft < this.minTimeHours.get()) return false;
        }

        if (this.enchantmentMode.get() && !this.requiredEnchantments.get().isEmpty() && !this.hasValidEnchantments(stack)) {
            return false;
        }

        Item.TooltipContext tooltipContext = Item.TooltipContext.create(this.mc.world);
        List<Text> tooltip = stack.getTooltip(tooltipContext, this.mc.player, TooltipType.BASIC);
        
        // Check user filter
        String sellerName = this.extractSellerName(tooltip);
        if (!this.isSellerAllowed(sellerName)) {
            if (this.notifications.get()) {
                this.info("Skipping item from blacklisted user: %s", sellerName);
            }
            return false;
        }

        double itemPrice = this.parseTooltipPrice(tooltip);
        double maxPriceValue = this.parsePrice(this.maxPrice.get());
        double minPriceValue = this.parsePrice(this.minPrice.get());

        if (maxPriceValue == -1.0) {
            if (this.notifications.get()) {
                ChatUtils.error("Invalid max price format!");
            }
            this.toggle();
            return false;
        }
        if (itemPrice == -1.0) return false;

        double comparisonPrice;
        if (this.priceMode.get() == PriceMode.PER_ITEM) {
            comparisonPrice = itemPrice / (double) stack.getCount();
        } else {
            comparisonPrice = itemPrice;
        }

        // Check minimum price
        if (minPriceValue > 0 && comparisonPrice < minPriceValue) {
            if (this.notifications.get()) {
                String mode = this.priceMode.get() == PriceMode.PER_ITEM ? "per item" : "per stack";
                this.info("Item price %s below minimum %s (%s)", this.formatPrice(comparisonPrice), this.formatPrice(minPriceValue), mode);
            }
            return false;
        }

        if (this.notifications.get()) {
            String mode = this.priceMode.get() == PriceMode.PER_ITEM ? "per item" : "per stack";
            String priceStr = this.formatPrice(comparisonPrice);
            String maxStr = this.formatPrice(maxPriceValue);
            String minStr = minPriceValue > 0 ? this.formatPrice(minPriceValue) : "none";
            boolean willBuy = comparisonPrice <= maxPriceValue && (minPriceValue <= 0 || comparisonPrice >= minPriceValue);
            this.info("Item: %dx %s | Price: %s (%s) | Min: %s | Max: %s | Will buy: %s",
                stack.getCount(), stack.getItem().getName().getString(),
                priceStr, mode, minStr, maxStr, willBuy ? "YES" : "NO");
        }

        return comparisonPrice <= maxPriceValue;
    }

    private String extractSellerName(List<Text> tooltip) {
        if (tooltip == null) return "";
        for (Text line : tooltip) {
            String text = line.getString();
            // Common patterns for seller name in auction house tooltips
            if (text.toLowerCase().contains("seller:") || text.toLowerCase().contains("sold by:")) {
                String[] parts = text.split(":");
                if (parts.length >= 2) {
                    return parts[1].trim();
                }
            }
            // Pattern: "Listed by <name>" or "Seller: <name>"
            Pattern sellerPattern = Pattern.compile("(?i)(?:seller|sold by|listed by)[:\\s]+(\\w+)");
            Matcher matcher = sellerPattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return "";
    }

    private boolean isSellerAllowed(String sellerName) {
        if (sellerName == null || sellerName.isEmpty()) return true; // If we can't determine seller, allow it
        
        // Check if seller is in blacklist
        for (String blacklisted : this.userBlacklist.get()) {
            if (blacklisted.equalsIgnoreCase(sellerName)) {
                // But if useAdminList is enabled and seller is an admin, allow it
                if (this.useAdminList.get()) {
                    AdminList adminList = Modules.get().get(AdminList.class);
                    if (adminList != null && adminList.isAdmin(sellerName)) {
                        return true;
                    }
                }
                return false;
            }
        }
        return true;
    }

    private boolean hasValidEnchantments(ItemStack stack) {
        if (this.requiredEnchantments.get().isEmpty()) return true;
        List<String> itemEnchantments = this.getItemEnchantments(stack);
        if (this.exactEnchantments.get()) {
            return this.hasExactEnchantments(itemEnchantments);
        }
        for (String requiredEnchant : this.requiredEnchantments.get()) {
            if (this.matchesEnchantment(itemEnchantments, requiredEnchant)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasExactEnchantments(List<String> itemEnchantments) {
        if (itemEnchantments.size() != this.requiredEnchantments.get().size()) return false;
        for (String requiredEnchant : this.requiredEnchantments.get()) {
            if (!this.matchesEnchantment(itemEnchantments, requiredEnchant)) return false;
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
                requiredLevel = this.parseRomanNumeral(parts[1]);
            }
        }

        for (String itemEnchant : itemEnchantments) {
            String itemEnchantLower = itemEnchant.toLowerCase();
            if (itemEnchantLower.contains(enchantName)) {
                if (requiredLevel == null) return true;
                int itemLevel = this.getEnchantmentLevel(itemEnchant);
                return itemLevel >= requiredLevel;
            }
        }
        return false;
    }

    private List<String> getItemEnchantments(ItemStack stack) {
        List<String> enchantments = new ArrayList<>();
        Item.TooltipContext tooltipContext = Item.TooltipContext.create(this.mc.world);
        List<Text> tooltip = stack.getTooltip(tooltipContext, this.mc.player, TooltipType.BASIC);

        for (Text line : tooltip) {
            String text = line.getString();
            if (text.matches(".*\\b(Sharpness|Protection|Efficiency|Fortune|Silk Touch|Unbreaking|Mending|Power|Punch|Flame|Infinity|Looting|Knockback|Fire Aspect|Smite|Bane of Arthropods|Sweeping Edge|Thorns|Respiration|Aqua Affinity|Depth Strider|Frost Walker|Feather Falling|Blast Protection|Projectile Protection|Fire Protection).*")) {
                enchantments.add(text.trim());
            }
        }
        return enchantments;
    }

    private String getEnchantmentsString(ItemStack stack) {
        List<String> enchants = this.getItemEnchantments(stack);
        if (this.debugMode.get()) {
            this.info("Debug: Found %d enchantments for %s", enchants.size(), stack.getItem().getName().getString());
            for (String enchant : enchants) {
                this.info("Debug: Enchantment: %s", enchant);
            }
        }
        return enchants.isEmpty() ? "None" : String.join("\n", enchants);
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
        Item.TooltipContext tooltipContext = Item.TooltipContext.create(this.mc.world);
        List<Text> tooltip = stack.getTooltip(tooltipContext, this.mc.player, TooltipType.BASIC);
        return this.parseTooltipPrice(tooltip);
    }

    private int countItemInInventory() {
        if (this.mc.player == null) return 0;
        int count = 0;
        Item targetItem = this.snipeMode.get() == SnipeMode.SINGLE ? this.snipingItem.get() : this.currentSnipedItem;
        if (targetItem == null || targetItem == Items.AIR) return 0;
        for (int i = 0; i < this.mc.player.getInventory().size(); ++i) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (stack.isOf(targetItem)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void handlePurchaseCheck() {
        ++this.inventoryCheckTicks;
        if (this.inventoryCheckTicks >= 10) {
            int currentItemCount = this.countItemInInventory();
            if (currentItemCount > this.previousItemCount) {
                this.lastActionTicks = 0;
                this.itemPickedUp = true;
                this.purchaseAttempted = false;
                this.purchaseTimeoutTicks = 0;
                this.hasClickedBuy = false;
                this.hasClickedConfirm = false;
                this.waitingForConfirmation = false;
                this.waitingToConfirm = false;

                int gained = currentItemCount - this.previousItemCount;

                if (this.notifications.get()) {
                    this.info("Purchase successful! Got %dx %s for %s", this.attemptedQuantity, this.attemptedItemName, this.formatPrice(this.attemptedActualPrice));
                }
                this.sendSuccessWebhook(this.attemptedItemName, this.attemptedActualPrice, gained, this.attemptedEnchantments);

                if (this.mc.player != null && this.mc.world != null) {
                    this.mc.world.playSound(this.mc.player, this.mc.player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
                }

                this.previousItemCount = currentItemCount;
                this.inventoryCheckTicks = 0;

                if (this.autoSell.get()) {
                    this.sellingPhase = true;
                    if (this.mc.currentScreen != null) {
                        this.mc.currentScreen.close();
                    }
                    this.sellingDelayCounter = SELL_DELAY_TICKS;
                }
            } else if (this.inventoryCheckTicks >= 50) {
                this.purchaseAttempted = false;
                this.inventoryCheckTicks = 0;
                this.purchaseTimeoutTicks = 0;
                this.hasClickedBuy = false;
                this.hasClickedConfirm = false;
                this.waitingForConfirmation = false;
                this.waitingToConfirm = false;
                if (this.notifications.get()) {
                    this.info("Purchase may have failed or item was outbid.");
                }
            }
        }
    }

    private boolean isConfirmationGUI(ScreenHandler screenHandler) {
        if (!(screenHandler instanceof GenericContainerScreenHandler handler)) return false;

        if (this.mc.currentScreen instanceof GenericContainerScreen screen) {
            String title = screen.getTitle().getString().toLowerCase();
            if (title.contains("confirm") || title.contains("sure") || title.contains("buy") || title.contains("purchase")) {
                return true;
            }
        }

        for (int i = 0; i < Math.min(handler.slots.size(), 54); ++i) {
            if (this.isConfirmButton(handler.getSlot(i).getStack())) {
                return true;
            }
        }

        return false;
    }

    public void info(String message, Object... args) {
        ChatUtils.info(String.format(message, args));
    }

    private void sendSuccessWebhook(String itemName, double actualPrice, int quantity, String enchantments) {
        if (!this.webhookEnabled.get() || this.webhookUrl.get().isEmpty()) {
            if (this.debugMode.get()) {
                this.info("Debug: Webhook not sent - Enabled: %s, URL set: %s", this.webhookEnabled.get(), !this.webhookUrl.get().isEmpty());
            }
            return;
        }

        if (this.debugMode.get()) {
            this.info("Debug: Creating webhook payload...");
            this.info("Debug: Item: %s, Quantity: %d, Price: %s, Enchants: %s", itemName, quantity, this.formatPrice(actualPrice), enchantments);
        }

        String jsonPayload = this.createSuccessEmbed(itemName, actualPrice, quantity, enchantments);
        this.sendWebhookMessage(jsonPayload, "Success");
    }

    private void sendWebhookMessage(String jsonPayload, String messageType) {
        try {
            if (this.debugMode.get()) {
                this.info("Debug: Sending %s webhook request...", messageType);
                this.info("Debug: Payload preview: %s", jsonPayload.substring(0, Math.min(jsonPayload.length(), 200)) + "...");
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.webhookUrl.get()))
                .header("Content-Type", "application/json")
                .header("User-Agent", "AH-Sniper/1.0")
                .timeout(Duration.ofSeconds(15L))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 204 && response.statusCode() != 200) {
                if (this.debugMode.get() || this.notifications.get()) {
                        ChatUtils.error("%s webhook failed - Status: %d", messageType, response.statusCode());
                }
                if (this.debugMode.get()) {
                    this.info("Debug: Response body: %s", response.body());
                }
            } else if (this.debugMode.get()) {
                this.info("%s webhook sent successfully - Status: %d", messageType, response.statusCode());
                    }
                } catch (Exception e) {
            if (this.debugMode.get() || this.notifications.get()) {
                    ChatUtils.error("%s webhook error: %s", messageType, e.getMessage());
            }
                    e.printStackTrace();
                }
    }

    private String createSuccessEmbed(String itemName, double actualPrice, int quantity, String enchantments) {
        String playerName = this.mc.player != null ? this.mc.player.getName().getString() : "Unknown";
        long timestamp = System.currentTimeMillis() / 1000L;

        String pingContent = "";
        if (this.selfPing.get() && !this.discordId.get().trim().isEmpty()) {
            pingContent = String.format("<@%s> ", this.discordId.get().trim());
        }

        String maxPriceStr;
        String priceModeStr;
        double maxPriceValue;

        if (this.snipeMode.get() == SnipeMode.MULTI && this.currentSnipedItem != null) {
            SnipeItemConfig config = null;
            for (SnipeItemConfig c : this.multiSnipeConfigs) {
                if (c.item == this.currentSnipedItem) {
                    config = c;
                    break;
                }
            }
            if (config != null) {
                maxPriceValue = this.parsePrice(config.maxPrice);
                maxPriceStr = this.formatPrice(maxPriceValue);
                priceModeStr = config.priceMode.toString();
            } else {
                maxPriceValue = actualPrice;
                maxPriceStr = this.formatPrice(actualPrice);
                priceModeStr = "Unknown";
            }
        } else {
            maxPriceValue = this.parsePrice(this.maxPrice.get());
            maxPriceStr = this.formatPrice(maxPriceValue);
            priceModeStr = this.priceMode.get().toString();
        }

        String actualPriceStr = this.formatPrice(actualPrice);
        double savings = maxPriceValue - actualPrice;
        String savingsStr = this.formatPrice(Math.abs(savings));
        String savingsPercentage = String.format("%.1f%%", savings / maxPriceValue * 100.0);

        String webhookUsernameHardcoded = "Glazed AH Sniper";
        String webhookAvatarUrlHardcoded = "https://i.imgur.com/OL2y1cr.png";
        String webhookThumbnailUrlHardcoded = "https://i.imgur.com/OL2y1cr.png";

        String messageContent = String.format("%s\ud83c\udfaf **%s** sniped **%dx %s** for **%s**!", pingContent, playerName, quantity, itemName, actualPriceStr);
        String description = String.format("\ud83d\udcb8 **Savings** of %s (**%s**)", savingsStr, savingsPercentage);

        String enchantValue;
        if (!enchantments.equals("None") && !enchantments.isEmpty()) {
            enchantValue = enchantments.trim();
        } else {
            enchantValue = "None";
        }

        String modeText = this.snipeMode.get() == SnipeMode.MULTI ? "Multi-Snipe" : "Single-Snipe";

        return String.format("{\"content\":\"%s\",\"username\":\"%s\",\"avatar_url\":\"%s\",\"embeds\":[{\"title\":\"Glazed AH Sniper Alert [%s]\",\"description\":\"%s\",\"color\":8388736,\"thumbnail\":{\"url\":\"%s\"},\"fields\":[{\"name\":\"\ud83d\udce6 Item\",\"value\":\"%s x%d\",\"inline\":true},{\"name\":\"\ud83d\udcb0 Purchase Price\",\"value\":\"%s\",\"inline\":true},{\"name\":\"\ud83d\udcb5 Max Price\",\"value\":\"%s (%s)\",\"inline\":true},{\"name\":\"\u2728 Enchantments\",\"value\":\"%s\",\"inline\":false},{\"name\":\"\u23f0 Time\",\"value\":\"<t:%d:R>\",\"inline\":true}],\"footer\":{\"text\":\"Glazed AH Sniper V2\"},\"timestamp\":\"%s\"}]}",
            this.escapeJson(messageContent), this.escapeJson(webhookUsernameHardcoded), this.escapeJson(webhookAvatarUrlHardcoded),
            modeText, this.escapeJson(description), this.escapeJson(webhookThumbnailUrlHardcoded),
            this.escapeJson(itemName), quantity, this.escapeJson(actualPriceStr),
            this.escapeJson(maxPriceStr), this.escapeJson(priceModeStr.toLowerCase()),
            this.escapeJson(enchantValue), timestamp, Instant.now().toString());
    }

    private void testWebhook() {
        if (!this.webhookEnabled.get() || this.webhookUrl.get().isEmpty()) {
            this.info("Debug: Cannot test webhook - not enabled or URL empty");
            return;
        }
        String testPayload = this.createSimpleTestMessage();
        this.sendWebhookMessage(testPayload, "Test");
    }

    private String createSimpleTestMessage() {
        String playerName = this.mc.player != null ? this.mc.player.getName().getString() : "Unknown";
        String webhookUsernameHardcoded = "Glazed AH Sniper";
        return String.format("{\"content\":\"Webhook Test - AH Sniper is working for **%s**!\",\"username\":\"%s\"}",
            this.escapeJson(playerName), this.escapeJson(webhookUsernameHardcoded));
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
        if (price >= 1_000_000_000.0) {
            return String.format("%.1fB", price / 1_000_000_000.0);
        } else if (price >= 1_000_000.0) {
            return String.format("%.1fM", price / 1_000_000.0);
        } else if (price >= 1_000.0) {
            return String.format("%.1fK", price / 1_000.0);
        } else {
            return String.format("%.0f", price);
        }
    }

    private double parseTooltipPrice(List<Text> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) return -1.0;

        Pattern[] pricePatterns = new Pattern[]{
            Pattern.compile("\\$([\\d,]+(?:\\.[\\d]+)?)([kmb])?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)price\\s*:\\s*([\\d,]+(?:\\.[\\d]+)?)([kmb])?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)buy\\s+for\\s*:\\s*([\\d,]+(?:\\.[\\d]+)?)([kmb])?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([\\d,]+(?:\\.[\\d]+)?)([kmb])?\\s*coins?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b([\\d,]+(?:\\.[\\d]+)?)([kmb])\\b", Pattern.CASE_INSENSITIVE)
        };

        for (Text line : tooltip) {
            String text = line.getString().replace(",", "").toLowerCase();
            if (text.contains("trillion") || text.contains(" t")) {
                return 999_999_999_999_999.0;
            }

            for (Pattern pattern : pricePatterns) {
                Matcher matcher = pattern.matcher(line.getString());
                if (matcher.find()) {
                    String numberStr = matcher.group(1).replace(",", "");
                    String suffix = "";
                    if (matcher.groupCount() >= 2 && matcher.group(2) != null) {
                        suffix = matcher.group(2).toLowerCase();
                    }
                    try {
                        double basePrice = Double.parseDouble(numberStr);
                        double multiplier = switch (suffix) {
                            case "k" -> 1_000.0;
                            case "m" -> 1_000_000.0;
                            case "b" -> 1_000_000_000.0;
                            default -> 1.0;
                        };
                        return basePrice * multiplier;
                    } catch (NumberFormatException e) {
                    }
                }
            }
        }
        return -1.0;
    }

    private double parsePrice(String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) return -1.0;
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

    public enum SnipeMode {
        SINGLE("Single"),
        MULTI("Multi-Snipe");

        private final String title;

        SnipeMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return this.title;
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
            return this.title;
        }
    }

    public static class SnipeItemConfig {
        public Item item;
        public String minPrice;
        public String maxPrice;
        public PriceMode priceMode;
        public List<String> enchantments;
        public boolean exactEnchantments;

        public SnipeItemConfig(Item item, String minPrice, String maxPrice, PriceMode priceMode) {
            this.item = item;
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
            this.priceMode = priceMode;
            this.enchantments = new ArrayList<>();
            this.exactEnchantments = false;
        }
    }
}
