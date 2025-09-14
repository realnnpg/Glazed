package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShearsItem;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class UndetectedTunneler extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgRotation = settings.createGroup("Rotation");

    // Visible settings
    private final Setting<Integer> scanDepth = sgGeneral.add(new IntSetting.Builder()
        .name("scan-depth")
        .description("How many blocks ahead to scan for hazards.")
        .defaultValue(6)
        .range(4, 10)
        .sliderRange(4, 10)
        .build()
    );

    private final Setting<Boolean> humanLikeRotation = sgGeneral.add(new BoolSetting.Builder()
        .name("human-like-rotation")
        .description("Add human-like imperfections to rotation.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Show info and warning messages in chat.")
        .defaultValue(true)
        .build()
    );

    // Hidden settings with fixed values
    private final Setting<Integer> yLevel = sgGeneral.add(new IntSetting.Builder()
        .name("y-level")
        .description("Maximum Y level to operate at.")
        .defaultValue(-50)
        .range(-64, -10)
        .sliderRange(-64, -10)
        .visible(() -> false)
        .build()
    );

    private final Setting<Integer> scanHeight = sgSafety.add(new IntSetting.Builder()
        .name("scan-height")
        .description("How many blocks above to scan for falling blocks.")
        .defaultValue(3)
        .range(3, 8)
        .sliderRange(3, 8)
        .visible(() -> false)
        .build()
    );

    private final Setting<Integer> safetyMargin = sgSafety.add(new IntSetting.Builder()
        .name("safety-margin")
        .description("Blocks before hazard to stop mining.")
        .defaultValue(4)
        .range(1, 4)
        .sliderRange(1, 4)
        .visible(() -> false)
        .build()
    );

    private final Setting<Boolean> strictGroundCheck = sgSafety.add(new BoolSetting.Builder()
        .name("strict-ground-check")
        .description("Require solid ground for scan area.")
        .defaultValue(false)
        .visible(() -> false)
        .build()
    );

    private final Setting<Integer> scanFrequency = sgSafety.add(new IntSetting.Builder()
        .name("scan-frequency")
        .description("How often to scan for hazards while mining (in ticks).")
        .defaultValue(1)
        .range(1, 20)
        .sliderRange(1, 20)
        .visible(() -> false)
        .build()
    );

    private final Setting<Integer> backtrackDistance = sgSafety.add(new IntSetting.Builder()
        .name("backtrack-distance")
        .description("How many blocks to go back when both sides are blocked.")
        .defaultValue(20)
        .range(10, 50)
        .sliderRange(10, 50)
        .visible(() -> false)
        .build()
    );

    private final Setting<Boolean> smoothRotation = sgRotation.add(new BoolSetting.Builder()
        .name("smooth-rotation")
        .description("Use smooth rotation instead of instant snapping.")
        .defaultValue(true)
        .visible(() -> false)
        .build()
    );

    private final Setting<Double> rotationSpeed = sgRotation.add(new DoubleSetting.Builder()
        .name("rotation-speed")
        .description("Base rotation speed.")
        .defaultValue(3.0)
        .range(1, 5)
        .sliderRange(1, 5)
        .visible(() -> false)
        .build()
    );

    private final Setting<Double> rotationAcceleration = sgRotation.add(new DoubleSetting.Builder()
        .name("rotation-acceleration")
        .description("How quickly rotation speeds up and slows down.")
        .defaultValue(0.5)
        .range(0.1, 1.0)
        .sliderRange(0.1, 1.0)
        .visible(() -> false)
        .build()
    );

    private final Setting<Double> overshootChance = sgRotation.add(new DoubleSetting.Builder()
        .name("overshoot-chance")
        .description("Chance to overshoot target rotation.")
        .defaultValue(0.2)
        .range(0, 1)
        .sliderRange(0, 1)
        .visible(() -> false)
        .build()
    );

    // Module components
    private MiningState currentState = MiningState.IDLE;
    private DirectionManager directionManager;
    private PathScanner pathScanner;
    private SafetyValidator safetyValidator;
    private BacktrackManager backtrackManager;
    private RotationController rotationController;

    // Mining tracking
    private int blocksMined = 0;
    private Vec3d lastPos = Vec3d.ZERO;
    private int scanTicks = 0;
    private int ticksSinceActivation = 0;
    private int lastCenterTick = -30;
    private int stateTransitionDelay = 0;
    private int rotationLockoutTicks = 30;
    private boolean hasCentered = false;
    private boolean packetSentThisTick = false;
    private BlockPos miningBlock = null; // Track the block being mined

    // Centering tracking
    private int centeringTicks = 0;
    private Vec3d centeringTarget = null;

    // Current operation tracking
    private Direction pendingDirection;
    private BacktrackManager.BacktrackPlan currentBacktrackPlan;

    public UndetectedTunneler() {
        super(GlazedAddon.CATEGORY, "UndetectedTunneler", "Automatically mines 1x1 tunnels below Y=-50.");
    }

    @Override
    public void onActivate() {
        warning("Works for 1x1, 2x1 tunnels. Very beta version, might get stuck sometimes");
        if (mc.player == null || mc.world == null || !mc.player.isAlive() || mc.player.networkHandler == null || !mc.player.networkHandler.getConnection().isOpen()) {
            senderror("Invalid player or server state! Cannot activate module.");
            toggle();
            return;
        }

        if (mc.player.getY() > yLevel.get()) {
            senderror("You must be below Y=" + yLevel.get() + " to use this module! Current Y: " + Math.round(mc.player.getY()));
            toggle();
            return;
        }

        try {
            directionManager = new DirectionManager();
            pathScanner = new PathScanner();
            safetyValidator = new SafetyValidator();
            backtrackManager = new BacktrackManager();
            rotationController = new RotationController();
        } catch (Exception e) {
            senderror("Failed to initialize components: " + e.getMessage());
            toggle();
            return;
        }

        // Stop all movement
        resetMovement();
        currentState = MiningState.IDLE;
        blocksMined = 0;
        lastPos = mc.player.getPos();
        scanTicks = 0;
        ticksSinceActivation = 0;
        lastCenterTick = -30;
        stateTransitionDelay = 0;
        rotationLockoutTicks = 30;
        hasCentered = false;
        centeringTicks = 0;
        centeringTarget = null;
        packetSentThisTick = false;
        miningBlock = null;

        sendInfo("AITunneler activated at Y=" + Math.round(mc.player.getY()) + ". Waiting for initialization...");
    }

    @Override
    public void onDeactivate() {
        resetMovement();
        currentState = MiningState.IDLE;
        if (safetyValidator != null) {
            safetyValidator.reset();
        }
        miningBlock = null;
        sendInfo("AITunneler deactivated.");
    }

    private void resetMovement() {
        if (mc.options != null) {
            mc.options.attackKey.setPressed(false);
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
        }
        if (mc.player != null) {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0); // Preserve vertical velocity
        }
        if (mc.interactionManager != null && miningBlock != null) {
            mc.interactionManager.cancelBlockBreaking();
            miningBlock = null;
        }
    }

    private void sendInfo(String message) {
        if (chatFeedback.get()) {
            info(message);
        }
    }

    private void sendWarning(String message) {
        if (chatFeedback.get()) {
            warning(message);
        }
    }

    private void senderror(String message) {
        if (chatFeedback.get()) {
            error(message);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || !mc.player.isAlive() || mc.player.networkHandler == null || !mc.player.networkHandler.getConnection().isOpen()) {
            toggle();
            return;
        }

        // Stop if GUI is open
        if (mc.currentScreen != null) {
            resetMovement();
            return;
        }

        ticksSinceActivation++;
        if (rotationLockoutTicks > 0) {
            rotationLockoutTicks--;
        }
        packetSentThisTick = false;

        if (mc.player.getY() > yLevel.get()) {
            senderror("Moved above Y=" + yLevel.get() + "! Disabling module for safety.");
            toggle();
            return;
        }

        if (currentState == MiningState.IDLE && ticksSinceActivation >= 30) {
            currentState = MiningState.CENTERING;
            resetMovement();
            sendInfo("Starting centering phase.");
        }

        if (stateTransitionDelay > 0) {
            stateTransitionDelay--;
            return;
        }

        // Update scanner for 1x1 mining (no width scanning)
        pathScanner.updateScanWidths(0, 0);
        rotationController.updateSettings(
            smoothRotation.get(),
            rotationSpeed.get(),
            rotationAcceleration.get(),
            humanLikeRotation.get(),
            overshootChance.get()
        );

        if (!safetyValidator.canContinue(mc.player, yLevel.get())) {
            senderror("Safety check failed!");
            toggle();
            return;
        }

        if (safetyValidator.checkStuck(mc.player)) {
            senderror("Player stuck, initiating backtrack");
            resetMovement();
            currentState = MiningState.HAZARD_DETECTED;
            stateTransitionDelay = 3;
            return;
        }

        FindItemResult pickaxe = InvUtils.findInHotbar(this::isTool);
        if (!pickaxe.found()) {
            senderror("No pickaxe!");
            toggle();
            return;
        }

        if (!pickaxe.isMainHand()) {
            if (!packetSentThisTick) {
                InvUtils.swap(pickaxe.slot(), false);
                packetSentThisTick = true;
                stateTransitionDelay = 3;
                sendInfo("Swapped to pickaxe, delaying next action.");
            }
            return;
        }

        if (currentState == MiningState.CENTERING && centeringTicks > 0) {
            gradualCenterPlayer();
            return;
        }

        if (rotationController.isRotating() && rotationLockoutTicks == 0 && !packetSentThisTick) {
            rotationController.update();
            packetSentThisTick = true;
            return;
        }

        try {
            switch (currentState) {
                case CENTERING -> {
                    handleCentering();
                    stateTransitionDelay = 3;
                }
                case SCANNING -> {
                    handleScanning();
                    stateTransitionDelay = 3;
                }
                case MINING -> {
                    handleMining();
                    stateTransitionDelay = 3;
                }
                case HAZARD_DETECTED -> {
                    handleHazardDetected();
                    stateTransitionDelay = 3;
                }
                case RETRACING -> {
                    handleRetracing();
                    stateTransitionDelay = 3;
                }
                case BACKTRACKING -> {
                    handleBacktracking();
                    stateTransitionDelay = 3;
                }
                case ROTATING -> {
                    currentState = MiningState.CENTERING; // Change to centering after rotation
                    stateTransitionDelay = 3;
                }
                case STOPPED -> toggle();
            }
        } catch (Exception e) {
            senderror("Error in state machine: " + e.getMessage());
            toggle();
        }
    }

    private boolean isTool(ItemStack itemStack) {
        return itemStack.isIn(ItemTags.PICKAXES) ||
            itemStack.isIn(ItemTags.SHOVELS) ||
            itemStack.isIn(ItemTags.AXES) ||
            itemStack.getItem() instanceof ShearsItem;
    }

    private boolean isPickaxe(ItemStack itemStack) {
        return itemStack.isIn(ItemTags.PICKAXES);
    }

    private void gradualCenterPlayer() {
        if (mc.player == null || packetSentThisTick) return;

        Vec3d pos = mc.player.getPos();
        double targetX = Math.floor(pos.x) + 0.5;
        double targetZ = Math.floor(pos.z) + 0.5;
        double dx = targetX - pos.x;
        double dz = targetZ - pos.z;
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance < 0.05 || centeringTicks <= 0) {
            centeringTicks = 0;
            centeringTarget = null;
            resetMovement();
            packetSentThisTick = true;
            sendInfo("Gradual centering complete.");
            return;
        }

        if (Math.abs(dx) > 0.05) {
            if (dx > 0) {
                mc.options.rightKey.setPressed(true);
                mc.options.leftKey.setPressed(false);
            } else {
                mc.options.leftKey.setPressed(true);
                mc.options.rightKey.setPressed(false);
            }
        } else {
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
        }

        if (Math.abs(dz) > 0.05) {
            if (dz > 0) {
                mc.options.forwardKey.setPressed(true);
                mc.options.backKey.setPressed(false);
            } else {
                mc.options.backKey.setPressed(true);
                mc.options.forwardKey.setPressed(false);
            }
        } else {
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
        }

        centeringTicks--;
        packetSentThisTick = true;
        sendInfo("Centering: Moving to (" + targetX + ", " + targetZ + "), ticks left: " + centeringTicks);
    }

    private void handleCentering() {
        if (!hasCentered && ticksSinceActivation - lastCenterTick >= 30 && isPlayerOffCenter()) {
            Vec3d pos = mc.player.getPos();
            centeringTarget = new Vec3d(Math.floor(pos.x) + 0.5, pos.y, Math.floor(pos.z) + 0.5);
            centeringTicks = 10;
            hasCentered = true;
            lastCenterTick = ticksSinceActivation;
            sendInfo("Starting gradual centering to " + centeringTarget);
            gradualCenterPlayer();
            return;
        }

        float yaw = mc.player.getYaw();
        Direction initialDir = getCardinalDirection(yaw);
        directionManager.setInitialDirection(initialDir);

        float targetYaw = directionToYaw(initialDir);
        currentState = MiningState.ROTATING;

        rotationController.startRotation(targetYaw, () -> {
            backtrackManager.startNewSegment(initialDir, mc.player.getPos());
            currentState = MiningState.SCANNING;
            sendInfo("Rotation complete, starting scan.");
        });
    }

    private boolean isPlayerOffCenter() {
        if (mc.player == null) return false;
        Vec3d pos = mc.player.getPos();
        double xFrac = pos.x - Math.floor(pos.x);
        double zFrac = pos.z - Math.floor(pos.z);
        return Math.abs(xFrac - 0.5) > 0.1 || Math.abs(zFrac - 0.5) > 0.1;
    }

    private void handleScanning() {
        BlockPos playerPos = mc.player.getBlockPos();
        Direction currentDir = directionManager.getCurrentDirection();

        PathScanner.ScanResult result = pathScanner.scanDirection(
            playerPos,
            currentDir,
            scanDepth.get(),
            scanHeight.get(),
            strictGroundCheck.get()
        );

        if (result.isSafe()) {
            currentState = MiningState.MINING;
            blocksMined = 0;
            scanTicks = 0;
            lastPos = mc.player.getPos();
            startMining(currentDir);
            sendInfo("Scan complete, starting mining in direction " + currentDir);
        } else {
            String hazardName = getHazardName(result.getHazardType());
            sendWarning(hazardName + " detected, changing direction");
            currentState = MiningState.HAZARD_DETECTED;
            resetMovement();
            sendInfo("Movement stopped due to hazard.");
        }
    }

    private void startMining(Direction direction) {
        mc.options.attackKey.setPressed(true);
        mc.options.forwardKey.setPressed(true);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        miningBlock = mc.player.getBlockPos().offset(direction);
    }

    private void handleMining() {
        if (mc.player.getY() > yLevel.get()) {
            senderror("Moved above Y=" + yLevel.get() + " while mining! Stopping.");
            resetMovement();
            toggle();
            return;
        }

        Vec3d currentPos = mc.player.getPos();
        scanTicks++;

        if (scanTicks >= scanFrequency.get()) {
            scanTicks = 0;

            BlockPos playerPos = mc.player.getBlockPos();
            Direction currentDir = directionManager.getCurrentDirection();

            PathScanner.ScanResult result = pathScanner.scanDirection(
                playerPos,
                currentDir,
                scanDepth.get(),
                scanHeight.get(),
                strictGroundCheck.get()
            );

            if (!result.isSafe() && result.getHazardDistance() <= safetyMargin.get()) {
                String hazardName = getHazardName(result.getHazardType());
                sendWarning(hazardName + " detected, changing direction");
                resetMovement();
                currentState = MiningState.HAZARD_DETECTED;
                sendInfo("Movement stopped due to hazard in mining.");
                return;
            }
        }

        double distanceMoved = currentPos.distanceTo(lastPos);
        if (distanceMoved >= 0.8) {
            blocksMined += 1; // 1x1 mining - one block at a time
            lastPos = currentPos;

            directionManager.recordMovement(1);
            backtrackManager.recordMovement();
        }

        if (blocksMined >= 1) {
            resetMovement();

            BlockPos playerPos = mc.player.getBlockPos();
            Direction currentDir = directionManager.getCurrentDirection();

            PathScanner.ScanResult result = pathScanner.scanDirection(
                playerPos,
                currentDir,
                scanDepth.get(),
                scanHeight.get(),
                strictGroundCheck.get()
            );

            if (!result.isSafe() && result.getHazardDistance() <= safetyMargin.get()) {
                String hazardName = getHazardName(result.getHazardType());
                sendWarning(hazardName + " detected, changing direction");
                currentState = MiningState.HAZARD_DETECTED;
                sendInfo("Movement stopped due to hazard in mining check.");
                return;
            }

            blocksMined = 0;
            scanTicks = 0;
            startMining(currentDir);
            sendInfo("Continuing mining in direction " + currentDir);
        }
    }

    private void handleHazardDetected() {
        resetMovement();

        // Center the player after detecting a hazard
        if (ticksSinceActivation - lastCenterTick >= 30 && isPlayerOffCenter()) {
            centeringTarget = new Vec3d(Math.floor(mc.player.getX()) + 0.5, mc.player.getY(), Math.floor(mc.player.getZ()) + 0.5);
            centeringTicks = 10;
            lastCenterTick = ticksSinceActivation;
            sendInfo("Starting gradual centering for hazard handling.");
            gradualCenterPlayer();
            return;
        }

        Direction currentDir = directionManager.getCurrentDirection();
        directionManager.recordHazard(currentDir, 0);

        DirectionManager.DirectionChoice choice = directionManager.getNextDirection();

        if (choice.needsBacktrack) {
            Direction mainTunnel = directionManager.getMainTunnel();
            currentBacktrackPlan = backtrackManager.createBacktrackPlan(mainTunnel, backtrackDistance.get());

            sendInfo("Backtracking and trying again");

            if (currentBacktrackPlan.needsRetrace) {
                float targetYaw = directionToYaw(currentBacktrackPlan.retraceDirection);
                currentState = MiningState.ROTATING;

                rotationController.startRotation(targetYaw, () -> {
                    currentState = MiningState.RETRACING;
                    backtrackManager.startRetrace(currentBacktrackPlan.retraceDistance);
                    lastPos = mc.player.getPos();
                    mc.options.forwardKey.setPressed(true);
                    mc.options.backKey.setPressed(false);
                    mc.options.leftKey.setPressed(false);
                    mc.options.rightKey.setPressed(false);
                    sendInfo("Starting retrace in direction " + currentBacktrackPlan.retraceDirection);
                });
            } else {
                startBacktrack();
            }
            return;
        }

        if (choice.direction == null) {
            senderror("No safe directions found!");
            currentState = MiningState.STOPPED;
            return;
        }

        pendingDirection = choice.direction;

        float targetYaw = directionToYaw(choice.direction);
        currentState = MiningState.ROTATING;

        rotationController.startRotation(targetYaw, () -> {
            backtrackManager.startNewSegment(pendingDirection, mc.player.getPos());
            // After rotation, we need to center before scanning
            currentState = MiningState.CENTERING;
            hasCentered = false; // Reset centering flag so it will center again
            sendInfo("Rotation complete, centering before scanning in direction " + pendingDirection);
        });
    }

    private void startBacktrack() {
        float targetYaw = directionToYaw(currentBacktrackPlan.backtrackDirection);
        currentState = MiningState.ROTATING;

        rotationController.startRotation(targetYaw, () -> {
            currentState = MiningState.BACKTRACKING;
            backtrackManager.startBacktrack(currentBacktrackPlan.backtrackDistance);
            lastPos = mc.player.getPos();
            mc.options.forwardKey.setPressed(true);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            sendInfo("Starting backtrack in direction " + currentBacktrackPlan.backtrackDirection);
        });
    }

    private void handleRetracing() {
        Vec3d currentPos = mc.player.getPos();
        double distanceMoved = currentPos.distanceTo(lastPos);

        if (distanceMoved >= 0.8) {
            lastPos = currentPos;

            if (backtrackManager.updateRetrace()) {
                resetMovement();
                sendInfo("Retrace complete, starting backtrack.");
                startBacktrack();
            }
        }
    }

    private void handleBacktracking() {
        Vec3d currentPos = mc.player.getPos();
        double distanceMoved = currentPos.distanceTo(lastPos);

        if (distanceMoved >= 0.8) {
            lastPos = currentPos;

            if (backtrackManager.updateBacktrack()) {
                resetMovement();
                sendInfo("Backtrack complete.");

                backtrackManager.reset();
                directionManager.markBacktrackComplete();

                DirectionManager.DirectionChoice choice = directionManager.getNextDirection();

                if (choice.direction == null) {
                    senderror("No safe directions found after backtracking!");
                    currentState = MiningState.STOPPED;
                    return;
                }

                pendingDirection = choice.direction;

                float targetYaw = directionToYaw(choice.direction);
                currentState = MiningState.ROTATING;

                rotationController.startRotation(targetYaw, () -> {
                    backtrackManager.startNewSegment(pendingDirection, mc.player.getPos());
                    // After rotation, we need to center before scanning
                    currentState = MiningState.CENTERING;
                    hasCentered = false; // Reset centering flag so it will center again
                    sendInfo("Rotation complete, centering before scanning in direction " + pendingDirection);
                });
            }
        }
    }

    private String getHazardName(PathScanner.HazardType type) {
        return switch (type) {
            case LAVA -> "Lava";
            case WATER -> "Water";
            case FALLING_BLOCK -> "Gravel";
            case UNSAFE_GROUND -> "Unsafe ground";
            case DANGEROUS_BLOCK -> "Dangerous block";
            default -> "Hazard";
        };
    }

    private Direction getCardinalDirection(float yaw) {
        yaw = (yaw % 360 + 360) % 360;
        if (yaw >= 315 || yaw < 45) return Direction.SOUTH;
        if (yaw >= 45 && yaw < 135) return Direction.WEST;
        if (yaw >= 135 && yaw < 225) return Direction.NORTH;
        return Direction.EAST;
    }

    private float directionToYaw(Direction dir) {
        return switch (dir) {
            case NORTH -> 180;
            case SOUTH -> 0;
            case EAST -> -90;
            case WEST -> 90;
            default -> 0;
        };
    }

    public enum MiningState {
        IDLE,
        CENTERING,
        SCANNING,
        MINING,
        HAZARD_DETECTED,
        ROTATING,
        RETRACING,
        BACKTRACKING,
        STOPPED
    }

    private class BacktrackManager {
        private final java.util.LinkedList<MoveSegment> moveHistory = new java.util.LinkedList<>();
        private MoveSegment currentSegment;
        private boolean isRetracing = false;
        private boolean isBacktracking = false;
        private int retraceBlocks = 0;
        private int backtrackBlocks = 0;

        public static class MoveSegment {
            public final Direction direction;
            public int blocksMoved;
            public final Vec3d startPos;

            public MoveSegment(Direction direction, Vec3d startPos) {
                this.direction = direction;
                this.startPos = startPos;
                this.blocksMoved = 0;
            }
        }

        public static class BacktrackPlan {
            public final boolean needsRetrace;
            public final boolean needsBacktrack;
            public final Direction retraceDirection;
            public final int retraceDistance;
            public final Direction backtrackDirection;
            public final int backtrackDistance;
            public final String description;

            public BacktrackPlan(boolean needsRetrace, boolean needsBacktrack,
                                 Direction retraceDirection, int retraceDistance,
                                 Direction backtrackDirection, int backtrackDistance,
                                 String description) {
                this.needsRetrace = needsRetrace;
                this.needsBacktrack = needsBacktrack;
                this.retraceDirection = retraceDirection;
                this.retraceDistance = retraceDistance;
                this.backtrackDirection = backtrackDirection;
                this.backtrackDistance = backtrackDistance;
                this.description = description;
            }
        }

        public void startNewSegment(Direction direction, Vec3d startPos) {
            if (currentSegment != null && currentSegment.blocksMoved > 0) {
                moveHistory.addLast(currentSegment);
                if (moveHistory.size() > 5) {
                    moveHistory.removeFirst();
                }
            }
            currentSegment = new MoveSegment(direction, startPos);
        }

        public void recordMovement() {
            if (currentSegment != null) {
                currentSegment.blocksMoved++;
            }
        }

        public BacktrackPlan createBacktrackPlan(Direction mainTunnelDirection, int backtrackDistance) {
            if (currentSegment != null && currentSegment.direction == mainTunnelDirection) {
                return new BacktrackPlan(
                    false, true,
                    null, 0,
                    mainTunnelDirection.getOpposite(), backtrackDistance,
                    "Backtrack in main tunnel"
                );
            }

            if (currentSegment != null && currentSegment.blocksMoved > 0) {
                return new BacktrackPlan(
                    true, true,
                    currentSegment.direction.getOpposite(), currentSegment.blocksMoved,
                    mainTunnelDirection.getOpposite(), backtrackDistance,
                    "Retrace side tunnel then backtrack in main"
                );
            }

            return new BacktrackPlan(
                false, true,
                null, 0,
                mainTunnelDirection.getOpposite(), backtrackDistance,
                "Direct backtrack"
            );
        }

        public void startRetrace(int blocks) {
            isRetracing = true;
            retraceBlocks = blocks;
        }

        public void startBacktrack(int blocks) {
            isBacktracking = true;
            backtrackBlocks = blocks;
        }

        public boolean updateRetrace() {
            if (retraceBlocks > 0) {
                retraceBlocks--;
                return retraceBlocks == 0;
            }
            return true;
        }

        public boolean updateBacktrack() {
            if (backtrackBlocks > 0) {
                backtrackBlocks--;
                return backtrackBlocks == 0;
            }
            return true;
        }

        public void reset() {
            isRetracing = false;
            isBacktracking = false;
            retraceBlocks = 0;
            backtrackBlocks = 0;
        }

        public boolean isRetracing() { return isRetracing; }
        public boolean isBacktracking() { return isBacktracking; }
        public int getCurrentSegmentBlocks() { return currentSegment != null ? currentSegment.blocksMoved : 0; }
    }

    private class DirectionManager {
        private Direction currentDirection;
        private Direction lastMovementDirection;

        public final java.util.Map<Direction, Integer> totalBlocksMined = new java.util.HashMap<>();
        private final java.util.Map<Direction, Boolean> activeHazards = new java.util.HashMap<>();
        private final java.util.Map<Direction, Integer> consecutiveHazards = new java.util.HashMap<>();

        private int blocksSinceLastTurn = 0;
        private boolean justBacktracked = false;

        private final java.util.Random random = new java.util.Random();

        public static class DirectionChoice {
            public final Direction direction;
            public final boolean needsBacktrack;
            public final String reason;

            public DirectionChoice(Direction direction, boolean needsBacktrack, String reason) {
                this.direction = direction;
                this.needsBacktrack = needsBacktrack;
                this.reason = reason;
            }
        }

        public DirectionManager() {
            for (Direction dir : getCardinalDirections()) {
                totalBlocksMined.put(dir, 0);
                activeHazards.put(dir, false);
                consecutiveHazards.put(dir, 0);
            }
        }

        public void setInitialDirection(Direction dir) {
            this.currentDirection = dir;
            this.lastMovementDirection = dir;
            this.blocksSinceLastTurn = 0;
            this.justBacktracked = false;
        }

        public Direction getCurrentDirection() {
            return currentDirection;
        }

        public Direction getMainTunnel() {
            Direction mainDir = currentDirection;
            int maxBlocks = 0;

            for (java.util.Map.Entry<Direction, Integer> entry : totalBlocksMined.entrySet()) {
                if (entry.getValue() > maxBlocks) {
                    maxBlocks = entry.getValue();
                    mainDir = entry.getKey();
                }
            }

            return mainDir;
        }

        public DirectionChoice getNextDirection() {
            if (justBacktracked) {
                justBacktracked = false;
                return getNextDirectionAfterBacktrack();
            }

            activeHazards.put(currentDirection, true);
            consecutiveHazards.put(currentDirection, consecutiveHazards.get(currentDirection) + 1);

            boolean immediateHazard = blocksSinceLastTurn < 3;
            Direction mainTunnel = getMainTunnel();

            if (currentDirection == mainTunnel) {
                Direction left = currentDirection.rotateYCounterclockwise();
                Direction right = currentDirection.rotateYClockwise();

                boolean leftClear = !activeHazards.get(left) || totalBlocksMined.get(left) == 0;
                boolean rightClear = !activeHazards.get(right) || totalBlocksMined.get(right) == 0;

                if (leftClear && rightClear) {
                    Direction chosen;
                    if (totalBlocksMined.get(left) < totalBlocksMined.get(right)) {
                        chosen = left;
                    } else if (totalBlocksMined.get(right) < totalBlocksMined.get(left)) {
                        chosen = right;
                    } else {
                        chosen = random.nextBoolean() ? left : right;
                    }

                    lastMovementDirection = currentDirection;
                    currentDirection = chosen;
                    blocksSinceLastTurn = 0;
                    return new DirectionChoice(chosen, false, "Side tunnel from main");
                } else if (leftClear) {
                    lastMovementDirection = currentDirection;
                    currentDirection = left;
                    blocksSinceLastTurn = 0;
                    return new DirectionChoice(left, false, "Left clear from main");
                } else if (rightClear) {
                    lastMovementDirection = currentDirection;
                    currentDirection = right;
                    blocksSinceLastTurn = 0;
                    return new DirectionChoice(right, false, "Right clear from main");
                } else {
                    return new DirectionChoice(null, true, "Backtrack in main tunnel");
                }
            } else {
                if (immediateHazard) {
                    Direction opposite = lastMovementDirection.getOpposite();
                    Direction otherPerpendicular = null;

                    for (Direction dir : getCardinalDirections()) {
                        if (dir != currentDirection && dir != lastMovementDirection && dir != opposite) {
                            otherPerpendicular = dir;
                            break;
                        }
                    }

                    if (otherPerpendicular != null && !activeHazards.get(otherPerpendicular)) {
                        currentDirection = otherPerpendicular;
                        blocksSinceLastTurn = 0;
                        return new DirectionChoice(otherPerpendicular, false, "Other perpendicular after immediate hazard");
                    }
                }

                if (totalBlocksMined.get(currentDirection) > 10 && !immediateHazard) {
                    blocksSinceLastTurn = 0;
                    activeHazards.put(currentDirection, false);
                    return new DirectionChoice(currentDirection, false, "Continue established side tunnel");
                }

                if (!activeHazards.get(mainTunnel) || consecutiveHazards.get(mainTunnel) < 2) {
                    lastMovementDirection = currentDirection;
                    currentDirection = mainTunnel;
                    blocksSinceLastTurn = 0;
                    activeHazards.put(mainTunnel, false);
                    consecutiveHazards.put(mainTunnel, 0);
                    return new DirectionChoice(mainTunnel, false, "Return to main tunnel");
                }

                Direction opposite = mainTunnel.getOpposite();
                if (!activeHazards.get(opposite)) {
                    lastMovementDirection = currentDirection;
                    currentDirection = opposite;
                    blocksSinceLastTurn = 0;
                    return new DirectionChoice(opposite, false, "Opposite of main tunnel");
                }

                return new DirectionChoice(null, true, "Backtrack in side tunnel");
            }
        }

        public DirectionChoice getNextDirectionAfterBacktrack() {
            activeHazards.put(currentDirection, false);
            consecutiveHazards.put(currentDirection, 0);

            Direction left = currentDirection.rotateYCounterclockwise();
            Direction right = currentDirection.rotateYClockwise();

            activeHazards.put(left, false);
            activeHazards.put(right, false);

            if (totalBlocksMined.get(left) < totalBlocksMined.get(right)) {
                currentDirection = left;
                blocksSinceLastTurn = 0;
                return new DirectionChoice(left, false, "Left after backtrack");
            } else if (totalBlocksMined.get(right) < totalBlocksMined.get(left)) {
                currentDirection = right;
                blocksSinceLastTurn = 0;
                return new DirectionChoice(right, false, "Right after backtrack");
            } else {
                Direction chosen = random.nextBoolean() ? left : right;
                currentDirection = chosen;
                blocksSinceLastTurn = 0;
                return new DirectionChoice(chosen, false, "Random after backtrack");
            }
        }

        public void recordMovement(int blocks) {
            totalBlocksMined.put(currentDirection, totalBlocksMined.get(currentDirection) + blocks);
            blocksSinceLastTurn += blocks;
        }

        public void recordHazard(Direction dir, int blocksMined) {
            activeHazards.put(dir, true);
        }

        public void markBacktrackComplete() {
            justBacktracked = true;
        }

        private Direction[] getCardinalDirections() {
            return new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
        }
    }

    private class PathScanner {
        private int scanWidthFallingBlocks = 0; // Set to 0 for 1x1 mining
        private int scanWidthFluids = 0; // Set to 0 for 1x1 mining

        public void updateScanWidths(int fallingBlocks, int fluids) {
            this.scanWidthFallingBlocks = fallingBlocks;
            this.scanWidthFluids = fluids;
        }

        public static class ScanResult {
            private final boolean safe;
            private final HazardType hazardType;
            private final int hazardDistance;

            public ScanResult(boolean safe, HazardType hazardType, int hazardDistance) {
                this.safe = safe;
                this.hazardType = hazardType;
                this.hazardDistance = hazardDistance;
            }

            public boolean isSafe() { return safe; }
            public HazardType getHazardType() { return hazardType; }
            public int getHazardDistance() { return hazardDistance; }
        }

        public enum HazardType {
            NONE,
            LAVA,
            WATER,
            FALLING_BLOCK,
            UNSAFE_GROUND,
            DANGEROUS_BLOCK
        }

        public ScanResult scanDirection(BlockPos start, Direction direction, int depth, int height, boolean strictGround) {
            // For 1x1 mining, we only scan directly ahead
            int scanWidth = 0;

            for (int forward = 1; forward <= depth; forward++) {
                for (int sideways = -scanWidth; sideways <= scanWidth; sideways++) {
                    for (int vertical = -1; vertical <= height; vertical++) {
                        BlockPos checkPos = offsetPosition(start, direction, forward, sideways, vertical);
                        net.minecraft.block.BlockState state = mc.world.getBlockState(checkPos);

                        HazardType fluidHazard = checkForFluids(state);
                        if (fluidHazard != HazardType.NONE) {
                            return new ScanResult(false, fluidHazard, forward);
                        }

                        HazardType hazard = checkBlock(checkPos, vertical, strictGround, forward, sideways, true);
                        if (hazard != HazardType.NONE) {
                            return new ScanResult(false, hazard, forward);
                        }
                    }
                }
            }

            return new ScanResult(true, HazardType.NONE, -1);
        }

        private HazardType checkForFluids(net.minecraft.block.BlockState state) {
            net.minecraft.block.Block block = state.getBlock();

            if (block == net.minecraft.block.Blocks.LAVA ||
                state.getFluidState().getFluid() == net.minecraft.fluid.Fluids.LAVA ||
                state.getFluidState().getFluid() == net.minecraft.fluid.Fluids.FLOWING_LAVA) {
                return HazardType.LAVA;
            }

            if (block == net.minecraft.block.Blocks.WATER ||
                state.getFluidState().getFluid() == net.minecraft.fluid.Fluids.WATER ||
                state.getFluidState().getFluid() == net.minecraft.fluid.Fluids.FLOWING_WATER) {
                return HazardType.WATER;
            }

            return HazardType.NONE;
        }

        private HazardType checkBlock(BlockPos pos, int yOffset, boolean strictGround, int forwardDist, int sidewaysDist, boolean checkFalling) {
            net.minecraft.block.BlockState state = mc.world.getBlockState(pos);
            net.minecraft.block.Block block = state.getBlock();

            if (yOffset == -1 && sidewaysDist == 0) {
                if (!canWalkOn(pos, state)) {
                    return HazardType.UNSAFE_GROUND;
                }
            }

            if (checkFalling && yOffset >= 0 && isFallingBlock(block)) {
                return HazardType.FALLING_BLOCK;
            }

            if (isDangerousBlock(block)) {
                return HazardType.DANGEROUS_BLOCK;
            }

            return HazardType.NONE;
        }

        private boolean canWalkOn(BlockPos pos, net.minecraft.block.BlockState state) {
            if (state.isAir()) return false;
            if (!state.isSolidBlock(mc.world, pos)) return false;

            net.minecraft.block.Block block = state.getBlock();
            if (block == net.minecraft.block.Blocks.MAGMA_BLOCK ||
                block == net.minecraft.block.Blocks.CAMPFIRE ||
                block == net.minecraft.block.Blocks.SOUL_CAMPFIRE) {
                return false;
            }

            return state.isFullCube(mc.world, pos) ||
                block instanceof net.minecraft.block.SlabBlock ||
                block instanceof net.minecraft.block.StairsBlock;
        }

        private boolean isFallingBlock(net.minecraft.block.Block block) {
            return block == net.minecraft.block.Blocks.SAND ||
                block == net.minecraft.block.Blocks.RED_SAND ||
                block == net.minecraft.block.Blocks.GRAVEL ||
                block == net.minecraft.block.Blocks.ANVIL ||
                block == net.minecraft.block.Blocks.CHIPPED_ANVIL ||
                block == net.minecraft.block.Blocks.DAMAGED_ANVIL ||
                block == net.minecraft.block.Blocks.POINTED_DRIPSTONE ||
                block == net.minecraft.block.Blocks.DRIPSTONE_BLOCK;
        }

        private boolean isDangerousBlock(net.minecraft.block.Block block) {
            return block == net.minecraft.block.Blocks.TNT ||
                block == net.minecraft.block.Blocks.FIRE ||
                block == net.minecraft.block.Blocks.SOUL_FIRE ||
                block == net.minecraft.block.Blocks.MAGMA_BLOCK ||
                block == net.minecraft.block.Blocks.WITHER_ROSE ||
                block == net.minecraft.block.Blocks.SWEET_BERRY_BUSH ||
                block == net.minecraft.block.Blocks.POINTED_DRIPSTONE ||
                block == net.minecraft.block.Blocks.POWDER_SNOW ||
                block == net.minecraft.block.Blocks.CACTUS;
        }

        private BlockPos offsetPosition(BlockPos start, Direction forward, int forwardDist, int sidewaysDist, int verticalDist) {
            Direction left = forward.rotateYCounterclockwise();

            return start
                .offset(forward, forwardDist)
                .offset(left, sidewaysDist)
                .offset(Direction.UP, verticalDist);
        }
    }

    private class RotationController {
        private final java.util.Random random = new java.util.Random();

        private float currentYaw;
        private float targetYaw;
        private float currentRotationSpeed;
        private boolean isRotating = false;
        private Runnable callback;
        private int rotationTickCounter = 0;

        private int overshootTicks = 0;
        private float overshootAmount = 0;

        private boolean smoothRotation = true;
        private double baseSpeed = 3.0;
        private double acceleration = 0.5;
        private boolean humanLike = true;
        private double overshootChance = 0.2;

        public void updateSettings(boolean smooth, double speed, double accel, boolean human, double overshoot) {
            this.smoothRotation = smooth;
            this.baseSpeed = speed;
            this.acceleration = accel;
            this.humanLike = human;
            this.overshootChance = overshoot;
        }

        public void startRotation(float targetYaw, Runnable onComplete) {
            this.targetYaw = targetYaw;
            this.callback = onComplete;

            if (!smoothRotation) {
                if (!packetSentThisTick) {
                    setYawAngle(targetYaw);
                    packetSentThisTick = true;
                    sendInfo("Instant rotation to yaw " + targetYaw);
                    if (callback != null) callback.run();
                }
                return;
            }

            isRotating = true;
            currentYaw = mc.player.getYaw();
            currentRotationSpeed = 0;
            rotationTickCounter = 0;

            if (humanLike && random.nextDouble() < overshootChance) {
                float totalRotation = net.minecraft.util.math.MathHelper.wrapDegrees(targetYaw - currentYaw);
                overshootAmount = (2 + random.nextFloat() * 3) * (totalRotation > 0 ? 1 : -1);
                overshootTicks = 8 + random.nextInt(10);
            } else {
                overshootAmount = 0;
                overshootTicks = 0;
            }
            sendInfo("Starting smooth rotation to yaw " + targetYaw);
        }

        public void update() {
            if (!isRotating || rotationLockoutTicks > 0 || packetSentThisTick) return;

            rotationTickCounter++;
            if (rotationTickCounter % 2 != 0) return;

            float actualTarget = targetYaw;
            if (overshootTicks > 0) {
                actualTarget = targetYaw + overshootAmount;
                overshootTicks--;
            }

            float deltaAngle = net.minecraft.util.math.MathHelper.wrapDegrees(actualTarget - currentYaw);
            float distance = Math.abs(deltaAngle);

            if (distance < 0.1) {
                setYawAngle(targetYaw);
                isRotating = false;
                packetSentThisTick = true;
                sendInfo("Rotation complete to yaw " + targetYaw);
                if (callback != null) {
                    callback.run();
                }
                return;
            }

            float targetSpeed;
            if (distance > 45) {
                targetSpeed = (float)(baseSpeed * 1.2);
            } else if (distance > 15) {
                targetSpeed = (float)baseSpeed;
            } else {
                targetSpeed = (float)(baseSpeed * (distance / 15));
                targetSpeed = Math.max(targetSpeed, 0.3f);
            }

            float accel = (float)acceleration;
            if (currentRotationSpeed < targetSpeed) {
                currentRotationSpeed = Math.min(currentRotationSpeed + accel, targetSpeed);
            } else {
                currentRotationSpeed = Math.max(currentRotationSpeed - accel, targetSpeed);
            }

            float jitter = 0;
            float speedVariation = 1;
            if (humanLike) {
                jitter = (random.nextFloat() - 0.5f) * 0.1f;
                speedVariation = 0.95f + random.nextFloat() * 0.1f;

                if (random.nextFloat() < 0.01 && distance > 10) {
                    currentRotationSpeed *= 0.5f;
                }
            }

            float step = Math.min(Math.min(distance, currentRotationSpeed * speedVariation), 5.0f);
            if (deltaAngle < 0) step = -step;

            currentYaw += step + jitter;
            setYawAngle(currentYaw);
            packetSentThisTick = true;
            sendInfo("Rotating: current yaw " + currentYaw + ", target " + targetYaw);

            if (distance < 0.5f || (overshootTicks == 0 && distance < 1.5)) {
                setYawAngle(targetYaw);
                isRotating = false;
                packetSentThisTick = true;
                sendInfo("Rotation complete to yaw " + targetYaw);
                if (callback != null) {
                    callback.run();
                }
            }
        }

        private void setYawAngle(float yawAngle) {
            mc.player.setYaw(yawAngle);
            mc.player.headYaw = yawAngle;
            mc.player.bodyYaw = yawAngle;
        }

        public boolean isRotating() { return isRotating; }
    }

    private class SafetyValidator {
        private Vec3d lastPosition;
        private int stuckTicks = 0;
        private static final int STUCK_THRESHOLD = 60;

        public boolean canContinue(net.minecraft.entity.player.PlayerEntity player, int maxY) {
            if (player.getY() > maxY) {
                return false;
            }

            if (!player.isOnGround()) {
                return false;
            }

            if (player.getHealth() < 10) {
                return false;
            }

            ItemStack mainHand = player.getMainHandStack();
            if (isPickaxe(mainHand)) {
                int durability = mainHand.getMaxDamage() - mainHand.getDamage();
                if (durability < 100) {
                    return false;
                }
            }

            return true;
        }

        public boolean checkStuck(net.minecraft.entity.player.PlayerEntity player) {
            Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());

            if (lastPosition == null) {
                lastPosition = currentPos;
                return false;
            }

            double distance = currentPos.distanceTo(lastPosition);

            if (distance < 0.1) {
                stuckTicks++;
                if (stuckTicks >= STUCK_THRESHOLD) {
                    stuckTicks = 0;
                    return true;
                }
            } else {
                stuckTicks = 0;
                lastPosition = currentPos;
            }

            return false;
        }

        public void reset() {
            stuckTicks = 0;
            lastPosition = null;
        }

        public boolean isInValidMiningArea(net.minecraft.entity.player.PlayerEntity player) {
            return player.getY() <= -50 && player.isOnGround();
        }

        public boolean hasValidTool(net.minecraft.entity.player.PlayerEntity player) {
            ItemStack mainHand = player.getMainHandStack();
            return isPickaxe(mainHand);
        }
    }
}
