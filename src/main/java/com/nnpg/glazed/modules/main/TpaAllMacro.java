package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

import java.util.*;

public class TpaAllMacro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<TpaType> tpaType = sgGeneral.add(new EnumSetting.Builder<TpaType>()
        .name("tpa-type")
        .description("Command to use.")
        .defaultValue(TpaType.TPA)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between command sends.")
        .defaultValue(20)
        .min(20)
        .sliderMax(200)
        .build()
    );

    private final Setting<Boolean> useGui = sgGeneral.add(new BoolSetting.Builder()
        .name("use-gui")
        .description("Click the confirmation GUI automatically.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> blacklist = sgGeneral.add(new StringListSetting.Builder()
        .name("blacklist")
        .description("Players to skip.")
        .defaultValue(List.of("Admin", "Mod", "YourFriend"))
        .build()
    );

    private int tickCounter = 0;
    private boolean waitingForConfirm = false;
    private long guiWaitStart = 0;
    private static final long GUI_TIMEOUT_MS = 5000;

    private final List<String> onlinePlayers = new ArrayList<>();
    private int currentIndex = 0;

    public enum TpaType {
        TPA,
        TPAHERE
    }

    public TpaAllMacro() {
        super(GlazedAddon.CATEGORY, "TpaAllMacro", "Cycles through all online players and sends /tpa or /tpahere with optional GUI confirmation.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        waitingForConfirm = false;
        guiWaitStart = 0;
        currentIndex = 0;
        onlinePlayers.clear();
        info("Starting TPA Macro loop.");
    }

    @Override
    public void onDeactivate() {
        waitingForConfirm = false;
        guiWaitStart = 0;
        info("TPA Macro deactivated.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        // Refresh player list every loop
        if (onlinePlayers.isEmpty() || currentIndex >= onlinePlayers.size()) {
            onlinePlayers.clear();
            mc.player.networkHandler.getPlayerList().forEach(info -> {
                String name = info.getProfile().getName();
                if (!Objects.equals(name, mc.player.getGameProfile().getName()) &&
                    !blacklist.get().contains(name)) {
                    onlinePlayers.add(name);
                }
            });
            currentIndex = 0;

            if (onlinePlayers.isEmpty()) {
                warning("No valid players online.");
                return;
            }
        }

        if (useGui.get()) {
            if (waitingForConfirm && mc.currentScreen instanceof HandledScreen<?>) {
                clickConfirmButtonIfPresent();
                return;
            }

            if (waitingForConfirm && guiWaitStart > 0 &&
                System.currentTimeMillis() - guiWaitStart > GUI_TIMEOUT_MS) {
                ChatUtils.warning("GUI timeout. Retrying...");
                waitingForConfirm = false;
                guiWaitStart = 0;
            }
        }

        if (!waitingForConfirm) {
            tickCounter++;
            if (tickCounter >= delay.get()) {
                String target = onlinePlayers.get(currentIndex++);
                String command = (tpaType.get() == TpaType.TPA ? "/tpa " : "/tpahere ") + target;
                ChatUtils.sendPlayerMsg(command);
                ChatUtils.info("📨 Sent: " + command);

                if (useGui.get()) {
                    waitingForConfirm = true;
                    guiWaitStart = System.currentTimeMillis();
                }

                tickCounter = 0;
            }
        }
    }

    private void clickConfirmButtonIfPresent() {
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;

        var handler = screen.getScreenHandler();
        for (int i = 0; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            String key = stack.getItem().getTranslationKey().toLowerCase();

            if (key.contains("stained_glass_pane") && key.contains("lime")) {
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                ChatUtils.info("🟢 Confirm button clicked (slot " + i + ").");
                mc.player.closeHandledScreen();
                waitingForConfirm = false;
                guiWaitStart = 0;
                return;
            }
        }
        ChatUtils.warning("No confirmation button found in GUI.");
    }
}
