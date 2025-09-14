package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.VersionUtil; // For 1.21.4 - change to VersionUtil2 for 1.21.5
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.toast.SystemToast;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
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

public class VillagerESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgwebhook = settings.createGroup("Webhook");

    // General settings
    private final Setting<DetectionMode> detectionMode = sgGeneral.add(new EnumSetting.Builder<DetectionMode>()
        .name("Detection Mode")
        .description("What type of villagers to detect")
        .defaultValue(DetectionMode.Both)
        .build()
    );

    // Render settings
    private final Setting<Boolean> showTracers = sgRender.add(new BoolSetting.Builder()
        .name("Show Tracers")
        .description("Draw tracer lines to villagers")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> villagerTracerColor = sgRender.add(new ColorSetting.Builder()
        .name("Villager Tracer Color")
        .description("Color of the tracer lines for regular villagers")
        .defaultValue(new SettingColor(0, 255, 0, 127))
        .visible(() -> showTracers.get() && (detectionMode.get() == DetectionMode.Villagers || detectionMode.get() == DetectionMode.Both))
        .build()
    );

    private final Setting<SettingColor> zombieVillagerTracerColor = sgRender.add(new ColorSetting.Builder()
        .name("Zombie Villager Tracer Color")
        .description("Color of the tracer lines for zombie villagers")
        .defaultValue(new SettingColor(255, 0, 0, 127))
        .visible(() -> showTracers.get() && (detectionMode.get() == DetectionMode.ZombieVillagers || detectionMode.get() == DetectionMode.Both))
        .build()
    );

    private final Setting<Boolean> enableWebhook = sgwebhook.add(new BoolSetting.Builder()
        .name("Webhook")
        .description("Send webhook notifications when villagers are detected")
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
        .description("Automatically disconnect when villagers are detected")
        .defaultValue(false)
        .build()
    );

    private final Setting<Mode> notificationMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("notification-mode")
        .description("How to notify when villagers are detected")
        .defaultValue(Mode.Both)
        .build()
    );

    private final Setting<Boolean> toggleOnFind = sgGeneral.add(new BoolSetting.Builder()
        .name("Toggle when found")
        .description("Automatically toggles the module when villagers are detected")
        .defaultValue(false)
        .build()
    );

    private final Set<Integer> detectedVillagers = new HashSet<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public VillagerESP() {
        super(GlazedAddon.esp, "VillagerESP", "Detects villagers and zombie villagers in the world");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Set<Integer> currentVillagers = new HashSet<>();
        int villagerCount = 0;
        int zombieVillagerCount = 0;

        // Find all villagers in the world
        for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
            boolean shouldDetect = false;
            Color tracerColor = null;

            if (entity instanceof VillagerEntity && (detectionMode.get() == DetectionMode.Villagers || detectionMode.get() == DetectionMode.Both)) {
                shouldDetect = true;
                tracerColor = new Color(villagerTracerColor.get());
                villagerCount++;
            } else if (entity instanceof ZombieVillagerEntity && (detectionMode.get() == DetectionMode.ZombieVillagers || detectionMode.get() == DetectionMode.Both)) {
                shouldDetect = true;
                tracerColor = new Color(zombieVillagerTracerColor.get());
                zombieVillagerCount++;
            }

            if (shouldDetect) {
                currentVillagers.add(entity.getId());

                // Draw tracers if enabled
                if (showTracers.get()) {
                    // Use VersionUtil for cross-version compatibility
                    double x = VersionUtil.getPrevX(entity) + (entity.getX() - VersionUtil.getPrevX(entity)) * event.tickDelta;
                    double y = VersionUtil.getPrevY(entity) + (entity.getY() - VersionUtil.getPrevY(entity)) * event.tickDelta;
                    double z = VersionUtil.getPrevZ(entity) + (entity.getZ() - VersionUtil.getPrevZ(entity)) * event.tickDelta;

                    // Target the center/body of the villager
                    double height = entity.getBoundingBox().maxY - entity.getBoundingBox().minY;
                    y += height / 2;

                    event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, x, y, z, tracerColor);
                }
            }
        }

        // Check if we found new villagers
        if (!currentVillagers.isEmpty() && !currentVillagers.equals(detectedVillagers)) {
            Set<Integer> newVillagers = new HashSet<>(currentVillagers);
            newVillagers.removeAll(detectedVillagers);

            if (!newVillagers.isEmpty()) {
                detectedVillagers.addAll(newVillagers);
                handleVillagerDetection(villagerCount, zombieVillagerCount);
            }
        } else if (currentVillagers.isEmpty()) {
            detectedVillagers.clear();
        }
    }

    private void handleVillagerDetection(int villagerCount, int zombieVillagerCount) {
        String message = buildDetectionMessage(villagerCount, zombieVillagerCount);

        switch (notificationMode.get()) {
            case Chat -> info("(highlight)%s", message);
            case Toast -> mc.getToastManager().add(new SystemToast(SystemToast.Type.WORLD_BACKUP, net.minecraft.text.Text.of(title), net.minecraft.text.Text.of(message)));
            case Both -> {
                info("(highlight)%s", message);
                mc.getToastManager().add(new SystemToast(SystemToast.Type.WORLD_BACKUP, net.minecraft.text.Text.of(title), net.minecraft.text.Text.of(message)));
            }
        }

        if (enableWebhook.get()) {
            sendWebhookNotification(villagerCount, zombieVillagerCount);
        }

        if (toggleOnFind.get()) {
            toggle();
        }

        if (enableDisconnect.get()) {
            disconnectFromServer(message);
        }
    }

    private String buildDetectionMessage(int villagerCount, int zombieVillagerCount) {
        int totalCount = villagerCount + zombieVillagerCount;

        if (detectionMode.get() == DetectionMode.Villagers) {
            return villagerCount == 1 ? "Villager detected!" : String.format("%d villagers detected!", villagerCount);
        } else if (detectionMode.get() == DetectionMode.ZombieVillagers) {
            return zombieVillagerCount == 1 ? "Zombie villager detected!" : String.format("%d zombie villagers detected!", zombieVillagerCount);
        } else {
            // Both mode
            if (villagerCount > 0 && zombieVillagerCount > 0) {
                return String.format("%d villagers and %d zombie villagers detected!", villagerCount, zombieVillagerCount);
            } else if (villagerCount > 0) {
                return villagerCount == 1 ? "Villager detected!" : String.format("%d villagers detected!", villagerCount);
            } else {
                return zombieVillagerCount == 1 ? "Zombie villager detected!" : String.format("%d zombie villagers detected!", zombieVillagerCount);
            }
        }
    }

    private void sendWebhookNotification(int villagerCount, int zombieVillagerCount) {
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

                String description = buildDetectionMessage(villagerCount, zombieVillagerCount);

                // Get current coordinates
                String coordinates = "Unknown";
                if (mc.player != null) {
                    coordinates = String.format("X: %.0f, Y: %.0f, Z: %.0f",
                        mc.player.getX(), mc.player.getY(), mc.player.getZ());
                }

                // Build fields based on what was detected
                StringBuilder fieldsBuilder = new StringBuilder();
                fieldsBuilder.append(String.format(
                    "{\"name\":\"Server\",\"value\":\"%s\",\"inline\":true},",
                    serverInfo.replace("\"", "\\\"")
                ));

                if (villagerCount > 0) {
                    fieldsBuilder.append(String.format(
                        "{\"name\":\"Villagers\",\"value\":\"%d\",\"inline\":true},",
                        villagerCount
                    ));
                }

                if (zombieVillagerCount > 0) {
                    fieldsBuilder.append(String.format(
                        "{\"name\":\"Zombie Villagers\",\"value\":\"%d\",\"inline\":true},",
                        zombieVillagerCount
                    ));
                }

                fieldsBuilder.append(String.format(
                    "{\"name\":\"Coordinates\",\"value\":\"%s\",\"inline\":false}," +
                        "{\"name\":\"Time\",\"value\":\"<t:%d:R>\",\"inline\":true}",
                    coordinates.replace("\"", "\\\""),
                    System.currentTimeMillis() / 1000
                ));

                String jsonPayload = String.format(
                    "{\"content\":\"%s\"," +
                        "\"username\":\"VillagerESP\"," +
                        "\"avatar_url\":\"https://i.imgur.com/OL2y1cr.png\"," +
                        "\"embeds\":[{" +
                        "\"title\":\"🏘️ Villager Alert\"," +
                        "\"description\":\"%s\"," +
                        "\"color\":65280," +
                        "\"thumbnail\":{\"url\":\"https://i.imgur.com/OL2y1cr.png\"}," +
                        "\"fields\":[%s]," +
                        "\"footer\":{\"text\":\"Sent by Glazed\"}" +
                        "}]}",
                    messageContent.replace("\"", "\\\""),
                    description.replace("\"", "\\\""),
                    fieldsBuilder.toString()
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
        detectedVillagers.clear();
    }

    @Override
    public void onDeactivate() {
        detectedVillagers.clear();
    }

    @Override
    public String getInfoString() {
        return detectedVillagers.isEmpty() ? null : String.valueOf(detectedVillagers.size());
    }

    public enum Mode {
        Chat,
        Toast,
        Both
    }

    public enum DetectionMode {
        Villagers("Villagers"),
        ZombieVillagers("Zombie Villagers"),
        Both("Both");

        private final String name;

        DetectionMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
