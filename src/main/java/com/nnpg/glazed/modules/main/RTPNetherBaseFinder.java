package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RTPNetherBaseFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");

    private final Setting<List<BlockEntityType<?>>> storageBlocks = sgGeneral.add(new StorageBlockListSetting.Builder()
        .name("storage-blocks")
        .description("Storage blocks to search for in the Nether.")
        .defaultValue(StorageBlockListSetting.STORAGE_BLOCKS)
        .build()
    );

    private final Setting<Integer> minimumStorageCount = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-storage-count")
        .description("Minimum storage blocks in a chunk to count as a stash.")
        .defaultValue(4)
        .min(1)
        .sliderMin(1)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> criticalSpawner = sgGeneral.add(new BoolSetting.Builder()
        .name("critical-spawner")
        .description("Mark chunk as stash even if only a single spawner is found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> rtpInterval = sgGeneral.add(new IntSetting.Builder()
        .name("rtp-interval")
        .description("Interval between /rtp nether commands in seconds.")
        .defaultValue(10)
        .min(5)
        .sliderMin(5)
        .sliderMax(60)
        .build()
    );

    private final Setting<Boolean> disconnectOnBaseFind = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect-on-base-find")
        .description("Automatically disconnect when a Nether base is found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sendNotifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Sends Minecraft notifications when new Nether stashes are found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Mode> notificationMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("The mode to use for notifications.")
        .defaultValue(Mode.Both)
        .visible(sendNotifications::get)
        .build()
    );

    private final Setting<Boolean> enableWebhook = sgWebhook.add(new BoolSetting.Builder()
        .name("webhook")
        .description("Send webhook notifications when Nether stashes are found.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL.")
        .defaultValue("")
        .visible(enableWebhook::get)
        .build()
    );

    private final Setting<Boolean> selfPing = sgWebhook.add(new BoolSetting.Builder()
        .name("self-ping")
        .description("Ping yourself in the webhook message.")
        .defaultValue(false)
        .visible(enableWebhook::get)
        .build()
    );

    private final Setting<String> discordId = sgWebhook.add(new StringSetting.Builder()
        .name("discord-id")
        .description("Your Discord user ID for pinging.")
        .defaultValue("")
        .visible(() -> enableWebhook.get() && selfPing.get())
        .build()
    );

    public List<NetherStashChunk> foundStashes = new ArrayList<>();
    private final Set<ChunkPos> processedChunks = new HashSet<>();
    private long lastRtpTime = 0;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public RTPNetherBaseFinder() {
        super(GlazedAddon.CATEGORY, "RTPNetherBaseFinder", "Continuously RTPs to the Nether and searches for stashes.");
    }

    @Override
    public void onActivate() {
        foundStashes.clear();
        processedChunks.clear();
        lastRtpTime = 0;
        info("Started RTP Nether Base Finder");
    }

    @Override
    public void onDeactivate() {
        processedChunks.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            if (isActive()) toggle();
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRtpTime >= rtpInterval.get() * 1000L) {
            ChatUtils.sendPlayerMsg("/rtp nether");
            lastRtpTime = currentTime;
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.player == null) return;

        ChunkPos chunkPos = event.chunk().getPos();
        if (processedChunks.contains(chunkPos)) return;

        NetherStashChunk chunk = new NetherStashChunk(chunkPos);
        boolean hasSpawner = false;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 10; y < 128; y++) { // Nether stash Y-range
                    if (mc.world.getBlockState(new BlockPos(chunkPos.getStartX() + x, y, chunkPos.getStartZ() + z)).getBlock() == Blocks.SPAWNER) {
                        chunk.spawners++;
                        hasSpawner = true;
                    }
                }
            }
        }

        for (BlockEntity blockEntity : event.chunk().getBlockEntities().values()) {
            BlockEntityType<?> type = blockEntity.getType();

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

        boolean isStash = false;
        boolean isCriticalSpawner = false;
        String detectionReason = "";

        if (criticalSpawner.get() && hasSpawner) {
            isStash = true;
            isCriticalSpawner = true;
            detectionReason = "Spawner(s) detected (Critical mode)";
        } else if (chunk.getTotalNonSpawner() >= minimumStorageCount.get()) {
            isStash = true;
            detectionReason = "Storage threshold reached (" + chunk.getTotalNonSpawner() + " blocks)";
        }

        if (isStash) {
            processedChunks.add(chunkPos);

            NetherStashChunk prevChunk = null;
            int existingIndex = foundStashes.indexOf(chunk);
            if (existingIndex < 0) foundStashes.add(chunk);
            else prevChunk = foundStashes.set(existingIndex, chunk);

            if (sendNotifications.get() && (!chunk.equals(prevChunk) || !chunk.countsEqual(prevChunk))) {
                String stashType = isCriticalSpawner ? "Nether spawner base" : "Nether stash";
                switch (notificationMode.get()) {
                    case Chat -> info("Found %s at (highlight)%s(default), (highlight)%s(default). %s",
                        stashType, chunk.x, chunk.z, detectionReason);
                    case Toast -> {
                        MeteorToast toast = new MeteorToast(Items.NETHER_BRICK, title,
                            "Found " + stashType.substring(0, 1).toUpperCase() + stashType.substring(1) + "!");
                        mc.getToastManager().add(toast);
                    }
                    case Both -> {
                        info("Found %s at (highlight)%s(default), (highlight)%s(default). %s",
                            stashType, chunk.x, chunk.z, detectionReason);
                        MeteorToast toast = new MeteorToast(Items.NETHER_BRICK, title,
                            "Found " + stashType.substring(0, 1).toUpperCase() + stashType.substring(1) + "!");
                        mc.getToastManager().add(toast);
                    }
                }
            }

            if (enableWebhook.get() && (!chunk.equals(prevChunk) || !chunk.countsEqual(prevChunk))) {
                sendWebhookNotification(chunk, isCriticalSpawner, detectionReason);
            }

            if (disconnectOnBaseFind.get()) {
                disconnectPlayer(isCriticalSpawner ? "Nether spawner base" : "Nether stash", chunk);
            }
        }
    }

    private void disconnectPlayer(String stashType, NetherStashChunk chunk) {
        info("Disconnecting due to " + stashType + " found at " + chunk.x + ", " + chunk.z);
        toggle();

        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            if (mc.player != null) {
                mc.player.networkHandler.onDisconnect(
                    new DisconnectS2CPacket(Text.literal("NETHER STASH FOUND AT " + chunk.x + ", " + chunk.z + "!"))
                );
            }
        }, 1, TimeUnit.SECONDS);
    }

    private void sendWebhookNotification(NetherStashChunk chunk, boolean isCriticalSpawner, String detectionReason) {
        String url = webhookUrl.get().trim();
        if (url.isEmpty()) {
            warning("Webhook URL not configured!");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String serverInfo = mc.getCurrentServerEntry() != null ?
                    mc.getCurrentServerEntry().address : "Unknown Server";

                String messageContent = "";
                if (selfPing.get() && !discordId.get().trim().isEmpty()) {
                    messageContent = String.format("<@%s>", discordId.get().trim());
                }

                String stashType = isCriticalSpawner ? "Nether Spawner Base" : "Nether Stash";
                String description = String.format("%s found at Nether coordinates %d, %d!", stashType, chunk.x, chunk.z);

                StringBuilder itemsFound = new StringBuilder();
                int totalItems = chunk.appendItemCounts(itemsFound, isCriticalSpawner);

                String jsonPayload = String.format(
                    "{\"content\":\"%s\"," +
                        "\"username\":\"RTP Nether-Stashfinder\"," +
                        "\"embeds\":[{" +
                        "\"title\":\"🔥 Nether Stashfinder Alert\"," +
                        "\"description\":\"%s\"," +
                        "\"color\":%d," +
                        "\"fields\":[" +
                        "{\"name\":\"Detection Reason\",\"value\":\"%s\",\"inline\":false}," +
                        "{\"name\":\"Total Items Found\",\"value\":\"%d\",\"inline\":false}," +
                        "{\"name\":\"Items Breakdown\",\"value\":\"%s\",\"inline\":false}," +
                        "{\"name\":\"Nether Coordinates\",\"value\":\"%d, %d\",\"inline\":true}," +
                        "{\"name\":\"Server\",\"value\":\"%s\",\"inline\":true}," +
                        "{\"name\":\"Time\",\"value\":\"<t:%d:R>\",\"inline\":true}" +
                        "]," +
                        "\"footer\":{\"text\":\"RTP Nether-Stashfinder\"}" +
                        "}]}",
                    messageContent.replace("\"", "\\\""),
                    description.replace("\"", "\\\""),
                    isCriticalSpawner ? 16734296 : 16509190,
                    detectionReason.replace("\"", "\\\""),
                    totalItems,
                    itemsFound.toString().replace("\"", "\\\""),
                    chunk.x, chunk.z,
                    serverInfo.replace("\"", "\\\""),
                    System.currentTimeMillis() / 1000
                );

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(30))
                    .build();

                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            } catch (IOException | InterruptedException e) {
                error("Failed to send webhook: " + e.getMessage());
            }
        });
    }

    @Override
    public String getInfoString() {
        return String.valueOf(foundStashes.size());
    }

    public enum Mode { Chat, Toast, Both }

    public static class NetherStashChunk {
        public ChunkPos chunkPos;
        public int x, z;
        public int chests, barrels, shulkers, enderChests, furnaces, dispensersDroppers, hoppers, spawners;

        public NetherStashChunk(ChunkPos chunkPos) {
            this.chunkPos = chunkPos;
            x = chunkPos.x * 16 + 8;
            z = chunkPos.z * 16 + 8;
        }

        public int getTotalNonSpawner() {
            return chests + barrels + shulkers + enderChests + furnaces + dispensersDroppers + hoppers;
        }

        public boolean countsEqual(NetherStashChunk c) {
            if (c == null) return false;
            return chests == c.chests && barrels == c.barrels && shulkers == c.shulkers &&
                enderChests == c.enderChests && furnaces == c.furnaces &&
                dispensersDroppers == c.dispensersDroppers && hoppers == c.hoppers && spawners == c.spawners;
        }

        public int appendItemCounts(StringBuilder sb, boolean includeSpawner) {
            int total = 0;
            if (chests > 0) { sb.append("Chests: ").append(chests).append("\\n"); total += chests; }
            if (barrels > 0) { sb.append("Barrels: ").append(barrels).append("\\n"); total += barrels; }
            if (shulkers > 0) { sb.append("Shulker Boxes: ").append(shulkers).append("\\n"); total += shulkers; }
            if (enderChests > 0) { sb.append("Ender Chests: ").append(enderChests).append("\\n"); total += enderChests; }
            if (furnaces > 0) { sb.append("Furnaces: ").append(furnaces).append("\\n"); total += furnaces; }
            if (dispensersDroppers > 0) { sb.append("Dispensers/Droppers: ").append(dispensersDroppers).append("\\n"); total += dispensersDroppers; }
            if (hoppers > 0) { sb.append("Hoppers: ").append(hoppers).append("\\n"); total += hoppers; }
            if (includeSpawner && spawners > 0) sb.append("Spawners: Present\\n");
            return total;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NetherStashChunk c)) return false;
            return Objects.equals(chunkPos, c.chunkPos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(chunkPos);
        }
    }
}
