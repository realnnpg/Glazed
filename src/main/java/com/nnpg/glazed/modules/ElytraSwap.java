package com.nnpg.glazed.modules;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.VersionUtil;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class ElytraSwap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<PreferredItem> preferredItem = sgGeneral.add(new EnumSetting.Builder<PreferredItem>()
        .name("preferred-item")
        .description("Which item to equip when neither elytra nor chestplate is worn.")
        .defaultValue(PreferredItem.Elytra)
        .build()
    );

    private final Setting<Integer> swapDelay = sgGeneral.add(new IntSetting.Builder()
        .name("swap-delay")
        .description("Delay in ticks before performing the swap (20 ticks = 1 second).")
        .defaultValue(0)
        .min(0)
        .max(100)
        .sliderMin(0)
        .sliderMax(40)
        .build()
    );

    private int delayTimer = 0;

    public ElytraSwap() {
        super(GlazedAddon.CATEGORY, "ElytraSwap", "Swap elytra with chestplate.");
    }

    @Override
    public void onActivate() {
        delayTimer = swapDelay.get();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        performSwap();
        toggle();
    }

    private void performSwap() {
        if (mc.player == null || mc.interactionManager == null) return;

        ClientPlayerEntity player = mc.player;
        //versionutils
        ItemStack chestSlot = VersionUtil.getArmorStack(player, 2); // Chest armor slot

        boolean hasElytra = chestSlot.getItem() == Items.ELYTRA;
        boolean hasChestplate = isChestplate(chestSlot.getItem());

        if (hasElytra) {
            Item bestChestplate = findBestChestplate();
            if (bestChestplate != null) {
                swapWithInventoryItem(bestChestplate);
            } else {
                ChatUtils.error("No chestplate found in inventory!");
                return;
            }
        } else if (hasChestplate) {
            if (findItemInInventory(Items.ELYTRA) != -1) {
                swapWithInventoryItem(Items.ELYTRA);
            } else {
                ChatUtils.error("No elytra found in inventory!");
                return;
            }
        } else {
            if (preferredItem.get() == PreferredItem.Elytra) {
                if (findItemInInventory(Items.ELYTRA) != -1) {
                    equipFromInventory(Items.ELYTRA);
                } else {
                    ChatUtils.error("No elytra found in inventory!");
                    return;
                }
            } else {
                Item bestChestplate = findBestChestplate();
                if (bestChestplate != null) {
                    equipFromInventory(bestChestplate);
                } else {
                    ChatUtils.error("No chestplate found in inventory!");
                    return;
                }
            }
        }
    }

    private void swapWithInventoryItem(Item item) {
        if (mc.player == null || mc.interactionManager == null) return;

        int slot = findItemInInventory(item);
        if (slot != -1) {
            mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(0, 6, 0, SlotActionType.PICKUP, mc.player); // Chest slot is 6
            mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private Item findBestChestplate() {
        // Priority order: Netherite -> Diamond -> Iron -> Gold -> Chain -> Leather
        Item[] chestplates = {
            Items.NETHERITE_CHESTPLATE,
            Items.DIAMOND_CHESTPLATE,
            Items.IRON_CHESTPLATE,
            Items.GOLDEN_CHESTPLATE,
            Items.CHAINMAIL_CHESTPLATE,
            Items.LEATHER_CHESTPLATE
        };

        for (Item chestplate : chestplates) {
            if (findItemInInventory(chestplate) != -1) {
                return chestplate;
            }
        }
        return null;
    }

    private void equipFromInventory(Item item) {
        if (mc.player == null || mc.interactionManager == null) return;

        int slot = findItemInInventory(item);
        if (slot != -1) {
            mc.interactionManager.clickSlot(0, slot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(0, 6, 0, SlotActionType.PICKUP, mc.player); // Chest slot is 6
        }
    }

    private int findItemInInventory(Item item) {
        if (mc.player == null) return -1;

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                return i + 36; // Convert to container slot
            }
        }

        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                return i; // Already container slot
            }
        }

        return -1;
    }

    private boolean isChestplate(Item item) {
        return item == Items.LEATHER_CHESTPLATE ||
            item == Items.CHAINMAIL_CHESTPLATE ||
            item == Items.IRON_CHESTPLATE ||
            item == Items.GOLDEN_CHESTPLATE ||
            item == Items.DIAMOND_CHESTPLATE ||
            item == Items.NETHERITE_CHESTPLATE;
    }

    public enum PreferredItem {
        Elytra("Elytra"),
        Chestplate("Chestplate");

        private final String title;

        PreferredItem(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}
