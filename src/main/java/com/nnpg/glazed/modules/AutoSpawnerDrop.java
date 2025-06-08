package com.nnpg.glazed.modules;



import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.Hand;
import baritone.api.BaritoneAPI;

public class AutoSpawnerDrop extends Module {
    private boolean stage1Done = false;
    private int tickDelay = 0;


    public AutoSpawnerDrop() {
        super(GlazedAddon.CATEGORY, "AutoSpawnerDrop", "Drops automatically all items from a spawner.");
        MeteorClient.EVENT_BUS.subscribe(this);
    }
public void onActivate() {
    BaritoneAPI.getSettings().allowSprint.value = true;
}


    /**
 *
 *
    @Override
    public void onActivate() {
        if (mc.crosshairTarget instanceof BlockHitResult target) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, target);
            stage1Done = false;
            tickDelay = 0;
        } else {
            error("No block targeted.");
            toggle();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.currentScreen == null) return;

        tickDelay++;

        if (!stage1Done && tickDelay == 5) {
            // Click the chest slot (usually index 10, but may vary)
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 10, 0, SlotActionType.PICKUP, mc.player);
            stage1Done = true;
        }

        if (stage1Done && tickDelay >= 10) {
            var handler = mc.player.currentScreenHandler;

            for (int i = 0; i < handler.slots.size(); i++) {
                var slot = handler.getSlot(i);
                if (!slot.hasStack()) continue;

                var item = slot.getStack().getItem();

                if (item == Items.BONE) {
                    mc.interactionManager.clickSlot(handler.syncId, i, 1, SlotActionType.THROW, mc.player);
                }
            }

            toggle();  // Done
        }
    }
    **/
}
