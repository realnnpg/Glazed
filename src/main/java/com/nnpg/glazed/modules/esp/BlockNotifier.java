package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.toast.SystemToast;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
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

public class BlockNotifier extends Module {
    private final SettingGroup sg_general = settings.getDefaultGroup();
    private final SettingGroup sg_notifications = settings.createGroup("Notifications");
    private final SettingGroup sg_webhook = settings.createGroup("Webhook");
    private final SettingGroup sg_render = settings.createGroup("Render");

    private final Setting<List<Block>> blocks_to_find = sg_general.add(new BlockListSetting.Builder()
        .name("blocks-to-find")
        .description("Blocks to notify when found.")
        .defaultValue(Collections.emptyList())
        .build()
    );

    private final Setting<Mode> notification_mode = sg_notifications.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("The mode to use for notifications.")
        .defaultValue(Mode.Both)
        .build()
    );

    private final Setting<Boolean> disconnect_on_find = sg_notifications.add(new BoolSetting.Builder()
        .name("disconnect-on-find")
        .description("Disconnect when a block is found.")
        .defaultValue(false)
        .build()
    );

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
        .description("Ping yourself in the webhook message.")
        .defaultValue(false)
        .visible(() -> webhook_enabled.get())
        .build()
    );

    private final Setting<String> discord_id = sg_webhook.add(new StringSetting.Builder()
        .name("discord-id")
        .description("Your Discord user ID for pinging.")
        .defaultValue("")
        .visible(() -> webhook_enabled.get() && self_ping.get())
        .build()
    );

    private final Setting<Boolean> show_esp = sg_render.add(new BoolSetting.Builder()
        .name("show-esp")
        .description("Highlight found blocks with ESP boxes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> esp_color = sg_render.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Color for the ESP boxes")
        .defaultValue(new SettingColor(255, 165, 0, 100))
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
        .description("Draw tracer lines to found blocks")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> tracer_color = sg_render.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color for the tracer lines")
        .defaultValue(new SettingColor(255, 165, 0, 200))
        .visible(() -> show_tracers.get())
        .build()
    );

    private final Setting<Boolean> only_render_new = sg_render.add(new BoolSetting.Builder()
        .name("only-render-new")
        .description("Only render ESP/tracers for newly discovered blocks")
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



    private final Set<ChunkPos> processed_chunks = new HashSet<>();
    private final Set<BlockPos> found_block_positions = new HashSet<>();
    private final Set<BlockPos> new_found_blocks = new HashSet<>();
    private final Map<BlockPos, Block> block_type_map = new HashMap<>();
    private final HttpClient http_client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private int total_blocks_found = 0;

    public BlockNotifier() {
        super(GlazedAddon.esp, "Block Notifier", "Notifies when specific blocks are detected with multiple notification options and visual ESP.");
    }

    @Override
    public void onActivate() {
        processed_chunks.clear();
        found_block_positions.clear();
        new_found_blocks.clear();
        block_type_map.clear();
        total_blocks_found = 0;
        info("BlockNotifier activated!");
    }

    @Override
    public void onDeactivate() {
        info("BlockNotifier deactivated. Found %d blocks total this session.", total_blocks_found);
    }

    @EventHandler
    private void on_chunk_data(ChunkDataEvent event) {
        if (mc.player == null || mc.world == null) return;

        ChunkPos chunk_pos = event.chunk().getPos();
        if (processed_chunks.contains(chunk_pos)) return;

        Map<Block, Integer> found_blocks = new HashMap<>();
        Map<Block, List<BlockPos>> block_positions = new HashMap<>();
        boolean has_target_blocks = false;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = mc.world.getBottomY(); y < mc.world.getHeight(); y++) {
                    BlockPos pos = new BlockPos(chunk_pos.getStartX() + x, y, chunk_pos.getStartZ() + z);
                    Block block = mc.world.getBlockState(pos).getBlock();

                    if (blocks_to_find.get().contains(block)) {
                        found_blocks.put(block, found_blocks.getOrDefault(block, 0) + 1);
                        block_positions.computeIfAbsent(block, k -> new ArrayList<>()).add(pos);

                        if (!found_block_positions.contains(pos)) {
                            found_block_positions.add(pos);
                            new_found_blocks.add(pos);
                            block_type_map.put(pos, block);
                        }

                        has_target_blocks = true;
                        total_blocks_found++;
                    }
                }
            }
        }

        if (has_target_blocks) {
            processed_chunks.add(chunk_pos);
            handle_blocks_found(chunk_pos, found_blocks);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || (!show_esp.get() && !show_tracers.get())) return;

        Vec3d playerPos = mc.player.getLerpedPos(event.tickDelta);
        Color sideColor = new Color(esp_color.get());
        Color lineColor = new Color(esp_color.get());
        Color tracerColorValue = new Color(tracer_color.get());

        double maxDistance = render_distance.get();

        for (BlockPos pos : found_block_positions) {
            if (only_render_new.get() && !new_found_blocks.contains(pos)) {
                continue;
            }

            if (maxDistance > 0) {
                double distance = playerPos.distanceTo(Vec3d.ofCenter(pos));
                if (distance > maxDistance) {
                    continue;
                }
            }

            if (show_esp.get()) {
                event.renderer.box(pos, sideColor, lineColor, shape_mode.get(), 0);
            }

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

    private void handle_blocks_found(ChunkPos chunk_pos, Map<Block, Integer> found_blocks) {
        String message = build_notification_message(chunk_pos, found_blocks);
        String detection_reason = build_detection_reason(found_blocks);

        switch (notification_mode.get()) {
            case Chat -> info(message);
            case Toast -> show_toast_notification(message, found_blocks);
            case Both -> {
                info(message);
                show_toast_notification(message, found_blocks);
            }
        }

        if (webhook_enabled.get() && !webhook_url.get().trim().isEmpty()) {
            send_webhook_notification(chunk_pos, found_blocks, detection_reason);
        }

        if (disconnect_on_find.get()) {
            handle_disconnection(chunk_pos, found_blocks);
        }
    }

    private String build_notification_message(ChunkPos chunk_pos, Map<Block, Integer> found_blocks) {
        StringBuilder msg = new StringBuilder();

        if (found_blocks.size() == 1) {
            Map.Entry<Block, Integer> entry = found_blocks.entrySet().iterator().next();
            Block block = entry.getKey();
            int count = entry.getValue();

            if (count == 1) {
                msg.append("Target block found: ").append(block.getName().getString());
            } else {
                msg.append("Target blocks found: ").append(block.getName().getString()).append(" x").append(count);
            }
        } else {
            msg.append("Target blocks found: ");
            List<String> block_list = new ArrayList<>();
            int total = 0;

            for (Map.Entry<Block, Integer> entry : found_blocks.entrySet()) {
                Block block = entry.getKey();
                int count = entry.getValue();
                total += count;

                if (count == 1) {
                    block_list.add(block.getName().getString());
                } else {
                    block_list.add(String.format("%s x%d", block.getName().getString(), count));
                }
            }

            msg.append(String.join(", ", block_list));
            msg.append(String.format(" (%d total)", total));
        }

        int center_x = chunk_pos.x * 16 + 8;
        int center_z = chunk_pos.z * 16 + 8;
        msg.append(" at chunk ").append(center_x).append(", ").append(center_z);

        return msg.toString();
    }

    private String build_detection_reason(Map<Block, Integer> found_blocks) {
        if (found_blocks.size() == 1) {
            Map.Entry<Block, Integer> entry = found_blocks.entrySet().iterator().next();
            return String.format("%s detected in chunk", entry.getKey().getName().getString());
        } else {
            return String.format("%d different target block types found", found_blocks.size());
        }
    }

    private void show_toast_notification(String message, Map<Block, Integer> found_blocks) {
        try {
            Block first_block = found_blocks.keySet().iterator().next();
              mc.getToastManager().add(new SystemToast(SystemToast.Type.WORLD_BACKUP, net.minecraft.text.Text.of(title), net.minecraft.text.Text.of(message)));
        } catch (Exception e) {
            info(message);
        }
    }

    private void send_webhook_notification(ChunkPos chunk_pos, Map<Block, Integer> found_blocks, String detection_reason) {
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

                int center_x = chunk_pos.x * 16 + 8;
                int center_z = chunk_pos.z * 16 + 8;

                StringBuilder items_breakdown = new StringBuilder();
                int total_items = 0;

                for (Map.Entry<Block, Integer> entry : found_blocks.entrySet()) {
                    String block_name = entry.getKey().getName().getString();
                    int count = entry.getValue();
                    items_breakdown.append(String.format("• %s: x%d\\n", block_name, count));
                    total_items += count;
                }

                String description = String.format("Target blocks detected in chunk at %d, %d!", center_x, center_z);

                String json_payload = String.format(
                    "{\"content\":\"%s\"," +
                        "\"username\":\"Block Notifier\"," +
                        "\"avatar_url\":\"https://i.imgur.com/OL2y1cr.png\"," +
                        "\"embeds\":[{" +
                        "\"title\":\"🎯 Block Notifier Alert\"," +
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
                        "\"footer\":{\"text\":\"Block Notifier\"}" +
                        "}]}",
                    message_content.replace("\"", "\\\""),
                    description.replace("\"", "\\\""),
                    3447003,
                    detection_reason.replace("\"", "\\\""),
                    total_items,
                    items_breakdown.toString().replace("\"", "\\\""),
                    center_x, center_z,
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

    private void handle_disconnection(ChunkPos chunk_pos, Map<Block, Integer> found_blocks) {
        int center_x = chunk_pos.x * 16 + 8;
        int center_z = chunk_pos.z * 16 + 8;

        info("TARGET BLOCKS FOUND! Disconnecting...");
        toggle();

        if (mc.player != null) {
            String first_block_name = found_blocks.keySet().iterator().next().getName().getString();
            mc.player.networkHandler.onDisconnect(
                new DisconnectS2CPacket(Text.literal(
                    String.format("TARGET BLOCKS FOUND: %s at %d, %d!",
                        first_block_name, center_x, center_z)))
            );
        }
    }

    @Override
    public String getInfoString() {
        return String.valueOf(total_blocks_found);
    }

    public enum Mode {
        Chat,
        Toast,
        Both
    }

    public int get_detected_block_count() {
        return total_blocks_found;
    }

    public Set<ChunkPos> get_processed_chunks() {
        return new HashSet<>(processed_chunks);
    }

    public Set<BlockPos> get_found_block_positions() {
        return new HashSet<>(found_block_positions);
    }

    public Set<BlockPos> get_new_found_blocks() {
        return new HashSet<>(new_found_blocks);
    }
}
