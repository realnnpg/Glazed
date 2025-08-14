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
        .description("Distance to get within (supports k/m, e.g., 10k = 10000, 1.5m = 1500000).")
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
    private double lastReportedDistance = -1;
    private int targetDistanceBlocks = 1000;

    public RTPer() {
        super(GlazedAddon.CATEGORY, "RTPer", "RTP to specific coordinates.");
    }

    @Override
    public void onActivate() {
        tickTimer = 0;
        isRtping = false;
        rtpAttempts = 0;
        lastRtpPos = null;
        lastReportedDistance = -1;

        targetDistanceBlocks = parseDistance();

        if (mc.player == null) return;

        double currentDist = getCurrentDistance();
        info("RTPer started - target: (%d, %d)", targetX.get(), targetZ.get());
        info("Distance: %s -> %d blocks", distance.get(), targetDistanceBlocks);
        info("Current: %.1f blocks away", currentDist);

        if (currentDist <= targetDistanceBlocks) {
            info("Already close enough!");
            toggle();
        }
    }

    @Override
    public void onDeactivate() {
        info("Stopped after %d attempts", rtpAttempts);
        isRtping = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        double currentDistance = getCurrentDistance();

        if (isNearTarget(currentDistance)) {
            info("Done! %.1f blocks away (target: %d)", currentDistance, targetDistanceBlocks);

            if (webhookEnabled.get()) {
                sendWebhook("Target Reached!",
                    String.format("Got to %d, %d\\nDistance: %.1f/%d blocks\\nAttempts: %d",
                        targetX.get(), targetZ.get(), currentDistance, targetDistanceBlocks, rtpAttempts),
                    0x00FF00);
            }

            if (disconnectOnReach.get()) {
                info("Disconnecting...");
                if (mc.world != null) {
                    mc.world.disconnect();
                }
            }

            toggle();
            return;
        }

        if (tickTimer % 100 == 0 && Math.abs(currentDistance - lastReportedDistance) > 100) {
            info("Distance: %.1f blocks", currentDistance);
            lastReportedDistance = currentDistance;
        }

        tickTimer++;

        if (tickTimer >= rtpDelay.get() * 20 && !isRtping) {
            performRTP();
            tickTimer = 0;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket && mc.player != null) {
            isRtping = false;
            BlockPos currentPos = mc.player.getBlockPos();

            if (lastRtpPos == null || !currentPos.equals(lastRtpPos)) {
                rtpAttempts++;
                lastRtpPos = currentPos;
                double distance = getCurrentDistance();
                info("RTP %d done - pos: (%d, %d, %d) dist: %.1f",
                    rtpAttempts, currentPos.getX(), currentPos.getY(), currentPos.getZ(), distance);

                if (lastReportedDistance > 0) {
                    double diff = lastReportedDistance - distance;
                    if (diff > 0) {
                        info("Better by %.1f blocks", diff);
                    } else if (diff < -1000) {
                        info("Worse by %.1f blocks", Math.abs(diff));
                    }
                }
                lastReportedDistance = distance;
            }
        }
    }

    private void performRTP() {
        if (mc.player == null) return;

        isRtping = true;

        ChatUtils.sendPlayerMsg("/rtp " + rtpRegion.get().getCommandPart());

        double currentDistance = getCurrentDistance();
        info("Attempting RTP (%s) - current: %.1f blocks",
            rtpRegion.get().getCommandPart(), currentDistance);
    }

    private boolean isNearTarget() {
        return isNearTarget(getCurrentDistance());
    }

    private boolean isNearTarget(double currentDistance) {
        return currentDistance <= targetDistanceBlocks;
    }

    private double getCurrentDistance() {
        if (mc.player == null) return Double.MAX_VALUE;

        BlockPos pos = mc.player.getBlockPos();
        double dx = pos.getX() - targetX.get();
        double dz = pos.getZ() - targetZ.get();

        return Math.sqrt(dx * dx + dz * dz);
    }

    private int parseDistance() {
        String dist = distance.get().toLowerCase().trim();

        if (dist.isEmpty()) {
            error("Empty distance, using 1000");
            return 1000;
        }

        try {
            if (dist.endsWith("k")) {
                String num = dist.substring(0, dist.length() - 1).trim();
                if (num.isEmpty()) {
                    error("Bad format: '%s', using 1000", dist);
                    return 1000;
                }
                double val = Double.parseDouble(num);
                return (int) (val * 1000);
            } else if (dist.endsWith("m")) {
                String num = dist.substring(0, dist.length() - 1).trim();
                if (num.isEmpty()) {
                    error("Bad format: '%s', using 1000", dist);
                    return 1000;
                }
                double val = Double.parseDouble(num);
                return (int) (val * 1000000);
            } else {
                return Integer.parseInt(dist);
            }
        } catch (NumberFormatException e) {
            error("Can't parse '%s': %s, using 1000", dist, e.getMessage());
            return 1000;
        }
    }

    private void sendWebhook(String title, String description, int color) {
        if (!webhookEnabled.get() || webhookUrl.get().isEmpty()) return;

        new Thread(() -> {
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

                String json = String.format("""
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
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl.get()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

                if (res.statusCode() == 204) {
                    info("Webhook sent");
                } else {
                    error("Webhook failed: %d", res.statusCode());
                }
            } catch (IOException | InterruptedException e) {
                error("Webhook error: %s", e.getMessage());
            }
        }).start();
    }
}
