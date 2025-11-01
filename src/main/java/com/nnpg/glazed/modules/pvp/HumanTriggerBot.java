package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class HumanTriggerBot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBypass = settings.createGroup("Grim Bypass");

    // General Settings
    private final Setting<CombatMode> mode = sgGeneral.add(new EnumSetting.Builder<CombatMode>()
        .name("combat-mode")
        .description("Choose between 1.8 spam mode or 1.9+ cooldown mode.")
        .defaultValue(CombatMode.V1_9_PLUS)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("attack-delay")
        .description("Delay between attacks in ticks (only for 1.8 mode or mace override).")
        .defaultValue(4)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> onlyPlayers = sgGeneral.add(new BoolSetting.Builder()
        .name("only-players")
        .description("Only attack players, not mobs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Won't attack Meteor friends.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useSwords = sgGeneral.add(new BoolSetting.Builder()
        .name("swords")
        .description("Use swords in the triggerbot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useAxes = sgGeneral.add(new BoolSetting.Builder()
        .name("axes")
        .description("Use axes in the triggerbot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useMaces = sgGeneral.add(new BoolSetting.Builder()
        .name("maces")
        .description("Use maces in the triggerbot.")
        .defaultValue(true)
        .build()
    );

    // Chat feedback option
    private final Setting<Boolean> showChatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("show-chat-feedback")
        .description("Toggle chat messages when activating, deactivating, or attacking.")
        .defaultValue(true)
        .build()
    );

    // Grim Bypass Settings
    private final Setting<Boolean> randomizeDelay = sgBypass.add(new BoolSetting.Builder()
        .name("randomize-delay")
        .description("Add random variation to attack delay to bypass Grim AC v3.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> randomDelayRange = sgBypass.add(new IntSetting.Builder()
        .name("random-delay-range")
        .description("Maximum random variation in ticks for attack delay.")
        .defaultValue(2)
        .min(0)
        .sliderMax(5)
        .visible(randomizeDelay::get)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();
    private static final double REACH = 3.0;
    private int ticksSinceLastHit;

    public HumanTriggerBot() {
        super(GlazedAddon.pvp, "human-triggerbot", "Automatically attacks entities within 3 blocks, with Grim AC v3 bypass.");
        ticksSinceLastHit = 0;
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            if (showChatFeedback.get()) ChatUtils.error("Cannot activate HumanTriggerBot: Player or world is null!");
            toggle();
            return;
        }
        ticksSinceLastHit = 0;
        if (showChatFeedback.get()) ChatUtils.info("HumanTriggerBot activated. Reach: " + REACH + " blocks.");
    }

    @Override
    public void onDeactivate() {
        ticksSinceLastHit = 0;
        if (showChatFeedback.get()) ChatUtils.info("HumanTriggerBot deactivated.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        ticksSinceLastHit++;

        List<Entity> targets = mc.world.getEntitiesByClass(LivingEntity.class, mc.player.getBoundingBox().expand(REACH), entity -> {
                if (!(entity instanceof LivingEntity living) || !living.isAlive() || entity == mc.player) return false;
                if (onlyPlayers.get() && !(entity instanceof PlayerEntity)) return false;
                if (ignoreFriends.get() && entity instanceof PlayerEntity player && Friends.get().isFriend(player)) return false;
                return distanceToPlayer(entity) <= REACH;
            }).stream()
            .sorted(Comparator.comparingDouble(this::distanceToPlayer))
            .limit(1)
            .collect(Collectors.toList());

        if (targets.isEmpty()) return;

        Entity target = targets.get(0);
        ItemStack stack = mc.player.getMainHandStack();

        if (!isHoldingValidWeapon(stack)) return;

        boolean isMace = stack.getItem() == Items.MACE;
        int effectiveDelay = delay.get();
        if (randomizeDelay.get()) effectiveDelay += random.nextInt(randomDelayRange.get() + 1);

        if (mode.get() == CombatMode.V1_9_PLUS && !isMace) {
            if (mc.player.getAttackCooldownProgress(0.0f) >= 1.0f) {
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                ticksSinceLastHit = 0;
                if (showChatFeedback.get()) ChatUtils.info("Attacked " + target.getName().getString() + " (1.9+ mode).");
            }
        } else {
            if (ticksSinceLastHit >= effectiveDelay) {
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                ticksSinceLastHit = 0;
                if (showChatFeedback.get()) ChatUtils.info("Attacked " + target.getName().getString() + " (1.8 mode or mace).");
            }
        }
    }

    private double distanceToPlayer(Entity target) {
        Box box = target.getBoundingBox();
        Vec3d playerPos = mc.player.getEyePos();
        double x = Math.max(box.minX, Math.min(playerPos.x, box.maxX));
        double y = Math.max(box.minY, Math.min(playerPos.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(playerPos.z, box.maxZ));
        return playerPos.distanceTo(new Vec3d(x, y, z));
    }

    private boolean isHoldingValidWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String name = stack.getItem().toString().toLowerCase();
        if (useSwords.get() && name.contains("sword")) return true;
        if (useAxes.get() && name.contains("axe")) return true;
        return useMaces.get() && stack.getItem() == Items.MACE;
    }

    public enum CombatMode {
        V1_8,
        V1_9_PLUS
    }
}
