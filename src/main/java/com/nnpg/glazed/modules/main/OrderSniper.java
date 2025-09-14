package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.VersionUtil;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.*;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OrderSniper extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private enum Stage {
        NONE, REFRESH, OPEN_ORDERS, WAIT_ORDERS_GUI, SELECT_ORDER,
        TRANSFER_ITEMS, WAIT_CONFIRM_GUI, CONFIRM_SALE,
        FINAL_EXIT, CYCLE_PAUSE
    }

    private Stage stage = Stage.NONE;
    private long stageStart = 0;
    private int transferIndex = 0;
    private long lastTransferTime = 0;
    private int ticksSinceStageStart = 0;

    // Settings
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> itemName = sgGeneral.add(new StringSetting.Builder()
        .name("item-name")
        .description("The item name to search for in /orders command.")
        .defaultValue("diamond")
        .build());

    private final Setting<Item> targetItem = sgGeneral.add(new ItemSetting.Builder()
        .name("sniping-item")
        .description("The actual item to snipe and sell.")
        .defaultValue(Items.DIAMOND)
        .build());

    private final Setting<String> minPrice = sgGeneral.add(new StringSetting.Builder()
        .name("min-price")
        .description("Minimum acceptable price.")
        .defaultValue("1")
        .build());

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Notify on important actions.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> shulkerSupport = sgGeneral.add(new BoolSetting.Builder()
        .name("shulker-support")
        .description("Enable slower but safer shulker box support.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> delayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("delay-ticks")
        .description("Delay in ticks for slower connections (0 = fastest)")
        .defaultValue(0)
        .min(0)
        .max(10)
        .sliderMax(10)
        .build());

    public OrderSniper() {
        super(GlazedAddon.CATEGORY, "Order-Sniper", "Sniping Orders and sell for your price.");
    }

    @Override
    public void onActivate() {
        if (parsePrice(minPrice.get()) == -1.0) {
            ChatUtils.error("Invalid price format.");
            toggle();
            return;
        }
        stage = Stage.REFRESH;
        stageStart = System.currentTimeMillis();
        transferIndex = 0;
        lastTransferTime = 0;
        ticksSinceStageStart = 0;
    }

    @Override
    public void onDeactivate() {
        stage = Stage.NONE;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        long now = System.currentTimeMillis();
        ticksSinceStageStart++;

        switch (stage) {
            case REFRESH -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();
                    mc.interactionManager.clickSlot(handler.syncId, 49, 1, SlotActionType.QUICK_MOVE, mc.player);
                    mc.interactionManager.clickSlot(handler.syncId, 49, 1, SlotActionType.QUICK_MOVE, mc.player);
                    mc.interactionManager.clickSlot(handler.syncId, 49, 1, SlotActionType.QUICK_MOVE, mc.player);
                    mc.interactionManager.clickSlot(handler.syncId, 49, 1, SlotActionType.QUICK_MOVE, mc.player);
                    mc.interactionManager.clickSlot(handler.syncId, 49, 1, SlotActionType.QUICK_MOVE, mc.player);

                    if (now - stageStart > 50) {
                        stage = Stage.OPEN_ORDERS;
                        stageStart = now;
                        ticksSinceStageStart = 0;
                    }
                } else {
                    stage = Stage.OPEN_ORDERS;
                    stageStart = now;
                    ticksSinceStageStart = 0;
                }
            }

            case OPEN_ORDERS -> {
                // Use the string setting for the command instead of the formatted item name
                ChatUtils.sendPlayerMsg("/orders " + itemName.get());
                stage = Stage.WAIT_ORDERS_GUI;
                stageStart = now;
                ticksSinceStageStart = 0;
            }

            case WAIT_ORDERS_GUI -> {
                if (ticksSinceStageStart < Math.max(8, delayTicks.get())) return;
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    stage = Stage.SELECT_ORDER;
                    ticksSinceStageStart = 0;
                } else if (now - stageStart > 2000) {
                    stage = Stage.OPEN_ORDERS;
                    stageStart = now;
                    ticksSinceStageStart = 0;
                }
            }

            case SELECT_ORDER -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
                ScreenHandler handler = screen.getScreenHandler();
                for (Slot slot : handler.slots) {
                    ItemStack stack = slot.getStack();
                    if (!stack.isEmpty() && isMatchingOrder(stack)) {
                        mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                        stage = Stage.TRANSFER_ITEMS;
                        transferIndex = 0;
                        lastTransferTime = now;
                        ticksSinceStageStart = 0;
                        return;
                    }
                }
                if (now - stageStart > 2000) {
                    mc.interactionManager.clickSlot(handler.syncId, 49, 1, SlotActionType.QUICK_MOVE, mc.player);
                    mc.interactionManager.clickSlot(handler.syncId, 49, 1, SlotActionType.QUICK_MOVE, mc.player);
                    mc.interactionManager.clickSlot(handler.syncId, 49, 1, SlotActionType.QUICK_MOVE, mc.player);
                    stage = Stage.OPEN_ORDERS;
                    stageStart = now;
                    ticksSinceStageStart = 0;
                }
            }

            case TRANSFER_ITEMS -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
                ScreenHandler handler = screen.getScreenHandler();

                if (transferIndex >= 36 || !hasItemsToSell()) {
                    mc.player.closeHandledScreen();
                    stage = Stage.WAIT_CONFIRM_GUI;
                    stageStart = now;
                    transferIndex = 0;
                    ticksSinceStageStart = 0;
                    return;
                }

                if (ticksSinceStageStart >= getTransferDelayTicks()) {
                    ItemStack stack = mc.player.getInventory().getStack(transferIndex);
                    boolean shouldTransfer = false;

                    if (!stack.isEmpty()) {
                        if (stack.isOf(targetItem.get())) {
                            shouldTransfer = true;
                        } else if (shulkerSupport.get() && isShulker(stack) && shulkerContainsTarget(stack)) {
                            shouldTransfer = true;
                            // Per le shulker, aggiungi un delay extra ridotto
                            if (ticksSinceStageStart < Math.max(10, delayTicks.get() * 2)) {
                                return;
                            }
                        }
                    }

                    if (shouldTransfer) {
                        int playerSlotId = -1;
                        for (Slot slot : handler.slots) {
                            if (slot.inventory == mc.player.getInventory() && slot.getIndex() == transferIndex) {
                                playerSlotId = slot.id;
                                break;
                            }
                        }
                        if (playerSlotId != -1) {
                            mc.interactionManager.clickSlot(handler.syncId, playerSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                        }
                    }
                    transferIndex++;
                    ticksSinceStageStart = 0;
                }
            }

            case WAIT_CONFIRM_GUI -> {
                if (ticksSinceStageStart < Math.max(8, delayTicks.get())) return;
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    stage = Stage.CONFIRM_SALE;
                    ticksSinceStageStart = 0;
                } else if (now - stageStart > 2000) {
                    toggle();
                }
            }

            case CONFIRM_SALE -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
                ScreenHandler handler = screen.getScreenHandler();
                for (Slot slot : handler.slots) {
                    ItemStack stack = slot.getStack();
                    if (isGreenGlass(stack)) {
                        for (int i = 0; i < 3; i++) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                        }
                        stage = Stage.FINAL_EXIT;
                        stageStart = now;
                        ticksSinceStageStart = 0;
                        return;
                    }
                }
            }

            case FINAL_EXIT -> {
                if (mc.currentScreen != null) {
                    mc.player.closeHandledScreen();
                    // Aspetta un po' prima di continuare per assicurarsi che la GUI sia chiusa
                    if (ticksSinceStageStart < Math.max(5, delayTicks.get())) return;
                }

                if (!hasItemsToSell()) {
                    if (notifications.get()) {
                        ChatUtils.info("Completed selling all items!");
                    }
                    toggle();
                } else {
                    stage = Stage.CYCLE_PAUSE;
                    stageStart = now;
                    ticksSinceStageStart = 0;
                }
            }

            case CYCLE_PAUSE -> {
                // Changed from 25 ticks to 5 ticks (0.25 seconds at 20 TPS)
                if (ticksSinceStageStart >= Math.max(5, delayTicks.get())) {
                    stage = Stage.REFRESH;
                    stageStart = now;
                    ticksSinceStageStart = 0;
                }
            }

            case NONE -> {}
        }
    }



    private boolean isMatchingOrder(ItemStack stack) {
        if (!stack.isOf(targetItem.get())) return false;
        double price = getOrderPrice(stack);
        double min = parsePrice(minPrice.get());
        return price >= min;
    }

    private double getOrderPrice(ItemStack stack) {
        List<Text> tooltip = stack.getTooltip(Item.TooltipContext.create(mc.world), mc.player, TooltipType.BASIC);
        return parseTooltipPrice(tooltip);
    }

    private double parseTooltipPrice(List<Text> tooltip) {
        Pattern pattern = Pattern.compile("\\$([\\d,.]+)([kmb])?", Pattern.CASE_INSENSITIVE);
        for (Text line : tooltip) {
            String text = line.getString().toLowerCase().replace(",", "").trim();
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                try {
                    double base = Double.parseDouble(matcher.group(1));
                    String suffix = matcher.group(2) != null ? matcher.group(2).toLowerCase() : "";
                    return switch (suffix) {
                        case "k" -> base * 1_000;
                        case "m" -> base * 1_000_000;
                        case "b" -> base * 1_000_000_000;
                        default -> base;
                    };
                } catch (NumberFormatException ignored) {}
            }
        }
        return -1.0;
    }

    private boolean hasItemsToSell() {
        for (ItemStack stack : VersionUtil.getMainInventory(mc.player)) {
            if (!stack.isEmpty()) {
                if (stack.isOf(targetItem.get())) {
                    return true;
                } else if (shulkerSupport.get() && isShulker(stack) && shulkerContainsTarget(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isGreenGlass(ItemStack stack) {
        return stack.getItem() == Items.LIME_STAINED_GLASS_PANE || stack.getItem() == Items.GREEN_STAINED_GLASS_PANE;
    }

    private boolean isShulker(ItemStack stack) {

        Item item = stack.getItem();
        String itemName = item.getName().getString().toLowerCase();
        return itemName.contains("shulker") ||
            item == Items.SHULKER_BOX ||
            item == Items.WHITE_SHULKER_BOX ||
            item == Items.ORANGE_SHULKER_BOX ||
            item == Items.MAGENTA_SHULKER_BOX ||
            item == Items.LIGHT_BLUE_SHULKER_BOX ||
            item == Items.YELLOW_SHULKER_BOX ||
            item == Items.LIME_SHULKER_BOX ||
            item == Items.PINK_SHULKER_BOX ||
            item == Items.GRAY_SHULKER_BOX ||
            item == Items.LIGHT_GRAY_SHULKER_BOX ||
            item == Items.CYAN_SHULKER_BOX ||
            item == Items.PURPLE_SHULKER_BOX ||
            item == Items.BLUE_SHULKER_BOX ||
            item == Items.BROWN_SHULKER_BOX ||
            item == Items.GREEN_SHULKER_BOX ||
            item == Items.RED_SHULKER_BOX ||
            item == Items.BLACK_SHULKER_BOX;
    }

    private boolean shulkerContainsTarget(ItemStack shulker) {
        if (!isShulker(shulker)) return false;


        List<Text> tooltip = shulker.getTooltip(Item.TooltipContext.create(mc.world), mc.player, TooltipType.BASIC);
        String targetName = targetItem.get().getName().getString().toLowerCase();

        for (Text line : tooltip) {
            String lineText = line.getString().toLowerCase();

            if (lineText.contains(targetName) ||
                lineText.contains(targetName.replace(" ", "_")) ||
                lineText.contains(targetName.replace("_", " "))) {
                return true;
            }
        }
        return false;
    }

    private double parsePrice(String priceStr) {
        try {
            String cleaned = priceStr.toLowerCase().replace(",", "").trim();
            double multiplier = 1;
            if (cleaned.endsWith("b")) {
                multiplier = 1_000_000_000; cleaned = cleaned.substring(0, cleaned.length() - 1);
            } else if (cleaned.endsWith("m")) {
                multiplier = 1_000_000; cleaned = cleaned.substring(0, cleaned.length() - 1);
            } else if (cleaned.endsWith("k")) {
                multiplier = 1_000; cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            return Double.parseDouble(cleaned) * multiplier;
        } catch (Exception e) {
            return -1.0;
        }
    }

    private String getFormattedItemName(Item item) {
        String[] parts = item.getTranslationKey().split("\\.");
        String name = parts[parts.length - 1].replace("_", " ");
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private long getTransferDelay() {
        if (!shulkerSupport.get()) {

            return 30;
        }


        ItemStack stack = mc.player.getInventory().getStack(transferIndex);
        if (isShulker(stack)) return 200;
        return 50;
    }

    private int getTransferDelayTicks() {
        if (!shulkerSupport.get()) {

            return Math.max(1, delayTicks.get());
        }


        ItemStack stack = mc.player.getInventory().getStack(transferIndex);
        if (isShulker(stack)) return Math.max(10, delayTicks.get() * 2);
        return Math.max(3, delayTicks.get());
    }

    @Override
    public String getInfoString() {
        if (!isActive()) return null;
        return String.format("%s -> %s (Stage: %s)",
            itemName.get(),
            targetItem.get().getName().getString(),
            stage.name());
    }
}
