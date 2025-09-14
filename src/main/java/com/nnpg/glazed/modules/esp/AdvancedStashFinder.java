package com.nnpg.glazed.modules.esp;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
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
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.client.toast.SystemToast;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.*;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class AdvancedStashFinder extends Module {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");

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

    private final Setting<Integer> minimumDistance = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-distance")
        .description("The minimum distance you must be from spawn to record a certain chunk.")
        .defaultValue(0)
        .min(0)
        .sliderMax(10000)
        .build()
    );

    private final Setting<Boolean> criticalSpawner = sgGeneral.add(new BoolSetting.Builder()
        .name("critical-spawner")
        .description("Mark chunk as stash even if only a single spawner is found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disconnectOnFind = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect-on-base-find")
        .description("Automatically disconnect from the server when a base is found.")
        .defaultValue(false)
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

    public List<Chunk> chunks = new ArrayList<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public AdvancedStashFinder() {
        super(GlazedAddon.esp, "AdvancedStashFinder", "Advanced stash finder with webhook support and auto-disconnect.");
    }

    @Override
    public void onActivate() {
        load();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        double chunkXAbs = Math.abs(event.chunk().getPos().x * 16);
        double chunkZAbs = Math.abs(event.chunk().getPos().z * 16);
        if (Math.sqrt(chunkXAbs * chunkXAbs + chunkZAbs * chunkZAbs) < minimumDistance.get()) return;

        Chunk chunk = new Chunk(event.chunk().getPos());

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
        }
        else if (chunk.getTotal() >= minimumStorageCount.get()) {
            isStash = true;
            detectionReason = "Storage threshold reached (" + chunk.getTotal() + " blocks)";
        }

        if (isStash) {
            Chunk prevChunk = null;
            int i = chunks.indexOf(chunk);

            if (i < 0) chunks.add(chunk);
            else prevChunk = chunks.set(i, chunk);

            saveJson();
            saveCsv();

            boolean isNewOrUpdated = (!chunk.equals(prevChunk) || !chunk.countsEqual(prevChunk));

            if (sendNotifications.get() && isNewOrUpdated) {
                String stashType = isCriticalSpawner ? "spawner base" : "stash";

                switch (notificationMode.get()) {
                    case Chat -> info("Found %s at (highlight)%s(default), (highlight)%s(default). %s", stashType, chunk.x, chunk.z, detectionReason);
                    case Toast -> {
                          mc.getToastManager().add(new SystemToast(SystemToast.Type.WORLD_BACKUP, net.minecraft.text.Text.of(title), net.minecraft.text.Text.of("Found " + stashType.substring(0, 1).toUpperCase() + stashType.substring(1) + "!")));
                    }
                    case Both -> {
                        info("Found %s at (highlight)%s(default), (highlight)%s(default). %s", stashType, chunk.x, chunk.z, detectionReason);
                        mc.getToastManager().add(new SystemToast(SystemToast.Type.WORLD_BACKUP, net.minecraft.text.Text.of(title), net.minecraft.text.Text.of("Found " + stashType.substring(0, 1).toUpperCase() + stashType.substring(1) + "!")));
                    }
                }
            }

            if (enableWebhook.get() && isNewOrUpdated) {
                sendWebhookNotification(chunk, isCriticalSpawner, detectionReason);
            }

            if (disconnectOnFind.get() && isNewOrUpdated) {
                //info("Base Found - Disconnecting at (highlight)%s(default), (highlight)%s(default)", chunk.x, chunk.z);
                toggle();

                if (mc.world != null) {
                      mc.world.disconnect(net.minecraft.text.Text.of("Disconnected by addon"));
                }
            }
        }
    }

    private void sendWebhookNotification(Chunk chunk, boolean isCriticalSpawner, String detectionReason) {
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

                String stashType = isCriticalSpawner ? "Spawner Base" : "Stash";
                String description = String.format("%s found at coordinates %d, %d!", stashType, chunk.x, chunk.z);

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

                String jsonPayload = String.format(
                    "{\"content\":\"%s\"," +
                        "\"username\":\"Advanced Stashfinder\"," +
                        "\"avatar_url\":\"https://i.imgur.com/OL2y1cr.png\"," +
                        "\"embeds\":[{" +
                        "\"title\":\"📦 Advanced Stashfinder Alert\"," +
                        "\"description\":\"%s\"," +
                        "\"color\":%d," +
                        "\"fields\":[" +
                        "{\"name\":\"Detection Reason\",\"value\":\"%s\",\"inline\":false}," +
                        "{\"name\":\"Total Items Found\",\"value\":\"%d\",\"inline\":false}," +
                        "{\"name\":\"Items Breakdown\",\"value\":\"%s\",\"inline\":false}," +
                        "{\"name\":\"Coordinates\",\"value\":\"%d, %d\",\"inline\":true}," +
                        "{\"name\":\"Server\",\"value\":\"%s\",\"inline\":true}," +
                        "{\"name\":\"Time\",\"value\":\"<t:%d:R>\",\"inline\":true}" +
                        "]," +
                        "\"footer\":{\"text\":\"Advanced Stashfinder\"}" +
                        "}]}",
                    messageContent.replace("\"", "\\\""),
                    description.replace("\"", "\\\""),
                    isCriticalSpawner ? 16711680 : 3066993, // Red for spawner bases, blue for regular stashes
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
        // Sort
        chunks.sort(Comparator.comparingInt(value -> -value.getTotal()));

        WVerticalList list = theme.verticalList();

        // Clear
        WButton clear = list.add(theme.button("Clear")).widget();

        WTable table = new WTable();
        if (!chunks.isEmpty()) list.add(table);

        clear.action = () -> {
            chunks.clear();
            table.clear();
        };

        // Chunks
        fillTable(theme, table);

        return list;
    }

    private void fillTable(GuiTheme theme, WTable table) {
        for (Chunk chunk : chunks) {
            table.add(theme.label("Pos: " + chunk.x + ", " + chunk.z));
            table.add(theme.label("Total: " + chunk.getTotal()));

            WButton open = table.add(theme.button("Open")).widget();
            open.action = () -> mc.setScreen(new ChunkScreen(theme, chunk));

            WButton gotoBtn = table.add(theme.button("Goto")).widget();
            gotoBtn.action = () -> PathManagers.get().moveTo(new BlockPos(chunk.x, 0, chunk.z), true);

            WMinus delete = table.add(theme.minus()).widget();
            delete.action = () -> {
                if (chunks.remove(chunk)) {
                    table.clear();
                    fillTable(theme, table);

                    saveJson();
                    saveCsv();
                }
            };

            table.row();
        }
    }

    private void load() {
        boolean loaded = false;

        // Try to load json
        File file = getJsonFile();
        if (file.exists()) {
            try {
                FileReader reader = new FileReader(file);
                chunks = GSON.fromJson(reader, new TypeToken<List<Chunk>>() {}.getType());
                reader.close();

                for (Chunk chunk : chunks) chunk.calculatePos();

                loaded = true;
            } catch (Exception ignored) {
                if (chunks == null) chunks = new ArrayList<>();
            }
        }

        // Try to load csv
        file = getCsvFile();
        if (!loaded && file.exists()) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                reader.readLine();

                String line;
                while ((line = reader.readLine()) != null) {
                    String[] values = line.split(",");
                    Chunk chunk = new Chunk(new ChunkPos(Integer.parseInt(values[0]), Integer.parseInt(values[1])));

                    chunk.chests = Integer.parseInt(values[2]);
                    chunk.barrels = Integer.parseInt(values[3]);
                    chunk.shulkers = Integer.parseInt(values[4]);
                    chunk.enderChests = Integer.parseInt(values[5]);
                    chunk.furnaces = Integer.parseInt(values[6]);
                    chunk.dispensersDroppers = Integer.parseInt(values[7]);
                    chunk.hoppers = Integer.parseInt(values[8]);
                    if (values.length > 9) {
                        chunk.spawners = Integer.parseInt(values[9]);
                    }

                    chunks.add(chunk);
                }

                reader.close();
            } catch (Exception ignored) {
                if (chunks == null) chunks = new ArrayList<>();
            }
        }
    }

    private void saveCsv() {
        try {
            File file = getCsvFile();
            file.getParentFile().mkdirs();
            Writer writer = new FileWriter(file);

            writer.write("X,Z,Chests,Barrels,Shulkers,EnderChests,Furnaces,DispensersDroppers,Hoppers,Spawners\n");
            for (Chunk chunk : chunks) chunk.write(writer);

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveJson() {
        try {
            File file = getJsonFile();
            file.getParentFile().mkdirs();
            Writer writer = new FileWriter(file);
            GSON.toJson(chunks, writer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File getJsonFile() {
        return new File(new File(new File(MeteorClient.FOLDER, "stashes"), Utils.getFileWorldName()), "advanced-stashes.json");
    }

    private File getCsvFile() {
        return new File(new File(new File(MeteorClient.FOLDER, "stashes"), Utils.getFileWorldName()), "advanced-stashes.csv");
    }

    @Override
    public String getInfoString() {
        return String.valueOf(chunks.size());
    }

    public enum Mode {
        Chat,
        Toast,
        Both
    }

    public static class Chunk {
        private static final StringBuilder sb = new StringBuilder();

        public ChunkPos chunkPos;
        public transient int x, z;
        public int chests, barrels, shulkers, enderChests, furnaces, dispensersDroppers, hoppers, spawners;

        public Chunk(ChunkPos chunkPos) {
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

        public void write(Writer writer) throws IOException {
            sb.setLength(0);
            sb.append(x).append(',').append(z).append(',');
            sb.append(chests).append(',').append(barrels).append(',').append(shulkers).append(',').append(enderChests).append(',').append(furnaces).append(',').append(dispensersDroppers).append(',').append(hoppers).append(',').append(spawners).append('\n');
            writer.write(sb.toString());
        }

        public boolean countsEqual(Chunk c) {
            if (c == null) return false;
            return chests != c.chests || barrels != c.barrels || shulkers != c.shulkers || enderChests != c.enderChests || furnaces != c.furnaces || dispensersDroppers != c.dispensersDroppers || hoppers != c.hoppers || spawners != c.spawners;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Chunk chunk = (Chunk) o;
            return Objects.equals(chunkPos, chunk.chunkPos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(chunkPos);
        }
    }

    private static class ChunkScreen extends WindowScreen {
        private final Chunk chunk;

        public ChunkScreen(GuiTheme theme, Chunk chunk) {
            super(theme, "Chunk at " + chunk.x + ", " + chunk.z);

            this.chunk = chunk;
        }

        @Override
        public void initWidgets() {
            WTable t = add(theme.table()).expandX().widget();

            // Total
            t.add(theme.label("Total:"));
            t.add(theme.label(chunk.getTotal() + ""));
            t.row();

            t.add(theme.horizontalSeparator()).expandX();
            t.row();

            // Separate
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

            t.add(theme.label("Dispensers and droppers:"));
            t.add(theme.label(chunk.dispensersDroppers + ""));
            t.row();

            t.add(theme.label("Hoppers:"));
            t.add(theme.label(chunk.hoppers + ""));
        }
    }
}
