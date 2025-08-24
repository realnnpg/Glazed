package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SpawnerNotifier extends Module {
    private final SettingGroup sg_general = settings.getDefaultGroup();
    private final SettingGroup sg_notifications = settings.createGroup("Notifications");
    private final SettingGroup sg_webhook = settings.createGroup("Webhook");
    private final SettingGroup sg_render = settings.createGroup("Render");

    // General settings
    private final Setting<Boolean> only_new_spawners = sg_general.add(new BoolSetting.Builder()
        .name("only-new-spawners")
        .description("Only notify about newly discovered spawners.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> show_coordinates = sg_general.add(new BoolSetting.Builder()
        .name("show-coordinates")
        .description("Show spawner coordinates in notifications.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> show_distance = sg_general.add(new BoolSetting.Builder()
        .name("show-distance")
        .description("Show distance to spawner in notifications.")
        .defaultValue(true)
        .build()
    );

    // Notification settings
    private final Setting<Mode> notification_mode = sg_notifications.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("The mode to use for notifications.")
        .defaultValue(Mode.Both)
        .build()
    );

    private final Setting<Boolean> disconnect_on_find = sg_notifications.add(new BoolSetting.Builder()
        .name("disconnect-on-find")
        .description("Disconnect when a spawner is found.")
        .defaultValue(true)
        .build()
    );

    // Webhook settings
    private final Setting<Boolean> webhook_enabled = sg_webhook.add(new BoolSetting.Builder()
        .name("webhook-enabled")
        .description("Enable webhook notifications.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhook_url = sg_webhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL.")
        .defaultValue("")
        .visible(() -> webhook_enabled.get())
        .build()
    );

    private final Setting<Boolean> self_ping = sg_webhook.add(new BoolSetting.Builder()
        .name("self-ping")
        .description("Ping yourself in the webhook message")
        .defaultValue(false)
        .visible(() -> webhook_enabled.get())
        .build()
    );

    private final Setting<String> discord_id = sg_webhook.add(new StringSetting.Builder()
        .name("discord-id")
        .description("Your Discord user ID for pinging")
        .defaultValue("")
        .visible(() -> webhook_enabled.get() && self_ping.get())
        .build()
    );

    // Render settings
    private final Setting<Boolean> show_esp = sg_render.add(new BoolSetting.Builder()
        .name("show-esp")
        .description("Highlight spawners with ESP boxes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> esp_color = sg_render.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Color for the ESP boxes")
        .defaultValue(new SettingColor(255, 0, 0, 100))
        .visible(() -> show_esp.get())
        .build()
    );

    private final Setting<ShapeMode> shape_mode = sg_render.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Rendering mode for the ESP boxes")
        .defaultValue(ShapeMode.Both)
        .visible(() -> show_esp.get())
        .build()
    );

    private final Setting<Boolean> show_tracers = sg_render.add(new BoolSetting.Builder()
        .name("show-tracers")
        .description("Draw tracer lines to spawners")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> tracer_color = sg_render.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color for the tracer lines")
        .defaultValue(new SettingColor(255, 0, 0, 200))
        .visible(() -> show_tracers.get())
        .build()
    );

    private final Setting<Boolean> only_render_new = sg_render.add(new BoolSetting.Builder()
        .name("only-render-new")
        .description("Only render ESP/tracers for newly discovered spawners")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> render_distance = sg_render.add(new DoubleSetting.Builder()
        .name("render-distance")
        .description("Maximum distance to render ESP/tracers (0 = unlimited)")
        .defaultValue(0.0)
        .min(0.0)
        .max(500.0)
        .sliderMax(200.0)
        .build()
    );

    private final Set<BlockPos> detected_spawners = new HashSet<>();
    private final Set<BlockPos> new_spawners = new HashSet<>();
    private final HttpClient http_client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private int tick_counter = 0;

    public SpawnerNotifier() {
        super(GlazedAddon.esp, "Spawner Notifier", "Notifies when spawners are detected with multiple notification options and visual ESP");
    }

    @Override
    public void onActivate() {
        detected_spawners.clear();
        new_spawners.clear();
        tick_counter = 0;
    }

    @Override
    public void onDeactivate() {
        info("SpawnerNotifier deactivated. Found %d spawners this session.", detected_spawners.size());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        tick_counter++;
        if (tick_counter % 2 != 0) return;

        scan_all_loaded_chunks();
    }

    @EventHandler
    private void on_chunk_data(ChunkDataEvent event) {
        if (mc.player == null || mc.world == null) return;
        scan_chunk(event.chunk());
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || (!show_esp.get() && !show_tracers.get())) return;

        Vec3d playerPos = mc.player.getLerpedPos(event.tickDelta);
        Color sideColor = new Color(esp_color.get());
        Color lineColor = new Color(esp_color.get());
        Color tracerColorValue = new Color(tracer_color.get());

        double maxDistance = render_distance.get();

        for (BlockPos pos : detected_spawners) {
            // Check if we should only render new spawners
            if (only_render_new.get() && !new_spawners.contains(pos)) {
                continue;
            }

            // Check render distance
            if (maxDistance > 0) {
                double distance = playerPos.distanceTo(Vec3d.ofCenter(pos));
                if (distance > maxDistance) {
                    continue;
                }
            }

            // Render ESP box
            if (show_esp.get()) {
                event.renderer.box(pos, sideColor, lineColor, shape_mode.get(), 0);
            }

            // Render tracers
            if (show_tracers.get()) {
                Vec3d blockCenter = Vec3d.ofCenter(pos);

                Vec3d startPos;
                if (mc.options.getPerspective().isFirstPerson()) {
                    Vec3d lookDirection = mc.player.getRotationVector();
                    startPos = new Vec3d(
                        playerPos.x + lookDirection.x * 0.5,
                        playerPos.y + mc.player.getEyeHeight(mc.player.getPose()) + lookDirection.y * 0.5,
                        playerPos.z + lookDirection.z * 0.5
                    );
                } else {
                    startPos = new Vec3d(
                        playerPos.x,
                        playerPos.y + mc.player.getEyeHeight(mc.player.getPose()),
                        playerPos.z
                    );
                }

                event.renderer.line(startPos.x, startPos.y, startPos.z,
                    blockCenter.x, blockCenter.y, blockCenter.z, tracerColorValue);
            }
        }
    }

    private void scan_all_loaded_chunks() {
        if (mc.world == null || mc.player == null) return;

        int centerX = mc.player.getChunkPos().x;
        int centerZ = mc.player.getChunkPos().z;
        int radius = 1600;

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                WorldChunk chunk = mc.world.getChunk(x, z);
                if (chunk != null && !chunk.isEmpty()) {
                    scan_chunk(chunk);
                }
            }
        }
    }

    private void scan_chunk(WorldChunk chunk) {
        List<BlockPos> chunk_spawners = new ArrayList<>();

        for (BlockEntity block_entity : chunk.getBlockEntities().values()) {
            if (block_entity instanceof MobSpawnerBlockEntity) {
                BlockPos pos = block_entity.getPos();
                chunk_spawners.add(pos);
            }
        }

        if (!chunk_spawners.isEmpty()) {
            for (BlockPos spawner_pos : chunk_spawners) {
                BlockEntity block_entity = mc.world.getBlockEntity(spawner_pos);
                if (block_entity instanceof MobSpawnerBlockEntity spawner) {
                    handle_spawner_found(spawner_pos, spawner);
                }
            }
        }
    }

    private void handle_spawner_found(BlockPos pos, MobSpawnerBlockEntity spawner) {
        boolean is_new_spawner = !detected_spawners.contains(pos);

        if (only_new_spawners.get() && !is_new_spawner) {
            return;
        }

        if (is_new_spawner) {
            detected_spawners.add(pos);
            new_spawners.add(pos);
        }

        double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(pos));
        String message = build_notification_message(pos, distance, is_new_spawner);

        switch (notification_mode.get()) {
            case Chat -> info(message);
            case Toast -> show_toast_notification(message);
            case Both -> {
                info(message);
                show_toast_notification(message);
            }
        }

        if (webhook_enabled.get() && !webhook_url.get().trim().isEmpty()) {
            send_webhook_notification(message, pos, distance, is_new_spawner);
        }

        if (disconnect_on_find.get() && is_new_spawner) {
            handle_disconnection(pos);
        }
    }

    private String build_notification_message(BlockPos pos, double distance, boolean is_new) {
        StringBuilder msg = new StringBuilder();

        if (is_new) {
            msg.append("New spawner found");
        } else {
            msg.append("Spawner detected");
        }

        if (show_coordinates.get()) {
            msg.append(" at ").append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ());
        }

        if (show_distance.get()) {
            msg.append(" (").append(String.format("%.1f", distance)).append("m away)");
        }

        return msg.toString();
    }

    private void show_toast_notification(String message) {
        try {
            MeteorToast toast = new MeteorToast(Items.SPAWNER, title, message);
            mc.getToastManager().add(toast);
        } catch (Exception e) {
            info(message);
        }
    }

    private void send_webhook_notification(String message, BlockPos pos, double distance, boolean is_new) {
        String url = webhook_url.get().trim();
        if (url.isEmpty()) {
            warning("Webhook URL not configured!");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String server_info = mc.getCurrentServerEntry() != null ?
                    mc.getCurrentServerEntry().address : "Unknown Server";

                String message_content = "";
                if (self_ping.get() && !discord_id.get().trim().isEmpty()) {
                    message_content = String.format("<@%s>", discord_id.get().trim());
                }

                String description = String.format("Spawner found at coordinates %d, %d, %d!", pos.getX(), pos.getY(), pos.getZ());

                String json_payload = String.format(
                    "{\"content\":\"%s\"," +
                        "\"username\":\"Spawner Notifier\"," +
                        "\"avatar_url\":\"https://i.imgur.com/OL2y1cr.png\"," +
                        "\"embeds\":[{" +
                        "\"title\":\"🔥 Spawner Alert\"," +
                        "\"description\":\"%s\"," +
                        "\"color\":%d," +
                        "\"fields\":[" +
                        "{\"name\":\"Coordinates\",\"value\":\"%d, %d, %d\",\"inline\":true}," +
                        "{\"name\":\"Distance\",\"value\":\"%.1fm\",\"inline\":true}," +
                        "{\"name\":\"Server\",\"value\":\"%s\",\"inline\":true}," +
                        "{\"name\":\"Time\",\"value\":\"<t:%d:R>\",\"inline\":true}" +
                        "]," +
                        "\"footer\":{\"text\":\"Spawner Notifier\"}" +
                        "}]}",
                    message_content.replace("\"", "\\\""),
                    description.replace("\"", "\\\""),
                    is_new ? 15158332 : 3447003,
                    pos.getX(), pos.getY(), pos.getZ(),
                    distance,
                    server_info.replace("\"", "\\\""),
                    System.currentTimeMillis() / 1000
                );

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json_payload))
                    .timeout(Duration.ofSeconds(30))
                    .build();

                HttpResponse<String> response = http_client.send(request,
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

    private void handle_disconnection(BlockPos pos) {
        info("SPAWNER FOUND! Disconnecting and disabling module...");
        toggle();

        if (mc.player != null) {
            mc.player.networkHandler.onDisconnect(
                new DisconnectS2CPacket(Text.literal("SPAWNER FOUND AT " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "!"))
            );
        }
    }

    @Override
    public String getInfoString() {
        return String.valueOf(detected_spawners.size());
    }

    public enum Mode {
        Chat,
        Toast,
        Both
    }

    public int get_detected_spawner_count() {
        return detected_spawners.size();
    }

    public Set<BlockPos> get_detected_spawners() {
        return new HashSet<>(detected_spawners);
    }

    public Set<BlockPos> get_new_spawners() {
        return new HashSet<>(new_spawners);
    }
}
