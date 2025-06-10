package com.nnpg.glazed.modules;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

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

    private final Setting<Boolean> sendOnActivation = sgwebhook.add(new BoolSetting.Builder()
        .name("Activate")
        .description("Send webhook message when a base gets found")
        .defaultValue(true)
        .build());

    private final Setting<String> webhookUrl = sgwebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL")
        .defaultValue("")
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


    private int rtpStage = -1; // -1 = just RTP'd, waiting, 0 = start mining, 1 = mining down, 2 = checking for bases
    private BlockPos lastPos;
    private long lastMoveTime;
    private boolean hasReachedGoal = false;
    private long baseCheckStartTime = 0;
    private final int BASE_CHECK_DURATION = 5000; // Check for 5 seconds

    private final Set<BlockPos> foundBases = new HashSet<>();

    public RTPBaseFinder() {
        super(GlazedAddon.CATEGORY, "RTPBaseFinder", "RTPs, mines to a Y level, and detects bases using Baritone.");
    }

    @Override
    public void onActivate() {
        // Start the cycle with RTP only
        ChatUtils.sendPlayerMsg("/rtp " + rtpRegion.get().getCommandPart());
        rtpStage = -1; // Wait for RTP to complete
        lastPos = mc.player.getBlockPos();
        lastMoveTime = System.currentTimeMillis();
        hasReachedGoal = false;
        foundBases.clear();
    }

    @Override
    public void onDeactivate() {
        ChatUtils.sendPlayerMsg("#stop");
    }

    private void toggleModule(Class<? extends Module> moduleClass, boolean enable) {
        Module module = Modules.get().get(moduleClass);
        if (module != null) {
            if (enable && !module.isActive()) module.toggle();
            else if (!enable && module.isActive()) module.toggle();
        }
    }

    private void startGoto() {
        BlockPos pos = mc.player.getBlockPos();
        ChatUtils.sendPlayerMsg("#set legitMine true");
        ChatUtils.sendPlayerMsg("#goto " + pos.getX() + " " + mineYLevel.get() + " " + pos.getZ());
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        // Always check for bases when chunks load
        for (BlockEntity blockEntity : event.chunk().getBlockEntities().values()) {
            BlockPos pos = blockEntity.getPos();
            if (foundBases.contains(pos)) continue;

            boolean isSpawner = blockEntity instanceof MobSpawnerBlockEntity;
            boolean isStorage = blockEntity instanceof ChestBlockEntity ||
                blockEntity instanceof BarrelBlockEntity ||
                blockEntity instanceof EnderChestBlockEntity ||
                blockEntity instanceof ShulkerBoxBlockEntity;

            if (isSpawner || isStorage) {
                foundBases.add(pos);

                if (isSpawner && spawnersCritical.get()) {
                    ChatUtils.info("Spawner found at " + pos + ", disconnecting...");
                    disconnectAndNotify();
                    return;
                }

                long storageCount = foundBases.stream()
                    .filter(p -> {
                        if (mc.world == null) return false;
                        BlockPos blockPos = new BlockPos(p.getX(), p.getY(), p.getZ());
                        return mc.world.getBlockState(blockPos).getBlock() instanceof BlockEntityProvider;
                    })
                    .count();

                if (storageCount >= storageBlockThreshold.get()) {
                    ChatUtils.info("Base detected (" + storageCount + " storage blocks), disconnecting...");
                    disconnectAndNotify();
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        BlockPos currentPos = mc.player.getBlockPos();
        long now = System.currentTimeMillis();

        // Toggle modules
        toggleModule(AutoTotem.class, enableAutoTotem.get());
        toggleModule(AutoTool.class, enableAutoTool.get());
        toggleModule(AutoReplenish.class, enableAutoReplenish.get());
        toggleModule(StorageESP.class, enableStorageESP.get());
        toggleModule(AutoEXP.class, enableAutoEat.get());
        toggleModule(AutoArmor.class, enableAutoArmor.get());
        toggleModule(AutoEXP.class, enableAutoExp.get());


        // Update movement tracking
        if (!currentPos.equals(lastPos)) {
            lastMoveTime = now;
            lastPos = currentPos;
        } else if (now - lastMoveTime > 10000) {
            // Stuck for 10 seconds, restart cycle
            ChatUtils.info("Stuck for too long, restarting...");
            restartCycle();
            return;
        }

        switch (rtpStage) {
            case -1 -> {
                // Just RTP'd, wait a bit before starting mining to ensure RTP completed
                if (now - lastMoveTime > 3000) { // Wait 3 seconds after last movement
                    rtpStage = 0;
                }
            }
            case 0 -> {
                // Now start mining down
                startGoto();
                rtpStage = 1;
                hasReachedGoal = false;
            }
            case 1 -> {
                // Mining down
                if (mc.player.getY() <= mineYLevel.get() + 2) { // Add small buffer
                    ChatUtils.sendPlayerMsg("#stop");
                    rtpStage = 2;
                    hasReachedGoal = true;
                    baseCheckStartTime = now;
                    ChatUtils.info("Reached mining goal, checking for bases...");
                }
            }
            case 2 -> {
                // Checking for bases
                if (now - baseCheckStartTime >= BASE_CHECK_DURATION) {
                    ChatUtils.info("Base check complete, no base found. Rtping...");
                    restartCycle();
                }
            }
        }
    }

    private void restartCycle() {
        ChatUtils.sendPlayerMsg("#stop");
        foundBases.clear(); // Clear found bases for next location
        ChatUtils.sendPlayerMsg("/rtp " + rtpRegion.get().getCommandPart());
        rtpStage = -1; // Wait for RTP to complete
        hasReachedGoal = false;
        lastMoveTime = System.currentTimeMillis();
    }

    private void disconnectAndNotify() {
        if (sendOnActivation.get()) {
            if (!webhookUrl.get().isEmpty()) sendWebhookMessage();
            else error("Webhook is enabled, but the URL is empty!");
        }
        mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("YOU FOUND A BASE!")));
        toggle();
    }

    private void sendWebhookMessage() {
        try {
            String playerName = MinecraftClient.getInstance().getSession().getUsername();
            String jsonPayload = String.format("""
                {
                  \"embeds\": [
                    {
                      \"title\": \"FOUND A BASE\",
                      \"description\": \"Player %s has found a base and successfully disconnected.\",
                      \"color\": 16711680,
                      \"footer\": { \"text\": \"Sent by Glazed\" }
                    }
                  ]
                }
                """, playerName);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl.get()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(10))
                .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300)
                        info("Webhook message sent successfully!");
                    else error("Failed to send webhook message. Status code: " + response.statusCode());
                })
                .exceptionally(throwable -> {
                    error("Error sending webhook message: " + throwable.getMessage());
                    return null;
                });

        } catch (Exception e) {
            error("Error creating webhook request: " + e.getMessage());
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
