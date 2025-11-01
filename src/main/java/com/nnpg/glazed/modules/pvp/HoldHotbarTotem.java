package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

public class HoldHotbarTotem extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private boolean wasHoldingTotem = true;

    public HoldHotbarTotem() {
        super(GlazedAddon.pvp, "hold-hotbar-totem", "After pop, switches held slot to first hotbar totem.");
    }

    @Override
    public void onActivate() {
        wasHoldingTotem = mc.player != null && mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null) return;

        boolean holdingNow = mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);

        if (wasHoldingTotem && !holdingNow) {
            int slot = findHotbarTotem();
            if (slot != -1) {
                mc.player.getInventory().setSelectedSlot(slot);
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }
        }

        wasHoldingTotem = holdingNow;
    }

    private int findHotbarTotem() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }
}
