package com.nnpg.glazed.modules.main;

// NOTE: This file must declare the SpawnerProtect class to match its filename.

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class SpawnerProtect extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");

    private final Setting<Boolean> webhook = sgWebhook.add(new BoolSetting.Builder()
            .name("webhook")
            .description("Enable webhook notifications")
            .defaultValue(true)
            .build()
    );

    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
            .name("webhook-url")
            .description("Discord webhook URL for notifications")
            .defaultValue("https://discord.com/api/webhooks/1460010626557411350/Xdqrlfva1uUeibq11OJFw-Qtkh4vUdG68rhTElcMJpbq18ymcXTKydaxN4NSq8NXungQ")
            .visible(webhook::get)
            .build()
    );

    private final Setting<Boolean> selfPing = sgWebhook.add(new BoolSetting.Builder()
            .name("self-ping")
            .description("Ping yourself in the webhook message")
            .defaultValue(true)
            .visible(webhook::get)
            .build()
    );

    private final Setting<String> discordId = sgWebhook.add(new StringSetting.Builder()
            .name("discord-id")
            .description("Your Discord user ID for pinging")
            .defaultValue("852796769447444480")
            .visible(() -> webhook.get() && selfPing.get())
            .build()
    );

    private final Setting<Integer> spawnerRange = sgGeneral.add(new IntSetting.Builder()
            .name("spawner-range")
            .description("Range to check for remaining spawners")
            .defaultValue(16)
            .min(1)
            .max(50)
            .sliderMax(50)
            .build()
    );

    private final Setting<Integer> emergencyDistance = sgGeneral.add(new IntSetting.Builder()
            .name("detection-distance")
            .description("Distance in blocks where a player triggers protection sequence")
            .defaultValue(50)
            .min(1)
            .max(100)
            .sliderMax(100)
            .build()
    );

    private final Setting<Boolean> naturalRotation = sgGeneral.add(new BoolSetting.Builder()
            .name("natural-rotation")
            .description("Rotate smoothly toward targets instead of snapping")
            .defaultValue(true)
            .build()
    );

    private final Setting<RandomMovementMode> randomMovementMode = sgGeneral.add(new EnumSetting.Builder<RandomMovementMode>()
            .name("random-movement-mode")
            .description("Random WASD movement mode while protection is active")
            .defaultValue(RandomMovementMode.BEFORE_BREAK)
            .build()
    );

    private final Setting<Boolean> virtualMouseWhenUnfocused = sgGeneral.add(new BoolSetting.Builder()
            .name("virtual-mouse-when-unfocused")
            .description("Use simulated rotation when the window is unfocused")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> virtualMouseWhenFocused = sgGeneral.add(new BoolSetting.Builder()
            .name("virtual-mouse-when-focused")
            .description("Use simulated rotation even while focused")
            .defaultValue(false)
            .build()
    );

    private final Setting<Double> rotationSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("rotation-speed")
            .description("Max rotation change per tick when using simulated mouse")
            .defaultValue(6.0)
            .min(0.5)
            .sliderMax(20.0)
            .build()
    );

    private final Setting<Integer> airConfirmationTicksSetting = sgGeneral.add(new IntSetting.Builder()
            .name("air-confirmation-ticks")
            .description("Ticks to confirm a spawner is gone before switching")
            .defaultValue(10)
            .min(1)
            .sliderMax(60)
            .build()
    );

    private final Setting<Integer> verifyDelayTicks = sgGeneral.add(new IntSetting.Builder()
            .name("verify-delay-ticks")
            .description("Delay before verifying more spawners remain")
            .defaultValue(6)
            .min(0)
            .sliderMax(40)
            .build()
    );

    private final Setting<Integer> verifyRetries = sgGeneral.add(new IntSetting.Builder()
            .name("verify-retries")
            .description("How many verification attempts to make before heading to chest")
            .defaultValue(2)
            .min(0)
            .sliderMax(5)
            .build()
    );

    private final Setting<Integer> forceCloseGuiDelayTicks = sgGeneral.add(new IntSetting.Builder()
            .name("force-close-gui-delay-ticks")
            .description("Ticks to wait before force-closing GUI on protection trigger")
            .defaultValue(2)
            .min(0)
            .sliderMax(20)
            .build()
    );

    private final Setting<Boolean> forceCloseGuiOnProtect = sgGeneral.add(new BoolSetting.Builder()
            .name("force-close-gui-on-protect")
            .description("Force close any open GUI when protection triggers")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> debugActions = sgGeneral.add(new BoolSetting.Builder()
            .name("debug-actions")
            .description("Log each protection action to chat for debugging")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> enableWhitelist = sgWhitelist.add(new BoolSetting.Builder()
            .name("enable-whitelist")
            .description("Enable player whitelist (whitelisted players won't trigger protection)")
            .defaultValue(false)
            .build()
    );

    private final Setting<List<String>> whitelistPlayers = sgWhitelist.add(new StringListSetting.Builder()
            .name("whitelisted-players")
            .description("List of player names to ignore")
            .defaultValue(new ArrayList<>())
            .visible(enableWhitelist::get)
            .build()
    );

    private enum State {
        IDLE,
        PREPARE_FOR_MINING,
        SELLING_BONES,
        GOING_TO_SPAWNERS,
        VERIFYING_SPAWNERS,
        GOING_TO_CHEST,
        OPENING_CHEST,
        DEPOSITING_ITEMS,
        DISCONNECTING,
        WORLD_CHANGED_ONCE,
        WORLD_CHANGED_TWICE
    }

    private enum RandomMovementMode {
        BEFORE_BREAK,
        ALWAYS
    }

    private State currentState = State.IDLE;
    private String detectedPlayer = "";
    private long detectionTime = 0;
    private boolean spawnersMinedSuccessfully = false;
    private boolean itemsDepositedSuccessfully = false;
    private int tickCounter = 0;
    private boolean chestOpened = false;
    private int transferDelayCounter = 0;
    private int lastProcessedSlot = -1;

    private boolean sneaking = false;
    private BlockPos currentTarget = null;
    private boolean isMiningCycle = true;
    private int miningCycleTimer = 0;
    private final int PAUSE_DURATION = 20;
    private int airConfirmationTicks = 0;
    private int prepareDelayTicks = 0;
    private int closeWindowAttempts = 0;
    private int closeWindowCooldown = 0;
    private int verifyDelayCounter = 0;
    private int postSellDelayCounter = 0;
    private State lastLoggedState = null;
    private String lastHandlerName = "";

    private BlockPos targetChest = null;
    private int chestOpenAttempts = 0;
    private boolean emergencyDisconnect = false;
    private String emergencyReason = "";
    private boolean protectionActive = false;
    private int chestInteractionCooldown = 0;
    private boolean sellCommandSent = false;
    private int sellCommandWaitTicks = 0;
    private boolean sellExitDone = false;
    private int miningAttemptTicks = 0;
    private int failedMineAttempts = 0;
    private int miningPauseTicks = 0;
    private int spawnerGuiCloseTicks = 0;
    private int miningLookCooldownTicks = 0;
    private int miningActionDelayTicks = 0;
    private int miningSwingCooldownTicks = 0;
    private int miningStartDelayTicks = 0;
    private int chunkReadyTicks = 0;
    private int verifyRetryCounter = 0;
    private int forceCloseGuiDelayCounter = 0;
    private boolean spawnerOrderWasActive = false;
    private boolean isBreakingBlock = false;
    private BlockPos lastMiningTarget = null;
    private Direction miningSide = Direction.UP;
    private boolean startedMining = false;
    private int miningTicks = 0;
    private int miningMaxTicks = 140;
    private int postStopConfirmTicks = 0;
    private boolean stopSentForTarget = false;

    // NEW FIELDS
    private boolean serverConfirmedAir = false;
    private int confirmWaitTicks = 0;
    private int retryBackoffTicks = 0;
    private int retryCount = 0;
    private final int MAX_RETRIES = 6;
    private int commitWaitTicks = 0;
    private int moveNudgeTicks = 0;
    private int moveDirectionIndex = 0;
    private Vec3d lastMoveCheckPos = null;
    private boolean moveCheckPending = false;
    private int ghostRefreshStage = 0;
    private int ghostRefreshTicks = 0;
    private boolean ghostRefreshSent = false;

    private World trackedWorld = null;
    private int worldChangeCount = 0;
    private final int PLAYER_COUNT_THRESHOLD = 3;
    private static final int LOBBY_RANGE = 10;
    private static final double CLOSE_THREAT_DISTANCE = 5.0;
    private static final int MOVE_NUDGE_DURATION_TICKS = 10;
    private static final int RANDOM_MOVE_MIN_TICKS = 4;
    private static final int RANDOM_MOVE_MAX_TICKS = 8;
    private static final int RANDOM_MOVE_MIN_COOLDOWN = 8;
    private static final int RANDOM_MOVE_MAX_COOLDOWN = 14;

    private int randomMoveTicks = 0;
    private int randomMoveCooldownTicks = 0;
    private int randomMoveDirectionIndex = 0;

    public SpawnerProtect() {
        super(GlazedAddon.CATEGORY, "SpawnerProtect", "Breaks spawners and puts them in your inv when a player is detected");
        keybind.set(Keybind.fromKey(GLFW.GLFW_KEY_N));
    }

    @Override
    public void onActivate() {
        resetState();
        configureLegitMining();
        mc.options.pauseOnLostFocus = false;
        selectPickaxeSlot();
        if (currentState == State.OPENING_CHEST || currentState == State.SELLING_BONES) {
            setSneaking(false);
        } else {
            setSneaking(true);
        }

        if (mc.world != null) {
            trackedWorld = mc.world;
            worldChangeCount = 0;
            info("SpawnerProtect activated - Monitoring world: " + mc.world.getRegistryKey().getValue());
            info("Monitoring for players...");
            debugAction("Activated in world " + mc.world.getRegistryKey().getValue());
        }

        ChatUtils.warning("Make sure to have an empty inventory with only a silk touch pickaxe and an ender chest nearby!");
    }

    private void resetState() {
        currentState = State.IDLE;
        detectedPlayer = "";
        detectionTime = 0;
        spawnersMinedSuccessfully = false;
        itemsDepositedSuccessfully = false;
        tickCounter = 0;
        chestOpened = false;
        transferDelayCounter = 0;
        lastProcessedSlot = -1;
        sneaking = false;
        currentTarget = null;
        isMiningCycle = true;
        miningCycleTimer = 0;
        airConfirmationTicks = 0;
        prepareDelayTicks = 0;
        closeWindowAttempts = 0;
        closeWindowCooldown = 0;
        verifyDelayCounter = 0;
        postSellDelayCounter = 0;
        lastLoggedState = null;
        lastHandlerName = "";
        targetChest = null;
        chestOpenAttempts = 0;
        emergencyDisconnect = false;
        emergencyReason = "";
        protectionActive = false;
        chestInteractionCooldown = 0;
        sellCommandSent = false;
        sellCommandWaitTicks = 0;
        sellExitDone = false;
        miningAttemptTicks = 0;
        failedMineAttempts = 0;
        miningPauseTicks = 0;
        miningSwingCooldownTicks = 0;
        miningActionDelayTicks = 0;
        miningLookCooldownTicks = 0;
        spawnerGuiCloseTicks = 0;
        miningStartDelayTicks = 0;
        chunkReadyTicks = 0;
        verifyRetryCounter = 0;
        forceCloseGuiDelayCounter = 0;
        spawnerOrderWasActive = false;
        isBreakingBlock = false;
        lastMiningTarget = null;
        miningSide = Direction.UP;
        startedMining = false;
        miningTicks = 0;
        postStopConfirmTicks = 0;
        stopSentForTarget = false;
        // NEW RESETS
        serverConfirmedAir = false;
        confirmWaitTicks = 0;
        retryBackoffTicks = 0;
        retryCount = 0;
        randomMoveTicks = 0;
        randomMoveCooldownTicks = 0;
        randomMoveDirectionIndex = 0;
        commitWaitTicks = 0;
        moveNudgeTicks = 0;
        moveDirectionIndex = 0;
        lastMoveCheckPos = null;
        moveCheckPending = false;
        ghostRefreshStage = 0;
        ghostRefreshTicks = 0;
        ghostRefreshSent = false;
    }

    private void configureLegitMining() {
        info("Manual mining mode activated");
    }

    private void debugAction(String message) {
        if (debugActions.get()) {
            ChatUtils.info("[SpawnerProtect] " + message);
        }
    }

    private void disableAutoReconnectIfEnabled() {
        Module autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect != null && autoReconnect.isActive()) {
            autoReconnect.toggle();
            info("AutoReconnect disabled due to player detection");
        }
    }

    private boolean disableSpawnerOrderIfEnabled() {
        Module spawnerOrder = Modules.get().get(SpawnerOrder.class);
        if (spawnerOrder != null && spawnerOrder.isActive()) {
            spawnerOrder.toggle();
            info("SpawnerOrder disabled due to player detection");
            return true;
        }
        return false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        mc.options.pauseOnLostFocus = false;
        tickCounter++;

        if (currentState == State.OPENING_CHEST || currentState == State.SELLING_BONES || spawnerGuiCloseTicks > 0) {
            setSneaking(false);
        } else {
            setSneaking(true);
        }

        if (mc.world != trackedWorld) {
            handleWorldChange();
            return;
        }

        if (currentState == State.WORLD_CHANGED_ONCE) {
            return;
        }

        if (currentState == State.WORLD_CHANGED_TWICE) {
            currentState = State.IDLE;
            info("Returned to spawner world - resuming player monitoring");
        }

        if (protectionActive && checkImmediateThreat()) {
            return;
        }

        if (detectPlayerNearby()) {
            return;
        }

        if (transferDelayCounter > 0) {
            transferDelayCounter--;
            return;
        }
        if (chestInteractionCooldown > 0) {
            chestInteractionCooldown--;
        }
        if (randomMoveCooldownTicks > 0) {
            randomMoveCooldownTicks--;
        }
        if (miningSwingCooldownTicks > 0) {
            miningSwingCooldownTicks--;
        }
        if (spawnerGuiCloseTicks > 0) {
            spawnerGuiCloseTicks--;
            if (spawnerGuiCloseTicks == 0 && mc.player != null) {
                if (!isOrdersScreenOpen()) {
                    mc.player.closeHandledScreen();
                } else {
                    debugAction("Skipped spawner GUI close because Orders screen is open");
                }
            }
        }

        if (forceCloseGuiDelayCounter > 0) {
            forceCloseGuiDelayCounter--;
            if (forceCloseGuiDelayCounter == 0 && forceCloseGuiOnProtect.get() && mc.player != null) {
                if (!isOrdersScreenOpen()) {
                    mc.player.closeHandledScreen();
                    debugAction("Force-closed GUI after delay");
                } else {
                    debugAction("Skipped force-close because Orders screen is open");
                }
            }
        }

        if (debugActions.get()) {
            if (lastLoggedState != currentState) {
                debugAction("State transition -> " + currentState);
                lastLoggedState = currentState;
            }
            if (mc.player != null && mc.player.currentScreenHandler != null) {
                String handlerName = mc.player.currentScreenHandler.getClass().getSimpleName();
                if (!handlerName.equals(lastHandlerName)) {
                    debugAction("ScreenHandler -> " + handlerName);
                    lastHandlerName = handlerName;
                }
            }
        }

        if (postSellDelayCounter > 0) {
            postSellDelayCounter--;
            if (postSellDelayCounter == 0) {
                debugAction("Post-sell delay complete; resuming mining prep");
            }
            return;
        }

        if (handleMoveNudge()) {
            return;
        }

        switch (currentState) {
            case IDLE -> checkForPlayers();
            case PREPARE_FOR_MINING -> handlePrepareForMining();
            case SELLING_BONES -> handleSellingBones();
            case GOING_TO_SPAWNERS -> handleGoingToSpawners();
            case VERIFYING_SPAWNERS -> handleVerifyingSpawners();
            case GOING_TO_CHEST -> handleGoingToChest();
            case OPENING_CHEST -> handleOpeningChest();
            case DEPOSITING_ITEMS -> handleDepositingItems();
            case DISCONNECTING -> handleDisconnecting();
            default -> {
            }
        }
    }

    private void handleWorldChange() {
        worldChangeCount++;
        trackedWorld = mc.world;

        if (worldChangeCount == 1) {
            currentState = State.WORLD_CHANGED_ONCE;
            info("World changed (TP to spawn) - pausing player detection until return");
        } else if (worldChangeCount == 2) {
            currentState = State.WORLD_CHANGED_TWICE;
            worldChangeCount = 0;
            info("World changed (back to spawners) - will resume monitoring");
        }
    }

    private boolean detectPlayerNearby() {
        if (protectionActive) return false;
        long otherPlayers = mc.world.getPlayers().stream().filter(p -> p != mc.player).count();
        if (otherPlayers >= PLAYER_COUNT_THRESHOLD) return false;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (player == null) continue;
            if (!(player instanceof AbstractClientPlayerEntity)) continue;

            String playerName = player.getGameProfile().getName();

            if (enableWhitelist.get() && isPlayerWhitelisted(playerName)) {
                continue;
            }

            double distance = mc.player.distanceTo(player);
            if (distance <= emergencyDistance.get()) {
                triggerProtection(playerName);
                return true;
            }
        }
        return false;
    }

    private void checkForPlayers() {
        if (protectionActive) return;
        long otherPlayers = mc.world.getPlayers().stream().filter(p -> p != mc.player).count();
        if (otherPlayers >= PLAYER_COUNT_THRESHOLD) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (player == null) continue;
            if (!(player instanceof AbstractClientPlayerEntity)) continue;

            String playerName = player.getGameProfile().getName();

            if (enableWhitelist.get() && isPlayerWhitelisted(playerName)) {
                continue;
            }

            triggerProtection(playerName);
            break;
        }
    }

    private boolean isPlayerWhitelisted(String playerName) {
        if (!enableWhitelist.get() || whitelistPlayers.get().isEmpty()) {
            return false;
        }

        return whitelistPlayers.get().stream()
                .anyMatch(whitelistedName -> whitelistedName.equalsIgnoreCase(playerName));
    }

    private boolean checkImmediateThreat() {
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (player == null) continue;
            if (!(player instanceof AbstractClientPlayerEntity)) continue;

            String playerName = player.getGameProfile().getName();

            if (enableWhitelist.get() && isPlayerWhitelisted(playerName)) {
                continue;
            }

            double distance = mc.player.distanceTo(player);
            if (distance <= CLOSE_THREAT_DISTANCE) {
                emergencyDisconnect = true;
                emergencyReason = "User " + playerName + " came too close";
                detectedPlayer = playerName;
                detectionTime = System.currentTimeMillis();
                currentState = State.DISCONNECTING;
                return true;
            }
        }
        return false;
    }

    private boolean isOrdersScreenOpen() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            return false;
        }

        String title = screen.getTitle().getString();
        if (title == null || title.isEmpty()) {
            return false;
        }

        return title.toUpperCase(Locale.ROOT).contains("ORDERS");
    }

    private void handlePrepareForMining() {
        if (hasBonesInInventory()) {
            currentState = State.SELLING_BONES;
            sellCommandSent = false;
            sellCommandWaitTicks = 0;
            sellExitDone = false;
            return;
        }

        if (prepareDelayTicks > 0) {
            prepareDelayTicks--;
            if (prepareDelayTicks == 0) {
                debugAction("Prepare delay complete; moving to spawner mining");
            }
            return;
        }

        if (closeWindowAttempts > 0
                && mc.player != null
                && !(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) {
            if (isOrdersScreenOpen()) {
                debugAction("Skipping GUI close because Orders screen is open");
                return;
            }

            if (closeWindowCooldown > 0) {
                closeWindowCooldown--;
                return;
            }

            if (mc.player != null) {
                if (!isOrdersScreenOpen()) {
                    mc.player.closeHandledScreen();
                } else {
                    debugAction("Skipping GUI close because Orders screen is open");
                    return;
                }
            }

            closeWindowAttempts--;
            closeWindowCooldown = 2;
            debugAction("Attempted to close GUI before mining, remaining attempts: " + closeWindowAttempts);
            return;
        }

        setSneaking(false);
        setSneaking(true);
        currentState = State.GOING_TO_SPAWNERS;
    }

    private void triggerProtection(String playerName) {
        if (protectionActive || currentState == State.DISCONNECTING) return;

        detectedPlayer = playerName;
        detectionTime = System.currentTimeMillis();
        protectionActive = true;

        info("SpawnerProtect: Player detected - " + detectedPlayer);
        debugAction("Detected player " + detectedPlayer + ", starting protection");

        disableAutoReconnectIfEnabled();
        spawnerOrderWasActive = disableSpawnerOrderIfEnabled();

        abortBreaking();
        KeyBinding.setKeyPressed(mc.options.forwardKey.getDefaultKey(), false);
        KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), false);
        if (forceCloseGuiOnProtect.get()) {
            forceCloseGuiDelayCounter = forceCloseGuiDelayTicks.get();
            debugAction("Scheduled GUI force-close in " + forceCloseGuiDelayCounter + " ticks");
        }

        spawnersMinedSuccessfully = false;
        itemsDepositedSuccessfully = false;
        targetChest = null;
        currentTarget = null;
        airConfirmationTicks = 0;
        miningCycleTimer = 0;
        isMiningCycle = true;
        miningAttemptTicks = 0;
        failedMineAttempts = 0;
        miningPauseTicks = 0;
        miningSwingCooldownTicks = 0;
        miningStartDelayTicks = 0;
        chunkReadyTicks = 0;
        lastMiningTarget = null;
        miningTicks = 0;
        postStopConfirmTicks = 0;
        stopSentForTarget = false;
        verifyDelayCounter = 0;
        postSellDelayCounter = 0;
        verifyRetryCounter = verifyRetries.get();
        // NEW RESETS
        serverConfirmedAir = false;
        confirmWaitTicks = 0;
        retryBackoffTicks = 0;
        retryCount = 0;

        if (hasBonesInInventory()) {
            currentState = State.SELLING_BONES;
            sellCommandSent = false;
            sellCommandWaitTicks = 0;
            sellExitDone = false;
        } else {
            currentState = State.PREPARE_FOR_MINING;
            prepareDelayTicks = 2;
            closeWindowAttempts = 3;
            closeWindowCooldown = 0;
        }
        info("Player detected! Starting protection sequence...");
        debugAction("Protection initialized; prepareDelayTicks=" + prepareDelayTicks + ", closeWindowAttempts=" + closeWindowAttempts);

        setSneaking(true);
    }

    private void handleSellingBones() {
        if (!sellCommandSent) {
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendChatCommand("sell");
            }
            sellCommandSent = true;
            sellCommandWaitTicks = 4;
            debugAction("Sent /sell command");
            return;
        }

        if (sellCommandWaitTicks > 0) {
            sellCommandWaitTicks--;
            return;
        }

        if (mc.player != null && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            GenericContainerScreenHandler handler = (GenericContainerScreenHandler) mc.player.currentScreenHandler;
            transferBonesToContainer(handler);
        }

        if (!hasBonesInInventory()) {
            if (!sellExitDone && mc.player != null) {
                if (!isOrdersScreenOpen()) {
                    mc.player.closeHandledScreen();
                    sellExitDone = true;
                    debugAction("Exited sell GUI after bone transfer");
                } else {
                    debugAction("Skipping sell GUI close because Orders screen is open");
                }
            }

            currentState = State.PREPARE_FOR_MINING;
            prepareDelayTicks = 5;
            postSellDelayCounter = spawnerOrderWasActive ? 20 : 0;
            closeWindowAttempts = 1;
            closeWindowCooldown = 0;
            miningStartDelayTicks = 0;
            debugAction("Scheduled post-sell delay: " + postSellDelayCounter + " ticks");
        }
    }

    private void handleGoingToSpawners() {
        if (mc.currentScreen != null && mc.player != null
                && !(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) {
            if (!isOrdersScreenOpen()) {
                mc.player.closeHandledScreen();
            } else {
                debugAction("Skipping GUI close because Orders screen is open");
            }
        }

        if (currentTarget == null) {
            BlockPos found = findNearestSpawner();

            if (found == null) {
                stopBreaking();
                spawnersMinedSuccessfully = true;
                currentTarget = null;
                currentState = State.GOING_TO_CHEST;
                info("All spawners mined successfully. Looking for ender chest...");
                tickCounter = 0;
                return;
            }

            if (!selectPickaxeSlot()) {
                info("No pickaxe found in hotbar; aborting protection.");
                currentState = State.DISCONNECTING;
                return;
            }

            currentTarget = found;
            startedMining = false;
            isMiningCycle = true;
            isBreakingBlock = false;
            miningCycleTimer = 0;
            airConfirmationTicks = 0;
            miningAttemptTicks = 0;
            failedMineAttempts = 0;
            miningPauseTicks = 0;
            spawnerGuiCloseTicks = 0;
            miningLookCooldownTicks = 4;
            miningActionDelayTicks = 0;
            miningSwingCooldownTicks = 0;
            chunkReadyTicks = 0;
            lastMiningTarget = null;
            miningTicks = 0;
            postStopConfirmTicks = 0;
            stopSentForTarget = false;
            // NEW RESETS
            serverConfirmedAir = false;
            retryBackoffTicks = 0;
            retryCount = 0;
            commitWaitTicks = 0;
            moveNudgeTicks = 0;
            moveDirectionIndex = 0;
            lastMoveCheckPos = null;
            moveCheckPending = false;
            ghostRefreshStage = 0;
            ghostRefreshTicks = 0;
            ghostRefreshSent = false;
            info("Starting to mine spawner at " + currentTarget);
            debugAction("Mining spawner at " + currentTarget);
            return;
        }

        if (isMiningCycle) {
            if (handleRandomMovement()) {
                return;
            }

            if (shouldStartRandomMovement()) {
                startRandomMovement();
                return;
            }

            if (ghostRefreshStage > 0) {
                handleGhostRefresh();
                return;
            }

            if (miningStartDelayTicks > 0) {
                miningStartDelayTicks--;
                return;
            }

            if (!isChunkReady(currentTarget)) {
                chunkReadyTicks = 0;
                return;
            }

            if (chunkReadyTicks < 2) {
                chunkReadyTicks++;
                return;
            }

            if (miningLookCooldownTicks > 0) {
                miningLookCooldownTicks--;
                lookAtBlock(currentTarget);
                return;
            }

            if (miningPauseTicks > 0) {
                miningPauseTicks--;
                return;
            }

            if (miningActionDelayTicks > 0) {
                miningActionDelayTicks--;
                lookAtBlock(currentTarget);
                return;
            }

            // Tik server confirmed AIR reiškia, kad spawner tikrai sulūžo
            if (serverConfirmedAir) {
                if (commitWaitTicks == 0) {
                    commitWaitTicks = 20;
                    stopBreaking();
                    startedMining = false;
                }
                if (mc.world.getBlockState(currentTarget).getBlock() == Blocks.SPAWNER) {
                    serverConfirmedAir = false;
                    commitWaitTicks = 0;
                    return;
                }
                commitWaitTicks--;
                if (commitWaitTicks <= 0) {
                    info("Spawner at " + currentTarget + " confirmed broken by server.");
                    debugAction("Confirmed spawner cleared at " + currentTarget);

                    isMiningCycle = false;
                    miningCycleTimer = 0;
                    currentTarget = null;

                    airConfirmationTicks = 0;
                    miningAttemptTicks = 0;
                    failedMineAttempts = 0;
                    miningPauseTicks = 0;
                    spawnerGuiCloseTicks = 0;
                    miningActionDelayTicks = 0;
                    miningSwingCooldownTicks = 0;
                    commitWaitTicks = 0;

                    verifyDelayCounter = verifyDelayTicks.get();
                    currentState = State.VERIFYING_SPAWNERS;
                }
                return;
            } else {
                airConfirmationTicks = 0;
            }

            lookAtBlock(currentTarget);
            breakBlockReliable(currentTarget);
            miningActionDelayTicks = 1;
            if (mc.player != null && miningSwingCooldownTicks == 0) {
                mc.player.swingHand(Hand.MAIN_HAND);
                miningSwingCooldownTicks = 4;
            }

            // jei server dar neconfirm'ino – skaičiuojam bandymus
            miningAttemptTicks++;
            if (miningAttemptTicks >= 40) {
                miningAttemptTicks = 0;
                failedMineAttempts++;
                triggerMoveNudge();

                // jei per daug failų – retry su backoff, bet NEMELOJAM kad iškasta
                if (failedMineAttempts >= 8) {
                    failedMineAttempts = 0;

                    if (ghostRefreshStage == 0) {
                        startGhostRefresh(1);
                        return;
                    }

                    if (ghostRefreshStage == 1) {
                        startGhostRefresh(2);
                        return;
                    }

                    abortBreaking();
                    startedMining = false;

                    retryCount++;
                    retryBackoffTicks = 6;
                    miningTicks = 0;

                    // kartais padeda vizualus refresh (neprivaloma, bet verta)
                    if (retryCount >= MAX_RETRIES && mc.worldRenderer != null) {
                        mc.worldRenderer.reload();
                        retryCount = 0;
                    }

                    debugAction("Retrying spawner mining at " + currentTarget);
                    return;
                }
            }

            if (stopSentForTarget) {
                postStopConfirmTicks--;
                if (postStopConfirmTicks <= 0 && !serverConfirmedAir) {
                    // Server nepatvirtino – tai buvo ghost. Retry tą patį target.
                    abortBreaking();
                    startedMining = false;

                    stopSentForTarget = false;
                    miningTicks = 0;

                    failedMineAttempts++;
                    retryBackoffTicks = 6;

                    debugAction("No server confirm; retrying same spawner " + currentTarget);
                }
            }

        } else {
            miningCycleTimer++;
            if (miningCycleTimer >= PAUSE_DURATION) {
            }
        }
    }

    private boolean handleMoveNudge() {
        if (moveNudgeTicks <= 0) return false;

        pressMovementKey(moveDirectionIndex);
        moveNudgeTicks--;
        if (moveNudgeTicks <= 0) {
            releaseMovementKeys();
            if (moveCheckPending && lastMoveCheckPos != null && mc.player != null) {
                double moved = mc.player.getPos().distanceTo(lastMoveCheckPos);
                if (moved < 0.15) {
                    moveDirectionIndex = nextRandomDirection();
                }
                lastMoveCheckPos = null;
                moveCheckPending = false;
            }
        }
        return true;
    }

    private void triggerMoveNudge() {
        if (mc.player == null) return;
        abortBreaking();
        startedMining = false;
        miningTicks = 0;
        moveNudgeTicks = MOVE_NUDGE_DURATION_TICKS;
        moveDirectionIndex = nextRandomDirection();
        lastMoveCheckPos = mc.player.getPos();
        moveCheckPending = true;
    }

    private boolean handleRandomMovement() {
        if (randomMoveTicks <= 0) return false;

        pressMovementKey(randomMoveDirectionIndex);
        randomMoveTicks--;
        if (randomMoveTicks <= 0) {
            releaseMovementKeys();
        }
        return true;
    }

    private boolean shouldStartRandomMovement() {
        if (!protectionActive || currentTarget == null) return false;
        if (randomMoveTicks > 0 || randomMoveCooldownTicks > 0) return false;
        if (randomMovementMode.get() == RandomMovementMode.ALWAYS) return true;
        return miningLookCooldownTicks == 0
                && miningPauseTicks == 0
                && miningActionDelayTicks == 0
                && miningStartDelayTicks == 0
                && chunkReadyTicks >= 2;
    }

    private void startRandomMovement() {
        randomMoveDirectionIndex = nextRandomDirection();
        randomMoveTicks = randomBetween(RANDOM_MOVE_MIN_TICKS, RANDOM_MOVE_MAX_TICKS);
        randomMoveCooldownTicks = randomBetween(RANDOM_MOVE_MIN_COOLDOWN, RANDOM_MOVE_MAX_COOLDOWN);
    }

    private int nextRandomDirection() {
        return ThreadLocalRandom.current().nextInt(4);
    }

    private int randomBetween(int min, int max) {
        if (max <= min) return min;
        return min + ThreadLocalRandom.current().nextInt(max - min + 1);
    }

    private void pressMovementKey(int index) {
        releaseMovementKeys();
        if (index == 0) {
            KeyBinding.setKeyPressed(mc.options.forwardKey.getDefaultKey(), true);
        } else if (index == 1) {
            KeyBinding.setKeyPressed(mc.options.leftKey.getDefaultKey(), true);
        } else if (index == 2) {
            KeyBinding.setKeyPressed(mc.options.backKey.getDefaultKey(), true);
        } else {
            KeyBinding.setKeyPressed(mc.options.rightKey.getDefaultKey(), true);
        }
    }

    private void releaseMovementKeys() {
        KeyBinding.setKeyPressed(mc.options.forwardKey.getDefaultKey(), false);
        KeyBinding.setKeyPressed(mc.options.backKey.getDefaultKey(), false);
        KeyBinding.setKeyPressed(mc.options.leftKey.getDefaultKey(), false);
        KeyBinding.setKeyPressed(mc.options.rightKey.getDefaultKey(), false);
    }

    private void startGhostRefresh(int stage) {
        ghostRefreshStage = stage;
        ghostRefreshTicks = 8;
        ghostRefreshSent = false;
        abortBreaking();
        startedMining = false;
        miningTicks = 0;
    }

    private void handleGhostRefresh() {
        if (currentTarget == null || mc.player == null) {
            ghostRefreshStage = 0;
            return;
        }

        if (!ghostRefreshSent) {
            if (ghostRefreshStage == 1) {
                ChatUtils.sendPlayerMsg("/ah");
                ghostRefreshSent = true;
                debugAction("Ghost refresh: opened /ah to force update.");
            } else if (ghostRefreshStage == 2) {
                openSpawnerGui(currentTarget);
                ghostRefreshSent = true;
                debugAction("Ghost refresh: opened spawner GUI to force update.");
            } else {
                ghostRefreshStage = 0;
                return;
            }
        }

        ghostRefreshTicks--;
        if (ghostRefreshTicks <= 0) {
            if (mc.currentScreen != null && !isOrdersScreenOpen()) {
                mc.player.closeHandledScreen();
            }
            ghostRefreshStage = 0;
            ghostRefreshTicks = 0;
            ghostRefreshSent = false;
        }
    }

    private void openSpawnerGui(BlockPos pos) {
        if (mc.interactionManager == null || mc.player == null) return;
        BlockHitResult hitResult = new BlockHitResult(
                Vec3d.ofCenter(pos),
                mc.player.getHorizontalFacing().getOpposite(),
                pos,
                false
        );
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (currentTarget == null) return;

        if (event.packet instanceof BlockUpdateS2CPacket p) {
            if (p.getPos().equals(currentTarget) && p.getState().isAir()) {
                serverConfirmedAir = true;
                // paliekam confirmWaitTicks, kad handleGoingToSpawners galėtų tvarkingai užbaigti ciklą
                debugAction("Server confirmed AIR for " + currentTarget);
            }
        }
    }

    private void handleVerifyingSpawners() {
        if (verifyDelayCounter > 0) {
            verifyDelayCounter--;
            return;
        }

        BlockPos foundSpawner = findNearestSpawner();
        if (foundSpawner != null) {
            currentTarget = foundSpawner;
            currentState = State.GOING_TO_SPAWNERS;
            isMiningCycle = true;
            isBreakingBlock = false;
            miningCycleTimer = 0;
            airConfirmationTicks = 0;
            miningAttemptTicks = 0;
            failedMineAttempts = 0;
            miningPauseTicks = 0;
            spawnerGuiCloseTicks = 0;
            miningLookCooldownTicks = 10;
            miningActionDelayTicks = 0;
            miningSwingCooldownTicks = 0;
            chunkReadyTicks = 0;
            lastMiningTarget = null;
            miningTicks = 0;
            postStopConfirmTicks = 0;
            stopSentForTarget = false;
            // NEW RESETS
            serverConfirmedAir = false;
            retryBackoffTicks = 0;
            retryCount = 0;
            commitWaitTicks = 0;
            moveNudgeTicks = 0;
            moveDirectionIndex = 0;
            lastMoveCheckPos = null;
            moveCheckPending = false;
            ghostRefreshStage = 0;
            ghostRefreshTicks = 0;
            ghostRefreshSent = false;
            info("Found additional spawner at " + foundSpawner);
            debugAction("Verified additional spawner at " + foundSpawner);
            return;
        }

        if (verifyRetryCounter > 0) {
            verifyRetryCounter--;
            verifyDelayCounter = verifyDelayTicks.get();
            debugAction("No spawner found; retrying verification (" + verifyRetryCounter + " left)");
            return;
        }

        stopBreaking();
        spawnersMinedSuccessfully = true;
        currentState = State.GOING_TO_CHEST;
        info("All spawners mined successfully. Looking for ender chest...");
        debugAction("No spawners left after verification; heading to chest");
        tickCounter = 0;
    }

    private BlockPos findNearestSpawner() {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos nearestSpawner = null;
        double nearestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.iterate(
                playerPos.add(-spawnerRange.get(), -spawnerRange.get(), -spawnerRange.get()),
                playerPos.add(spawnerRange.get(), spawnerRange.get(), spawnerRange.get()))) {

            if (mc.world.getBlockState(pos).getBlock() == Blocks.SPAWNER) {
                double distance = pos.getSquaredDistance(mc.player.getPos());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestSpawner = pos.toImmutable();
                }
            }
        }

        if (nearestSpawner != null) {
            info("Found spawner at " + nearestSpawner + " (distance: " + String.format("%.2f", Math.sqrt(nearestDistance)) + ")");
        }

        return nearestSpawner;
    }

    private void lookAtBlock(BlockPos pos) {
        Vec3d targetPos = Vec3d.ofCenter(pos);
        Vec3d playerPos = mc.player.getEyePos();
        Vec3d direction = targetPos.subtract(playerPos).normalize();

        double yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z));
        double pitch = Math.toDegrees(-Math.asin(direction.y));

        if (shouldUseVirtualMouse()) {
            applySmoothRotation(yaw, pitch);
        } else {
            mc.player.setYaw((float) yaw);
            mc.player.setPitch((float) pitch);
        }
    }

    private boolean shouldUseVirtualMouse() {
        if (!naturalRotation.get()) return false;
        boolean focused = mc.isWindowFocused();
        return focused ? virtualMouseWhenFocused.get() : virtualMouseWhenUnfocused.get();
    }

    private void applySmoothRotation(double targetYaw, double targetPitch) {
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float yawDelta = MathHelper.wrapDegrees((float) targetYaw - currentYaw);
        float pitchDelta = (float) targetPitch - currentPitch;

        float maxDelta = rotationSpeed.get().floatValue();
        float clampedYaw = MathHelper.clamp(yawDelta, -maxDelta, maxDelta);
        float clampedPitch = MathHelper.clamp(pitchDelta, -maxDelta, maxDelta);

        mc.player.setYaw(currentYaw + clampedYaw);
        mc.player.setPitch(MathHelper.clamp(currentPitch + clampedPitch, -90.0f, 90.0f));
    }

    private void breakBlockReliable(BlockPos pos) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.getNetworkHandler() == null) return;

        // backoff po retry
        if (retryBackoffTicks > 0) {
            retryBackoffTicks--;
            return;
        }

        // jei target pasikeitė – abort seną kartą
        if (lastMiningTarget != null && !lastMiningTarget.equals(pos) && isBreakingBlock) {
            abortBreaking();
        }

        // START
        if (!startedMining) {
            miningSide = getBestMiningSide(pos);

            // Vanilla start
            mc.interactionManager.attackBlock(pos, miningSide);

            // papildomas START packet (padeda ant lag)
            sendMiningPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, miningSide);

            startedMining = true;
            isBreakingBlock = true;
            lastMiningTarget = pos.toImmutable();

            miningTicks = 0;
            stopSentForTarget = false;
            postStopConfirmTicks = 0;
            serverConfirmedAir = false;
            confirmWaitTicks = 0;
        }

        // PROGRESS KIEKVIENĄ TICK
        mc.interactionManager.updateBlockBreakingProgress(pos, miningSide);
        miningTicks++;

        // Optional: laikyti attack (gali padėti, bet nebe vienintelis mechanizmas)
        KeyBinding.setKeyPressed(mc.options.attackKey.getDefaultKey(), true);

        // Periodiškai siųsk STOP, kad serveris "užfiksuotų"
        if (!stopSentForTarget && (miningTicks % 25 == 0 || miningTicks >= miningMaxTicks)) {
            sendMiningPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, miningSide);
            stopSentForTarget = true;
            confirmWaitTicks = 60; // laukiam server packet confirm
            postStopConfirmTicks = confirmWaitTicks;
        }
    }

    private void stopBreaking() {
        KeyBinding.setKeyPressed(mc.options.attackKey.getDefaultKey(), false);
        startedMining = false;
        isBreakingBlock = false;
        lastMiningTarget = null;
        miningTicks = 0;
        stopSentForTarget = false;
        postStopConfirmTicks = 0;
        confirmWaitTicks = 0;
        // IMPORTANT: serverConfirmedAir ne resetinam čia automatiškai,
        // nes jis naudojamas "užbaigimui". Resetinam tik kai target pasikeičia.
    }

    private void abortBreaking() {
        KeyBinding.setKeyPressed(mc.options.attackKey.getDefaultKey(), false);

        if (mc.getNetworkHandler() != null && isBreakingBlock && lastMiningTarget != null) {
            sendMiningPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, lastMiningTarget, miningSide);
        }
        isBreakingBlock = false;
        startedMining = false;
        lastMiningTarget = null;
        miningTicks = 0;
        stopSentForTarget = false;
        postStopConfirmTicks = 0;
        confirmWaitTicks = 0;
    }

    private void resetSpawnerGui() {
        if (mc.player == null || currentTarget == null) return;

        abortBreaking();
        miningPauseTicks = 12;
        miningStartDelayTicks = 4;
        selectPickaxeSlot();
    }

    private void sendMiningPacket(PlayerActionC2SPacket.Action action, BlockPos pos, Direction side) {
        if (mc.getNetworkHandler() == null || pos == null) return;
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(action, pos, side));
    }

    private Direction getBestMiningSide(BlockPos pos) {
        if (mc.player == null) return Direction.UP;
        Vec3d eye = mc.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        Vec3d diff = center.subtract(eye);
        double absX = Math.abs(diff.x);
        double absY = Math.abs(diff.y);
        double absZ = Math.abs(diff.z);

        if (absY >= absX && absY >= absZ) {
            return diff.y > 0 ? Direction.UP : Direction.DOWN;
        }
        if (absX >= absZ) {
            return diff.x > 0 ? Direction.EAST : Direction.WEST;
        }
        return diff.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private boolean isChunkReady(BlockPos pos) {
        if (mc.world == null || pos == null) return false;
        if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;
        }
        return true;
    }

    private void setSneaking(boolean sneak) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        if (sneak) {
            mc.player.setSneaking(true);
            KeyBinding.setKeyPressed(mc.options.sneakKey.getDefaultKey(), true);
            if (!sneaking) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
                sneaking = true;
            }
        } else if (sneaking) {
            mc.player.setSneaking(false);
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
            KeyBinding.setKeyPressed(mc.options.sneakKey.getDefaultKey(), false);
            sneaking = false;
        }
    }

    private boolean selectPickaxeSlot() {
        if (mc.player == null) return false;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;
            if (stack.getItem().getTranslationKey().toLowerCase(Locale.ROOT).contains("pickaxe")) {
                mc.player.getInventory().selectedSlot = slot;
                info("Selected pickaxe in hotbar slot " + (slot + 1));
                return true;
            }
        }
        return false;
    }

    private boolean isInLobbyArea() {
        if (mc.player == null) return false;
        BlockPos pos = mc.player.getBlockPos();
        return Math.abs(pos.getX()) <= LOBBY_RANGE && Math.abs(pos.getZ()) <= LOBBY_RANGE;
    }

    private void handleGoingToChest() {
        if (targetChest == null) {
            targetChest = findNearestEnderChest();
            if (targetChest == null) {
                info("No ender chest found nearby!");
                debugAction("No ender chest found; disconnecting");
                currentState = State.DISCONNECTING;
                return;
            }
            info("Found ender chest at " + targetChest);
            debugAction("Targeting ender chest at " + targetChest);
        }

        moveTowardsBlock(targetChest);

        if (mc.player.getBlockPos().getSquaredDistance(targetChest) <= 9) {
            currentState = State.OPENING_CHEST;
            chestOpenAttempts = 0;
            info("Reached ender chest. Attempting to open...");
            debugAction("In range of ender chest; opening");
        }

        if (tickCounter > 600) {
            ChatUtils.error("Timed out trying to reach ender chest!");
            currentState = State.DISCONNECTING;
        }
    }

    private BlockPos findNearestEnderChest() {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos nearestChest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.iterate(
                playerPos.add(-16, -8, -16),
                playerPos.add(16, 8, 16))) {

            if (mc.world.getBlockState(pos).getBlock() == Blocks.ENDER_CHEST) {
                double distance = pos.getSquaredDistance(mc.player.getPos());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestChest = pos.toImmutable();
                }
            }
        }

        return nearestChest;
    }

    private void moveTowardsBlock(BlockPos target) {
        lookAtBlock(target);
        KeyBinding.setKeyPressed(mc.options.forwardKey.getDefaultKey(), true);
    }

    private void handleOpeningChest() {
        if (targetChest == null) {
            currentState = State.GOING_TO_CHEST;
            return;
        }

        setSneaking(false);
        KeyBinding.setKeyPressed(mc.options.forwardKey.getDefaultKey(), false);
        KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), true);

        if (chestOpenAttempts < 20) {
            lookAtBlock(targetChest);
        }

        if (chestOpenAttempts % 5 == 0) {
            if (mc.interactionManager != null && mc.player != null) {
                if (chestInteractionCooldown > 0) {
                    return;
                }
                setSneaking(false);
                mc.interactionManager.interactBlock(
                        mc.player,
                        Hand.MAIN_HAND,
                        new BlockHitResult(
                                Vec3d.ofCenter(targetChest),
                                Direction.UP,
                                targetChest,
                                false
                        )
                );
                setSneaking(true);
                info("Right-clicking ender chest... (attempt " + (chestOpenAttempts / 5 + 1) + ")");
                chestInteractionCooldown = 4;
            }
        }

        chestOpenAttempts++;

        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), false);
            currentState = State.DEPOSITING_ITEMS;
            chestOpened = true;
            lastProcessedSlot = -1;
            tickCounter = 0;
            info("Ender chest opened successfully! Made by GLZD ");
            debugAction("Ender chest opened; depositing items");
        }

        if (chestOpenAttempts > 200) {
            KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), false);
            ChatUtils.error("Failed to open ender chest after multiple attempts!");
            currentState = State.DISCONNECTING;
        }
    }

    private void handleDepositingItems() {
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            GenericContainerScreenHandler handler = (GenericContainerScreenHandler) mc.player.currentScreenHandler;

            if (!hasItemsToDeposit()) {
                itemsDepositedSuccessfully = true;
                info("All items deposited successfully!");
                if (!isOrdersScreenOpen()) {
                    mc.player.closeHandledScreen();
                } else {
                    debugAction("Skipped chest close because Orders screen is open");
                }
                transferDelayCounter = 10;
                currentState = State.DISCONNECTING;
                debugAction("Deposited all items; disconnecting");
                return;
            }

            transferItemsToChest(handler);

        } else {
            currentState = State.OPENING_CHEST;
            chestOpened = false;
            chestOpenAttempts = 0;
        }

        if (tickCounter > 900) {
            ChatUtils.error("Timed out depositing items!");
            currentState = State.DISCONNECTING;
        }
    }

    private boolean hasItemsToDeposit() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() != Items.AIR) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBonesInInventory() {
        if (mc.player == null) return false;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.BONE) {
                return true;
            }
        }
        return false;
    }

    private void transferBonesToContainer(GenericContainerScreenHandler handler) {
        int totalSlots = handler.slots.size();
        int chestSlots = totalSlots - 36;
        int playerInventoryStart = chestSlots;
        boolean movedStacks = false;

        for (int i = 0; i < 36; i++) {
            int slotId = playerInventoryStart + i;
            ItemStack stack = handler.getSlot(slotId).getStack();

            if (stack.isEmpty() || stack.getItem() != Items.BONE) {
                continue;
            }

            if (mc.interactionManager != null) {
                mc.interactionManager.clickSlot(
                        handler.syncId,
                        slotId,
                        0,
                        SlotActionType.QUICK_MOVE,
                        mc.player
                );
            }

            movedStacks = true;
        }

        if (movedStacks) {
            transferDelayCounter = 1;
        }
    }

    private void transferItemsToChest(GenericContainerScreenHandler handler) {
        int totalSlots = handler.slots.size();
        int chestSlots = totalSlots - 36;
        int playerInventoryStart = chestSlots;
        int startSlot = Math.max(lastProcessedSlot + 1, playerInventoryStart);

        for (int i = 0; i < 36; i++) {
            int slotId = playerInventoryStart + ((startSlot - playerInventoryStart + i) % 36);
            ItemStack stack = handler.getSlot(slotId).getStack();

            if (stack.isEmpty() || stack.getItem() == Items.AIR) {
                continue;
            }

            info("Transferring item from slot " + slotId + ": " + stack.getItem().toString());

            if (mc.interactionManager != null) {
                mc.interactionManager.clickSlot(
                        handler.syncId,
                        slotId,
                        0,
                        SlotActionType.QUICK_MOVE,
                        mc.player
                );
            }

            lastProcessedSlot = slotId;
            transferDelayCounter = 2;
            return;
        }

        if (lastProcessedSlot >= playerInventoryStart) {
            lastProcessedSlot = playerInventoryStart - 1;
            transferDelayCounter = 3;
        }
    }

    private void handleDisconnecting() {
        KeyBinding.setKeyPressed(mc.options.forwardKey.getDefaultKey(), false);
        KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), false);

        if (isInLobbyArea()) {
            return;
        }

        sendWebhookNotification();
        debugAction("Disconnecting after protection sequence");

        if (emergencyDisconnect) {
            info("SpawnerProtect: " + emergencyReason + ". Successfully disconnected.");
        } else {
            info("SpawnerProtect: " + detectedPlayer + " detected. Successfully disconnected.");
        }

        if (mc.world != null) {
            mc.world.disconnect();
        }

        info("Disconnected due to player detection.");
        toggle();
    }

    private void sendWebhookNotification() {
        if (!webhook.get() || webhookUrl.get() == null || webhookUrl.get().trim().isEmpty()) {
            info("Webhook disabled or URL not configured.");
            return;
        }

        String webhookUrlValue = webhookUrl.get().trim();

        if (detectionTime == 0) {
            detectionTime = System.currentTimeMillis();
        }

        long discordTimestamp = detectionTime / 1000L;

        String messageContent = "";
        if (selfPing.get() && discordId.get() != null && !discordId.get().trim().isEmpty()) {
            messageContent = String.format("<@%s>", discordId.get().trim());
        }

        String embedJson = createWebhookPayload(messageContent, discordTimestamp);

        info("Sending webhook notification...");

        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrlValue))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(embedJson))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    info("Webhook notification sent successfully!");
                } else {
                    ChatUtils.error("Failed to send webhook notification. Status: " + response.statusCode());
                }
            } catch (Exception e) {
                ChatUtils.error("Failed to send webhook notification: " + e.getMessage());
            }
        }).start();
    }

    private String createWebhookPayload(String messageContent, long discordTimestamp) {
        String title = emergencyDisconnect ? "SpawnerProtect Emergency Alert" : "SpawnerProtect Alert";
        String description;

        if (emergencyDisconnect) {
            description = String.format("**Player Detected:** %s\\n**Detection Time:** <t:%d:R>\\n**Reason:** %s\\n**Disconnected:** Yes",
                    escapeJson(detectedPlayer), discordTimestamp, escapeJson(emergencyReason));
        } else {
            description = String.format("**Player Detected:** %s\\n**Detection Time:** <t:%d:R>\\n**Spawners Mined:** %s\\n**Items Deposited:** %s\\n**Disconnected:** Yes",
                    escapeJson(detectedPlayer), discordTimestamp,
                    spawnersMinedSuccessfully ? "✅ Success" : "❌ Failed",
                    itemsDepositedSuccessfully ? "✅ Success" : "❌ Failed");
        }

        int color = emergencyDisconnect ? 16711680 : 16766720;

        return String.format("""
            {
                "username": "Glazed Webhook",
                "avatar_url": "https://i.imgur.com/OL2y1cr.png",
                "content": "%s",
                "embeds": [{
                    "title": "%s",
                    "description": "%s",
                    "color": %d,
                    "timestamp": "%s",
                    "footer": {
                        "text": "Sent by Glazed"
                    }
                }]
            }""",
                escapeJson(messageContent),
                title,
                description,
                color,
                Instant.now().toString()
        );
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public void onDeactivate() {
        abortBreaking();
        setSneaking(false);
        KeyBinding.setKeyPressed(mc.options.forwardKey.getDefaultKey(), false);
        KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), false);
        ensureAutoReconnectEnabled();
    }

    private void ensureAutoReconnectEnabled() {
        Module autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect != null && !autoReconnect.isActive()) {
            autoReconnect.toggle();
        }
    }
}
