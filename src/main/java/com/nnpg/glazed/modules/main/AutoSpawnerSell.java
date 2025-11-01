package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;

public class AutoSpawnerSell extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> dropDelay = sgGeneral.add(new IntSetting.Builder()
        .name("drop-delay")
        .description("Delay in ticks between drops")
        .defaultValue(20)
        .min(0)
        .max(2400)
        .build()
    );

    private final Setting<Integer> pageAmount = sgGeneral.add(new IntSetting.Builder()
        .name("page-amount")
        .description("Number of pages to drop before selling")
        .defaultValue(2)
        .min(1)
        .max(10)
        .build()
    );

    private final Setting<Integer> pageSwitchDelay = sgGeneral.add(new IntSetting.Builder()
        .name("page-switch-delay")
        .description("Delay between page switches (ticks)")
        .defaultValue(80)
        .min(0)
        .max(7200)
        .build()
    );

    private int delayCounter = 0;
    private int pageCounter = 0;
    private boolean isProcessing = false;
    private boolean isSelling = false;
    private boolean isPageSwitching = false;

    public AutoSpawnerSell() {
        super(GlazedAddon.CATEGORY, "auto-spawner-sell", "Automatically drops bones from spawner and sells them");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        ScreenHandler handler = mc.player.currentScreenHandler;

        if (pageCounter >= pageAmount.get()) {
            isSelling = true;
            pageCounter = 0;
            delayCounter = 40;
            return;
        }

        if (isSelling) {
            handleSelling(handler);
        } else {
            handleDropping(handler);
        }
    }

    private void handleSelling(ScreenHandler handler) {
        if (!(handler instanceof GenericContainerScreenHandler)) {
            mc.getNetworkHandler().sendChatCommand("order " + getOrderCommand());
            delayCounter = 20;
            return;
        }

        GenericContainerScreenHandler container = (GenericContainerScreenHandler) handler;

        // Example 6-row shop container
        if (container.getRows() == 6) {
            ItemStack stack = container.getSlot(47).getStack();
            if (stack.isEmpty()) {
                delayCounter = 2;
                mc.player.closeHandledScreen();
                return;
            }

            mc.interactionManager.clickSlot(handler.syncId, 47, 1, SlotActionType.QUICK_MOVE, mc.player);
            delayCounter = 5;
            return;
        }

        // Default fallback
        mc.player.closeHandledScreen();
        delayCounter = 20;
    }

    private void handleDropping(ScreenHandler handler) {
        if (!(handler instanceof GenericContainerScreenHandler)) {
            // Right-click to start dropping
            KeyBinding.onKeyPressed(InputUtil.Type.MOUSE.createFromCode(1));
            delayCounter = 20;
            return;
        }

        DefaultedList<ItemStack> stacks = handler.getStacks();
        boolean allBones = true;

        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && stack.getItem() != Items.BONE) {
                allBones = false;
                break;
            }
        }

        if (allBones) {
            mc.interactionManager.clickSlot(handler.syncId, 52, 1, SlotActionType.THROW, mc.player);
            isProcessing = true;
            delayCounter = pageSwitchDelay.get();
            pageCounter++;
        } else if (isProcessing) {
            isProcessing = false;
            mc.interactionManager.clickSlot(handler.syncId, 50, 0, SlotActionType.PICKUP, mc.player);
            delayCounter = 20;
        } else {
            isProcessing = false;
            if (pageCounter != 0) {
                pageCounter = 0;
                isSelling = true;
                delayCounter = 40;
                return;
            }
            mc.interactionManager.clickSlot(handler.syncId, 45, 1, SlotActionType.THROW, mc.player);
            delayCounter = dropDelay.get();
        }
    }

    private Item getInventoryItem() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) return stack.getItem();
        }
        return Items.AIR;
    }

    private String getOrderCommand() {
        Item item = getInventoryItem();
        if (item == Items.BONE) return "Bones";
        return item.getName().getString();
    }
}
