package com.nnpg.glazed.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import com.nnpg.glazed.GlazedAddon;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class SpawnerProtect extends Module {
    private boolean isTriggered = false;
    private boolean isMiningDone = false;
    private boolean detectedPlayer = false;
    private IBaritone baritone;
    private boolean baritoneAvailable = false;
    private int commandCooldown = 0;
    private int timer = 0;
    private boolean isAtEnderChest = false;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> webhook = sgGeneral.add(new BoolSetting.Builder()
        .name("Webhook")
        .description("Sends a webhook when a player is nearby")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> webhookUrl = sgGeneral.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL")
        .defaultValue("")
        .build()
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public SpawnerProtect() {
        super(GlazedAddon.CATEGORY, "SpawnerProtect", "Mines spawners when players are nearby, stores them in an Ender Chest, and disconnects.");
    }

    @Override
    public void onActivate() {
        isTriggered = false;
        isMiningDone = false;
        detectedPlayer = false;
        commandCooldown = 0;
        timer = 0;
        isAtEnderChest = false;

        try {
            baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            baritoneAvailable = true;
            ChatUtils.info("SpawnerProtect activated - Baritone ready, watching for players...");
        } catch (Exception e) {
            ChatUtils.error("Failed to initialize Baritone: " + e.getMessage());
            baritoneAvailable = false;
            this.toggle();
            return;
        }
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!isActive() || !baritoneAvailable) return;

        if (!this.detectedPlayer) {
            Entity entity = event.entity;
            if (entity instanceof PlayerEntity && entity != mc.player) {
                this.detectedPlayer = true;
                this.isTriggered = true;
                ChatUtils.info("Player detected! Starting spawner mining...");

                startSneaking();

                commandCooldown = 10;
                sendwebhook();
            }
        }
    }

    @EventHandler
    private void sendwebhook() {
        // Only send if webhook setting is enabled
        if (!webhook.get()) {
            return;
        }

        // Check if webhook URL is provided
        if (webhookUrl.get().isEmpty()) {
            ChatUtils.error("Please provide a webhook URL in the settings!");
            return;
        }

        try {
            //String playerName = MinecraftClient.getInstance().getSession().getUsername();

            String jsonPayload = String.format(
                "{\"embeds\":[{\"title\":\"%s\",\"description\":\"%s\",\"color\":%d,\"footer\":{\"text\":\"Sent by Glazed\"}}]}",
                "Player detected nearby!!!",
                "Starting mining process",
                16777215
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl.get()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(10))
                .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        ChatUtils.info("Webhook message sent successfully!");
                    } else {
                        ChatUtils.error("Failed to send webhook message. Status code: " + response.statusCode());
                    }
                })
                .exceptionally(throwable -> {
                    ChatUtils.error("Error sending webhook message: " + throwable.getMessage());
                    return null;
                });

        } catch (Exception e) {
            ChatUtils.error("Error creating webhook request: " + e.getMessage());
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || !baritoneAvailable || !isTriggered) return;

        // Handle command cooldown (initial mining command)
        if (commandCooldown > 0) {
            commandCooldown--;
            if (commandCooldown == 0) {
                startMiningSequence();
            }
            return;
        }

        if (isTriggered && !isMiningDone) {
            boolean stillMining = false;
            try {
                stillMining = baritone.getPathingBehavior().isPathing() ||
                    baritone.getMineProcess().isActive();
            } catch (Exception e) {
                stillMining = false;
            }

            if (!stillMining) {
                if (timer == 0) {
                    timer = 20;
                    ChatUtils.info("Mining stopped, waiting for item collection...");
                } else {
                    timer--;
                    if (timer == 0) {
                        isMiningDone = true;
                        stopSneaking();
                        ChatUtils.info("Spawner mining complete! Going to Ender Chest...");
                        timer = 10;
                    }
                }
            }
            return;
        }

        if (isMiningDone && timer > 0) {
            timer--;
            if (timer == 0) {
                int spawnerCount = countSpawnersInInventory();
                ChatUtils.info("Mining complete! Found " + spawnerCount + " spawners in inventory. Disconnecting...");
                disconnect();
            }
            return;
        }

        /** COMMENTED OUT - ENDER CHEST FUNCTIONALITY (NOT WORKING)
         // Go to ender chest after mining is confirmed complete
         if (isMiningDone && !isAtEnderChest && timer > 0) {
         timer--;
         if (timer == 0) {
         goToEnderChest();
         }
         return;
         }

         // Check if we've reached the ender chest (and it's been opened by goto command)
         if (isMiningDone && !isAtEnderChest && !baritone.getPathingBehavior().isPathing()) {
         isAtEnderChest = true;
         ChatUtils.info("Reached Ender Chest and opened it. Preparing to transfer spawners...");
         timer = 40; // Wait 2 seconds before starting inventory operations
         }

         // Handle spawner storage after reaching ender chest
         if (isAtEnderChest) {
         handleInventoryManagement();
         }
         **/
    }

    private void startMiningSequence() {
        if (!baritoneAvailable || baritone == null) {
            ChatUtils.error("Baritone is not available!");
            return;
        }

        try {
            baritone.getCommandManager().execute("set legitMine true");
            baritone.getCommandManager().execute("mine minecraft:spawner");


        } catch (Exception e) {
            ChatUtils.error("Failed to send Baritone command: " + e.getMessage());

            try {
                ChatUtils.info("Trying fallback method...");
                ChatUtils.sendPlayerMsg("#set legitMine true");
                ChatUtils.sendPlayerMsg("#mine minecraft:spawner");
            } catch (Exception e2) {
                ChatUtils.error("All methods failed: " + e2.getMessage());
            }
        }
    }

    private void startSneaking() {
        if (mc.player != null && mc.getNetworkHandler() != null) {
            try {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, Mode.PRESS_SHIFT_KEY));
                ChatUtils.info("Started sneaking");
            } catch (Exception e) {
                ChatUtils.error("Failed to start sneaking: " + e.getMessage());
            }
        }
    }

    private void stopSneaking() {
        if (mc.player != null && mc.getNetworkHandler() != null) {
            try {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, Mode.RELEASE_SHIFT_KEY));
                ChatUtils.info("Stopped sneaking");
            } catch (Exception e) {
                ChatUtils.error("Failed to stop sneaking: " + e.getMessage());
            }
        }
    }

    /**
     * COMMENTED OUT - ENDER CHEST METHODS (NOT WORKING)
     * private void goToEnderChest() {
     * try {
     * baritone.getCommandManager().execute("goto minecraft:ender_chest");
     * ChatUtils.info("Pathfinding to nearest Ender Chest (will auto-open)...");
     * } catch (Exception e) {
     * ChatUtils.error("Failed to navigate to Ender Chest: " + e.getMessage());
     * // Try fallback
     * try {
     * ChatUtils.sendPlayerMsg("#goto minecraft:ender_chest");
     * } catch (Exception e2) {
     * ChatUtils.error("Fallback navigation failed: " + e2.getMessage());
     * }
     * }
     * }
     * <p>
     * private void handleInventoryManagement() {
     * if (timer > 0) {
     * timer--;
     * if (timer == 30) { // When timer hits 30, show inventory info
     * int spawnerCount = countSpawnersInInventory();
     * ChatUtils.info("Found " + spawnerCount + " spawners in inventory");
     * }
     * return;
     * }
     * <p>
     * // Check if ender chest is properly opened
     * if (mc.player == null) return;
     * <p>
     * if (mc.player.currentScreenHandler == null ||
     * mc.player.currentScreenHandler == mc.player.playerScreenHandler) {
     * // Ender chest not open yet, wait a bit more
     * ChatUtils.info("Ender chest not fully opened yet, waiting...");
     * timer = 20; // Wait 1 second and try again
     * return;
     * }
     * <p>
     * // Find spawners in inventory and move them with shift-click
     * boolean foundSpawner = false;
     * <p>
     * // Check hotbar first (slots 0-8 in inventory, slots 36-44 in screen handler)
     * for (int i = 0; i < 9; i++) {
     * ItemStack stack = mc.player.getInventory().getStack(i);
     * if (!stack.isEmpty() && stack.getItem().equals(Items.SPAWNER)) {
     * try {
     * int screenSlot = i + 36; // Hotbar: inventory slots 0-8 → screen slots 36-44
     * mc.interactionManager.clickSlot(
     * mc.player.currentScreenHandler.syncId,
     * screenSlot,
     * 0,
     * SlotActionType.QUICK_MOVE,
     * mc.player
     * );
     * ChatUtils.info("Shift-clicked " + stack.getCount() + " spawner(s) from hotbar slot " + (i + 1));
     * foundSpawner = true;
     * timer = 8; // Wait 0.4 seconds between moves
     * break;
     * } catch (Exception e) {
     * ChatUtils.error("Failed to shift-click spawner from hotbar slot " + (i + 1) + ": " + e.getMessage());
     * }
     * }
     * }
     * <p>
     * // If no spawner found in hotbar, check main inventory (slots 9-35 in inventory, slots 9-35 in screen handler)
     * if (!foundSpawner) {
     * for (int i = 9; i < 36; i++) {
     * ItemStack stack = mc.player.getInventory().getStack(i);
     * if (!stack.isEmpty() && stack.getItem().equals(Items.SPAWNER)) {
     * try {
     * int screenSlot = i; // Main inventory: same slot numbers
     * mc.interactionManager.clickSlot(
     * mc.player.currentScreenHandler.syncId,
     * screenSlot,
     * 0,
     * SlotActionType.QUICK_MOVE,
     * mc.player
     * );
     * ChatUtils.info("Shift-clicked " + stack.getCount() + " spawner(s) from inventory slot " + (i - 8));
     * foundSpawner = true;
     * timer = 8; // Wait 0.4 seconds between moves
     * break;
     * } catch (Exception e) {
     * ChatUtils.error("Failed to shift-click spawner from inventory slot " + (i - 8) + ": " + e.getMessage());
     * }
     * }
     * }
     * }
     * <p>
     * // If no more spawners found, disconnect
     * if (!foundSpawner && timer == 0) {
     * int remainingSpawners = countSpawnersInInventory();
     * if (remainingSpawners == 0) {
     * ChatUtils.info("All spawners successfully moved to Ender Chest. Disconnecting...");
     * timer = 60; // Wait 3 seconds before disconnect
     * disconnect();
     * } else {
     * ChatUtils.warning("Still have " + remainingSpawners + " spawner(s) but couldn't move them. Retrying...");
     * timer = 20; // Wait 1 second and try again
     * }
     * }
     * }
     **/ // END OF COMMENTED ENDER CHEST CODE
    private int countSpawnersInInventory() {
        if (mc.player == null) return 0;

        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem().equals(Items.SPAWNER)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void disconnect() {
        try {
            ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
            if (networkHandler != null && mc.player != null) {
                mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("Someone found you - SpawnerProtect")));
            }
        } catch (Exception e) {
            ChatUtils.error("Failed to disconnect: " + e.getMessage());
            try {
                if (mc.world != null) {
                    mc.disconnect();
                }
            } catch (Exception e2) {
                ChatUtils.error("Alternative disconnect failed: " + e2.getMessage());
            }
        }
    }

    @Override
    public void onDeactivate() {
        isTriggered = false;
        isMiningDone = false;
        detectedPlayer = false;
        commandCooldown = 0;
        timer = 0;
        isAtEnderChest = false;

        stopSneaking();

        if (baritoneAvailable && baritone != null) {
            try {
                baritone.getPathingBehavior().cancelEverything();
            } catch (Exception e) {
            }
        }

    }
}
