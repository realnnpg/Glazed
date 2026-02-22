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
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
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

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show chat feedback.")
        .defaultValue(true)
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

    private final Setting<Integer> emergencyDistance = sgGeneral.add(new IntSetting.Builder()
        .name("emergency-distance")
        .description("Distance in blocks where player triggers immediate disconnect")
        .defaultValue(7)
        .min(1)
        .max(20)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> spawnerCheckDelay = sgGeneral.add(new IntSetting.Builder()
        .name("spawner-check-delay-ms")
        .description("Delay in milliseconds before confirming all spawners are gone")
        .defaultValue(3000)
        .min(1000)
        .max(10000)
        .sliderMax(10000)
        .build()
    );

    private final Setting<Integer> manualSpawnerCount = sgGeneral.add(new IntSetting.Builder()
        .name("manual-spawner-count")
        .description("Manually set expected spawner stack size (256, 512, 1024, etc). Used when auto-detection fails.")
        .defaultValue(256)
        .min(64)
        .max(5000)
        .sliderMax(5000)
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
        WORLD_CHANGED_ONCE,
        WORLD_CHANGED_TWICE
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

    private boolean isMiningCycle = true;
    private int miningCycleTimer = 0;
    private final int MINING_DURATION = 80;
    private final int PAUSE_DURATION = 20;
    private long noSpawnerDetectedTime = 0;
    private boolean waitingForSpawnerConfirm = false;
    private int expectedSpawnersAtPosition = 0;
    private int initialInventoryCount = 0;
    private BlockPos lastMiningPosition = null;

    private BlockPos targetChest = null;
    private int chestOpenAttempts = 0;
    private boolean emergencyDisconnect = false;
    private String emergencyReason = "";

    private World trackedWorld = null;
    private int worldChangeCount = 0;
    // If there are this many or more other players online, do not activate protection
    private final int PLAYER_COUNT_THRESHOLD = 3;

    public SpawnerProtect() {
        super(GlazedAddon.CATEGORY, "spawner-protect", "Breaks spawners and puts them in your inv when a player is detected");
    }

    @Override
    public void onActivate() {
        resetState();
        configureLegitMining();

        if (mc.world != null) {
            trackedWorld = mc.world;
            worldChangeCount = 0;
            if (notifications.get()) info("SpawnerProtect activated - Monitoring world: " + mc.world.getRegistryKey().getValue());
            if (notifications.get()) info("Monitoring for players...");
        }

        if (notifications.get()) ChatUtils.warning("Make sure to have an empty inventory with only a silk touch pickaxe and an ender chest nearby!");
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
        isMiningCycle = true;
        miningCycleTimer = 0;
        noSpawnerDetectedTime = 0;
        waitingForSpawnerConfirm = false;
        expectedSpawnersAtPosition = 0;
        initialInventoryCount = 0;
        lastMiningPosition = null;
        targetChest = null;
        chestOpenAttempts = 0;
        emergencyDisconnect = false;
        emergencyReason = "";
    }

    private void configureLegitMining() {
        if (notifications.get()) info("Manual mining mode activated");
    }

    private void disableAutoReconnectIfEnabled() {
        Module autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect != null && autoReconnect.isActive()) {
            autoReconnect.toggle();
            if (notifications.get()) info("AutoReconnect disabled due to player detection");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        
        // Keep sneak pressed ONLY during spawner mining
        if (sneaking && currentState == State.GOING_TO_SPAWNERS) {
            mc.options.sneakKey.setPressed(true);
        } else if (currentState != State.GOING_TO_SPAWNERS) {
            mc.options.sneakKey.setPressed(false);
        }
        
        tickCounter++;

        if (mc.world != trackedWorld) {
            handleWorldChange();
            return;
        }

        if (currentState == State.WORLD_CHANGED_ONCE) {
            return;
        }

        if (currentState == State.WORLD_CHANGED_TWICE) {
            currentState = State.IDLE;
            if (notifications.get()) info("Returned to spawner world - resuming player monitoring");
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
        }
    }

    private void handleWorldChange() {
        worldChangeCount++;
        trackedWorld = mc.world;

        if (worldChangeCount == 1) {
            currentState = State.WORLD_CHANGED_ONCE;
            if (notifications.get()) info("World changed (TP to spawn) - pausing player detection until return");
        } else if (worldChangeCount == 2) {
            currentState = State.WORLD_CHANGED_TWICE;
            worldChangeCount = 0;
            if (notifications.get()) info("World changed (back to spawners) - will resume monitoring");
        }
    }

    private boolean checkEmergencyDisconnect() {
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
                if (notifications.get()) info("EMERGENCY: Player " + playerName + " came too close (" + String.format("%.1f", distance) + " blocks)!");

                emergencyDisconnect = true;
                emergencyReason = "User " + playerName + " came too close";

                toggle();
                if (mc.world != null) {
                    mc.world.disconnect();
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
        // nigga
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

            detectedPlayer = playerName;
            detectionTime = System.currentTimeMillis();

            if (notifications.get()) info("SpawnerProtect: Player detected - " + detectedPlayer);

            disableAutoReconnectIfEnabled();

            currentState = State.GOING_TO_SPAWNERS;
            if (notifications.get()) info("Player detected! Starting protection sequence...");

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

    private int countSpawnersInInventory() {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.SPAWNER) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void handleGoingToSpawners() {
        setSneaking(true);

        if (currentTarget == null) {
            BlockPos found = findNearestSpawner();
            if (found == null) {
                if (!waitingForSpawnerConfirm) {
                    noSpawnerDetectedTime = System.currentTimeMillis();
                    waitingForSpawnerConfirm = true;
                    if (notifications.get()) info("No spawners detected, waiting " + spawnerCheckDelay.get() + "ms...");
                    return;
                } else {
                    long elapsed = System.currentTimeMillis() - noSpawnerDetectedTime;
                    if (elapsed < spawnerCheckDelay.get()) {
                        return;
                    }
                    if (notifications.get()) info("Confirmed: No spawners found.");
                    stopBreaking();
                    spawnersMinedSuccessfully = true;
                    setSneaking(false);
                    currentTarget = null;
                    currentState = State.GOING_TO_CHEST;
                    if (notifications.get()) info("All spawners mined! Total collected: " + countSpawnersInInventory());
                    tickCounter = 0;
                    waitingForSpawnerConfirm = false;
                    noSpawnerDetectedTime = 0;
                    return;
                }
            } else {
                waitingForSpawnerConfirm = false;
                noSpawnerDetectedTime = 0;

                // Check if new position - use manual count
                if (lastMiningPosition == null || !lastMiningPosition.equals(found)) {
                    lastMiningPosition = found;
                    currentTarget = found;
                    expectedSpawnersAtPosition = manualSpawnerCount.get();
                    initialInventoryCount = countSpawnersInInventory();
                    if (notifications.get()) info("New spawner at " + found + " - expecting " + manualSpawnerCount.get() + " spawners");
                }

                currentTarget = found;
                isMiningCycle = true;
                miningCycleTimer = 0;
            }
        }

        if (isMiningCycle) {
            lookAtBlock(currentTarget);
            breakBlock(currentTarget);

            if (mc.world.getBlockState(currentTarget).getBlock() != Blocks.SPAWNER) {
                if (mc.world.getBlockState(currentTarget).isAir()) {
                    if (notifications.get()) info("Spawner broken! Waiting for pickup...");
                    stopBreaking();
                    isMiningCycle = false;
                    miningCycleTimer = 0;
                    transferDelayCounter = 40;
                }
            }

        } else {
            miningCycleTimer++;
            if (miningCycleTimer >= PAUSE_DURATION) {
                int currentCount = countSpawnersInInventory();
                int collected = currentCount - initialInventoryCount;

                if (notifications.get()) info("Progress: " + collected + "/" + expectedSpawnersAtPosition + " spawners collected");

                if (collected >= expectedSpawnersAtPosition) {
                    if (notifications.get()) info("Stack complete! Moving to next spawner...");
                    currentTarget = null;
                    lastMiningPosition = null;
                    expectedSpawnersAtPosition = 0;
                    miningCycleTimer = 0;
                } else {
                    if (mc.world.getBlockState(lastMiningPosition).getBlock() == Blocks.SPAWNER) {
                        if (notifications.get()) info("Spawner respawned! Continuing...");
                        currentTarget = lastMiningPosition;
                        isMiningCycle = true;
                        miningCycleTimer = 0;
                    } else {
                        miningCycleTimer = 0;
                    }
                }
            }
        }
    }

        private void handleWaitingForSpawners() {
        recheckDelay++;
        if (recheckDelay == delaySeconds.get() * 20) {
            BlockPos foundSpawner = findNearestSpawner();

            if (foundSpawner != null) {
                waiting = false;
                currentTarget = foundSpawner;
                isMiningCycle = true;
                miningCycleTimer = 0;
                if (notifications.get()) info("Found additional spawner at " + foundSpawner);
                return;
            }
        }

        if (recheckDelay > delaySeconds.get() * 20) {
            confirmDelay++;
            if (confirmDelay >= 5) {
                stopBreaking();
                spawnersMinedSuccessfully = true;
                setSneaking(false);
                currentState = State.GOING_TO_CHEST;
                if (notifications.get()) info("All spawners mined successfully. Looking for ender chest...");
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
            if (notifications.get()) info("Found spawner at " + nearestSpawner + " (distance: " + String.format("%.2f", Math.sqrt(nearestDistance)) + ")");
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
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        if (sneak && !sneaking) {
            mc.player.setSneaking(true);
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
            sneaking = true;
        } else if (!sneak && sneaking) {
            mc.player.setSneaking(false);
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
            sneaking = false;
        }
    }

    private void handleGoingToChest() {
        if (sneaking) {
            setSneaking(false);
        }
        if (targetChest == null) {
            targetChest = findNearestEnderChest();
            if (targetChest == null) {
                if (notifications.get()) info("No ender chest found nearby!");
                currentState = State.DISCONNECTING;
                return;
            }
            if (notifications.get()) info("Found ender chest at " + targetChest);
        }

        moveTowardsBlock(targetChest);

        if (mc.player.getBlockPos().getSquaredDistance(targetChest) <= 9) {
            currentState = State.OPENING_CHEST;
            chestOpenAttempts = 0;
            if (notifications.get()) info("Reached ender chest. Attempting to open...");
        }

        if (tickCounter > 600) {
            if (notifications.get()) ChatUtils.error("Timed out trying to reach ender chest!");
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

        double yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z));
        mc.player.setYaw((float) yaw);

        KeyBinding.setKeyPressed(mc.options.forwardKey.getDefaultKey(), true);
    }

    private void handleOpeningChest() {
        if (targetChest == null) {
            currentState = State.GOING_TO_CHEST;
            return;
        }

        if (sneaking) {
            setSneaking(false);
        }
        mc.options.sneakKey.setPressed(false);

        KeyBinding.setKeyPressed(mc.options.forwardKey.getDefaultKey(), false);
        KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), true);

        if (chestOpenAttempts < 20) {
            lookAtBlock(targetChest);
        }

        if (chestOpenAttempts % 5 == 0) {
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
                if (notifications.get()) info("Right-clicking ender chest... (attempt " + (chestOpenAttempts / 5 + 1) + ")");
            }
        }

        chestOpenAttempts++;

        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), false);
            currentState = State.DEPOSITING_ITEMS;
            chestOpened = true;
            lastProcessedSlot = -1;
            tickCounter = 0;
            if (notifications.get()) info("Ender chest opened successfully! Made by GLZD ");
        }

        if (chestOpenAttempts > 200) {
            KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), false);
            if (notifications.get()) ChatUtils.error("Failed to open ender chest after multiple attempts!");
            currentState = State.DISCONNECTING;
        }
    }

    private void handleDepositingItems() {
        if (sneaking) {
            setSneaking(false);
        }
        mc.options.sneakKey.setPressed(false);

        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            GenericContainerScreenHandler handler = (GenericContainerScreenHandler) mc.player.currentScreenHandler;

            if (!hasItemsToDeposit()) {
                itemsDepositedSuccessfully = true;
                if (notifications.get()) info("All items deposited successfully!");
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
            if (notifications.get()) ChatUtils.error("Timed out depositing items!");
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

            if (notifications.get()) info("Transferring item from slot " + slotId + ": " + stack.getItem().toString());

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
            if (notifications.get()) info("SpawnerProtect: " + emergencyReason + ". Successfully disconnected.");
        } else {
            if (notifications.get()) info("SpawnerProtect: " + detectedPlayer + " detected. Successfully disconnected.");
        }

        if (mc.world != null) {
            mc.world.disconnect();
        }

        if (notifications.get()) info("Disconnected due to player detection.");
        toggle();
    }

    private void sendWebhookNotification() {
        if (!webhook.get() || webhookUrl.get() == null || webhookUrl.get().trim().isEmpty()) {
            if (notifications.get()) info("Webhook disabled or URL not configured.");
            return;
        }

        String webhookUrlValue = webhookUrl.get().trim();

        long discordTimestamp = detectionTime / 1000L;

        String messageContent = "";
        if (selfPing.get() && discordId.get() != null && !discordId.get().trim().isEmpty()) {
            messageContent = String.format("<@%s>", discordId.get().trim());
        }

        String embedJson = createWebhookPayload(messageContent, discordTimestamp);

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
                    if (notifications.get()) info("Webhook notification sent successfully!");
                } else {
                    if (notifications.get()) ChatUtils.error("Failed to send webhook notification. Status: " + response.statusCode());
                }
            } catch (Exception e) {
                if (notifications.get()) ChatUtils.error("Failed to send webhook notification: " + e.getMessage());
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