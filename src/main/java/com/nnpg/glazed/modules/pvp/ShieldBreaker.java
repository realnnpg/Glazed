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
        .range(1, 9)
        .sliderRange(1, 9)
        .visible(() -> !returnToPrevSlot.get())
        .build()
    );
    
    private final Setting<Integer> attackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("attack-delay")
        .description("Delay in ticks between shield break and weapon switch")
        .defaultValue(10)
        .range(1, 40)
        .sliderRange(1, 20)
        .build()
    );
    
    private final Setting<Integer> killDelay = sgGeneral.add(new IntSetting.Builder()
        .name("kill-delay")  
        .description("Delay in ticks between weapon switch and kill attack")
        .defaultValue(8)
        .range(1, 40)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> axeSwitchDelay = sgGeneral.add(new IntSetting.Builder()
        .name("axe-switch-delay")
        .description("Delay in ticks to ensure axe switch is completed")
        .defaultValue(2)
        .range(1, 20)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> manualShieldBreakDelay = sgGeneral.add(new IntSetting.Builder()
        .name("manual-shield-break-delay")
        .description("Delay in ticks before switching back in manual mode")
        .defaultValue(2)
        .range(1, 20)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> weaponSwitchDelay = sgGeneral.add(new IntSetting.Builder()
        .name("weapon-switch-delay")
        .description("Delay in ticks to ensure weapon switch is completed")
        .defaultValue(3)
        .range(1, 20)
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
        .defaultValue(4.5)
        .range(1.0, 6.0)
        .sliderRange(1.0, 6.0)
        .build()
    );
    
    private final Setting<Boolean> chatInfo = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-info")
        .description("Send info messages to chat")
        .defaultValue(true)
        .build()
    );

    // State variables
    private PlayerEntity targetPlayer = null;
    private int originalSlot = -1;
    private int tickCounter = 0;
    private ShieldBreakerState state = ShieldBreakerState.IDLE;
    
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

        if (autoBreak.get()) {
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
                    originalSlot = mc.player.getInventory().getSelectedSlot();

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
                        originalSlot = mc.player.getInventory().getSelectedSlot();
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
            // Attack to break shield
            mc.interactionManager.attackEntity(mc.player, targetPlayer);
            mc.player.swingHand(Hand.MAIN_HAND);
            
            if (chatInfo.get()) info("Shield broken! Switching to weapon...");
            
            state = ShieldBreakerState.BREAKING;
            tickCounter = 0;
        }
    }

    private void handleShieldBreak() {
        tickCounter++;
        
        // For manual mode, use a shorter delay
        int delay = autoBreak.get() ? attackDelay.get() : manualShieldBreakDelay.get();
        
        // Wait for delay before switching back
        if (tickCounter >= delay) {
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
                // Auto mode behavior
                if (returnToPrevSlot.get()) {
                    if (originalSlot != -1) {
                        InvUtils.swap(originalSlot, false);
                    }
                } else {
                    int weaponSlotIndex = weaponSlot.get() - 1;
                    InvUtils.swap(weaponSlotIndex, false);
                }
                state = ShieldBreakerState.SWITCHING_BACK;
            }
            tickCounter = 0;
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
            // Verify target is still valid and in range
            if (targetPlayer != null && !targetPlayer.isRemoved() && 
                mc.player.distanceTo(targetPlayer) <= range.get()) {
                
                mc.interactionManager.attackEntity(mc.player, targetPlayer);
                mc.player.swingHand(Hand.MAIN_HAND);
                
                if (chatInfo.get()) info("Kill attack executed!");
            }
            
            // Reset to idle state
            resetState();
        }
    }

    private boolean isUsingShield(PlayerEntity player) {
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

    private void resetState() {
        targetPlayer = null;
        originalSlot = -1;
        tickCounter = 0;
        state = ShieldBreakerState.IDLE;
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