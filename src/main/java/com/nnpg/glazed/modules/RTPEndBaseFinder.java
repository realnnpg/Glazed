package com.nnpg.glazed.modules;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.orbit.EventHandler;
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

public class RTPEndBaseFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");

    //SETTINGS
    private final Setting<List<BlockEntityType<?>>> storageBlocks = sgGeneral.add(new StorageBlockListSetting.Builder()
        .name("storage-blocks")
        .description("Select the storage blocks to search for.")
        .defaultValue(StorageBlockListSetting.STORAGE_BLOCKS)
        .build()
    );

    private final Setting<Integer> minimumStorageCount = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-storage-count")
        .description("The minimum amount of storage blocks in a chunk to record the chunk.")
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
        .description("Interval between RTP commands in seconds.")
        .defaultValue(10)
        .min(5)
        .sliderMin(5)
        .sliderMax(60)
        .build()
    );

    private final Setting<Boolean> disconnectOnBaseFind = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect-on-base-find")
        .description("Automatically disconnect when a base is found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> lookDown = sgGeneral.add(new BoolSetting.Builder()
        .name("look-down")
        .description("Automatically look down to avoid enderman aggro.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sendNotifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Sends Minecraft notifications when new stashes are found.")
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
        .description("Send webhook notifications when stashes are found")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL")
        .defaultValue("")
        .visible(enableWebhook::get)
        .build()
    );

    private final Setting<Boolean> selfPing = sgWebhook.add(new BoolSetting.Builder()
        .name("self-ping")
        .description("Ping yourself in the webhook message")
        .defaultValue(false)
        .visible(enableWebhook::get)
        .build()
    );

    private final Setting<String> discordId = sgWebhook.add(new StringSetting.Builder()
        .name("discord-id")
        .description("Your Discord user ID for pinging")
        .defaultValue("")
        .visible(() -> enableWebhook.get() && selfPing.get())
        .build()
    );


    public List<EndStashChunk> foundStashes = new ArrayList<>();
    private final Set<ChunkPos> processedChunks = new HashSet<>();
    private long lastRtpTime = 0;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();


    private Float originalPitch = null;

    public RTPEndBaseFinder() {
        super(GlazedAddon.CATEGORY, "RTPEndBaseFinder", "Continuously RTPs to the End and searches for stashes.");
    }

    @Override
    public void onActivate() {
        foundStashes.clear();
        processedChunks.clear();
        lastRtpTime = 0;


        if (lookDown.get() && mc.player != null) {
            originalPitch = mc.player.getPitch();
        }

        info("Started RTP End Base Finder");
    }

    @Override
    public void onDeactivate() {
        processedChunks.clear();


        if (originalPitch != null && mc.player != null) {
            mc.player.setPitch(originalPitch);
            originalPitch = null;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;


        if (lookDown.get()) {
            mc.player.setPitch(90.0f); // 90 degrees down
        }

        long currentTime = System.currentTimeMillis();

        if (currentTime - lastRtpTime >= rtpInterval.get() * 1000L) {
            ChatUtils.sendPlayerMsg("/rtp end");
            lastRtpTime = currentTime;
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.player == null) return;

        ChunkPos chunkPos = event.chunk().getPos();

        //SKIP PROCESSED CHUNKS
        if (processedChunks.contains(chunkPos)) return;

        EndStashChunk chunk = new EndStashChunk(chunkPos);

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

        boolean isStash = false;
        boolean isCriticalSpawner = false;
        String detectionReason = "";

        if (criticalSpawner.get() && chunk.spawners > 0) {
            isStash = true;
            isCriticalSpawner = true;
            detectionReason = "Spawner(s) detected (Critical mode)";
        } else if (chunk.getTotal() >= minimumStorageCount.get()) {
            isStash = true;
            detectionReason = "Storage threshold reached (" + chunk.getTotal() + " blocks)";
        }

        if (isStash) {
            processedChunks.add(chunkPos);

            EndStashChunk prevChunk = null;
            int existingIndex = foundStashes.indexOf(chunk);

            if (existingIndex < 0) {
                foundStashes.add(chunk);
            } else {
                prevChunk = foundStashes.set(existingIndex, chunk);
            }


            if (sendNotifications.get() && (!chunk.equals(prevChunk) || !chunk.countsEqual(prevChunk))) {
                String stashType = isCriticalSpawner ? "End spawner base" : "End stash";

                switch (notificationMode.get()) {
                    case Chat -> info("Found %s at (highlight)%s(default), (highlight)%s(default). %s",
                        stashType, chunk.x, chunk.z, detectionReason);
                    case Toast -> {
                        MeteorToast toast = new MeteorToast(Items.ENDER_CHEST, title,
                            "Found " + stashType.substring(0, 1).toUpperCase() + stashType.substring(1) + "!");
                        mc.getToastManager().add(toast);
                    }
                    case Both -> {
                        info("Found %s at (highlight)%s(default), (highlight)%s(default). %s",
                            stashType, chunk.x, chunk.z, detectionReason);
                        MeteorToast toast = new MeteorToast(Items.ENDER_CHEST, title,
                            "Found " + stashType.substring(0, 1).toUpperCase() + stashType.substring(1) + "!");
                        mc.getToastManager().add(toast);
                    }
                }
            }


            if (enableWebhook.get() && (!chunk.equals(prevChunk) || !chunk.countsEqual(prevChunk))) {
                sendWebhookNotification(chunk, isCriticalSpawner, detectionReason);
            }

            // Disconnect
            if (disconnectOnBaseFind.get()) {
                String stashTypeForDisconnect = isCriticalSpawner ? "End spawner base" : "End stash";
                disconnectPlayer(stashTypeForDisconnect, chunk);
            }
        }
    }

    private void disconnectPlayer(String stashType, EndStashChunk chunk) {
        info("Disconnecting due to " + stashType + " found at " + chunk.x + ", " + chunk.z);

        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            if (mc.player != null) {
                mc.player.networkHandler.onDisconnect(
                    new DisconnectS2CPacket(Text.literal("END STASH FOUND AT " + chunk.x + ", " + chunk.z + "!"))
                );
                toggle();
            }
        }, 1, TimeUnit.SECONDS);
    }

    private void sendWebhookNotification(EndStashChunk chunk, boolean isCriticalSpawner, String detectionReason) {
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

                String stashType = isCriticalSpawner ? "End Spawner Base" : "End Stash";
                String description = String.format("%s found at End coordinates %d, %d!", stashType, chunk.x, chunk.z);

                StringBuilder itemsFound = new StringBuilder();
                int totalItems = 0;

                if (chunk.spawners > 0) { itemsFound.append("Spawners: ").append(chunk.spawners).append("\\n"); totalItems += chunk.spawners; }
                if (chunk.chests > 0) { itemsFound.append("Chests: ").append(chunk.chests).append("\\n"); totalItems += chunk.chests; }
                if (chunk.barrels > 0) { itemsFound.append("Barrels: ").append(chunk.barrels).append("\\n"); totalItems += chunk.barrels; }
                if (chunk.shulkers > 0) { itemsFound.append("Shulker Boxes: ").append(chunk.shulkers).append("\\n"); totalItems += chunk.shulkers; }
                if (chunk.enderChests > 0) { itemsFound.append("Ender Chests: ").append(chunk.enderChests).append("\\n"); totalItems += chunk.enderChests; }
                if (chunk.furnaces > 0) { itemsFound.append("Furnaces: ").append(chunk.furnaces).append("\\n"); totalItems += chunk.furnaces; }
                if (chunk.dispensersDroppers > 0) { itemsFound.append("Dispensers/Droppers: ").append(chunk.dispensersDroppers).append("\\n"); totalItems += chunk.dispensersDroppers; }
                if (chunk.hoppers > 0) { itemsFound.append("Hoppers: ").append(chunk.hoppers).append("\\n"); totalItems += chunk.hoppers; }
//EMBED
                String jsonPayload = String.format(
                    "{\"content\":\"%s\"," +
                        "\"username\":\"RTP End-Stashfinder\"," +
                        "\"avatar_url\":\"https://i.imgur.com/OL2y1cr.png\"," +
                        "\"embeds\":[{" +
                        "\"title\":\"🌌 End Stashfinder Alert\"," +
                        "\"description\":\"%s\"," +
                        "\"color\":%d," +
                        "\"fields\":[" +
                        "{\"name\":\"Detection Reason\",\"value\":\"%s\",\"inline\":false}," +
                        "{\"name\":\"Total Items Found\",\"value\":\"%d\",\"inline\":false}," +
                        "{\"name\":\"Items Breakdown\",\"value\":\"%s\",\"inline\":false}," +
                        "{\"name\":\"End Coordinates\",\"value\":\"%d, %d\",\"inline\":true}," +
                        "{\"name\":\"Server\",\"value\":\"%s\",\"inline\":true}," +
                        "{\"name\":\"Time\",\"value\":\"<t:%d:R>\",\"inline\":true}" +
                        "]," +
                        "\"footer\":{\"text\":\"RTP End-Stashfinder\"}" +
                        "}]}",
                    messageContent.replace("\"", "\\\""),
                    description.replace("\"", "\\\""),
                    isCriticalSpawner ? 9830144 : 8388736, // Dark purple for spawners, dark green for stashes
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

                HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 204) {
                    info("Webhook notification sent successfully");
                } else {
                    error("Webhook failed with status: " + response.statusCode());
                }

            } catch (IOException | InterruptedException e) {
                error("Failed to send webhook: " + e.getMessage());
            }
        });
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {

        foundStashes.sort(Comparator.comparingInt(value -> -value.getTotal()));

        WVerticalList list = theme.verticalList();


        WButton clear = list.add(theme.button("Clear")).widget();

        WTable table = new WTable();
        if (!foundStashes.isEmpty()) list.add(table);

        clear.action = () -> {
            foundStashes.clear();
            processedChunks.clear();
            table.clear();
        };


        fillTable(theme, table);

        return list;
    }

    private void fillTable(GuiTheme theme, WTable table) {
        for (EndStashChunk chunk : foundStashes) {
            table.add(theme.label("End Pos: " + chunk.x + ", " + chunk.z));
            table.add(theme.label("Total: " + chunk.getTotal()));

            WButton open = table.add(theme.button("Details")).widget();
            open.action = () -> mc.setScreen(new EndStashScreen(theme, chunk));

            WButton gotoBtn = table.add(theme.button("Goto")).widget();
            gotoBtn.action = () -> PathManagers.get().moveTo(new BlockPos(chunk.x, 0, chunk.z), true);

            WMinus delete = table.add(theme.minus()).widget();
            delete.action = () -> {
                if (foundStashes.remove(chunk)) {
                    processedChunks.remove(chunk.chunkPos);
                    table.clear();
                    fillTable(theme, table);
                }
            };

            table.row();
        }
    }

    @Override
    public String getInfoString() {
        return String.valueOf(foundStashes.size());
    }

    public enum Mode {
        Chat,
        Toast,
        Both
    }

    public static class EndStashChunk {
        public ChunkPos chunkPos;
        public transient int x, z;
        public int chests, barrels, shulkers, enderChests, furnaces, dispensersDroppers, hoppers, spawners;

        public EndStashChunk(ChunkPos chunkPos) {
            this.chunkPos = chunkPos;
            calculatePos();
        }

        public void calculatePos() {
            x = chunkPos.x * 16 + 8;
            z = chunkPos.z * 16 + 8;
        }

        public int getTotal() {
            return chests + barrels + shulkers + enderChests + furnaces + dispensersDroppers + hoppers + spawners;
        }

        public boolean countsEqual(EndStashChunk c) {
            if (c == null) return false;
            return chests == c.chests && barrels == c.barrels && shulkers == c.shulkers &&
                enderChests == c.enderChests && furnaces == c.furnaces &&
                dispensersDroppers == c.dispensersDroppers && hoppers == c.hoppers &&
                spawners == c.spawners;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EndStashChunk chunk = (EndStashChunk) o;
            return Objects.equals(chunkPos, chunk.chunkPos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(chunkPos);
        }
    }

    private static class EndStashScreen extends WindowScreen {
        private final EndStashChunk chunk;

        public EndStashScreen(GuiTheme theme, EndStashChunk chunk) {
            super(theme, "End Stash at " + chunk.x + ", " + chunk.z);
            this.chunk = chunk;
        }

        @Override
        public void initWidgets() {
            WTable t = add(theme.table()).expandX().widget();


            t.add(theme.label("Total:"));
            t.add(theme.label(chunk.getTotal() + ""));
            t.row();

            t.add(theme.horizontalSeparator()).expandX();
            t.row();

// CHECKS
            t.add(theme.label("Chests:"));
            t.add(theme.label(chunk.chests + ""));
            t.row();

            t.add(theme.label("Barrels:"));
            t.add(theme.label(chunk.barrels + ""));
            t.row();

            t.add(theme.label("Shulkers:"));
            t.add(theme.label(chunk.shulkers + ""));
            t.row();

            t.add(theme.label("Ender Chests:"));
            t.add(theme.label(chunk.enderChests + ""));
            t.row();

            t.add(theme.label("Spawners:"));
            t.add(theme.label(chunk.spawners + ""));
            t.row();

            t.add(theme.label("Furnaces:"));
            t.add(theme.label(chunk.furnaces + ""));
            t.row();

            t.add(theme.label("Dispensers/Droppers:"));
            t.add(theme.label(chunk.dispensersDroppers + ""));
            t.row();

            t.add(theme.label("Hoppers:"));
            t.add(theme.label(chunk.hoppers + ""));
        }
    }
}
