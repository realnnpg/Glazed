package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class ElytraFirework extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> fireworkSlot = sgGeneral.add(new IntSetting.Builder()
        .name("firework-slot")
        .description("Hotbar slot (0–8) containing fireworks.")
        .defaultValue(0)
        .min(0).max(8)
        .build()
    );

    private boolean wasPressed = false;
    private boolean hasFired = false;

    public ElytraFirework() {
        super(GlazedAddon.pvp, "elytra-firework", "Uses firework when gliding and right-clicks once per press.");
    }

    @Override
    public void onDeactivate() {
        if (mc.options != null) {
            KeyBinding.setKeyPressed(mc.options.useKey.getDefaultKey(), false);
        }
        wasPressed = false;
        hasFired = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.options == null) return;

        // Detect key press transition
        boolean keyPressed = mc.options.useKey.isPressed();
        if (keyPressed && !wasPressed) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        wasPressed = keyPressed;

        // Firework logic
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        boolean isWearingElytra = chest.isOf(Items.ELYTRA);

        if (mc.player.isGliding() && isWearingElytra && !hasFired) {
            ItemStack stack = mc.player.getInventory().getStack(fireworkSlot.get());
            if (stack.isOf(Items.FIREWORK_ROCKET)) {
                mc.player.getInventory().setSelectedSlot(fireworkSlot.get());
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                hasFired = true;
            }
        }

        if (!mc.player.isGliding()) hasFired = false;
    }
}
