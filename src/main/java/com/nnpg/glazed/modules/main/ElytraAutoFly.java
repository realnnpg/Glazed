//from iwwi with permission
package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;


public class ElytraAutoFly extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> targetHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-height")
        .description("Y height to ascend to.")
        .defaultValue(150)
        .min(64)
        .sliderMax(300)
        .build());

    private final Setting<Integer> targetX = sgGeneral.add(new IntSetting.Builder()
        .name("target-x")
        .description("X coordinate to fly to.")
        .defaultValue(0)
        .build());

    private final Setting<Integer> targetZ = sgGeneral.add(new IntSetting.Builder()
        .name("target-z")
        .description("Z coordinate to fly to.")
        .defaultValue(0)
        .build());

    private final Setting<String> webhookUrl = sgGeneral.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL.")
        .defaultValue(" ")
        .build());

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private Stage stage = Stage.INIT;
    private boolean elytraStarted = false;
    private long lastFireworkTime = 0;
    private long lookUpUntil = 0;
    private double lastAltitude = 0;
    private boolean lowDurabilityLanding = false;

    private enum Stage {
        INIT, ASCENDING, CRUISING, DESCENDING, LANDED
    }

    public ElytraAutoFly() {
        super(GlazedAddon.CATEGORY, "ElytraAutoFly", "Auto Elytra flight with airplane-style landing.");
    }

    @Override
    public void onActivate() {
        stage = Stage.INIT;
        elytraStarted = false;
        lookUpUntil = 0;
        lowDurabilityLanding = false;
    }

    @Override
    public void onDeactivate() {
        mc.options.forwardKey.setPressed(false);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        ClientPlayerEntity p = mc.player;

        switch (stage) {
            case INIT -> {
                for (int i = 0; i < 36; i++) {
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    if (stack.getItem() == Items.ELYTRA) {
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, i, net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
                        break;
                    }
                }
                stage = Stage.ASCENDING;
            }

            case ASCENDING -> {
                if (isElytraLowDurability()) {
                    emergencyLand();
                    return;
                }

                if (p.isOnGround()) {
                    p.jump();
                    return;
                }

                if (!elytraStarted) {
                    mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    elytraStarted = true;
                    mc.player.setPitch(-45f);
                    useFirework();
                    lastFireworkTime = mc.world.getTime();
                    lookUpUntil = mc.world.getTime() + 60;
                }

                if (mc.world.getTime() - lastFireworkTime > 60) {
                    mc.player.setPitch(-45f);
                    useFirework();
                    lastFireworkTime = mc.world.getTime();
                    lookUpUntil = mc.world.getTime() + 60;
                }

                if (p.getY() >= targetHeight.get()) {
                    stage = Stage.CRUISING;
                    lastAltitude = p.getY();
                    ChatUtils.info("Reached height. Cruising...");
                }
            }

            case CRUISING -> {
                if (isElytraLowDurability()) {
                    emergencyLand();
                    return;
                }

                double dx = targetX.get() - p.getX();
                double dz = targetZ.get() - p.getZ();
                double distance = Math.sqrt(dx * dx + dz * dz);

                if (mc.world.getTime() < lookUpUntil) {
                    mc.player.setPitch(-45f);
                } else {
                    float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float yaw = mc.player.getYaw();
                    mc.player.setYaw(yaw + (desiredYaw - yaw) * 0.1f);
                    mc.player.setPitch(0f);
                }

                if (distance > 10) {
                    mc.options.forwardKey.setPressed(true);
                } else {
                    mc.options.forwardKey.setPressed(false);
                    stage = Stage.DESCENDING;
                }

                // 🔁 Periodic height correction every 5 sec
                if (mc.world.getTime() % 100 == 0) {
                    double currentY = p.getY();
                    if (currentY < targetHeight.get() + 5) {
                        mc.player.setPitch(-45f);
                        useFirework();
                        lastFireworkTime = mc.world.getTime();
                        lookUpUntil = mc.world.getTime() + 60;
                        lastAltitude = currentY;
                    }
                }

                if (lastAltitude - p.getY() > 10) {
                    mc.player.setPitch(-45f);
                    useFirework();
                    lastFireworkTime = mc.world.getTime();
                    lookUpUntil = mc.world.getTime() + 60;
                    lastAltitude = p.getY();
                }
            }

            case DESCENDING -> {
                int chunkX = targetX.get() >> 4;
                int chunkZ = targetZ.get() >> 4;

                if (!mc.world.isChunkLoaded(chunkX, chunkZ)) {
                    mc.options.forwardKey.setPressed(false);
                    mc.player.setPitch(-10f);
                    return;
                }

                double dx = targetX.get() - p.getX();
                double dz = targetZ.get() - p.getZ();
                double distance = Math.sqrt(dx * dx + dz * dz);

                if (distance > 2) {
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    mc.player.setYaw(yaw);
                    mc.player.setPitch(-15f);
                    mc.options.forwardKey.setPressed(true);
                } else {
                    mc.options.forwardKey.setPressed(false);
                    mc.player.setPitch(10f);
                }

                if (p.isOnGround()) {
                    mc.options.forwardKey.setPressed(false);
                    if (stage != Stage.LANDED) {
                        stage = Stage.LANDED;
                        String reason = lowDurabilityLanding
                            ? "Logged out due to Elytra below 15% durability."
                            : "AutoFly landing complete.";
                        sendWebhook(reason);

                        mc.execute(() -> {
                            mc.getNetworkHandler().getConnection().disconnect(Text.literal(reason));
                            toggle();
                        });
                    }
                }
            }
        }
    }

    private void emergencyLand() {
        lowDurabilityLanding = true;
        stage = Stage.DESCENDING;
        sendWebhook("⚠️ Elytra durability below 15%! Emergency landing.");
        ChatUtils.warning("⚠️ Elytra durability below 15%! Emergency landing.");
    }

    private void useFirework() {
        ItemStack firework = mc.player.getStackInHand(Hand.MAIN_HAND);
        if (!(firework.getItem() instanceof FireworkRocketItem)) {
            if (!equipFirework()) return;
            firework = mc.player.getStackInHand(Hand.MAIN_HAND);
        }

        if (firework.getItem() instanceof FireworkRocketItem) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        }
    }

    private boolean equipFirework() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof FireworkRocketItem) {
                int selectedSlot = i < 9 ? i : 0;
                mc.player.getInventory().setSelectedSlot(selectedSlot);
                if (i >= 9) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i, selectedSlot, net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
                }
                return true;
            }
        }
        return false;
    }

    private boolean isElytraLowDurability() {
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (chest.getItem() == Items.ELYTRA && chest.isDamaged()) {
            int damage = chest.getDamage();
            int max = chest.getMaxDamage();
            return ((double)(max - damage) / max) <= 0.15;
        }
        return false;
    }

    private void sendWebhook(String msg) {
        new Thread(() -> {
            try {
                URL url = new URL(webhookUrl.get());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String payload = "{\"content\":\"" + msg + "\"}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                }
                conn.getInputStream().close();
            } catch (Exception ignored) {}
        }).start();
    }
}
