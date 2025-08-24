package com.nnpg.glazed.modules.treefarmer;
import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.VersionUtil;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.*;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.option.KeyBinding;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class AutoTreeFarmer2 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRefill = settings.createGroup("Order Refill");
    private final SettingGroup sgAutoFeatures = settings.createGroup("Auto Features");

    // Removed humanization settings - using hardcoded safe values

    private final Setting<SaplingType> saplingType = sgGeneral.add(new EnumSetting.Builder<SaplingType>()
        .name("sapling-type")
        .description("Type of sapling to farm.")
        .defaultValue(SaplingType.SPRUCE)
        .build()
    );

    private static final int PLACE_DELAY = 2;
    private static final int BREAK_DELAY = 2;
    private static final int ACTION_DELAY = 3;
    private static final int RANDOM_DELAY_RANGE = 3;
    private static final double PAUSE_CHANCE = 2.0;
    private static final int MAX_PAUSE_TICKS = 3;
    private static final double ROTATION_NOISE = 1.2;
    private static final double WRONG_LOOK_CHANCE = 12.0;

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate to look at blocks when interacting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useBoneMeal = sgGeneral.add(new BoolSetting.Builder()
        .name("use-bone-meal")
        .description("Whether to use bone meal to instantly grow trees.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Range to work within.")
        .defaultValue(4)
        .min(1)
        .max(6)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("Chat Feedback")
        .description("um send or no send messages")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enableOrderRefill = sgRefill.add(new BoolSetting.Builder()
        .name("enable-order-refill")
        .description("Enable automatic refilling from /order system.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> refillThreshold = sgRefill.add(new IntSetting.Builder()
        .name("refill-threshold")
        .description("Minimum total items in inventory before refilling.")
        .defaultValue(32)
        .min(1)
        .max(128)
        .sliderMax(128)
        .build()
    );

    private final Setting<Integer> refillAmount = sgRefill.add(new IntSetting.Builder()
        .name("refill-amount")
        .description("Number of stacks to take from orders (max 3).")
        .defaultValue(3)
        .min(1)
        .max(3)
        .sliderMax(3)
        .build()
    );

    private final Setting<Integer> orderDelay = sgRefill.add(new IntSetting.Builder()
        .name("order-delay")
        .description("Base delay between order GUI actions in milliseconds.")
        .defaultValue(900)
        .min(600)
        .max(2500)
        .sliderMax(2500)
        .build()
    );

    private final Setting<Integer> maxRefillRetries = sgRefill.add(new IntSetting.Builder()
        .name("max-refill-retries")
        .description("Maximum retries for refill operations.")
        .defaultValue(3)
        .min(1)
        .max(10)
        .sliderMax(10)
        .build()
    );

    private final Setting<Boolean> autoEat = sgAutoFeatures.add(new BoolSetting.Builder()
        .name("auto-eat")
        .description("Automatically eat when hunger is critically low.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> hungerThreshold = sgAutoFeatures.add(new IntSetting.Builder()
        .name("hunger-threshold")
        .description("Hunger level to trigger auto eating.")
        .defaultValue(6)
        .min(1)
        .max(19)
        .sliderMax(19)
        .build()
    );

    private final Setting<Boolean> autoDrop = sgAutoFeatures.add(new BoolSetting.Builder()
        .name("auto-drop")
        .description("Automatically drop unwanted items (logs and sticks).")
        .defaultValue(true)
        .build()
    );

    private int tickCounter = 0;
    private int actionDelay = 0;
    private int dropTickCounter = 0;
    private List<BlockPos> farmPositions = new ArrayList<>();
    private int currentFarmIndex = 0;
    private FarmState currentState = FarmState.ANALYZING_CURRENT_STATE;
    private BlockPos currentWorkingPos = null;
    private boolean shouldBeJumping = false;
    private int rotationStabilizeTicks = 0;
    private BlockPos currentBreakingPos = null;
    private boolean isBreaking = false;
    private long currentBreakingStartTime = 0;
    private static final long MAX_BREAK_TIME_MS = 3000;
    private RefillState refillState = RefillState.NONE;
    private long refillStageStart = 0;
    private Item currentRefillItem = null;
    private int stacksCollected = 0;
    private int refillRetryCount = 0;
    private boolean isEating = false;
    private int eatingTicks = 0;
    private boolean isPausedForEating = false;
    private boolean isUsingItemKeyHeld = false;
    private final Random random = new Random();
    private int currentPauseTicks = 0;
    private boolean isPaused = false;
    private List<BlockPos> logBlocks = new ArrayList<>();
    private int consecutiveActions = 0;
    private long lastActionTime = 0;

    private boolean hasAnalyzedCurrentState = false;
    private int totalLogsInCurrentTree = 0;
    private int logsHarvestedInCurrentTree = 0;

    public AutoTreeFarmer2() {
        super(GlazedAddon.treefarmer, "AutoTreeFarmer2", "Slower but maybe not detectable");
    }

    @Override
    public void onActivate() {
        resetAllStates();
        currentState = FarmState.ANALYZING_CURRENT_STATE;
        hasAnalyzedCurrentState = false;
        sendInfo("AutoTreeFarmer activated. Analyzing current state...");
    }

    @Override
    public void onDeactivate() {
        stopBreaking();
        resetJump();
        stopEating();
        refillState = RefillState.NONE;
        isPausedForEating = false;
        releaseUseItemKey();
        sendInfo("AutoTreeFarmer deactivated!");
    }

    private void resetAllStates() {
        farmPositions.clear();
        currentFarmIndex = 0;
        tickCounter = 0;
        actionDelay = 0;
        dropTickCounter = 0;
        shouldBeJumping = false;
        rotationStabilizeTicks = 0;
        currentBreakingPos = null;
        isBreaking = false;
        currentBreakingStartTime = 0;
        refillState = RefillState.NONE;
        refillStageStart = 0;
        currentRefillItem = null;
        stacksCollected = 0;
        refillRetryCount = 0;
        isEating = false;
        eatingTicks = 0;
        isPausedForEating = false;
        releaseUseItemKey();
        currentPauseTicks = 0;
        isPaused = false;
        logBlocks.clear();
        consecutiveActions = 0;
        lastActionTime = System.currentTimeMillis();
        hasAnalyzedCurrentState = false;
        totalLogsInCurrentTree = 0;
        logsHarvestedInCurrentTree = 0;
    }

    private void sendInfo(String message) {
        if (chatFeedback.get()) {
            info(message);
        }
    }

    private void sendWarning(String message) {
        if (chatFeedback.get()) {
            warning(message);
        }
    }

    private void senderror(String message) {
        if (chatFeedback.get()) {
            error(message);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (isEating) {
            handleEating();
            return;
        }

        if (isPausedForEating) {
            if (mc.player.getHungerManager().getFoodLevel() >= 19) {
                sendInfo("Hunger full, resuming AutoTreeFarmer.");
                isPausedForEating = false;
            } else {
                return;
            }
        }

        if (isPaused) {
            currentPauseTicks--;
            if (currentPauseTicks <= 0) {
                isPaused = false;
            } else {
                return;
            }
        }

        if (isBreaking && currentBreakingPos != null) {
            if (mc.world.getBlockState(currentBreakingPos).isAir()) {
                stopBreaking();
            } else if (System.currentTimeMillis() - currentBreakingStartTime > MAX_BREAK_TIME_MS) {
                ChatUtils.warning("Block breaking timeout, moving to next block.");
                stopBreaking();
            }
        }

        tickCounter++;
        dropTickCounter++;
        handleAutoFeatures();

        if (enableOrderRefill.get() && refillState == RefillState.NONE) {
            checkAndStartRefill();
        }

        if (refillState != RefillState.NONE) {
            handleRefill();
            return;
        }

        autoRefillHotbar(Items.BONE_MEAL, 5);
        autoRefillHotbar(getCurrentSapling(), 4);

        if (currentState != FarmState.APPLYING_BONEMEAL && shouldBeJumping) {
            resetJump();
        }

        int currentDelay = getRandomizedDelay(ACTION_DELAY);
        if (tickCounter >= currentDelay) {
            if (shouldTakeRandomPause()) {
                startRandomPause();
                return;
            }

            tickCounter = 0;

            if (actionDelay > 0) {
                actionDelay--;
                return;
            }

            executeFarmingState();
        }
    }

    private boolean shouldTakeRandomPause() {
        return random.nextDouble() * 100.0 < PAUSE_CHANCE && !isPaused && consecutiveActions > 3;
    }

    private void startRandomPause() {
        isPaused = true;
        currentPauseTicks = random.nextInt(MAX_PAUSE_TICKS) + 5;
        consecutiveActions = 0;
    }

    private int getRandomizedDelay(int baseDelay) {
        return baseDelay + random.nextInt(RANDOM_DELAY_RANGE * 2 + 1) - RANDOM_DELAY_RANGE;
    }

    private void executeFarmingState() {
        switch (currentState) {
            case ANALYZING_CURRENT_STATE -> {
                if (!hasAnalyzedCurrentState) {
                    analyzeCurrentState();
                    hasAnalyzedCurrentState = true;
                }
            }
            case FINDING_POSITIONS -> {
                findFarmPositions();
                if (!farmPositions.isEmpty()) {
                    currentState = FarmState.PLACING_SAPLINGS;
                    sendInfo("Found " + farmPositions.size() + " farm positions.");
                }
            }
            case PLACING_SAPLINGS -> {
                if (placeSaplings()) {
                    actionDelay = getRandomizedDelay(PLACE_DELAY);
                    currentState = useBoneMeal.get() ? FarmState.APPLYING_BONEMEAL : FarmState.WAITING_FOR_GROWTH;
                    rotationStabilizeTicks = 0;
                    consecutiveActions++;
                }
            }
            case APPLYING_BONEMEAL -> {
                if (applyBoneMeal()) {
                    actionDelay = getRandomizedDelay(PLACE_DELAY);
                    currentState = FarmState.WAITING_FOR_GROWTH;
                    consecutiveActions++;
                }
            }
            case WAITING_FOR_GROWTH -> {
                if (checkTreeGrown()) {
                    scanAndPrepareHarvest();
                    currentState = FarmState.HARVESTING;
                }
            }
            case HARVESTING -> {
                if (harvestTree()) {
                    actionDelay = getRandomizedDelay(BREAK_DELAY);
                    moveToNextFarm();
                    currentState = FarmState.PLACING_SAPLINGS;
                    consecutiveActions++;
                }
            }
        }
    }

    private void analyzeCurrentState() {
        sendInfo("Analyzing current farm state...");
        findFarmPositions();

        if (farmPositions.isEmpty()) {
            sendInfo("No valid farm positions found. Searching for new locations...");
            currentState = FarmState.FINDING_POSITIONS;
            return;
        }

        // Check each farm position to determine current state
        boolean foundExistingSaplings = false;
        boolean foundGrownTree = false;
        int bestFarmIndex = 0;

        for (int i = 0; i < farmPositions.size(); i++) {
            BlockPos farmPos = farmPositions.get(i);
            FarmAnalysis analysis = analyzeFarmPosition(farmPos);

            switch (analysis) {
                case EMPTY_READY_FOR_PLANTING -> {
                    if (!foundExistingSaplings && !foundGrownTree) {
                        bestFarmIndex = i;
                        currentState = FarmState.PLACING_SAPLINGS;
                    }
                }
                case HAS_SAPLINGS -> {
                    if (!foundGrownTree) {
                        bestFarmIndex = i;
                        foundExistingSaplings = true;
                        if (useBoneMeal.get()) {
                            currentState = FarmState.APPLYING_BONEMEAL;
                        } else {
                            currentState = FarmState.WAITING_FOR_GROWTH;
                        }
                    }
                }
                case HAS_GROWN_TREE -> {
                    bestFarmIndex = i;
                    foundGrownTree = true;
                    scanAndPrepareHarvest(i);
                    currentState = FarmState.HARVESTING;
                    break; // Prioritize harvesting
                }
            }
        }

        currentFarmIndex = bestFarmIndex;
        sendInfo("Resuming from: " + currentState.toString().toLowerCase().replace("_", " ") +
            " at farm " + (currentFarmIndex + 1) + "/" + farmPositions.size());
    }

    private FarmAnalysis analyzeFarmPosition(BlockPos farmPos) {
        BlockPos[] saplingPositions = get2x2Positions(farmPos);

        boolean hasAnyLogs = false;
        boolean hasAnySaplings = false;
        int emptySpots = 0;

        for (BlockPos pos : saplingPositions) {
            Block currentBlock = mc.world.getBlockState(pos).getBlock();

            if (isCurrentLog(currentBlock)) {
                hasAnyLogs = true;
            } else if (currentBlock.equals(getCurrentSapling())) {
                hasAnySaplings = true;
            } else if (currentBlock.equals(Blocks.AIR)) {
                emptySpots++;
            }
        }

        if (hasAnyLogs) {
            return FarmAnalysis.HAS_GROWN_TREE;
        } else if (hasAnySaplings) {
            return FarmAnalysis.HAS_SAPLINGS;
        } else if (emptySpots == 4) {
            return FarmAnalysis.EMPTY_READY_FOR_PLANTING;
        } else {
            return FarmAnalysis.INVALID_OR_BLOCKED;
        }
    }

    private void scanAndPrepareHarvest() {
        scanAndPrepareHarvest(currentFarmIndex);
    }

    private void scanAndPrepareHarvest(int farmIndex) {
        logBlocks.clear();
        totalLogsInCurrentTree = 0;
        logsHarvestedInCurrentTree = 0;

        if (farmIndex >= farmPositions.size()) return;

        BlockPos farmPos = farmPositions.get(farmIndex);
        BlockPos[] saplingPositions = get2x2Positions(farmPos);

        for (BlockPos basePos : saplingPositions) {
            for (int y = 1; y <= 25; y++) {
                BlockPos logPos = basePos.up(y);
                Block block = mc.world.getBlockState(logPos).getBlock();
                if (isCurrentLog(block)) {
                    logBlocks.add(logPos);
                    totalLogsInCurrentTree++;
                } else if (!isCurrentLeaves(block) && !block.equals(Blocks.AIR)) {
                    break;
                }
            }
        }

        logBlocks.sort((pos1, pos2) -> Integer.compare(pos1.getY(), pos2.getY()));

        sendInfo("Found tree with " + totalLogsInCurrentTree + " logs to harvest.");
    }

    private boolean harvestTree() {
        stopBreaking();

        if (currentFarmIndex >= farmPositions.size()) {
            sendInfo("All farms processed.");
            return true;
        }

        FindItemResult axe = InvUtils.find(itemStack -> itemStack.getItem() instanceof AxeItem);
        if (!axe.found()) {
            ChatUtils.warning("No axe found in inventory!");
            return true;
        }

        if (logBlocks.isEmpty()) {
            sendInfo("Tree harvesting complete. Logs harvested: " + logsHarvestedInCurrentTree + "/" + totalLogsInCurrentTree);
            return true;
        }

        BlockPos targetLog = logBlocks.get(0);
        currentWorkingPos = targetLog;

        if (mc.world.getBlockState(targetLog).isAir()) {
            logBlocks.remove(0);
            logsHarvestedInCurrentTree++;
            return false;
        }

        Vec3d playerPos = mc.player.getEyePos();
        Vec3d blockCenter = Vec3d.ofCenter(targetLog);
        double distance = playerPos.distanceTo(blockCenter);

        if (distance > range.get() + 2.5) {
            ChatUtils.warning("Log too far away, skipping: " + targetLog);
            logBlocks.remove(0);
            return false;
        }

        if (rotate.get()) {
            lookAtBlockWithNoise(targetLog);
        }

        if (actionDelay <= 0) {
            InvUtils.swap(axe.slot(), false);
            breakBlock(targetLog);
            actionDelay = getRandomizedDelay(BREAK_DELAY);
            recordAction();
        }

        return false;
    }

    // Rest of the methods remain the same but with hardcoded safe values...
    private void handleAutoFeatures() {
        if (autoEat.get() && !isEating && !isPausedForEating) {
            int hunger = mc.player.getHungerManager().getFoodLevel();
            if (hunger <= hungerThreshold.get()) {
                startAutoEat();
            }
        }
        if (autoDrop.get() && dropTickCounter >= getRandomizedDelay(25)) {
            dropTickCounter = 0;
            handleAutoDrop();
        }
    }

    private void startAutoEat() {
        FindItemResult food = findBestFood();
        if (food.found()) {
            isPausedForEating = true;
            sendInfo("Pausing AutoTreeFarmer to eat.");

            InvUtils.swap(food.slot(), false);
            pressUseItemKey();
            isEating = true;
            eatingTicks = 0;
        }
    }

    private FindItemResult findBestFood() {
        Item[] foodItems = {
            Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
            Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.BREAD,
            Items.APPLE, Items.COOKED_CHICKEN, Items.COOKED_MUTTON,
            Items.BAKED_POTATO, Items.CARROT, Items.POTATO
        };

        for (Item food : foodItems) {
            FindItemResult result = InvUtils.find(food);
            if (result.found()) {
                return result;
            }
        }

        return new FindItemResult(-1, -1);
    }

    private void handleEating() {
        eatingTicks++;
        if (!mc.player.isUsingItem() || eatingTicks >= 40) {
            stopEating();
        }
    }

    private void stopEating() {
        isEating = false;
        eatingTicks = 0;
        releaseUseItemKey();
    }

    private void pressUseItemKey() {
        if (!isUsingItemKeyHeld) {
            KeyBinding.setKeyPressed(mc.options.useKey.getDefaultKey(), true);
            isUsingItemKeyHeld = true;
        }
    }

    private void releaseUseItemKey() {
        if (isUsingItemKeyHeld) {
            KeyBinding.setKeyPressed(mc.options.useKey.getDefaultKey(), false);
            isUsingItemKeyHeld = false;
        }
    }

    private void handleAutoDrop() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && shouldDropItem(stack.getItem())) {
                InvUtils.drop().slot(i);
                break;
            }
        }
    }

    private boolean shouldDropItem(Item item) {
        return item == getCurrentLogItem() || item == Items.STICK ||
            (saplingType.get() == SaplingType.SPRUCE && item == Items.SPRUCE_LOG) ||
            (saplingType.get() == SaplingType.JUNGLE && item == Items.JUNGLE_LOG);
    }

    private void checkAndStartRefill() {
        if (needsRefill(Items.BONE_MEAL)) {
            startRefill(Items.BONE_MEAL);
        } else if (needsRefill(getCurrentSapling())) {
            startRefill(getCurrentSapling());
        }
    }

    private boolean needsRefill(Item item) {
        return countItemInInventory(item) < refillThreshold.get();
    }

    private int countItemInInventory(Item item) {
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void startRefill(Item item) {
        if (refillRetryCount >= maxRefillRetries.get()) {
            sendInfo("Max refill retries reached for " + item.getName().getString());
            refillRetryCount = 0;
            return;
        }

        currentRefillItem = item;
        refillState = RefillState.OPEN_ORDERS;
        refillStageStart = System.currentTimeMillis();
        stacksCollected = 0;
        sendInfo("Starting refill for " + item.getName().getString());
    }

    private void handleRefill() {
        long now = System.currentTimeMillis();
        long timeInState = now - refillStageStart;
        int randomizedOrderDelay = getRandomizedOrderDelay();

        switch (refillState) {
            case OPEN_ORDERS -> {
                if (timeInState < 200) return;
                ChatUtils.sendPlayerMsg("/order");
                refillState = RefillState.WAIT_ORDERS_GUI;
                //Glazed copyright
                refillStageStart = now;
            }

            case WAIT_ORDERS_GUI -> {
                if (timeInState < randomizedOrderDelay) return;
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    refillState = RefillState.CLICK_SLOT_51;
                    refillStageStart = now;
                } else if (timeInState > 8000) {
                    retryRefill("Failed to open orders GUI");
                }
            }

            case CLICK_SLOT_51 -> {
                if (timeInState < randomizedOrderDelay) return;
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
                    retryRefill("Orders GUI closed unexpectedly");
                    return;
                }
                ScreenHandler handler = screen.getScreenHandler();
                if (handler.slots.size() > 51) {
                    mc.interactionManager.clickSlot(handler.syncId, 51, 0, SlotActionType.PICKUP, mc.player);
                    refillState = RefillState.WAIT_SECOND_GUI;
                    refillStageStart = now;
                } else {
                    retryRefill("Invalid GUI layout.");
                }
            }

            case WAIT_SECOND_GUI -> {
                if (timeInState < randomizedOrderDelay) return;
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    refillState = RefillState.CLICK_TARGET_ITEM;
                    refillStageStart = now;
                } else if (timeInState > 8000) {
                    retryRefill("Failed to open second GUI");
                }
            }

            case CLICK_TARGET_ITEM -> {
                if (timeInState < randomizedOrderDelay) return;
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
                    retryRefill("Second GUI closed unexpectedly");
                    return;
                }
                ScreenHandler handler = screen.getScreenHandler();

                boolean found = false;
                for (int i = 0; i < handler.slots.size(); i++) {
                    Slot slot = handler.slots.get(i);
                    if (slot.hasStack() && slot.getStack().getItem() == currentRefillItem) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                        refillState = RefillState.WAIT_THIRD_GUI;
                        refillStageStart = now;
                        found = true;
                        break;
                    }
                }
                if (!found && timeInState > 6000) {
                    retryRefill("Target item not found in GUI");
                }
            }

            case WAIT_THIRD_GUI -> {
                if (timeInState < randomizedOrderDelay) return;
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    refillState = RefillState.CLICK_SLOT_13;
                    refillStageStart = now;
                } else if (timeInState > 8000) {
                    retryRefill("Failed to open third GUI");
                }
            }

            case CLICK_SLOT_13 -> {
                if (timeInState < randomizedOrderDelay) return;
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
                    retryRefill("Third GUI closed unexpectedly");
                    return;
                }
                ScreenHandler handler = screen.getScreenHandler();

                if (handler.slots.size() > 13) {
                    Slot slot13 = handler.slots.get(13);
                    if (slot13.hasStack() && slot13.getStack().getItem() == Items.CHEST) {
                        mc.interactionManager.clickSlot(handler.syncId, 13, 0, SlotActionType.PICKUP, mc.player);
                    } else if (handler.slots.size() > 15) {
                        Slot slot15 = handler.slots.get(15);
                        if (slot15.hasStack()) {
                            mc.interactionManager.clickSlot(handler.syncId, 15, 0, SlotActionType.PICKUP, mc.player);
                        }
                    }
                    refillState = RefillState.WAIT_ITEMS_GUI;
                    refillStageStart = now;
                } else {
                    retryRefill("Invalid GUI layout");
                }
            }

            case WAIT_ITEMS_GUI -> {
                if (timeInState < randomizedOrderDelay) return;
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    refillState = RefillState.COLLECT_ITEMS;
                    refillStageStart = now;
                } else if (timeInState > 8000) {
                    retryRefill("Failed to open items GUI");
                }
            }

            case COLLECT_ITEMS -> {
                if (timeInState < randomizedOrderDelay) return;
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
                    retryRefill("Items GUI closed unexpectedly");
                    return;
                }
                ScreenHandler handler = screen.getScreenHandler();

                boolean foundItem = false;
                for (int i = 0; i < handler.slots.size(); i++) {
                    Slot slot = handler.slots.get(i);
                    if (slot.hasStack() && slot.getStack().getItem() == currentRefillItem) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                        stacksCollected++;
                        foundItem = true;
                        refillStageStart = now;

                        if (stacksCollected >= refillAmount.get()) {
                            finishRefill(true);
                            return;
                        }
                        break;
                    }
                }

                if (!foundItem) {
                    if (stacksCollected > 0) {
                        finishRefill(true);
                    } else {
                        retryRefill("No items found to collect");
                    }
                }
            }

            case CLOSE_GUI -> {
                if (mc.currentScreen != null) {
                    mc.currentScreen.close();
                }
                finishRefill(false);
            }

            case NONE -> {}
        }
    }

    private int getRandomizedOrderDelay() {
        int base = orderDelay.get();
        int variance = 400;
        return base + random.nextInt(variance * 2 + 1) - variance;
    }

    private void retryRefill(String reason) {
        refillRetryCount++;
        if (refillRetryCount < maxRefillRetries.get()) {
            sendInfo("Refill failed, Retrying...");
            if (mc.currentScreen != null) {
                mc.currentScreen.close();
            }
            refillState = RefillState.OPEN_ORDERS;
            refillStageStart = System.currentTimeMillis() + 1500 + random.nextInt(1000);
        } else {
            sendInfo("Refill failed after max retries");
            finishRefill(false);
        }
    }

    private void finishRefill(boolean success) {
        if (mc.currentScreen != null) {
            mc.currentScreen.close();
        }

        if (success) {
            sendInfo("Refill completed! Collected " + stacksCollected + " stacks of " +
                (currentRefillItem != null ? currentRefillItem.getName().getString() : "items"));
            refillRetryCount = 0;
        }

        refillState = RefillState.NONE;
        currentRefillItem = null;
        stacksCollected = 0;
    }

    private void autoRefillHotbar(Item targetItem, int minAmount) {
        try {
            int selectedSlot = VersionUtil.getSelectedSlot(mc.player);
            ItemStack stack = mc.player.getInventory().getStack(selectedSlot);

            if (stack == null || stack.isEmpty() || stack.getItem() != targetItem || stack.getCount() <= minAmount) {
                FindItemResult hotbarItem = InvUtils.findInHotbar(targetItem);

                if (!hotbarItem.found()) {
                    FindItemResult invItem = InvUtils.find(targetItem);
                    if (invItem.found()) {
                        InvUtils.move().from(invItem.slot()).to(selectedSlot);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private Item getCurrentSapling() {
        return switch (saplingType.get()) {
            case SPRUCE -> Items.SPRUCE_SAPLING;
            case JUNGLE -> Items.JUNGLE_SAPLING;
        };
    }

    private Block getCurrentLog() {
        return switch (saplingType.get()) {
            case SPRUCE -> Blocks.SPRUCE_LOG;
            case JUNGLE -> Blocks.JUNGLE_LOG;
        };
    }

    private Item getCurrentLogItem() {
        return switch (saplingType.get()) {
            case SPRUCE -> Items.SPRUCE_LOG;
            case JUNGLE -> Items.JUNGLE_LOG;
        };
    }

    private Block getCurrentLeaves() {
        return switch (saplingType.get()) {
            case SPRUCE -> Blocks.SPRUCE_LEAVES;
            case JUNGLE -> Blocks.JUNGLE_LEAVES;
        };
    }

    private void lookAtBlockWithNoise(BlockPos pos) {
        if (random.nextDouble() * 100.0 < WRONG_LOOK_CHANCE) {
            BlockPos wrongTarget = pos.add(
                random.nextInt(3) - 1,
                random.nextBoolean() ? -1 : 0,
                random.nextInt(3) - 1
            );
            lookAtBlockWithOffset(wrongTarget);
            return;
        }

        lookAtBlockWithOffset(pos);
    }

    private void lookAtBlockWithOffset(BlockPos pos) {
        Vec3d basePos = Vec3d.ofCenter(pos);
        double noise = ROTATION_NOISE;

        double offsetX = (random.nextDouble() - 0.5) * noise;
        double offsetY = (random.nextDouble() - 0.5) * noise * 0.5;
        double offsetZ = (random.nextDouble() - 0.5) * noise;

        Vec3d targetPos = basePos.add(offsetX, offsetY, offsetZ);
        Vec3d playerPos = mc.player.getEyePos();
        Vec3d direction = targetPos.subtract(playerPos).normalize();

        double yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z));
        double pitch = Math.toDegrees(-Math.asin(direction.y));

        mc.player.setYaw((float) yaw);
        mc.player.setPitch((float) pitch);
    }

    private void breakBlock(BlockPos pos) {
        if (mc.interactionManager != null) {
            mc.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
            KeyBinding.setKeyPressed(mc.options.attackKey.getDefaultKey(), true);
        }
        isBreaking = true;
        currentBreakingPos = pos;
        currentBreakingStartTime = System.currentTimeMillis();
    }

    private void stopBreaking() {
        if (isBreaking) {
            KeyBinding.setKeyPressed(mc.options.attackKey.getDefaultKey(), false);
            isBreaking = false;
            currentBreakingPos = null;
            currentBreakingStartTime = 0;
        }
    }

    private void resetJump() {
        if (shouldBeJumping) {
            KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), false);
            shouldBeJumping = false;
        }
    }

    private void findFarmPositions() {
        farmPositions.clear();
        BlockPos playerPos = mc.player.getBlockPos();

        for (int x = -range.get(); x <= range.get(); x++) {
            for (int z = -range.get(); z <= range.get(); z++) {
                BlockPos pos = playerPos.add(x, 0, z);
                if (isValidFarmPosition(pos)) {
                    farmPositions.add(pos);
                }
            }
        }

        // Always randomize order for more natural behavior
        Collections.shuffle(farmPositions, random);
    }

    private boolean isValidFarmPosition(BlockPos pos) {
        BlockPos[] saplingPositions = get2x2Positions(pos);

        for (BlockPos saplingPos : saplingPositions) {
            BlockPos groundPos = saplingPos.down();
            Block groundBlock = mc.world.getBlockState(groundPos).getBlock();
            if (!isValidGroundBlock(groundBlock)) return false;

            Block currentBlock = mc.world.getBlockState(saplingPos).getBlock();
            if (!currentBlock.equals(Blocks.AIR) && !currentBlock.equals(getCurrentSapling()) && !isCurrentLog(currentBlock)) {
                return false;
            }

            for (int i = 1; i <= 8; i++) {
                Block aboveBlock = mc.world.getBlockState(saplingPos.up(i)).getBlock();
                if (!aboveBlock.equals(Blocks.AIR) && !isCurrentLog(aboveBlock) && !isCurrentLeaves(aboveBlock)) {
                    return false;
                }
            }
        }

        return true;
    }

    private BlockPos[] get2x2Positions(BlockPos basePos) {
        return new BlockPos[]{
            basePos,
            basePos.add(1, 0, 0),
            basePos.add(0, 0, 1),
            basePos.add(1, 0, 1)
        };
    }

    private boolean placeSaplings() {
        if (currentFarmIndex >= farmPositions.size()) return true;

        BlockPos farmPos = farmPositions.get(currentFarmIndex);
        BlockPos[] saplingPositions = get2x2Positions(farmPos);
        FindItemResult saplings = InvUtils.find(getCurrentSapling());

        if (!saplings.found()) return false;

        List<BlockPos> posList = new ArrayList<>(List.of(saplingPositions));
        Collections.shuffle(posList, random);
        BlockPos[] positions = posList.toArray(new BlockPos[0]);

        for (BlockPos pos : positions) {
            if (mc.world.getBlockState(pos).getBlock().equals(Blocks.AIR)) {
                currentWorkingPos = pos;

                Vec3d playerPos = mc.player.getPos();
                Vec3d blockPos = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                double distance = playerPos.distanceTo(blockPos);

                if (distance > range.get() + 1.5) {
                    continue;
                }

                if (rotate.get()) {
                    lookAtBlockWithNoise(pos);
                }

                if (actionDelay <= 0) {
                    InvUtils.swap(saplings.slot(), false);
                    BlockUtils.place(pos, saplings, rotate.get(), 50);
                    actionDelay = getRandomizedDelay(PLACE_DELAY);
                    recordAction();
                }

                return false;
            }
        }
        return true;
    }

    private boolean applyBoneMeal() {
        if (!useBoneMeal.get()) {
            resetJump();
            return true;
        }
        if (currentFarmIndex >= farmPositions.size()) {
            resetJump();
            return true;
        }

        BlockPos farmPos = farmPositions.get(currentFarmIndex);
        BlockPos[] saplingPositions = get2x2Positions(farmPos);
        FindItemResult boneMeal = InvUtils.find(Items.BONE_MEAL);

        if (!boneMeal.found()) {
            resetJump();
            return true;
        }

        if (!shouldBeJumping && random.nextInt(3) == 0) {
            KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), true);
            shouldBeJumping = true;
        }

        for (BlockPos pos : saplingPositions) {
            Block block = mc.world.getBlockState(pos).getBlock();

            if (block == Blocks.SPRUCE_SAPLING || block == Blocks.JUNGLE_SAPLING) {
                currentWorkingPos = pos;

                Vec3d playerPos = mc.player.getPos();
                Vec3d blockPos = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                double distance = playerPos.distanceTo(blockPos);

                if (distance > range.get() + 1.5) {
                    continue;
                }

                if (rotate.get()) {
                    lookAtBlockWithNoise(pos);

                    if (rotationStabilizeTicks < random.nextInt(2) + 1) {
                        rotationStabilizeTicks++;
                        return false;
                    }
                }

                if (actionDelay <= 0) {
                    InvUtils.swap(boneMeal.slot(), false);
                    Vec3d hitPos = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    BlockHitResult hitResult = new BlockHitResult(hitPos, Direction.UP, pos, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                    actionDelay = getRandomizedDelay(Math.max(1, PLACE_DELAY / 2));
                    recordAction();
                }

                return false;
            }
        }

        resetJump();
        return true;
    }

    private boolean checkTreeGrown() {
        if (currentFarmIndex >= farmPositions.size()) return true;

        BlockPos farmPos = farmPositions.get(currentFarmIndex);
        BlockPos[] saplingPositions = get2x2Positions(farmPos);

        for (BlockPos pos : saplingPositions) {
            Block block = mc.world.getBlockState(pos).getBlock();
            if (isCurrentLog(block)) {
                return true;
            }
        }

        return false;
    }

    private void recordAction() {
        long now = System.currentTimeMillis();
        if (now - lastActionTime < 150) {
            consecutiveActions++;
        } else {
            consecutiveActions = 0;
        }
        lastActionTime = now;
    }

    private void moveToNextFarm() {
        currentFarmIndex++;
        rotationStabilizeTicks = 0;
        logBlocks.clear();
        totalLogsInCurrentTree = 0;
        logsHarvestedInCurrentTree = 0;

        if (currentFarmIndex >= farmPositions.size()) {
            currentFarmIndex = 0;
            findFarmPositions();
        }
    }

    private boolean isValidGroundBlock(Block block) {
        return block.equals(Blocks.GRASS_BLOCK) ||
            block.equals(Blocks.DIRT) ||
            block.equals(Blocks.COARSE_DIRT) ||
            block.equals(Blocks.PODZOL) ||
            block.equals(Blocks.MYCELIUM);
    }

    private boolean isCurrentLog(Block block) {
        return block.equals(getCurrentLog());
    }

    private boolean isCurrentLeaves(Block block) {
        return block.equals(getCurrentLeaves());
    }

    @Override
    public String getInfoString() {
        if (isEating) {
            return "Eating food";
        }
        if (isPausedForEating) {
            return "Paused (Eating)";
        }
        if (isPaused) {
            return "Paused (Human-like)";
        }
        if (refillState != RefillState.NONE) {
            return "Refill: " + refillState.toString().toLowerCase().replace("_", " ");
        }

        String stateStr = currentState.toString().toLowerCase().replace("_", " ");

        if (currentState == FarmState.HARVESTING && totalLogsInCurrentTree > 0) {
            stateStr += " (" + logsHarvestedInCurrentTree + "/" + totalLogsInCurrentTree + " logs)";
        }

        return stateStr + " [" + (currentFarmIndex + 1) + "/" + farmPositions.size() + " farms]";
    }

    public enum SaplingType {
        SPRUCE,
        JUNGLE
    }

    private enum FarmState {
        ANALYZING_CURRENT_STATE,
        FINDING_POSITIONS,
        PLACING_SAPLINGS,
        APPLYING_BONEMEAL,
        WAITING_FOR_GROWTH,
        HARVESTING
    }

    private enum FarmAnalysis {
        EMPTY_READY_FOR_PLANTING,
        HAS_SAPLINGS,
        HAS_GROWN_TREE,
        INVALID_OR_BLOCKED
    }

    private enum RefillState {
        NONE,
        OPEN_ORDERS,
        WAIT_ORDERS_GUI,
        CLICK_SLOT_51,
        WAIT_SECOND_GUI,
        CLICK_TARGET_ITEM,
        WAIT_THIRD_GUI,
        CLICK_SLOT_13,
        WAIT_ITEMS_GUI,
        COLLECT_ITEMS,
        CLOSE_GUI
    }
}
