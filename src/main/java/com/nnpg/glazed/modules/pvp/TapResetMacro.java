package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;

public class TapResetMacro extends Module {
    private final Setting<Boolean> sTap = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("s-tap")
        .description("Briefly presses S after hitting a player.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> tapDuration = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("tap-duration")
        .description("How long to hold S after hitting a player (ms).")
        .defaultValue(100)
        .min(10)
        .max(500)
        .sliderMax(300)
        .build()
    );

    private long tapTime = 0;
    private boolean tapped = false;

    public TapResetMacro() {
        super(GlazedAddon.pvp, "tap-reset-macro", "Performs S-tap after hitting a player.");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (!(event.entity instanceof PlayerEntity)) return;
        if (!sTap.get()) return;

        tapTime = System.currentTimeMillis();
        tapped = true;
        mc.options.backKey.setPressed(true);
    }

    @EventHandler
    private void onTickUpdate(TickEvent.Pre event) {
        if (!tapped) return;

        if (System.currentTimeMillis() - tapTime >= tapDuration.get()) {
            mc.options.backKey.setPressed(false);
            tapped = false;
        }
    }
}
