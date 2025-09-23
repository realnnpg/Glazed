package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.settings.RandomBetweenIntSetting;
import com.nnpg.glazed.settings.TextDisplaySetting;
import com.nnpg.glazed.utils.RandomBetweenInt;
import com.nnpg.glazed.utils.glazed.StringUtils;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class UIHelper extends Module {

    public UIHelper() {
        super(GlazedAddon.CATEGORY,
            "UI Helper",
            "Helps perform various UI tasks automatically."
        );
    }

    private final SettingGroup sgAutoConfirm = settings.createGroup("AutoConfirm");

    private final Setting<String> acDescription = sgAutoConfirm.add(new TextDisplaySetting.Builder()
        .name("")
        .description("")
        .defaultValue("Automatically confirms various confirm pop-up dialogs.")
        .build()
    );

    private final Setting<Boolean> enableAutoConfirm = sgAutoConfirm.add(new BoolSetting.Builder()
        .name("enable-auto-confirm")
        .description("Automatically confirms various actions in the UI.")
        .defaultValue(false)
        .build()
    );

    // Progression: "AUCTION (Page {number})" -> "CONFIRM PURCHASE"
    private final Setting<Boolean> acAHBuy = sgAutoConfirm.add(new BoolSetting.Builder()
        .name("ah-buy")
        .description("Automatically confirms purchases in the Auction House.")
        .defaultValue(false)
        .visible(enableAutoConfirm::get)
        .build()
    );

    // Progression: /ah sell {price} -> "CONFIRM LISTING"
    // Progression: "auction -> Your Items" -> "INSERT ITEM" -> Sign GUI -> "CONFIRM LISTING"
    private final Setting<Boolean> acAHSell = sgAutoConfirm.add(new BoolSetting.Builder()
        .name("ah-sell")
        .description("Automatically confirms sales in the Auction House.")
        .defaultValue(false)
        .visible(enableAutoConfirm::get)
        .build()
    );

    // Progression: "ORDERS (Page {number})" -> "ORDERS -> Deliver Items" -> "ORDERS -> Confirm Delivery"
    private final Setting<Boolean> acOrderFulfill = sgAutoConfirm.add(new BoolSetting.Builder()
        .name("order-fulfill")
        .description("Automatically confirms fulfilling orders.")
        .defaultValue(false)
        .visible(enableAutoConfirm::get)
        .build()
    );

    // Won't add for creating orders at the moment or for cancelling orders
    // as creating orders requires more interaction and cancelling
    // orders is not frequent enough to warrant an auto-confirm

    // Progression: /tpa {player_name} -> "CONFIRM REQUEST"
    private final Setting<Boolean> acTPA = sgAutoConfirm.add(new BoolSetting.Builder()
        .name("tpa")
        .description("Automatically confirms TPA requests sent to someone else.")
        .defaultValue(false)
        .visible(enableAutoConfirm::get)
        .build()
    );

    // Progression: /tpahere {player_name} -> "CONFIRM REQUEST"
    private final Setting<Boolean> acTPAHere = sgAutoConfirm.add(new BoolSetting.Builder()
        .name("tpahere")
        .description("Automatically confirms TPAHERE requests sent to someone else.")
        .defaultValue(false)
        .visible(enableAutoConfirm::get)
        .build()
    );

    // Trigger: Chat Link Message
    // Player123 sent you a tpa request
    // [CLICK] or type /tpaccept Player123
    // Progression: /tpaccept {player_name} -> "ACCEPT REQUEST"
    private final Setting<Boolean> acTPAReceive = sgAutoConfirm.add(new BoolSetting.Builder()
        .name("tpa-receive")
        .description("Automatically confirms TPA requests sent to you.")
        .defaultValue(false)
        .visible(enableAutoConfirm::get)
        .build()
    );

    // Trigger: Chat Link Message
    // Player123 sent you a tpa request
    // [CLICK] or type /tpaccept Player123
    // Progression: /tpaccept {player_name} -> "ACCEPT TPAHERE REQUEST"
    private final Setting<Boolean> acTPAHereReceive = sgAutoConfirm.add(new BoolSetting.Builder()
        .name("tpahere-receive")
        .description("Automatically confirms TPAHERE requests sent to you.")
        .defaultValue(false)
        .visible(enableAutoConfirm::get)
        .build()
    );

    // Progression: "SHOP - SHARD SHOP" -> "CONFIRM PURCHASE"
    private final Setting<Boolean> acShardshopBuy = sgAutoConfirm.add(new BoolSetting.Builder()
        .name("shardshop-buy")
        .description("Automatically confirms purchases in the Shard Shop.")
        .defaultValue(false)
        .visible(enableAutoConfirm::get)
        .build()
    );


    // Progression: "SHOP" -> "SHOP - {END/NETHER/GEAR/FOOD}" -> "BUYING {ITEM}"
    // Not including shop at the moment due to normally having to set quantities


    // Progression: "CHOOSE 1 ITEM" -> "CONFIRM"
    private final Setting<Boolean> acCrateBuy = sgAutoConfirm.add(new BoolSetting.Builder()
        .name("crate-buy")
        .description("Automatically confirms purchases from crates.")
        .defaultValue(false)
        .visible(enableAutoConfirm::get)
        .build()
    );

    // Progression: /bounty add {player_name} {amount} -> "CONFIRM BOUNTY"
    private final Setting<Boolean> acBounty = sgAutoConfirm.add(new BoolSetting.Builder()
        .name("bounty-add")
        .description("Automatically confirms adding bounties on players.")
        .defaultValue(false)
        .visible(enableAutoConfirm::get)
        .build()
    );

    // Progression: "{amount} {PIG/COW/ZOMBIE/SPIDER/SKELETON/CREEPER/ZOMBIFIED PIGLIN/BLAZE/IRON GOLEM} SPAWNERS" -> "CONFIRM SELL"
    private final Setting<Boolean> acSpawnerSellAll = sgAutoConfirm.add(new BoolSetting.Builder()
        .name("spawner-sell-all")
        .description("Automatically confirms selling all items in spawners.")
        .defaultValue(false)
        .visible(enableAutoConfirm::get)
        .build()
    );

    private final Setting<RandomBetweenInt> acRandomDelay = sgAutoConfirm.add(new RandomBetweenIntSetting.Builder()
        .name("random-delay")
        .description("Random delay between actions in milliseconds.")
        .defaultRange(50, 150)
        .range(0, 2000)
        .sliderRange(10, 1000)
        .visible(enableAutoConfirm::get)
        .build()
    );

    private final CircularBuffer<String> lastScreens = new CircularBuffer<>(5);
    private String currentScreen = null;

    private String currentCommand = null;
    private long commandTime = 0;
    private static final long COMMAND_TIMEOUT = 10000;

    private long acTimer = 0;
    private long lastClickTime = 0;
    private static final long CLICK_COOLDOWN = 1000;


    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen == null) {
            // Reset timer if screen closes unexpectedly
            if (acTimer > 0) {
                acTimer = 0;
            }
            return;
        }

        if (event.screen instanceof HandledScreen<?>) {
            String newScreen = StringUtils.convertUnicodeToAscii(((HandledScreen<?>) event.screen).getTitle().getString()).toUpperCase();

            // Only update screen tracking if it's actually a new screen
            if (currentScreen != null && !currentScreen.equals(newScreen)) {
                lastScreens.add(currentScreen);
            }
            currentScreen = newScreen;
        }

        if (shouldConfirm(currentScreen)) {
            // Check cooldown to prevent rapid clicking
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastClickTime < CLICK_COOLDOWN) {
                return;
            }
            acTimer = currentTime + acRandomDelay.get().getRandom();
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof ChatMessageC2SPacket packet) {
            String message = packet.chatMessage().trim();
            if (message.startsWith("/")) {
                if (message.startsWith("/ah sell") || message.startsWith("/tpa ") ||
                    message.startsWith("/tpahere ") || message.startsWith("/tpaccept ") ||
                    message.startsWith("/bounty add ")) {
                    currentCommand = message;
                    commandTime = System.currentTimeMillis();
                }
            }
        } else if (event.packet instanceof CommandExecutionC2SPacket packet) {
            String command = "/" + packet.command().trim();
            // Check if it's a relevant command
            if (command.startsWith("/ah sell") || command.startsWith("/tpa ") ||
                command.startsWith("/tpahere ") || command.startsWith("/tpaccept ") ||
                command.startsWith("/bounty add ")) {
                currentCommand = command;
                commandTime = System.currentTimeMillis();
            }
        }
    }

    private boolean shouldConfirm(String currentScreenTitle) {
        if (currentScreenTitle == null) {
            return false;
        }
        if (!(currentScreenTitle.contains("CONFIRM") || currentScreenTitle.contains("ACCEPT"))) {
            return false;
        }

        boolean shouldConfirm = false;

        switch (currentScreenTitle) {
            case "CONFIRM PURCHASE" -> {
                boolean foundAuction = false;
                boolean foundShardShop = false;

                for (int i = 0; i < Math.min(lastScreens.size, 3); i++) {
                    try {
                        String recentScreen = lastScreens.get(i);
                        if (recentScreen != null) {
                            if (recentScreen.contains("AUCTION")) {
                                foundAuction = true;
                            }
                            if (recentScreen.contains("SHOP - SHARD SHOP")) {
                                foundShardShop = true;
                            }
                        }
                    } catch (Exception e) {
                        // Ignore screen check errors
                    }
                }

                if (acAHBuy.get() && foundAuction) {
                    shouldConfirm = true;
                } else if (acShardshopBuy.get() && foundShardShop) {
                    shouldConfirm = true;
                }
            }
            case "CONFIRM LISTING" -> {
                if (acAHSell.get()) {
                    shouldConfirm = true;
                }
            }
            case "ORDERS -> CONFIRM DELIVERY" -> {
                // Check previous screen for Orders
                String prevScreen = lastScreens.get(0);
                if (acOrderFulfill.get() && prevScreen != null && prevScreen.contains("ORDERS")) {
                    shouldConfirm = true;
                }
            }
            case "CONFIRM REQUEST" -> {
                // Check current command for /tpa or /tpahere
                if (currentCommand != null && System.currentTimeMillis() - commandTime < COMMAND_TIMEOUT) {
                    if (acTPA.get() && currentCommand.startsWith("/tpa ")) {
                        shouldConfirm = true;
                    } else if (acTPAHere.get() && currentCommand.startsWith("/tpahere ")) {
                        shouldConfirm = true;
                    }
                }
            }
            case "ACCEPT REQUEST" -> {
                // Check current command for /tpaccept
                if (acTPAReceive.get() && currentCommand != null &&
                    System.currentTimeMillis() - commandTime < COMMAND_TIMEOUT &&
                    currentCommand.startsWith("/tpaccept ")) {
                    shouldConfirm = true;
                }
            }
            case "ACCEPT TPAHERE REQUEST" -> {
                // Check current command for /tpaccept
                if (acTPAHereReceive.get() && currentCommand != null &&
                    System.currentTimeMillis() - commandTime < COMMAND_TIMEOUT &&
                    currentCommand.startsWith("/tpaccept ")) {
                    shouldConfirm = true;
                }
            }
            case "CONFIRM" -> {
                // Check recent screens for CHOOSE 1 ITEM
                if (acCrateBuy.get()) {
                    for (int i = 0; i < Math.min(lastScreens.size, 3); i++) {
                        try {
                            String recentScreen = lastScreens.get(i);
                            if (recentScreen != null && recentScreen.contains("CHOOSE 1 ITEM")) {
                                shouldConfirm = true;
                                break;
                            }
                        } catch (Exception e) {
                            // Ignore screen access errors
                        }
                    }
                }
            }
            case "CONFIRM BOUNTY" -> {
                // Check current command for /bounty add
                if (acBounty.get() && currentCommand != null &&
                    System.currentTimeMillis() - commandTime < COMMAND_TIMEOUT &&
                    currentCommand.startsWith("/bounty add ")) {
                    shouldConfirm = true;
                }
            }
            case "CONFIRM SELL" -> {
                // Check previous screen for spawner sell all
                try {
                    String prevScreen = lastScreens.get(0);
                    if (acSpawnerSellAll.get() && prevScreen != null && (prevScreen.contains("SPAWNER"))) {
                        shouldConfirm = true;
                    }
                } catch (Exception e) {
                    // Ignore if no previous screen
                }
            }
        }
        return shouldConfirm;
    }


    private void pressConfirmButton() {
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }

        if (mc.currentScreen == null) {
            // Don't retry if recently clicked
            if (System.currentTimeMillis() - lastClickTime < CLICK_COOLDOWN) {
                acTimer = 0;
                return;
            }
            acTimer = System.currentTimeMillis() + 10; // Retry
            return;
        }

        if (!(mc.currentScreen instanceof HandledScreen<?>)) {
            return;
        }

        HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;
        ScreenHandler handler = screen.getScreenHandler();

        // Find the confirm button (green/lime stained glass pane with "confirm" or "accept" in the name)
        for (int i = 0; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (isConfirmButton(stack)) {
                // Double Click
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                lastClickTime = System.currentTimeMillis();
                acTimer = 0; // Clear timer to prevent immediate retry
                return;
            }
        }
    }

    private boolean isConfirmButton(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // Check if it's a green/lime stained glass pane
        boolean isGreenGlass = stack.getItem() == Items.LIME_STAINED_GLASS_PANE ||
            stack.getItem() == Items.GREEN_STAINED_GLASS_PANE;

        // Check if the name contains confirm or accept
        String name = StringUtils.convertUnicodeToAscii(stack.getName().getString()).toLowerCase();
        boolean hasConfirmText = name.contains("confirm") || name.contains("accept");

        return isGreenGlass && hasConfirmText;
    }

    private final SettingGroup sgAutoAdvance = settings.createGroup("AutoAdvance");

    private final Setting<String> aaDescription = sgAutoAdvance.add(new TextDisplaySetting.Builder()
        .name("")
        .description("")
        .defaultValue("Automatically advances pages in spawner GUI after dropping items.")
        .build()
    );

    private final Setting<Boolean> enableAutoAdvance = sgAutoAdvance.add(new BoolSetting.Builder()
        .name("enable-auto-advance")
        .description("Automatically advances pages in UIs after certain actions.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Direction> aaDirection = sgAutoAdvance.add(new EnumSetting.Builder<Direction>()
        .name("direction")
        .description("The direction to advance the page.")
        .defaultValue(Direction.FORWARDS)
        .visible(enableAutoAdvance::get)
        .build()
    );

    private final Setting<RandomBetweenInt> aaRandomDelay = sgAutoAdvance.add(new RandomBetweenIntSetting.Builder()
        .name("random-delay")
        .description("Random delay between actions in milliseconds.")
        .defaultRange(50, 150)
        .range(0, 2000)
        .sliderRange(10, 1000)
        .visible(enableAutoAdvance::get)
        .build()
    );

    private long aaTimer = 0;

    private void advancePage(Direction dir) {
        if (mc.player == null || mc.interactionManager == null || mc.currentScreen == null) {
            return;
        }
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
            return;
        }

        ScreenHandler handler = screen.getScreenHandler();

        for (int i = 0; i < handler.slots.size(); i++) {
            String name = StringUtils.convertUnicodeToAscii(handler.getSlot(i).getStack().getName().getString());
            if ((dir == Direction.FORWARDS && name.equals("next")) ||
                (dir == Direction.BACKWARDS && name.equals("back"))) {
                // Double click
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                return;
            }
        }
    }

    private enum Direction {
        FORWARDS,
        BACKWARDS
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!isActive()) return;

        // AutoAdvance functionality
        if (enableAutoAdvance.get()) {
            // Check if the packet is a slot click packet (when player clicks items in GUI)
            if (event.packet instanceof ClickSlotC2SPacket packet) {
                // Detect if the slot clicked contained a dropper
                if (StringUtils.convertUnicodeToAscii(packet.getStack().getName().getString()).equals("drop loot")) {
                    // Start timer after dropping loot (delay is already in milliseconds)
                    aaTimer = System.currentTimeMillis() + aaRandomDelay.get().getRandom();
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (acTimer > 0 && System.currentTimeMillis() >= acTimer) {
            acTimer = 0;
            if (currentScreen != null && shouldConfirm(currentScreen)) {
                pressConfirmButton();
            }
        }
        if (aaTimer > 0 && System.currentTimeMillis() >= aaTimer) {
            aaTimer = 0;
            advancePage(aaDirection.get());
        }
    }

    private static class CircularBuffer<T> {
        private final Object[] buffer;
        private int index = 0;
        public int size = 0;

        public CircularBuffer(int capacity) {
            buffer = new Object[capacity];
        }

        public void add(T item) {
            buffer[index] = item;
            index = (index + 1) % buffer.length;
            if (size < buffer.length) size++;
        }

        @SuppressWarnings("unchecked")
        public T get(int i) {
            if (i >= size) throw new IndexOutOfBoundsException();
            int idx = (index - size + i + buffer.length) % buffer.length;
            return (T) buffer[idx];
        }
    }
}
