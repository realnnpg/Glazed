package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class OrderDropper extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private enum Stage {
        NONE, OPEN_ORDERS, WAIT_ORDERS_GUI, CLICK_SLOT_51, WAIT_SECOND_GUI,
        CLICK_TARGET_ITEM, WAIT_THIRD_GUI, CLICK_SLOT_13, WAIT_ITEMS_GUI,
        CLICK_SLOT_52, CLICK_SLOT_53, CYCLE_PAUSE
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Item> targetItem = sgGeneral.add(new ItemSetting.Builder()
        .name("target-item")
        .description("Item to search for and drop from orders.")
        .defaultValue(Items.DIAMOND)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between actions in milliseconds.")
        .defaultValue(300)
        .min(100)
        .sliderMin(100)
        .sliderMax(2000)
        .build()
    );

    private Stage stage = Stage.NONE;
    private long stageStart = 0;
    private boolean foundTargetItem = false;

    public OrderDropper() {
        super(GlazedAddon.CATEGORY, "OrderDropper", "Automatically processes orders and drops specified items.");
    }

    @Override
    public void onActivate() {
        if (targetItem.get() == null) {
            error("No target item selected.");
            toggle();
            return;
        }
        stage = Stage.OPEN_ORDERS;
        stageStart = System.currentTimeMillis();
        foundTargetItem = false;
    }

    @Override
    public void onDeactivate() {
        stage = Stage.NONE;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();

        switch (stage) {
            case OPEN_ORDERS -> {
                ChatUtils.sendPlayerMsg("/order");
                stage = Stage.WAIT_ORDERS_GUI;
                stageStart = now;
            }

            case WAIT_ORDERS_GUI -> {
                if (now - stageStart < delay.get()) return;
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    stage = Stage.CLICK_SLOT_51;
                    stageStart = now;
                } else if (now - stageStart > 3000) {
                    stage = Stage.OPEN_ORDERS;
                    stageStart = now;
                }
            }

            case CLICK_SLOT_51 -> {
                if (now - stageStart < delay.get()) return;
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
                ScreenHandler handler = screen.getScreenHandler();
                if (handler.slots.size() > 51) {
                    mc.interactionManager.clickSlot(handler.syncId, 51, 0, SlotActionType.PICKUP, mc.player);
                    stage = Stage.WAIT_SECOND_GUI;
                    stageStart = now;
                }
            }

            case WAIT_SECOND_GUI -> {
                if (now - stageStart < delay.get()) return;
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    stage = Stage.CLICK_TARGET_ITEM;
                    stageStart = now;
                } else if (now - stageStart > 3000) {
                    stage = Stage.OPEN_ORDERS;
                    stageStart = now;
                }
            }

            case CLICK_TARGET_ITEM -> {
                if (now - stageStart < delay.get()) return;
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
                if (targetItem.get() == null) {
                    error("Target item is null.");
                    toggle();
                    return;
                }
                ScreenHandler handler = screen.getScreenHandler();
                foundTargetItem = false;
                for (int i = 0; i < handler.slots.size(); i++) {
                    Slot slot = handler.slots.get(i);
                    if (slot.hasStack() && slot.getStack().getItem() == targetItem.get()) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                        stage = Stage.WAIT_THIRD_GUI;
                        stageStart = now;
                        foundTargetItem = true;
                        break;
                    }
                }
                if (!foundTargetItem && now - stageStart > 4000) {
                    stage = Stage.CYCLE_PAUSE;
                    stageStart = now;
                }
            }

            case WAIT_THIRD_GUI -> {
                if (now - stageStart < delay.get()) return;
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    stage = Stage.CLICK_SLOT_13;
                    stageStart = now;
                } else if (now - stageStart > 3000) {
                    stage = Stage.OPEN_ORDERS;
                    stageStart = now;
                }
            }

            case CLICK_SLOT_13 -> {
                if (now - stageStart < delay.get()) return;
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
                ScreenHandler handler = screen.getScreenHandler();

                if (handler.slots.size() > 13) {
                    Slot slot13 = handler.slots.get(13);
                    if (slot13.hasStack() && slot13.getStack().getItem() == Items.CHEST) {
                        mc.interactionManager.clickSlot(handler.syncId, 13, 0, SlotActionType.PICKUP, mc.player);
                    } else if (handler.slots.size() > 15) {
                        mc.interactionManager.clickSlot(handler.syncId, 15, 0, SlotActionType.PICKUP, mc.player);
                    }
                    stage = Stage.WAIT_ITEMS_GUI;
                    stageStart = now;
                }
            }

            case WAIT_ITEMS_GUI -> {
                if (now - stageStart < delay.get()) return;
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    stage = Stage.CLICK_SLOT_52;
                    stageStart = now;
                } else if (now - stageStart > 3000) {
                    stage = Stage.OPEN_ORDERS;
                    stageStart = now;
                }
            }

            case CLICK_SLOT_52 -> {
                if (now - stageStart < delay.get()) return;
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
                ScreenHandler handler = screen.getScreenHandler();
                if (handler.slots.size() > 52) {
                    mc.interactionManager.clickSlot(handler.syncId, 52, 0, SlotActionType.PICKUP, mc.player);
                    stage = Stage.CLICK_SLOT_53;
                    stageStart = now;
                }
            }

            case CLICK_SLOT_53 -> {
                if (now - stageStart < delay.get()) return;
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
                    toggle();
                    return;
                }
                ScreenHandler handler = screen.getScreenHandler();
                if (handler.slots.size() > 53) {
                    Slot nextPageSlot = handler.slots.get(53);
                    if (nextPageSlot.hasStack() && nextPageSlot.getStack().getItem() == Items.ARROW) {
                        mc.interactionManager.clickSlot(handler.syncId, 53, 0, SlotActionType.PICKUP, mc.player);
                        stage = Stage.CLICK_SLOT_52;
                        stageStart = now;
                    } else {
                        if (handler.slots.size() > 52) {
                            mc.interactionManager.clickSlot(handler.syncId, 52, 0, SlotActionType.PICKUP, mc.player);
                        }
                        toggle();
                    }
                }
            }

            case CYCLE_PAUSE -> {
                if (now - stageStart > 2000) {
                    stage = Stage.OPEN_ORDERS;
                    stageStart = now;
                }
            }

            case NONE -> {}
        }
    }

    @Override
    public String getInfoString() {
        return stage.toString().toLowerCase().replace("_", " ");
    }
}
