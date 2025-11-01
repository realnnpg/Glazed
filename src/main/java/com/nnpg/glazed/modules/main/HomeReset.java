package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

public class HomeReset extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Show chat messages when running commands")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> homeSlot = sgGeneral.add(new IntSetting.Builder()
        .name("home-slot")
        .description("Which home slot to reset (1–5).")
        .defaultValue(1)
        .min(1).max(5)
        .build()
    );

    public HomeReset() {
        super(GlazedAddon.CATEGORY, "home-reset", "Automatically runs /delhome and /sethome for a selected slot.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;

        int slot = homeSlot.get();

        mc.execute(() -> {
            sendServerCommand("/delhome " + slot);

            new Thread(() -> {
                try {
                    Thread.sleep(1000); // 1 second delay
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                mc.execute(() -> {
                    sendServerCommand("/sethome " + slot);

                    if (chatFeedback.get()) {
                        ChatUtils.info("§aHome " + slot + " deleted and set successfully!");
                    }

                    toggle(); // Disable module after running
                });
            }, "HomeReset-DelayThread").start();
        });
    }

    private void sendServerCommand(String command) {
        ChatUtils.sendPlayerMsg(command);
    }
}
