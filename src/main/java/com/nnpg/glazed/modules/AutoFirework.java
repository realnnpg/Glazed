
package com.nnpg.glazed.modules;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;


public class AutoFirework extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> hotbarSlot = sgGeneral.add(new IntSetting.Builder()
        .name("hotbar-slot")
        .description("Which hotbar slot to look for fireworks (1-9).")
        .defaultValue(9)
        .min(1)
        .max(9)
        .build()
    );

    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("delay")
        .description("Delay between firework usage in seconds.")
        .defaultValue(1.5)
        .min(0.1)
        .max(10.0)
        .sliderMin(0.1)
        .sliderMax(5.0)
        .build()
    );


    private final Setting<Boolean> checkDurability = sgGeneral.add(new BoolSetting.Builder()
        .name("check-durability")
        .description("Stop using fireworks when elytra durability is low.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minDurability = sgGeneral.add(new IntSetting.Builder()
        .name("min-durability")
        .description("Minimum elytra durability before stopping firework usage.")
        .defaultValue(10)
        .min(1)
        .max(100)
        .visible(checkDurability::get)
        .build()
    );

    private long lastFireworkTime = 0;

    public AutoFirework() {
        super(GlazedAddon.CATEGORY, "AutoFirework", "Automatically uses fireworks for elytra flying.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        ClientPlayerEntity player = mc.player;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFireworkTime < delay.get() * 1000) return;


        if (checkDurability.get()) {
            ItemStack chestItem = player.getInventory().getArmorStack(2);
            if (!chestItem.isEmpty() && chestItem.isDamaged()) {
                int durability = chestItem.getMaxDamage() - chestItem.getDamage();
                if (durability <= minDurability.get()) return;
            }
        }

        int slotIndex = hotbarSlot.get() - 1;
        ItemStack fireworkStack = player.getInventory().getStack(slotIndex);

        if (fireworkStack.isEmpty() || !(fireworkStack.getItem() instanceof FireworkRocketItem)) {
            return;
        }

        int originalSlot = player.getInventory().selectedSlot;

        player.getInventory().selectedSlot = slotIndex;

        mc.interactionManager.interactItem(player, Hand.MAIN_HAND);

        player.getInventory().selectedSlot = originalSlot;

        lastFireworkTime = currentTime;
    }

    @Override
    public void onActivate() {
        lastFireworkTime = 0;
    }
}
