package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.VersionUtil;
import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class AutoFirework extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");

    // General Settings
    private final Setting<Double> fireworkDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("firework-delay")
        .description("Delay between firework usage in seconds.")
        .defaultValue(5.0)
        .min(0.1)
        .sliderMax(20.0)
        .build()
    );

    private final Setting<Integer> durabilityThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("durability-threshold")
        .description("Durability percentage threshold to trigger low durability actions.")
        .defaultValue(15)
        .range(1, 50)
        .sliderMax(50)
        .build()
    );

    private final Setting<LowDurabilityAction> lowDurabilityAction = sgGeneral.add(new EnumSetting.Builder<LowDurabilityAction>()
        .name("low-durability-action")
        .description("Action to take when elytra durability is below threshold.")
        .defaultValue(LowDurabilityAction.Nothing)
        .build()
    );

    // Webhook Settings
    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL for notifications.")
        .defaultValue("")
        .visible(() -> lowDurabilityAction.get() == LowDurabilityAction.SendWebhook ||
            lowDurabilityAction.get() == LowDurabilityAction.DisconnectAndWebhook)
        .build()
    );

    private final Setting<Boolean> selfPing = sgWebhook.add(new BoolSetting.Builder()
        .name("self-ping")
        .description("Ping yourself in webhook notifications.")
        .defaultValue(false)
        .visible(() -> lowDurabilityAction.get() == LowDurabilityAction.SendWebhook ||
            lowDurabilityAction.get() == LowDurabilityAction.DisconnectAndWebhook)
        .build()
    );

    private final Setting<String> discordId = sgWebhook.add(new StringSetting.Builder()
        .name("discord-id")
        .description("Your Discord user ID for pinging.")
        .defaultValue("")
        .visible(() -> selfPing.get() && (lowDurabilityAction.get() == LowDurabilityAction.SendWebhook ||
            lowDurabilityAction.get() == LowDurabilityAction.DisconnectAndWebhook))
        .build()
    );

    private long lastFireworkTime = 0;
    private boolean lowDurabilityTriggered = false;
    private boolean wasFlying = false; // Track if we were flying in previous tick
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public enum LowDurabilityAction {
        Nothing,
        Disconnect,
        SendWebhook,
        DisconnectAndWebhook
    }

    public AutoFirework() {
        super(GlazedAddon.CATEGORY, "AutoFirework", "Automatically uses fireworks when flying with elytra and handles low durability.");
    }

    @Override
    public void onActivate() {
        lastFireworkTime = 0;
        lowDurabilityTriggered = false;
        wasFlying = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Check if player is flying with elytra
        ItemStack chestplate = VersionUtil.getArmorStackByType(mc.player, 2); // Chest slot
        boolean hasElytra = chestplate.getItem() == Items.ELYTRA;

        // Check if player is elytra flying (not on ground, has elytra, and moving)
        boolean isFlying = hasElytra && !mc.player.isOnGround() &&
            (mc.player.getVelocity().lengthSquared() > 0.1 || mc.player.getVelocity().y < -0.1);

        if (!isFlying) {
            lowDurabilityTriggered = false; // Reset when not flying
            wasFlying = false;
            return;
        }

        // If we just started flying, use firework immediately
        if (!wasFlying) {
            useFirework();
            lastFireworkTime = System.currentTimeMillis();
            wasFlying = true;
            return;
        }

        // Check elytra durability
        if (chestplate.getItem() == Items.ELYTRA) {
            double durabilityPercent = ((double) (chestplate.getMaxDamage() - chestplate.getDamage()) / chestplate.getMaxDamage()) * 100;

            if (durabilityPercent <= durabilityThreshold.get() && !lowDurabilityTriggered) {
                handleLowDurability(durabilityPercent);
                lowDurabilityTriggered = true;
                return;
            }
        }

        // Use firework if delay has passed
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFireworkTime >= fireworkDelay.get() * 1000) {
            useFirework();
            lastFireworkTime = currentTime;
        }
    }

    private void useFirework() {
        int fireworkSlot = -1;

        // Find firework in inventory
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.FIREWORK_ROCKET) {
                fireworkSlot = i;
                break;
            }
        }

        if (fireworkSlot == -1) {
            warning("No firework rockets found in inventory!");
            return;
        }

        int currentSlot = VersionUtil.getSelectedSlot(mc.player);
        int hotbarIndex = 36 + currentSlot;

        if (fireworkSlot >= 0 && fireworkSlot <= 8) {
            // Firework is already in hotbar
            VersionUtil.setSelectedSlot(mc.player, fireworkSlot);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            VersionUtil.setSelectedSlot(mc.player, currentSlot);
        } else {
            // Firework is in inventory, need to swap
            int invSlot = fireworkSlot;

            // Swap firework to hotbar
            mc.interactionManager.clickSlot(0, invSlot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(0, hotbarIndex, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(0, invSlot, 0, SlotActionType.PICKUP, mc.player);

            // Use firework
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);

            // Swap back
            mc.interactionManager.clickSlot(0, invSlot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(0, hotbarIndex, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(0, invSlot, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private void handleLowDurability(double durabilityPercent) {
        //warning(String.format("Elytra durability is low! (%.1f%%)", durabilityPercent));

        switch (lowDurabilityAction.get()) {
            case Disconnect:
                toggle();
                mc.world.disconnect();
                break;
            case SendWebhook:
                sendWebhookNotification(durabilityPercent);
                break;
            case DisconnectAndWebhook:
                sendWebhookNotification(durabilityPercent);
                toggle();
                mc.world.disconnect();
                break;
            case Nothing:
            default:
                break;
        }
    }

    private void sendWebhookNotification(double durabilityPercent) {
        String url = webhookUrl.get().trim();
        if (url.isEmpty()) {
            warning("Webhook URL not configured!");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String serverInfo = mc.getCurrentServerEntry() != null ?
                    mc.getCurrentServerEntry().address : "Single Player";

                String messageContent = "";
                if (selfPing.get() && !discordId.get().trim().isEmpty()) {
                    messageContent = String.format("<@%s>", discordId.get().trim());
                }

                String description = String.format("Your elytra durability is critically low at %.1f%%!", durabilityPercent);

                String jsonPayload = String.format(
                    "{\"content\":\"%s\"," +
                        "\"username\":\"AutoFirework\"," +
                        "\"avatar_url\":\"https://i.imgur.com/OL2y1cr.png\"," +
                        "\"embeds\":[{" +
                        "\"title\":\"⚠️ LOW DURABILITY\"," +
                        "\"description\":\"%s\"," +
                        "\"color\":%d," +
                        "\"fields\":[" +
                        "{\"name\":\"Current Durability\",\"value\":\"%.1f%%\",\"inline\":true}," +
                        "{\"name\":\"Server\",\"value\":\"%s\",\"inline\":true}," +
                        "{\"name\":\"Time\",\"value\":\"<t:%d:R>\",\"inline\":true}" +
                        "]," +
                        "\"footer\":{\"text\":\"AutoFirework Alert\"}" +
                        "}]}",
                    messageContent.replace("\"", "\\\""),
                    description.replace("\"", "\\\""),
                    16711680, // Red color
                    durabilityPercent,
                    serverInfo.replace("\"", "\\\""),
                    System.currentTimeMillis() / 1000
                );

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(30))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 204) {
                    info("Low durability webhook notification sent successfully");
                } else {
                    error("Webhook failed with status: " + response.statusCode());
                }

            } catch (IOException | InterruptedException e) {
                error("Failed to send webhook: " + e.getMessage());
            }
        });
    }
}
