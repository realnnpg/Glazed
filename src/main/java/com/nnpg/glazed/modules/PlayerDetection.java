package com.nnpg.glazed.modules;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PlayerDetection extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> enableWebhook = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-webhook")
        .description("Send webhook notifications when players are detected")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhookUrl = sgGeneral.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL")
        .defaultValue("")
        .visible(enableWebhook::get)
        .build()
    );

    private final Setting<Boolean> enableDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-disconnect")
        .description("Automatically disconnect when players are detected")
        .defaultValue(true)
        .build()
    );

    private final Setting<Mode> notificationMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("How to notify when players are detected")
        .defaultValue(Mode.Both)
        .build()
    );

    private final Set<String> detectedPlayers = new HashSet<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();


    public PlayerDetection() {
        super(GlazedAddon.CATEGORY, "PlayerDetection", "Detects when players are in the world");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Set<String> currentPlayers = new HashSet<>();

        // Check for rendered players
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;

            // Player is being rendered if it's in the world
            currentPlayers.add(player.getGameProfile().getName());
        }

        // If new players detected
        if (!currentPlayers.isEmpty() && !currentPlayers.equals(detectedPlayers)) {
            detectedPlayers.clear();
            detectedPlayers.addAll(currentPlayers);

            handlePlayerDetection(currentPlayers);
        } else if (currentPlayers.isEmpty()) {
            detectedPlayers.clear();
        }
    }

    private void handlePlayerDetection(Set<String> players) {
        String playerList = String.join(", ", players);

        // Send notifications
        switch (notificationMode.get()) {
            case Chat -> info("Player(s) detected: (highlight)%s", playerList);
            case Toast -> mc.getToastManager().add(new MeteorToast(Items.PLAYER_HEAD, title, "Player Detected!"));
            case Both -> {
                info("Player(s) detected: (highlight)%s", playerList);
                mc.getToastManager().add(new MeteorToast(Items.PLAYER_HEAD, title, "Player Detected!"));
            }
        }

        // Send webhook if enabled
        if (enableWebhook.get()) {
            sendWebhookNotification(players);
        }

        // Disconnect if enabled
        if (enableDisconnect.get()) {
            disconnectFromServer(playerList);
        }
    }

    private void sendWebhookNotification(Set<String> players) {
        String url = webhookUrl.get().trim();
        if (url.isEmpty()) {
            warning("Webhook URL not configured!");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String playerList = String.join(", ", players);
                String serverInfo = mc.getCurrentServerEntry() != null ?
                    mc.getCurrentServerEntry().address : "Unknown Server";

                String jsonPayload = String.format(
                    "{\"embeds\":[{" +
                        "\"title\":\"🚨 Player Detection Alert\"," +
                        "\"description\":\"Player(s) detected on server!\"," +
                        "\"color\":15158332," +
                        "\"fields\":[" +
                        "{\"name\":\"Players\",\"value\":\"%s\",\"inline\":false}," +
                        "{\"name\":\"Server\",\"value\":\"%s\",\"inline\":true}," +
                        "{\"name\":\"Time\",\"value\":\"<t:%d:R>\",\"inline\":true}" +
                        "]," +
                        "\"footer\":{\"text\":\"Meteor Player Detection\"}" +
                        "}]}",
                    playerList.replace("\"", "\\\""),
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

    private void disconnectFromServer(String playerList) {
        if (mc.world != null && mc.getNetworkHandler() != null) {
            String reason = "Player(s) detected: " + playerList;
            mc.getNetworkHandler().getConnection().disconnect(Text.literal(reason));
            info("Disconnected from server - " + reason);
        }
    }

    @Override
    public void onActivate() {
        detectedPlayers.clear();
    }

    @Override
    public void onDeactivate() {
        detectedPlayers.clear();
    }

    @Override
    public String getInfoString() {
        return detectedPlayers.isEmpty() ? null : String.valueOf(detectedPlayers.size());
    }

    public enum Mode {
        Chat,
        Toast,
        Both
    }
}
