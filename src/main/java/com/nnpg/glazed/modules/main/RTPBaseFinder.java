package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import meteordevelopment.meteorclient.settings.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
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

import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import meteordevelopment.meteorclient.systems.modules.combat.AutoTotem;
import meteordevelopment.meteorclient.systems.modules.player.AutoTool;
import net.minecraft.util.math.ChunkPos;


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
        .max(80)
        .sliderMax(20)
        .sliderMin(-64)
        .build());

    private final Setting<Integer> baseThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("Base Threshold")
        .description("Minimum storage blocks to consider as a base and disconnect.")
        .defaultValue(4)
        .min(1)
        .sliderMax(50)
        .build());

    // Add storage blocks setting (you'll need to implement StorageBlockListSetting)
    private final Setting<List<BlockEntityType<?>>> storageBlocks = sgGeneral.add(new StorageBlockListSetting.Builder()
        .name("storage-blocks")
        .description("Select the storage blocks to search for.")
        .defaultValue(StorageBlockListSetting.STORAGE_BLOCKS)
        .build());

    private final Setting<Boolean> spawnersCritical = sgGeneral.add(new BoolSetting.Builder()
        .name("Spawners Critical")
        .description("Disconnect immediately on spawner if true, otherwise treat as storage.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> disconnectOnBaseFind = sgGeneral.add(new BoolSetting.Builder()
        .name("Disconnect on Base Find")
        .description("Automatically disconnect when a base is found.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> rtptotempop = sgGeneral.add(new BoolSetting.Builder()
        .name("RTP on Totem Pop")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> rtplowhealth = sgGeneral.add(new BoolSetting.Builder()
        .name("RTP on low health")
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

    private final Setting<Boolean> baseFindWebhook = sgwebhook.add(new BoolSetting.Builder()
        .name("Base Find Webhook")
        .description("Send webhook message when a base gets found")
        .defaultValue(false)
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

    private final Setting<String> webhookUrl = sgwebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL")
        .defaultValue("")
        .visible(this::isAnyWebhookEnabled)
        .build());

    private final Setting<Boolean> selfPing = sgwebhook.add(new BoolSetting.Builder()
        .name("Self Ping")
        .description("Ping yourself in the webhook message")
        .defaultValue(false)
        .visible(this::isAnyWebhookEnabled)
        .build()
    );

    private final Setting<String> discordId = sgwebhook.add(new StringSetting.Builder()
        .name("Discord ID")
        .description("Your Discord user ID for pinging")
        .defaultValue("")
        .visible(() -> isAnyWebhookEnabled() && selfPing.get())
        .build()
    );

    private int loopStage = 0;
    private long stageStartTime;
    private BlockPos lastPos;
    private long lastMoveTime;
    private final int RTP_WAIT_DURATION = 6000;
    private final int STUCK_TIMEOUT = 20000;
    private boolean emergencyRtpTriggered = false;


    private final Set<ChunkPos> processedChunks = new HashSet<>();

    private float lastHealth = 20.0f;
    private boolean playerWasAlive = true;

    public RTPBaseFinder() {
        super(GlazedAddon.CATEGORY, "RTPBaseFinder", "RTPs, mines to a Y level, and detects bases using chunk loading.");
    }

    private boolean isAnyWebhookEnabled() {
        return baseFindWebhook.get() || totemPopWebhook.get() || deathWebhook.get();
    }

    @Override
    public void onActivate() {
        ChatUtils.sendPlayerMsg("#set legitMine true");
        ChatUtils.sendPlayerMsg("#set smoothLook true");
        ChatUtils.sendPlayerMsg("#set antiCheatCompatibility true");
        ChatUtils.sendPlayerMsg("#freelook false");
        ChatUtils.sendPlayerMsg("#legitMineIncludeDiagonals true");
        ChatUtils.sendPlayerMsg("#smoothLookTicks 10");
        ChatUtils.sendPlayerMsg("#blocksToAvoidBreaking gravel");
        ChatUtils.sendPlayerMsg("#blocksToAvoidBreaking gravel");


        startLoop();
    }

    @Override
    public void onDeactivate() {
        ChatUtils.sendPlayerMsg("#stop");
        processedChunks.clear();
    }

    private void startLoop() {
        ChatUtils.sendPlayerMsg("/rtp " + rtpRegion.get().getCommandPart());
        loopStage = 0;
        stageStartTime = System.currentTimeMillis();
        updateMovementTracking();
        processedChunks.clear();
        info("Starting RTP to " + rtpRegion.get().getCommandPart());
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.player == null) return;

        ChunkPos chunkPos = event.chunk().getPos();
        if (processedChunks.contains(chunkPos)) return;

        StashChunk chunk = new StashChunk(chunkPos);

        for (BlockEntity blockEntity : event.chunk().getBlockEntities().values()) {
            BlockEntityType<?> type = blockEntity.getType();

            if (blockEntity instanceof MobSpawnerBlockEntity) {
                chunk.spawners++;
                continue;
            }

            if (storageBlocks.get().contains(type)) {
                if (blockEntity instanceof ChestBlockEntity) chunk.chests++;
                else if (blockEntity instanceof BarrelBlockEntity) chunk.barrels++;
                else if (blockEntity instanceof ShulkerBoxBlockEntity) chunk.shulkers++;
                else if (blockEntity instanceof EnderChestBlockEntity) chunk.enderChests++;
                else if (blockEntity instanceof AbstractFurnaceBlockEntity) chunk.furnaces++;
                else if (blockEntity instanceof DispenserBlockEntity) chunk.dispensersDroppers++;
                else if (blockEntity instanceof HopperBlockEntity) chunk.hoppers++;
            }
        }

        boolean isBaseFound = false;
        String detectionReason = "";

        if (spawnersCritical.get() && chunk.spawners > 0) {
            isBaseFound = true;
            detectionReason = "Spawner(s) detected (Critical mode)";
        }
        else if (chunk.getTotal() >= baseThreshold.get()) {
            isBaseFound = true;
            detectionReason = "Storage threshold reached (" + chunk.getTotal() + " blocks)";
        }

        if (isBaseFound) {
            processedChunks.add(chunkPos);
            info("Base found! Reason: " + detectionReason + " at chunk " + chunkPos.x + ", " + chunkPos.z);
            disconnectAndNotify(chunk, detectionReason);
        }
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

        if (!currentPos.equals(lastPos)) {
            lastPos = currentPos;
            lastMoveTime = now;
            return false;
        }

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
        ChatUtils.sendPlayerMsg("#goto " + pos.getX() + " " + mineYLevel.get() + " " + pos.getZ());
        info("Started mining down to Y level " + mineYLevel.get());
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof EntityStatusS2CPacket packet) {
            if (packet.getStatus() == 35) {
                if (totemPopWebhook.get() && !webhookUrl.get().isEmpty()) {
                    if (mc.player != null) {
                        BlockPos pos = mc.player.getBlockPos();
                        String playerName = MinecraftClient.getInstance().getSession().getUsername();
                        sendTotemPopWebhook(playerName, pos);
                    }
                }
                if (rtptotempop.get()) {
                    ChatUtils.sendPlayerMsg("#stop");
                    info("Totem popped! Stopping mining and restarting RTP loop for safety...");

                    Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                        MinecraftClient.getInstance().execute(() -> {
                            startLoop();
                        });
                    }, 1, TimeUnit.SECONDS);
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();

        if (isPlayerStuck()) {
            info("Player stuck for 20 seconds, restarting loop...");
            ChatUtils.sendPlayerMsg("#stop");
            startLoop();
            return;
        }

        toggleModule(AutoTotem.class, enableAutoTotem.get());
        toggleModule(AutoTool.class, enableAutoTool.get());
        toggleModule(AutoReplenish.class, enableAutoReplenish.get());
        toggleModule(StorageESP.class, enableStorageESP.get());
        toggleModule(AutoEat.class, enableAutoEat.get());
        toggleModule(AutoArmor.class, enableAutoArmor.get());
        toggleModule(AutoEXP.class, enableAutoExp.get());

        if (mc.player != null) {
            float currentHealth = mc.player.getHealth();
            boolean isAlive = mc.player.isAlive();

            if (currentHealth < 11.0f && isAlive && !emergencyRtpTriggered && rtplowhealth.get()) {
                info("Health dropped to " + currentHealth + " (below 5.5 hearts), emergency RTP...");
                ChatUtils.sendPlayerMsg("#stop");
                startLoop();
                emergencyRtpTriggered = true;
                return;
            }

            if (emergencyRtpTriggered && currentHealth >= 14.0f) {
                emergencyRtpTriggered = false;
            }

            if (playerWasAlive && !isAlive && currentHealth <= 0) {
                if (deathWebhook.get() && !webhookUrl.get().isEmpty()) {
                    BlockPos deathPos = mc.player.getBlockPos();
                    String playerName = MinecraftClient.getInstance().getSession().getUsername();
                    String deathReason = getDeathReason();
                    sendDeathWebhook(playerName, deathPos, deathReason);
                }
            }

            lastHealth = currentHealth;
            playerWasAlive = isAlive;
        }

        switch (loopStage) {
            case 0 -> {
                if (now - stageStartTime >= RTP_WAIT_DURATION) {
                    loopStage = 1;
                    stageStartTime = now;
                    info("RTP completed, starting mining...");
                    startMining();
                }
            }
            case 1 -> {
                if (mc.player.getY() <= mineYLevel.get() + 2) {
                    ChatUtils.sendPlayerMsg("#stop");
                    info("Reached mining goal, restarting loop...");
                    startLoop();
                }
            }
        }
    }

    private String getDeathReason() {
        if (mc.player == null) return "unknown";

        DamageSource lastDamage = ((LivingEntity) mc.player).getRecentDamageSource();

        if (lastDamage == null) return "unknown";

        String name = lastDamage.getName();

        switch (name) {
            case "player":
                return "another player";
            case "mob":
                return "a mob";
            case "fall":
                return "fall damage";
            case "lava":
                return "lava";
            case "fire":
                return "fire";
            case "drown":
                return "drowning";
            case "magic":
                return "magic";
            default:
                return name;
        }
    }

    private void disconnectAndNotify(StashChunk chunk, String detectionReason) {
        if (baseFindWebhook.get() && !webhookUrl.get().isEmpty()) {
            sendBaseFindWebhook(chunk, detectionReason);

            if (disconnectOnBaseFind.get()) {
                Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                    MinecraftClient.getInstance().execute(() -> {
                        if (mc.player != null) {
                            mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("YOU FOUND A BASE!")));
                            toggle();
                        }
                    });
                }, 2, TimeUnit.SECONDS);
            } else {
                info("Base found but disconnect is disabled. Continuing mining...");
            }
        } else if (disconnectOnBaseFind.get()) {
            Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                MinecraftClient.getInstance().execute(() -> {
                    if (mc.player != null) {
                        mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("YOU FOUND A BASE!")));
                        toggle();
                    }
                });
            }, 2, TimeUnit.SECONDS);
        }
    }

    private void sendBaseFindWebhook(StashChunk chunk, String detectionReason) {
        try {
            String playerName = MinecraftClient.getInstance().getSession().getUsername();
            BlockPos playerPos = mc.player.getBlockPos();

            String messageContent = "";
            if (selfPing.get() && !discordId.get().trim().isEmpty()) {
                messageContent = String.format("<@%s>", discordId.get().trim());
            }

            StringBuilder description = new StringBuilder();
            description.append("Player **").append(playerName).append("** discovered a base in chunk at coordinates **")
                .append(chunk.x).append(", ").append(chunk.z).append("** containing:\\n\\n");

            if (chunk.spawners > 0) description.append("🔥 **").append(chunk.spawners).append("** Spawner(s)\\n");
            if (chunk.chests > 0) description.append("📦 **").append(chunk.chests).append("** Chest(s)\\n");
            if (chunk.barrels > 0) description.append("🛢️ **").append(chunk.barrels).append("** Barrel(s)\\n");
            if (chunk.enderChests > 0)
                description.append("🎆 **").append(chunk.enderChests).append("** Ender Chest(s)\\n");
            if (chunk.shulkers > 0) description.append("📫 **").append(chunk.shulkers).append("** Shulker Box(es)\\n");
            if (chunk.hoppers > 0) description.append("⚙️ **").append(chunk.hoppers).append("** Hopper(s)\\n");
            if (chunk.furnaces > 0) description.append("🔥 **").append(chunk.furnaces).append("** Furnace(s)\\n");
            if (chunk.dispensersDroppers > 0)
                description.append("🎯 **").append(chunk.dispensersDroppers).append("** Dispenser(s)/Dropper(s)\\n");

            description.append("\\n**Total Storage Blocks:** ").append(chunk.getTotal());
            description.append("\\n**Detection Reason:** ").append(detectionReason);
            description.append("\\n**Player Position:** ").append(playerPos.getX()).append(", ")
                .append(playerPos.getY()).append(", ").append(playerPos.getZ());

            String jsonPayload = String.format("""
            {
              "username": "Glazed Webhook",
              "avatar_url": "https://i.imgur.com/OL2y1cr.png",
              "content": "%s",
              "embeds": [
                {
                  "title": "🏰 Base Discovery Confirmed!",
                  "description": "%s",
                  "color": 16711680,
                  "author": {
                    "name": "Base Alert"
                  },
                  "footer": { "text": "Sent by Glazed" }
                }
              ]
            }
            """, messageContent, description.toString());

            sendWebhookRequest(jsonPayload, "Base find");
        } catch (Exception e) {
            error("Error creating base find webhook request: " + e.getMessage());
        }
    }

    private void sendTotemPopWebhook(String playerName, BlockPos pos) {
        try {
            String jsonPayload = String.format("""
                {
                  \"username\": \"Glazed Webhook\",
                  \"avatar_url\": \"https://i.imgur.com/OL2y1cr.png\",
                  \"embeds\": [
                    {
                      \"title\": \"⚡ Totem Pop at (%d, %d, %d)\",
                      \"description\": \"Player **%s** popped a totem of undying at coordinates **%d, %d, %d**.\",
                      \"color\": 16776960,
                      \"author\": {
                        \"name\": \"AutoTotem Alert\"
                      },
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
                  \"username\": \"Glazed Webhook\",
                  \"avatar_url\": \"https://i.imgur.com/OL2y1cr.png\",
                  \"embeds\": [
                    {
                      \"title\": \"💀 Death at (%d, %d, %d)\",
                      \"description\": \"Player **%s** died at coordinates **%d, %d, %d**\\n\\n**Cause:** %s\",
                      \"color\": 16711680,
                      \"author\": {
                        \"name\": \"Death Alert\"
                      },
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
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        info(type + " webhook sent successfully!");
                    } else {
                        error("Failed to send " + type + " webhook. Status: " + response.statusCode());
                    }
                })
                .exceptionally(throwable -> {
                    error("Error sending " + type + " webhook: " + throwable.getMessage());
                    return null;
                });
        } catch (Exception e) {
            error("Error creating " + type + " webhook request: " + e.getMessage());
        }
    }

    private static class StashChunk {
        public final int x, z;
        public int chests, barrels, shulkers, enderChests, furnaces, dispensersDroppers, hoppers, spawners;

        public StashChunk(ChunkPos pos) {
            this.x = pos.x * 16;
            this.z = pos.z * 16;
        }

        public int getTotal() {
            return chests + barrels + shulkers + enderChests + furnaces + dispensersDroppers + hoppers + spawners;
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
