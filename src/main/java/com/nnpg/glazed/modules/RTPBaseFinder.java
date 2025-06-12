package com.nnpg.glazed.modules;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AutoArmor;
import meteordevelopment.meteorclient.systems.modules.combat.AutoEXP;
import meteordevelopment.meteorclient.systems.modules.player.AutoReplenish;
import meteordevelopment.meteorclient.systems.modules.render.StorageESP;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockEntityProvider;

import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import baritone.api.BaritoneAPI;
import meteordevelopment.meteorclient.systems.modules.combat.AutoTotem;
import meteordevelopment.meteorclient.systems.modules.player.AutoTool;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;


public class RTPBaseFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgother = settings.createGroup("Other module");
    private final SettingGroup sgwebhook = settings.createGroup("Webhook");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final Setting<RTPRegion> rtpRegion = sgGeneral.add(new EnumSetting.Builder<RTPRegion>()
        .name("RTP Region")
        .description("The region to RTP to.")
        .defaultValue(RTPRegion.EU_CENTRAL)
        .build());

    private final Setting<Integer> mineYLevel = sgGeneral.add(new IntSetting.Builder()
        .name("Mine Y Level")
        .description("Y level to mine down to.")
        .defaultValue(-22)
        .min(-64)
        .max(320)
        .sliderMax(100)
        .sliderMin(-64)
        .build());

    private final Setting<Integer> storageBlockThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("Storage Blocks")
        .defaultValue(15)
        .min(1)
        .sliderMax(200)
        .build());

    // Webhook settings
    private final Setting<String> webhookUrl = sgwebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL")
        .defaultValue("")
        .build());

    private final Setting<Boolean> baseFindWebhook = sgwebhook.add(new BoolSetting.Builder()
        .name("Base Find Webhook")
        .description("Send webhook message when a base gets found")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> totemPopWebhook = sgwebhook.add(new BoolSetting.Builder()
        .name("Totem Pop Webhook")
        .description("Send webhook message when player pops a totem")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> deathWebhook = sgwebhook.add(new BoolSetting.Builder()
        .name("Death Webhook")
        .description("Send webhook message when player dies")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> disconnectWebhook = sgwebhook.add(new BoolSetting.Builder()
        .name("Disconnect Webhook")
        .description("Send webhook message when player disconnects")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> spawnersCritical = sgGeneral.add(new BoolSetting.Builder()
        .name("Spawners Critical")
        .description("Disconnect immediately on spawner if true, otherwise treat as storage.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> enableAutoTotem = sgother.add(new BoolSetting.Builder()
        .name("Enable AutoTotem")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> enableAutoTool = sgother.add(new BoolSetting.Builder()
        .name("Enable AutoTool")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> enableAutoReplenish = sgother.add(new BoolSetting.Builder()
        .name("Enable Replenish")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> enableAutoEat = sgother.add(new BoolSetting.Builder()
        .name("Enable AutoEat")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> enableStorageESP = sgother.add(new BoolSetting.Builder()
        .name("Enable StorageESP")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> enableAutoArmor = sgother.add(new BoolSetting.Builder()
        .name("Enable AutoArmor")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> enableAutoExp = sgother.add(new BoolSetting.Builder()
        .name("Enable AutoExp")
        .defaultValue(false)
        .build());

    // Loop stages: 0 = RTP, 1 = checking after RTP, 2 = mining down, 3 = checking after mining
    private int loopStage = 0;
    private long stageStartTime;
    private BlockPos lastPos;
    private long lastMoveTime;
    private boolean hasReachedGoal = false;
    private final int RTP_WAIT_DURATION = 6000; // Wait 6 seconds after RTP
    private final int BASE_CHECK_DURATION = 5000; // Check for 5 seconds
    private final int STUCK_TIMEOUT = 20000; // 20 seconds stuck timeout
    private boolean hasCheckedForBases = false;

    private final Set<BlockPos> foundBases = new HashSet<>();

    // Variables for death detection
    private float lastHealth = 20.0f;
    private boolean playerWasAlive = true;

    public RTPBaseFinder() {
        super(GlazedAddon.CATEGORY, "RTPBaseFinder", "RTPs, mines to a Y level, and detects bases using Baritone.");
    }

    @Override
    public void onActivate() {
        startLoop();
    }

    @Override
    public void onDeactivate() {
        ChatUtils.sendPlayerMsg("#stop");
    }

    private void startLoop() {
        // Start the loop with RTP
        ChatUtils.sendPlayerMsg("/rtp " + rtpRegion.get().getCommandPart());
        loopStage = 0; // RTP stage
        stageStartTime = System.currentTimeMillis();
        updateMovementTracking();
        hasReachedGoal = false;
        foundBases.clear();
        ChatUtils.info("Starting RTP to " + rtpRegion.get().getCommandPart());
    }

    private void updateMovementTracking() {
        if (mc.player != null) {
            lastPos = mc.player.getBlockPos();
            lastMoveTime = System.currentTimeMillis();
        }
    }

    private boolean isPlayerStuck() {
        if (mc.player == null) return false;

        BlockPos currentPos = mc.player.getBlockPos();
        long now = System.currentTimeMillis();

        // Update movement tracking
        if (!currentPos.equals(lastPos)) {
            lastPos = currentPos;
            lastMoveTime = now;
            return false;
        }

        // Check if stuck for too long
        return (now - lastMoveTime) > STUCK_TIMEOUT;
    }

    private void toggleModule(Class<? extends Module> moduleClass, boolean enable) {
        Module module = Modules.get().get(moduleClass);
        if (module != null) {
            if (enable && !module.isActive()) module.toggle();
            else if (!enable && module.isActive()) module.toggle();
        }
    }

    private void startMining() {
        if (mc.player == null) return;
        BlockPos pos = mc.player.getBlockPos();
        ChatUtils.sendPlayerMsg("#set legitMine true");
        ChatUtils.sendPlayerMsg("#goto " + pos.getX() + " " + mineYLevel.get() + " " + pos.getZ());
        ChatUtils.info("Started mining down to Y level " + mineYLevel.get());
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        // Handle totem pop events
        if (event.packet instanceof EntityStatusS2CPacket packet) {
            if (packet.getStatus() == 35) { // Totem pop status
                if (totemPopWebhook.get() && !webhookUrl.get().isEmpty()) {
                    if (mc.player != null) {
                        BlockPos pos = mc.player.getBlockPos();
                        String playerName = MinecraftClient.getInstance().getSession().getUsername();
                        sendTotemPopWebhook(playerName, pos);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (disconnectWebhook.get() && !webhookUrl.get().isEmpty()) {
            String playerName = MinecraftClient.getInstance().getSession().getUsername();
            sendDisconnectWebhook(playerName);
        }
    }

    private void checkForBasesAroundPlayer() {
        if (mc.player == null || mc.world == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        int searchRadius = 60; // Search in a 50 block radius

        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -60; y <= 200; y++) { // Check 20 blocks up and down
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos checkPos = playerPos.add(x, y, z);

                    if (foundBases.contains(checkPos)) continue;

                    BlockEntity blockEntity = mc.world.getBlockEntity(checkPos);
                    if (blockEntity == null) continue;

                    boolean isSpawner = blockEntity instanceof MobSpawnerBlockEntity;
                    boolean isStorage = blockEntity instanceof ChestBlockEntity ||
                        blockEntity instanceof BarrelBlockEntity ||
                        blockEntity instanceof EnderChestBlockEntity ||
                        blockEntity instanceof ShulkerBoxBlockEntity ||
                        blockEntity instanceof HopperBlockEntity;

                    if (isSpawner || isStorage) {
                        foundBases.add(checkPos);

                        if (isSpawner && spawnersCritical.get()) {
                            ChatUtils.info("Spawner found at " + checkPos + ", disconnecting...");
                            disconnectAndNotify();
                            return;
                        }
                    }
                }
            }
        }

        // Check if we found enough storage blocks to consider it a base
        long storageCount = foundBases.size();
        if (storageCount >= storageBlockThreshold.get()) {
            ChatUtils.info("Base detected (" + storageCount + " storage blocks), disconnecting...");
            disconnectAndNotify();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();

        // Always check if player is stuck (20 seconds)
        if (isPlayerStuck()) {
            ChatUtils.info("Player stuck for 20 seconds, restarting loop...");
            ChatUtils.sendPlayerMsg("#stop");
            startLoop();
            return;
        }

        // Toggle modules
        toggleModule(AutoTotem.class, enableAutoTotem.get());
        toggleModule(AutoTool.class, enableAutoTool.get());
        toggleModule(AutoReplenish.class, enableAutoReplenish.get());
        toggleModule(StorageESP.class, enableStorageESP.get());
        toggleModule(AutoEat.class, enableAutoEat.get());
        toggleModule(AutoArmor.class, enableAutoArmor.get());
        toggleModule(AutoEXP.class, enableAutoExp.get());

        // Death detection
        if (mc.player != null) {
            float currentHealth = mc.player.getHealth();
            boolean isAlive = mc.player.isAlive();

            // Check if player just died
            if (playerWasAlive && !isAlive && currentHealth <= 0) {
                if (deathWebhook.get() && !webhookUrl.get().isEmpty()) {
                    BlockPos deathPos = mc.player.getBlockPos();
                    String playerName = MinecraftClient.getInstance().getSession().getUsername();

                    // Get reason of death from new method
                    String deathReason = getDeathReason();

                    sendDeathWebhook(playerName, deathPos, deathReason);
                }
            }

            lastHealth = currentHealth;
            playerWasAlive = isAlive;
        }

        // Main loop logic
        switch (loopStage) {
            case 0 -> {
                // RTP stage - wait for RTP to complete
                if (now - stageStartTime >= RTP_WAIT_DURATION) {
                    loopStage = 1; // Move to base checking after RTP
                    stageStartTime = now;
                    ChatUtils.info("RTP completed, checking for bases at surface...");
                }
            }
            case 1 -> {
                // Base checking after RTP
                checkForBasesAroundPlayer();
                if (now - stageStartTime >= BASE_CHECK_DURATION) {
                    loopStage = 2; // Move to mining stage
                    stageStartTime = now;
                    ChatUtils.info("Surface base check complete, no base found. Starting mining...");
                    startMining();
                }
            }
            case 2 -> {
                // Mining down stage
                if (mc.player.getY() <= mineYLevel.get() + 2) { // Add small buffer
                    ChatUtils.sendPlayerMsg("#stop");
                    loopStage = 3; // Move to base checking after mining
                    stageStartTime = now;
                    hasReachedGoal = true;
                    ChatUtils.info("Reached mining goal, checking for bases at depth...");
                }
            }
            case 3 -> {
                // Base checking after mining
                checkForBasesAroundPlayer();
                if (now - stageStartTime >= BASE_CHECK_DURATION) {
                    ChatUtils.info("Underground base check complete, no base found. Restarting loop...");
                    startLoop(); // Restart the entire loop
                }
            }
        }
    }

        private String getDeathReason() {
            if (mc.player == null) return "unknown";

            DamageSource lastDamage = ((LivingEntity) mc.player).getRecentDamageSource();

            if (lastDamage == null) return "unknown";

            String name = lastDamage.getName();

            // Optional: more readable translations
            switch (name) {
                case "player": return "another player";
                case "mob": return "a mob";
                case "fall": return "fall damage";
                case "lava": return "lava";
                case "fire": return "fire";
                case "drown": return "drowning";
                case "magic": return "magic";
                default: return name;
            }
        }

    private void disconnectAndNotify() {
        if (baseFindWebhook.get() && !webhookUrl.get().isEmpty()) {
            sendBaseFindWebhook();

            // Delay disconnect/toggle by 2 seconds (2000 milliseconds)
            Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                MinecraftClient.getInstance().execute(() -> {
                    if (mc.player != null) {
                        mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("YOU FOUND A BASE!")));
                        toggle();
                    }
                });
            }, 2, TimeUnit.SECONDS);
        } else {
            error("Webhook is enabled, but the URL is empty!");
        }
    }

    private void sendBaseFindWebhook() {
        try {
            String playerName = MinecraftClient.getInstance().getSession().getUsername();
            BlockPos playerPos = mc.player.getBlockPos();

            // Determine if this was found at surface or underground
            String location = (loopStage == 1) ? "surface" : "underground";

            // Count different types of storage blocks and spawners
            int chestCount = 0;
            int barrelCount = 0;
            int enderChestCount = 0;
            int shulkerBoxCount = 0;
            int hopperCount = 0;
            int spawnerCount = 0;

            for (BlockPos pos : foundBases) {
                if (mc.world == null) continue;
                BlockEntity blockEntity = mc.world.getBlockEntity(pos);
                if (blockEntity instanceof ChestBlockEntity) chestCount++;
                else if (blockEntity instanceof BarrelBlockEntity) barrelCount++;
                else if (blockEntity instanceof EnderChestBlockEntity) enderChestCount++;
                else if (blockEntity instanceof ShulkerBoxBlockEntity) shulkerBoxCount++;
                else if (blockEntity instanceof HopperBlockEntity) hopperCount++;
                else if (blockEntity instanceof MobSpawnerBlockEntity) spawnerCount++;
            }

            int totalStorage = chestCount + barrelCount + enderChestCount + shulkerBoxCount + hopperCount;

            StringBuilder description = new StringBuilder();
            description.append("Player **").append(playerName).append("** has successfully located a hidden base ")
                .append(location).append(" at coordinates **")
                .append(playerPos.getX()).append(", ")
                .append(playerPos.getY()).append(", ")
                .append(playerPos.getZ()).append("** with:\\n\\n");

            if (spawnerCount > 0) description.append("🔥 **").append(spawnerCount).append("** Spawner(s)\\n");
            if (chestCount > 0) description.append("📦 **").append(chestCount).append("** Chest(s)\\n");
            if (barrelCount > 0) description.append("🛢️ **").append(barrelCount).append("** Barrel(s)\\n");
            if (enderChestCount > 0) description.append("🎆 **").append(enderChestCount).append("** Ender Chest(s)\\n");
            if (shulkerBoxCount > 0) description.append("📫 **").append(shulkerBoxCount).append("** Shulker Box(es)\\n");
            if (hopperCount > 0) description.append("⚙️ **").append(hopperCount).append("** Hopper(s)\\n");

            description.append("\\n**Total Storage Blocks:** ").append(totalStorage);

            String jsonPayload = String.format("""
            {
              "username": "Base Alert",
              "embeds": [
                {
                  "title": "🏰 Base Discovery Confirmed!",
                  "description": "%s",
                  "color": 16711680,
                  "footer": { "text": "Sent by Glazed" }
                }
              ]
            }
            """, description.toString());

            sendWebhookRequest(jsonPayload, "Base find");
        } catch (Exception e) {
            error("Error creating base find webhook request: " + e.getMessage());
        }
    }

    private void sendTotemPopWebhook(String playerName, BlockPos pos) {
        try {
            String jsonPayload = String.format("""
                {
                  \"username\": \"Pop Alert\",
                  \"avatar_url\": \"https://static.wikia.nocookie.net/minecraft_gamepedia/images/2/2e/Totem_of_Undying_JE2_BE2.png/revision/latest?cb=20200522030253\",
                  \"embeds\": [
                    {
                      \"title\": \"⚡ Totem Pop at (%d, %d, %d)\",
                      \"description\": \"Player **%s** popped a totem of undying at coordinates **%d, %d, %d**.\",
                      \"color\": 16776960,
                      \"footer\": { \"text\": \"Sent by Glazed\" }
                    }
                  ]
                }
                """, pos.getX(), pos.getY(), pos.getZ(), playerName, pos.getX(), pos.getY(), pos.getZ());

            sendWebhookRequest(jsonPayload, "Totem pop");
        } catch (Exception e) {
            error("Error creating totem pop webhook request: " + e.getMessage());
        }
    }

    private void sendDeathWebhook(String playerName, BlockPos pos, String deathReason) {
        try {
            String jsonPayload = String.format("""
                {
                  \"username\": \"Death Alert\",
                  \"avatar_url\": \"https://art.pixilart.com/e342706146dbb3d.gif\",
                  \"embeds\": [
                    {
                      \"title\": \"💀 Death at (%d, %d, %d)\",
                      \"description\": \"Player **%s** died at coordinates **%d, %d, %d**\\n\\n**Cause:** %s\",
                      \"color\": 16711680,
                      \"footer\": { \"text\": \"Sent by Glazed\" }
                    }
                  ]
                }
                """, pos.getX(), pos.getY(), pos.getZ(), playerName, pos.getX(), pos.getY(), pos.getZ(), deathReason);

            sendWebhookRequest(jsonPayload, "Death");
        } catch (Exception e) {
            error("Error creating death webhook request: " + e.getMessage());
        }
    }

    private void sendDisconnectWebhook(String playerName) {
        try {
            String jsonPayload = String.format("""
                {
                  \"username\": \"Disconnect Alert\",
                  \"embeds\": [
                    {
                      \"title\": \"🔌 User %s Disconnected\",
                      \"description\": \"User **%s** has disconnected from the server.\",
                      \"color\": 8421504,
                      \"footer\": { \"text\": \"Sent by Glazed\" }
                    }
                  ]
                }
                """, playerName, playerName);

            sendWebhookRequest(jsonPayload, "Disconnect");
        } catch (Exception e) {
            error("Error creating disconnect webhook request: " + e.getMessage());
        }
    }

    private void sendWebhookRequest(String jsonPayload, String type) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl.get()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(10))
                .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300)
                        info(type + " webhook message sent successfully!");
                    else error("Failed to send " + type + " webhook message. Status code: " + response.statusCode());
                })
                .exceptionally(throwable -> {
                    error("Error sending " + type + " webhook message: " + throwable.getMessage());
                    return null;
                });

        } catch (Exception e) {
            error("Error creating " + type + " webhook request: " + e.getMessage());
        }
    }

    public enum RTPRegion {
        ASIA("asia"),
        EAST("east"),
        EU_CENTRAL("eu central"),
        EU_WEST("eu west"),
        OCEANIA("oceania"),
        WEST("west");

        private final String commandPart;

        RTPRegion(String commandPart) {
            this.commandPart = commandPart;
        }

        public String getCommandPart() {
            return commandPart;
        }
    }
}
