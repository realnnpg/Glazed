package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;

public class SpawnerProtect extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");

    private final Setting<Boolean> webhook = sgWebhook.add(new BoolSetting.Builder()
        .name("webhook")
        .description("Enable webhook notifications")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL for notifications")
        .defaultValue("")
        .visible(webhook::get)
        .build()
    );

    private final Setting<Boolean> selfPing = sgWebhook.add(new BoolSetting.Builder()
        .name("self-ping")
        .description("Ping yourself in the webhook message")
        .defaultValue(false)
        .visible(webhook::get)
        .build()
    );

    private final Setting<String> discordId = sgWebhook.add(new StringSetting.Builder()
        .name("discord-id")
        .description("Your Discord user ID for pinging")
        .defaultValue("")
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

    private final Setting<Integer> delaySeconds = sgGeneral.add(new IntSetting.Builder()
        .name("recheck-delay-seconds")
        .description("Delay in seconds before rechecking for spawners")
        .defaultValue(1)
        .min(1)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> miningTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("mining-timeout")
        .description("Max time in seconds to mine a single spawner before restarting")
        .defaultValue(3)
        .min(1)
        .max(30)
        .sliderMax(30)
        .build()
    );

    private final Setting<Integer> miningRestartDelay = sgGeneral.add(new IntSetting.Builder()
        .name("mining-restart-delay")
        .description("Delay in seconds before restarting mining after timeout")
        .defaultValue(2)
        .min(1)
        .max(10)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> emergencyDistance = sgGeneral.add(new IntSetting.Builder()
        .name("emergency-distance")
        .description("Distance in blocks where player triggers immediate disconnect")
        .defaultValue(7)
        .min(1)
        .max(20)
        .sliderMax(20)
        .build()
    );

    // New setting for position tolerance
    private final Setting<Double> positionTolerance = sgGeneral.add(new DoubleSetting.Builder()
        .name("position-tolerance")
        .description("Distance tolerance from start position to still consider player at original location")
        .defaultValue(5.0)
        .min(1.0)
        .max(20.0)
        .sliderMax(20.0)
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
        GOING_TO_SPAWNERS,
        GOING_TO_CHEST,
        OPENING_CHEST,
        DEPOSITING_ITEMS,
        DISCONNECTING,
        POSITION_DISPLACED  // New state for when player is not at start position
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
    private int recheckDelay = 0;
    private int confirmDelay = 0;
    private boolean waiting = false;

    // Mining timeout
    private int miningStartTime = 0;
    private boolean isMining = false;
    private boolean miningTimeoutTriggered = false;
    private int miningRestartTimer = 0;

    // Ender chest
    private BlockPos targetChest = null;
    private int chestOpenAttempts = 0;
    private boolean emergencyDisconnect = false;
    private String emergencyReason = "";

    // Position tracking variables
    private Vec3d startPosition = null;
    private boolean positionTracked = false;
    private int positionCheckTimer = 0;

    public SpawnerProtect() {
        super(GlazedAddon.CATEGORY, "SpawnerProtect", "Breaks spawners and puts them in your inv when a player is detected");
    }

    @Override
    public void onActivate() {
        resetState();
        configureLegitMining();

        // Record the starting position when module activates
        if (mc.player != null) {
            startPosition = mc.player.getPos();
            positionTracked = true;
            info("SpawnerProtect activated - Start position recorded: " +
                String.format("%.1f, %.1f, %.1f", startPosition.x, startPosition.y, startPosition.z));
            info("Monitoring for players...");
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
        recheckDelay = 0;
        confirmDelay = 0;
        waiting = false;
        miningStartTime = 0;
        isMining = false;
        miningTimeoutTriggered = false;
        miningRestartTimer = 0;
        targetChest = null;
        chestOpenAttempts = 0;
        emergencyDisconnect = false;
        emergencyReason = "";

        // Don't reset position tracking variables here
        positionCheckTimer = 0;
    }

    private void configureLegitMining() {
        info("Manual mining mode activated");
    }

    private void disableAutoReconnectIfEnabled() {
        Module autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect != null && autoReconnect.isActive()) {
            autoReconnect.toggle();
            info("AutoReconnect disabled due to player detection");
        }
    }

    // New method to check if player is at start position
    private boolean isPlayerAtStartPosition() {
        if (!positionTracked || startPosition == null || mc.player == null) {
            return true; // If we don't have start position, assume we're at start
        }

        Vec3d currentPos = mc.player.getPos();
        double distance = currentPos.distanceTo(startPosition);

        return distance <= positionTolerance.get();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;
        positionCheckTimer++;

        // Check position every 20 ticks (1 second)
        if (positionCheckTimer >= 20) {
            positionCheckTimer = 0;

            if (!isPlayerAtStartPosition() && currentState == State.IDLE) {
                currentState = State.POSITION_DISPLACED;
                info("Player moved from start position - protection temporarily disabled");
            } else if (isPlayerAtStartPosition() && currentState == State.POSITION_DISPLACED) {
                currentState = State.IDLE;
                info("Player returned to start position - protection re-enabled");
            }
        }

        // Handle position displaced state
        if (currentState == State.POSITION_DISPLACED) {
            return; // Do nothing while displaced from start position
        }

        if (checkEmergencyDisconnect()) {
            return;
        }

        if (transferDelayCounter > 0) {
            transferDelayCounter--;
            return;
        }

        switch (currentState) {
            case IDLE:
                checkForPlayers();
                break;
            case GOING_TO_SPAWNERS:
                handleGoingToSpawners();
                break;
            case GOING_TO_CHEST:
                handleGoingToChest();
                break;
            case OPENING_CHEST:
                handleOpeningChest();
                break;
            case DEPOSITING_ITEMS:
                handleDepositingItems();
                break;
            case DISCONNECTING:
                handleDisconnecting();
                break;
            case POSITION_DISPLACED:
                // This case is handled above, but included for completeness
                break;
        }
    }

    private boolean checkEmergencyDisconnect() {
        // Don't check for emergency disconnect if we're not at start position
        if (!isPlayerAtStartPosition()) {
            return false;
        }

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (!(player instanceof AbstractClientPlayerEntity)) continue;

            String playerName = player.getGameProfile().getName();

            if (enableWhitelist.get() && isPlayerWhitelisted(playerName)) {
                continue;
            }

            double distance = mc.player.distanceTo(player);
            if (distance <= emergencyDistance.get()) {
                info("EMERGENCY: Player " + playerName + " came too close (" + String.format("%.1f", distance) + " blocks)!");

                emergencyDisconnect = true;
                emergencyReason = "User " + playerName + " came too close";

                //me
                toggle(); //maybe?
                if (mc.world != null) {
                      mc.world.disconnect(net.minecraft.text.Text.of("Disconnected by addon"));
                }

                detectedPlayer = playerName;
                detectionTime = System.currentTimeMillis();

                disableAutoReconnectIfEnabled();

                currentState = State.DISCONNECTING;
                return true;
            }
        }
        return false;
    }

    private void checkForPlayers() {
        // Only check for players if we're at the start position
        if (!isPlayerAtStartPosition()) {
            return;
        }

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (!(player instanceof AbstractClientPlayerEntity)) continue;

            String playerName = player.getGameProfile().getName();

            if (enableWhitelist.get() && isPlayerWhitelisted(playerName)) {
                continue;
            }

            detectedPlayer = playerName;
            detectionTime = System.currentTimeMillis();

            info("SpawnerProtect: Player detected - " + detectedPlayer);

            disableAutoReconnectIfEnabled();

            currentState = State.GOING_TO_SPAWNERS;
            info("Player detected! Starting protection sequence...");

            setSneaking(true);
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

    private void handleGoingToSpawners() {
        setSneaking(true);

        if (miningTimeoutTriggered) {
            miningRestartTimer++;
            if (miningRestartTimer >= miningRestartDelay.get() * 20) {
                miningTimeoutTriggered = false;
                miningRestartTimer = 0;
                info("Restarting mining after timeout delay...");
            } else {
                return;
            }
        }

        if (currentTarget == null) {
            currentTarget = findNearestSpawner();

            if (currentTarget == null && !waiting) {
                waiting = true;
                recheckDelay = 0;
                confirmDelay = 0;
                info("No more spawners found, waiting to confirm...");
            } else if (currentTarget != null) {
                miningStartTime = tickCounter;
                isMining = true;
                info("Starting to mine spawner at " + currentTarget);
            }
        } else {
            if (isMining && (tickCounter - miningStartTime) > (miningTimeout.get() * 20)) {
                info("Mining timeout reached! Made by Glazed. Will restart mining in " + miningRestartDelay.get() + " seconds...");
                stopBreaking();
                miningTimeoutTriggered = true;
                miningRestartTimer = 0;
                isMining = false;
                miningStartTime = 0;
                return;
            }

            lookAtBlock(currentTarget);
            breakBlock(currentTarget);

            if (mc.world.getBlockState(currentTarget).isAir()) {
                info("Spawner at " + currentTarget + " broken! Looking for next spawner...");
                currentTarget = null;
                stopBreaking();
                isMining = false;
                transferDelayCounter = 5;
            }
        }

        if (waiting) {
            handleWaitingForSpawners();
        }
    }

    private void handleWaitingForSpawners() {
        //Glazed copyright
        recheckDelay++;
        if (recheckDelay == delaySeconds.get() * 20) {
            BlockPos foundSpawner = findNearestSpawner();

            if (foundSpawner != null) {
                waiting = false;
                currentTarget = foundSpawner;
                miningStartTime = tickCounter;
                isMining = true;
                info("Found additional spawner at " + foundSpawner);
                return;
            }
        }

        if (recheckDelay > delaySeconds.get() * 20) {
            confirmDelay++;
            if (confirmDelay >= 5) {
                stopBreaking();
                spawnersMinedSuccessfully = true;
                setSneaking(false);
                isMining = false;
                currentState = State.GOING_TO_CHEST;
                info("All spawners mined successfully. Looking for ender chest...");
                tickCounter = 0;
            }
        }
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

        mc.player.setYaw((float) yaw);
        mc.player.setPitch((float) pitch);
    }

    private void breakBlock(BlockPos pos) {
        if (mc.interactionManager != null) {
            mc.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
            KeyBinding.setKeyPressed(mc.options.attackKey.getDefaultKey(), true);
        }
    }

    private void stopBreaking() {
        KeyBinding.setKeyPressed(mc.options.attackKey.getDefaultKey(), false);
    }

    private void setSneaking(boolean sneak) {
        if (mc.player == null) return;

        // Sneaking is handled client-side in 1.21.8
        if (sneak && !sneaking) {
            mc.player.setSneaking(true);
            sneaking = true;
        } else if (!sneak && sneaking) {
            mc.player.setSneaking(false);
            sneaking = false;
        }
    }

    private void handleGoingToChest() {
        if (targetChest == null) {
            targetChest = findNearestEnderChest();
            if (targetChest == null) {
                info("No ender chest found nearby!");
                currentState = State.DISCONNECTING;
                return;
            }
            info("Found ender chest at " + targetChest);
        }

        moveTowardsBlock(targetChest);

        if (mc.player.getBlockPos().getSquaredDistance(targetChest) <= 9) {
            currentState = State.OPENING_CHEST;
            chestOpenAttempts = 0;
            info("Reached ender chest. Attempting to open...");
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
        Vec3d playerPos = mc.player.getPos();
        Vec3d targetPos = Vec3d.ofCenter(target);
        Vec3d direction = targetPos.subtract(playerPos).normalize();
        //Glazed copyright
        double yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z));
        mc.player.setYaw((float) yaw);

        KeyBinding.setKeyPressed(mc.options.forwardKey.getDefaultKey(), true);
    }

    private void handleOpeningChest() {
        if (targetChest == null) {
            currentState = State.GOING_TO_CHEST;
            return;
        }

        KeyBinding.setKeyPressed(mc.options.forwardKey.getDefaultKey(), false);
        // jumps to open ec. idk why it doesnt work if it doesnt jump
        KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), true);

        if (chestOpenAttempts < 20) {
            lookAtBlock(targetChest);
        }

        if (chestOpenAttempts % 5 == 0) { //0.25 seconds
            if (mc.interactionManager != null && mc.player != null) {
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
                info("Right-clicking ender chest... (attempt " + (chestOpenAttempts / 5 + 1) + ")");
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
                mc.player.closeHandledScreen();
                transferDelayCounter = 10;
                currentState = State.DISCONNECTING;
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

        sendWebhookNotification();

        if (emergencyDisconnect) {
            info("SpawnerProtect: " + emergencyReason + ". Successfully disconnected.");
        } else {
            info("SpawnerProtect: " + detectedPlayer + " detected. Successfully disconnected.");
        }

        if (mc.world != null) {
              mc.world.disconnect(net.minecraft.text.Text.of("Disconnected by addon"));
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

        long discordTimestamp = detectionTime / 1000L;

        String messageContent = "";
        if (selfPing.get() && discordId.get() != null && !discordId.get().trim().isEmpty()) {
            messageContent = String.format("<@%s>", discordId.get().trim());
        }

        String embedJson = createWebhookPayload(messageContent, discordTimestamp);
        //Glazed copyright
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
        stopBreaking();
        setSneaking(false);
        KeyBinding.setKeyPressed(mc.options.forwardKey.getDefaultKey(), false);
        KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), false);
    }
}
