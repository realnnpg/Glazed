package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

import java.util.HashSet;
import java.util.Set;

public class LightESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("How far to look for sus lights")
        .defaultValue(16)
        .min(1)
        .sliderMax(32)
        .build()
    );

    private final Setting<Integer> yThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("y-threshold")
        .description("Only checks below this Y level")
        .defaultValue(60)
        .min(-64)
        .sliderMax(320)
        .build()
    );

    private final Setting<Integer> minLight = sgGeneral.add(new IntSetting.Builder()
        .name("min-light")
        .description("Minimum light level to flag")
        .defaultValue(8)
        .min(1)
        .max(15)
        .sliderMax(15)
        .build()
    );

    private final Setting<Integer> updateDelay = sgGeneral.add(new IntSetting.Builder()
        .name("update-delay")
        .description("Ticks between scans to save FPS")
        .defaultValue(20)
        .min(1)
        .sliderMax(100)
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .description("Color of the box")
        .defaultValue(new SettingColor(255, 165, 0, 100))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Set<BlockPos> spots = new HashSet<>();
    private int timer = 0;

    public LightESP() {
        super(GlazedAddon.esp, "light-esp", "Finds underground bases via light levels.");
    }

    @Override
    public void onActivate() {
        spots.clear();
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (timer > 0) {
            timer--;
            return;
        }
        timer = updateDelay.get();

        spots.clear();
        int r = range.get();
        BlockPos pPos = mc.player.getBlockPos();

        // rip fps if range is high
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = pPos.add(x, y, z);

                    // yeet the sky light or high altitude blocks
                    if (pos.getY() > yThreshold.get()) continue;

                    // we look for air/blocks receiving block light (torches etc)
                    if (mc.world.getBlockState(pos).isAir()) {
                        int lvl = mc.world.getLightLevel(LightType.BLOCK, pos);
                        if (lvl >= minLight.get()) {
                            spots.add(pos);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (spots.isEmpty()) return;

        // found their secret base lmao
        for (BlockPos pos : spots) {
            event.renderer.box(pos, color.get(), color.get(), shapeMode.get(), 0);
        }
    }
}
