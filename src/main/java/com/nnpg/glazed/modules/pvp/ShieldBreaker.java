package com.nnpg.glazed.modules.pvp;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class ShieldBreaker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    
    // Settings
    private final Setting<Boolean> autoBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-break")
        .description("Automatically break shields without requiring clicks")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> returnToPrevSlot = sgGeneral.add(new BoolSetting.Builder()
        .name("return-to-prev-slot")
        .description("Return to the previous slot after breaking shield instead of a specific weapon slot")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> weaponSlot = sgGeneral.add(new IntSetting.Builder()
        .name("weapon-slot")
        .description("The hotbar slot to switch back to after breaking shield (1-9)")
        .defaultValue(1)
        .range(0, 9)
        .sliderRange(1, 9)
        .visible(() -> !returnToPrevSlot.get())
        .build()
    );
    
    private final Setting<Integer> attackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("attack-delay")
        .description("Delay in ticks between shield break and weapon switch")
        .defaultValue(0)
        .range(0, 40)
        .sliderRange(1, 20)
        .build()
    );
    
    private final Setting<Integer> killDelay = sgGeneral.add(new IntSetting.Builder()
        .name("kill-delay")  
        .description("Delay in ticks between weapon switch and kill attack")
        .defaultValue(1)
        .range(0, 40)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> axeSwitchDelay = sgGeneral.add(new IntSetting.Builder()
        .name("axe-switch-delay")
        .description("Delay in ticks to ensure axe switch is completed")
        .defaultValue(0)
        .range(0, 20)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> manualShieldBreakDelay = sgGeneral.add(new IntSetting.Builder()
        .name("manual-shield-break-delay")
        .description("Delay in ticks before switching back in manual mode")
        .defaultValue(1)
        .range(0, 20)
        .sliderRange(1, 10)
        .build()
    );

        private final Setting<Integer> cycleCooldown = sgGeneral.add(new IntSetting.Builder()
            .name("cycle-cooldown")
            .description("Delay in ticks before starting the next shield break cycle.")
            .defaultValue(4)
            .range(0, 20)
            .sliderRange(0, 10)
            .build()
        );

    private final Setting<Integer> weaponSwitchDelay = sgGeneral.add(new IntSetting.Builder()
        .name("weapon-switch-delay")
        .description("Delay in ticks to ensure weapon switch is completed")
        .defaultValue(0)
        .range(0, 20)
        .sliderRange(1, 10)
        .build()
    );
    
    private final Setting<Boolean> onlyPlayers = sgGeneral.add(new BoolSetting.Builder()
        .name("only-players")
        .description("Only break shields of players, not other entities")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum range to detect shield usage")
        .defaultValue(6.0)
        .range(0.0, 10.0)
        .sliderRange(1.0, 6.0)
        .build()
    );
    
    private final Setting<Boolean> chatInfo = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-info")
        .description("Send info messages to chat")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> killSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("kill-switch")
        .description("Enable auto attack after breaking shield")
        .defaultValue(true)
        .build()
    );

    // State variables
    private PlayerEntity targetPlayer = null;
    private int originalSlot = -1;
    private int tickCounter = 0;
    private ShieldBreakerState state = ShieldBreakerState.IDLE;
    private boolean shieldBroken = false;
    private long lastBreakAttempt = 0;

        private int cooldownTicks = 0;
    
    private enum ShieldBreakerState {
        IDLE,           // Waiting for shield detection
        SWITCHING_AXE,  // Switching to axe
        BREAKING,       // Breaking shield with axe
        SWITCHING_BACK, // Switching back to weapon
        KILLING         // Final kill attack
    }

    public ShieldBreaker() {
        super(GlazedAddon.pvp, "shield-breaker", "Automatically breaks player shields with axe then switches back to weapon for kill.");
    }

    @Override
    public void onActivate() {
        resetState();
        if (chatInfo.get()) info("Shield Breaker activated - aim at players using shields!");
    }

    @Override  
    public void onDeactivate() {
        resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Don't activate if player is eating or using shield
        if (mc.player.isUsingItem()) return;

        if (autoBreak.get()) {
                // Wait for cooldown before restarting cycle
                if (cooldownTicks > 0) {
                    cooldownTicks--;
                    return;
                }
            switch (state) {
                case IDLE -> checkForShieldUser();
                case SWITCHING_AXE -> handleAxeSwitch();
                case BREAKING -> handleShieldBreak();
                case SWITCHING_BACK -> handleWeaponSwitch();
                case KILLING -> handleKillAttack();
            }
        } else {
            // Manual mode - just check for shield users
            checkForShieldUser();
        }
    }

    private void checkForShieldUser() {
        // Check what we're looking at
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.ENTITY) {
            return;
        }

        EntityHitResult entityHit = (EntityHitResult) mc.crosshairTarget;
        
        // Only target players if setting enabled
        if (onlyPlayers.get() && !(entityHit.getEntity() instanceof PlayerEntity)) {
            return;
        }
        
        if (entityHit.getEntity() instanceof PlayerEntity player) {
            // Check if player is within range
            if (mc.player.distanceTo(player) > range.get()) {
                return;
            }
            
            // Check if player is using a shield
            if (isUsingShield(player)) {
                targetPlayer = player;
                boolean isAttacking = mc.options.attackKey.isPressed();

                if (!autoBreak.get() && isAttacking) {
                    // Manual mode - Store current slot FIRST before any swaps
                    // PlayerInventory no longer exposes getSelectedSlot(); use the selectedSlot field instead
                    originalSlot = mc.player.getInventory().selectedSlot;

                    // Find axe in hotbar
                    FindItemResult axeResult = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
                    
                    if (!axeResult.found()) {
                        if (chatInfo.get()) error("No axe found in hotbar!");
                        return;
                    }

                    if (chatInfo.get()) info("Shield detected! Breaking with axe");
                    
                    // Switch to axe, attack, and prepare to switch back
                    InvUtils.swap(axeResult.slot(), false);
                    mc.interactionManager.attackEntity(mc.player, player);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    
                    // Small delay before switching back (2 ticks)
                    tickCounter = 0;
                    state = ShieldBreakerState.BREAKING;

                } else if (autoBreak.get()) {
                    // Auto mode - use state machine
                    if (originalSlot == -1) {
                        // PlayerInventory no longer exposes getSelectedSlot(); use the selectedSlot field instead
                        originalSlot = mc.player.getInventory().selectedSlot;
                    }
                    
                    FindItemResult axeResult = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof AxeItem);
                    
                    if (!axeResult.found()) {
                        if (chatInfo.get()) error("No axe found in hotbar!");
                        return;
                    }

                    if (chatInfo.get()) info("Shield detected! Breaking with axe");
                    InvUtils.swap(axeResult.slot(), false);
                    state = ShieldBreakerState.SWITCHING_AXE;
                    tickCounter = 0;
                }
            }
        }
    }

    private void handleAxeSwitch() {
        tickCounter++;
        
        // Small delay to ensure switch completed
        if (tickCounter >= axeSwitchDelay.get()) {
            // Only attempt break if we haven't already broken the shield
            if (!shieldBroken) {
                // Reduced cooldown for faster response while preventing double breaks
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastBreakAttempt > 150) { // Reduced to 150ms cooldown
                    // Attack to break shield
                    mc.interactionManager.attackEntity(mc.player, targetPlayer);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    lastBreakAttempt = currentTime;
                    shieldBroken = true;
                    
                    if (chatInfo.get()) info("Shield broken! Switching to weapon...");
                }
            }
            
            state = ShieldBreakerState.BREAKING;
            tickCounter = 0;
        }
    }

    private void handleShieldBreak() {
        tickCounter++;
        
        // Immediately proceed if shield is broken
        if (shieldBroken) {
            // Always return to original slot in manual mode
            if (!autoBreak.get()) {
                if (originalSlot != -1) {
                    // Switch back to original slot
                    InvUtils.swap(originalSlot, false);
                    
                    // Attack with original weapon
                    if (targetPlayer != null && !targetPlayer.isRemoved() && mc.player.distanceTo(targetPlayer) <= range.get()) {
                        mc.interactionManager.attackEntity(mc.player, targetPlayer);
                        mc.player.swingHand(Hand.MAIN_HAND);
                        if (chatInfo.get()) info("Attacking with original weapon!");
                    }
                    resetState();
                    return;
                }
            } else {
                // Auto mode behavior - switch immediately after break
                if (returnToPrevSlot.get()) {
                    if (originalSlot != -1) {
                        InvUtils.swap(originalSlot, false);
                    }
                } else {
                    int weaponSlotIndex = weaponSlot.get() - 1;
                    InvUtils.swap(weaponSlotIndex, false);
                }
                state = ShieldBreakerState.SWITCHING_BACK;
                tickCounter = 0;
            }
        } else {
            // If shield isn't broken yet, wait a bit then reset if taking too long
            if (tickCounter >= 3) { // Reduced timeout for faster retry
                if (chatInfo.get()) error("Shield break failed, retrying...");
                state = ShieldBreakerState.IDLE;
                tickCounter = 0;
            }
        }
    }

    private void handleWeaponSwitch() {
        tickCounter++;
        
        // Small delay to ensure weapon switch completed
        if (tickCounter >= weaponSwitchDelay.get()) {
            state = ShieldBreakerState.KILLING;
            tickCounter = 0;
        }
    }

    private void handleKillAttack() {
        tickCounter++;
        
        // Wait for kill delay then attack
        if (tickCounter >= killDelay.get()) {
            // Only execute kill attack if kill switch is enabled
            if (killSwitch.get()) {
                // Simplified but safe target validation
                if (targetPlayer != null && !targetPlayer.isRemoved()) { // Removed redundant range check
                    mc.interactionManager.attackEntity(mc.player, targetPlayer);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    
                    if (chatInfo.get()) info("Kill attack executed!");
                }
            } else if (chatInfo.get()) {
                info("Shield broken - Kill switch disabled");
            }
            
            // Reset to idle state
            resetState();
            cooldownTicks = cycleCooldown.get();
        }
    }

    private boolean isUsingShield(PlayerEntity player) {
        // First check if we're behind the player
        if (isPlayerBehindTarget(mc.player, player)) {
            if (chatInfo.get()) info("Cannot break shield from behind!");
            return false;
        }

        // Check main hand
        ItemStack mainHand = player.getMainHandStack();
        if (mainHand.getItem() == Items.SHIELD && player.isUsingItem() && player.getActiveHand() == Hand.MAIN_HAND) {
            return true;
        }
        
        // Check offhand  
        ItemStack offHand = player.getOffHandStack();
        if (offHand.getItem() == Items.SHIELD && player.isUsingItem() && player.getActiveHand() == Hand.OFF_HAND) {
            return true;
        }
        
        return false;
    }

    private boolean isPlayerBehindTarget(PlayerEntity source, PlayerEntity target) {
        // Get the angle between the target's looking direction and the vector to the source
        double dx = source.getX() - target.getX();
        double dz = source.getZ() - target.getZ();
        
        // Calculate the angle between the target's looking direction and the vector to the source
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        
        // Normalize the target's yaw to 0-360
        float targetYaw = target.getYaw() % 360;
        if (targetYaw < 0) targetYaw += 360;
        
        // Normalize the calculated angle to 0-360
        if (angle < 0) angle += 360;
        
        // Calculate the absolute difference between angles
        double angleDiff = Math.abs(angle - targetYaw);
        
        // Consider "behind" if the angle difference is less than 90 degrees
        return angleDiff < 90 || angleDiff > 270;
    }

    private void resetState() {
        targetPlayer = null;
        originalSlot = -1;
        tickCounter = 0;
        state = ShieldBreakerState.IDLE;
        shieldBroken = false;
        lastBreakAttempt = 0;
            // cooldownTicks is set in handleKillAttack
    }

    @Override
    public String getInfoString() {
        return switch (state) {
            case IDLE -> null;
            case SWITCHING_AXE -> "Switching to Axe";
            case BREAKING -> "Breaking Shield";
            case SWITCHING_BACK -> "Switching to Weapon";
            case KILLING -> "Executing Kill";
        };
    }
}