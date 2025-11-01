package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
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

public class StunMace extends Module {
    private final SettingGroup sg;

    private final Setting<Integer> axeSlot;
    private final Setting<Integer> maceSlot;
    private final Setting<Integer> fallDistance;
    private final Setting<Integer> returnSlot;
    private final Setting<Boolean> ignoreShield;

    private final MinecraftClient mc;

    private boolean comboActive = false;
    private PlayerEntity currentTarget = null;

    public StunMace() {
        super(GlazedAddon.pvp, "stunmace", "Hit axe + mace and return to slot all in one tick.");

        sg = settings.createGroup("Settings");

        axeSlot = sg.add(new IntSetting.Builder()
            .name("axe-slot")
            .description("Hotbar slot (0–8) containing your axe.")
            .defaultValue(1)
            .min(0)
            .max(8)
            .build()
        );

        maceSlot = sg.add(new IntSetting.Builder()
            .name("mace-slot")
            .description("Hotbar slot (0–8) containing your mace.")
            .defaultValue(2)
            .min(0)
            .max(8)
            .build()
        );

        fallDistance = sg.add(new IntSetting.Builder()
            .name("fall-distance")
            .description("Minimum fall distance to trigger the combo.")
            .defaultValue(2)
            .min(1)
            .max(10)
            .build()
        );

        returnSlot = sg.add(new IntSetting.Builder()
            .name("return-slot")
            .description("Hotbar slot to return to after combo (0–8).")
            .defaultValue(0)
            .min(0)
            .max(8)
            .build()
        );

        ignoreShield = sg.add(new BoolSetting.Builder()
            .name("ignore-shield")
            .description("Perform the combo on any player, not just those blocking with a shield.")
            .defaultValue(false)
            .build()
        );

        mc = MinecraftClient.getInstance();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        ClientPlayerEntity player = mc.player;

        // Reset combo if grounded
        if (player.isOnGround() && comboActive) {
            comboActive = false;
            currentTarget = null;
            return;
        }

        // Combo trigger logic
        if (!comboActive && player.fallDistance >= fallDistance.get()) {
            PlayerEntity target = getTarget();
            if (target != null && (ignoreShield.get() || target.isBlocking())) {
                comboActive = true;
                currentTarget = target;

                // Axe hit
                player.getInventory().setSelectedSlot(axeSlot.get());
                mc.interactionManager.attackEntity(player, target);
                player.swingHand(Hand.MAIN_HAND);

                // Mace hit
                player.getInventory().setSelectedSlot(maceSlot.get());
                mc.interactionManager.attackEntity(player, target);
                player.swingHand(Hand.MAIN_HAND);

                // Return to chosen slot immediately (same tick)
                player.getInventory().setSelectedSlot(returnSlot.get());

                comboActive = false;
                currentTarget = null;
            }
        }
    }

    private PlayerEntity getTarget() {
        Entity targeted = mc.targetedEntity;
        if (targeted instanceof PlayerEntity player) {
            if (Friends.get().isFriend(player)) return null;
            return player;
        }
        return null;
    }
}
