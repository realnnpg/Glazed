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
import net.minecraft.entity.passive.LlamaEntity;
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

public class LamaESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgwebhook = settings.createGroup("Webhook");


    // Render settings
    private final Setting<Boolean> showTracers = sgRender.add(new BoolSetting.Builder()
        .name("Show Tracers")
        .description("Draw tracer lines to llamas")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
        .name("Tracer Color")
        .description("Color of the tracer lines")
        .defaultValue(new SettingColor(255, 165, 0, 127)) // Orange color for llamas
        .visible(showTracers::get)
        .build()
    );

    private final Setting<Boolean> enableWebhook = sgwebhook.add(new BoolSetting.Builder()
        .name("Webhook")
        .description("Send webhook notifications when llamas are detected")
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
        .description("Automatically disconnect when llamas are detected")
        .defaultValue(false)
        .build()
    );

    private final Setting<Mode> notificationMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("How to notify when llamas are detected")
        .defaultValue(Mode.Both)
        .build()
    );

    private final Setting<Boolean> toggleOnFind = sgGeneral.add(new BoolSetting.Builder()
        .name("Toggle when found")
        .description("Automatically toggles the module when a llama is detected")
        .defaultValue(false)
        .build()
    );

    private final Set<Integer> detectedLlamas = new HashSet<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public LamaESP() {
        super(GlazedAddon.esp, "LamaESP", "Detects llamas in the world");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Set<Integer> currentLlamas = new HashSet<>();

        // Find all llamas in the world
        for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
            if (entity instanceof LlamaEntity) {
                LlamaEntity llama = (LlamaEntity) entity;
                currentLlamas.add(entity.getId());

                // Draw tracers if enabled
                if (showTracers.get()) {
                    double x = VersionUtil.getPrevX(entity) + (entity.getX() - VersionUtil.getPrevX(entity)) * event.tickDelta;
                    double y = VersionUtil.getPrevY(entity) + (entity.getY() - VersionUtil.getPrevY(entity)) * event.tickDelta;
                    double z = VersionUtil.getPrevZ(entity) + (entity.getZ() - VersionUtil.getPrevZ(entity)) * event.tickDelta;

                    // Target the center/body of the llama
                    double height = llama.getBoundingBox().maxY - llama.getBoundingBox().minY;
                    y += height / 2;

                    Color color = new Color(tracerColor.get());
                    event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, x, y, z, color);
                }
            }
        }

        // Check if we found new llamas
        if (!currentLlamas.isEmpty() && !currentLlamas.equals(detectedLlamas)) {
            Set<Integer> newLlamas = new HashSet<>(currentLlamas);
            newLlamas.removeAll(detectedLlamas);

            if (!newLlamas.isEmpty()) {
                detectedLlamas.addAll(newLlamas);
                handleLlamaDetection(newLlamas.size());
            }
        } else if (currentLlamas.isEmpty()) {
            detectedLlamas.clear();
        }
    }

    private void handleLlamaDetection(int llamaCount) {
        String message = llamaCount == 1 ?
            "Llama detected!" :
            String.format("%d llamas detected!", llamaCount);

        switch (notificationMode.get()) {
            case Chat -> info("(highlight)%s", message);
            case Toast -> mc.getToastManager().add(new SystemToast(SystemToast.Type.WORLD_BACKUP, net.minecraft.text.Text.of(title), net.minecraft.text.Text.of(message)));
            case Both -> {
                info("(highlight)%s", message);
                mc.getToastManager().add(new SystemToast(SystemToast.Type.WORLD_BACKUP, net.minecraft.text.Text.of(title), net.minecraft.text.Text.of(message)));
            }
        }

        if (enableWebhook.get()) {
            sendWebhookNotification(llamaCount);
        }

        if (toggleOnFind.get()) {
            toggle();
        }

        if (enableDisconnect.get()) {
            disconnectFromServer(message);
        }
    }

    private void sendWebhookNotification(int llamaCount) {
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

                String llamaText = llamaCount == 1 ? "llama" : "llamas";
                String description = String.format("%d %s detected!", llamaCount, llamaText);

                // Get current coordinates
                String coordinates = "Unknown";
                if (mc.player != null) {
                    coordinates = String.format("X: %.0f, Y: %.0f, Z: %.0f",
                        mc.player.getX(), mc.player.getY(), mc.player.getZ());
                }

                String jsonPayload = String.format(
                    "{\"content\":\"%s\"," +
                        "\"username\":\"LamaESP\"," +
                        "\"avatar_url\":\"https://minecraft.wiki/images/f/f4/Llama_BE2.png\"," +
                        "\"embeds\":[{" +
                        "\"title\":\"🦙 Llama Alert\"," +
                        "\"description\":\"%s\"," +
                        "\"color\":16753920," + // Orange color
                        "\"thumbnail\":{\"url\":\"https://minecraft.wiki/images/f/f4/Llama_BE2.png\"}," +
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
                    llamaCount,
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
        detectedLlamas.clear();
    }

    @Override
    public void onDeactivate() {
        detectedLlamas.clear();
    }

    @Override
    public String getInfoString() {
        return detectedLlamas.isEmpty() ? null : String.valueOf(detectedLlamas.size());
    }

    public enum Mode {
        Chat,
        Toast,
        Both
    }
}
