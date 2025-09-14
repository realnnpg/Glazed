package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.VersionUtil;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.toast.SystemToast;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class WanderingESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgwebhook = settings.createGroup("Webhook");


    // Render settings
    private final Setting<Boolean> showTracers = sgRender.add(new BoolSetting.Builder()
        .name("Show Tracers")
        .description("Draw tracer lines to wandering traders")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
        .name("Tracer Color")
        .description("Color of the tracer lines")
        .defaultValue(new SettingColor(0, 255, 0, 127))
        .visible(showTracers::get)
        .build()
    );

    private final Setting<Boolean> enableWebhook = sgwebhook.add(new BoolSetting.Builder()
        .name("Webhook")
        .description("Send webhook notifications when wandering traders are detected")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhookUrl = sgwebhook.add(new StringSetting.Builder()
        .name("Webhook URL")
        .description("Discord webhook URL")
        .defaultValue("")
        .visible(enableWebhook::get)
        .build()
    );

    private final Setting<Boolean> selfPing = sgwebhook.add(new BoolSetting.Builder()
        .name("Self Ping")
        .description("Ping yourself in the webhook message")
        .defaultValue(false)
        .visible(enableWebhook::get)
        .build()
    );

    private final Setting<String> discordId = sgwebhook.add(new StringSetting.Builder()
        .name("Discord ID")
        .description("Your Discord user ID for pinging")
        .defaultValue("")
        .visible(() -> enableWebhook.get() && selfPing.get())
        .build()
    );

    private final Setting<Boolean> enableDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("Disconnect")
        .description("Automatically disconnect when wandering traders are detected")
        .defaultValue(false)
        .build()
    );

    private final Setting<Mode> notificationMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("How to notify when wandering traders are detected")
        .defaultValue(Mode.Both)
        .build()
    );

    private final Setting<Boolean> toggleOnFind = sgGeneral.add(new BoolSetting.Builder()
        .name("Toggle when found")
        .description("Automatically toggles the module when a wandering trader is detected")
        .defaultValue(false)
        .build()
    );

    private final Set<Integer> detectedTraders = new HashSet<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public WanderingESP() {
        super(GlazedAddon.esp, "WanderingESP", "Detects wandering traders in the world");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Set<Integer> currentTraders = new HashSet<>();

        // Find all wandering traders in the world
        for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
            if (entity instanceof WanderingTraderEntity) {
                WanderingTraderEntity trader = (WanderingTraderEntity) entity;
                currentTraders.add(entity.getId());

                // Draw tracers if enabled
                if (showTracers.get()) {
                    double x = VersionUtil.getPrevX(entity) + (entity.getX() - VersionUtil.getPrevX(entity)) * event.tickDelta;
                    double y = VersionUtil.getPrevY(entity) + (entity.getY() - VersionUtil.getPrevY(entity)) * event.tickDelta;
                    double z = VersionUtil.getPrevZ(entity) + (entity.getZ() - VersionUtil.getPrevZ(entity)) * event.tickDelta;

                    // Target the center/body of the trader
                    double height = trader.getBoundingBox().maxY - trader.getBoundingBox().minY;
                    y += height / 2;

                    Color color = new Color(tracerColor.get());
                    event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, x, y, z, color);
                }
            }
        }

        // Check if we found new traders
        if (!currentTraders.isEmpty() && !currentTraders.equals(detectedTraders)) {
            Set<Integer> newTraders = new HashSet<>(currentTraders);
            newTraders.removeAll(detectedTraders);

            if (!newTraders.isEmpty()) {
                detectedTraders.addAll(newTraders);
                handleTraderDetection(newTraders.size());
            }
        } else if (currentTraders.isEmpty()) {
            detectedTraders.clear();
        }
    }

    private void handleTraderDetection(int traderCount) {
        String message = traderCount == 1 ?
            "Wandering trader detected!" :
            String.format("%d wandering traders detected!", traderCount);

        switch (notificationMode.get()) {
            case Chat -> info("(highlight)%s", message);
            case Toast -> mc.getToastManager().add(new SystemToast(SystemToast.Type.WORLD_BACKUP, net.minecraft.text.Text.of(title), net.minecraft.text.Text.of(message)));
            case Both -> {
                info("(highlight)%s", message);
                mc.getToastManager().add(new SystemToast(SystemToast.Type.WORLD_BACKUP, net.minecraft.text.Text.of(title), net.minecraft.text.Text.of(message)));
            }
        }

        if (enableWebhook.get()) {
            sendWebhookNotification(traderCount);
        }

        if (toggleOnFind.get()) {
            toggle();
        }

        if (enableDisconnect.get()) {
            disconnectFromServer(message);
        }
    }

    private void sendWebhookNotification(int traderCount) {
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

                String traderText = traderCount == 1 ? "trader" : "traders";
                String description = String.format("%d wandering %s detected!", traderCount, traderText);

                // Get current coordinates
                String coordinates = "Unknown";
                if (mc.player != null) {
                    coordinates = String.format("X: %.0f, Y: %.0f, Z: %.0f",
                        mc.player.getX(), mc.player.getY(), mc.player.getZ());
                }

                String jsonPayload = String.format(
                    "{\"content\":\"%s\"," +
                        "\"username\":\"WanderingESP\"," +
                        "\"avatar_url\":\"https://i.imgur.com/OL2y1cr.png\"," +
                        "\"embeds\":[{" +
                        "\"title\":\"🛒 Wandering Trader Alert\"," +
                        "\"description\":\"%s\"," +
                        "\"color\":65280," +
                        "\"thumbnail\":{\"url\":\"https://i.imgur.com/OL2y1cr.png\"}," +
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
                    traderCount,
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

    @Override
    public void onActivate() {
        detectedTraders.clear();
    }

    @Override
    public void onDeactivate() {
        detectedTraders.clear();
    }

    @Override
    public String getInfoString() {
        return detectedTraders.isEmpty() ? null : String.valueOf(detectedTraders.size());
    }

    public enum Mode {
        Chat,
        Toast,
        Both
    }
}
