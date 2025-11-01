package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class QuickShieldBreaker extends Module {
    private final SettingGroup sg = settings.createGroup("Settings");

    private final Setting<Integer> axeSlot = sg.add(new IntSetting.Builder()
        .name("axe-slot")
        .description("Hotbar slot (0-8) containing your axe.")
        .defaultValue(1)
        .min(0).max(8)
        .build()
    );

    private final Setting<Integer> swordSlot = sg.add(new IntSetting.Builder()
        .name("sword-slot")
        .description("Hotbar slot (0-8) to return to after breaking shield.")
        .defaultValue(0)
        .min(0).max(8)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sg.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("If enabled, will not target players marked as friends in Meteor.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stealthMode = sg.add(new BoolSetting.Builder()
        .name("stealth-mode")
        .description("Randomizes timings to mimic vanilla behavior and avoid anti-cheat detection.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> swapDelay = sg.add(new IntSetting.Builder()
        .name("swap-delay")
        .description("Delay in ticks before switching slots (1-10).")
        .defaultValue(2)
        .min(1).max(10)
        .build()
    );

    private final Setting<Integer> maxAttacks = sg.add(new IntSetting.Builder()
        .name("max-attacks")
        .description("Maximum attacks per cycle to avoid anti-cheat flags (1-5).")
        .defaultValue(2)
        .min(1).max(5)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private boolean swappedToAxe = false;
    private int swapCounter = 0;
    private int attackCounter = 0;
    private int returnDelay = 0;

    public QuickShieldBreaker() {
        super(GlazedAddon.pvp, "quick-shield-breaker", "Fast vanilla-style shield breaker with axe swap and auto-attacks, updated for Grim AC v3.");
    }

    @Override
    public void onActivate() {
        resetState();
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    private void resetState() {
        swappedToAxe = false;
        swapCounter = 0;
        attackCounter = 0;
        returnDelay = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        PlayerEntity target = getBlockingTarget();

        // Swap to axe when a blocking target is found
        if (target != null && shouldAttack(target) && !swappedToAxe) {
            if (swapCounter < getRandomizedDelay(swapDelay.get())) {
                swapCounter++;
                return;
            }
            swapCounter = 0;
            if (isAxe(mc.player.getInventory().getStack(axeSlot.get()))) {
                InvUtils.swap(axeSlot.get(), false);
                swappedToAxe = true;
                returnDelay = getRandomizedDelay(4); // Base 4 ticks to swap back
            }
        }

        if (swappedToAxe && isAxe(mc.player.getMainHandStack())) {
            if (target != null && shouldAttack(target)) {
                if (attackCounter < maxAttacks.get()) {
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(mc.player.getActiveHand());
                    attackCounter++;
                }
            }

            if (returnDelay > 0) {
                returnDelay--;
            } else {
                if (swapCounter < getRandomizedDelay(swapDelay.get())) {
                    swapCounter++;
                    return;
                }
                swapCounter = 0;
                InvUtils.swap(swordSlot.get(), false);
                swappedToAxe = false;
                attackCounter = 0;
            }
        }
    }

    private PlayerEntity getBlockingTarget() {
        if (mc.targetedEntity instanceof PlayerEntity target) {
            if (isBlockingWithShield(target)) return target;
        }
        return null;
    }

    private boolean isBlockingWithShield(LivingEntity entity) {
        ItemStack main = entity.getMainHandStack();
        ItemStack off = entity.getOffHandStack();
        return entity.isBlocking() && (main.isOf(Items.SHIELD) || off.isOf(Items.SHIELD));
    }

    private boolean isAxe(ItemStack stack) {
        return stack.isOf(Items.NETHERITE_AXE) ||
               stack.isOf(Items.DIAMOND_AXE) ||
               stack.isOf(Items.IRON_AXE) ||
               stack.isOf(Items.STONE_AXE) ||
               stack.isOf(Items.WOODEN_AXE) ||
               stack.isOf(Items.GOLDEN_AXE);
    }

    private boolean shouldAttack(PlayerEntity target) {
        return !ignoreFriends.get() || !Friends.get().isFriend(target);
    }

    private int getRandomizedDelay(int baseDelay) {
        if (stealthMode.get()) {
            // Randomize delay by ±25% to mimic vanilla timing
            double variation = baseDelay * 0.25 * (Math.random() * 2 - 1);
            return Math.max(1, baseDelay + (int) variation);
        }
        return baseDelay;
    }
}
