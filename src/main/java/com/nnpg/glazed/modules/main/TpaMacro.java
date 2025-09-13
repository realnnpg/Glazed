package com.nnpg.glazed.modules.main;
//imports
import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import java.util.Objects;





public class TpaMacro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> playerName = sgGeneral.add(new StringSetting.Builder()
        .name("target-player")
        .description("Name of the player to send TPA to.")
        .defaultValue("Steve")
        .build()
    );

    private final Setting<TpaType> tpaType = sgGeneral.add(new EnumSetting.Builder<TpaType>()
        .name("tpa-type")
        .description("Command to use.")
        .defaultValue(TpaType.TPA)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between command sends.")
        .defaultValue(40)
        .min(10)
        .sliderMax(200)
        .build()
    );

    private int tickCounter = 0;
    private boolean waitingForConfirm = false;
    private long guiWaitStart = 0;
    private static final long GUI_TIMEOUT_MS = 5000;

    public enum TpaType {
        TPA,
        TPAHERE
    }

    public TpaMacro() {
        super(GlazedAddon.CATEGORY, "TpaMacro", "Spams /tpa or /tpahere and clicks the confirmation.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        waitingForConfirm = false;
        guiWaitStart = 0;
        info("Starting TPA to " + playerName.get());
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

        // If the player is nearby, disable
        PlayerEntity target = mc.world.getPlayers().stream()
            .filter(p -> Objects.equals(p.getGameProfile().getName(), playerName.get()))
            .findFirst()
            .orElse(null);

        if (target != null && mc.player.distanceTo(target) < 5) {
            info(" Player " + playerName.get() + " is nearby. Macro complete.");
            toggle();
            return;
        }

        // If we're in GUI and waiting for confirm, try clicking
        if (waitingForConfirm && mc.currentScreen instanceof HandledScreen<?>) {
            clickConfirmButtonIfPresent();
            return;
        }

        // Timeout if GUI didn't show up
        if (waitingForConfirm && guiWaitStart > 0 && System.currentTimeMillis() - guiWaitStart > GUI_TIMEOUT_MS) {
            ChatUtils.warning("GUI timeout. Retrying...");
            waitingForConfirm = false;
            guiWaitStart = 0;
        }

        // Send TPA command if not waiting
        if (!waitingForConfirm) {
            tickCounter++;
            if (tickCounter >= delay.get()) {
                String command = (tpaType.get() == TpaType.TPA ? "/tpa " : "/tpahere ") + playerName.get();
                ChatUtils.sendPlayerMsg(command);
                ChatUtils.info("📨 Sent: " + command);
                waitingForConfirm = true;
                guiWaitStart = System.currentTimeMillis();
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
        ChatUtils.warning("No button's found in GUI.");
    }
}
