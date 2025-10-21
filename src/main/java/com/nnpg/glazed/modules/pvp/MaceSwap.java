/*Thank you to [DonKisser](https://github.com/DonKisser) for making this module for us!
Their inspiration was this Youtube video by @scilangaming:
https://www.youtube.com/watch?v=q99eqD_fBqo*/
/*Thanks Trouser Streak for the based code */
package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;

public class MaceSwap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> autoSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-swap-breach-mace")
        .description("Automatically finds and swaps to a breach mace.")
        .defaultValue(true)
        .build());
    private final Setting<Integer> targetSlot = sgGeneral.add(new IntSetting.Builder()
        .name("target-slot")
        .description("The hotbar slot to swap to when attacking.")
        .sliderRange(1, 9)
        .defaultValue(1)
        .min(1)
        .visible(() -> !autoSwap.get())
        .build());
    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Print debug messages in chat.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> checkWeapon = sgGeneral.add(new BoolSetting.Builder()
        .name("check-weapon")
        .description("Only activate when holding a sword or axe.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> allowSword = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-sword")
        .description("Allow activation when holding a sword.")
        .defaultValue(true)
        .visible(checkWeapon::get)
        .build());

    private final Setting<Boolean> allowAxe = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-axe")
        .description("Allow activation when holding an axe.")
        .defaultValue(true)
        .visible(checkWeapon::get)
        .build());

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Swap back to the original slot after a short delay.")
        .defaultValue(true)
        .build());
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder().name("swap-back-delay").description("Delay in ticks before swapping back to the previous slot.").sliderRange(1, 20).defaultValue(8).min(1).visible(swapBack::get).build());
    private int prevSlot = -1;
    private int dDelay = 0;
    public MaceSwap() {
        super(GlazedAddon.pvp, "mace-swap", "Swaps with the mace in the target slot on attack");
    }

    private int findBreachMace() {
        int bestSlot = -1;
        int highestLevel = 0;

        // Search through hotbar for items with Breach enchantment
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                var enchantInfo = stack.getEnchantments();
                String enchantString = enchantInfo.toString();
                if (debugMode.get()) info("Slot " + i + " enchants: " + enchantString);

                if (enchantString.contains("minecraft:breach")) {
                    try {
                        // Find the number after the =>
                        int levelStart = enchantString.lastIndexOf("=>");
                        if (levelStart != -1) {
                            String levelStr = enchantString.substring(levelStart + 2).replaceAll("[^0-9]", "");
                            int level = Integer.parseInt(levelStr);
                            if (debugMode.get()) info("Found breach level " + level + " in slot " + i);
                            if (level > highestLevel) {
                                highestLevel = level;
                                bestSlot = i;
                            }
                        }
                    } catch (Exception e) {
                        if (debugMode.get()) error("Error parsing level: " + e.getMessage());
                    }
                }
            }
        }
        if (bestSlot != -1) {
            if (debugMode.get()) info("Selected slot " + bestSlot + " with level " + highestLevel);
        } else {
            if (debugMode.get()) warning("No breach mace found");
        }
        return bestSlot;
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (mc.player == null || mc.world == null) return;

        // Check if we're holding a valid weapon when weapon checking is enabled
        if (checkWeapon.get()) {
            String heldItemId = mc.player.getMainHandStack().getItem().toString();
            boolean isSword = heldItemId.contains("sword");
            boolean isAxe = heldItemId.contains("_axe");
            
            if ((!allowSword.get() || !isSword) && (!allowAxe.get() || !isAxe)) {
                if (debugMode.get()) info("Not holding selected weapon type");
                return;
            }
        }
        
        if (swapBack.get()) {
            prevSlot = mc.player.getInventory().getSelectedSlot();
        }

        if (autoSwap.get()) {
            // Find breach mace in hotbar
            int breachMaceSlot = findBreachMace();
            if (breachMaceSlot != -1) {
                InvUtils.swap(breachMaceSlot, false);
            }
        } else {
            InvUtils.swap(targetSlot.get() - 1, false);
        }

        if (swapBack.get() && prevSlot != -1) {
            dDelay = delay.get();
        }
    }
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (dDelay > 0) {
            dDelay--;
            if (dDelay == 0 && prevSlot != -1) {
                InvUtils.swap(prevSlot, false);
                prevSlot = -1;
            }
        }
    }
}