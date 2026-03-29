package com.nnpg.glazed.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nnpg.glazed.managers.SellHotbarManager;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.item.ItemStack;
import net.minecraft.command.CommandSource;

public class SellHotbarCommand extends Command {

    public SellHotbarCommand() {
        super("sell_hotbar", "Sells all items in your hotbar for a specified price. Supports K/M/B suffixes.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("price", StringArgumentType.word())
            .executes(context -> {
                String price = StringArgumentType.getString(context, "price");

                if (!isValidPrice(price)) {
                    error("Invalid price format: " + price + ". Use numbers with K/M/B suffixes (e.g., 30k, 1.5m, 2b).");
                    return SINGLE_SUCCESS;
                }

                if (SellHotbarManager.get().isRunning()) {
                    error("Already selling items. Please wait.");
                    return SINGLE_SUCCESS;
                }

                if (!hasSellableItemsInHotbar()) {
                    error("No sellable items found in hotbar.");
                    return SINGLE_SUCCESS;
                }

                info("Starting to sell hotbar items for " + formatPrice(parsePrice(price)) + " each.");
                SellHotbarManager.get().start(price, true);

                return SINGLE_SUCCESS;
            })
        );
    }

    private boolean hasSellableItemsInHotbar() {
        if (mc.player == null) return false;

        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty()) {
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
