package com.nnpg.glazed.modules;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.StorageESP;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.Direction;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

    private final Setting<String> discordId = sgGeneral.add(new StringSetting.Builder()
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



    // Whitelist settings
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
        DEPOSITING_ITEMS,
        DISCONNECTING
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

    public SpawnerProtect() {
        super(GlazedAddon.CATEGORY, "SpawnerProtect", "Breaks spawners and puts them in your inv when a player is detected");
    }

    @Override
    public void onActivate() {
        resetState();
        configureLegitMining();
        info("SpawnerProtect activated - monitoring for players...");
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
    }

    private void configureLegitMining() {
        ChatUtils.sendPlayerMsg("#set legitMine true");
        ChatUtils.sendPlayerMsg("#set smoothLook true");
        ChatUtils.sendPlayerMsg("#set antiCheatCompatibility true");
        ChatUtils.sendPlayerMsg("#freelook false");
        ChatUtils.sendPlayerMsg("#legitMineIncludeDiagonals true");
        ChatUtils.sendPlayerMsg("#smoothLookTicks 10");
    }

    private void disableAutoReconnectIfEnabled() {
        Module autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect != null && autoReconnect.isActive()) {
            autoReconnect.toggle();
            info("AutoReconnect disabled due to player detection");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;

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
            case DEPOSITING_ITEMS:
                handleDepositingItems();
                break;
            case DISCONNECTING:
                handleDisconnecting();
                break;
        }

        // Apply AutoReconnect setting only when needed
        // (removed the continuous checking)
    }

    private void checkForPlayers() {
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

            // Disable AutoReconnect when player is detected
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

        if (currentTarget == null) {
            currentTarget = findNearestSpawner();

            if (currentTarget == null && !waiting) {
                waiting = true;
                recheckDelay = 0;
                confirmDelay = 0;
                info("No more spawners found, waiting to confirm...");
            }
        } else {
            lookAtBlock(currentTarget);
            breakBlock(currentTarget);

            if (mc.world.getBlockState(currentTarget).isAir()) {
                info("Spawner at " + currentTarget + " broken! Looking for next spawner...");
                currentTarget = null;
                stopBreaking();
                transferDelayCounter = 5;
            }
        }

        if (waiting) {
            handleWaitingForSpawners();
        }
    }

    private void handleWaitingForSpawners() {
        recheckDelay++;
        if (recheckDelay == delaySeconds.get() * 20) {
            BlockPos foundSpawner = findNearestSpawner();

            if (foundSpawner != null) {
                waiting = false;
                currentTarget = foundSpawner;
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
                currentState = State.GOING_TO_CHEST;
                info("All spawners mined successfully. Going to ender chest...");
                ChatUtils.sendPlayerMsg("#goto ender_chest");
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
        boolean nearEnderChest = isNearEnderChest();

        if (nearEnderChest) {
            currentState = State.DEPOSITING_ITEMS;
            tickCounter = 0;
            info("Reached ender chest area. Opening and depositing items...");
        }

        if (tickCounter > 600) {
            ChatUtils.error("Timed out trying to reach ender chest!");
            currentState = State.DISCONNECTING;
        }
    }

    private boolean isNearEnderChest() {
        BlockPos playerPos = mc.player.getBlockPos();

        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() == Blocks.ENDER_CHEST) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void handleDepositingItems() {
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            GenericContainerScreenHandler handler = (GenericContainerScreenHandler) mc.player.currentScreenHandler;

            if (!chestOpened) {
                chestOpened = true;
                lastProcessedSlot = -1;
                info("Ender chest opened, starting item transfer...");
            }

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
            if (tickCounter % 20 == 0) {
                ChatUtils.sendPlayerMsg("#goto ender_chest");
            }
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
        sendWebhookNotification();
        info("SpawnerProtect: Player detected - " + detectedPlayer);

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
        if (!isValidWebhookUrl(webhookUrlValue)) {
            ChatUtils.error("Invalid webhook URL format!");
            return;
        }

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
                    info("Webhook notification sent successfully!");
                } else {
                    ChatUtils.error("Failed to send webhook notification. Status: " + response.statusCode());
                }
            } catch (Exception e) {
                ChatUtils.error("Failed to send webhook notification: " + e.getMessage());
            }
        }).start();
    }

    private boolean isValidWebhookUrl(String url) {
        return url.startsWith("https://discord.com/api/webhooks/") ||
            url.startsWith("https://discordapp.com/api/webhooks/");
    }

    private String createWebhookPayload(String messageContent, long discordTimestamp) {
        return String.format("""
            {
                "username": "Glazed Webhook",
                "avatar_url": "https://i.imgur.com/gVzV8ve.jpeg",
                "content": "%s",
                "embeds": [{
                    "title": "SpawnerProtect Alert",
                    "description": "**Player Detected:** %s\\n**Detection Time:** <t:%d:R>\\n**Spawners Mined:** %s\\n**Items Deposited:** %s\\n**Disconnected:** Yes",
                    "color": 16766720,
                    "timestamp": "%s",
                    "footer": {
                        "text": "Sent by Glazed"
                    }
                }]
            }""",
            escapeJson(messageContent),
            escapeJson(detectedPlayer),
            discordTimestamp,
            spawnersMinedSuccessfully ? "✅ Success" : "❌ Failed",
            itemsDepositedSuccessfully ? "✅ Success" : "❌ Failed",
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
        ChatUtils.sendPlayerMsg("#stop");
    }
}
