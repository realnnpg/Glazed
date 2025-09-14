package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.VersionUtil;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class AHSell extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> sellPrice = sgGeneral.add(new StringSetting.Builder()
        .name("sell-price")
        .description("The price to list each hotbar item for. Supports K/M/B.")
        .defaultValue("30k")
        .build()
    );

    private final Setting<Integer> confirmDelay = sgGeneral.add(new IntSetting.Builder()
        .name("confirm-delay")
        .description("Delay in ticks before clicking the confirm button.")
        .defaultValue(10)
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

    private final Setting<Boolean> enableFilter = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-item-filter")
        .description("Only sell selected item type from the hotbar.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Item> filterItem = sgGeneral.add(new ItemSetting.Builder()
        .name("filter-item")
        .description("Only this item will be sold when filter is enabled.")
        .defaultValue(Items.DIAMOND)
        .build()
    );

    private int delayCounter = 0;
    private boolean awaitingConfirmation = false;
    private int currentSlot = 0;

    public AHSell() {
        super(GlazedAddon.CATEGORY, "ah-sell", "Automatically sells all hotbar items using /ah sell.");
    }

    @Override
    public void onActivate() {
        if (!isValidPrice(sellPrice.get())) {
            if (notifications.get()) error("Invalid price format: " + sellPrice.get());
            toggle();
            return;
        }

        if (!hasSellableItemsInHotbar()) {
            if (notifications.get()) error("No sellable items found in hotbar.");
            toggle();
            return;
        }

        currentSlot = 0;
        attemptSellCurrentSlot();
    }

    @Override
    public void onDeactivate() {
        awaitingConfirmation = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!awaitingConfirmation || mc.player == null) return;

        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        ScreenHandler screenHandler = mc.player.currentScreenHandler;

        if (screenHandler instanceof GenericContainerScreenHandler handler) {
            if (handler.getRows() == 3) {
                ItemStack confirmButton = handler.getSlot(15).getStack();
                if (!confirmButton.isEmpty()) {
                    mc.interactionManager.clickSlot(handler.syncId, 15, 1, SlotActionType.QUICK_MOVE, mc.player);
                    if (notifications.get()) info("Sold item in hotbar slot " + currentSlot + ".");
                }

                awaitingConfirmation = false;
                moveToNextSlot();
            }
        }
    }

    @EventHandler
    private void onChatMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString();
        if (msg.contains("You have too many listed items.")) {
            if (notifications.get()) warning("Sell limit reached! Disabling module.");
            toggle();
        }
    }

    private void attemptSellCurrentSlot() {
        if (currentSlot > 8) {
            if (notifications.get()) info("Finished processing hotbar. Disabling module.");
            toggle();
            return;
        }

        // Use VersionUtil to handle version differences
        VersionUtil.setSelectedSlot(mc.player, currentSlot);
        ItemStack stack = mc.player.getInventory().getStack(currentSlot);

        if (enableFilter.get() && (stack.isEmpty() || !stack.isOf(filterItem.get()))) {
            if (notifications.get()) info("Skipping slot " + currentSlot + " (does not match filter).");
            moveToNextSlot();
            return;
        }

        if (stack.isEmpty()) {
            moveToNextSlot();
            return;
        }

        String price = sellPrice.get().trim();
        double parsedPrice = parsePrice(price);

        if (parsedPrice <= 0) {
            if (notifications.get()) error("Invalid price format: " + price);
            toggle();
            return;
        }

        if (notifications.get()) {
            info("Sending /ah sell %s for slot %d", formatPrice(parsedPrice), currentSlot);
        }

        mc.getNetworkHandler().sendChatCommand("ah sell " + price);
        delayCounter = confirmDelay.get();
        awaitingConfirmation = true;
    }

    private void moveToNextSlot() {
        currentSlot++;
        attemptSellCurrentSlot();
    }

    private boolean hasSellableItemsInHotbar() {
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;

            if (enableFilter.get()) {
                if (stack.isOf(filterItem.get())) return true;
            } else {
                return true;
            }
        }
        return false;
    }

    private boolean isValidPrice(String priceStr) {
        return parsePrice(priceStr) > 0;
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
}
