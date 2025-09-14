package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Direction;

public class ShulkerDropper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between actions in ticks.")
        .defaultValue(1)
        .min(0)
        .max(20)
        .build()
    );

    private int delayCounter = 0;

    public ShulkerDropper() {
        super(GlazedAddon.CATEGORY, "ShulkerDropper", "Automatically buys shulkers from shop and drops them.");
    }

    @Override
    public void onActivate() {
        delayCounter = 0;
    }

    @Override
    public void onDeactivate() {
        delayCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        ScreenHandler currentScreenHandler = mc.player.currentScreenHandler;

        if (!(currentScreenHandler instanceof GenericContainerScreenHandler)) {
            ChatUtils.sendPlayerMsg("/shop");
            delayCounter = 20;
            return;
        }

        GenericContainerScreenHandler containerHandler = (GenericContainerScreenHandler) currentScreenHandler;

        if (containerHandler.getRows() != 3) return;

        if (currentScreenHandler.getSlot(11).getStack().isOf(Items.END_STONE) &&
            currentScreenHandler.getSlot(11).getStack().getCount() == 1) {
            mc.interactionManager.clickSlot(currentScreenHandler.syncId, 11, 0, SlotActionType.PICKUP, mc.player);
            delayCounter = 20;
            return;
        }

        if (currentScreenHandler.getSlot(17).getStack().isOf(Items.SHULKER_BOX)) {
            mc.interactionManager.clickSlot(currentScreenHandler.syncId, 17, 0, SlotActionType.PICKUP, mc.player);
            delayCounter = 20;
            return;
        }

        if (currentScreenHandler.getSlot(13).getStack().isOf(Items.SHULKER_BOX)) {
            mc.interactionManager.clickSlot(currentScreenHandler.syncId, 23, 0, SlotActionType.QUICK_MOVE, mc.player);
            delayCounter = delay.get();
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.DROP_ITEM,
                BlockPos.ORIGIN,
                Direction.DOWN
            ));
        }
    }
}
