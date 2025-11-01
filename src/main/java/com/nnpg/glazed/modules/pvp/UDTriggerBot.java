package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Hand;

public class UDTriggerBot extends Module {
    private final SettingGroup sg = settings.createGroup("Settings");

    private final Setting<Boolean> ignoreFriends = sg.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("If enabled, will not target players marked as friends in Meteor.")
        .defaultValue(true)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final double REACH = 3.0;

    public UDTriggerBot() {
        super(GlazedAddon.pvp, "ud-triggerbot", "Legit triggerbot that attacks crosshair target if valid.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.crosshairTarget == null) return;
        if (mc.currentScreen != null) return;
        if (!isHoldingWeapon()) return;

        if (mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) mc.crosshairTarget;
            Entity entity = entityHit.getEntity();
            if (!(entity instanceof LivingEntity target)) return;
            if (!isValidTarget(target)) return;

            if (mc.player.getAttackCooldownProgress(0) >= 1.0f) {
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private boolean isHoldingWeapon() {
        Item item = mc.player.getMainHandStack().getItem();
        return item == Items.NETHERITE_SWORD || item == Items.DIAMOND_SWORD || item == Items.IRON_SWORD ||
               item == Items.STONE_SWORD || item == Items.WOODEN_SWORD ||
               item == Items.NETHERITE_AXE || item == Items.DIAMOND_AXE || item == Items.IRON_AXE ||
               item == Items.STONE_AXE || item == Items.WOODEN_AXE ||
               item == Items.MACE;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == mc.player || entity.isDead() || entity.getHealth() <= 0.0f || entity.isInvisible()) return false;
        if (ignoreFriends.get() && entity instanceof PlayerEntity player && Friends.get().isFriend(player)) return false;
        double distance = distanceToPlayer(entity);
        return distance <= REACH;
    }

    private double distanceToPlayer(Entity target) {
        Box box = target.getBoundingBox();
        Vec3d playerPos = mc.player.getPos();
        double x = Math.max(box.minX, Math.min(playerPos.x, box.maxX));
        double y = Math.max(box.minY, Math.min(playerPos.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(playerPos.z, box.maxZ));
        return playerPos.distanceTo(new Vec3d(x, y, z));
    }
}
