package com.nnpg.glazed.modules;

/*
 * This file was taken from the Meteor Client module called BreakDelay. It was added here to make it easier to access and make it more popular.
 */


import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.entity.player.BlockBreakingCooldownEvent;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;

public class FastBreak extends Module {
    SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Block break cooldown in ticks.")
        .defaultValue(0)
        .min(0)
        .sliderMax(5)
        .build()
    );

    private final Setting<Boolean> noInstaBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("no-insta-break")
        .description("Prevents you from misbreaking blocks if you can instantly break them.")
        .defaultValue(false)
        .build()
    );

    private boolean breakBlockCooldown = false;

    public FastBreak() {
        super(GlazedAddon.CATEGORY, "FastBreak", "Changes the delay between breaking blocks. This module was taken from the Meteor Client module called BreakDelay. It was added here to make it easier to access and make it more popular.");
    }

    @EventHandler
    private void onBlockBreakingCooldown(BlockBreakingCooldownEvent event) {
        if (breakBlockCooldown) {
            event.cooldown = 5;
            breakBlockCooldown = false;
        } else {
            event.cooldown = cooldown.get();
        }
    }

    @EventHandler
    private void onClick(MouseButtonEvent event) {
        if (event.action == KeyAction.Press && noInstaBreak.get()) {
            breakBlockCooldown = true;
        }
    }

    public boolean preventInstaBreak() {
        return isActive() && noInstaBreak.get();
    }
}
