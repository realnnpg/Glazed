package com.nnpg.glazed.modules;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import meteordevelopment.meteorclient.mixin.HandledScreenAccessor;


public class HoverTotem extends Module {
    private final Setting<Integer> delay = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks before equipping the totem after hovering it.")
        .defaultValue(5)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private int ticksRemaining = -1;
    private boolean equipScheduled = false;

    public HoverTotem() {
        super(GlazedAddon.CATEGORY, "hover-totem", "Equips a Totem when hovered in inventory.");
    }
/**
    @Override
    public void onActivate() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @Override
    public void onDeactivate() {
        MeteorClient.EVENT_BUS.unsubscribe(this);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive()) return;

        if (mc.currentScreen instanceof HandledScreen<?> screen) {
            double mouseX = mc.mouse.getX() * mc.getWindow().getScaledWidth() / mc.getWindow().getWidth();
            double mouseY = mc.mouse.getY() * mc.getWindow().getScaledHeight() / mc.getWindow().getHeight();

            Slot hoveredSlot = ((HandledScreenAccessor) screen).callGetSlotAt(mouseX, mouseY);
            if (hoveredSlot != null) {
                ItemStack stack = hoveredSlot.getStack();
                if (stack.getItem() == Items.TOTEM_OF_UNDYING && !equipScheduled) {
                    ticksRemaining = delay.get();
                    equipScheduled = true;
                }
            }
        }

        if (equipScheduled) {
            ticksRemaining--;
            if (ticksRemaining <= 0) {
                equipScheduled = false;
                if (mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                    var totem = InvUtils.find(Items.TOTEM_OF_UNDYING);
                    if (totem.found()) {
                        InvUtils.move().from(totem.slot()).toOffhand();
                        info("Equipped totem from hovered slot.");
                    }
                }
            }
        }
    }
    **/
}
