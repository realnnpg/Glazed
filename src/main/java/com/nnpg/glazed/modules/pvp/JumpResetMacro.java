package com.nnpg.glazed.modules.pvp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;

public class JumpResetMacro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyOnPlayers = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-players")
        .description("Only jump reset when hit by players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> jumpDuration = sgGeneral.add(new IntSetting.Builder()
        .name("jump-duration")
        .description("How long to hold jump key (in ms).")
        .defaultValue(100)
        .min(10)
        .sliderMax(500)
        .build()
    );

    private final Setting<Integer> jumpDelay = sgGeneral.add(new IntSetting.Builder()
        .name("jump-delay")
        .description("Delay between jumps (in ms).")
        .defaultValue(300)
        .min(50)
        .sliderMax(1000)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private boolean jumping = false;
    private long jumpStartTime = 0;
    private long lastJumpTime = 0;
    private int lastHurtTime = 0;

    public JumpResetMacro() {
        super(GlazedAddon.pvp, "jump-reset-macro", "Legit sprint reset after being hit.");
    }

    @Override
    public void onActivate() {
        jumping = false;
        jumpStartTime = 0;
        lastJumpTime = 0;
        lastHurtTime = mc.player != null ? mc.player.hurtTime : 0;
    }

    @Override
    public void onDeactivate() {
        if (jumping && mc.options != null) {
            mc.options.jumpKey.setPressed(false);
            jumping = false;
        }
        lastHurtTime = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        long currentTime = System.currentTimeMillis();

        // Detect new damage tick
        if (mc.player.hurtTime > 0 && mc.player.hurtTime != lastHurtTime) {
            DamageSource source = mc.player.getRecentDamageSource();
            boolean validAttacker = !onlyOnPlayers.get() || (source != null && source.getAttacker() instanceof PlayerEntity);

            if (validAttacker && currentTime - lastJumpTime >= jumpDelay.get() && mc.player.isOnGround()) {
                jumping = true;
                jumpStartTime = currentTime;
                lastJumpTime = currentTime;
                mc.options.jumpKey.setPressed(true); // legit keypress
            }
        }

        lastHurtTime = mc.player.hurtTime;

        // Release jump after duration
        if (jumping && currentTime - jumpStartTime >= jumpDuration.get()) {
            mc.options.jumpKey.setPressed(false);
            jumping = false;
        }
    }
}
