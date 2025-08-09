package com.nnpg.glazed.modules;
//IMPORTS
import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.*;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OrderSniper extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private enum Stage {
        NONE, OPEN_ORDERS, WAIT_ORDERS_GUI, SELECT_ORDER, TRANSFER_ITEMS, WAIT_CONFIRM_GUI,
        CONFIRM_SALE, FINAL_EXIT, CYCLE_PAUSE
    }

    private Stage stage = Stage.NONE;
    private long stageStart = 0;
    private int transferIndex = 0;
    private long lastTransferTime = 0;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Item> targetItem = sgGeneral.add(new ItemSetting.Builder()
        .name("item-to-sell")
        .description("Item to sell.")
        .defaultValue(Items.GOLDEN_APPLE)
        .build()
    );

    private final Setting<String> minPrice = sgGeneral.add(new StringSetting.Builder()
        .name("min-price")
        .description("Minimum price to sell.")
        .defaultValue("1")
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .defaultValue(true)
        .build()
    );

    public OrderSniper() {
        super(GlazedAddon.CATEGORY, "order-sniper", "Snipe Orders And Sell For Your Price.");
    }

    @Override
    public void onActivate() {
        if (parsePrice(minPrice.get()) == -1.0) {
            if (notifications.get()) ChatUtils.error("Invalid min-price format.");
            toggle();
            return;
        }

        stage = Stage.OPEN_ORDERS;
        stageStart = System.currentTimeMillis();
        transferIndex = 0;
        lastTransferTime = 0;

        if (notifications.get()) {
            info("OrderSniper activated.", targetItem.get().getName().getString());
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
            case OPEN_ORDERS -> {
                ChatUtils.sendPlayerMsg("/orders " + getFormattedItemName(targetItem.get()));
                stage = Stage.WAIT_ORDERS_GUI;
                stageStart = now;
            }

            case WAIT_ORDERS_GUI -> {
                if (now - stageStart < 500) return;
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    stage = Stage.SELECT_ORDER;
                    stageStart = now;
                } else if (now - stageStart > 3000) {
                    if (notifications.get()) ChatUtils.error("Timeout opening order GUI.");
                    stage = Stage.OPEN_ORDERS;
                    stageStart = now;
                }
            }

            case SELECT_ORDER -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
                ScreenHandler handler = screen.getScreenHandler();

                for (Slot slot : handler.slots) {
                    ItemStack stack = slot.getStack();
                    if (!stack.isEmpty() && isMatchingOrder(stack)) {
                        mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                        if (notifications.get()) info("Found matching order for %s", targetItem.get().getName().getString());
                        stage = Stage.TRANSFER_ITEMS;
                        stageStart = now;
                        transferIndex = 0;
                        lastTransferTime = 0;
                        return;
                    }
                }

                if (now - stageStart > 4000) {
                    mc.interactionManager.clickSlot(handler.syncId, 49, 1, SlotActionType.QUICK_MOVE, mc.player);
                    stage = Stage.CYCLE_PAUSE;
                    stageStart = now;
                }
            }

            case TRANSFER_ITEMS -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
                    stage = Stage.OPEN_ORDERS;
                    return;
                }

                if (transferIndex >= 36) {
                    mc.player.closeHandledScreen();
                    stage = Stage.WAIT_CONFIRM_GUI;
                    stageStart = now;
                    return;
                }

                if (now - lastTransferTime >= 50) {
                    ItemStack stack = mc.player.getInventory().getStack(transferIndex);

                    if (!stack.isEmpty() && (stack.isOf(targetItem.get()) || isValidShulkerWithTarget(stack))) {
                        int slotId = getSlotId(screen.getScreenHandler(), transferIndex);
                        if (slotId != -1) {
                            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                        }
                    }

                    transferIndex++;
                    lastTransferTime = now;
                }
            }

            case WAIT_CONFIRM_GUI -> {
                if (now - stageStart < 300) return;
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    stage = Stage.CONFIRM_SALE;
                    stageStart = now;
                } else if (now - stageStart > 3000) {
                    toggle();
                }
            }

            case CONFIRM_SALE -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
                for (Slot slot : screen.getScreenHandler().slots) {
                    ItemStack stack = slot.getStack();
                    if (isGreenGlass(stack)) {
                        for (int i = 0; i < 3; i++) {
                            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                        }
                        if (notifications.get()) info("Confirmed sale.");
                        stage = Stage.FINAL_EXIT;
                        stageStart = now;
                        return;
                    }
                }

                if (now - stageStart > 5000) {
                    mc.player.closeHandledScreen();
                    stage = Stage.CYCLE_PAUSE;
                    stageStart = now;
                }
            }

            case FINAL_EXIT -> {
                if (mc.currentScreen != null) {
                    mc.player.closeHandledScreen();
                    stageStart = now;
                } else if (now - stageStart > 250) {
                    stage = Stage.CYCLE_PAUSE;
                    stageStart = now;
                }
            }

            case CYCLE_PAUSE -> {
                if (now - stageStart > 1000) {
                    stage = Stage.OPEN_ORDERS;
                    stageStart = now;
                }
            }

            case NONE -> {}
        }
    }

    // === Helpers ===

    private boolean isMatchingOrder(ItemStack stack) {
        if (!stack.isOf(targetItem.get()) && !isValidShulkerWithTarget(stack)) return false;
        return getOrderPrice(stack) >= parsePrice(minPrice.get());
    }

    private boolean isValidShulkerWithTarget(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        if (!(blockItem.getBlock() instanceof ShulkerBoxBlock)) return false;

        List<Text> tooltip = stack.getTooltip(Item.TooltipContext.create(mc.world), mc.player, TooltipType.BASIC);
        for (Text line : tooltip) {
            String lower = line.getString().toLowerCase(Locale.ROOT);
            if (lower.contains(targetItem.get().getName().getString().toLowerCase(Locale.ROOT))) return true;
        }

        return false;
    }

    private double getOrderPrice(ItemStack stack) {
        List<Text> tooltip = stack.getTooltip(Item.TooltipContext.create(mc.world), mc.player, TooltipType.BASIC);
        return parseTooltipPrice(tooltip);
    }

    private double parseTooltipPrice(List<Text> tooltip) {
        Pattern pattern = Pattern.compile("\\$([\\d,.]+)([kmb])?", Pattern.CASE_INSENSITIVE);
        for (Text line : tooltip) {
            String s = line.getString().replace(",", "").toLowerCase(Locale.ROOT);
            Matcher m = pattern.matcher(s);
            if (m.find()) {
                try {
                    double value = Double.parseDouble(m.group(1));
                    return switch (m.group(2) != null ? m.group(2).toLowerCase(Locale.ROOT) : "") {
                        case "k" -> value * 1_000;
                        case "m" -> value * 1_000_000;
                        case "b" -> value * 1_000_000_000;
                        default -> value;
                    };
                } catch (NumberFormatException ignored) {}
            }
        }
        return -1.0;
    }

    private double parsePrice(String input) {
        if (input == null || input.isEmpty()) return -1.0;
        input = input.toLowerCase().replace(",", "").trim();
        double mult = 1;
        if (input.endsWith("b")) { mult = 1_000_000_000; input = input.substring(0, input.length() - 1); }
        else if (input.endsWith("m")) { mult = 1_000_000; input = input.substring(0, input.length() - 1); }
        else if (input.endsWith("k")) { mult = 1_000; input = input.substring(0, input.length() - 1); }

        try { return Double.parseDouble(input) * mult; }
        catch (NumberFormatException e) { return -1.0; }
    }

    private int getSlotId(ScreenHandler handler, int inventoryIndex) {
        for (Slot slot : handler.slots) {
            if (slot.inventory == mc.player.getInventory() && slot.getIndex() == inventoryIndex) {
                return slot.id;
            }
        }
        return -1;
    }

    private boolean isGreenGlass(ItemStack stack) {
        return stack.getItem() == Items.LIME_STAINED_GLASS_PANE || stack.getItem() == Items.GREEN_STAINED_GLASS_PANE;
    }

    private String getFormattedItemName(Item item) {
        String[] parts = item.getTranslationKey().split("\\.");
        String name = parts[parts.length - 1].replace("_", " ");
        StringBuilder sb = new StringBuilder();
        for (String w : name.split(" ")) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
