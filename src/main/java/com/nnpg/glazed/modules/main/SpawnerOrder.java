package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.SpawnerBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpawnerOrder extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private enum State {
        INVENTORY_CHECK,
        SCANNING,
        MOVING,
        OPENING,
        DROPPING,
        WAITING_CLOSE,
        CLOSING_GUI,
        ORDER_COMMAND,
        OPENING_ORDER,
        CLICKING_SLOT0,
        DEPOSITING_ITEMS,
        WAITING_CONFIRM_GUI,
        CONFIRMING_SALE,
        CLOSING_ORDER,
        WAITING_CYCLE
    }

    private State currentState = State.INVENTORY_CHECK;
    private int tickCounter = 0;
    private int waitCounter = 0;

    private BlockPos targetSpawner = null;
    private final List<BlockPos> spawners = new ArrayList<>();
    private int spawnerIndex = 0;
    private int itemIndex = 0;
    private final List<String> itemsToSellReference = Arrays.asList("bones", "arrows");
    private int currentSpawnerPageCounter = 0;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> dropDelayMinutes = sgGeneral.add(new StringSetting.Builder()
        .name("drop-delay-minutes")
        .description("Minutes between cycles.")
        .defaultValue("5")
        .build());

    private final Setting<Integer> actionDelaySetting = sgGeneral.add(new IntSetting.Builder()
        .name("action-delay-ticks")
        .description("Delay between each action.")
        .defaultValue(20)
        .min(1)
        .max(200)
        .build());

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show notifications")
        .defaultValue(true)
        .build());

    private final Setting<Integer> spawnerPagesToProcess = sgGeneral.add(new IntSetting.Builder()
        .name("spawner-pages-to-process")
        .description("Number of pages to proces.")
        .defaultValue(1)
        .min(1)
        .max(10)
        .build());

    private long lastDropTime = 0;

    public SpawnerOrder() {
        super(GlazedAddon.CATEGORY, "Spawner-Order", "Order All Spawner Loot.");
    }

    private boolean isGreenGlass(net.minecraft.item.ItemStack stack) {
        return stack.getItem() == Items.LIME_STAINED_GLASS_PANE ||
            stack.getItem() == Items.GREEN_STAINED_GLASS_PANE;
    }

    private int getDropDelayMinutes() {
        try {
            return Integer.parseInt(dropDelayMinutes.get());
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    private List<String> getAvailableItemsToSell() {
        List<String> availableItems = new ArrayList<>();
        boolean hasBones = false;
        boolean hasArrows = false;

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                String itemName = stack.getItem().getName().getString().toLowerCase();
                if (itemName.contains("bone")) {
                    hasBones = true;
                }
                if (itemName.contains("arrow")) {
                    hasArrows = true;
                }
            }
        }

        if (hasBones) {
            availableItems.add("bones");
        }
        if (hasArrows) {
            availableItems.add("arrows");
        }
        return availableItems;
    }

    @Override
    public void onActivate() {
        currentState = State.INVENTORY_CHECK;
        tickCounter = 0;
        lastDropTime = System.currentTimeMillis();
        spawners.clear();
        spawnerIndex = 0;
        itemIndex = 0;
        targetSpawner = null;
        currentSpawnerPageCounter = 0;

        if (notifications.get()) {
            ChatUtils.info("SpawnerOrder activated");
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        mc.player.setPitch(0f);
        if (notifications.get()) {
            ChatUtils.info("SpawnerOrder deactivated");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;
        waitCounter++;

        if (tickCounter >= actionDelaySetting.get()) {
            if (hasItemsToSell() &&
                currentState != State.ORDER_COMMAND &&
                currentState != State.OPENING_ORDER &&
                currentState != State.CLICKING_SLOT0 &&
                currentState != State.DEPOSITING_ITEMS &&
                currentState != State.WAITING_CONFIRM_GUI &&
                currentState != State.CONFIRMING_SALE &&
                currentState != State.CLOSING_ORDER) {

                if (notifications.get()) {
                    ChatUtils.info("");
                }
                currentState = State.ORDER_COMMAND;
                itemIndex = 0;
                tickCounter = 0;
                return;
            }
        }

        if (shouldStartNewCycle()) {
            startNewCycle();
            return;
        }

        if (tickCounter >= actionDelaySetting.get()) {
            executeCurrentState();
            tickCounter = 0;
        }
    }

    private boolean shouldStartNewCycle() {
        if (currentState != State.WAITING_CYCLE) return false;

        long currentTime = System.currentTimeMillis();
        long timeSinceLastDrop = currentTime - lastDropTime;
        long dropInterval = getDropDelayMinutes() * 60 * 1000L;

        return timeSinceLastDrop >= dropInterval;
    }

    private void startNewCycle() {
        currentState = State.INVENTORY_CHECK;
        tickCounter = 0;
        waitCounter = 0;
        spawners.clear();
        spawnerIndex = 0;
        itemIndex = 0;
        targetSpawner = null;
        lastDropTime = System.currentTimeMillis();
        currentSpawnerPageCounter = 0;

        if (notifications.get()) {
            ChatUtils.info("Starting new spawner cycle...");
        }
    }

    private void executeCurrentState() {
        switch (currentState) {
            case INVENTORY_CHECK -> handleInventoryCheck();
            case SCANNING -> handleScanning();
            case MOVING -> handleMoving();
            case OPENING -> handleOpening();
            case DROPPING -> handleDropping();
            case WAITING_CLOSE -> handleWaitingClose();
            case CLOSING_GUI -> handleClosingGui();
            case ORDER_COMMAND -> handleOrderCommand();
            case OPENING_ORDER -> handleOpeningOrder();
            case CLICKING_SLOT0 -> handleClickingSlot0();
            case DEPOSITING_ITEMS -> handleDepositingItems();
            case WAITING_CONFIRM_GUI -> handleWaitingConfirmGui();
            case CONFIRMING_SALE -> handleConfirmingSale();
            case CLOSING_ORDER -> handleClosingOrder();
            case WAITING_CYCLE -> handleWaitingCycle();
        }
    }

    private void handleInventoryCheck() {
        if (hasItemsToSell()) {
            currentState = State.ORDER_COMMAND;
            if (notifications.get()) {
                ChatUtils.info("");
            }
        } else {
            currentState = State.SCANNING;
            if (notifications.get()) {
                ChatUtils.info("");
            }
        }
    }

    private boolean hasItemsToSell() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                String itemName = stack.getItem().getName().getString().toLowerCase();
                if (itemName.contains("bone") || itemName.contains("arrow")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleScanning() {
        scanForSpawners();

        if (spawners.isEmpty()) {
            if (notifications.get()) {
                ChatUtils.error("No spawners found.");
            }
            if (hasItemsToSell()) {
                currentState = State.ORDER_COMMAND;
            } else {
                currentState = State.WAITING_CYCLE;
            }
            return;
        }

        targetSpawner = spawners.get(0);
        spawnerIndex = 0;
        currentState = State.MOVING;

        if (notifications.get()) {
            ChatUtils.info("");
        }
    }

    private void handleMoving() {
        if (targetSpawner == null) {
            currentState = State.SCANNING;
            return;
        }

        double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(targetSpawner));

        if (distance <= 4.5) {
            currentState = State.OPENING;
            if (notifications.get()) {
                ChatUtils.info("");
            }
        }
    }

    private void handleOpening() {
        if (mc.currentScreen instanceof GenericContainerScreen) {
            currentState = State.DROPPING;
            currentSpawnerPageCounter = 0;
            if (notifications.get()) {
                ChatUtils.info("");
            }
            return;
        }

        if (targetSpawner != null) {
            interactWithBlock(targetSpawner);
        }
    }

    private void handleDropping() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            currentState = State.OPENING;
            return;
        }

        mc.player.setPitch(90f);

        ScreenHandler handler = screen.getScreenHandler();


        if (handler.slots.size() > 50) {
            mc.interactionManager.clickSlot(
                handler.syncId,
                50,
                0,
                SlotActionType.PICKUP,
                mc.player
            );

            currentSpawnerPageCounter++;

            if (notifications.get()) {
                ChatUtils.info("");
            }


            if (currentSpawnerPageCounter < spawnerPagesToProcess.get()) {
                if (handler.slots.size() > 53) {
                    mc.interactionManager.clickSlot(
                        handler.syncId,
                        53,
                        0,
                        SlotActionType.PICKUP,
                        mc.player
                    );
                    if (notifications.get()) {
                        ChatUtils.info("");
                    }
                    currentState = State.DROPPING;
                    waitCounter = 0;
                } else {
                    if (notifications.get()) {
                        ChatUtils.warning("");
                    }
                    currentState = State.CLOSING_GUI;
                    waitCounter = 0;
                }
            } else {
                if (notifications.get()) {
                    ChatUtils.info("");
                }
                currentState = State.WAITING_CLOSE;
                waitCounter = 0;
            }
        } else {
            if (notifications.get()) {
                ChatUtils.warning("");
            }
            currentState = State.CLOSING_GUI;
            waitCounter = 0;
        }
    }

    private void handleWaitingClose() {
        if (waitCounter >= 10) {
            currentState = State.CLOSING_GUI;
        }
    }

    private void handleClosingGui() {
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }

        mc.player.setPitch(0f);

        spawnerIndex++;
        if (spawnerIndex < spawners.size()) {
            targetSpawner = spawners.get(spawnerIndex);
            currentState = State.MOVING;
            waitCounter = 0;
            if (notifications.get()) {
                ChatUtils.info("");
            }
        } else {
            currentState = State.ORDER_COMMAND;
            waitCounter = 0;
            if (notifications.get()) {
                ChatUtils.info("");
            }
        }
    }

    private void handleOrderCommand() {
        List<String> currentItemsToSell = getAvailableItemsToSell();

        if (itemIndex >= currentItemsToSell.size()) {
            if (hasItemsToSell()) {
                itemIndex = 0;
                ChatUtils.info("");
            } else {
                currentState = State.WAITING_CYCLE;
                if (notifications.get()) {
                    ChatUtils.info("Cycle completed! Next cycle in " + dropDelayMinutes.get() + " minutes");
                }
            }
            return;
        }

        String item = currentItemsToSell.get(itemIndex);
        ChatUtils.sendPlayerMsg("/order " + item);

        currentState = State.OPENING_ORDER;
        waitCounter = 0;

        if (notifications.get()) {
            ChatUtils.info("");
        }
    }

    private void handleOpeningOrder() {
        if (mc.currentScreen instanceof GenericContainerScreen) {
            currentState = State.CLICKING_SLOT0;
            if (notifications.get()) {
                ChatUtils.info("");
            }
        }
    }

    private void handleClickingSlot0() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            currentState = State.ORDER_COMMAND;
            return;
        }

        ScreenHandler handler = screen.getScreenHandler();

        if (handler.slots.size() > 0) {
            mc.interactionManager.clickSlot(
                handler.syncId,
                0,
                0,
                SlotActionType.PICKUP,
                mc.player
            );

            currentState = State.DEPOSITING_ITEMS;
            waitCounter = 0;

            if (notifications.get()) {
                ChatUtils.info("");
            }
        }
    }

    private void handleDepositingItems() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            if (waitCounter > 20) {
                currentState = State.WAITING_CONFIRM_GUI;
            }
            return;
        }

        ScreenHandler handler = screen.getScreenHandler();
        List<String> currentItemsToSell = getAvailableItemsToSell();
        String item = currentItemsToSell.get(itemIndex);
        String itemSingular = item.replace("s", "");

        boolean depositedItems = false;
        int itemsDeposited = 0;

        for (int i = 0; i < handler.slots.size(); i++) {
            var slot = handler.slots.get(i);
            if (slot.inventory == mc.player.getInventory() &&
                !slot.getStack().isEmpty() &&
                slot.getStack().getItem().getName().getString().toLowerCase().contains(itemSingular)) {

                mc.interactionManager.clickSlot(
                    handler.syncId,
                    i,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
                );
                depositedItems = true;
                itemsDeposited++;
            }
        }

        if (depositedItems) {
            if (notifications.get()) {
                ChatUtils.info("");
            }
            mc.player.closeHandledScreen();
            currentState = State.WAITING_CONFIRM_GUI;
            waitCounter = 0;
        } else if (waitCounter > 40) {
            if (notifications.get()) {
                ChatUtils.info("");
            }
            mc.player.closeHandledScreen();
            currentState = State.WAITING_CONFIRM_GUI;
            waitCounter = 0;
        }
    }

    private void handleWaitingConfirmGui() {
        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            ScreenHandler handler = screen.getScreenHandler();

            if (handler.slots.size() > 15) {
                var stack = handler.getSlot(15).getStack();
                if (isGreenGlass(stack)) {
                    currentState = State.CONFIRMING_SALE;
                    if (notifications.get()) {
                        ChatUtils.info("");
                    }
                    return;
                }
            }
        }

        if (waitCounter > 100) {
            if (notifications.get()) {
                ChatUtils.info("");
            }
            currentState = State.CLOSING_ORDER;
        }
    }

    private void handleConfirmingSale() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            currentState = State.CLOSING_ORDER;
            return;
        }

        ScreenHandler handler = screen.getScreenHandler();

        if (handler.slots.size() > 15) {
            var stack = handler.getSlot(15).getStack();
            if (isGreenGlass(stack)) {
                mc.interactionManager.clickSlot(
                    handler.syncId,
                    15,
                    0,
                    SlotActionType.PICKUP,
                    mc.player
                );

                if (notifications.get()) {
                    ChatUtils.info("");
                }

                currentState = State.CLOSING_ORDER;
                waitCounter = 0;
                return;
            }
        }

        if (notifications.get()) {
            ChatUtils.warning("");
        }
        currentState = State.CLOSING_ORDER;
    }

    private void handleClosingOrder() {
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }

        List<String> currentItemsToSell = getAvailableItemsToSell();

        if (itemIndex < currentItemsToSell.size()) {
            String completedItem = currentItemsToSell.get(itemIndex);
            ChatUtils.info("");
        }

        itemIndex++;

        if (itemIndex < currentItemsToSell.size()) {
            currentState = State.ORDER_COMMAND;
            if (notifications.get()) {
                ChatUtils.info("");
            }
        } else {
            if (hasItemsToSell()) {
                itemIndex = 0;
                currentState = State.ORDER_COMMAND;
                if (notifications.get()) {
                    ChatUtils.info("");
                }
            } else {
                currentState = State.WAITING_CYCLE;
                if (notifications.get()) {
                    ChatUtils.info("Cycle completed! Next cycle in " + dropDelayMinutes.get() + " minutes");
                }
                if (mc.currentScreen != null) {
                    mc.player.closeHandledScreen();
                    mc.player.closeHandledScreen();
                    if (notifications.get()) {
                        ChatUtils.info("");
                    }
                }
            }
        }
    }

    private void handleWaitingCycle() {
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
            mc.player.closeHandledScreen();
            if (notifications.get()) {
                ChatUtils.info("");
            }
        }
    }

    private void scanForSpawners() {
        spawners.clear();
        BlockPos playerPos = mc.player.getBlockPos();
        int radius = 5;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    Block block = mc.world.getBlockState(pos).getBlock();
                    if (block instanceof SpawnerBlock) {
                        spawners.add(pos);
                    }
                }
            }
        }
    }

    private void interactWithBlock(BlockPos pos) {
        try {
            BlockHitResult hitResult = new BlockHitResult(
                Vec3d.ofCenter(pos),
                mc.player.getHorizontalFacing().getOpposite(),
                pos,
                false
            );
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        } catch (Exception e) {
        }
    }

    @Override
    public String getInfoString() {
        if (!isActive()) return null;

        long timeRemainingSeconds = (getDropDelayMinutes() * 60 * 1000L - (System.currentTimeMillis() - lastDropTime)) / 1000;
        if (timeRemainingSeconds < 0) timeRemainingSeconds = 0;

        String spawnerInfo = "";
        if (currentState == State.DROPPING || currentState == State.OPENING || currentState == State.MOVING || currentState == State.WAITING_CLOSE) {
            spawnerInfo = " | Spawner Page: " + currentSpawnerPageCounter + "/" + spawnerPagesToProcess.get();
        }

        return currentState.name().replace("_", " ") +
            spawnerInfo +
            " | Next cycle in: " + timeRemainingSeconds + "s";
    }
}
