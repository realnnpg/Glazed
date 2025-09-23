package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.settings.RandomBetweenIntSetting;
import com.nnpg.glazed.utils.RandomBetweenInt;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;

public class AutoConfirm extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Progression: "AUCTION (Page {number})" -> "CONFIRM PURCHASE"
    private final Setting<Boolean> acAHBuy = sgGeneral.add(new BoolSetting.Builder()
        .name("ah-buy")
        .description("Automatically confirms purchases in the Auction House.")
        .defaultValue(false)
        .build()
    );

    // Progression: /ah sell {price} -> "CONFIRM LISTING"
    // Progression: "auction -> Your Items" -> "INSERT ITEM" -> Sign GUI -> "CONFIRM LISTING"
    private final Setting<Boolean> acAHSell = sgGeneral.add(new BoolSetting.Builder()
        .name("ah-sell")
        .description("Automatically confirms sales in the Auction House.")
        .defaultValue(false)
        .build()
    );

    // Progression: "ORDERS (Page {number})" -> "ORDERS -> Deliver Items" -> "ORDERS -> Confirm Delivery"
    private final Setting<Boolean> acOrderFulfill = sgGeneral.add(new BoolSetting.Builder()
        .name("order-fulfill")
        .description("Automatically confirms fulfilling orders.")
        .defaultValue(false)
        .build()
    );

    // Won't add for creating orders at the moment or for cancelling orders
    // as creating orders requires more interaction and cancelling
    // orders is not frequent enough to warrant an auto-confirm

    // Progression: /tpa {player_name} -> "CONFIRM REQUEST"
    private final Setting<Boolean> acTPA = sgGeneral.add(new BoolSetting.Builder()
        .name("tpa")
        .description("Automatically confirms TPA requests sent to someone else.")
        .defaultValue(false)
        .build()
    );

    // Progression: /tpahere {player_name} -> "CONFIRM REQUEST"
    private final Setting<Boolean> acTPAHere = sgGeneral.add(new BoolSetting.Builder()
        .name("tpahere")
        .description("Automatically confirms TPAHERE requests sent to someone else.")
        .defaultValue(false)
        .build()
    );

    // Trigger: Chat Link Message
    // Player123 sent you a tpa request
    // [CLICK] or type /tpaccept Player123
    // Progression: /tpaccept {player_name} -> "ACCEPT REQUEST"
    private final Setting<Boolean> acTPAReceive = sgGeneral.add(new BoolSetting.Builder()
        .name("tpa-receive")
        .description("Automatically confirms TPA requests sent to you.")
        .defaultValue(false)
        .build()
    );

    // Trigger: Chat Link Message
    // Player123 sent you a tpa request
    // [CLICK] or type /tpaccept Player123
    // Progression: /tpaccept {player_name} -> "ACCEPT TPAHERE REQUEST"
    private final Setting<Boolean> acTPAHereReceive = sgGeneral.add(new BoolSetting.Builder()
        .name("tpahere-receive")
        .description("Automatically confirms TPAHERE requests sent to you.")
        .defaultValue(false)
        .build()
    );

    // Progression: "SHOP - SHARD SHOP" -> "CONFIRM PURCHASE"
    private final Setting<Boolean> acShardshopBuy = sgGeneral.add(new BoolSetting.Builder()
        .name("shardshop-buy")
        .description("Automatically confirms purchases in the Shard Shop.")
        .defaultValue(false)
        .build()
    );


    // Progression: "SHOP" -> "SHOP - {END/NETHER/GEAR/FOOD}" -> "BUYING {ITEM}"
    // Not including shop at the moment due to normally having to set quantities


    // Progression: "CHOOSE 1 ITEM" -> "CONFIRM"
    private final Setting<Boolean> acCrateBuy = sgGeneral.add(new BoolSetting.Builder()
        .name("crate-buy")
        .description("Automatically confirms purchases from crates.")
        .defaultValue(false)
        .build()
    );

    // Progression: /bounty add {player_name} {amount} -> "CONFIRM BOUNTY"
    private final Setting<Boolean> acBounty = sgGeneral.add(new BoolSetting.Builder()
        .name("bounty-add")
        .description("Automatically confirms adding bounties on players.")
        .defaultValue(false)
        .build()
    );

    // Progression: "{amount} {PIG/COW/ZOMBIE/SPIDER/SKELETON/CREEPER/ZOMBIFIED PIGLIN/BLAZE/IRON GOLEM} SPAWNERS" -> "CONFIRM SELL"
    private final Setting<Boolean> acSpawnerSellAll = sgGeneral.add(new BoolSetting.Builder()
        .name("spawner-sell-all")
        .description("Automatically confirms selling all items in spawners.")
        .defaultValue(false)
        .build()
    );

    private final Setting<RandomBetweenInt> randomDelay = sgGeneral.add(new RandomBetweenIntSetting.Builder()
        .name("random-delay")
        .description("Random delay between actions in milliseconds.")
        .defaultRange(50, 150)
        .range(0, 2000)
        .sliderRange(10, 1000)
        .build()
    );

    public AutoConfirm() {
        super(GlazedAddon.CATEGORY,
            "auto-confirm",
            "Automatically confirms various confirmation UIs."
        );
    }

    private final CircularBuffer<String> lastScreens = new CircularBuffer<>(5);
    private String currentScreen = null;

    private String currentCommand = null;
    private long commandTime = 0;
    private static final long COMMAND_TIMEOUT = 10000;

    private long timer = 0;
    private long lastClickTime = 0;
    private static final long CLICK_COOLDOWN = 1000;


    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        // Handle screen closing (event.screen == null)
        if (event.screen == null) {
            // Reset timer if screen closes unexpectedly
            if (timer > 0) {
                timer = 0;
            }
            return;
        }

        // Check if it's any handled screen (inventory-related)
        if (event.screen instanceof HandledScreen<?>) {
            String newScreen = convertUnicodeToAscii(((HandledScreen<?>) event.screen).getTitle().getString()).toUpperCase();

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
            timer = currentTime + randomDelay.get().getRandom();;
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof ChatMessageC2SPacket packet) {
            String message = packet.chatMessage().trim();
            // Check if it's a relevant command (starts with /)
            if (message.startsWith("/")) {
                // Only store commands that we care about for auto-confirm
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

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (timer > 0 && System.currentTimeMillis() >= timer) {
            timer = 0;
            if (currentScreen != null && shouldConfirm(currentScreen)) {
                pressConfirmButton();
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
                // Check recent screens for AH or Shard Shop
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
                // Check current command for /bounty add (with timeout)
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
            // Don't retry if we recently clicked - screen might be transitioning
            if (System.currentTimeMillis() - lastClickTime < CLICK_COOLDOWN) {
                timer = 0;
                return;
            }
            timer = System.currentTimeMillis() + 10; // Retry very quickly
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
                // First click
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                // Second click immediately after (double-click simulation)
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                lastClickTime = System.currentTimeMillis();
                timer = 0; // Clear timer to prevent immediate retry
                return;
            }
        }
    }

    private boolean isConfirmButton(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // Check if it's a green/lime stained glass pane
        boolean isGreenGlass = stack.getItem() == Items.LIME_STAINED_GLASS_PANE ||
                              stack.getItem() == Items.GREEN_STAINED_GLASS_PANE;

        // Check if the name contains confirm or accept (convert unicode first)
        String name = convertUnicodeToAscii(stack.getName().getString()).toLowerCase();
        boolean hasConfirmText = name.contains("confirm") || name.contains("accept");

        return isGreenGlass && hasConfirmText;
    }

    private String convertUnicodeToAscii(String text) {
        StringBuilder result = new StringBuilder();

        for (char c : text.toCharArray()) {
            switch (c) {
                case 'ᴀ' -> result.append('a');
                case 'ʙ' -> result.append('b');
                case 'ᴄ' -> result.append('c');
                case 'ᴅ' -> result.append('d');
                case 'ᴇ' -> result.append('e');
                case 'ꜰ' -> result.append('f');
                case 'ɢ' -> result.append('g');
                case 'ʜ' -> result.append('h');
                case 'ɪ' -> result.append('i');
                case 'ᴊ' -> result.append('j');
                case 'ᴋ' -> result.append('k');
                case 'ʟ' -> result.append('l');
                case 'ᴍ' -> result.append('m');
                case 'ɴ' -> result.append('n');
                case 'ᴏ' -> result.append('o');
                case 'ᴘ' -> result.append('p');
                case 'ꞯ', 'ǫ' -> result.append('q');
                case 'ʀ' -> result.append('r');
                case 'ꜱ', 'ѕ' -> result.append('s');
                case 'ᴛ' -> result.append('t');
                case 'ᴜ' -> result.append('u');
                case 'ᴠ' -> result.append('v');
                case 'ᴡ' -> result.append('w');
                case 'x' -> result.append('x'); // X doesn't have a small cap variant
                case 'ʏ' -> result.append('y');
                case 'ᴢ' -> result.append('z');
                default -> result.append(c);
            }
        }

        return result.toString();
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
