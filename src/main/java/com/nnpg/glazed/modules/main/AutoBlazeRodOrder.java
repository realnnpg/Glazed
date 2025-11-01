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

public class AutoBlazeRodOrder extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private enum Stage {NONE, SHOP, SHOP_CATEGORY, SHOP_ITEM, SHOP_GLASS_PANE, SHOP_BUY_ONE, SHOP_CHECK_FULL, SHOP_EXIT, WAIT, ORDERS, ORDERS_SELECT, ORDERS_EXIT, ORDERS_CONFIRM, ORDERS_FINAL_EXIT, CYCLE_PAUSE}

    private Stage stage = Stage.NONE;
    private long stage_start = 0;
    private static final long WAIT_TIME_MS = 50;
    private int shell_move_index = 0;
    private long last_shell_move_time = 0;
    private int exit_count = 0;
    private int final_exit_count = 0;
    private long final_exit_start = 0;

    private final SettingGroup sg_general = settings.getDefaultGroup();

    private final Setting<String> min_price = sg_general.add(new StringSetting.Builder()
        .name("min-price")
        .description("Minimum price to deliver blaze rods for (supports K, M, B suffixes).")
        .defaultValue("350.00001")
        .build()
    );

    private final Setting<Boolean> notifications = sg_general.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show detailed price checking notifications.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> speed_mode = sg_general.add(new BoolSetting.Builder()
        .name("speed-mode")
        .description("Maximum speed mode - removes most delays (may be unstable).")
        .defaultValue(true)
        .build()
    );

    public AutoBlazeRodOrder() {
        super(GlazedAddon.CATEGORY, "AutoBlazeRodOrder", "Automatically buys and sells blaze rods in orders for profit (FAST MODE)");
    }

    @Override
    public void onActivate() {
        double parsed_price = parse_price(min_price.get());
        if (parsed_price == -1.0) {
            if (notifications.get()) {
                ChatUtils.error("Invalid minimum price format!");
            }
            toggle();
            return;
        }

        stage = Stage.SHOP;
        stage_start = System.currentTimeMillis();
        shell_move_index = 0;
        last_shell_move_time = 0;
        exit_count = 0;
        final_exit_count = 0;

        if (notifications.get()) {
            info("🚀 FAST AutoBlazeRod Order activated! Minimum: %s", min_price.get());
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
                stage = Stage.SHOP_CATEGORY;
                stage_start = now;
            }
            case SHOP_CATEGORY -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && is_shop_category(stack)) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.SHOP_ITEM;
                            stage_start = now;
                            return;
                        }
                    }
                    if (now - stage_start > (speed_mode.get() ? 1000 : 3000)) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP;
                        stage_start = now;
                    }
                }
            }
            case SHOP_ITEM -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && is_blaze_rod(stack) && slot.inventory != mc.player.getInventory()) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.SHOP_GLASS_PANE;
                            stage_start = now;
                            return;
                        }
                    }
                    if (now - stage_start > (speed_mode.get() ? 300 : 1000)) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP;
                        stage_start = now;
                    }
                }
            }
            case SHOP_GLASS_PANE -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();

                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && is_glass_pane(stack) && stack.getCount() == 64) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.SHOP_BUY_ONE;
                            stage_start = now;
                            return;
                        }
                    }

                    if (now - stage_start > (speed_mode.get() ? 300 : 1000)) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP;
                        stage_start = now;
                    }
                }
            }
            case SHOP_BUY_ONE -> {
                long wait_delay = speed_mode.get() ? 500 : 1000;
                if (now - stage_start >= wait_delay) {
                    if (mc.currentScreen instanceof GenericContainerScreen screen) {
                        ScreenHandler handler = screen.getScreenHandler();

                        for (Slot slot : handler.slots) {
                            ItemStack stack = slot.getStack();
                            if (!stack.isEmpty() && is_green_glass(stack) && stack.getCount() == 1) {
                                int max_clicks = speed_mode.get() ? 50 : 30;
                                for (int i = 0; i < max_clicks; i++) {
                                    mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                                    if (is_inventory_full()) break;
                                }
                                stage = Stage.SHOP_CHECK_FULL;
                                stage_start = now;
                                return;
                            }
                        }

                        if (now - stage_start > (speed_mode.get() ? 2000 : 3000)) {
                            stage = Stage.SHOP_GLASS_PANE;
                            stage_start = now;
                        }
                    }
                }
            }
            case SHOP_CHECK_FULL -> {
                mc.player.closeHandledScreen();
                stage = Stage.SHOP_EXIT;
                stage_start = now;
            }
            case SHOP_EXIT -> {
                if (mc.currentScreen == null) {
                    stage = Stage.WAIT;
                    stage_start = now;
                }
                if (now - stage_start > (speed_mode.get() ? 1000 : 5000)) {
                    mc.player.closeHandledScreen();
                    stage = Stage.SHOP;
                    stage_start = now;
                }
            }
            case WAIT -> {
                long wait_time = speed_mode.get() ? 25 : WAIT_TIME_MS;
                if (now - stage_start >= wait_time) {
                    ChatUtils.sendPlayerMsg("/orders blaze rod");
                    stage = Stage.ORDERS;
                    stage_start = now;
                }
            }
            case ORDERS -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && is_blaze_rod(stack)) {
                            double order_price = get_order_price(stack);
                            double min_price_value = parse_price(min_price.get());

                            if (order_price > 1500) {
                                continue;
                            }

                            if (order_price >= min_price_value) {
                                if (notifications.get()) {
                                    info("✅ Found blaze rod order: %s", format_price(order_price));
                                }
                                mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                                stage = Stage.ORDERS_SELECT;
                                stage_start = now;
                                shell_move_index = 0;
                                last_shell_move_time = 0;
                                return;
                            }
                        }
                    }
                    if (now - stage_start > (speed_mode.get() ? 2000 : 5000)) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP;
                        stage_start = now;
                    }
                }
            }
            case ORDERS_SELECT -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();

                    if (shell_move_index >= 36) {
                        mc.player.closeHandledScreen();
                        stage = Stage.ORDERS_CONFIRM;
                        stage_start = now;
                        shell_move_index = 0;
                        return;
                    }

                    long move_delay = speed_mode.get() ? 10 : 100;
                    if (now - last_shell_move_time >= move_delay) {
                        int batch_size = speed_mode.get() ? 3 : 1;

                        for (int batch = 0; batch < batch_size && shell_move_index < 36; batch++) {
                            ItemStack stack = mc.player.getInventory().getStack(shell_move_index);
                            if (is_blaze_rod(stack)) {
                                int player_slot_id = -1;
                                for (Slot slot : handler.slots) {
                                    if (slot.inventory == mc.player.getInventory() && slot.getIndex() == shell_move_index) {
                                        player_slot_id = slot.id;
                                        break;
                                    }
                                }

                                if (player_slot_id != -1) {
                                    mc.interactionManager.clickSlot(handler.syncId, player_slot_id, 0, SlotActionType.QUICK_MOVE, mc.player);
                                }
                            }
                            shell_move_index++;
                        }
                        last_shell_move_time = now;
                    }
                }
            }
            case ORDERS_EXIT -> {
                if (mc.currentScreen == null) {
                    exit_count++;
                    if (exit_count < 2) {
                        mc.player.closeHandledScreen();
                        stage_start = now;
                    } else {
                        exit_count = 0;
                        stage = Stage.ORDERS_CONFIRM;
                        stage_start = now;
                    }
                }
            }
            case ORDERS_CONFIRM -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && is_green_glass(stack)) {
                            for (int i = 0; i < (speed_mode.get() ? 15 : 5); i++) {
                                mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            }
                            stage = Stage.ORDERS_FINAL_EXIT;
                            stage_start = now;
                            final_exit_count = 0;
                            final_exit_start = now;
                            return;
                        }
                    }
                    if (now - stage_start > (speed_mode.get() ? 2000 : 5000)) {
                        mc.player.closeHandledScreen();
                        stage = Stage.SHOP;
                        stage_start = now;
                    }
                }
            }
            case ORDERS_FINAL_EXIT -> {
                long exit_delay = speed_mode.get() ? 50 : 200;

                if (final_exit_count == 0) {
                    if (System.currentTimeMillis() - final_exit_start >= exit_delay) {
                        mc.player.closeHandledScreen();
                        final_exit_count++;
                        final_exit_start = System.currentTimeMillis();
                    }
                } else if (final_exit_count == 1) {
                    if (System.currentTimeMillis() - final_exit_start >= exit_delay) {
                        mc.player.closeHandledScreen();
                        final_exit_count++;
                        final_exit_start = System.currentTimeMillis();
                    }
                } else {
                    final_exit_count = 0;
                    stage = Stage.CYCLE_PAUSE;
                    stage_start = System.currentTimeMillis();
                }
            }
            case CYCLE_PAUSE -> {
                long cycle_wait = speed_mode.get() ? 25 : WAIT_TIME_MS;
                if (now - stage_start >= cycle_wait) {
                    stage = Stage.SHOP;
                    stage_start = now;
                }
            }
            case NONE -> {
            }
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
            Pattern.compile("\\$([\\d,]+(?:\\.[\\d]+)?)([kmb])?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)price\\s*:\\s*([\\d,]+(?:\\.[\\d]+)?)([kmb])?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)pay\\s*:\\s*([\\d,]+(?:\\.[\\d]+)?)([kmb])?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)reward\\s*:\\s*([\\d,]+(?:\\.[\\d]+)?)([kmb])?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([\\d,]+(?:\\.[\\d]+)?)([kmb])?\\s*coins?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b([\\d,]+(?:\\.[\\d]+)?)([kmb])\\b", Pattern.CASE_INSENSITIVE)
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

    private boolean is_shop_category(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        return stack.getItem() == Items.NETHERRACK || name.contains("nether");
    }

    private boolean is_blaze_rod(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() == Items.BLAZE_ROD;
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
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) return false;
        }
        return true;
    }
}