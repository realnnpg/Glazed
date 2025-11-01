package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BreachSwap extends Module {
    private static final Logger LOGGER = LogManager.getLogger();

    private final SettingGroup sg;

    private final Setting<Integer> maceSlot;
    private final Setting<Integer> returnSlot;
    private final Setting<Integer> comboCooldown;

    private final MinecraftClient mc;

    private boolean comboActive = false;
    private long lastComboTime = 0;  // Store the last time combo was triggered

    public BreachSwap() {
        super(GlazedAddon.pvp, "breachswap", "Use mace slot and return to slot all in one tick.");

        sg = settings.createGroup("Settings");

        maceSlot = sg.add(new IntSetting.Builder()
            .name("mace-slot")
            .description("Hotbar slot (0–8) containing your mace.")
            .defaultValue(2)
            .min(0)
            .max(8)
            .build()
        );

        returnSlot = sg.add(new IntSetting.Builder()
            .name("return-slot")
            .description("Hotbar slot to return to after the combo (0–8).")
            .defaultValue(0)
            .min(0)
            .max(8)
            .build()
        );

        comboCooldown = sg.add(new IntSetting.Builder()
            .name("combo-cooldown")
            .description("Cooldown time in milliseconds before the combo can be triggered again.")
            .defaultValue(1000) // 1 second cooldown by default
            .min(0)
            .max(5000)  // Max cooldown of 5 seconds
            .build()
        );

        mc = MinecraftClient.getInstance();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        ClientPlayerEntity player = mc.player;

        // Check if combo is on cooldown
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastComboTime < comboCooldown.get()) {
            return;  // If cooldown hasn't passed, exit early and don't trigger combo
        }

        // Reset combo if grounded
        if (player.isOnGround() && comboActive) {
            comboActive = false;
            return;
        }

        // Ensure the combo is not spammed, trigger only once per cooldown
        if (!comboActive) {
            PlayerEntity target = getTarget();
            if (target != null) {
                LOGGER.info("Target found: " + target.getName().getString()); // Log the target

                // Trigger combo
                comboActive = true;
                lastComboTime = currentTime; // Update the last combo time

                // Mace hit
                LOGGER.info("Switching to mace slot: " + maceSlot.get()); // Log slot switch
                player.getInventory().setSelectedSlot(maceSlot.get());
                mc.interactionManager.attackEntity(player, target);
                player.swingHand(Hand.MAIN_HAND);

                // Return to the return slot immediately (same tick)
                LOGGER.info("Returning to slot: " + returnSlot.get()); // Log return slot
                player.getInventory().setSelectedSlot(returnSlot.get());

                // Combo complete
                comboActive = false;
            }
        }
    }

    private PlayerEntity getTarget() {
        Entity targeted = mc.targetedEntity;
        if (targeted instanceof PlayerEntity player) {
            if (Friends.get().isFriend(player)) {
                LOGGER.info("Target is a friend, ignoring: " + player.getName().getString()); // Log friend check
                return null;
            }
            return player;
        }
        return null;
    }
}
