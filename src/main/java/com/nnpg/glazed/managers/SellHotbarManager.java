package com.nnpg.glazed.managers;

import com.nnpg.glazed.VersionUtil;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public class SellHotbarManager {
    private static SellHotbarManager instance;
    
    
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private int delayCounter = 0;
    private boolean awaitingConfirmation = false;
    private int currentSlot = 0;
    private String currentPrice = "30k";
    private boolean isRunning = false;
    private boolean notifications = true;

    public static SellHotbarManager get() {
        if (instance == null) {
            instance = new SellHotbarManager();
            MeteorClient.EVENT_BUS.subscribe(instance);
        }
        return instance;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void start(String price, boolean notify) {
        if (isRunning) return;

        currentPrice = price;
        currentSlot = 0;
        notifications = notify;
        isRunning = true;
        awaitingConfirmation = false;
        delayCounter = 0;

        attemptSellCurrentSlot();
    }

    public void stop() {
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isRunning || mc.player == null) return;

        if (awaitingConfirmation) {
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
                        if (notifications) info("Sold item in hotbar slot " + currentSlot + ".");
                    }

                    awaitingConfirmation = false;
                    moveToNextSlot();
                }
            }
        }
    }

    @EventHandler
    private void onChatMessage(ReceiveMessageEvent event) {
        if (!isRunning) return;

        String msg = event.getMessage().getString();
        if (msg.contains("You have too many listed items.")) {
            warning("Sell limit reached! Stopping.");
            reset();
        }
    }

    private void attemptSellCurrentSlot() {
        if (mc.player == null) {
            reset();
            return;
        }

        if (currentSlot > 8) {
            if (notifications) info("Finished processing hotbar.");
            reset();
            return;
        }

        VersionUtil.setSelectedSlot(mc.player, currentSlot);
        ItemStack stack = mc.player.getInventory().getStack(currentSlot);

        if (stack.isEmpty()) {
            moveToNextSlot();
            return;
        }

        if (notifications) {
            info("Sending /ah sell " + currentPrice + " for slot " + currentSlot);
        }

        mc.getNetworkHandler().sendChatCommand("ah sell " + currentPrice);
        delayCounter = 10;
        awaitingConfirmation = true;
    }

    private void moveToNextSlot() {
        currentSlot++;
        attemptSellCurrentSlot();
    }

    private void reset() {
        isRunning = false;
        awaitingConfirmation = false;
        currentSlot = 0;
    }

    private void info(String message) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("§a[SellHotbar] §f" + message), false);
        }
    }

    private void warning(String message) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("§e[SellHotbar] §f" + message), false);
        }
    }
}
