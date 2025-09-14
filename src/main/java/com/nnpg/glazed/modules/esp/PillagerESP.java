package com.nnpg.glazed.modules.esp;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.nnpg.glazed.GlazedAddon;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.toast.SystemToast;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class PillagerESP extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgESP = settings.createGroup("ESP");
    private final SettingGroup sgTracers = settings.createGroup("Tracers");
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");

    private final Setting<Integer> maxDistance = sgGeneral.add(new IntSetting.Builder()
        .name("max-distance")
        .description("Maximum distance to render pillagers")
        .defaultValue(128)
        .range(16, 256)
        .sliderRange(16, 256)
        .build());

    private final Setting<Boolean> showCount = sgGeneral.add(new BoolSetting.Builder()
        .name("show-count")
        .description("Show pillager count in chat")
        .defaultValue(true)
        .build());

    private final Setting<NotificationMode> notificationMode = sgGeneral.add(new EnumSetting.Builder<NotificationMode>()
        .name("notification-mode")
        .description("How to notify when pillagers are detected")
        .defaultValue(NotificationMode.Both)
        .build());

    private final Setting<Boolean> toggleOnFind = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-when-found")
        .description("Automatically toggles the module when pillagers are detected")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> enableDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect")
        .description("Automatically disconnect when pillagers are detected")
        .defaultValue(false)
        .build());

    private final Setting<SettingColor> espColor = sgESP.add(new ColorSetting.Builder()
        .name("esp-color")
        .description("Color of pillager ESP")
        .defaultValue(new SettingColor(255, 0, 0, 100))
        .build());

    private final Setting<ShapeMode> shapeMode = sgESP.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the ESP shapes are rendered")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<Boolean> tracersEnabled = sgTracers.add(new BoolSetting.Builder()
        .name("tracers-enabled")
        .description("Enable tracers to pillagers")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> tracerColor = sgTracers.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Color of tracers")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(tracersEnabled::get)
        .build());

    private final Setting<TracersMode> tracersMode = sgTracers.add(new EnumSetting.Builder<TracersMode>()
        .name("tracers-mode")
        .description("How tracers are rendered")
        .defaultValue(TracersMode.Line)
        .visible(tracersEnabled::get)
        .build());

    private final Setting<Boolean> enableWebhook = sgWebhook.add(new BoolSetting.Builder()
        .name("webhook")
        .description("Send webhook notifications when pillagers are detected")
        .defaultValue(false)
        .build());

    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL")
        .defaultValue("")
        .visible(enableWebhook::get)
        .build());

    private final Setting<Boolean> selfPing = sgWebhook.add(new BoolSetting.Builder()
        .name("self-ping")
        .description("Ping yourself in the webhook message")
        .defaultValue(false)
        .visible(enableWebhook::get)
        .build());

    private final Setting<String> discordId = sgWebhook.add(new StringSetting.Builder()
        .name("discord-id")
        .description("Your Discord user ID for pinging")
        .defaultValue("")
        .visible(() -> enableWebhook.get() && selfPing.get())
        .build());

    private final List<PillagerEntity> pillagers = new ArrayList<>();
    private final Set<Integer> detectedPillagers = new HashSet<>();
    private int lastPillagerCount = 0;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public enum TracersMode {
        Line,
        Dot,
        Both
    }

    public enum NotificationMode {
        Chat,
        Toast,
        Both
    }

    public PillagerESP() {
        super(GlazedAddon.esp, "PillagerESP", "ESP for pillagers with tracers, webhook notifications and info display");
    }

    @Override
    public void onActivate() {
        pillagers.clear();
        detectedPillagers.clear();
        lastPillagerCount = 0;
    }

    @Override
    public void onDeactivate() {
        pillagers.clear();
        detectedPillagers.clear();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || event.renderer == null) return;

        try {
            pillagers.clear();
            Set<Integer> currentPillagers = new HashSet<>();

            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof PillagerEntity pillager) {
                    double distance = mc.player.distanceTo(pillager);
                    if (distance <= maxDistance.get()) {
                        pillagers.add(pillager);
                        currentPillagers.add(entity.getId());
                    }
                }
            }

            // Handle notifications and count display
            if (showCount.get() && pillagers.size() != lastPillagerCount) {
                if (pillagers.size() > 0) {
                    String message = "Found " + pillagers.size() + " pillager(s) nearby";

                    switch (notificationMode.get()) {
                        case Chat -> info("§5[§dPillagerESP§5] §c" + message);
                        case Toast -> mc.getToastManager().add(new SystemToast(SystemToast.Type.WORLD_BACKUP, net.minecraft.text.Text.of(title), net.minecraft.text.Text.of(message)));
                        case Both -> {
                            info("§5[§dPillagerESP§5] §c" + message);
                            mc.getToastManager().add(new SystemToast(SystemToast.Type.WORLD_BACKUP, net.minecraft.text.Text.of(title), net.minecraft.text.Text.of(message)));
                        }
                    }
                }
                lastPillagerCount = pillagers.size();
            }

            // Check for new pillagers for webhook notifications
            if (!currentPillagers.isEmpty() && !currentPillagers.equals(detectedPillagers)) {
                Set<Integer> newPillagers = new HashSet<>(currentPillagers);
                newPillagers.removeAll(detectedPillagers);

                if (!newPillagers.isEmpty()) {
                    detectedPillagers.addAll(newPillagers);
                    handlePillagerDetection(pillagers.size());
                }
            } else if (currentPillagers.isEmpty()) {
                detectedPillagers.clear();
            }

            // Render pillagers
            for (PillagerEntity pillager : pillagers) {
                if (pillager == null || !pillager.isAlive()) continue;

                try {
                    renderPillager(pillager, event);
                } catch (Exception e) {
                    // Silently handle rendering errors
                }
            }
        } catch (Exception e) {
            // Silently handle general errors
        }
    }

    private void handlePillagerDetection(int pillagerCount) {
        if (enableWebhook.get()) {
            sendWebhookNotification(pillagerCount);
        }

        if (toggleOnFind.get()) {
            toggle();
        }

        if (enableDisconnect.get()) {
            String message = pillagerCount == 1 ?
                "Pillager detected!" :
                String.format("%d pillagers detected!", pillagerCount);
            disconnectFromServer(message);
        }
    }

    private void sendWebhookNotification(int pillagerCount) {
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

                String pillagerText = pillagerCount == 1 ? "pillager" : "pillagers";
                String description = String.format("%d %s detected!", pillagerCount, pillagerText);

                // Get current coordinates
                String coordinates = "Unknown";
                if (mc.player != null) {
                    coordinates = String.format("X: %.0f, Y: %.0f, Z: %.0f",
                        mc.player.getX(), mc.player.getY(), mc.player.getZ());
                }

                String jsonPayload = String.format(
                    "{\"content\":\"%s\"," +
                        "\"username\":\"PillagerESP\"," +
                        "\"avatar_url\":\"https://minecraft.wiki/images/4/4b/Pillager_JE2_BE2.png\"," +
                        "\"embeds\":[{" +
                        "\"title\":\"⚔️ Pillager Alert\"," +
                        "\"description\":\"%s\"," +
                        "\"color\":16711680," + // Red color
                        "\"thumbnail\":{\"url\":\"https://minecraft.wiki/images/4/4b/Pillager_JE2_BE2.png\"}," +
                        "\"fields\":[" +
                        "{\"name\":\"Count\",\"value\":\"%d\",\"inline\":true}," +
                        "{\"name\":\"Server\",\"value\":\"%s\",\"inline\":true}," +
                        "{\"name\":\"Coordinates\",\"value\":\"%s\",\"inline\":false}," +
                        "{\"name\":\"Time\",\"value\":\"<t:%d:R>\",\"inline\":true}" +
                        "]," +
                        "\"footer\":{\"text\":\"Sent by Glazed\"}" +
                        "}]}",
                    messageContent.replace("\"", "\\\""),
                    description.replace("\"", "\\\""),
                    pillagerCount,
                    serverInfo.replace("\"", "\\\""),
                    coordinates.replace("\"", "\\\""),
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

    private void disconnectFromServer(String reason) {
        if (mc.world != null && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().getConnection().disconnect(Text.literal(reason));
            info("Disconnected from server - " + reason);
        }
    }

    private void renderPillager(PillagerEntity pillager, Render3DEvent event) {
        if (mc.player == null) return;

        Vec3d pos = pillager.getPos();
        Box box = pillager.getBoundingBox();

        renderESP(pillager, box, event);

        if (tracersEnabled.get()) {
            renderTracers(pillager, pos, event);
        }
    }

    private void renderESP(PillagerEntity pillager, Box box, Render3DEvent event) {
        meteordevelopment.meteorclient.utils.render.color.Color color = new meteordevelopment.meteorclient.utils.render.color.Color(espColor.get());

        event.renderer.box(box, color, color, shapeMode.get(), 0);
    }

    private void renderTracers(PillagerEntity pillager, Vec3d pos, Render3DEvent event) {
        if (mc.player == null) return;

        meteordevelopment.meteorclient.utils.render.color.Color color = new meteordevelopment.meteorclient.utils.render.color.Color(tracerColor.get());

        Vec3d pillagerCenter = Vec3d.ofCenter(new BlockPos(
            (int) pos.x,
            (int) (pos.y + pillager.getHeight() / 2),
            (int) pos.z
        ));

        switch (tracersMode.get()) {
            case Line:
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    pillagerCenter.x, pillagerCenter.y, pillagerCenter.z, color);
                break;
            case Dot:
                Box dotBox = new Box(pillagerCenter.x - 0.1, pillagerCenter.y - 0.1, pillagerCenter.z - 0.1,
                    pillagerCenter.x + 0.1, pillagerCenter.y + 0.1, pillagerCenter.z + 0.1);
                event.renderer.box(dotBox, color, color, ShapeMode.Both, 0);
                break;
            case Both:
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    pillagerCenter.x, pillagerCenter.y, pillagerCenter.z, color);
                Box dotBox2 = new Box(pillagerCenter.x - 0.1, pillagerCenter.y - 0.1, pillagerCenter.z - 0.1,
                    pillagerCenter.x + 0.1, pillagerCenter.y + 0.1, pillagerCenter.z + 0.1);
                event.renderer.box(dotBox2, color, color, ShapeMode.Both, 0);
                break;
        }
    }

    @Override
    public String getInfoString() {
        return String.valueOf(pillagers.size());
    }
}
