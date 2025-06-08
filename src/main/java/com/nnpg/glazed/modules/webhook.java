package com.nnpg.glazed.modules;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class webhook extends Module {

    // Settings for the module
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Setting for the webhook URL
    private final Setting<String> webhookUrl = sgGeneral.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL")
        .defaultValue("")
        .build()
    );

    // Setting for custom title
    private final Setting<String> embedTitle = sgGeneral.add(new StringSetting.Builder()
        .name("embed-title")
        .description("Title for the Discord embed")
        .defaultValue("Tittle")
        .build()
    );

    // Setting for custom description
    private final Setting<String> embedDescription = sgGeneral.add(new StringSetting.Builder()
        .name("embed-description")
        .description("Description for the Discord embed")
        .defaultValue("Description")
        .build()
    );

    // Setting for embed color (in decimal format)
    private final Setting<Integer> embedColor = sgGeneral.add(new IntSetting.Builder()
        .name("embed-color")
        .description("Color of the embed in decimal format (default: 3447003 = blue)")
        .defaultValue(3447003)
        .min(0)
        .max(16777215)
        .build()
    );

    // Setting to enable/disable sending webhook on activation
    private final Setting<Boolean> sendOnActivation = sgGeneral.add(new BoolSetting.Builder()
        .name("send-on-activation")
        .description("Send webhook message when module is activated")
        .defaultValue(true)
        .build()
    );

    // HTTP client for sending requests
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public webhook() {
        super(GlazedAddon.CATEGORY, "WebhookTesting", "When turned on sends a webhook.");
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @Override
    public void onActivate() {
        // Check if user wants to send webhook on activation
        if (!sendOnActivation.get()) {
            info("Module activated (webhook sending disabled)");
            return;
        }

        // Check if webhook URL is provided
        if (webhookUrl.get().isEmpty()) {
            error("Please provide a webhook URL in the settings!");
            toggle();
            return;
        }

        // Send the webhook message when activated
        sendWebhookMessage();

        // Automatically disable the module after sending
        toggle();
    }

    private void sendWebhookMessage() {
        try {
            // Get current player name
            String playerName = MinecraftClient.getInstance().getSession().getUsername();

            // Create JSON payload for Discord webhook with embed
            String jsonPayload = String.format(
                "{\"embeds\":[{\"title\":\"%s\",\"description\":\"%s\",\"color\":%d,\"footer\":{\"text\":\"Sent by %s\"}}]}",
                embedTitle.get(),
                embedDescription.get(),
                embedColor.get(),
                playerName
            );

            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl.get()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(10))
                .build();

            // Send request asynchronously to avoid blocking the game
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        info("Webhook message sent successfully!");
                    } else {
                        error("Failed to send webhook message. Status code: " + response.statusCode());
                    }
                })
                .exceptionally(throwable -> {
                    error("Error sending webhook message: " + throwable.getMessage());
                    return null;
                });

        } catch (Exception e) {
            error("Error creating webhook request: " + e.getMessage());
        }
    }
}
