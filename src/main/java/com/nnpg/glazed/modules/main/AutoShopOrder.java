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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoShopOrder extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    
    public enum ShopCategory { END, NETHER, GEAR, FOOD }
    
    public enum PriceMode { AUTO, CUSTOM }

    public enum EndItem {
        ENDER_CHEST, ENDER_PEARL, END_STONE, DRAGON_BREATH, END_ROD,
        CHORUS_FRUIT, POPPED_CHORUS_FRUIT, SHULKER_SHELL, SHULKER_BOX
    }

    public enum NetherItem {
        BLAZE_ROD, NETHER_WART, GLOWSTONE_DUST, MAGMA_CREAM, GHAST_TEAR,
        NETHER_QUARTZ, SOUL_SAND, MAGMA_BLOCK, CRYING_OBSIDIAN
    }

    public enum GearItem {
        OBSIDIAN, END_CRYSTAL, RESPAWN_ANCHOR, GLOWSTONE, TOTEM_OF_UNDYING,
        ENDER_PEARL, GOLDEN_APPLE, EXPERIENCE_BOTTLE, TIPPED_ARROW
    }

    public enum FoodItem {
        POTATO, SWEET_BERRIES, MELON_SLICE, CARROT, APPLE,
        COOKED_CHICKEN, COOKED_BEEF, GOLDEN_CARROT, GOLDEN_APPLE
    }

    
    
    private int getShopPrice() {
        return switch (category.get()) {
            case END -> switch (endItem.get()) {
                case ENDER_CHEST -> 2500;
                case ENDER_PEARL -> 75;
                case END_STONE -> 8;           
                case DRAGON_BREATH -> 1000;
                case END_ROD -> 100;
                case CHORUS_FRUIT -> 108;
                case POPPED_CHORUS_FRUIT -> 24;
                case SHULKER_SHELL -> 350;
                case SHULKER_BOX -> 800;
            };
            case NETHER -> switch (netherItem.get()) {
                case BLAZE_ROD -> 150;
                case NETHER_WART -> 96;
                case GLOWSTONE_DUST -> 15;
                case MAGMA_CREAM -> 96;
                case GHAST_TEAR -> 350;
                case NETHER_QUARTZ -> 30;
                case SOUL_SAND -> 50;
                case MAGMA_BLOCK -> 35;
                case CRYING_OBSIDIAN -> 150;
            };
            case GEAR -> switch (gearItem.get()) {
                case OBSIDIAN -> 100;
                case END_CRYSTAL -> 350;
                case RESPAWN_ANCHOR -> 1000;
                case GLOWSTONE -> 100;
                case TOTEM_OF_UNDYING -> 1500;
                case ENDER_PEARL -> 75;
                case GOLDEN_APPLE -> 250;
                case EXPERIENCE_BOTTLE -> 100;
                case TIPPED_ARROW -> 500;
            };
            case FOOD -> switch (foodItem.get()) {
                case POTATO -> 96;
                case SWEET_BERRIES -> 50;
                case MELON_SLICE -> 36;
                case CARROT -> 96;
                case APPLE -> 25;
                case COOKED_CHICKEN -> 48;
                case COOKED_BEEF -> 35;
                case GOLDEN_CARROT -> 120;
                case GOLDEN_APPLE -> 250;
            };
        };
    }

    
    private int getDefaultMinPrice() {
        return getShopPrice() + 1;
    }

    
    private enum Stage {
        NONE,
        SHOP_OPEN,
        SHOP_CATEGORY,
        SHOP_ITEM,
        SHOP_SET_STACK,
        SHOP_WAIT_FOR_BUY_SCREEN,
        SHOP_BUY_SPAM,
        SHOP_EXIT,
        WAIT,
        ORDERS_OPEN,
        ORDERS_SELECT,
        ORDERS_VERIFY_EMPTY,
        ORDERS_CONFIRM,
        ORDERS_FINAL_EXIT,
        CYCLE_PAUSE
    }

    private Stage stage = Stage.NONE;
    private long stage_start = 0;
    private static final long WAIT_TIME_MS = 50;
    private int final_exit_count = 0;
    private long final_exit_start = 0;
    private int confirm_slot_id = -1;
    private int buy_screen_retry_count = 0;
    private static final int MAX_BUY_RETRIES = 20;
    
    
    private long last_action_time = 0;
    private static final long STUCK_TIMEOUT_MS = 5000; 
    
    
    private long buy_spam_start_time = 0;
    private static final long BUY_SPAM_TIMEOUT_MS = 5000;
    
    
    private int move_pass_count = 0;
    private static final int MAX_MOVE_PASSES = 5;

    
    private final SettingGroup sg_general = settings.getDefaultGroup();
    private final SettingGroup sg_blacklist = settings.createGroup("Blacklist");

    private final Setting<ShopCategory> category = sg_general.add(new EnumSetting.Builder<ShopCategory>()
        .name("category")
        .description("Select the shop category.")
        .defaultValue(ShopCategory.FOOD)
        .build()
    );

    private final Setting<EndItem> endItem = sg_general.add(new EnumSetting.Builder<EndItem>()
        .name("end-item")
        .description("Select item from END category.")
        .defaultValue(EndItem.SHULKER_SHELL)
        .visible(() -> category.get() == ShopCategory.END)
        .build()
    );

    private final Setting<NetherItem> netherItem = sg_general.add(new EnumSetting.Builder<NetherItem>()
        .name("nether-item")
        .description("Select item from NETHER category.")
        .defaultValue(NetherItem.BLAZE_ROD)
        .visible(() -> category.get() == ShopCategory.NETHER)
        .build()
    );

    private final Setting<GearItem> gearItem = sg_general.add(new EnumSetting.Builder<GearItem>()
        .name("gear-item")
        .description("Select item from GEAR category.")
        .defaultValue(GearItem.TOTEM_OF_UNDYING)
        .visible(() -> category.get() == ShopCategory.GEAR)
        .build()
    );

    private final Setting<FoodItem> foodItem = sg_general.add(new EnumSetting.Builder<FoodItem>()
        .name("food-item")
        .description("Select item from FOOD category.")
        .defaultValue(FoodItem.COOKED_CHICKEN)
        .visible(() -> category.get() == ShopCategory.FOOD)
        .build()
    );

    private final Setting<PriceMode> priceMode = sg_general.add(new EnumSetting.Builder<PriceMode>()
        .name("min-price")
        .description("Auto uses shop_price + $1, Custom lets you set your own.")
        .defaultValue(PriceMode.AUTO)
        .build()
    );

    private final Setting<String> customPrice = sg_general.add(new StringSetting.Builder()
        .name("custom-price")
        .description("Custom minimum price (supports K, M, B suffixes).")
        .defaultValue("50")
        .visible(() -> priceMode.get() == PriceMode.CUSTOM)
        .build()
    );

    private final Setting<Integer> click_delay = sg_general.add(new IntSetting.Builder()
        .name("click-delay")
        .description("Delay in milliseconds between GUI clicks (except confirm spam).")
        .defaultValue(50)
        .min(0)
        .max(500)
        .sliderMax(200)
        .build()
    );

    private final Setting<Boolean> notifications = sg_general.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show detailed notifications.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> blacklisted_players = sg_blacklist.add(new StringListSetting.Builder()
        .name("blacklisted-players")
        .description("Players whose orders will be ignored.")
        .defaultValue(List.of())
        .build()
    );

    public AutoShopOrder() {
        super(GlazedAddon.CATEGORY, "Auto Shop Order", "Auto Shop Order - Buys items from shop and delivers to orders automatically.");
    }

    
    private double getEffectiveMinPrice() {
        if (priceMode.get() == PriceMode.AUTO) {
            return getDefaultMinPrice();
        }
        
        
        String priceStr = customPrice.get().trim();
        double parsed = parse_price(priceStr);
        if (parsed < 0) {
            return getDefaultMinPrice();
        }
        return parsed;
    }

    @Override
    public void onActivate() {
        double effective_min = getEffectiveMinPrice();
        
        stage = Stage.SHOP_OPEN;
        stage_start = System.currentTimeMillis();
        last_action_time = System.currentTimeMillis();
        final_exit_count = 0;
        confirm_slot_id = -1;
        buy_screen_retry_count = 0;
        buy_spam_start_time = 0;
        move_pass_count = 0;

        if (notifications.get()) {
            info("AutoShopOrder activated! Item: %s | Shop: $%d | Min: %s", 
                getSearchKeyword(), getShopPrice(), format_price(effective_min));
        }
    }

    @Override
    public void onDeactivate() {
        stage = Stage.NONE;
    }

    
    private void recordAction() {
        last_action_time = System.currentTimeMillis();
    }

    
    private boolean checkAndHandleStuck() {
        if (mc.currentScreen instanceof GenericContainerScreen) {
            long now = System.currentTimeMillis();
            if (now - last_action_time > STUCK_TIMEOUT_MS) {
                if (notifications.get()) {
                    info("Stuck detected - resetting...");
                }
                mc.player.closeHandledScreen();
                stage = Stage.SHOP_OPEN;
                stage_start = now;
                last_action_time = now;
                return true;
            }
        }
        return false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        long now = System.currentTimeMillis();

        
        if (checkAndHandleStuck()) return;

        switch (stage) {
            
            case SHOP_OPEN -> {
                ChatUtils.sendPlayerMsg("/shop");
                stage = Stage.SHOP_CATEGORY;
                stage_start = now;
                recordAction();
            }

            case SHOP_CATEGORY -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    if (now - stage_start < click_delay.get()) return;

                    ScreenHandler handler = screen.getScreenHandler();
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && isCategoryIcon(stack)) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.SHOP_ITEM;
                            stage_start = now;
                            recordAction();
                            return;
                        }
                    }
                    if (now - stage_start > 3000) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP_OPEN;
                        stage_start = now;
                        recordAction();
                    }
                }
            }

            case SHOP_ITEM -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    if (now - stage_start < click_delay.get()) return;

                    ScreenHandler handler = screen.getScreenHandler();
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && isTargetItem(stack) && slot.inventory != mc.player.getInventory()) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            
                            
                            if (!isTargetItemStackable()) {
                                stage = Stage.SHOP_WAIT_FOR_BUY_SCREEN;
                                stage_start = now;
                                confirm_slot_id = -1;
                                buy_screen_retry_count = 0;
                                recordAction();
                            } else {
                                stage = Stage.SHOP_SET_STACK;
                                stage_start = now;
                                recordAction();
                            }
                            return;
                        }
                    }
                    if (now - stage_start > 1000) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP_OPEN;
                        stage_start = now;
                        recordAction();
                    }
                }
            }

            case SHOP_SET_STACK -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    if (now - stage_start < click_delay.get()) return;

                    ScreenHandler handler = screen.getScreenHandler();
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && is_glass_pane(stack) && stack.getCount() == 64) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.SHOP_WAIT_FOR_BUY_SCREEN;
                            stage_start = now;
                            confirm_slot_id = -1;
                            buy_screen_retry_count = 0;
                            recordAction();
                            return;
                        }
                    }
                    if (now - stage_start > 1000) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP_OPEN;
                        stage_start = now;
                        recordAction();
                    }
                }
            }

            case SHOP_WAIT_FOR_BUY_SCREEN -> {
                if (now - stage_start < 50) return;

                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && is_green_glass(stack) && stack.getCount() == 1) {
                            confirm_slot_id = slot.id;
                            stage = Stage.SHOP_BUY_SPAM;
                            stage_start = now;
                            buy_spam_start_time = now;
                            recordAction();
                            return;
                        }
                    }

                    buy_screen_retry_count++;
                    if (buy_screen_retry_count > MAX_BUY_RETRIES) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP_OPEN;
                        stage_start = now;
                        recordAction();
                    }
                }
            }

            case SHOP_BUY_SPAM -> {
                
                if (now - buy_spam_start_time > BUY_SPAM_TIMEOUT_MS) {
                    mc.player.closeHandledScreen();
                    stage = Stage.SHOP_OPEN;
                    stage_start = now;
                    recordAction();
                    return;
                }

                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
                    stage = Stage.SHOP_OPEN;
                    stage_start = now;
                    recordAction();
                    return;
                }

                ScreenHandler handler = screen.getScreenHandler();

                
                if (confirm_slot_id == -1) {
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && is_green_glass(stack) && stack.getCount() == 1) {
                            confirm_slot_id = slot.id;
                            break;
                        }
                    }
                }

                if (confirm_slot_id != -1) {
                    
                    if (is_inventory_full()) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP_EXIT;
                        stage_start = now;
                        recordAction();
                        return;
                    }

                    
                    mc.interactionManager.clickSlot(handler.syncId, confirm_slot_id, 0, SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(handler.syncId, confirm_slot_id, 0, SlotActionType.PICKUP, mc.player);
                    recordAction();
                } else {
                    
                    buy_screen_retry_count++;
                    if (buy_screen_retry_count > MAX_BUY_RETRIES) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP_OPEN;
                        stage_start = now;
                        recordAction();
                    }
                }
            }

            case SHOP_EXIT -> {
                if (mc.currentScreen == null) {
                    stage = Stage.WAIT;
                    stage_start = now;
                    recordAction();
                }
                if (now - stage_start > 3000) {
                    mc.player.closeHandledScreen();
                    stage = Stage.SHOP_OPEN;
                    stage_start = now;
                    recordAction();
                }
            }

            
            case WAIT -> {
                if (now - stage_start >= click_delay.get()) {
                    ChatUtils.sendPlayerMsg("/orders " + getSearchKeyword());
                    stage = Stage.ORDERS_OPEN;
                    stage_start = now;
                    recordAction();
                }
            }

            case ORDERS_OPEN -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    if (now - stage_start < click_delay.get()) return;

                    ScreenHandler handler = screen.getScreenHandler();
                    
                    
                    Slot best_order = null;
                    double best_price = -1;
                    double min_price_value = getEffectiveMinPrice();
                    
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && isTargetItem(stack)) {
                            String player_name = get_order_player_name(stack);
                            if (is_blacklisted(player_name)) continue;
                            
                            double order_price = get_order_price(stack);

                            if (order_price >= min_price_value && order_price > best_price) {
                                best_price = order_price;
                                best_order = slot;
                            }
                        }
                    }
                    
                    if (best_order != null) {
                        if (notifications.get()) {
                            info("Found order: %s", format_price(best_price));
                        }
                        mc.interactionManager.clickSlot(handler.syncId, best_order.id, 0, SlotActionType.PICKUP, mc.player);
                        stage = Stage.ORDERS_SELECT;
                        stage_start = now;
                        move_pass_count = 0;
                        recordAction();
                        return;
                    }
                    
                    if (now - stage_start > 3000) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP_OPEN;
                        stage_start = now;
                        recordAction();
                    }
                }
            }

            
            case ORDERS_SELECT -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();

                    
                    List<Integer> slots_to_move = new ArrayList<>();
                    
                    for (Slot slot : handler.slots) {
                        
                        if (slot.inventory == mc.player.getInventory()) {
                            int slot_index = slot.getIndex();
                            
                            
                            if (slot_index >= 0 && slot_index < 36) {
                                ItemStack stack = slot.getStack();
                                if (isTargetItem(stack)) {
                                    slots_to_move.add(slot.id);
                                }
                            }
                        }
                    }

                    if (slots_to_move.isEmpty()) {
                        
                        mc.player.closeHandledScreen();
                        stage = Stage.ORDERS_CONFIRM;
                        stage_start = now;
                        recordAction();
                        return;
                    }

                    
                    for (int slot_id : slots_to_move) {
                        mc.interactionManager.clickSlot(handler.syncId, slot_id, 0, SlotActionType.QUICK_MOVE, mc.player);
                    }
                    
                    move_pass_count++;
                    recordAction();

                    
                    if (move_pass_count >= MAX_MOVE_PASSES) {
                        
                        mc.player.closeHandledScreen();
                        stage = Stage.ORDERS_CONFIRM;
                        stage_start = now;
                        recordAction();
                    }
                    
                }
            }

            case ORDERS_CONFIRM -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    if (now - stage_start < click_delay.get()) return;

                    ScreenHandler handler = screen.getScreenHandler();
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && is_green_glass(stack)) {
                            for (int i = 0; i < 5; i++) {
                                mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            }
                            stage = Stage.ORDERS_FINAL_EXIT;
                            stage_start = now;
                            final_exit_count = 0;
                            final_exit_start = now;
                            recordAction();
                            return;
                        }
                    }
                    if (now - stage_start > 3000) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP_OPEN;
                        stage_start = now;
                        recordAction();
                    }
                }
            }

            case ORDERS_FINAL_EXIT -> {
                long exit_delay = click_delay.get();

                if (final_exit_count == 0) {
                    if (System.currentTimeMillis() - final_exit_start >= exit_delay) {
                        mc.player.closeHandledScreen();
                        final_exit_count++;
                        final_exit_start = System.currentTimeMillis();
                        recordAction();
                    }
                } else if (final_exit_count == 1) {
                    if (System.currentTimeMillis() - final_exit_start >= exit_delay) {
                        mc.player.closeHandledScreen();
                        final_exit_count++;
                        final_exit_start = System.currentTimeMillis();
                        recordAction();
                    }
                } else {
                    final_exit_count = 0;
                    stage = Stage.CYCLE_PAUSE;
                    stage_start = System.currentTimeMillis();
                    recordAction();
                }
            }

            case CYCLE_PAUSE -> {
                if (now - stage_start >= WAIT_TIME_MS) {
                    stage = Stage.SHOP_OPEN;
                    stage_start = now;
                    recordAction();
                }
            }

            case NONE -> {}
        }
    }

    

    private boolean isCategoryIcon(ItemStack stack) {
        String name = stack.getName().getString().toLowerCase();
        return switch (category.get()) {
            case END -> name.contains("ᴇɴᴅ") || name.contains("end") || stack.getItem() == Items.END_STONE;
            case NETHER -> name.contains("ɴᴇᴛʜᴇʀ") || name.contains("nether") || stack.getItem() == Items.NETHERRACK;
            case GEAR -> name.contains("ɢᴇᴀʀ") || name.contains("gear") || stack.getItem() == Items.TOTEM_OF_UNDYING;
            case FOOD -> name.contains("ꜰᴏᴏᴅ") || name.contains("food") || stack.getItem() == Items.COOKED_BEEF;
        };
    }

    private boolean isTargetItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == getTargetMcItem();
    }

    
    private boolean isTargetItemStackable() {
        Item item = getTargetMcItem();
        return item.getMaxCount() > 1;
    }

    
    private boolean hasTargetItemsInInventory() {
        for (int i = 0; i < 36; i++) {
            if (isTargetItem(mc.player.getInventory().getStack(i))) {
                return true;
            }
        }
        return false;
    }

    private Item getTargetMcItem() {
        return switch (category.get()) {
            case END -> switch (endItem.get()) {
                case ENDER_CHEST -> Items.ENDER_CHEST;
                case ENDER_PEARL -> Items.ENDER_PEARL;
                case END_STONE -> Items.END_STONE;
                case DRAGON_BREATH -> Items.DRAGON_BREATH;
                case END_ROD -> Items.END_ROD;
                case CHORUS_FRUIT -> Items.CHORUS_FRUIT;
                case POPPED_CHORUS_FRUIT -> Items.POPPED_CHORUS_FRUIT;
                case SHULKER_SHELL -> Items.SHULKER_SHELL;
                case SHULKER_BOX -> Items.SHULKER_BOX;
            };
            case NETHER -> switch (netherItem.get()) {
                case BLAZE_ROD -> Items.BLAZE_ROD;
                case NETHER_WART -> Items.NETHER_WART;
                case GLOWSTONE_DUST -> Items.GLOWSTONE_DUST;
                case MAGMA_CREAM -> Items.MAGMA_CREAM;
                case GHAST_TEAR -> Items.GHAST_TEAR;
                case NETHER_QUARTZ -> Items.QUARTZ;
                case SOUL_SAND -> Items.SOUL_SAND;
                case MAGMA_BLOCK -> Items.MAGMA_BLOCK;
                case CRYING_OBSIDIAN -> Items.CRYING_OBSIDIAN;
            };
            case GEAR -> switch (gearItem.get()) {
                case OBSIDIAN -> Items.OBSIDIAN;
                case END_CRYSTAL -> Items.END_CRYSTAL;
                case RESPAWN_ANCHOR -> Items.RESPAWN_ANCHOR;
                case GLOWSTONE -> Items.GLOWSTONE;
                case TOTEM_OF_UNDYING -> Items.TOTEM_OF_UNDYING;
                case ENDER_PEARL -> Items.ENDER_PEARL;
                case GOLDEN_APPLE -> Items.GOLDEN_APPLE;
                case EXPERIENCE_BOTTLE -> Items.EXPERIENCE_BOTTLE;
                case TIPPED_ARROW -> Items.TIPPED_ARROW;
            };
            case FOOD -> switch (foodItem.get()) {
                case POTATO -> Items.POTATO;
                case SWEET_BERRIES -> Items.SWEET_BERRIES;
                case MELON_SLICE -> Items.MELON_SLICE;
                case CARROT -> Items.CARROT;
                case APPLE -> Items.APPLE;
                case COOKED_CHICKEN -> Items.COOKED_CHICKEN;
                case COOKED_BEEF -> Items.COOKED_BEEF;
                case GOLDEN_CARROT -> Items.GOLDEN_CARROT;
                case GOLDEN_APPLE -> Items.GOLDEN_APPLE;
            };
        };
    }

    private String getSearchKeyword() {
        return switch (category.get()) {
            case END -> switch (endItem.get()) {
                case ENDER_CHEST -> "ender chest";
                case ENDER_PEARL -> "ender pearl";
                case END_STONE -> "end stone";
                case DRAGON_BREATH -> "dragon breath";
                case END_ROD -> "end rod";
                case CHORUS_FRUIT -> "chorus fruit";
                case POPPED_CHORUS_FRUIT -> "popped chorus fruit";
                case SHULKER_SHELL -> "shulker shell";
                case SHULKER_BOX -> "shulker box";
            };
            case NETHER -> switch (netherItem.get()) {
                case BLAZE_ROD -> "blaze rod";
                case NETHER_WART -> "nether wart";
                case GLOWSTONE_DUST -> "glowstone dust";
                case MAGMA_CREAM -> "magma cream";
                case GHAST_TEAR -> "ghast tear";
                case NETHER_QUARTZ -> "quartz";
                case SOUL_SAND -> "soul sand";
                case MAGMA_BLOCK -> "magma block";
                case CRYING_OBSIDIAN -> "crying obsidian";
            };
            case GEAR -> switch (gearItem.get()) {
                case OBSIDIAN -> "obsidian";
                case END_CRYSTAL -> "end crystal";
                case RESPAWN_ANCHOR -> "respawn anchor";
                case GLOWSTONE -> "glowstone";
                case TOTEM_OF_UNDYING -> "totem of undying";
                case ENDER_PEARL -> "ender pearl";
                case GOLDEN_APPLE -> "golden apple";
                case EXPERIENCE_BOTTLE -> "experience bottle";
                case TIPPED_ARROW -> "tipped arrow";
            };
            case FOOD -> switch (foodItem.get()) {
                case POTATO -> "potato";
                case SWEET_BERRIES -> "sweet berries";
                case MELON_SLICE -> "melon slice";
                case CARROT -> "carrot";
                case APPLE -> "apple";
                case COOKED_CHICKEN -> "cooked chicken";
                case COOKED_BEEF -> "cooked beef";
                case GOLDEN_CARROT -> "golden carrot";
                case GOLDEN_APPLE -> "golden apple";
            };
        };
    }

    private boolean is_glass_pane(ItemStack stack) {
        String item_name = stack.getItem().getName().getString().toLowerCase();
        return item_name.contains("glass") && item_name.contains("pane");
    }

    private boolean is_green_glass(ItemStack stack) {
        return stack.getItem() == Items.LIME_STAINED_GLASS_PANE || stack.getItem() == Items.GREEN_STAINED_GLASS_PANE;
    }

    private boolean is_inventory_full() {
        for (int i = 9; i <= 35; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return false;
        }
        return true;
    }

    

    private double parse_price(String price_str) {
        if (price_str == null || price_str.isEmpty()) {
            return -1.0;
        }

        String cleaned = price_str.trim().toLowerCase().replace(",", "");
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

    private String format_price(double price) {
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

    private double get_order_price(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1.0;
        }

        Item.TooltipContext tooltip_context = Item.TooltipContext.create(mc.world);
        List<Text> tooltip = stack.getTooltip(tooltip_context, mc.player, TooltipType.BASIC);

        return parse_tooltip_price(tooltip);
    }

    private double parse_tooltip_price(List<Text> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            return -1.0;
        }

        
        Pattern[] price_patterns = {
            Pattern.compile("\\$([\\d,]+(?:\\.\\d+)?)([kmbKMB])?\\s*each", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\$([\\d,]+(?:\\.\\d+)?)([kmbKMB])?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)price\\s*:\\s*([\\d,]+(?:\\.\\d+)?)([kmbKMB])?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)pay\\s*:\\s*([\\d,]+(?:\\.\\d+)?)([kmbKMB])?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)reward\\s*:\\s*([\\d,]+(?:\\.\\d+)?)([kmbKMB])?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([\\d,]+(?:\\.\\d+)?)([kmbKMB])?\\s*coins?", Pattern.CASE_INSENSITIVE)
        };

        for (Text line : tooltip) {
            String text = line.getString();

            for (Pattern pattern : price_patterns) {
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    String number_str = matcher.group(1).replace(",", "");
                    String suffix = "";
                    if (matcher.groupCount() >= 2 && matcher.group(2) != null) {
                        suffix = matcher.group(2).toLowerCase();
                    }

                    try {
                        double base_price = Double.parseDouble(number_str);
                        double multiplier = 1.0;

                        switch (suffix) {
                            case "k" -> multiplier = 1_000.0;
                            case "m" -> multiplier = 1_000_000.0;
                            case "b" -> multiplier = 1_000_000_000.0;
                        }

                        return base_price * multiplier;
                    } catch (NumberFormatException e) {
                        
                    }
                }
            }
        }

        return -1.0;
    }

    

    private boolean is_blacklisted(String playerName) {
        if (playerName == null || blacklisted_players.get().isEmpty()) return false;
        return blacklisted_players.get().stream().anyMatch(p -> p.equalsIgnoreCase(playerName));
    }

    private String get_order_player_name(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Item.TooltipContext ctx = Item.TooltipContext.create(mc.world);
        List<Text> tooltip = stack.getTooltip(ctx, mc.player, TooltipType.BASIC);
        
        
        Pattern deliver_pattern = Pattern.compile("(?i)click to deliver\\s+\\.?([a-zA-Z0-9_]+)");
        
        
        Pattern[] patterns = {
            deliver_pattern,
            Pattern.compile("(?i)player\\s*:\\s*([a-zA-Z0-9_]+)"),
            Pattern.compile("(?i)from\\s*:\\s*([a-zA-Z0-9_]+)"),
            Pattern.compile("(?i)by\\s*:\\s*([a-zA-Z0-9_]+)"),
            Pattern.compile("(?i)seller\\s*:\\s*([a-zA-Z0-9_]+)"),
            Pattern.compile("(?i)owner\\s*:\\s*([a-zA-Z0-9_]+)")
        };
        
        for (Text line : tooltip) {
            String text = line.getString();
            for (Pattern p : patterns) {
                Matcher m = p.matcher(text);
                if (m.find()) {
                    String name = m.group(1);
                    if (name.length() >= 3 && name.length() <= 16) return name;
                }
            }
        }
        return null;
    }
}