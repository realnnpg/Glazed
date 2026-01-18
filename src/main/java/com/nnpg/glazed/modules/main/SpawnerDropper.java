package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

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

    private final Setting<Boolean> boneOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("bone-only")
        .description("Only drop bones. Stops when arrows are detected in slots 0-44.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> autoReopenInterval = sgGeneral.add(new IntSetting.Builder()
        .name("auto-reopen-interval")
        .description("Time in minutes to auto-reopen spawner.")
        .defaultValue(10)
        .min(1)
        .max(600)
        .sliderMax(600)
        .build()
    );

    private int tickCounter = 0;
    private int currentStep = 0;
    private int checkDelayCounter = 0;
    private int reopenTimer = 0;
    private BlockPos spawnerPos = null;
    private boolean waitingForInterval = false;

    private static final int CHECK_DELAY = 3;

    public SpawnerDropper() {
        super(GlazedAddon.CATEGORY, "SpawnerDropper", "Drops all items from spawners");
    }

    private boolean hasArrowsInInventory(HandledScreen<?> screen) {
        for (int i = 0; i <= 44; i++) {
            if (!screen.getScreenHandler().getSlot(i).getStack().isEmpty() &&
                screen.getScreenHandler().getSlot(i).getStack().getItem() == Items.ARROW) {
                return true;
            }
        }
        return false;
    }

    private BlockPos findNearbySpawner() {
        if (mc.player == null || mc.world == null) return null;

        BlockPos playerPos = mc.player.getBlockPos();
        for (int x = -6; x <= 6; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -6; z <= 6; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() == net.minecraft.block.Blocks.SPAWNER) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private void openSpawner() {
        if (spawnerPos == null) {
            spawnerPos = findNearbySpawner();
        }

        if (spawnerPos != null && mc.interactionManager != null && mc.player != null) {
            BlockHitResult hitResult = new BlockHitResult(
                Vec3d.ofCenter(spawnerPos),
                Direction.UP,
                spawnerPos,
                false
            );
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            currentStep = 0;
            checkDelayCounter = 0;
            tickCounter = 0;
            waitingForInterval = false;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return;

        // Autodetect spawner if not found
        if (spawnerPos == null) {
            spawnerPos = findNearbySpawner();
            if (spawnerPos != null) {
                info("Spawner detected nearby!");
            }
        }

        // Handle auto-reopen timer
        reopenTimer++;
        int reopenIntervalTicks = autoReopenInterval.get() * 60 * 20; // minutes to ticks

        // If waiting for interval just count and wait ty
        if (waitingForInterval) {
            if (reopenTimer >= reopenIntervalTicks) {
                info("Interval reached - opening spawner.");
                openSpawner();
                reopenTimer = 0;
            }
            return;
        }

        // Auto-open spawner at interval if no screen is open 
        if (!(mc.currentScreen instanceof HandledScreen)) {
            if (reopenTimer >= reopenIntervalTicks || reopenTimer == 20) {
                if (spawnerPos != null) {
                    openSpawner();
                    reopenTimer = 0;
                }
            }
            return;
        }

        HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;

        // Check for arrows if found close and wait for interval sigma
        if (boneOnly.get() && hasArrowsInInventory(screen)) {
            info("Arrows detected - closing spawner and waiting for interval.");
            mc.currentScreen.close();
            waitingForInterval = true;
            reopenTimer = 0;
            return;
        }

        // Check delay steps
        if (currentStep == 2 || currentStep == 5) {
            checkDelayCounter++;
            if (checkDelayCounter >= CHECK_DELAY) {
                if (screen.getScreenHandler().getSlot(0).getStack().isEmpty()) {
                    info("All bones dropped - closing and waiting for interval.");
                    mc.currentScreen.close();
                    waitingForInterval = true;
                    reopenTimer = 0;
                    return;
                } else {
                    if (currentStep == 2) {
                        currentStep = 3;
                    } else if (currentStep == 5) {
                        currentStep = 0;
                    }
                    checkDelayCounter = 0;
                }
            }
            return;
        }

        // Click delay
        tickCounter++;
        if (tickCounter < delay.get()) {
            return;
        }

        tickCounter = 0;

        // Execute clicks
        switch (currentStep) {
            case 0:
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, 50, 0, SlotActionType.PICKUP, mc.player);
                currentStep = 1;
                break;
            case 1:
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, 53, 0, SlotActionType.PICKUP, mc.player);
                currentStep = 2;
                checkDelayCounter = 0;
                break;
            case 3:
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, 50, 0, SlotActionType.PICKUP, mc.player);
                currentStep = 4;
                break;
            case 4:
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, 53, 0, SlotActionType.PICKUP, mc.player);
                currentStep = 5;
                checkDelayCounter = 0;
                break;
        }
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        currentStep = 0;
        checkDelayCounter = 0;
        reopenTimer = 0;
        spawnerPos = null;
        waitingForInterval = false;
        info("SpawnerDropper activated - will auto-open spawner.");
    }

    @Override
    public void onDeactivate() {
        currentStep = 0;
        checkDelayCounter = 0;
        reopenTimer = 0;
        spawnerPos = null;
        waitingForInterval = false;
        if (mc.currentScreen != null) {
            mc.setScreen(null);
        }
    }
}