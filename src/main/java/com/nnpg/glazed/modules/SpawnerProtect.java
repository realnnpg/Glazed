package com.nnpg.glazed.modules;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.Direction;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

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

    private final Setting<Boolean> webhook = sgGeneral.add(new BoolSetting.Builder()
        .name("webhook")
        .description("Enable webhook notifications")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhookUrl = sgGeneral.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL for notifications")
        .defaultValue("")
        .visible(webhook::get)
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

    public SpawnerProtect() {
        super(GlazedAddon.CATEGORY, "SpawnerProtect", "Breaks spawners and puts them in your inv when a player is detected");
    }

    @Override
    public void onActivate() {
        currentState = State.IDLE;
        detectedPlayer = "";
        detectionTime = 0;
        spawnersMinedSuccessfully = false;
        itemsDepositedSuccessfully = false;
        tickCounter = 0;
        chestOpened = false;
        transferDelayCounter = 0;
        lastProcessedSlot = -1;

        ChatUtils.sendPlayerMsg("#set legitMine true");
        ChatUtils.sendPlayerMsg("#set smoothLook true");

        ChatUtils.info("SpawnerProtect activated - monitoring for players...");
        ChatUtils.warning("Make sure to have an empty inventory with only a silk touch pickaxe and an ender chest nearby!");

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
    }

    private void checkForPlayers() {
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (!(player instanceof AbstractClientPlayerEntity)) continue;

            detectedPlayer = player.getGameProfile().getName();
            detectionTime = System.currentTimeMillis();

            ChatUtils.sendPlayerMsg("SpawnerProtect: Player detected - " + detectedPlayer);

            currentState = State.GOING_TO_SPAWNERS;
            ChatUtils.info("Player detected! Starting protection sequence...");
            mc.player.setSneaking(true);
            ChatUtils.sendPlayerMsg("#mine spawner");

            break;
        }
    }

    private void handleGoingToSpawners() {
        boolean spawnersRemain = false;
        BlockPos playerPos = mc.player.getBlockPos();

        for (int x = -spawnerRange.get(); x <= spawnerRange.get(); x++) {
            for (int y = -spawnerRange.get(); y <= spawnerRange.get(); y++) {
                for (int z = -spawnerRange.get(); z <= spawnerRange.get(); z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() == Blocks.SPAWNER) {
                        spawnersRemain = true;
                        break;
                    }
                }
                if (spawnersRemain) break;
            }
            if (spawnersRemain) break;
        }

        if (!spawnersRemain) {
            spawnersMinedSuccessfully = true;
            mc.player.setSneaking(false);
            currentState = State.GOING_TO_CHEST;
            ChatUtils.info("All spawners mined successfully. Going to ender chest...");
            ChatUtils.sendPlayerMsg("#goto ender_chest");
            tickCounter = 0;
        } else {
            if (tickCounter % 60 == 0) {
                ChatUtils.sendPlayerMsg("#mine spawner");
            }
        }
    }

    private void handleGoingToChest() {
        boolean nearEnderChest = false;
        BlockPos playerPos = mc.player.getBlockPos();

        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() == Blocks.ENDER_CHEST) {
                        nearEnderChest = true;
                        break;
                    }
                }
            }
        }

        if (nearEnderChest) {
            currentState = State.DEPOSITING_ITEMS;
            tickCounter = 0;
            ChatUtils.info("Reached ender chest area. Opening and depositing items...");
        }

        if (tickCounter > 600) {
            ChatUtils.error("Timed out trying to reach ender chest!");
            currentState = State.DISCONNECTING;
        }
    }

    private void handleDepositingItems() {
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            GenericContainerScreenHandler handler = (GenericContainerScreenHandler) mc.player.currentScreenHandler;

            if (!chestOpened) {
                chestOpened = true;
                lastProcessedSlot = -1;
                ChatUtils.info("Ender chest opened, starting item transfer...");
            }

            if (!hasItemsToDeposit()) {
                itemsDepositedSuccessfully = true;
                ChatUtils.info("All items deposited successfully!");
                mc.player.closeHandledScreen();
                transferDelayCounter = 40;
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

            ChatUtils.info("Transferring item from slot " + slotId + ": " + stack.getItem().toString());

            mc.interactionManager.clickSlot(
                handler.syncId,
                slotId,
                0,
                SlotActionType.QUICK_MOVE,
                mc.player
            );

            lastProcessedSlot = slotId;
            transferDelayCounter = 8;
            return;
        }

        if (lastProcessedSlot >= playerInventoryStart) {
            lastProcessedSlot = playerInventoryStart - 1;
            transferDelayCounter = 10;
        }
    }

    private void handleDisconnecting() {
        sendWebhookNotification();

        ChatUtils.info("SpawnerProtect: Player detected - "  + detectedPlayer);

        if (mc.world != null) {
            mc.world.disconnect();
        }

        ChatUtils.info("Disconnected due to player detection.");
        toggle();
    }

    private void sendWebhookNotification() {
        if (!webhook.get() || webhookUrl.get().isEmpty()) {
            ChatUtils.info("Webhook disabled or URL not configured.");
            return;
        }

        long discordTimestamp = detectionTime / 1000L;
        String embedJson = String.format("""
            {
                "username": "Glazed Webhook",
                "avatar_url": "https://i.imgur.com/OL2y1cr.png",
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
            detectedPlayer,
            discordTimestamp,
            spawnersMinedSuccessfully ? "✅ Success" : "❌ Failed",
            itemsDepositedSuccessfully ? "✅ Success" : "❌ Failed",
            Instant.now().toString()
        );

        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl.get()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(embedJson))
                    .build();

                client.send(request, HttpResponse.BodyHandlers.ofString());
                ChatUtils.info("Webhook notification sent successfully!");
            } catch (Exception e) {
                ChatUtils.error("Failed to send webhook notification: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) {
            mc.player.setSneaking(false);
        }

        ChatUtils.sendPlayerMsg("#stop");
    }
}
