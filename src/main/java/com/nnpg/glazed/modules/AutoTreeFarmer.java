package com.nnpg.glazed.modules;

import com.nnpg.glazed.GlazedAddon;
import com.nnpg.glazed.VersionUtil;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.option.KeyBinding;

import java.util.ArrayList;
import java.util.List;

public class AutoTreeFarmer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMovement = settings.createGroup("Movement");


    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Delay between placing blocks (ticks).")
        .defaultValue(1)
        .min(0)
        .max(10)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> breakDelay = sgGeneral.add(new IntSetting.Builder()
        .name("break-delay")
        .description("Delay between breaking blocks (ticks).")
        .defaultValue(3)
        .min(1)
        .max(20)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate to look at blocks when interacting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useBoneMeal = sgGeneral.add(new BoolSetting.Builder()
        .name("use-bone-meal")
        .description("Whether to use bone meal to instantly grow trees.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between actions in ticks.")
        .defaultValue(3)
        .min(0)
        .max(20)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Range to work within.")
        .defaultValue(4)
        .min(1)
        .max(6)
        .sliderMax(6)
        .build()
    );

    // Movement Settings
    private final Setting<Boolean> enableMovement = sgMovement.add(new BoolSetting.Builder()
        .name("enable-movement")
        .description("Enable automatic player movement to farming positions.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> movementSpeed = sgMovement.add(new DoubleSetting.Builder()
        .name("movement-speed")
        .description("Speed multiplier for player movement.")
        .defaultValue(0.15)
        .min(0.05)
        .max(0.5)
        .sliderMax(0.5)
        .build()
    );

    private final Setting<Double> stopDistance = sgMovement.add(new DoubleSetting.Builder()
        .name("stop-distance")
        .description("Distance to stop moving before reaching target.")
        .defaultValue(1.8)
        .min(0.5)
        .max(3.0)
        .sliderMax(3.0)
        .build()
    );

    private final Setting<Double> smoothness = sgMovement.add(new DoubleSetting.Builder()
        .name("smoothness")
        .description("Movement smoothness factor (higher = smoother).")
        .defaultValue(0.8)
        .min(0.1)
        .max(0.95)
        .sliderMax(0.95)
        .build()
    );

    private final Setting<Boolean> easeInOut = sgMovement.add(new BoolSetting.Builder()
        .name("ease-in-out")
        .description("Use ease-in-out movement for more natural motion.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> rotationSpeed = sgMovement.add(new DoubleSetting.Builder()
        .name("rotation-speed")
        .description("How fast to rotate camera (lower = smoother).")
        .defaultValue(5.0)
        .min(0.0)
        .max(50.0)
        .sliderMax(50.0)
        .build()
    );

    private int tickCounter = 0;
    private int actionDelay = 0;
    private List<BlockPos> farmPositions = new ArrayList<>();
    private int currentFarmIndex = 0;
    private FarmState currentState = FarmState.FINDING_POSITIONS;
    private BlockPos currentWorkingPos = null;
    private Vec3d targetPosition = null;
    private Vec3d currentVelocity = Vec3d.ZERO;
    private boolean isMovingToTarget = false;
    private double currentYaw = 0.0;
    private double currentPitch = 0.0;
    private double targetYaw = 0.0;
    private double targetPitch = 0.0;
    private int movementTicks = 0;
    private int ticksSinceRotationChange = 0;
    private boolean shouldBeJumping = false;
    private BlockPos lastSaplingPos = null;
    private int rotationStabilizeTicks = 0;
    private BlockPos currentBreakingPos = null;
    private boolean isBreaking = false;

    public AutoTreeFarmer() {
        super(GlazedAddon.CATEGORY, "AutoTreeFarmer", "Automatically farms spruce trees in 2x2 patterns with auto-refill.");
    }

    @Override
    public void onActivate() {
        farmPositions.clear();
        currentFarmIndex = 0;
        currentState = FarmState.FINDING_POSITIONS;
        tickCounter = 0;
        actionDelay = 0;
        targetPosition = null;
        currentVelocity = Vec3d.ZERO;
        isMovingToTarget = false;
        currentYaw = mc.player.getYaw();
        currentPitch = mc.player.getPitch();
        targetYaw = currentYaw;
        targetPitch = currentPitch;
        movementTicks = 0;
        ticksSinceRotationChange = 0;
        shouldBeJumping = false;
        lastSaplingPos = null;
        rotationStabilizeTicks = 0;
        currentBreakingPos = null;
        isBreaking = false;
        findFarmPositions();
    }

    @Override
    public void onDeactivate() {
        stopBreaking();
        resetJump();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {

        autoRefillHotbar(Items.BONE_MEAL, 5);
        autoRefillHotbar(Items.SPRUCE_SAPLING, 4);

        if (currentState != FarmState.APPLYING_BONEMEAL && shouldBeJumping) {
            resetJump();
        }

        if (tickCounter++ >= delay.get()) {
            tickCounter = 0;

            if (actionDelay > 0) {
                actionDelay--;
                return;
            }

            ticksSinceRotationChange++;

            if (farmPositions.isEmpty()) {
                findFarmPositions();
                return;
            }

            switch (currentState) {
                case FINDING_POSITIONS -> {
                    findFarmPositions();
                    if (!farmPositions.isEmpty()) {
                        currentState = enableMovement.get() ? FarmState.MOVING_TO_FARM : FarmState.PLACING_SAPLINGS;
                    }
                }
                case MOVING_TO_FARM -> {
                    if (moveToFarmPosition()) {
                        currentState = FarmState.PLACING_SAPLINGS;
                    }
                }
                case PLACING_SAPLINGS -> {
                    if (placeSaplings()) {
                        actionDelay = placeDelay.get();
                        currentState = useBoneMeal.get() ? FarmState.APPLYING_BONEMEAL : FarmState.WAITING_FOR_GROWTH;
                    }
                }
                case APPLYING_BONEMEAL -> {
                    if (applyBoneMeal()) {
                        actionDelay = placeDelay.get();
                        currentState = FarmState.WAITING_FOR_GROWTH;
                    }
                }
                case WAITING_FOR_GROWTH -> {
                    if (checkTreeGrown()) {
                        actionDelay = breakDelay.get();
                        currentState = FarmState.HARVESTING;
                    }
                }
                case HARVESTING -> {
                    if (harvestTree()) {
                        actionDelay = breakDelay.get();
                        moveToNextFarm();
                        currentState = enableMovement.get() ? FarmState.MOVING_TO_FARM : FarmState.PLACING_SAPLINGS;
                    }
                }
            }
        }
    }


    private void autoRefillHotbar(Item targetItem, int minAmount) {
        try {
            int selectedSlot = VersionUtil.getSelectedSlot(mc.player);
            ItemStack stack = mc.player.getInventory().getStack(selectedSlot);

            if (stack == null || stack.isEmpty() || stack.getItem() != targetItem || stack.getCount() <= minAmount) {
                FindItemResult hotbarItem = InvUtils.findInHotbar(targetItem);

                if (!hotbarItem.found()) {
                    FindItemResult invItem = InvUtils.find(targetItem);
                    if (invItem.found()) {
                        InvUtils.move().from(invItem.slot()).to(selectedSlot);
                    }
                }
            }
        } catch (Exception ignored) {

        }
    }


    private void lookAtBlock(BlockPos pos) {
        Vec3d targetPos = Vec3d.ofCenter(pos);
        Vec3d playerPos = mc.player.getEyePos();
        Vec3d direction = targetPos.subtract(playerPos).normalize();

        double yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z));
        double pitch = Math.toDegrees(-Math.asin(direction.y));

        mc.player.setYaw((float) yaw);
        mc.player.setPitch((float) pitch);
        ticksSinceRotationChange = 0;
    }

    private void breakBlock(BlockPos pos) {
        if (mc.interactionManager != null) {
            mc.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
            KeyBinding.setKeyPressed(mc.options.attackKey.getDefaultKey(), true);
        }
        isBreaking = true;
        currentBreakingPos = pos;
    }

    private void stopBreaking() {
        if (isBreaking) {
            KeyBinding.setKeyPressed(mc.options.attackKey.getDefaultKey(), false);
            isBreaking = false;
            currentBreakingPos = null;
        }
    }

    private void resetJump() {
        if (shouldBeJumping) {
            KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), false);
            shouldBeJumping = false;
        }
    }

    private boolean moveToFarmPosition() {
        if (currentFarmIndex >= farmPositions.size()) return true;

        BlockPos farmPos = farmPositions.get(currentFarmIndex);
        Vec3d playerPos = mc.player.getPos();
        Vec3d farmCenter = new Vec3d(farmPos.getX() + 0.5, farmPos.getY(), farmPos.getZ() + 0.5);
        double distance = playerPos.distanceTo(farmCenter);

        if (distance <= stopDistance.get()) {
            isMovingToTarget = false;
            currentVelocity = Vec3d.ZERO;
            return true;
        }

        if (targetPosition == null || !isMovingToTarget) {
            targetPosition = farmCenter;
            isMovingToTarget = true;
            movementTicks = 0;
        }

        movementTicks++;
        smoothMoveToTarget(farmCenter);
        smoothRotateToTarget(farmPos);
        return false;
    }

    private void smoothMoveToTarget(Vec3d target) {
        Vec3d playerPos = mc.player.getPos();
        Vec3d direction = target.subtract(playerPos);
        double distance = direction.length();

        if (distance < 0.1) return;

        direction = direction.normalize();
        double speed = movementSpeed.get();

        if (easeInOut.get()) {
            double progress = Math.min(movementTicks / 20.0, 1.0);
            double easedProgress = easeInOutCubic(progress);
            speed *= easedProgress;

            if (distance < 3.0) {
                double slowdownFactor = distance / 3.0;
                speed *= Math.max(0.2, slowdownFactor);
            }
        }

        Vec3d desiredVelocity = direction.multiply(speed);
        double smoothFactor = smoothness.get();
        currentVelocity = currentVelocity.multiply(smoothFactor).add(desiredVelocity.multiply(1.0 - smoothFactor));

        Vec3d newPos = playerPos.add(currentVelocity);


        double yDiff = target.y - playerPos.y;
        if (Math.abs(yDiff) > 0.1) {
            double ySpeed = Math.min(0.05, Math.abs(yDiff) * 0.1);
            newPos = new Vec3d(newPos.x, playerPos.y + Math.signum(yDiff) * ySpeed, newPos.z);
        }


        if (newPos.distanceTo(playerPos) < 0.5) {
            mc.player.setPosition(newPos.x, newPos.y, newPos.z);
        }
    }

    private void smoothRotateToTarget(BlockPos target) {
        if (!rotate.get()) return;

        Vec3d targetVec = Vec3d.ofCenter(target.down());
        Vec3d playerPos = mc.player.getEyePos();
        Vec3d direction = targetVec.subtract(playerPos).normalize();

        double yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z));
        double pitch = Math.toDegrees(-Math.asin(direction.y));

        if (!target.equals(lastSaplingPos)) {
            lastSaplingPos = target;
            rotationStabilizeTicks = 0;
            targetYaw = yaw;
            targetPitch = pitch;
        }

        double diffYaw = normalizeYaw(targetYaw - mc.player.getYaw());
        double diffPitch = targetPitch - mc.player.getPitch();

        double lerpFactor = 0.3 * rotationSpeed.get();
        double smoothedYaw = mc.player.getYaw() + (diffYaw * lerpFactor);
        double smoothedPitch = mc.player.getPitch() + (diffPitch * lerpFactor);

        mc.player.setYaw((float) smoothedYaw);
        mc.player.setPitch((float) smoothedPitch);

        rotationStabilizeTicks++;
        ticksSinceRotationChange = 0;
    }

    private double easeInOutCubic(double t) {
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    private double normalizeYaw(double yaw) {
        while (yaw > 180.0) yaw -= 360.0;
        while (yaw < -180.0) yaw += 360.0;
        return yaw;
    }

    private void findFarmPositions() {
        farmPositions.clear();
        BlockPos playerPos = mc.player.getBlockPos();
        float yaw = mc.player.getYaw();

        int frontX = 0, frontZ = 0;
        yaw = ((yaw % 360) + 360) % 360;

        if (yaw >= 315.0f || yaw < 45.0f) {
            frontZ = 2;
        } else if (yaw >= 45.0f && yaw < 135.0f) {
            frontX = -2;
        } else if (yaw >= 135.0f && yaw < 225.0f) {
            frontZ = -2;
        } else {
            frontX = 2;
        }

        BlockPos frontPos = playerPos.add(frontX, 0, frontZ);
        if (isValidFarmPosition(frontPos)) {
            farmPositions.add(frontPos);
        }

        if (farmPositions.isEmpty()) {
            for (int x = -range.get(); x <= range.get(); x++) {
                for (int z = -range.get(); z <= range.get(); z++) {
                    BlockPos pos = playerPos.add(x, 0, z);
                    if (isValidFarmPosition(pos)) {
                        farmPositions.add(pos);
                        break;
                    }
                }
                if (!farmPositions.isEmpty()) break;
            }
        }
    }

    private boolean isValidFarmPosition(BlockPos pos) {
        BlockPos[] saplingPositions = get2x2Positions(pos);

        for (BlockPos saplingPos : saplingPositions) {
            BlockPos groundPos = saplingPos.down();
            Block groundBlock = mc.world.getBlockState(groundPos).getBlock();
            if (!isValidGroundBlock(groundBlock)) return false;

            Block currentBlock = mc.world.getBlockState(saplingPos).getBlock();
            if (!currentBlock.equals(Blocks.AIR) && !currentBlock.equals(Blocks.SPRUCE_SAPLING) && !isSpruceLog(currentBlock)) {
                return false;
            }

            for (int i = 1; i <= 6; i++) {
                Block aboveBlock = mc.world.getBlockState(saplingPos.up(i)).getBlock();
                if (!aboveBlock.equals(Blocks.AIR) && !isSpruceLog(aboveBlock) && !isSpruceLeaves(aboveBlock)) {
                    return false;
                }
            }
        }

        return true;
    }

    private BlockPos[] get2x2Positions(BlockPos basePos) {
        return new BlockPos[]{
            basePos,
            basePos.add(1, 0, 0),
            basePos.add(0, 0, 1),
            basePos.add(1, 0, 1)
        };
    }

    private boolean placeSaplings() {
        if (currentFarmIndex >= farmPositions.size()) return true;

        BlockPos farmPos = farmPositions.get(currentFarmIndex);
        BlockPos[] saplingPositions = get2x2Positions(farmPos);
        FindItemResult saplings = InvUtils.find(Items.SPRUCE_SAPLING);

        if (!saplings.found()) return false;

        for (BlockPos pos : saplingPositions) {
            if (mc.world.getBlockState(pos).getBlock().equals(Blocks.AIR)) {
                currentWorkingPos = pos;

                if (enableMovement.get()) {
                    Vec3d playerPos = mc.player.getPos();
                    Vec3d blockPos = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    double distance = playerPos.distanceTo(blockPos);

                    if (distance > 2.2) {
                        smoothMoveToTarget(blockPos);
                        return false;
                    }
                }


                lookAtBlock(pos);

                if (actionDelay <= 0) {
                    InvUtils.swap(saplings.slot(), false);
                    BlockUtils.place(pos, saplings, rotate.get(), 50);
                    actionDelay = placeDelay.get();
                }

                return false;
            }
        }
        return true;
    }

    private boolean applyBoneMeal() {
        if (!useBoneMeal.get()) {
            resetJump();
            return true;
        }
        if (currentFarmIndex >= farmPositions.size()) {
            resetJump();
            return true;
        }

        BlockPos farmPos = farmPositions.get(currentFarmIndex);
        BlockPos[] saplingPositions = get2x2Positions(farmPos);
        FindItemResult boneMeal = InvUtils.find(Items.BONE_MEAL);

        if (!boneMeal.found()) {
            resetJump();
            return true;
        }


        if (!shouldBeJumping) {
            KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), true);
            shouldBeJumping = true;
        }

        for (BlockPos pos : saplingPositions) {
            if (mc.world.getBlockState(pos).getBlock().equals(Blocks.SPRUCE_SAPLING)) {
                currentWorkingPos = pos;

                if (enableMovement.get()) {
                    Vec3d playerPos = mc.player.getPos();
                    Vec3d blockPos = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    double distance = playerPos.distanceTo(blockPos);

                    if (distance > 2.2) {
                        smoothMoveToTarget(blockPos);
                        return false;
                    }
                }

                if (rotate.get()) {
                    smoothRotateToTarget(pos);
                    if (rotationStabilizeTicks < 3) {
                        return false;
                    }
                }

                if (actionDelay <= 0) {
                    InvUtils.swap(boneMeal.slot(), false);
                    Vec3d hitPos = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    BlockHitResult hitResult = new BlockHitResult(hitPos, Direction.UP, pos, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                    actionDelay = placeDelay.get();
                }

                return false;
            }
        }

        resetJump();
        return true;
    }

    private boolean checkTreeGrown() {
        if (currentFarmIndex >= farmPositions.size()) return true;

        BlockPos farmPos = farmPositions.get(currentFarmIndex);
        BlockPos[] saplingPositions = get2x2Positions(farmPos);

        for (BlockPos pos : saplingPositions) {
            Block block = mc.world.getBlockState(pos).getBlock();
            if (isSpruceLog(block)) {
                return true;
            }
        }

        return false;
    }

    private boolean harvestTree() {
        if (currentFarmIndex >= farmPositions.size()) return true;

        FindItemResult axe = InvUtils.find(itemStack -> itemStack.getItem() instanceof AxeItem);
        if (!axe.found()) return true;

        BlockPos farmPos = farmPositions.get(currentFarmIndex);
        BlockPos[] saplingPositions = get2x2Positions(farmPos);


        for (BlockPos pos : saplingPositions) {
            BlockPos upPos = pos.up();
            Block block = mc.world.getBlockState(upPos).getBlock();
            if (isSpruceLog(block)) {
                currentWorkingPos = upPos;

                if (enableMovement.get()) {
                    Vec3d playerPos = mc.player.getPos();
                    Vec3d blockPos = new Vec3d(upPos.getX() + 0.5, upPos.getY(), upPos.getZ() + 0.5);
                    double distance = playerPos.distanceTo(blockPos);

                    if (distance > 2.0) {
                        smoothMoveToTarget(blockPos);
                        return false;
                    }
                }

                if (rotate.get()) {
                    lookAtBlock(upPos);
                }

                if (actionDelay <= 0) {
                    InvUtils.swap(axe.slot(), false);
                    breakBlock(upPos); // Break the block above the sapling
                    actionDelay = breakDelay.get();
                }

                if (mc.world.getBlockState(upPos).isAir()) {
                    stopBreaking();
                    return false;
                }

                return false;
            }
        }

        stopBreaking();
        return true;
    }

    private void moveToNextFarm() {
        currentFarmIndex++;
        targetPosition = null;
        isMovingToTarget = false;
        lastSaplingPos = null;
        rotationStabilizeTicks = 0;

        if (currentFarmIndex >= farmPositions.size()) {
            currentFarmIndex = 0;
            findFarmPositions();
        }
    }

    private boolean isValidGroundBlock(Block block) {
        return block.equals(Blocks.GRASS_BLOCK) ||
            block.equals(Blocks.DIRT) ||
            block.equals(Blocks.COARSE_DIRT) ||
            block.equals(Blocks.PODZOL) ||
            block.equals(Blocks.MYCELIUM);
    }

    private boolean isSpruceLog(Block block) {
        return block.equals(Blocks.SPRUCE_LOG);
    }

    private boolean isSpruceLeaves(Block block) {
        return block.equals(Blocks.SPRUCE_LEAVES);
    }

    private enum FarmState {
        FINDING_POSITIONS,
        MOVING_TO_FARM,
        PLACING_SAPLINGS,
        APPLYING_BONEMEAL,
        WAITING_FOR_GROWTH,
        HARVESTING
    }
}
