package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RTPer extends Module {

    public enum RTPMode {
        COORDINATES("Coordinates"),
        BIOME("Biome");

        private final String displayName;

        RTPMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum RTPRegion {
        ASIA("asia"),
        EAST("east"),
        EU_CENTRAL("eu central"),
        EU_WEST("eu west"),
        OCEANIA("oceania"),
        WEST("west"),
        NETHER("nether"),
        END("end");

        private final String commandPart;

        RTPRegion(String commandPart) {
            this.commandPart = commandPart;
        }

        public String getCommandPart() {
            return commandPart;
        }
    }

    public enum MinecraftBiome {

        PLAINS("Plains", "minecraft:plains"),
        SUNFLOWER_PLAINS("Sunflower Plains", "minecraft:sunflower_plains"),
        SNOWY_PLAINS("Snowy Plains", "minecraft:snowy_plains"),

        FOREST("Forest", "minecraft:forest"),
        FLOWER_FOREST("Flower Forest", "minecraft:flower_forest"),
        BIRCH_FOREST("Birch Forest", "minecraft:birch_forest"),
        OLD_GROWTH_BIRCH_FOREST("Old Growth Birch Forest", "minecraft:old_growth_birch_forest"),
        DARK_FOREST("Dark Forest", "minecraft:dark_forest"),
        OAK_AND_BIRCH_FOREST("Oak and Birch Forest", "minecraft:oak_and_birch_forest"),

        TAIGA("Taiga", "minecraft:taiga"),
        SNOWY_TAIGA("Snowy Taiga", "minecraft:snowy_taiga"),
        OLD_GROWTH_SPRUCE_TAIGA("Old Growth Spruce Taiga", "minecraft:old_growth_spruce_taiga"),
        OLD_GROWTH_PINE_TAIGA("Old Growth Pine Taiga", "minecraft:old_growth_pine_taiga"),

        JUNGLE("Jungle", "minecraft:jungle"),
        SPARSE_JUNGLE("Sparse Jungle", "minecraft:sparse_jungle"),
        BAMBOO_JUNGLE("Bamboo Jungle", "minecraft:bamboo_jungle"),

        DESERT("Desert", "minecraft:desert"),

        SAVANNA("Savanna", "minecraft:savanna"),
        SAVANNA_PLATEAU("Savanna Plateau", "minecraft:savanna_plateau"),
        WINDSWEPT_SAVANNA("Windswept Savanna", "minecraft:windswept_savanna"),

        BADLANDS("Badlands", "minecraft:badlands"),
        ERODED_BADLANDS("Eroded Badlands", "minecraft:eroded_badlands"),
        WOODED_BADLANDS("Wooded Badlands", "minecraft:wooded_badlands"),

        SWAMP("Swamp", "minecraft:swamp"),
        MANGROVE_SWAMP("Mangrove Swamp", "minecraft:mangrove_swamp"),

        BEACH("Beach", "minecraft:beach"),
        SNOWY_SLOPES("Snowy Slopes", "minecraft:snowy_slopes"),
        JAGGED_PEAKS("Jagged Peaks", "minecraft:jagged_peaks"),
        FROZEN_PEAKS("Frozen Peaks", "minecraft:frozen_peaks"),
        STONY_PEAKS("Stony Peaks", "minecraft:stony_peaks"),

        OCEAN("Ocean", "minecraft:ocean"),
        WARM_OCEAN("Warm Ocean", "minecraft:warm_ocean"),
        LUKEWARM_OCEAN("Lukewarm Ocean", "minecraft:lukewarm_ocean"),
        COLD_OCEAN("Cold Ocean", "minecraft:cold_ocean"),
        FROZEN_OCEAN("Frozen Ocean", "minecraft:frozen_ocean"),
        DEEP_OCEAN("Deep Ocean", "minecraft:deep_ocean"),
        DEEP_WARM_OCEAN("Deep Warm Ocean", "minecraft:deep_warm_ocean"),
        DEEP_LUKEWARM_OCEAN("Deep Lukewarm Ocean", "minecraft:deep_lukewarm_ocean"),
        DEEP_COLD_OCEAN("Deep Cold Ocean", "minecraft:deep_cold_ocean"),
        DEEP_FROZEN_OCEAN("Deep Frozen Ocean", "minecraft:deep_frozen_ocean"),

        RIVER("River", "minecraft:river"),
        FROZEN_RIVER("Frozen River", "minecraft:frozen_river"),

        MUSHROOM_FIELDS("Mushroom Fields", "minecraft:mushroom_fields"),

        DRIPSTONE_CAVES("Dripstone Caves", "minecraft:dripstone_caves"),

        LUSH_CAVES("Lush Caves", "minecraft:lush_caves"),

        DEEP_DARK("Deep Dark", "minecraft:deep_dark"),

        NETHER_WASTES("Nether Wastes", "minecraft:nether_wastes"),
        SOUL_SAND_VALLEY("Soul Sand Valley", "minecraft:soul_sand_valley"),
        CRIMSON_FOREST("Crimson Forest", "minecraft:crimson_forest"),
        WARPED_FOREST("Warped Forest", "minecraft:warped_forest"),
        BASALT_DELTAS("Basalt Deltas", "minecraft:basalt_deltas"),

        THE_END("The End", "minecraft:the_end"),
        END_HIGHLANDS("End Highlands", "minecraft:end_highlands"),
        END_MIDLANDS("End Midlands", "minecraft:end_midlands"),
        SMALL_END_ISLANDS("Small End Islands", "minecraft:small_end_islands"),
        END_BARRENS("End Barrens", "minecraft:end_barrens"),

        CAVES("Caves", "minecraft:caves"),

        GROVE("Grove", "minecraft:grove"),

        MEADOW("Meadow", "minecraft:meadow"),

        CHERRY_GROVE("Cherry Grove", "minecraft:cherry_grove");

        private final String displayName;
        private final String id;

        MinecraftBiome(String displayName, String id) {
            this.displayName = displayName;
            this.id = id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getId() {
            return id;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCoordinates = settings.createGroup("Coordinates");
    private final SettingGroup sgBiome = settings.createGroup("Biome");
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");

    private final Setting<RTPMode> rtpMode = sgGeneral.add(new EnumSetting.Builder<RTPMode>()
        .name("rtp-mode")
        .description("RTP mode: Coordinates or Biome.")
        .defaultValue(RTPMode.COORDINATES)
        .build()
    );

    private final Setting<Integer> targetX = sgCoordinates.add(new IntSetting.Builder()
        .name("target-x")
        .description("Target X coordinate.")
        .defaultValue(0)
        .visible(() -> rtpMode.get() == RTPMode.COORDINATES)
        .build()
    );

    private final Setting<Integer> targetZ = sgCoordinates.add(new IntSetting.Builder()
        .name("target-z")
        .description("Target Z coordinate.")
        .defaultValue(0)
        .visible(() -> rtpMode.get() == RTPMode.COORDINATES)
        .build()
    );

    private final Setting<String> distance = sgCoordinates.add(new StringSetting.Builder()
        .name("distance")
        .description("Distance to get within (supports k/m, e.g., 10k = 10000, 1.5m = 1500000).")
        .defaultValue("1000")
        .visible(() -> rtpMode.get() == RTPMode.COORDINATES)
        .build()
    );

    private final Setting<MinecraftBiome> targetBiome = sgBiome.add(new EnumSetting.Builder<MinecraftBiome>()
        .name("target-biome")
        .description("Target biome to find.")
        .defaultValue(MinecraftBiome.PLAINS)
        .visible(() -> false)
        .build()
    );

    private final Setting<RTPRegion> rtpRegion = sgGeneral.add(new EnumSetting.Builder<RTPRegion>()
        .name("rtp-region")
        .description("RTP region to use.")
        .defaultValue(RTPRegion.WEST)
        .build()
    );

    private final Setting<Boolean> disconnectOnReach = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect-on-reach")
        .description("Disconnect when reaching the target coordinates or finding the target biome.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> rtpDelay = sgGeneral.add(new IntSetting.Builder()
        .name("rtp-delay")
        .description("Delay between RTP attempts in seconds.")
        .defaultValue(15)
        .min(11)
        .max(20)
        .sliderMin(11)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> webhookEnabled = sgWebhook.add(new BoolSetting.Builder()
        .name("webhook-enabled")
        .description("Enable webhook notifications.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL.")
        .defaultValue("")
        .visible(webhookEnabled::get)
        .build()
    );

    private final Setting<Boolean> selfPing = sgWebhook.add(new BoolSetting.Builder()
        .name("self-ping")
        .description("Ping yourself in the webhook message.")
        .defaultValue(false)
        .visible(webhookEnabled::get)
        .build()
    );

    private final Setting<String> discordId = sgWebhook.add(new StringSetting.Builder()
        .name("discord-id")
        .description("Your Discord user ID for pinging.")
        .defaultValue("")
        .visible(() -> webhookEnabled.get() && selfPing.get())
        .build()
    );

    private int tickTimer = 0;
    private boolean isRtping = false;
    private int rtpAttempts = 0;
    private BlockPos lastRtpPos = null;
    private double lastReportedDistance = -1;
    private int targetDistanceBlocks = 1000;
    private boolean biomeFound = false;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public RTPer() {
        super(GlazedAddon.CATEGORY, "RTPer", "RTP to specific coordinates or find specific biomes.");
    }

    @Override
    public void onActivate() {
        tickTimer = 0;
        isRtping = false;
        rtpAttempts = 0;
        lastRtpPos = null;
        lastReportedDistance = -1;
        biomeFound = false;

        if (rtpMode.get() == RTPMode.COORDINATES) {
            targetDistanceBlocks = parseDistance();
        }

        if (mc.player == null) return;

        if (rtpMode.get() == RTPMode.COORDINATES) {
            double currentDist = getCurrentDistance();
            info("RTPer started - target: (%d, %d)", targetX.get(), targetZ.get());
            info("Distance: %s -> %d blocks", distance.get(), targetDistanceBlocks);
            info("Current: %.1f blocks away", currentDist);

            if (currentDist <= targetDistanceBlocks) {
                info("Already close enough!");
                toggle();
            }
        } else {
            info("RTPer started - Biome Finder mode");
            info("Target biome: %s", targetBiome.get().getDisplayName());
        }
        if (rtpMode.get() == RTPMode.COORDINATES &&
            (rtpRegion.get() == RTPRegion.NETHER || rtpRegion.get() == RTPRegion.END)) {
            warning("Using %s region with coordinate mode - make sure your coordinates are valid for this dimension!",
                rtpRegion.get().getCommandPart());
        }
    }

    @Override
    public void onDeactivate() {
        if (rtpMode.get() == RTPMode.COORDINATES) {
            info("Stopped after %d attempts", rtpAttempts);
        } else {
            info("Biome finder stopped after %d attempts", rtpAttempts);
        }
        isRtping = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (rtpMode.get() == RTPMode.COORDINATES) {
            handleCoordinatesMode();
        } else {
            handleBiomeMode();
        }

        tickTimer++;

        if (isRtping && tickTimer >= rtpDelay.get() * 20) {
            isRtping = false;
        }

        if (tickTimer >= rtpDelay.get() * 20 && !isRtping) {
            performRTP();
            tickTimer = 0;
        }
    }

    private void handleCoordinatesMode() {
        double currentDistance = getCurrentDistance();

        if (isNearTarget(currentDistance)) {
            info("Done! %.1f blocks away (target: %d)", currentDistance, targetDistanceBlocks);

            if (webhookEnabled.get()) {
                sendWebhook("Target Reached!",
                    String.format("Got to %d, %d in %s\\nDistance: %.1f/%d blocks\\nAttempts: %d",
                        targetX.get(), targetZ.get(), rtpRegion.get().getCommandPart(),
                        currentDistance, targetDistanceBlocks, rtpAttempts),
                    0x00FF00);
            }

            if (disconnectOnReach.get()) {
                info("Disconnecting...");
                if (mc.world != null) {
                      mc.world.disconnect(net.minecraft.text.Text.of("Disconnected by addon"));
                }
            }

            toggle();
            return;
        }

        if (tickTimer % 100 == 0 && Math.abs(currentDistance - lastReportedDistance) > 100) {
            info("Distance: %.1f blocks", currentDistance);
            lastReportedDistance = currentDistance;
        }
    }

    private void handleBiomeMode() {
        if (biomeFound) {
            info("Target biome found: %s", targetBiome.get().getDisplayName());

            if (webhookEnabled.get()) {
                sendWebhook("Biome Found!",
                    String.format("Found %s biome in %s!\\nAttempts: %d\\nPosition: %d, %d, %d",
                        targetBiome.get().getDisplayName(), rtpRegion.get().getCommandPart(), rtpAttempts,
                        mc.player.getBlockPos().getX(), mc.player.getBlockPos().getY(), mc.player.getBlockPos().getZ()),
                    0x00FF00);
            }

            if (disconnectOnReach.get()) {
                info("Disconnecting...");
                disconnectWithMessage("Glazed: found requested biome");
            }

            toggle();
            return;
        }

        if (isInTargetBiome()) {
            biomeFound = true;
            return;
        }

        if (tickTimer % 100 == 0) {
            String currentBiome = getCurrentBiome();
            info("Current biome: %s", currentBiome);
        }
    }

    private boolean isInTargetBiome() {
        if (mc.world == null || mc.player == null) return false;

        BlockPos pos = mc.player.getBlockPos();
        String biomeId = getBiomeIdAt(pos);
        if (biomeId == null) return false;
        return biomeId.equals(targetBiome.get().getId());
    }

    private String getCurrentBiome() {
        if (mc.world == null || mc.player == null) return "Unknown";

        BlockPos pos = mc.player.getBlockPos();
        String biomeId = getBiomeIdAt(pos);
        if (biomeId == null) return "Unknown";

        for (MinecraftBiome minecraftBiome : MinecraftBiome.values()) {
            if (minecraftBiome.getId().equals(biomeId)) return minecraftBiome.getDisplayName();
        }
        return toDisplayName(biomeId);
    }

    private String getBiomeIdAt(BlockPos pos) {
        if (mc.world == null) return null;
        Biome biome = mc.world.getBiome(pos).value();
        if (biome == null) return null;
        Identifier id = mc.world.getRegistryManager().getOrThrow(RegistryKeys.BIOME).getId(biome);
        return id != null ? id.toString() : null;
    }

    private String toDisplayName(String id) {
        if (id == null || id.isEmpty()) return "Unknown";
        String raw = id.contains(":") ? id.substring(id.indexOf(":") + 1) : id;
        String[] parts = raw.split("_");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            b.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) b.append(p.substring(1));
            if (i < parts.length - 1) b.append(' ');
        }
        return b.toString();
    }

    private void disconnectWithMessage(String message) {
        try {
            if (mc != null) {
                if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
                    mc.getNetworkHandler().getConnection().disconnect(Text.literal(message));
                    return;
                }
                if (mc.player != null && mc.player.networkHandler != null && mc.player.networkHandler.getConnection() != null) {
                    mc.player.networkHandler.getConnection().disconnect(Text.literal(message));
                    return;
                }
                if (mc.world != null) {
                      mc.world.disconnect(net.minecraft.text.Text.of("Disconnected by addon"));
                }
            }
        } catch (Exception ignored) {
              if (mc != null && mc.world != null) mc.world.disconnect(net.minecraft.text.Text.of("Disconnected by addon"));
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket && mc.player != null) {
            isRtping = false;
            BlockPos currentPos = mc.player.getBlockPos();

            if (lastRtpPos == null || !currentPos.equals(lastRtpPos)) {
                rtpAttempts++;
                lastRtpPos = currentPos;

                if (rtpMode.get() == RTPMode.COORDINATES) {
                    double distance = getCurrentDistance();
                    info("RTP %d done - pos: (%d, %d, %d) dist: %.1f",
                        rtpAttempts, currentPos.getX(), currentPos.getY(), currentPos.getZ(), distance);

                    if (lastReportedDistance > 0) {
                        double diff = lastReportedDistance - distance;
                        if (diff > 0) {
                            info("Better by %.1f blocks", diff);
                        } else if (diff < -1000) {
                            info("Worse by %.1f blocks", Math.abs(diff));
                        }
                    }
                    lastReportedDistance = distance;
                } else {
                    String biome = getCurrentBiome();
                    info("RTP %d done - pos: (%d, %d, %d) biome: %s",
                        rtpAttempts, currentPos.getX(), currentPos.getY(), currentPos.getZ(), biome);

                    if (isInTargetBiome()) {
                        biomeFound = true;
                    }
                }
            }
        }
    }

    private void performRTP() {
        if (mc.player == null) return;

        isRtping = true;

        ChatUtils.sendPlayerMsg("/rtp " + rtpRegion.get().getCommandPart());

        if (rtpMode.get() == RTPMode.COORDINATES) {
            double currentDistance = getCurrentDistance();
            info("Attempting RTP (%s) - current: %.1f blocks",
                rtpRegion.get().getCommandPart(), currentDistance);
        } else {
            info("Attempting RTP (%s) - searching for %s biome",
                rtpRegion.get().getCommandPart(), targetBiome.get().getDisplayName());
        }
    }

    private boolean isNearTarget() {
        return isNearTarget(getCurrentDistance());
    }

    private boolean isNearTarget(double currentDistance) {
        return currentDistance <= targetDistanceBlocks;
    }

    private double getCurrentDistance() {
        if (mc.player == null) return Double.MAX_VALUE;

        BlockPos pos = mc.player.getBlockPos();
        double dx = pos.getX() - targetX.get();
        double dz = pos.getZ() - targetZ.get();

        return Math.sqrt(dx * dx + dz * dz);
    }

    private int parseDistance() {
        String dist = distance.get().toLowerCase().trim();

        if (dist.isEmpty()) {
            error("Empty distance, using 1000");
            return 1000;
        }

        try {
            if (dist.endsWith("k")) {
                String num = dist.substring(0, dist.length() - 1).trim();
                if (num.isEmpty()) {
                    error("Bad format: '%s', using 1000", dist);
                    return 1000;
                }
                double val = Double.parseDouble(num);
                return (int) (val * 1000);
            } else if (dist.endsWith("m")) {
                String num = dist.substring(0, dist.length() - 1).trim();
                if (num.isEmpty()) {
                    error("Bad format: '%s', using 1000", dist);
                    return 1000;
                }
                double val = Double.parseDouble(num);
                return (int) (val * 1000000);
            } else {
                return Integer.parseInt(dist);
            }
        } catch (NumberFormatException e) {
            error("Can't parse '%s': %s, using 1000", dist, e.getMessage());
            return 1000;
        }
    }

    private void sendWebhook(String title, String description, int color) {
        if (!webhookEnabled.get() || webhookUrl.get().isEmpty()) return;

        CompletableFuture.runAsync(() -> {
            try {
                String serverInfo = mc.getCurrentServerEntry() != null ?
                    mc.getCurrentServerEntry().address : "Unknown Server";

                String messageContent = "";
                if (selfPing.get() && !discordId.get().trim().isEmpty()) {
                    messageContent = String.format("<@%s>", discordId.get().trim());
                }

                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

                String jsonPayload = String.format("""
                    {
                        "content": "%s",
                        "username": "RTPer Webhook",
                        "avatar_url": "https://i.imgur.com/OL2y1cr.png",
                        "embeds": [{
                            "title": "🎯 RTPer Alert",
                            "description": "%s",
                            "color": %d,
                            "fields": [
                                {
                                    "name": "Status",
                                    "value": "%s",
                                    "inline": true
                                },
                                {
                                    "name": "Server",
                                    "value": "%s",
                                    "inline": true
                                },
                                {
                                    "name": "Time",
                                    "value": "<t:%d:R>",
                                    "inline": true
                                }
                            ],
                            "footer": {
                                "text": "RTPer by Glazed"
                            },
                            "timestamp": "%sZ"
                        }]
                    }""",
                    messageContent.replace("\"", "\\\""),
                    description.replace("\"", "\\\"").replace("\\n", "\\n"),
                    color,
                    title.replace("\"", "\\\""),
                    serverInfo.replace("\"", "\\\""),
                    System.currentTimeMillis() / 1000,
                    timestamp);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl.get()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(30))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 204) {
                    info("Webhook sent successfully");
                } else {
                    error("Webhook failed with status: %d", response.statusCode());
                }
            } catch (IOException | InterruptedException e) {
                error("Webhook error: %s", e.getMessage());
            }
        });
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();

        table.add(theme.label("Biome Picker:"));
        WLabel current = table.add(theme.label(targetBiome.get().getDisplayName())).expandX().widget();
        WButton open = table.add(theme.button("Select")).widget();
        open.action = () -> {
            if (rtpMode.get() == RTPMode.BIOME) mc.setScreen(new BiomePickerScreen(theme, current));
        };
        table.row();

        return table;
    }

    private class BiomePickerScreen extends WindowScreen {
        private WTable listTable;
        private WTextBox searchBox;
        private final WLabel currentLabel;

        public BiomePickerScreen(GuiTheme theme, WLabel currentLabel) {
            super(theme, "Select Biome");
            this.currentLabel = currentLabel;
        }

        @Override
        public void initWidgets() {
            searchBox = add(theme.textBox("")).expandX().widget();
            searchBox.setFocused(true);
            searchBox.action = this::reloadList;

            add(theme.horizontalSeparator()).expandX();

            listTable = add(theme.table()).expandX().widget();
            reloadList();
        }

        private void reloadList() {
            listTable.clear();
            String query = searchBox.get().trim().toLowerCase();

            for (MinecraftBiome biome : MinecraftBiome.values()) {
                String name = biome.getDisplayName();
                if (!query.isEmpty() && !name.toLowerCase().contains(query)) continue;

                listTable.add(theme.label(name)).expandX();
                WButton select = listTable.add(theme.button("Use")).widget();
                select.action = () -> {
                    targetBiome.set(biome);
                    if (currentLabel != null) currentLabel.set(biome.getDisplayName());
                    mc.setScreen(null);
                };
                listTable.row();
            }
        }
    }
}
