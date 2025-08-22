package com.nnpg.glazed.modules;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.List;

public class SpawnerDropper extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between clicks in ticks.")
        .defaultValue(5)
        .min(1)
        .max(20)
        .sliderMax(20)
        .build()
    );

    private final Setting<String> ignoreIfPresentName = sgGeneral.add(new StringSetting.Builder()
        .name("ignore-item-name")
        .description("If the GUI contains any item whose name or tooltip contains this text, skip drop clicks.")
        .defaultValue("arrow")
        .build()
    );

    private int tickCounter = 0;
    private boolean hasClickedSlots = false;
    private int warningCooldown = 0;
    private int currentStep = 0;
    private int checkDelayCounter = 0;
    private static final int CHECK_DELAY = 3;
    private static final boolean shouldrepeat = true;
    private boolean skipNotificationShown = false;

    public SpawnerDropper() {
        super(GlazedAddon.CATEGORY, "SpawnerDropper", "Drops all items from spawners");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.interactionManager == null) return;

        if (warningCooldown > 0) warningCooldown--;

        if (!(mc.currentScreen instanceof HandledScreen)) {
            if (isActive() && !hasClickedSlots && warningCooldown == 0) {
                warning("You need to be on the spawner screen to use this module.");
                warningCooldown = 20;
            }
            return;
        }

        HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;
        ScreenHandler handler = screen.getScreenHandler();

        if (currentStep == 2 || currentStep == 5) {
            checkDelayCounter++;
            if (checkDelayCounter >= CHECK_DELAY) {
                if (handler.getSlot(0).getStack().isEmpty()) {
                    info("Dropped all items from spawner.");
                    toggle();
                    return;
                } else {
                    if (currentStep == 2) currentStep = 3;
                    else if (currentStep == 5) currentStep = 0;
                }
                checkDelayCounter = 0;
            }
            return;
        }

        tickCounter++;
        if (tickCounter < delay.get()) return;
        tickCounter = 0;
        hasClickedSlots = true;

        boolean shouldSkip = false;
        String needle = ignoreIfPresentName.get();
        if (needle != null && !needle.trim().isEmpty()) {
            shouldSkip = guiContainsItemName(handler, needle);
        }

        if (shouldSkip) {
            if (!skipNotificationShown) {
                info("Found \"%s\" in GUI — skipping drop clicks until it is removed.", needle);
                skipNotificationShown = true;
            }
            return;
        } else {
            skipNotificationShown = false;
        }

        switch (currentStep) {
            case 0:
                clickAvoidingPrevious(handler, 50);
                currentStep = 1;
                break;
            case 1:
                clickAvoidingPrevious(handler, 53);
                currentStep = 2;
                checkDelayCounter = 0;
                break;
            case 3:
                clickAvoidingPrevious(handler, 50);
                currentStep = 4;
                break;
            case 4:
                clickAvoidingPrevious(handler, 45);
                currentStep = 5;
                checkDelayCounter = 0;
                break;
        }
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        hasClickedSlots = false;
        warningCooldown = 0;
        currentStep = 0;
        checkDelayCounter = 0;
        skipNotificationShown = false;
    }

    @Override
    public void onDeactivate() {
        currentStep = 0;
        checkDelayCounter = 0;
        mc.setScreen(null);
    }

    private boolean guiContainsItemName(ScreenHandler handler, String searchName) {
        if (mc.player == null || mc.world == null || handler == null || searchName == null || searchName.isEmpty()) {
            return false;
        }

        String needle = searchName.toLowerCase().trim();

        int containerSlots;
        if (handler instanceof GenericContainerScreenHandler gc) {
            int rows = gc.getRows();
            containerSlots = Math.max(0, rows * 9 - 9);
        } else {
            containerSlots = Math.max(0, handler.slots.size() - 36 - 9);
            if (containerSlots <= 0) containerSlots = Math.max(0, handler.slots.size() - 9);
        }

        Item.TooltipContext tooltipContext = Item.TooltipContext.create(mc.world);

        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack == null || stack.isEmpty()) continue;

            String display = stack.getName().getString();
            if (display != null) {
                String cleanDisplay = stripColorCodes(display).toLowerCase();
                if (cleanDisplay.contains(needle)) return true;
            }

            List<Text> tooltip = stack.getTooltip(tooltipContext, mc.player, TooltipType.BASIC);
            if (tooltip != null) {
                for (Text line : tooltip) {
                    if (line == null) continue;
                    String s = stripColorCodes(line.getString()).toLowerCase();
                    if (s.contains(needle)) return true;
                }
            }
        }

        return false;
    }

    private void clickAvoidingPrevious(ScreenHandler handler, int desiredSlotIndex) {
        if (handler == null) return;

        if (!slotTooltipContains(handler, desiredSlotIndex, "previous page")) {
            mc.interactionManager.clickSlot(handler.syncId, desiredSlotIndex, 0, SlotActionType.PICKUP, mc.player);
            return;
        }

        int alt = findSlotWithTooltip(handler, "next page");
        if (alt != -1) {
            mc.interactionManager.clickSlot(handler.syncId, alt, 0, SlotActionType.PICKUP, mc.player);
            return;
        }

        if (warningCooldown == 0) {
            info("Found a 'previous page' arrow at slot %d and no 'next page' arrow to use — skipping click to avoid going back.", desiredSlotIndex);
            warningCooldown = 20;
        }
    }

    private boolean slotTooltipContains(ScreenHandler handler, int slotIndex, String needle) {
        if (handler == null || slotIndex < 0 || slotIndex >= handler.slots.size() || needle == null) return false;
        ItemStack stack = handler.getSlot(slotIndex).getStack();
        if (stack == null || stack.isEmpty()) return false;

        String lower = needle.toLowerCase().trim();
        String display = stack.getName().getString();
        if (display != null && stripColorCodes(display).toLowerCase().contains(lower)) return true;

        Item.TooltipContext tooltipContext = Item.TooltipContext.create(mc.world);
        List<Text> tooltip = stack.getTooltip(tooltipContext, mc.player, TooltipType.BASIC);
        if (tooltip != null) {
            for (Text line : tooltip) {
                if (line == null) continue;
                if (stripColorCodes(line.getString()).toLowerCase().contains(lower)) return true;
            }
        }

        return false;
    }

    private int findSlotWithTooltip(ScreenHandler handler, String needle) {
        if (handler == null || needle == null || needle.isEmpty()) return -1;
        String lower = needle.toLowerCase().trim();

        Item.TooltipContext tooltipContext = Item.TooltipContext.create(mc.world);

        for (int i = 0; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack == null || stack.isEmpty()) continue;

            String display = stack.getName().getString();
            if (display != null && stripColorCodes(display).toLowerCase().contains(lower)) return i;

            List<Text> tooltip = stack.getTooltip(tooltipContext, mc.player, TooltipType.BASIC);
            if (tooltip != null) {
                for (Text line : tooltip) {
                    if (line == null) continue;
                    if (stripColorCodes(line.getString()).toLowerCase().contains(lower)) return i;
                }
            }
        }

        return -1;
    }

    private String stripColorCodes(String in) {
        if (in == null) return "";
        return in.replaceAll("\u00A7.", "");
    }
}
