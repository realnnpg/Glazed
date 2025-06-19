package com.nnpg.glazed.modules;

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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RTPer extends Module {

    public enum RTPRegion {
        ASIA("asia"),
        EAST("east"),
        EU_CENTRAL("eu central"),
        EU_WEST("eu west"),
        OCEANIA("oceania"),
        WEST("west");

        private final String commandPart;

        RTPRegion(String commandPart) {
            this.commandPart = commandPart;
        }

        public String getCommandPart() {
            return commandPart;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");

    // General Settings
    private final Setting<Integer> targetX = sgGeneral.add(new IntSetting.Builder()
        .name("target-x")
        .description("Target X coordinate.")
        .defaultValue(0)
        .build()
    );

    private final Setting<Integer> targetZ = sgGeneral.add(new IntSetting.Builder()
        .name("target-z")
        .description("Target Z coordinate.")
        .defaultValue(0)
        .build()
    );

    private final Setting<String> distance = sgGeneral.add(new StringSetting.Builder()
        .name("distance")
        .description("How close to get to the coordinates (supports k for thousands, e.g., 10k = 10000).")
        .defaultValue("1000")
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
        .description("Disconnect when reaching the target coordinates.")
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

    // Webhook Settings
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

    private int tickTimer = 0;
    private boolean isRtping = false;
    private int rtpAttempts = 0;
    private BlockPos lastRtpPos = null;

    public RTPer() {
        super(GlazedAddon.CATEGORY, "RTPer", "RTP to specific coordinates.");
    }

    @Override
    public void onActivate() {
        tickTimer = 0;
        isRtping = false;
        rtpAttempts = 0;
        lastRtpPos = null;

        if (mc.player == null) return;

        info("Starting RTPer to coordinates: %d, %d", targetX.get(), targetZ.get());
        info("Target distance: %d blocks", parseDistance());
    }

    @Override
    public void onDeactivate() {
        info("RTPer stopped after %d attempts", rtpAttempts);
        isRtping = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        tickTimer++;

        // Check if we're close enough to target
        if (isNearTarget()) {
            info("Reached target coordinates! Distance: %.0f blocks", getCurrentDistance());

            if (webhookEnabled.get()) {
                sendWebhook("Target Reached!",
                    String.format("Reached coordinates %d, %d\\nFinal distance: %.0f blocks\\nTotal RTP attempts: %d",
                        targetX.get(), targetZ.get(), getCurrentDistance(), rtpAttempts),
                    0x00FF00);
            }

            if (disconnectOnReach.get()) {
                info("Disconnecting as requested...");
                if (mc.world != null) {
                    mc.world.disconnect();
                }
            }

            toggle();
            return;
        }

        // Convert seconds to ticks (20 ticks = 1 second)
        if (tickTimer >= rtpDelay.get() * 20 && !isRtping) {
            performRTP();
            tickTimer = 0;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            isRtping = false;
            BlockPos currentPos = mc.player.getBlockPos();

            if (lastRtpPos == null || !currentPos.equals(lastRtpPos)) {
                lastRtpPos = currentPos;
                double distance = getCurrentDistance();
                info("RTP #%d completed. Current position: %d, %d, %d (Distance: %.0f blocks)",
                    rtpAttempts, currentPos.getX(), currentPos.getY(), currentPos.getZ(), distance);
            }
        }
    }

    private void performRTP() {
        if (mc.player == null) return;

        rtpAttempts++;
        isRtping = true;

        ChatUtils.sendPlayerMsg("/rtp " + rtpRegion.get().getCommandPart());

        info("Performing RTP #%d (Region: %s)...", rtpAttempts, rtpRegion.get().getCommandPart());
    }

    private boolean isNearTarget() {
        if (mc.player == null) return false;
        return getCurrentDistance() <= parseDistance();
    }

    private double getCurrentDistance() {
        if (mc.player == null) return Double.MAX_VALUE;

        BlockPos playerPos = mc.player.getBlockPos();
        double dx = playerPos.getX() - targetX.get();
        double dz = playerPos.getZ() - targetZ.get();

        return Math.sqrt(dx * dx + dz * dz);
    }

    private int parseDistance() {
        String dist = distance.get().toLowerCase().trim();

        if (dist.isEmpty()) {
            error("Distance is empty. Using default 1000.");
            return 1000;
        }

        try {
            if (dist.endsWith("k")) {
                String numberPart = dist.substring(0, dist.length() - 1).trim();
                if (numberPart.isEmpty()) {
                    error("Invalid distance format: '%s'. Using default 1000.", dist);
                    return 1000;
                }
                double value = Double.parseDouble(numberPart);
                return (int) (value * 1000);
            } else if (dist.endsWith("m")) {
                String numberPart = dist.substring(0, dist.length() - 1).trim();
                if (numberPart.isEmpty()) {
                    error("Invalid distance format: '%s'. Using default 1000.", dist);
                    return 1000;
                }
                double value = Double.parseDouble(numberPart);
                return (int) (value * 1000000);
            } else {
                return Integer.parseInt(dist);
            }
        } catch (NumberFormatException e) {
            error("Invalid distance format: '%s'. Error: %s. Using default 1000.", dist, e.getMessage());
            return 1000;
        }
    }

    private void sendWebhook(String title, String description, int color) {
        if (!webhookEnabled.get() || webhookUrl.get().isEmpty()) return;

        new Thread(() -> {
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

                String jsonPayload = String.format("""
                    {
                        "username": "Glazed Webhook",
                        "avatar_url": "https://i.imgur.com/OL2y1cr.png",
                        "embeds": [{
                            "title": "RTPer Alert",
                            "description": "%s",
                            "color": %d,
                            "footer": {
                                "text": "Sent by Glazed"
                            },
                            "timestamp": "%sZ",
                            "fields": [{
                                "name": "Status",
                                "value": "%s",
                                "inline": true
                            }]
                        }]
                    }""", description.replace("\\n", "\\n"), color, timestamp, title);

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl.get()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 204) {
                    info("Webhook sent successfully");
                } else {
                    error("Webhook failed with status: %d", response.statusCode());
                }
            } catch (IOException | InterruptedException e) {
                error("Failed to send webhook: %s", e.getMessage());
            }
        }).start();
    }
}
