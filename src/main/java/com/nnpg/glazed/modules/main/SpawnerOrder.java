package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.SpawnerBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        WAITING_ORDER_CHECK,
        WAITING_SPAWNER_CONFIRM,
        BALANCE_COMMAND,
        WAITING_BALANCE,
        PAY_COMMAND,
        WAITING_CYCLE
    }

    private State currentState = State.INVENTORY_CHECK;
    private int tickCounter = 0;
    private int waitCounter = 0;

    private BlockPos targetSpawner = null;
    private final List<BlockPos> spawners = new ArrayList<>();
    private int spawnerIndex = 0;
    private int currentSpawnerPageCounter = 0;
    private int stacksDepositedForSpawner = 0;
    private int depositDelayCounter = 0;
    private int orderCheckDelayCounter = 0;
    private int stacksPulledThisOpen = 0;
    private int spawnerConfirmAttempt = 0;
    private boolean awaitingBalance = false;
    private long pendingBalance = 0;
    private boolean pendingPayAfterOrder = false;
    private int balanceWaitTicks = 0;
    private boolean previousPauseOnLostFocus = true;
    private static final int BONE_ORDER_THRESHOLD = 35;
    private static final int MAX_STACKS_PER_PULL = 35;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> cycleDelayHours = sgGeneral.add(new IntSetting.Builder()
        .name("cycle-delay-hours")
        .description("Hours between spawner cycles.")
        .defaultValue(3)
        .min(1)
        .max(24)
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

    private final Setting<Integer> spawnersPerCycle = sgGeneral.add(new IntSetting.Builder()
        .name("spawners-per-cycle")
        .description("How many spawners to process per cycle.")
        .defaultValue(3)
        .min(1)
        .max(10)
        .build());

    private final Setting<Integer> stacksPerSpawner = sgGeneral.add(new IntSetting.Builder()
        .name("stacks-per-spawner")
        .description("How many bone stacks to deposit per spawner.")
        .defaultValue(4)
        .min(1)
        .max(10)
        .build());

    private final Setting<Integer> depositDelayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("deposit-delay-ticks")
        .description("Delay between bone stack deposits (ticks).")
        .defaultValue(2)
        .min(1)
        .max(40)
        .build());

    private final Setting<Integer> orderCheckDelayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("order-check-delay-ticks")
        .description("Delay before reopening spawner GUI after a sale.")
        .defaultValue(2)
        .min(1)
        .max(100)
        .build());

    private final Setting<Integer> maxStacksPerPull = sgGeneral.add(new IntSetting.Builder()
        .name("max-stacks-per-pull")
        .description("Maximum bone stacks to pull from the spawner GUI before selling.")
        .defaultValue(MAX_STACKS_PER_PULL)
        .min(MAX_STACKS_PER_PULL)
        .max(MAX_STACKS_PER_PULL)
        .build());

    private final Setting<Integer> orderMenuSlot = sgGeneral.add(new IntSetting.Builder()
        .name("order-menu-slot")
        .description("Slot to click in the /order bones menu.")
        .defaultValue(0)
        .min(0)
        .max(53)
        .build());

    private final Setting<Integer> playerDetectRange = sgGeneral.add(new IntSetting.Builder()
        .name("player-detect-range")
        .description("Disable SpawnerOrder when a player gets too close.")
        .defaultValue(10)
        .min(1)
        .max(50)
        .build());

    private final Setting<String> payTarget = sgGeneral.add(new StringSetting.Builder()
        .name("pay-target")
        .description("Player to pay after each cycle.")
        .defaultValue("igniusmc")
        .build());

    private long lastDropTime = 0;

    public SpawnerOrder() {
        super(GlazedAddon.CATEGORY, "Spawner-Order", "Order All Spawner Loot.");
        keybind.set(meteordevelopment.meteorclient.utils.misc.Keybind.fromKey(GLFW.GLFW_KEY_I));
    }

    private int getDropDelayMinutes() {
        return cycleDelayHours.get() * 60;
    }

    private boolean isOrderState(State state) {
        return state == State.ORDER_COMMAND ||
            state == State.OPENING_ORDER ||
            state == State.CLICKING_SLOT0 ||
            state == State.DEPOSITING_ITEMS ||
            state == State.WAITING_CONFIRM_GUI ||
            state == State.CONFIRMING_SALE ||
            state == State.CLOSING_ORDER ||
            state == State.WAITING_ORDER_CHECK;
    }

    private boolean isBalanceState(State state) {
        return state == State.BALANCE_COMMAND ||
            state == State.WAITING_BALANCE ||
            state == State.PAY_COMMAND;
    }

    private boolean isSpawnerState(State state) {
        return state == State.SCANNING ||
            state == State.MOVING ||
            state == State.OPENING ||
            state == State.DROPPING ||
            state == State.WAITING_CLOSE ||
            state == State.CLOSING_GUI;
    }

    private boolean isGreenGlass(net.minecraft.item.ItemStack stack) {
        return stack.getItem() == Items.LIME_STAINED_GLASS_PANE ||
            stack.getItem() == Items.GREEN_STAINED_GLASS_PANE;
    }

    private boolean isBoneStack(net.minecraft.item.ItemStack stack) {
        return !stack.isEmpty() && stack.getItem().getName().getString().toLowerCase().contains("bone");
    }

    private boolean hasNearbyPlayer() {
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (!(player instanceof AbstractClientPlayerEntity)) continue;
            if (mc.player.distanceTo(player) <= playerDetectRange.get()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onActivate() {
        currentState = State.INVENTORY_CHECK;
        tickCounter = 0;
        lastDropTime = System.currentTimeMillis();
        spawners.clear();
        spawnerIndex = 0;
        targetSpawner = null;
        currentSpawnerPageCounter = 0;
        stacksDepositedForSpawner = 0;
        depositDelayCounter = 0;
        orderCheckDelayCounter = 0;
        stacksPulledThisOpen = 0;
        awaitingBalance = false;
        pendingBalance = 0;
        balanceWaitTicks = 0;
        spawnerConfirmAttempt = 0;
        pendingPayAfterOrder = false;
        if (mc.options != null) {
            previousPauseOnLostFocus = mc.options.pauseOnLostFocus;
            mc.options.pauseOnLostFocus = false;
        }

        if (notifications.get()) {
            ChatUtils.info("SpawnerOrder activated");
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        if (mc.options != null) {
            mc.options.pauseOnLostFocus = previousPauseOnLostFocus;
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

        if (hasNearbyPlayer()) {
            if (notifications.get()) {
                ChatUtils.warning("SpawnerOrder paused: player nearby.");
            }
            toggle();
            return;
        }

        if (tickCounter >= actionDelaySetting.get() &&
            shouldOrderBones() &&
            !isOrderState(currentState) &&
            !isBalanceState(currentState) &&
            !isSpawnerState(currentState)) {
            currentState = State.ORDER_COMMAND;
            tickCounter = 0;
            return;
        }

        if (shouldStartNewCycle()) {
            startNewCycle();
            return;
        }

        if (currentState == State.WAITING_BALANCE && awaitingBalance) {
            balanceWaitTicks++;
            if (balanceWaitTicks > 200) {
                awaitingBalance = false;
                currentState = State.WAITING_CYCLE;
            }
        }

        if (currentState == State.DROPPING) {
            handleDropping();
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
        targetSpawner = null;
        lastDropTime = System.currentTimeMillis();
        currentSpawnerPageCounter = 0;
        stacksDepositedForSpawner = 0;
        depositDelayCounter = 0;
        orderCheckDelayCounter = 0;
        stacksPulledThisOpen = 0;
        awaitingBalance = false;
        pendingBalance = 0;
        balanceWaitTicks = 0;
        spawnerConfirmAttempt = 0;
        pendingPayAfterOrder = false;

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
            case WAITING_ORDER_CHECK -> handleWaitingOrderCheck();
            case WAITING_SPAWNER_CONFIRM -> handleWaitingSpawnerConfirm();
            case BALANCE_COMMAND -> handleBalanceCommand();
            case WAITING_BALANCE -> handleWaitingBalance();
            case PAY_COMMAND -> handlePayCommand();
            case WAITING_CYCLE -> handleWaitingCycle();
        }
    }

    private void handleInventoryCheck() {
        if (shouldOrderBones()) {
            currentState = State.ORDER_COMMAND;
            return;
        }
        currentState = State.SCANNING;
    }

    private boolean hasBonesInInventory() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (isBoneStack(stack)) {
                return true;
            }
        }
        return false;
    }

    private int countBoneStacksInInventory() {
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (isBoneStack(stack)) {
                count++;
            }
        }
        return count;
    }

    private boolean hasBonesInContainer(ScreenHandler handler) {
        for (int i = 0; i < handler.slots.size(); i++) {
            var stack = handler.getSlot(i).getStack();
            if (handler.getSlot(i).inventory == mc.player.getInventory()) {
                continue;
            }
            if (isBoneStack(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInventorySpaceForBones() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) {
                return true;
            }
            if (isBoneStack(stack) &&
                stack.getCount() < stack.getMaxCount()) {
                return true;
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
            currentState = State.WAITING_CYCLE;
            return;
        }

        targetSpawner = spawners.get(0);
        spawnerIndex = 0;
        currentState = State.MOVING;
        stacksDepositedForSpawner = 0;

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
            stacksDepositedForSpawner = 0;
            depositDelayCounter = 20;
            stacksPulledThisOpen = 0;
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

        ScreenHandler handler = screen.getScreenHandler();

        if (depositDelayCounter > 0) {
            depositDelayCounter--;
            return;
        }

        if (shouldOrderBones()) {
            currentState = State.ORDER_COMMAND;
            return;
        }

        if (hasBonesInInventory() &&
            (stacksPulledThisOpen >= maxStacksPerPull.get() || !hasInventorySpaceForBones())) {
            currentState = State.ORDER_COMMAND;
            return;
        }

        boolean movedStack = false;
        int movedThisTick = 0;
        for (int i = 0; i < handler.slots.size(); i++) {
            var slot = handler.slots.get(i);
            if (slot.inventory == mc.player.getInventory()) {
                continue;
            }
            if (isBoneStack(slot.getStack())) {
                mc.interactionManager.clickSlot(
                    handler.syncId,
                    i,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
                );
                movedStack = true;
                movedThisTick++;
                stacksDepositedForSpawner++;
                stacksPulledThisOpen++;
            }
            if (movedThisTick >= 4) {
                break;
            }
        }

        if (movedStack) {
            if (stacksDepositedForSpawner >= stacksPerSpawner.get()) {
                stacksDepositedForSpawner = 0;
                depositDelayCounter = depositDelayTicks.get();
            }
            if (stacksPulledThisOpen >= maxStacksPerPull.get() || !hasInventorySpaceForBones()) {
                currentState = State.ORDER_COMMAND;
            }
            return;
        }

        if (!hasBonesInContainer(handler)) {
            if (handler.slots.size() > 48) {
                mc.interactionManager.clickSlot(
                    handler.syncId,
                    48,
                    0,
                    SlotActionType.PICKUP,
                    mc.player
                );
                spawnerConfirmAttempt = 0;
                currentState = State.WAITING_SPAWNER_CONFIRM;
                waitCounter = 0;
                return;
            }
            currentState = State.WAITING_CLOSE;
            waitCounter = 0;
            return;
        }

        currentState = State.WAITING_CLOSE;
        waitCounter = 0;
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
            stacksDepositedForSpawner = 0;
            stacksPulledThisOpen = 0;
            if (notifications.get()) {
                ChatUtils.info("");
            }
        } else {
            if (notifications.get()) {
                ChatUtils.error("No more spawners found.");
            }
            currentState = State.BALANCE_COMMAND;
            waitCounter = 0;
            if (notifications.get()) {
                ChatUtils.info("");
            }
        }
    }

    private void handleOrderCommand() {
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        ChatUtils.sendPlayerMsg("/order bones");
        currentState = State.OPENING_ORDER;
        waitCounter = 0;
    }

    private void handleOpeningOrder() {
        if (mc.currentScreen instanceof GenericContainerScreen) {
            currentState = State.CLICKING_SLOT0;
        }
    }

    private void handleClickingSlot0() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            currentState = State.ORDER_COMMAND;
            return;
        }

        ScreenHandler handler = screen.getScreenHandler();
        int slotIndex = orderMenuSlot.get();
        if (slotIndex >= 0 && slotIndex < handler.slots.size()) {
            mc.interactionManager.clickSlot(
                handler.syncId,
                slotIndex,
                0,
                SlotActionType.PICKUP,
                mc.player
            );
            currentState = State.DEPOSITING_ITEMS;
            waitCounter = 0;
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
        boolean depositedItems = false;

        for (int i = 0; i < handler.slots.size(); i++) {
            var slot = handler.slots.get(i);
            if (slot.inventory == mc.player.getInventory() &&
                !slot.getStack().isEmpty() &&
                slot.getStack().getItem().getName().getString().toLowerCase().contains("bone")) {

                mc.interactionManager.clickSlot(
                    handler.syncId,
                    i,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
                );
                depositedItems = true;
            }
        }

        if (depositedItems || waitCounter > 40) {
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
                    return;
                }
            }
        }

        if (waitCounter > 100) {
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
                mc.player.closeHandledScreen();
                mc.player.closeHandledScreen();
                currentState = State.CLOSING_ORDER;
                waitCounter = 0;
                return;
            }
        }

        currentState = State.CLOSING_ORDER;
    }

    private void handleClosingOrder() {
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
            mc.player.closeHandledScreen();
        }
        if (hasBonesInInventory()) {
            currentState = State.ORDER_COMMAND;
        } else if (pendingPayAfterOrder) {
            currentState = State.PAY_COMMAND;
        } else {
            currentState = State.OPENING;
        }
        orderCheckDelayCounter = 0;
    }

    private void handleWaitingOrderCheck() {
        orderCheckDelayCounter++;
        if (orderCheckDelayCounter >= orderCheckDelayTicks.get()) {
            if (shouldOrderBones()) {
                currentState = State.ORDER_COMMAND;
            } else {
                currentState = targetSpawner != null ? State.OPENING : State.SCANNING;
            }
        }
    }

    private void handleWaitingSpawnerConfirm() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            currentState = State.WAITING_CLOSE;
            waitCounter = 0;
            return;
        }

        if (waitCounter < 2) return;

        ScreenHandler handler = screen.getScreenHandler();
        if (handler.slots.size() <= 15) {
            currentState = State.WAITING_CLOSE;
            waitCounter = 0;
            return;
        }

        if (!isGreenGlass(handler.getSlot(15).getStack())) {
            if (waitCounter > 40) {
                currentState = State.WAITING_CLOSE;
                waitCounter = 0;
            }
            return;
        }

        switch (spawnerConfirmAttempt) {
            case 0 -> mc.interactionManager.clickSlot(handler.syncId, 15, 0, SlotActionType.PICKUP, mc.player);
            case 1 -> mc.interactionManager.clickSlot(handler.syncId, 15, 0, SlotActionType.QUICK_MOVE, mc.player);
            case 2 -> mc.interactionManager.clickSlot(handler.syncId, 15, 0, SlotActionType.SWAP, mc.player);
            case 3 -> mc.interactionManager.clickSlot(handler.syncId, 15, 0, SlotActionType.THROW, mc.player);
            default -> mc.interactionManager.clickSlot(handler.syncId, 15, 0, SlotActionType.PICKUP_ALL, mc.player);
        }

        spawnerConfirmAttempt++;
        waitCounter = 0;

        if (spawnerConfirmAttempt >= 5) {
            currentState = State.WAITING_CLOSE;
        }
    }

    private void handleBalanceCommand() {
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        awaitingBalance = true;
        pendingBalance = 0;
        balanceWaitTicks = 0;
        ChatUtils.sendPlayerMsg("/bal");
        currentState = State.WAITING_BALANCE;
    }

    private void handleWaitingBalance() {
        if (!awaitingBalance) {
            currentState = State.PAY_COMMAND;
        }
    }

    private void handlePayCommand() {
        if (hasBonesInInventory() || shouldOrderBones()) {
            pendingPayAfterOrder = true;
            currentState = State.ORDER_COMMAND;
            return;
        }

        if (pendingBalance <= 0 || payTarget.get().trim().isEmpty()) {
            currentState = State.WAITING_CYCLE;
            return;
        }

        ChatUtils.sendPlayerMsg("/pay " + payTarget.get().trim() + " " + pendingBalance);
        pendingBalance = 0;
        pendingPayAfterOrder = false;
        currentState = State.WAITING_CYCLE;
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
        Set<BlockPos> uniqueSpawners = new LinkedHashSet<>();
        BlockPos playerPos = mc.player.getBlockPos();
        int radius = 5;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    Block block = mc.world.getBlockState(pos).getBlock();
                    if (block instanceof SpawnerBlock) {
                        uniqueSpawners.add(pos.toImmutable());
                    }
                }
            }
        }
        spawners.addAll(uniqueSpawners);
        if (spawners.size() > spawnersPerCycle.get()) {
            spawners.subList(spawnersPerCycle.get(), spawners.size()).clear();
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

    private boolean shouldOrderBones() {
        return countBoneStacksInInventory() >= BONE_ORDER_THRESHOLD;
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

    @EventHandler
    private void onChatMessage(ReceiveMessageEvent event) {
        if (!awaitingBalance) return;

        OptionalLong balance = parseBalanceFromMessage(event.getMessage().getString());
        if (balance.isPresent()) {
            pendingBalance = balance.getAsLong();
            awaitingBalance = false;
        }
    }

    private OptionalLong parseBalanceFromMessage(String message) {
        if (message == null) return OptionalLong.empty();

        String lower = message.toLowerCase();
        if (!lower.contains("you have") || !lower.contains("$")) {
            return OptionalLong.empty();
        }

        Matcher matcher = Pattern.compile("\\$([\\d,.]+)\\s*([kKmMbB]?)").matcher(message);
        if (!matcher.find()) return OptionalLong.empty();

        String raw = matcher.group(1);
        String suffix = matcher.group(2);
        String normalized = raw.replace(",", "");
        int dotIndex = normalized.indexOf('.');
        if (dotIndex >= 0) {
            normalized = normalized.substring(0, dotIndex);
        }
        try {
            long base = Long.parseLong(normalized);
            long multiplier = switch (suffix.toLowerCase()) {
                case "k" -> 1_000L;
                case "m" -> 1_000_000L;
                case "b" -> 1_000_000_000L;
                default -> 1L;
            };
            return OptionalLong.of(base * multiplier);
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

}
