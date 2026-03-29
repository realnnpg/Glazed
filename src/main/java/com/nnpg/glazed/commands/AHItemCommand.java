package com.nnpg.glazed.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.command.CommandSource;

import java.util.ArrayList;
import java.util.List;

public class AHItemCommand extends Command {

    public AHItemCommand() {
        super("ahitem", "Searches /ah for the item in your main hand with its enchantments.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            if (mc.player == null) return SINGLE_SUCCESS;

            ItemStack mainHandItem = mc.player.getMainHandStack();

            if (mainHandItem.isEmpty()) {
                error("You are not holding any item in your main hand.");
                return SINGLE_SUCCESS;
            }

            // Get item name
            String itemName = getItemName(mainHandItem);

            // Get enchantments using the new API
            List<String> enchantmentStrings = getEnchantments(mainHandItem);

            // Build the search command
            StringBuilder searchCommand = new StringBuilder("ah ");
            searchCommand.append(itemName);

            // Add "stack" suffix if item is a full stack (64 items)
            if (mainHandItem.getCount() == 64) {
                searchCommand.append(" stack");
            }

            if (!enchantmentStrings.isEmpty()) {
                searchCommand.append(" ");
                searchCommand.append(String.join(" ", enchantmentStrings));
            }

            // Send the command
            String command = searchCommand.toString();
            info("Searching: /" + command);
            mc.getNetworkHandler().sendChatCommand(command);

            return SINGLE_SUCCESS;
        });
    }

    private String getItemName(ItemStack stack) {
        // Get the item ID
        String itemId = stack.getItem().toString();

        // Remove namespace if present (e.g., "minecraft:diamond_sword" -> "diamond_sword")
        if (itemId.contains(":")) {
            itemId = itemId.split(":")[1];
        }

        return itemId.toLowerCase().replace(" ", "_");
    }

    private List<String> getEnchantments(ItemStack stack) {
        List<String> result = new ArrayList<>();

        // Use the new 1.20.5+ API
        ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(stack);

        for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {
            int level = enchantments.getLevel(entry);
            String enchantmentName = getEnchantmentName(entry);
            result.add(enchantmentName + " " + level);
        }

        return result;
    }

    private String getEnchantmentName(RegistryEntry<Enchantment> enchantmentEntry) {
        // Get the enchantment ID from the registry
        String enchantmentId = enchantmentEntry.getIdAsString();

        // Remove namespace (e.g., "minecraft:protection" -> "protection")
        if (enchantmentId.contains(":")) {
            enchantmentId = enchantmentId.split(":")[1];
        }

        return enchantmentId.toLowerCase().replace(" ", "_");
    }
}
