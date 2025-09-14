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
import java.time.Instant;

public class CoordSnapper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> chatfeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("Chat Feedback")
        .description("Show notification in chat")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> webhook = sgGeneral.add(new BoolSetting.Builder()
        .name("webhook")
        .description("Enable webhook notifications")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhookUrl = sgGeneral.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL for notifications")
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

            if (webhook.get() && !webhookUrl.get().isEmpty()) {
                sendWebhook(x, y, z);
            }

        } catch (Exception e) {
            error("Failed to copy/send coordinates: " + e.getMessage());
        } finally {
            toggle();
        }
    }

    private void sendWebhook(int x, int y, int z) {
        new Thread(() -> {
            try {
                URL url = new URL(webhookUrl.get());
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
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

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = json.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                connection.getInputStream().close();

            } catch (Exception e) {
                error("Webhook failed: " + e.getMessage());
            }
        }).start();
    }
}
