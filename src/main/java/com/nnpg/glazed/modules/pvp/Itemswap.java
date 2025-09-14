package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.VersionUtil;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.ItemSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.List;

public class Itemswap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onCrystal = sgGeneral.add(new BoolSetting.Builder()
        .name("switch-on-crystal")
        .description("Whether to swap items when attacking end crystals.")
        .defaultValue(false)
        .build()
    );

    public enum SelectMode {
        Specific_Slot,
        Specific_Item
    }

    private final Setting<SelectMode> mode = sgGeneral.add(new EnumSetting.Builder<SelectMode>()
        .name("mode")
        .description("Choose how to select the target: by specific hotbar slot or by item.")
        .defaultValue(SelectMode.Specific_Slot)
        .build()
    );

    private final Setting<Integer> targetSlot = sgGeneral.add(new IntSetting.Builder()
        .name("target-slot")
        .description("The hotbar slot to use when in Specific Slot mode.")
        .sliderRange(1, 9)
        .defaultValue(1)
        .min(1)
        .visible(() -> mode.get() == SelectMode.Specific_Slot)
        .build()
    );

    private final Setting<List<Item>> selectedItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("selected-items")
        .description("Items to choose from when in Specific Item mode. Uses the first matching item on your hotbar.")
        .defaultValue(List.of(Items.DIAMOND_SWORD))
        .visible(() -> mode.get() == SelectMode.Specific_Item)
        .build()
    );

    public enum SwapBackMode {
        None,
        Previous,
        Specific_Slot,
        Specific_Item
    }

    private final Setting<SwapBackMode> backMode = sgGeneral.add(new EnumSetting.Builder<SwapBackMode>()
        .name("swap-back-mode")
        .description("How to swap back after attacking.")
        .defaultValue(SwapBackMode.Previous)
        .build()
    );

    private final Setting<Integer> backSlot = sgGeneral.add(new IntSetting.Builder()
        .name("swap-back-slot")
        .description("Specific hotbar slot to swap back to when using Specific Slot mode.")
        .sliderRange(1, 9)
        .defaultValue(1)
        .min(1)
        .visible(() -> backMode.get() == SwapBackMode.Specific_Slot)
        .build()
    );

    private final Setting<Item> backItem = sgGeneral.add(new ItemSetting.Builder()
        .name("swap-back-item")
        .description("Specific item to swap back to when using Specific Item mode.")
        .defaultValue(Items.AIR)
        .visible(() -> backMode.get() == SwapBackMode.Specific_Item)
        .build()
    );

    private final Setting<Integer> backDelay = sgGeneral.add(new IntSetting.Builder()
        .name("swap-back-delay")
        .description("Delay in ticks before swapping back to the chosen slot/item.")
        .sliderRange(1, 20)
        .defaultValue(1)
        .min(1)
        .visible(() -> backMode.get() != SwapBackMode.None)
        .build()
    );

    private int prevSlot = -1;
    private int dDelay = 0;

    public Itemswap() {
        super(GlazedAddon.pvp, "itemswap", "Swaps the main hand item to a specific slot or item on attack and optionally swaps back.");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (mc.player == null || mc.world == null) return;

        // Check if attacking an end crystal and the crystal setting is disabled
        if (event.entity instanceof EndCrystalEntity && !onCrystal.get()) {
            return;
        }

        int target = -1;

        if (mode.get() == SelectMode.Specific_Slot) {
            target = targetSlot.get() - 1;
        } else {
            target = findHotbarSlotForAny(selectedItems.get());
            if (target == -1) return;
        }

        switch (backMode.get()) {
            case None -> prevSlot = -1;
            case Previous -> prevSlot = VersionUtil.getSelectedSlot(mc.player);
            case Specific_Slot -> prevSlot = backSlot.get() - 1;
            case Specific_Item -> prevSlot = findHotbarSlotForItem(backItem.get());
        }

        InvUtils.swap(target, false);

        if (backMode.get() != SwapBackMode.None && prevSlot != -1) {
            dDelay = backDelay.get();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (dDelay > 0) {
            dDelay--;
            if (dDelay == 0 && prevSlot != -1) {
                InvUtils.swap(prevSlot, false);
                prevSlot = -1;
            }
        }
    }

    private int findHotbarSlotForAny(List<Item> items) {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && items.contains(stack.getItem())) return i;
        }
        return -1;
    }

    private int findHotbarSlotForItem(Item item) {
        if (mc.player == null || item == null || item == Items.AIR) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) return i;
        }
        return -1;
    }
}
