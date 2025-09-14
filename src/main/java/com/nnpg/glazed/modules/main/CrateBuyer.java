package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class CrateBuyer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum ItemType {
        All,
        Helmet,
        Chestplate,
        Leggings,
        Boots,
        Sword,
        Pickaxe,
        Shovel
    }

    private final Setting<CrateBuyer.ItemType> WhatToBuy = sgGeneral.add(new EnumSetting.Builder<CrateBuyer.ItemType>()
        .name("Item Type")
        .description("What item to buy from the crates")
        .defaultValue(CrateBuyer.ItemType.All)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between clicks in ticks.")
        .defaultValue(5)
        .min(5)
        .max(20)
        .sliderMax(20)
        .build()
    );

    private int tickCounter = 0;
    private int warningCooldown = 0;
    private int currentStep = 0;
    private int currentItemIndex = 0;
    private boolean hasClickedOnce = false;

    private static final int HELMET_SLOT = 10;
    private static final int CHESTPLATE_SLOT = 11;
    private static final int LEGGINGS_SLOT = 12;
    private static final int BOOTS_SLOT = 13;
    private static final int SWORD_SLOT = 14;
    private static final int PICKAXE_SLOT = 15;
    private static final int SHOVEL_SLOT = 16;

    private static final int CONFIRM_SLOT_DEFAULT = 15;
    private static final int CONFIRM_SLOT_SHOVEL = 15;

    public CrateBuyer() {
        super(GlazedAddon.CATEGORY, "CrateBuyer", "Automatically buys items from the common crate");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.interactionManager == null) return;

        if (warningCooldown > 0) {
            warningCooldown--;
        }

        if (!(mc.currentScreen instanceof HandledScreen)) {
            if (isActive() && warningCooldown == 0) {
                warning("You need to be on the crate screen to use this module.");
                warningCooldown = 20;
            }
            return;
        }

        HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;

        if (!hasClickedOnce && !isValidCrateScreen(screen)) {
            if (warningCooldown == 0) {
                warning("This doesn't appear to be a valid crate screen. Closing screen.");
                warningCooldown = 20;
                mc.setScreen(null);
            }
            return;
        }

        tickCounter++;

        if (tickCounter < delay.get()) {
            return;
        }

        tickCounter = 0;

        if (WhatToBuy.get() == ItemType.All) {
            handleAllItems(screen);
        } else {
            handleSingleItem(screen);
        }
    }

    private boolean isValidCrateScreen(HandledScreen<?> screen) {
        for (int i = 0; i <= 9; i++) {
            if (!screen.getScreenHandler().getSlot(i).getStack().isOf(Items.GRAY_STAINED_GLASS_PANE)) {
                return false;
            }
        }

        for (int i = 17; i <= 26; i++) {
            if (!screen.getScreenHandler().getSlot(i).getStack().isOf(Items.GRAY_STAINED_GLASS_PANE)) {
                return false;
            }
        }

        return true;
    }

    private void handleAllItems(HandledScreen<?> screen) {
        ItemType[] items = {ItemType.Helmet, ItemType.Chestplate, ItemType.Leggings,
            ItemType.Boots, ItemType.Sword, ItemType.Pickaxe, ItemType.Shovel};

        if (currentStep == 0) {
            int itemSlot = getItemSlot(items[currentItemIndex]);
            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, itemSlot, 0, SlotActionType.PICKUP, mc.player);
            hasClickedOnce = true;
            currentStep = 1;
        } else {
            int confirmSlot = getConfirmSlot(items[currentItemIndex]);
            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, confirmSlot, 0, SlotActionType.PICKUP, mc.player);
            currentStep = 0;

            currentItemIndex++;
            if (currentItemIndex >= items.length) {
                currentItemIndex = 0;
            }
        }
    }

    private void handleSingleItem(HandledScreen<?> screen) {
        if (currentStep == 0) {
            int itemSlot = getItemSlot(WhatToBuy.get());
            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, itemSlot, 0, SlotActionType.PICKUP, mc.player);
            hasClickedOnce = true;
            currentStep = 1;
        } else {
            int confirmSlot = getConfirmSlot(WhatToBuy.get());
            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, confirmSlot, 0, SlotActionType.PICKUP, mc.player);
            currentStep = 0;
        }
    }

    private int getItemSlot(ItemType itemType) {
        switch (itemType) {
            case Helmet:
                return HELMET_SLOT;
            case Chestplate:
                return CHESTPLATE_SLOT;
            case Leggings:
                return LEGGINGS_SLOT;
            case Boots:
                return BOOTS_SLOT;
            case Sword:
                return SWORD_SLOT;
            case Pickaxe:
                return PICKAXE_SLOT;
            case Shovel:
                return SHOVEL_SLOT;
            default:
                return HELMET_SLOT;
        }
    }

    private int getConfirmSlot(ItemType itemType) {
        if (itemType == ItemType.Shovel) {
            return CONFIRM_SLOT_SHOVEL;
        }
        return CONFIRM_SLOT_DEFAULT;
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        warningCooldown = 0;
        currentStep = 0;
        currentItemIndex = 0;
        hasClickedOnce = false;
        info("CrateBuyer activated. Mode: " + WhatToBuy.get().toString());
    }

    @Override
    public void onDeactivate() {
        currentStep = 0;
        currentItemIndex = 0;
        hasClickedOnce = false;
    }
}
