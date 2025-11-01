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

public class AutoShellOrder extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private enum Stage {NONE, SHOP, SHOP_END, SHOP_SHELL, SHOP_CONFIRM, SHOP_CHECK_FULL, SHOP_EXIT, WAIT, ORDERS, ORDERS_SELECT, ORDERS_EXIT, ORDERS_CONFIRM, ORDERS_FINAL_EXIT, CYCLE_PAUSE, TARGET_ORDERS}

    private Stage stage = Stage.NONE;
    private long stageStart = 0;
    private static final long WAIT_TIME_MS = 50;
    private int shellMoveIndex = 0;
    private long lastShellMoveTime = 0;
    private int exitCount = 0;
    private int finalExitCount = 0;
    private long finalExitStart = 0;

    // Player targeting variables
    private String targetPlayer = "";
    private boolean isTargetingActive = false;

    // Settings
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Player Targeting");

    private final Setting<String> minPrice = sgGeneral.add(new StringSetting.Builder()
        .name("min-price")
        .description("Minimum price to deliver shells for (supports K, M, B suffixes).")
        .defaultValue("50")
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

    // Targeting settings
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

    public AutoShellOrder() {
        super(GlazedAddon.CATEGORY, "AutoShellOrder", "Automatically buys shulker shells and sells them in orders with player targeting");
    }

    @Override
    public void onActivate() {
        double parsedPrice = parsePrice(minPrice.get());
        if (parsedPrice == -1.0 && !enableTargeting.get()) {
            if (notifications.get()) ChatUtils.error("Invalid minimum price format!");
            toggle();
            return;
        }

        updateTargetPlayer();
        stage = Stage.SHOP;
        stageStart = System.currentTimeMillis();
        shellMoveIndex = 0;
        lastShellMoveTime = 0;
        exitCount = 0;
        finalExitCount = 0;

        if (notifications.get()) {
            String modeInfo = isTargetingActive ? String.format(" | Targeting: %s", targetPlayer) : "";
            info("🚀 FAST AutoShellOrder activated! Minimum: %s%s", minPrice.get(), modeInfo);
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
            if (notifications.get()) info("🎯 Targeting enabled for player: %s", targetPlayer);
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
                if (notifications.get()) info("🔍 Checking orders for: %s", targetPlayer);
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
                            stage = Stage.SHOP_SHELL;
                            stageStart = now;
                            return;
                        }
                    }
                }
            }
            case SHOP_SHELL -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && isShell(stack)) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.SHOP_CONFIRM;
                            stageStart = now;
                            return;
                        }
                    }
                }
            }
            case ORDERS -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (!stack.isEmpty() && isShell(stack)) {
                            double orderPrice = getOrderPrice(stack);
                            if (orderPrice >= parsePrice(minPrice.get())) {
                                mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                                stage = Stage.ORDERS_SELECT;
                                stageStart = now;
                                if (notifications.get()) info("✅ Found shell order: %s", formatPrice(orderPrice));
                                return;
                            }
                        }
                    }
                }
            }
            default -> {}
        }
    }

    private boolean isShell(ItemStack stack) {
        return stack.getItem() == Items.SHULKER_SHELL;
    }

    private boolean isEndStone(ItemStack stack) {
        return stack.getItem() == Items.END_STONE;
    }

    private double getOrderPrice(ItemStack stack) {
        if (stack.isEmpty()) return -1.0;
        Item.TooltipContext tooltipContext = Item.TooltipContext.create(mc.world);
        List<Text> tooltip = stack.getTooltip(tooltipContext, mc.player, TooltipType.BASIC);
        return parseTooltipPrice(tooltip);
    }

    private double parseTooltipPrice(List<Text> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) return -1.0;
        Pattern pattern = Pattern.compile("\\$([\\d,]+)");
        for (Text line : tooltip) {
            Matcher matcher = pattern.matcher(line.getString());
            if (matcher.find()) {
                try {
                    return Double.parseDouble(matcher.group(1).replace(",", ""));
                } catch (NumberFormatException ignored) {}
            }
        }
        return -1.0;
    }

    private double parsePrice(String priceStr) {
        try { return Double.parseDouble(priceStr.replace(",", "")); }
        catch (NumberFormatException e) { return -1.0; }
    }

    private String formatPrice(double price) {
        if (price >= 1_000_000) return String.format("$%.1fM", price / 1_000_000);
        if (price >= 1_000) return String.format("$%.1fK", price / 1_000);
        return String.format("$%.0f", price);
    }

    public void info(String message, Object... args) {
        if (notifications.get()) ChatUtils.info(String.format(message, args));
    }
}
