package com.nnpg.glazed.modules;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.MeteorClient;
import com.nnpg.glazed.GlazedAddon;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.player.InvUtils;


public class AutoInvTotem extends Module {


    private final Setting<Integer> delay = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("delay")
        .description("How many ticks to wait before equipping the totem.")
        .defaultValue(5)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private boolean inventoryOpened = false;
    private int tickCounter = 0;

    public AutoInvTotem() {
        super(GlazedAddon.CATEGORY, "AutoInvTotem", "Automatically puts a Totem into your offhand when inventory opens");
    }

    @Override
    public void onActivate() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen instanceof InventoryScreen) {
            inventoryOpened = true;
            tickCounter = 0;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!inventoryOpened) return;

        tickCounter++;

        if (tickCounter >= delay.get()) {
            assert mc.player != null;
            if (mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                var totem = InvUtils.find(Items.TOTEM_OF_UNDYING);
                if (totem.found()) {
                    InvUtils.move().from(totem.slot()).toOffhand();
                }
            }
            inventoryOpened = false;
        }
    }

    @Override
    public void onDeactivate() {
        MeteorClient.EVENT_BUS.unsubscribe(this);
    }
}
