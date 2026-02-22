package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.text.Text;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

public class PremiumTunnelBaseFinder extends Module {
    // just the pickaxe types we support
    public enum PickaxeType {
        NORMAL("Normal Pickaxe", "Mines 2x2 tunnel"),
        AMETHYST("ᴀᴍᴇᴛʜʏѕᴛ ᴘɪᴄᴋᴀхᴇ", "Mines 3x3 area (only target center block)");

        private final String displayName;
        private final String description;

        PickaxeType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }


    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPickaxe = settings.createGroup("Pickaxe");
    private final SettingGroup sgMining = settings.createGroup("Mining");
    private final SettingGroup sgAntiStuck = settings.createGroup("Anti-Stuck");
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgLavaDetection = settings.createGroup("Enhanced Lava Detection");
    private final SettingGroup sgPearlThrough = settings.createGroup("Pearl-Through");
    private final SettingGroup sgYLevel = settings.createGroup("Y-Level Control");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");
    private final SettingGroup sgPlayerDetection = settings.createGroup("Player Detection");

    // basic settings
    private final Setting<Boolean> infiniteTunnel = sgGeneral.add(new BoolSetting.Builder()
        .name("infinite-tunnel")
        .description("Tunnel infinitely until manually stopped.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> tunnelLength = sgGeneral.add(new IntSetting.Builder()
        .name("tunnel-length")
        .description("How many blocks to tunnel before stopping.")
        .defaultValue(100)
        .min(1)
        .max(10000)
        .visible(() -> !infiniteTunnel.get())
        .build()
    );

    // pickaxe stuff
    private final Setting<PickaxeType> pickaxeType = sgPickaxe.add(new EnumSetting.Builder<PickaxeType>()
        .name("pickaxe-type")
        .description("Type of pickaxe being used.")
        .defaultValue(PickaxeType.NORMAL)
        .build()
    );

    private final Setting<Double> lookSmoothnessFactor = sgPickaxe.add(new DoubleSetting.Builder()
        .name("look-smoothness-factor")
        .description("Multiplier for look smoothness (higher = smoother/slower movement).")
        .defaultValue(1.5)
        .min(0.1)
        .max(5.0)
        .sliderMin(0.1)
        .sliderMax(3.0)
        .build()
    );

    private final Setting<Boolean> adaptiveSmoothing = sgPickaxe.add(new BoolSetting.Builder()
        .name("adaptive-smoothing")
        .description("Automatically adjust smoothness based on pickaxe type.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> naturalMiningLook = sgPickaxe.add(new BoolSetting.Builder()
        .name("natural-mining-look")
        .description("Use more natural looking patterns when mining (anti-baritone detection).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> lookRandomization = sgPickaxe.add(new DoubleSetting.Builder()
        .name("look-randomization")
        .description("Amount of randomization in look direction (makes it more human-like).")
        .defaultValue(2.0)
        .min(0.0)
        .max(10.0)
        .visible(() -> naturalMiningLook.get())
        .build()
    );

    // lava detection stuff (the fancy 3D scanning)
    private final Setting<Integer> lavaDetectionRange = sgLavaDetection.add(new IntSetting.Builder()
        .name("lava-detection-range")
        .description("How far ahead to scan for lava (blocks).")
        .defaultValue(8)
        .min(3)
        .max(20)
        .build()
    );

    private final Setting<Integer> lavaDetectionWidth = sgLavaDetection.add(new IntSetting.Builder()
        .name("lava-detection-width")
        .description("How many blocks to each side to scan for lava.")
        .defaultValue(2)
        .min(1)
        .max(5)
        .build()
    );

    private final Setting<Integer> lavaDetectionHeight = sgLavaDetection.add(new IntSetting.Builder()
        .name("lava-detection-height")
        .description("How many blocks above and below to scan for lava.")
        .defaultValue(3)
        .min(1)
        .max(8)
        .build()
    );

    private final Setting<Boolean> debugLavaDetection = sgLavaDetection.add(new BoolSetting.Builder()
        .name("debug-lava-detection")
        .description("Show detailed lava detection debug messages.")
        .defaultValue(false)
        .build()
    );

    // pearl through feature (for caves and stuff)
    private final Setting<Boolean> enablePearlThrough = sgPearlThrough.add(new BoolSetting.Builder()
        .name("enable-pearl-through")
        .description("Enable pearl-through feature for empty areas (caves).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> emptyAreaThreshold = sgPearlThrough.add(new DoubleSetting.Builder()
        .name("empty-area-threshold")
        .description("Percentage of air blocks required to activate pearl-through (0.0-1.0).")
        .defaultValue(0.7)
        .min(0.5)
        .max(0.95)
        .visible(() -> enablePearlThrough.get())
        .build()
    );

    private final Setting<Integer> pearlThroughCooldown = sgPearlThrough.add(new IntSetting.Builder()
        .name("pearl-through-cooldown")
        .description("Cooldown in ticks before resuming mining after pearl-through.")
        .defaultValue(60)
        .min(20)
        .max(200)
        .visible(() -> enablePearlThrough.get())
        .build()
    );

    private final Setting<Integer> emptyAreaScanRadius = sgPearlThrough.add(new IntSetting.Builder()
        .name("empty-area-scan-radius")
        .description("Radius to scan around player for empty area detection.")
        .defaultValue(3)
        .min(2)
        .max(6)
        .visible(() -> enablePearlThrough.get())
        .build()
    );

    // backup system settings (when we get stuck)
    private final Setting<Integer> backupStuckTimeout = sgAntiStuck.add(new IntSetting.Builder()
        .name("backup-stuck-timeout")
        .description("Ticks to wait before breaking blocks during backup.")
        .defaultValue(40)
        .min(20)
        .max(100)
        .build()
    );

    private final Setting<Boolean> randomizeAvoidanceDirection = sgAntiStuck.add(new BoolSetting.Builder()
        .name("randomize-avoidance-direction")
        .description("Randomly choose left or right for avoidance maneuvers.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> breakBlockingStones = sgAntiStuck.add(new BoolSetting.Builder()
        .name("break-blocking-stones")
        .description("Automatically break blocks that block backup path.")
        .defaultValue(true)
        .build()
    );

    // y level stuff (keeping the tunnel straight)
    private final Setting<Boolean> maintainYLevel = sgYLevel.add(new BoolSetting.Builder()
        .name("maintain-y-level")
        .description("Maintain the Y-level where mining started.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoPlaceBlocks = sgYLevel.add(new BoolSetting.Builder()
        .name("auto-place-blocks")
        .description("Automatically place blocks to maintain Y-level.")
        .defaultValue(true)
        .visible(() -> maintainYLevel.get())
        .build()
    );

    private final Setting<Integer> yLevelTolerance = sgYLevel.add(new IntSetting.Builder()
        .name("y-level-tolerance")
        .description("How many blocks above/below start Y-level is acceptable.")
        .defaultValue(1)
        .min(0)
        .max(5)
        .visible(() -> maintainYLevel.get())
        .build()
    );

    // safety stuff (avoiding death basically)
    private final Setting<Boolean> avoidLava = sgSafety.add(new BoolSetting.Builder()
        .name("avoid-lava")
        .description("Detect and avoid lava by backing up and going around.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectLavaVertical = sgSafety.add(new BoolSetting.Builder()
        .name("detect-lava-vertical")
        .description("Detect lava above and below player position.")
        .defaultValue(true)
        .visible(() -> avoidLava.get())
        .build()
    );

    private final Setting<Integer> verticalLavaRange = sgSafety.add(new IntSetting.Builder()
        .name("vertical-lava-range")
        .description("How many blocks above/below to check for lava.")
        .defaultValue(3)
        .min(1)
        .max(10)
        .visible(() -> avoidLava.get() && detectLavaVertical.get())
        .build()
    );

    private final Setting<Boolean> avoidGravel = sgSafety.add(new BoolSetting.Builder()
        .name("avoid-gravel")
        .description("Detect and avoid falling blocks (gravel/sand).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> safetyBackupDistance = sgSafety.add(new IntSetting.Builder()
        .name("safety-backup-distance")
        .description("How many blocks to back up when lava/gravel detected.")
        .defaultValue(4)
        .min(2)
        .max(10)
        .visible(() -> avoidLava.get() || avoidGravel.get())
        .build()
    );

    private final Setting<Integer> lavaAvoidanceDistance = sgSafety.add(new IntSetting.Builder()
        .name("lava-avoidance-distance")
        .description("How many blocks to mine sideways when avoiding lava.")
        .defaultValue(6)
        .min(3)
        .max(15)
        .visible(() -> avoidLava.get())
        .build()
    );

    private final Setting<Boolean> avoidCaves = sgSafety.add(new BoolSetting.Builder()
        .name("avoid-caves")
        .description("Avoid large air pockets (caves).")
        .defaultValue(false)
        .build()
    );

    // discord webhook stuff (for screenshots and notifications)
    private final Setting<Boolean> enableWebhook = sgWebhook.add(new BoolSetting.Builder()
        .name("enable-webhook")
        .description("Enable Discord webhook notifications with screenshots.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL for notifications.")
        .defaultValue("YOUR_WEBHOOK_URL_HERE")
        .visible(() -> enableWebhook.get())
        .onChanged(this::validateWebhookUrl)
        .build()
    );

    private final Setting<Boolean> screenshotOnStuck = sgWebhook.add(new BoolSetting.Builder()
        .name("screenshot-on-stuck")
        .description("Take screenshot when stuck and send to webhook.")
        .defaultValue(true)
        .visible(() -> enableWebhook.get())
        .build()
    );

    private final Setting<Boolean> screenshotOnDanger = sgWebhook.add(new BoolSetting.Builder()
        .name("screenshot-on-danger")
        .description("Take screenshot when lava/gravel detected.")
        .defaultValue(true)
        .visible(() -> enableWebhook.get())
        .build()
    );

    private final Setting<Boolean> screenshotOnComplete = sgWebhook.add(new BoolSetting.Builder()
        .name("screenshot-on-complete")
        .description("Take screenshot when tunneling completes.")
        .defaultValue(true)
        .visible(() -> enableWebhook.get())
        .build()
    );

    private final Setting<Boolean> testWebhook = sgWebhook.add(new BoolSetting.Builder()
        .name("test-webhook")
        .description("Test webhook with screenshot (automatically resets).")
        .defaultValue(false)
        .visible(() -> enableWebhook.get())
        .onChanged(this::onTestWebhookChanged)
        .build()
    );

    private final Setting<Boolean> debugMode = sgWebhook.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Enable debug messages for webhook operations.")
        .defaultValue(false)
        .visible(() -> enableWebhook.get())
        .build()
    );

    // player detection (for staff and stuff)
    private final Setting<Boolean> enablePlayerDetection = sgPlayerDetection.add(new BoolSetting.Builder()
        .name("enable-player-detection")
        .description("Enable detection of staff members and suspicious players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectGreenNames = sgPlayerDetection.add(new BoolSetting.Builder()
        .name("detect-green-names")
        .description("Detect players with green names (usually mods).")
        .defaultValue(true)
        .visible(() -> enablePlayerDetection.get())
        .build()
    );

    private final Setting<Boolean> detectGoldNames = sgPlayerDetection.add(new BoolSetting.Builder()
        .name("detect-gold-names")
        .description("Detect players with gold/yellow names (usually admins).")
        .defaultValue(true)
        .visible(() -> enablePlayerDetection.get())
        .build()
    );

    private final Setting<Boolean> detectPurpleNames = sgPlayerDetection.add(new BoolSetting.Builder()
        .name("detect-purple-names")
        .description("Detect players with purple names (usually owners/high staff).")
        .defaultValue(true)
        .visible(() -> enablePlayerDetection.get())
        .build()
    );

    private final Setting<Boolean> ignorePlusRankers = sgPlayerDetection.add(new BoolSetting.Builder()
        .name("ignore-plus-rankers")
        .description("Ignore blue names with + symbols (plus rank holders).")
        .defaultValue(true)
        .visible(() -> enablePlayerDetection.get())
        .build()
    );

    private final Setting<Double> detectionRange = sgPlayerDetection.add(new DoubleSetting.Builder()
        .name("detection-range")
        .description("Range in blocks to detect suspicious players.")
        .defaultValue(50.0)
        .min(10.0)
        .max(200.0)
        .visible(() -> enablePlayerDetection.get())
        .build()
    );

    private final Setting<Integer> randomLookDuration = sgPlayerDetection.add(new IntSetting.Builder()
        .name("random-look-duration")
        .description("How long to look around randomly before staring (in ticks).")
        .defaultValue(60)
        .min(20)
        .max(200)
        .visible(() -> enablePlayerDetection.get())
        .build()
    );

    private final Setting<Integer> directStareDuration = sgPlayerDetection.add(new IntSetting.Builder()
        .name("direct-stare-duration")
        .description("How long to stare directly at the detected player (in ticks).")
        .defaultValue(100)
        .min(40)
        .max(300)
        .visible(() -> enablePlayerDetection.get())
        .build()
    );

    private final Setting<Boolean> pauseMiningOnDetection = sgPlayerDetection.add(new BoolSetting.Builder()
        .name("pause-mining-on-detection")
        .description("Pause mining when a suspicious player is detected.")
        .defaultValue(true)
        .visible(() -> enablePlayerDetection.get())
        .build()
    );

    private final Setting<Boolean> moveAwayOnDetection = sgPlayerDetection.add(new BoolSetting.Builder()
        .name("move-away-on-detection")
        .description("Automatically move away from detected players.")
        .defaultValue(true)
        .visible(() -> enablePlayerDetection.get())
        .build()
    );

    private final Setting<Integer> moveAwayDistance = sgPlayerDetection.add(new IntSetting.Builder()
        .name("move-away-distance")
        .description("How many blocks to move away from detected players.")
        .defaultValue(20)
        .min(5)
        .max(50)
        .visible(() -> enablePlayerDetection.get() && moveAwayOnDetection.get())
        .build()
    );

    private final Setting<Boolean> screenshotOnPlayerDetection = sgPlayerDetection.add(new BoolSetting.Builder()
        .name("screenshot-on-player-detection")
        .description("Take screenshot when suspicious player detected.")
        .defaultValue(true)
        .visible(() -> enablePlayerDetection.get() && enableWebhook.get())
        .build()
    );

    private final Setting<Boolean> debugPlayerDetection = sgPlayerDetection.add(new BoolSetting.Builder()
        .name("debug-player-detection")
        .description("Show debug messages for player detection.")
        .defaultValue(false)
        .visible(() -> enablePlayerDetection.get())
        .build()
    );

    private final Setting<Integer> lookSmoothness = sgPlayerDetection.add(new IntSetting.Builder()
        .name("look-smoothness")
        .description("How smooth head movement is (1=fastest, 10=smoothest/most human-like).")
        .defaultValue(7)
        .min(1)
        .max(10)
        .visible(() -> enablePlayerDetection.get())
        .build()
    );

    // mining stuff
    private final Setting<Boolean> holdLeftClick = sgMining.add(new BoolSetting.Builder()
        .name("hold-left-click")
        .description("Hold left mouse button to mine continuously.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> mineDelay = sgMining.add(new DoubleSetting.Builder()
        .name("mine-delay")
        .description("Delay between mining actions in ticks.")
        .defaultValue(1.0)
        .min(0.0)
        .max(20.0)
        .build()
    );

    private final Setting<Boolean> autoRightClick = sgMining.add(new BoolSetting.Builder()
        .name("auto-right-click")
        .description("Automatically right-click to fix ghost blocks based on ping.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> rightClickInterval = sgMining.add(new IntSetting.Builder()
        .name("right-click-interval")
        .description("Ticks between right-clicks (lower = more frequent for higher ping).")
        .defaultValue(40)
        .min(5)
        .max(200)
        .visible(() -> autoRightClick.get())
        .build()
    );

    // anti stuck stuff (when we get stuck)
    private final Setting<Integer> stuckThreshold = sgAntiStuck.add(new IntSetting.Builder()
        .name("stuck-threshold")
        .description("Ticks without progress before considering stuck.")
        .defaultValue(100)
        .min(20)
        .max(500)
        .build()
    );

    private final Setting<Boolean> antiStuckMovement = sgAntiStuck.add(new BoolSetting.Builder()
        .name("anti-stuck-movement")
        .description("Move left/right when stuck to avoid obstacles.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> antiStuckDistance = sgAntiStuck.add(new IntSetting.Builder()
        .name("anti-stuck-distance")
        .description("How many blocks to move sideways when stuck.")
        .defaultValue(3)
        .min(1)
        .max(10)
        .visible(() -> antiStuckMovement.get())
        .build()
    );

    private final Setting<Boolean> improvedBackupLogic = sgAntiStuck.add(new BoolSetting.Builder()
        .name("improved-backup-logic")
        .description("Use improved backup logic that checks for obstacles.")
        .defaultValue(true)
        .build()
    );

    // rendering stuff (what you see on screen)
    private final Setting<Boolean> renderTargetBlock = sgRender.add(new BoolSetting.Builder()
        .name("render-target-block")
        .description("Renders the blocks being targeted for mining.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> targetBlockColor = sgRender.add(new ColorSetting.Builder()
        .name("target-block-color")
        .description("Color of the target block render.")
        .defaultValue(new SettingColor(255, 0, 0, 100))
        .build()
    );

    private final Setting<Boolean> renderPath = sgRender.add(new BoolSetting.Builder()
        .name("render-path")
        .description("Show the planned tunnel path.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> pathColor = sgRender.add(new ColorSetting.Builder()
        .name("path-color")
        .description("Color of the path render.")
        .defaultValue(new SettingColor(0, 255, 0, 50))
        .visible(() -> renderPath.get())
        .build()
    );

    private final Setting<Boolean> renderYLevel = sgRender.add(new BoolSetting.Builder()
        .name("render-y-level")
        .description("Show the target Y-level line.")
        .defaultValue(true)
        .visible(() -> maintainYLevel.get())
        .build()
    );

    private final Setting<SettingColor> yLevelColor = sgRender.add(new ColorSetting.Builder()
        .name("y-level-color")
        .description("Color of the Y-level render.")
        .defaultValue(new SettingColor(255, 255, 0, 80))
        .visible(() -> maintainYLevel.get() && renderYLevel.get())
        .build()
    );

    private final Setting<Boolean> renderLavaDetection = sgRender.add(new BoolSetting.Builder()
        .name("render-lava-detection")
        .description("Show lava detection scan area.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> lavaDetectionColor = sgRender.add(new ColorSetting.Builder()
        .name("lava-detection-color")
        .description("Color of the lava detection area render.")
        .defaultValue(new SettingColor(255, 100, 0, 30))
        .visible(() -> renderLavaDetection.get())
        .build()
    );

    // keeping track of stuff
    private BlockPos startPos;
    private int startYLevel;
    private Direction originalDirection;
    private Direction currentDirection;
    private int blocksMined;
    private boolean isStuck = false;
    private boolean isAvoiding = false;
    private boolean isBackingUp = false;
    private boolean shouldDisable = false;

    // lava avoidance state stuff
    private boolean isLavaAvoidance = false;
    private int lavaAvoidancePhase = 0;
    private Direction lavaAvoidanceDirection = null;
    private BlockPos lavaAvoidanceStartPos = null;
    private int lavaAvoidanceSteps = 0;
    private BlockPos detectedLavaPos = null;
    private boolean rightSideChecked = false;
    private boolean leftSideChecked = false;
    private boolean rightSideSafe = false;
    private boolean leftSideSafe = false;
    private boolean tryingBackward = false;
    private boolean turning180 = false;
    private BlockPos backwardBlockToBreak = null;
    private int backwardBreakAttempts = 0;

    // pearl through state stuff
    private boolean isPearlThroughActive = false;
    private int pearlThroughCooldownTicks = 0;
    private boolean emptyAreaDetected = false;
    private long lastMiddleClickTime = 0;

    // backup system state stuff
    private boolean isBreakingBlockingStones = false;
    private int backupStuckTicks = 0;
    private BlockPos backupBlockingStone = null;
    private boolean hasRandomizedAvoidanceDirection = false;

    // y level maintenance stuff
    private boolean isAdjustingYLevel = false;
    private int yLevelAdjustmentSteps = 0;
    private boolean needsBlockPlacement = false;
    private BlockPos blockPlacementTarget = null;

    // timing stuff (when did we last do things)
    private long lastMineTime = 0;
    private long lastRightClickTime = 0;
    private long lastMovementTime = 0;
    private long lastLookChangeTime = 0;

    // stuck detection stuff
    private int ticksStuck = 0;
    private BlockPos lastPlayerPos;
    private int lastBlocksMined = 0;
    private boolean alreadyReportedStuck = false;
    private int consecutiveBackupAttempts = 0;

    // avoidance state stuff
    private int avoidanceSteps = 0;
    private int backupSteps = 0;
    private BlockPos dangerousBlockPos = null;
    private String dangerType = "";
    private BlockPos backupStartPos = null;

    // what blocks we're trying to mine
    private List<BlockPos> blocksToMine = new ArrayList<>();
    private BlockPos currentTarget = null;
    private BlockPos lastTargetBlock = null;

    // player detection state stuff
    private boolean playerDetected = false;
    private String detectedPlayerName = "";
    private Vec3d detectedPlayerPos = null;
    private int randomLookTicks = 0;
    private int directStareTicks = 0;
    private boolean isRandomLooking = false;
    private boolean isDirectStaring = false;
    private boolean isMovingAway = false;
    private int moveAwaySteps = 0;
    private Direction moveAwayDirection = null;
    private BlockPos playerDetectionStartPos = null;
    private float originalYaw = 0.0f;
    private float originalPitch = 0.0f;
    private float randomYaw = 0.0f;
    private float randomPitch = 0.0f;
    private long lastPlayerDetectionTime = 0;
    private boolean wasAdaptiveSmoothingEnabled = true;

    // smooth looking stuff (making it look human)
    private float currentYaw = 0.0f;
    private float currentPitch = 0.0f;
    private float targetYaw = 0.0f;
    private float targetPitch = 0.0f;
    private boolean isSmoothLooking = false;
    private boolean isMiningLook = false;
    private float lastLookYaw = 0.0f;
    private float lastLookPitch = 0.0f;
    private int naturalLookTimer = 0;

    // webhook status stuff
    private String webhookStatus = "Not configured";
    private boolean isValidWebhook = false;

    public PremiumTunnelBaseFinder() {
        super(GlazedAddon.CATEGORY, "premium-tunnel-base-finder", "Advanced tunnel mining with enhanced lava detection, pearl-through feature, and improved safety systems.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;

        startPos = mc.player.getBlockPos();
        startYLevel = startPos.getY();
        originalDirection = mc.player.getHorizontalFacing();
        currentDirection = originalDirection;
        blocksMined = 0;

        // reset everything
        resetAllStates();

        // remember if adaptive smoothing was on
        wasAdaptiveSmoothingEnabled = adaptiveSmoothing.get();

        // check if webhook is valid
        validateWebhookUrl(webhookUrl.get());

        // let go of all keys
        releaseAllKeys();

        String pickaxeInfo = pickaxeType.get() == PickaxeType.AMETHYST ? " (ᴀᴍᴇᴛʜʏѕᴛ mode - 3x3 mining)" : " (Normal mode - 2x2 tunnel)";
        String yLevelInfo = maintainYLevel.get() ? " | Y-Level: " + startYLevel : "";
        String enhancedFeaturesInfo = " | Enhanced: " +
            (avoidLava.get() ? "Lava" : "") +
            (enablePearlThrough.get() ? "+Pearl" : "") +
            (enablePlayerDetection.get() ? "+Players" : "");

        info("TunnelBaseFinder activated" + pickaxeInfo + yLevelInfo + enhancedFeaturesInfo + ". " +
            (infiniteTunnel.get() ? "Infinite tunnel mode" : "Target: " + tunnelLength.get() + " blocks"));
    }

    @Override
    public void onDeactivate() {
        releaseAllKeys();

        // put adaptive smoothing back how it was
        adaptiveSmoothing.set(wasAdaptiveSmoothingEnabled);

        // take a screenshot if we finished and it's enabled
        if (enableWebhook.get() && screenshotOnComplete.get() && blocksMined > 0) {
            takeScreenshotAndSend("🏁 Tunneling Complete",
                "Successfully mined " + blocksMined + " blocks" +
                    (infiniteTunnel.get() ? "" : "/" + tunnelLength.get()) +
                    " using " + pickaxeType.get().toString() +
                    (maintainYLevel.get() ? " at Y-level " + startYLevel : ""));
        }

        info("PremiumTunnelBaseFinder deactivated. Mined " + blocksMined + " blocks using " + pickaxeType.get().toString() + ".");
    }

    private void resetAllStates() {
        // reset the basic stuff
        isStuck = false;
        isAvoiding = false;
        isBackingUp = false;
        shouldDisable = false;

        // reset lava avoidance stuff
        isLavaAvoidance = false;
        lavaAvoidancePhase = 0;
        lavaAvoidanceDirection = null;
        lavaAvoidanceStartPos = null;
        lavaAvoidanceSteps = 0;
        detectedLavaPos = null;
        rightSideChecked = false;
        leftSideChecked = false;
        rightSideSafe = false;
        leftSideSafe = false;
        tryingBackward = false;
        turning180 = false;
        backwardBlockToBreak = null;
        backwardBreakAttempts = 0;

        // reset pearl through stuff
        isPearlThroughActive = false;
        pearlThroughCooldownTicks = 0;
        emptyAreaDetected = false;
        lastMiddleClickTime = 0;

        // reset backup stuff
        isBreakingBlockingStones = false;
        backupStuckTicks = 0;
        backupBlockingStone = null;
        hasRandomizedAvoidanceDirection = false;

        // reset y level stuff
        isAdjustingYLevel = false;
        yLevelAdjustmentSteps = 0;
        needsBlockPlacement = false;
        blockPlacementTarget = null;

        // reset timing stuff
        lastMineTime = 0;
        lastRightClickTime = 0;
        lastMovementTime = 0;
        lastLookChangeTime = 0;

        // reset stuck detection stuff
        ticksStuck = 0;
        lastPlayerPos = mc.player.getBlockPos();
        lastBlocksMined = 0;
        alreadyReportedStuck = false;
        consecutiveBackupAttempts = 0;

        // reset avoidance stuff
        avoidanceSteps = 0;
        backupSteps = 0;
        dangerousBlockPos = null;
        dangerType = "";
        backupStartPos = null;

        // clear mining targets
        blocksToMine.clear();
        currentTarget = null;
        lastTargetBlock = null;

        // reset player detection stuff
        playerDetected = false;
        detectedPlayerName = "";
        detectedPlayerPos = null;
        randomLookTicks = 0;
        directStareTicks = 0;
        isRandomLooking = false;
        isDirectStaring = false;
        isMovingAway = false;
        moveAwaySteps = 0;
        moveAwayDirection = null;
        playerDetectionStartPos = null;
        originalYaw = 0.0f;
        originalPitch = 0.0f;
        randomYaw = 0.0f;
        randomPitch = 0.0f;
        lastPlayerDetectionTime = 0;

        // reset smooth looking stuff
        currentYaw = mc.player != null ? mc.player.getYaw() : 0.0f;
        currentPitch = mc.player != null ? mc.player.getPitch() : 0.0f;
        targetYaw = currentYaw;
        targetPitch = currentPitch;
        isSmoothLooking = false;
        isMiningLook = false;
        lastLookYaw = currentYaw;
        lastLookPitch = currentPitch;
        naturalLookTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (shouldDisable) {
            toggle();
            return;
        }

        if (mc.player == null || mc.world == null) return;

        // check if we should stop (only if not infinite mode)
        if (!infiniteTunnel.get() && blocksMined >= tunnelLength.get()) {
            info("Tunnel length reached! Mined " + blocksMined + " blocks.");
            shouldDisable = true;
            return;
        }

        // handle pearl through cooldown
        if (isPearlThroughActive) {
            handlePearlThroughCooldown();
            return;
        }

        // update which way we're facing
        updateDirection();

        // check y level and fix it if needed
        if (maintainYLevel.get()) {
            handleYLevelMaintenance();
        }

        // check for staff/suspicious players first (this is top priority)
        if (enablePlayerDetection.get()) {
            checkForSuspiciousPlayers();
            handlePlayerDetection();
        }

        // don't do anything else if we're dealing with a player
        if (playerDetected && (pauseMiningOnDetection.get() || isMovingAway)) {
            return;
        }

        // handle y level adjustment (important but not as important as players)
        if (isAdjustingYLevel || needsBlockPlacement) {
            handleYLevelAdjustment();
            return;
        }

        // handle lava avoidance (pretty important)
        if (isLavaAvoidance) {
            handleLavaAvoidance();
            return;
        }

        // check for lava with the fancy 3D scanning
        if (detectLavaEnhanced3D()) {
            initiateLavaAvoidance();
            return;
        }

        // check for empty areas (caves) for pearl through
        if (enablePearlThrough.get() && !isPearlThroughActive) {
            checkForEmptyArea();
        }

        // check for other dangerous stuff
        if (detectOtherDangers()) {
            handleDangerAvoidance();
            return;
        }

        // check if we're stuck
        checkStuckCondition();

        // handle whatever state we're in
        if (isBackingUp) {
            handleBackupEnhanced();
        } else if (isAvoiding) {
            handleAvoidance();
        } else if (isStuck && antiStuckMovement.get()) {
            handleStuckMovement();
        } else {
            // just normal mining
            handleNormalMining();
        }

        // auto right click to fix ghost blocks
        handleAutoRightClick();

        // update smooth looking for mining (but not when dealing with players)
        if (!isRandomLooking && !isDirectStaring && !isMovingAway) {
            updateSmoothLookingEnhanced();
        }

        // make it look more natural/human
        if (naturalMiningLook.get()) {
            handleNaturalLookBehavior();
        }

        // handle middle click for pearl through
        handleMiddleClickDetection();
    }

    // ===== LAVA DETECTION (THE FANCY 3D VERSION) =====
    private boolean detectLavaEnhanced3D() {
        if (!avoidLava.get()) return false;

        assert mc.player != null;
        BlockPos playerPos = mc.player.getBlockPos();
        int scanRange = lavaDetectionRange.get();
        int scanWidth = lavaDetectionWidth.get();
        int scanHeight = lavaDetectionHeight.get();

        if (debugLavaDetection.get()) {
            info("🔍 Scanning for lava: Range=" + scanRange + ", Width=" + scanWidth + ", Height=" + scanHeight);
        }

        // scan in a 3D area ahead of us
        for (int distance = 1; distance <= scanRange; distance++) {
            for (int side = -scanWidth; side <= scanWidth; side++) {
                for (int vertical = -scanHeight; vertical <= scanHeight; vertical++) {
                    BlockPos scanPos = calculateScanPosition(playerPos, distance, side, vertical);

                    if (isLava(mc.world.getBlockState(scanPos))) {
                        detectedLavaPos = scanPos;
                        dangerousBlockPos = scanPos;
                        dangerType = "lava (3D scan at " + scanPos.toShortString() + ")";

                        if (debugLavaDetection.get()) {
                            warning("🔥 LAVA DETECTED at " + scanPos.toShortString() +
                                " (distance=" + distance + ", side=" + side + ", vertical=" + vertical + ")");
                        }

                        return true;
                    }
                }
            }
        }

        // old vertical detection (keeping it for compatibility)
        if (detectLavaVertical.get()) {
            return detectLavaVerticalLegacy();
        }

        return false;
    }

    private BlockPos calculateScanPosition(BlockPos playerPos, int distance, int side, int vertical) {
        BlockPos basePos = playerPos.offset(currentDirection, distance);

        // figure out which way is "side" based on where we're facing
        BlockPos sidePos = switch (currentDirection) {
            case NORTH, SOUTH -> basePos.add(side, vertical, 0);
            case EAST, WEST -> basePos.add(0, vertical, side);
            default -> basePos.add(side, vertical, 0);
        };

        return sidePos;
    }

    private boolean detectLavaVerticalLegacy() {
        if (!detectLavaVertical.get()) return false;

        BlockPos playerPos = mc.player.getBlockPos();
        int range = verticalLavaRange.get();

        // check above us
        for (int y = 1; y <= range; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos checkPos = playerPos.add(x, y, z);
                    if (isLava(mc.world.getBlockState(checkPos))) {
                        detectedLavaPos = checkPos;
                        dangerousBlockPos = checkPos;
                        dangerType = "lava (above, legacy)";
                        return true;
                    }
                }
            }
        }

        // check below us
        for (int y = 1; y <= range; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos checkPos = playerPos.add(x, -y, z);
                    if (isLava(mc.world.getBlockState(checkPos))) {
                        detectedLavaPos = checkPos;
                        dangerousBlockPos = checkPos;
                        dangerType = "lava (below, legacy)";
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // ===== PEARL THROUGH FEATURE =====
    private void checkForEmptyArea() {
        BlockPos playerPos = mc.player.getBlockPos();
        int radius = emptyAreaScanRadius.get();
        int totalBlocks = 0;
        int airBlocks = 0;

        // scan around us in 3D
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = playerPos.add(x, y, z);
                    totalBlocks++;

                    if (mc.world.getBlockState(checkPos).isAir()) {
                        airBlocks++;
                    }
                }
            }
        }

        double airPercentage = (double) airBlocks / totalBlocks;
        boolean wasEmptyArea = emptyAreaDetected;
        emptyAreaDetected = airPercentage >= emptyAreaThreshold.get();

        if (emptyAreaDetected && !wasEmptyArea) {
            info("🕳️ Empty area detected! Air percentage: " + String.format("%.1f%%", airPercentage * 100) +
                " (threshold: " + String.format("%.1f%%", emptyAreaThreshold.get() * 100) + ")");

            if (enableWebhook.get()) {
                takeScreenshotAndSend("🕳️ Empty Area Detected - Pearl-Through Available",
                    "**Air Percentage:** " + String.format("%.1f%%", airPercentage * 100) + "\n" +
                        "**Threshold:** " + String.format("%.1f%%", emptyAreaThreshold.get() * 100) + "\n" +
                        "**Position:** " + playerPos.toShortString() + "\n" +
                        "**Action:** Middle-click to activate pearl-through");
            }
        } else if (!emptyAreaDetected && wasEmptyArea) {
            info("✅ Left empty area. Air percentage: " + String.format("%.1f%%", airPercentage * 100));
        }
    }

    private void handleMiddleClickDetection() {
        if (!enablePearlThrough.get() || !emptyAreaDetected || isPearlThroughActive) return;

        // check if middle mouse button is pressed
        boolean middlePressed = mc.options.pickItemKey.isPressed();
        long currentTime = System.currentTimeMillis();

        if (middlePressed && (currentTime - lastMiddleClickTime) > 500) { // 500ms cooldown
            lastMiddleClickTime = currentTime;
            activatePearlThrough();
        }
    }

    private void activatePearlThrough() {
        isPearlThroughActive = true;
        pearlThroughCooldownTicks = pearlThroughCooldown.get();

        // stop moving
        stopAllMovement();

        info("🌟 Pearl-through activated! Cooldown: " + pearlThroughCooldownTicks + " ticks");

        if (enableWebhook.get()) {
            takeScreenshotAndSend("🌟 Pearl-Through Activated",
                "**Cooldown:** " + pearlThroughCooldownTicks + " ticks (" + (pearlThroughCooldownTicks / 20.0) + " seconds)\n" +
                    "**Position:** " + mc.player.getBlockPos().toShortString() + "\n" +
                    "**Action:** Pausing mining until cooldown expires");
        }
    }

    private void handlePearlThroughCooldown() {
        pearlThroughCooldownTicks--;

        // show updates every so often
        if (pearlThroughCooldownTicks % 20 == 0) { // Every second
            info("🌟 Pearl-through cooldown: " + (pearlThroughCooldownTicks / 20) + " seconds remaining");
        }

        if (pearlThroughCooldownTicks <= 0) {
            isPearlThroughActive = false;
            info("✅ Pearl-through cooldown complete. Resuming mining.");
        }
    }

    // ===== BACKUP SYSTEM =====
    private void handleBackupEnhanced() {
        backupStuckTicks++;

        if (improvedBackupLogic.get()) {
            handleImprovedBackupWithStoneBreaking();
        } else {
            handleBasicBackup();
        }
    }

    private void handleImprovedBackupWithStoneBreaking() {
        BlockPos currentPos = mc.player.getBlockPos();
        int actualBackupDistance = (int) Math.abs(getDistanceInDirection(backupStartPos, currentPos, currentDirection.getOpposite()));

        // check if we're stuck while backing up
        if (backupStuckTicks >= backupStuckTimeout.get() && actualBackupDistance < 1) {
            if (!isBreakingBlockingStones && breakBlockingStones.get()) {
                initiateStoneBreaking();
                return;
            }
        }

        // handle breaking stones
        if (isBreakingBlockingStones) {
            handleStoneBreaking();
            return;
        }

        // check if something's blocking us from behind
        BlockPos behindPos = currentPos.offset(currentDirection.getOpposite());
        boolean obstacleBehind = !mc.world.getBlockState(behindPos).isAir() ||
            !mc.world.getBlockState(behindPos.up()).isAir();

        if (obstacleBehind && actualBackupDistance < 2) {
            // try to mine whatever's behind us
            if (!mc.world.getBlockState(behindPos).isAir()) {
                smoothLookAtBlock(behindPos);
                performMiningAction();
                backupSteps++;
                return;
            }
            if (!mc.world.getBlockState(behindPos.up()).isAir()) {
                smoothLookAtBlock(behindPos.up());
                performMiningAction();
                backupSteps++;
                return;
            }
        }

        if (actualBackupDistance < safetyBackupDistance.get() && !obstacleBehind) {
            // just back up normally
            moveBackward();
            backupSteps++;
        } else {
            // Backup complete
            info("Enhanced backup complete (" + actualBackupDistance + " blocks). Starting avoidance maneuver...");
            completeBackup();
        }

        // don't try to backup forever
        if (backupSteps > safetyBackupDistance.get() * 4 || backupStuckTicks > backupStuckTimeout.get() * 3) {
            warning("Backup timeout after " + backupSteps + " attempts. Proceeding to avoidance.");
            completeBackup();
        }
    }

    private void initiateStoneBreaking() {
        isBreakingBlockingStones = true;

        // find what's blocking us behind
        BlockPos currentPos = mc.player.getBlockPos();
        BlockPos behindPos = currentPos.offset(currentDirection.getOpposite());

        if (!mc.world.getBlockState(behindPos).isAir()) {
            backupBlockingStone = behindPos;
        } else if (!mc.world.getBlockState(behindPos.up()).isAir()) {
            backupBlockingStone = behindPos.up();
        } else {
            // nothing blocking behind, try the sides
            Direction sideDir = randomizeAvoidanceDirection.get() ?
                (Math.random() > 0.5 ? currentDirection.rotateYClockwise() : currentDirection.rotateYCounterclockwise()) :
                currentDirection.rotateYClockwise();
            backupBlockingStone = currentPos.offset(sideDir);
        }

        info("🔨 Breaking blocking stone at " + backupBlockingStone.toShortString());

        if (enableWebhook.get()) {
            takeScreenshotAndSend("🔨 Breaking Blocking Stone",
                "**Backup stuck for:** " + backupStuckTicks + " ticks\n" +
                    "**Breaking stone at:** " + backupBlockingStone.toShortString() + "\n" +
                    "**Action:** Clearing backup path");
        }
    }

    private void handleStoneBreaking() {
        if (backupBlockingStone == null) {
            isBreakingBlockingStones = false;
            return;
        }

        // look at and mine whatever's blocking us
        smoothLookAtBlock(backupBlockingStone);
        performMiningAction();

        // check if we broke it
        if (mc.world.getBlockState(backupBlockingStone).isAir()) {
            info("✅ Blocking stone cleared. Resuming backup.");
            isBreakingBlockingStones = false;
            backupBlockingStone = null;
            backupStuckTicks = 0; // Reset stuck timer
        }
    }

    private void completeBackup() {
        isBackingUp = false;
        isAvoiding = true;
        avoidanceSteps = 0;
        consecutiveBackupAttempts = 0;
        backupStuckTicks = 0;
        isBreakingBlockingStones = false;
        backupBlockingStone = null;

        stopAllMovement();

        // pick which way to go (maybe random)
        if (randomizeAvoidanceDirection.get() && !hasRandomizedAvoidanceDirection) {
            currentDirection = Math.random() > 0.5 ?
                originalDirection.rotateYClockwise() :
                originalDirection.rotateYCounterclockwise();
            hasRandomizedAvoidanceDirection = true;
            info("🎲 Randomly chose " + (currentDirection == originalDirection.rotateYClockwise() ? "right" : "left") + " direction for avoidance");
        } else {
            currentDirection = originalDirection.rotateYClockwise();
        }

        lookInDirection(currentDirection);
    }

    private void updateDirection() {
        if (!isAvoiding && !isBackingUp && !isRandomLooking && !isDirectStaring && !isLavaAvoidance &&
            !isMovingAway && !isAdjustingYLevel && !isPearlThroughActive) {
            // update to where we're actually facing (when not doing special stuff)
            currentDirection = mc.player.getHorizontalFacing();
        }
    }

    // Y LEVEL MAINTENANCE (keeping the tunnel straight)
    private void handleYLevelMaintenance() {
        if (!maintainYLevel.get()) return;

        int currentY = mc.player.getBlockPos().getY();
        int yDifference = currentY - startYLevel;

        if (Math.abs(yDifference) > yLevelTolerance.get()) {
            if (!isAdjustingYLevel) {
                info("Y-level deviation detected: " + yDifference + " blocks. Starting adjustment...");
                isAdjustingYLevel = true;
                yLevelAdjustmentSteps = 0;

                if (yDifference < 0 && autoPlaceBlocks.get()) {
                    // we're too low, need to place blocks
                    needsBlockPlacement = true;
                    blockPlacementTarget = mc.player.getBlockPos().down();
                }
            }
        }
    }

    private void handleYLevelAdjustment() {
        int currentY = mc.player.getBlockPos().getY();
        int yDifference = currentY - startYLevel;

        if (needsBlockPlacement && yDifference < 0) {
            // try to place blocks to get back up
            if (tryPlaceBlock()) {
                needsBlockPlacement = false;
                info("Block placed to maintain Y-level.");
            } else {
                yLevelAdjustmentSteps++;
                if (yLevelAdjustmentSteps > 100) {
                    warning("Failed to place blocks for Y-level adjustment after 100 attempts.");
                    needsBlockPlacement = false;
                    isAdjustingYLevel = false;
                }
            }
        } else if (yDifference > 0) {
            // we're too high, mine down
            BlockPos targetPos = mc.player.getBlockPos().down();
            if (!mc.world.getBlockState(targetPos).isAir()) {
                smoothLookAtBlock(targetPos);
                performMiningAction();
            } else {
                // move down if we can
                moveInDirection(Direction.DOWN);
            }
            yLevelAdjustmentSteps++;
        }

        // check if we're done adjusting
        if (Math.abs(currentY - startYLevel) <= yLevelTolerance.get()) {
            info("Y-level adjustment complete. Current Y: " + currentY);
            isAdjustingYLevel = false;
            yLevelAdjustmentSteps = 0;
            needsBlockPlacement = false;
        }

        // don't try forever
        if (yLevelAdjustmentSteps > 200) {
            warning("Y-level adjustment timeout. Resuming normal mining.");
            isAdjustingYLevel = false;
            yLevelAdjustmentSteps = 0;
            needsBlockPlacement = false;
        }
    }

    private boolean tryPlaceBlock() {
        if (blockPlacementTarget == null) return false;

        // find a block in our inventory
        ItemStack blockStack = findBlockInInventory();
        if (blockStack.isEmpty()) {
            if (debugMode.get()) {
                info("No blocks found in inventory for placement.");
            }
            return false;
        }

        // switch to the block
        int blockSlot = findBlockSlot();
        if (blockSlot == -1) return false;

        int currentSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = blockSlot;

        try {
            // look at where we want to place it
            smoothLookAtBlock(blockPlacementTarget);

            // try to place the block
            BlockHitResult hitResult = new BlockHitResult(
                Vec3d.ofCenter(blockPlacementTarget),
                Direction.UP,
                blockPlacementTarget,
                false
            );

            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);

            // switch back to what we had (but not if it was a pickaxe)
            if (currentSlot != blockSlot && !isPickaxeSlot(currentSlot)) {
                mc.player.getInventory().selectedSlot = currentSlot;
            }

            return true;
        } catch (Exception e) {
            if (debugMode.get()) {
                error("Failed to place block: " + e.getMessage());
            }
            return false;
        }
    }

    private ItemStack findBlockInInventory() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem &&
                !isPickaxeItem(stack) && !isFoodItem(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) { // Check hotbar first
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem &&
                !isPickaxeItem(stack) && !isFoodItem(stack)) {
                return i;
            }
        }

        // if no block in hotbar, grab one from inventory
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem &&
                !isPickaxeItem(stack) && !isFoodItem(stack)) {
                // move it to an empty hotbar slot
                int emptySlot = findEmptyHotbarSlot();
                if (emptySlot != -1) {
                    mc.interactionManager.clickSlot(0, i, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(0, emptySlot, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                    return emptySlot;
                }
            }
        }
        return -1;
    }

    private int findEmptyHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private boolean isPickaxeItem(ItemStack stack) {
        return stack.getItem().toString().toLowerCase().contains("pickaxe");
    }

    private boolean isFoodItem(ItemStack stack) {
        return stack.getItem().getComponents() != null;
    }

    private boolean isPickaxeSlot(int slot) {
        ItemStack stack = mc.player.getInventory().getStack(slot);
        return isPickaxeItem(stack);
    }

    // PLAYER DETECTION (with fast mouse movement so it looks real)
    private void checkForSuspiciousPlayers() {
        if (mc.world == null || mc.player == null) return;

        long currentTime = System.currentTimeMillis();
        // only check every 500ms so we don't spam
        if (currentTime - lastPlayerDetectionTime < 500) return;
        lastPlayerDetectionTime = currentTime;

        for (var player : mc.world.getPlayers()) {
            if (player == mc.player) continue; // Skip self

            double distance = mc.player.distanceTo(player);
            if (distance > detectionRange.get()) continue;

            String playerName = player.getName().getString();
            String displayName = player.getDisplayName().getString();

            if (debugPlayerDetection.get()) {
                info("Checking player: " + playerName + " | Display: " + displayName + " | Distance: " + String.format("%.1f", distance));
            }

            if (isSuspiciousPlayer(displayName, playerName)) {
                if (!playerDetected) {
                    // new player detected - turn off adaptive smooth looking right away
                    wasAdaptiveSmoothingEnabled = adaptiveSmoothing.get();
                    if (wasAdaptiveSmoothingEnabled) {
                        adaptiveSmoothing.set(false); // Disable adaptive smoothing during detection
                        if (debugPlayerDetection.get()) {
                            info("🚨 DISABLED adaptive smoothing for fast player detection response");
                        }
                    }

                    playerDetected = true;
                    detectedPlayerName = playerName;
                    detectedPlayerPos = player.getPos();
                    isRandomLooking = true;
                    isDirectStaring = false;
                    isMovingAway = false;
                    randomLookTicks = 0;
                    directStareTicks = 0;
                    moveAwaySteps = 0;
                    playerDetectionStartPos = mc.player.getBlockPos();

                    // remember where we were looking
                    originalYaw = mc.player.getYaw();
                    originalPitch = mc.player.getPitch();

                    // look around randomly FAST (no smooth movement)
                    generateAndApplyRandomLookFast();

                    warning("🚨 SUSPICIOUS PLAYER DETECTED: " + playerName + " at distance " + String.format("%.1f", distance));

                    if (enableWebhook.get() && screenshotOnPlayerDetection.get()) {
                        takeScreenshotAndSend("🚨 Suspicious Player Detected",
                            "**Player:** " + playerName + "\n" +
                                "**Distance:** " + String.format("%.1f", distance) + " blocks\n" +
                                "**Display Name:** " + displayName + "\n" +
                                "**Action:** " + (moveAwayOnDetection.get() ? "Moving away " + moveAwayDistance.get() + " blocks" : "Pausing mining"));
                    }
                } else if (detectedPlayerName.equals(playerName)) {
                    // update where the player is (we already detected them)
                    detectedPlayerPos = player.getPos();
                }
                return; // Only handle one player at a time
            }
        }

        // no suspicious players found, reset if we were tracking someone
        if (playerDetected) {
            info("Suspicious player left detection range. Resuming normal operation.");
            resetPlayerDetectionAndRestoreSmoothing();
        }
    }

    private boolean isSuspiciousPlayer(String displayName, String playerName) {
        // remove formatting codes and check what color their name is
        String cleanName = displayName.replaceAll("§[0-9a-fk-or]", "");

        // check if their name has color formatting
        boolean hasGreen = displayName.contains("§a") || displayName.contains("§2");
        boolean hasGold = displayName.contains("§6") || displayName.contains("§e");
        boolean hasPurple = displayName.contains("§5") || displayName.contains("§d");
        boolean hasBlue = displayName.contains("§9") || displayName.contains("§b") || displayName.contains("§3");
        boolean hasPlus = cleanName.contains("+");

        if (debugPlayerDetection.get()) {
            info("Player analysis - Name: " + playerName +
                " | Green: " + hasGreen +
                " | Gold: " + hasGold +
                " | Purple: " + hasPurple +
                " | Blue: " + hasBlue +
                " | Plus: " + hasPlus);
        }

        // ignore blue names with + if the setting is on (those are just plus rankers)
        if (ignorePlusRankers.get() && hasBlue && hasPlus) {
            if (debugPlayerDetection.get()) {
                info("Ignoring plus ranker: " + playerName);
            }
            return false;
        }

        // check for staff colors
        if (detectGreenNames.get() && hasGreen) return true;
        if (detectGoldNames.get() && hasGold) return true;
        if (detectPurpleNames.get() && hasPurple) return true;

        return false;
    }

    private void handlePlayerDetection() {
        if (!playerDetected) return;

        if (isRandomLooking) {
            randomLookTicks++;

            // look around randomly FAST - change direction every few ticks
            if (randomLookTicks % 8 == 0) { // Change direction every 8 ticks (faster than before)
                generateAndApplyRandomLookFast();
            }

            if (randomLookTicks >= randomLookDuration.get()) {
                // switch to staring directly at them
                isRandomLooking = false;
                isDirectStaring = true;
                directStareTicks = 0;

                if (debugPlayerDetection.get()) {
                    info("Switching to direct stare at " + detectedPlayerName);
                }
            }
        } else if (isDirectStaring) {
            directStareTicks++;

            // look directly at the player - FAST, no smooth movement
            if (detectedPlayerPos != null) {
                fastLookAtPosition(detectedPlayerPos);
            }

            if (directStareTicks >= directStareDuration.get()) {
                // done staring, check if we should move away
                if (moveAwayOnDetection.get()) {
                    initiateMoveAway();
                } else {
                    // just reset detection
                    info("Finished analyzing " + detectedPlayerName + ". Resuming normal operation.");
                    resetPlayerDetectionAndRestoreSmoothing();
                }
            }
        } else if (isMovingAway) {
            handleMoveAway();
        }
    }

    private void generateAndApplyRandomLookFast() {
        // pick a random yaw (0-360 degrees)
        float newRandomYaw = (float) (Math.random() * 360);

        // pick a random pitch (-30 to 30 degrees so it looks natural)
        float newRandomPitch = (float) ((Math.random() - 0.5) * 60);

        // Apply IMMEDIATELY without smooth interpolation
        mc.player.setYaw(newRandomYaw);
        mc.player.setPitch(newRandomPitch);

        if (debugPlayerDetection.get()) {
            info("Applied FAST random look: Yaw=" + String.format("%.1f", newRandomYaw) +
                " Pitch=" + String.format("%.1f", newRandomPitch));
        }
    }

    private void fastLookAtPosition(Vec3d targetPos) {
        Vec3d playerPos = mc.player.getEyePos();
        double diffX = targetPos.x - playerPos.x;
        double diffY = targetPos.y - playerPos.y;
        double diffZ = targetPos.z - playerPos.z;

        double distXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) Math.toDegrees(-Math.atan2(diffY, distXZ));

        // add a tiny bit of randomness so it looks human but still fast
        yaw += (float) ((Math.random() - 0.5) * 3.0); // ±1.5 degree randomness
        pitch += (float) ((Math.random() - 0.5) * 2.0); // ±1 degree randomness

        // Apply IMMEDIATELY without smooth interpolation
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);

        if (debugPlayerDetection.get()) {
            info("Applied FAST look at player: Yaw=" + String.format("%.1f", yaw) +
                " Pitch=" + String.format("%.1f", pitch));
        }
    }

    private void resetPlayerDetectionAndRestoreSmoothing() {
        playerDetected = false;
        detectedPlayerName = "";
        detectedPlayerPos = null;
        randomLookTicks = 0;
        directStareTicks = 0;
        isRandomLooking = false;
        isDirectStaring = false;
        isMovingAway = false;
        moveAwaySteps = 0;
        moveAwayDirection = null;
        playerDetectionStartPos = null;

        // turn adaptive smoothing back on for normal mining
        if (wasAdaptiveSmoothingEnabled) {
            adaptiveSmoothing.set(true);
            if (debugPlayerDetection.get()) {
                info("✅ RESTORED adaptive smoothing for normal mining operations");
            }
        }

        // smoothly look back where we were (now that smoothing is back on)
        setTargetLook(originalYaw, originalPitch, false);
    }

    private void initiateMoveAway() {
        isDirectStaring = false;
        isMovingAway = true;
        moveAwaySteps = 0;

        // figure out which way to go to get away from the player
        if (detectedPlayerPos != null && mc.player != null) {
            Vec3d playerPos = mc.player.getPos();
            Vec3d directionAway = playerPos.subtract(detectedPlayerPos).normalize();

            // convert to a cardinal direction (prefer going backward)
            double angle = Math.atan2(directionAway.z, directionAway.x);
            angle = Math.toDegrees(angle);

            // pick the best direction based on the angle
            if (angle >= -45 && angle < 45) {
                moveAwayDirection = Direction.EAST;
            } else if (angle >= 45 && angle < 135) {
                moveAwayDirection = Direction.SOUTH;
            } else if (angle >= 135 || angle < -135) {
                moveAwayDirection = Direction.WEST;
        } else {
                moveAwayDirection = Direction.NORTH;
            }

            // if none of that worked, just go backward from where we're facing
            if (moveAwayDirection == null) {
                moveAwayDirection = currentDirection.getOpposite();
            }
        } else {
            // fallback: just go backward
            moveAwayDirection = currentDirection.getOpposite();
        }

        info("Moving away from " + detectedPlayerName + " in direction: " + moveAwayDirection);

        if (debugPlayerDetection.get()) {
            info("Starting move away sequence: " + moveAwayDistance.get() + " blocks in direction " + moveAwayDirection);
        }
    }

    private void handleMoveAway() {
        if (moveAwaySteps < moveAwayDistance.get()) {
            // move away from the player
            if (moveAwayDirection == currentDirection.getOpposite()) {
                // going backward - don't change where we're looking, just walk backward
                moveBackward();
            } else {
                // going a different direction - use FAST looking while moving away
                fastLookInDirection(moveAwayDirection);
                moveInDirection(moveAwayDirection);
            }

            // check how far we actually moved
            BlockPos currentPos = mc.player.getBlockPos();
            int actualDistance = (int) Math.abs(getDistanceInDirection(playerDetectionStartPos, currentPos, moveAwayDirection));

            if (actualDistance > moveAwaySteps) {
                moveAwaySteps = actualDistance;
            } else {
                moveAwaySteps++; // Increment even if not moving to avoid infinite loop
            }

            if (debugPlayerDetection.get() && moveAwaySteps % 10 == 0) {
                info("Move away progress: " + moveAwaySteps + "/" + moveAwayDistance.get() + " blocks");
            }
        } else {
            // done moving away
            info("✅ Moved away " + moveAwaySteps + " blocks from " + detectedPlayerName + ". Resuming normal operation.");
            resetPlayerDetectionAndRestoreSmoothing();
            stopAllMovement();
        }
    }

    private void fastLookInDirection(Direction direction) {
        if (mc.player == null) return;

        float yaw = switch (direction) {
            case NORTH -> 180.0f;
            case SOUTH -> 0.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> mc.player.getYaw();
        };

        // apply it RIGHT NOW (no smooth movement during player detection)
        mc.player.setYaw(yaw);
        mc.player.setPitch(0.0f);

        if (debugPlayerDetection.get()) {
            info("Applied FAST directional look: " + direction + " (Yaw=" + yaw + ")");
        }
    }

    private void handleBasicBackup() {
        if (backupSteps < safetyBackupDistance.get()) {
            BlockPos currentPos = mc.player.getBlockPos();
            int actualBackupDistance = (int) Math.abs(getDistanceInDirection(backupStartPos, currentPos, currentDirection.getOpposite()));

            if (actualBackupDistance < safetyBackupDistance.get()) {
                // keep looking where we are, just walk backward with S
                moveBackward();
                backupSteps++;
            } else {
                // we backed up enough, start avoiding
                info("Backup complete (" + actualBackupDistance + " blocks). Starting avoidance maneuver...");
                completeBackup();
            }
        } else {
            // fallback: if we somehow hit max steps without going far enough
            info("Backup steps limit reached. Starting avoidance maneuver...");
            completeBackup();
        }
    }

    // NATURAL LOOKING BEHAVIOR (making it look human)
    private void handleNaturalLookBehavior() {
        if (!naturalMiningLook.get()) return;

        naturalLookTimer++;

        // add tiny look variations every so often
        if (naturalLookTimer % 60 == 0) { // Every 3 seconds
            addNaturalLookVariation();
        }

        // sometimes look around briefly (every 5-10 seconds)
        if (naturalLookTimer % (100 + (int)(Math.random() * 100)) == 0) {
            addBriefLookAround();
        }
    }

    private void addNaturalLookVariation() {
        if (isSmoothLooking || isRandomLooking || isDirectStaring || isMovingAway) return;

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        // add small random variations
        float yawVariation = (float) ((Math.random() - 0.5) * lookRandomization.get());
        float pitchVariation = (float) ((Math.random() - 0.5) * lookRandomization.get() * 0.5);

        setTargetLook(currentYaw + yawVariation, currentPitch + pitchVariation, true);
    }

    private void addBriefLookAround() {
        if (isSmoothLooking || isRandomLooking || isDirectStaring || isMovingAway) return;

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        // quick look to the side (15-45 degrees)
        float lookDirection = Math.random() > 0.5 ? 1 : -1;
        float yawChange = (float) (lookDirection * (15 + Math.random() * 30));
        float pitchChange = (float) ((Math.random() - 0.5) * 20);

        setTargetLook(currentYaw + yawChange, currentPitch + pitchChange, true);
    }

    // SMOOTH LOOKING SYSTEM (making head movement look natural)
    private void updateSmoothLookingEnhanced() {
        if (!isSmoothLooking) return;

        // skip smooth looking during player detection (they use fast looking)
        if (isRandomLooking || isDirectStaring || isMovingAway) {
            isSmoothLooking = false;
            isMiningLook = false;
            return;
        }

        // figure out how fast to move based on context and settings
        float baseSpeed = 0.15f;

        if (isMiningLook && adaptiveSmoothing.get()) {
            // for mining looks, use pickaxe-specific smoothing only if adaptive smoothing is on
            double smoothnessFactor = getAdaptiveSmoothnessFactor();
            baseSpeed *= smoothnessFactor;
        } else if (isMiningLook && !adaptiveSmoothing.get()) {
            // if adaptive smoothing is off, use faster speed
            baseSpeed = 0.8f; // Much faster for non-adaptive mode
        } else {
            // for non-mining stuff, use the original smoothness
            baseSpeed = 0.15f - (lookSmoothness.get() * 0.01f); // Range: 0.05f to 0.14f
        }

        // add some randomness so it looks more human
        if (naturalMiningLook.get()) {
            baseSpeed += (Math.random() - 0.5) * 0.02f;
        }
        baseSpeed = Math.max(0.02f, Math.min(0.9f, baseSpeed)); // Clamp between reasonable values

        // smoothly change yaw
        float yawDiff = targetYaw - currentYaw;

        // handle yaw wrapping (the 360 degree thing)
        if (yawDiff > 180) yawDiff -= 360;
        if (yawDiff < -180) yawDiff += 360;

        if (Math.abs(yawDiff) > 0.5f) {
            currentYaw += yawDiff * baseSpeed;
            // normalize yaw to 0-360 range
            if (currentYaw > 360) currentYaw -= 360;
            if (currentYaw < 0) currentYaw += 360;
        }

        // smoothly change pitch
        float pitchDiff = targetPitch - currentPitch;
        if (Math.abs(pitchDiff) > 0.5f) {
            currentPitch += pitchDiff * baseSpeed;
            // clamp pitch to valid range
            currentPitch = Math.max(-90f, Math.min(90f, currentPitch));
        }

        // apply the smooth look
        mc.player.setYaw(currentYaw);
        mc.player.setPitch(currentPitch);

        // check if we're close enough to the target
        if (Math.abs(yawDiff) <= 0.5f && Math.abs(pitchDiff) <= 0.5f) {
            isSmoothLooking = false;
            isMiningLook = false;
            if (debugPlayerDetection.get()) {
                info("Smooth look completed");
            }
        }

        // remember where we were looking (for natural behavior)
        lastLookYaw = currentYaw;
        lastLookPitch = currentPitch;
    }

    private double getAdaptiveSmoothnessFactor() {
        // make it smoother/slower based on what pickaxe we're using
        switch (pickaxeType.get()) {
            case AMETHYST:
                // amethyst pickaxe mines 3x3, so we don't need to look around as much
                // make it way smoother/slower
                return 0.3 * lookSmoothnessFactor.get();
            case NORMAL:
            default:
                // normal pickaxe needs to look around more but still keep it smooth
                return 0.6 * lookSmoothnessFactor.get();
        }
    }

    private void setTargetLook(float yaw, float pitch, boolean isMining) {
        targetYaw = yaw;
        targetPitch = pitch;
        isSmoothLooking = true;
        isMiningLook = isMining;

        if (debugPlayerDetection.get()) {
            info("Setting target look: Yaw=" + String.format("%.1f", yaw) +
                " Pitch=" + String.format("%.1f", pitch) +
                " Mining=" + isMining);
        }
    }

    private void smoothLookAtPosition(Vec3d targetPos, boolean isMining) {
        Vec3d playerPos = mc.player.getEyePos();
        double diffX = targetPos.x - playerPos.x;
        double diffY = targetPos.y - playerPos.y;
        double diffZ = targetPos.z - playerPos.z;

        double distXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) Math.toDegrees(-Math.atan2(diffY, distXZ));

        // add some randomness so it looks natural (if enabled)
        if (naturalMiningLook.get() && isMining) {
            yaw += (float) ((Math.random() - 0.5) * lookRandomization.get());
            pitch += (float) ((Math.random() - 0.5) * lookRandomization.get() * 0.5);
        }

        setTargetLook(yaw, pitch, isMining);
    }

    // LAVA DETECTION AND AVOIDANCE (don't die pls)
    private boolean detectOtherDangers() {
        BlockPos playerPos = mc.player.getBlockPos();

        // check 5 blocks ahead for other dangerous stuff
        for (int i = 1; i <= 5; i++) {
            BlockPos checkPos = playerPos.offset(currentDirection, i);
            BlockState state = mc.world.getBlockState(checkPos);
            BlockState stateAbove = mc.world.getBlockState(checkPos.up());

            // check for falling blocks (gravel/sand)
            if (avoidGravel.get() && (state.getBlock() instanceof FallingBlock || stateAbove.getBlock() instanceof FallingBlock)) {
                dangerousBlockPos = checkPos;
                dangerType = "falling blocks";
                return true;
            }

            // check for big caves/air pockets
            if (avoidCaves.get() && isLargeAirPocket(checkPos)) {
                dangerousBlockPos = checkPos;
                dangerType = "cave";
                return true;
            }
        }

        return false;
    }

    private void initiateLavaAvoidance() {
        if (!isLavaAvoidance) {
            warning("🔥 LAVA/GRAVEL DETECTED (" + dangerType + ")! Initiating improved avoidance maneuver...");

            if (enableWebhook.get() && screenshotOnDanger.get()) {
                takeScreenshotAndSend("🔥 Danger Detected - Improved Avoidance",
                    "**Danger Type:** " + dangerType + "\n" +
                        "**Position:** " + (detectedLavaPos != null ? detectedLavaPos.toShortString() : "Unknown") + "\n" +
                        "**Detection Range:** " + lavaDetectionRange.get() + " blocks\n" +
                        "**Action:** Check both sides → choose safe side → backup if needed → turn 180 if blocked");
            }

            isLavaAvoidance = true;
            lavaAvoidancePhase = 0;
            lavaAvoidanceStartPos = mc.player.getBlockPos();
            lavaAvoidanceSteps = 0;
            rightSideChecked = false;
            leftSideChecked = false;
            rightSideSafe = false;
            leftSideSafe = false;
            tryingBackward = false;
            turning180 = false;
            backwardBlockToBreak = null;
            backwardBreakAttempts = 0;

            info("Phase 0: Backing up " + safetyBackupDistance.get() + " blocks, then checking both sides...");
        }
    }

    private void handleLavaAvoidance() {
        switch (lavaAvoidancePhase) {
            case 0: // Backup phase
                handleLavaBackup();
                break;
            case 1: // Choose direction phase
                chooseLavaAvoidanceDirection();
                break;
            case 2: // Mine sideways phase
                handleLavaSidewaysMining();
                break;
            case 3: // Return to original direction phase
                handleLavaReturn();
                break;
            case 4: // Try backward phase
                handleLavaBackwardAttempt();
                break;
            case 5: // Turn 180 and break blocks phase
                handleLava180TurnAndBreak();
                break;
        }
    }

    private void handleLavaBackup() {
        BlockPos currentPos = mc.player.getBlockPos();
        int backupDistance = (int) Math.abs(getDistanceInDirection(lavaAvoidanceStartPos, currentPos, currentDirection.getOpposite()));

        if (backupDistance < safetyBackupDistance.get()) {
            // Keep looking in current direction, just walk backward with S key
            moveBackward();
            lavaAvoidanceSteps++;
            } else {
            // Backup complete
            info("Lava backup complete (" + backupDistance + " blocks). Choosing avoidance direction...");
            lavaAvoidancePhase = 1;
            lavaAvoidanceSteps = 0;
            stopAllMovement();
        }
    }

    private void chooseLavaAvoidanceDirection() {
        BlockPos currentPos = mc.player.getBlockPos();
        Direction rightDir = originalDirection.rotateYClockwise();
        Direction leftDir = originalDirection.rotateYCounterclockwise();

        if (!rightSideChecked) {
            rightSideSafe = isPathSafeForDistance(currentPos, rightDir, lavaAvoidanceDistance.get());
            rightSideChecked = true;
            info("Checked right side: " + (rightSideSafe ? "SAFE" : "UNSAFE (lava/gravel detected)"));
        }

        if (!leftSideChecked) {
            leftSideSafe = isPathSafeForDistance(currentPos, leftDir, lavaAvoidanceDistance.get());
            leftSideChecked = true;
            info("Checked left side: " + (leftSideSafe ? "SAFE" : "UNSAFE (lava/gravel detected)"));
        }

        if (rightSideSafe && leftSideSafe) {
            lavaAvoidanceDirection = Math.random() > 0.5 ? rightDir : leftDir;
            info("Both sides safe. Randomly chose " + (lavaAvoidanceDirection == rightDir ? "right" : "left") + " direction.");
            lavaAvoidancePhase = 2;
            lavaAvoidanceSteps = 0;
            currentDirection = lavaAvoidanceDirection;
        } else if (rightSideSafe) {
            lavaAvoidanceDirection = rightDir;
            info("Right side safe. Going right.");
            lavaAvoidancePhase = 2;
            lavaAvoidanceSteps = 0;
            currentDirection = lavaAvoidanceDirection;
        } else if (leftSideSafe) {
            lavaAvoidanceDirection = leftDir;
            info("Left side safe. Going left.");
            lavaAvoidancePhase = 2;
            lavaAvoidanceSteps = 0;
            currentDirection = lavaAvoidanceDirection;
        } else {
            info("Both sides blocked by lava/gravel. Trying backward...");
            tryingBackward = true;
            lavaAvoidancePhase = 4;
        }
    }

    private void handleLavaSidewaysMining() {
        if (lavaAvoidanceSteps < lavaAvoidanceDistance.get()) {
            // turn to face the direction we picked and move forward
            moveForward(lavaAvoidanceDirection);

            updateBlocksToMine();
            performMining();

            // count how far we actually moved
            BlockPos currentPos = mc.player.getBlockPos();
            int actualDistance = (int) Math.abs(getDistanceInDirection(lavaAvoidanceStartPos, currentPos, lavaAvoidanceDirection));

            if (actualDistance > lavaAvoidanceSteps) {
                lavaAvoidanceSteps = actualDistance;
            }
        } else {
            // done mining sideways, check if the original path is clear now
            info("Sideways mining complete (" + lavaAvoidanceSteps + " blocks). Checking original path...");

            BlockPos currentPos = mc.player.getBlockPos();
            BlockPos checkPos = currentPos.offset(originalDirection, 5);

            if (!detectDangerAt(checkPos) && !detectLavaInArea(checkPos)) {
                // original path is clear, go back to it
                lavaAvoidancePhase = 3;
                lavaAvoidanceSteps = 0;
                currentDirection = originalDirection;

                info("Phase 3: Original path clear. Returning to original direction...");
            } else {
                // still dangerous, keep mining sideways
                warning("Original path still blocked by lava. Continuing sideways mining...");
                lavaAvoidanceSteps = lavaAvoidanceDistance.get() - 2;
            }
        }
    }

    private void handleLavaReturn() {
        // turn back to the original direction and keep going
        moveForward(originalDirection);
        updateBlocksToMine();
        performMining();

        lavaAvoidanceSteps++;

        // after mining forward a bit, we're done avoiding
        if (lavaAvoidanceSteps >= 3) {
            info("✅ Enhanced lava avoidance maneuver complete! Resuming normal tunneling.");

            // reset all the lava avoidance stuff
            isLavaAvoidance = false;
            lavaAvoidancePhase = 0;
            lavaAvoidanceDirection = null;
            lavaAvoidanceStartPos = null;
            lavaAvoidanceSteps = 0;
            dangerousBlockPos = null;
            dangerType = "";
            detectedLavaPos = null;
            rightSideChecked = false;
            leftSideChecked = false;
            rightSideSafe = false;
            leftSideSafe = false;
            tryingBackward = false;
            turning180 = false;
            backwardBlockToBreak = null;
            backwardBreakAttempts = 0;

            // make sure we're facing the original direction
            currentDirection = originalDirection;
            stopAllMovement();
        }
    }

    private void handleLavaBackwardAttempt() {
        BlockPos currentPos = mc.player.getBlockPos();
        Direction backwardDir = originalDirection.getOpposite();

        // check if going backward is safe
        boolean backwardSafe = isPathSafeForDistance(currentPos, backwardDir, safetyBackupDistance.get());

        if (backwardSafe) {
            info("Backward path is safe. Going backward...");
            moveBackward();
            lavaAvoidanceSteps++;

            if (lavaAvoidanceSteps >= safetyBackupDistance.get()) {
                info("Backed up enough. Checking sides again...");
                rightSideChecked = false;
                leftSideChecked = false;
                lavaAvoidancePhase = 1;
                lavaAvoidanceSteps = 0;
            }
        } else {
            // check if there are blocks behind us we can break
            BlockPos behindPos = currentPos.offset(backwardDir);
            BlockState behindState = mc.world.getBlockState(behindPos);
            BlockState behindStateUp = mc.world.getBlockState(behindPos.up());

            boolean hasBlocksBehind = (!behindState.isAir() && isBlockSafeToBreak(behindPos)) ||
                (!behindStateUp.isAir() && isBlockSafeToBreak(behindPos.up()));

            if (hasBlocksBehind) {
                info("Cannot go backward (lava/gravel), but blocks behind detected. Turning 180 degrees to break blocks...");
                turning180 = true;
                lavaAvoidancePhase = 5;
                backwardBreakAttempts = 0;

                if (!behindState.isAir() && isBlockSafeToBreak(behindPos)) {
                    backwardBlockToBreak = behindPos;
                } else if (!behindStateUp.isAir() && isBlockSafeToBreak(behindPos.up())) {
                    backwardBlockToBreak = behindPos.up();
                }
            } else {
                warning("Cannot go backward and no blocks to break. Stuck! Trying random direction...");
                Direction randomDir = Math.random() > 0.5 ? originalDirection.rotateYClockwise() : originalDirection.rotateYCounterclockwise();
                lavaAvoidanceDirection = randomDir;
                lavaAvoidancePhase = 2;
                currentDirection = randomDir;
                info("Attempting random direction: " + (randomDir == originalDirection.rotateYClockwise() ? "right" : "left"));
            }
        }
    }

    private void handleLava180TurnAndBreak() {
        if (backwardBlockToBreak == null) {
            info("No blocks to break. Resuming original direction...");
            lavaAvoidancePhase = 3;
            currentDirection = originalDirection;
            turning180 = false;
            return;
        }
        
        // turn around 180 degrees to face backward
        Direction backwardDir = originalDirection.getOpposite();
        lookInDirection(backwardDir);

        // break whatever's blocking us
        smoothLookAtBlock(backwardBlockToBreak);
        performMiningAction();

        // check if we broke it
        if (mc.world.getBlockState(backwardBlockToBreak).isAir()) {
            info("✅ Block behind cleared. Checking for more blocks or resuming...");
            backwardBreakAttempts = 0;

            // check if there are more blocks behind us
            BlockPos currentPos = mc.player.getBlockPos();
            BlockPos nextBehind = currentPos.offset(backwardDir);
            BlockState nextBehindState = mc.world.getBlockState(nextBehind);
            BlockState nextBehindStateUp = mc.world.getBlockState(nextBehind.up());

            if (!nextBehindState.isAir() && isBlockSafeToBreak(nextBehind)) {
                backwardBlockToBreak = nextBehind;
            } else if (!nextBehindStateUp.isAir() && isBlockSafeToBreak(nextBehind.up())) {
                backwardBlockToBreak = nextBehind.up();
            } else {
                // no more blocks, check if going backward is safe now
                boolean backwardNowSafe = isPathSafeForDistance(currentPos, backwardDir, safetyBackupDistance.get());
                if (backwardNowSafe) {
                    info("Backward path now clear. Going backward...");
                    tryingBackward = true;
                    lavaAvoidancePhase = 4;
                    backwardBlockToBreak = null;
                } else {
                    info("Backward path still blocked. Checking sides again...");
                    rightSideChecked = false;
                    leftSideChecked = false;
                    lavaAvoidancePhase = 1;
                    backwardBlockToBreak = null;
                    turning180 = false;
                }
            }
        } else {
            backwardBreakAttempts++;
            if (backwardBreakAttempts > 100) {
                warning("Failed to break block after 100 attempts. Trying different approach...");
                rightSideChecked = false;
                leftSideChecked = false;
                lavaAvoidancePhase = 1;
                backwardBlockToBreak = null;
                turning180 = false;
            }
        }
    }

    private boolean isPathSafeForDistance(BlockPos startPos, Direction direction, int distance) {
        for (int i = 1; i <= distance; i++) {
            BlockPos checkPos = startPos.offset(direction, i);

            // check the current level and above for dangerous stuff
            if (detectDangerAt(checkPos) || detectDangerAt(checkPos.up()) || detectLavaInArea(checkPos)) {
                return false;
            }
        }
        return true;
    }

    private boolean detectLavaInArea(BlockPos center) {
        // check a small area around this position for lava
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos checkPos = center.add(x, y, z);
                    if (isLava(mc.world.getBlockState(checkPos))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isLava(BlockState state) {
        return state.getBlock() == Blocks.LAVA ||
            (state.getBlock() instanceof FluidBlock && state.getBlock() == Blocks.LAVA);
    }

    private boolean isLargeAirPocket(BlockPos center) {
        int airBlocks = 0;
        // check a 3x3x3 area around this position
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos checkPos = center.add(x, y, z);
                    if (mc.world.getBlockState(checkPos).isAir()) {
                        airBlocks++;
                    }
                }
            }
        }
        return airBlocks > 15; // more than half the area is air
    }

    private void handleDangerAvoidance() {
        if (!isBackingUp) {
            warning("⚠️ " + dangerType + " detected ahead! Backing up " + safetyBackupDistance.get() + " blocks...");

            if (enableWebhook.get() && screenshotOnDanger.get()) {
                takeScreenshotAndSend("⚠️ Danger Detected",
                    "Found " + dangerType + " at " + dangerousBlockPos.toShortString() +
                        ". Backing up " + safetyBackupDistance.get() + " blocks for safety.");
            }

            isBackingUp = true;
            backupSteps = 0;
            backupStuckTicks = 0;
            backupStartPos = mc.player.getBlockPos(); // Store starting position for backup
        }
    }

    private void handleAvoidance() {
        if (avoidanceSteps < antiStuckDistance.get() + 2) {
            // Turn camera to avoidance direction and move forward with W
            Direction avoidanceDir = randomizeAvoidanceDirection.get() && !hasRandomizedAvoidanceDirection ?
                (Math.random() > 0.5 ? originalDirection.rotateYClockwise() : originalDirection.rotateYCounterclockwise()) :
                originalDirection.rotateYClockwise();

            if (randomizeAvoidanceDirection.get() && !hasRandomizedAvoidanceDirection) {
                hasRandomizedAvoidanceDirection = true;
                info("🎲 Randomly chose " + (avoidanceDir == originalDirection.rotateYClockwise() ? "right" : "left") + " for avoidance");
            }

            // Turn camera to face the avoidance direction, then use W to move
            moveForward(avoidanceDir);

            avoidanceSteps++;
            } else {
            // Check if original path is clear
            BlockPos checkPos = mc.player.getBlockPos().offset(originalDirection, 3);
            if (!detectDangerAt(checkPos) && !detectLavaInArea(checkPos)) {
                info("Danger avoided. Returning to original direction.");
                isAvoiding = false;
                hasRandomizedAvoidanceDirection = false;
                currentDirection = originalDirection;
                lookInDirection(currentDirection);
                dangerousBlockPos = null;
                dangerType = "";
                avoidanceSteps = 0;
                backupStartPos = null;
                stopAllMovement();
            } else {
                // Try other direction
                currentDirection = randomizeAvoidanceDirection.get() ?
                    (Math.random() > 0.5 ? originalDirection.rotateYClockwise() : originalDirection.rotateYCounterclockwise()) :
                    originalDirection.rotateYCounterclockwise();
                avoidanceSteps = 0;
            }
        }
    }

    private boolean detectDangerAt(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        BlockState stateAbove = mc.world.getBlockState(pos.up());

        return (avoidLava.get() && isLava(state)) ||
            (avoidGravel.get() && (state.getBlock() instanceof FallingBlock || stateAbove.getBlock() instanceof FallingBlock)) ||
            (avoidCaves.get() && isLargeAirPocket(pos));
    }

    private boolean isBlockSafeToBreak(BlockPos pos) {
        if (pos == null || mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();

        if (block == Blocks.AIR) return false;
        if (block == Blocks.BEDROCK) return false;
        if (isLava(state)) return false;
        if (block instanceof FallingBlock) return false;

        String blockName = block.getTranslationKey().toLowerCase();
        if (blockName.contains("water") || blockName.contains("lava")) return false;

        return true;
    }

    private boolean isLavaOrGravelNearby(BlockPos pos) {
        if (pos == null || mc.world == null) return false;

        for (Direction dir : Direction.values()) {
            BlockPos checkPos = pos.offset(dir);
            BlockState state = mc.world.getBlockState(checkPos);

            if (isLava(state)) return true;
            if (state.getBlock() instanceof FallingBlock) return true;
        }

        return false;
    }

    private void checkStuckCondition() {
        BlockPos currentPos = mc.player.getBlockPos();

        // check if we moved or mined any blocks
        if (currentPos.equals(lastPlayerPos) && blocksMined == lastBlocksMined) {
            ticksStuck++;
        } else {
            ticksStuck = 0;
            isStuck = false;
            alreadyReportedStuck = false;
        }

        // figure out if we're stuck
        if (ticksStuck >= stuckThreshold.get() && !isStuck) {
            isStuck = true;
            if (!alreadyReportedStuck) {
                alreadyReportedStuck = true;
                warning("🚫 Stuck detected! No progress for " + ticksStuck + " ticks.");

                if (enableWebhook.get() && screenshotOnStuck.get()) {
                    takeScreenshotAndSend("🚫 Mining Stuck - Enhanced Detection",
                        "**Stuck Duration:** " + ticksStuck + " ticks (" + (ticksStuck / 20.0) + " seconds)\n" +
                            "**Position:** " + currentPos.toShortString() + "\n" +
                            "**Blocks Mined:** " + blocksMined + (infiniteTunnel.get() ? "" : "/" + tunnelLength.get()) + "\n" +
                            "**Pearl-Through Available:** " + (emptyAreaDetected ? "Yes (middle-click)" : "No") + "\n" +
                            "**Action:** " + (antiStuckMovement.get() ? "Anti-stuck movement" : "Manual intervention needed"));
                }
            }
        }

        lastPlayerPos = currentPos;
        lastBlocksMined = blocksMined;
    }

    private void handleStuckMovement() {
        if (avoidanceSteps < antiStuckDistance.get()) {
            // try moving sideways (maybe random direction)
            Direction sideDirection = randomizeAvoidanceDirection.get() && !hasRandomizedAvoidanceDirection ?
                (Math.random() > 0.5 ? originalDirection.rotateYClockwise() : originalDirection.rotateYCounterclockwise()) :
                (avoidanceSteps % 2 == 0) ? originalDirection.rotateYClockwise() : originalDirection.rotateYCounterclockwise();

            if (randomizeAvoidanceDirection.get() && !hasRandomizedAvoidanceDirection) {
                hasRandomizedAvoidanceDirection = true;
                info("🎲 Randomly chose " + (sideDirection == originalDirection.rotateYClockwise() ? "right" : "left") + " for stuck movement");
            }

            lookInDirection(sideDirection);
            moveInDirection(sideDirection);
            avoidanceSteps++;
            } else {
            // reset stuck state and go back to the original direction
            isStuck = false;
            hasRandomizedAvoidanceDirection = false;
            avoidanceSteps = 0;
            currentDirection = originalDirection;
            lookInDirection(currentDirection);
            info("Anti-stuck movement complete. Resuming normal mining.");
        }
    }

    private void handleNormalMining() {
        updateBlocksToMine();
        performMining();
        handleMovement();
    }

    private void updateBlocksToMine() {
        blocksToMine.clear();
            BlockPos playerPos = mc.player.getBlockPos();

        if (pickaxeType.get() == PickaxeType.AMETHYST) {
            // ᴀᴍᴇᴛʜʏѕᴛ ᴘɪᴄᴋᴀхᴇ: Only target the center block (it mines 3x3)
            BlockPos centerBlock = playerPos.offset(currentDirection).up(); // Target center block at eye level
            if (!mc.world.getBlockState(centerBlock).isAir()) {
                blocksToMine.add(centerBlock);
            }
        } else {
            // Normal pickaxe: Mine 2x2 area in front of player with improved targeting
            BlockPos base = playerPos.offset(currentDirection);

            // Add blocks in order of priority to reduce jarring look movements
            if (!mc.world.getBlockState(base).isAir()) {
                blocksToMine.add(base);           // Bottom block first
            }
            if (!mc.world.getBlockState(base.up()).isAir()) {
                blocksToMine.add(base.up());      // Top block second
            }
        }

        // Set current target with smooth transitions
        if (!blocksToMine.isEmpty()) {
            BlockPos newTarget = blocksToMine.get(0);

            // If we have a new target and natural looking is enabled, smooth transition
            if (currentTarget == null || !currentTarget.equals(newTarget)) {
                currentTarget = newTarget;
                lastTargetBlock = newTarget;
            }
        } else {
            currentTarget = null;
        }
    }

    private void performMining() {
        if (currentTarget == null) return;

        // check if we should wait before mining again
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastMineTime < mineDelay.get() * 50) return;

        // look at the target block smoothly (but not when dealing with players)
        if (!isRandomLooking && !isDirectStaring && !isMovingAway) {
            smoothLookAtBlock(currentTarget);
        }

        // actually mine the block
        performMiningAction();

        // check if we broke the block
        if (mc.world.getBlockState(currentTarget).isAir()) {
            blocksMined++;
            blocksToMine.remove(currentTarget);
            currentTarget = null;

            // wait a tiny bit after breaking a block
            lastMineTime = currentTime;
        }
    }

    private void performMiningAction() {
        // hold left click if it's enabled
        if (holdLeftClick.get()) {
            if (mc.options != null && mc.options.attackKey != null) {
                mc.options.attackKey.setPressed(true);
            }
        }
    }

    private void handleAutoRightClick() {
        if (!autoRightClick.get()) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRightClickTime < rightClickInterval.get() * 50) return;

        // right click to fix ghost blocks
        if (mc.options != null && mc.options.useKey != null) {
            mc.options.useKey.setPressed(true);

            // let go after a short delay
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(50); // 50ms click
                    if (mc.options != null && mc.options.useKey != null) {
                        mc.options.useKey.setPressed(false);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        lastRightClickTime = currentTime;
    }

    private void handleMovement() {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos frontBottom = playerPos.offset(currentDirection);
        BlockPos frontTop = frontBottom.up();

        // check if we can move forward
        boolean canMoveForward = mc.world.getBlockState(frontBottom).isAir() &&
            mc.world.getBlockState(frontTop).isAir();

        if (canMoveForward) {
            moveInDirection(currentDirection);
        }
    }

    // MOVEMENT METHODS (only using W and S keys)
    private void stopAllMovement() {
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);   // Never used anyway
            mc.options.rightKey.setPressed(false);  // Never used anyway
        }
    }

    private void moveBackward() {
        // keep looking where we are and press S to go backward
        if (mc.options != null) {
            mc.options.backKey.setPressed(true);
            mc.options.forwardKey.setPressed(false);
            mc.options.leftKey.setPressed(false);   // Never use
            mc.options.rightKey.setPressed(false);  // Never use
        }
    }

    private void moveForward(Direction direction) {
        // turn to face the direction, then press W to go forward
        lookInDirection(direction);
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(true);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);   // Never use
            mc.options.rightKey.setPressed(false);  // Never use
        }
    }

    private void moveInDirection(Direction direction) {
        if (mc.options == null) return;

        // reset all movement keys first - we only use W and S
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);   // Never use these
        mc.options.rightKey.setPressed(false);  // Never use these

        // get which way we're facing
        Direction playerFacing = mc.player.getHorizontalFacing();

        if (direction == playerFacing) {
            // face that direction and press W to go forward
            lookInDirection(direction);
            mc.options.forwardKey.setPressed(true);
        } else if (direction == playerFacing.getOpposite()) {
            // turn around to face that direction, then press W to go forward
            lookInDirection(direction);
            mc.options.forwardKey.setPressed(true);
                        } else {
            // for any other direction (left/right), turn to face it then press W to go forward
            lookInDirection(direction);
            mc.options.forwardKey.setPressed(true);
        }

        // special case for going up/down (y level adjustment)
        if (direction == Direction.DOWN) {
            // this is just for y level adjustment
            // in practice we'll mine downward blocks instead of actually moving
        }
    }

    // LOOK DIRECTION METHODS (where to look)
    private void lookInDirection(Direction direction) {
        // don't mess with player detection looking (they use fast looking)
        if (isRandomLooking || isDirectStaring || isMovingAway) return;

        // don't interfere if we're already smoothly looking for mining
        if (isSmoothLooking && isMiningLook) return;

        if (mc.player == null) return;

        float yaw = switch (direction) {
            case NORTH -> 180.0f;
            case SOUTH -> 0.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> mc.player.getYaw();
        };

        // use smooth looking for normal mining (when adaptive smoothing is on)
        if (adaptiveSmoothing.get()) {
            setTargetLook(yaw, 0.0f, true); // true = mining look
            } else {
            // if adaptive smoothing is off, just look there right away
            mc.player.setYaw(yaw);
            mc.player.setPitch(0.0f);
        }
    }

    private void lookAtBlock(BlockPos targetBlock) {
        // Don't override player detection looking
        if (isRandomLooking || isDirectStaring || isMovingAway) return;

        Vec3d playerVec = mc.player.getEyePos();
        Vec3d targetVec = Vec3d.ofCenter(targetBlock);
        double diffX = targetVec.x - playerVec.x;
        double diffY = targetVec.y - playerVec.y;
        double diffZ = targetVec.z - playerVec.z;

        double distXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) Math.toDegrees(-Math.atan2(diffY, distXZ));

        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }

    private void smoothLookAtBlock(BlockPos targetBlock) {
        // don't mess with player detection looking (they use fast looking)
        if (isRandomLooking || isDirectStaring || isMovingAway) return;

        // only use smooth looking for mining if we're not already smoothly looking AND adaptive smoothing is on
        if (isSmoothLooking && isMiningLook) return;
        if (!adaptiveSmoothing.get()) {
            // if adaptive smoothing is off, just look directly at it
            lookAtBlock(targetBlock);
            return;
        }

        Vec3d playerVec = mc.player.getEyePos();
        Vec3d targetVec = Vec3d.ofCenter(targetBlock);

        smoothLookAtPosition(targetVec, true); // true = mining look
    }

    private double getDistanceInDirection(BlockPos from, BlockPos to, Direction direction) {
        return switch (direction) {
            case NORTH -> from.getZ() - to.getZ();
            case SOUTH -> to.getZ() - from.getZ();
            case WEST -> from.getX() - to.getX();
            case EAST -> to.getX() - from.getX();
            default -> 0;
        };
    }

    private void releaseAllKeys() {
        if (mc.options == null) return;

        mc.options.attackKey.setPressed(false);
        mc.options.useKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);

        if (mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }
    }

    // DISCORD WEBHOOK METHODS (sending screenshots and stuff)
    private void takeScreenshotAndSend(String title, String description) {
        if (!enableWebhook.get() || !isValidWebhook) {
            if (debugMode.get()) {
                warning("Webhook not enabled or invalid. Status: " + webhookStatus);
            }
            return;
        }

        if (debugMode.get()) {
            info("Taking screenshot for webhook: " + title);
        }

        CompletableFuture.runAsync(() -> {
            try {
                ScreenshotRecorder.saveScreenshot(
                    mc.runDirectory,
                    mc.getFramebuffer(),
                    (text) -> {
                        try {
                            File screenshotsDir = new File(mc.runDirectory, "screenshots");
                            File[] screenshots = screenshotsDir.listFiles((dir, name) -> name.endsWith(".png"));

                            if (screenshots != null && screenshots.length > 0) {
                                File latestScreenshot = screenshots[0];
                                for (File screenshot : screenshots) {
                                    if (screenshot.lastModified() > latestScreenshot.lastModified()) {
                                        latestScreenshot = screenshot;
                                    }
                                }

                                if (debugMode.get()) {
                                    info("Screenshot saved: " + latestScreenshot.getName());
                                }
                                sendWebhookWithImage(title, description, latestScreenshot);
                    } else {
                                error("No screenshot files found");
                            }
                        } catch (Exception e) {
                            error("Failed to process screenshot: " + e.getMessage());
                            if (debugMode.get()) {
                                e.printStackTrace();
                            }
                        }
                    }
                );
            } catch (Exception e) {
                error("Failed to take screenshot: " + e.getMessage());
                if (debugMode.get()) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void sendWebhookWithImage(String title, String description, File imageFile) {
        try {
            if (debugMode.get()) {
                info("Sending webhook to: " + webhookUrl.get().substring(0, Math.min(50, webhookUrl.get().length())) + "...");
            }

            URI uri = URI.create(webhookUrl.get());
            URL url = uri.toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("User-Agent", "TunnelBaseFinder/3.0");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            String playerName = mc.player != null ? mc.player.getName().getString() : "Unknown";
            String position = mc.player != null ? mc.player.getBlockPos().toShortString() : "Unknown";
            String timestamp = java.time.Instant.now().toString();
            String pickaxeInfo = pickaxeType.get().toString();

            String escapedTitle = escapeJson(title);
            String escapedDescription = escapeJson(description);
            String escapedPlayerName = escapeJson(playerName);
            String escapedPosition = escapeJson(position);
            String escapedPickaxeInfo = escapeJson(pickaxeInfo);

            String jsonPayload = String.format(
                "{\"embeds\":[{\"title\":\"%s\",\"description\":\"%s\\n\\n**Player:** %s\\n**Position:** %s\\n**Pickaxe:** %s\\n**Time:** %s\",\"color\":16711680,\"image\":{\"url\":\"attachment://screenshot.png\"},\"footer\":{\"text\":\"TunnelBaseFinder v3.0 Enhanced\"}}]}",
                escapedTitle, escapedDescription, escapedPlayerName, escapedPosition, escapedPickaxeInfo, timestamp
            );

            if (debugMode.get()) {
                info("JSON Payload: " + jsonPayload);
            }

            try (OutputStream os = connection.getOutputStream()) {
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));

                writer.write("--" + boundary + "\r\n");
                writer.write("Content-Disposition: form-data; name=\"payload_json\"\r\n");
                writer.write("Content-Type: application/json; charset=UTF-8\r\n");
                writer.write("\r\n");
                writer.write(jsonPayload);
                writer.write("\r\n");
                writer.flush();

                writer.write("--" + boundary + "\r\n");
                writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"screenshot.png\"\r\n");
                writer.write("Content-Type: image/png\r\n");
                writer.write("\r\n");
                writer.flush();

                try (FileInputStream fis = new FileInputStream(imageFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
                os.flush();

                writer.write("\r\n");
                writer.write("--" + boundary + "--\r\n");
                writer.flush();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == 200 || responseCode == 204) {
                webhookStatus = "Last sent: Success";
                if (debugMode.get()) {
                    info("Screenshot sent to webhook successfully (Code: " + responseCode + ")");
                }
            } else {
                webhookStatus = "Last sent: Failed (" + responseCode + ")";
                warning("Webhook failed with response code: " + responseCode);

                if (debugMode.get()) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream()))) {
                        String line;
                        StringBuilder errorResponse = new StringBuilder();
                        while ((line = reader.readLine()) != null) {
                            errorResponse.append(line).append("\n");
                        }
                        warning("Response body: " + errorResponse.toString());
        } catch (Exception e) {
                        warning("Could not read error response: " + e.getMessage());
                    }
                }
            }

            connection.disconnect();

        } catch (Exception e) {
            webhookStatus = "Last sent: Error - " + e.getMessage();
            error("Failed to send webhook: " + e.getMessage());
            if (debugMode.get()) {
                e.printStackTrace();
            }
        }
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private void validateWebhookUrl(String url) {
        if (url == null || url.isEmpty() || url.equals("YOUR_WEBHOOK_URL_HERE")) {
            webhookStatus = "Not configured";
            isValidWebhook = false;
            return;
        }

        if (!url.startsWith("https://discord.com/api/webhooks/") && !url.startsWith("https://discordapp.com/api/webhooks/")) {
            webhookStatus = "Invalid URL format";
            isValidWebhook = false;
            warning("Invalid webhook URL format. Must be a Discord webhook URL.");
            return;
        }

        webhookStatus = "Configured (not tested)";
        isValidWebhook = true;

        if (debugMode.get()) {
            info("Webhook URL validated: " + url.substring(0, Math.min(50, url.length())) + "...");
        }
    }

    private void onTestWebhookChanged(Boolean value) {
        if (value && enableWebhook.get()) {
            info("Testing webhook...");
            takeScreenshotAndSend("🧪 Enhanced Webhook Test",
                "This is a test message from TunnelBaseFinder v3.0 to verify webhook functionality.\n" +
                    "**Enhanced Features:**\n" +
                    "• Enhanced 3D Lava Detection: Range=" + lavaDetectionRange.get() + ", Width=" + lavaDetectionWidth.get() + ", Height=" + lavaDetectionHeight.get() + "\n" +
                    "• Pearl-Through Feature: " + (enablePearlThrough.get() ? "Enabled (threshold=" + String.format("%.0f%%", emptyAreaThreshold.get() * 100) + ")" : "Disabled") + "\n" +
                    "• Enhanced Backup System: " + (breakBlockingStones.get() ? "Stone breaking enabled" : "Basic backup") + "\n" +
                    "• Random Avoidance: " + (randomizeAvoidanceDirection.get() ? "Enabled" : "Disabled") + "\n" +
                    "• Pickaxe Type: " + pickaxeType.get().toString() + "\n" +
                    "• Adaptive Smoothing: " + (adaptiveSmoothing.get() ? "Enabled" : "Disabled") + "\n" +
                    "• Player Detection: " + (enablePlayerDetection.get() ? "Enabled" : "Disabled"));

            // Reset the setting after triggering
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(1000);
                    testWebhook.set(false);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        // render the block we're targeting
        if (renderTargetBlock.get() && currentTarget != null) {
            event.renderer.box(currentTarget, targetBlockColor.get(), targetBlockColor.get(), ShapeMode.Both, 0);
        }

        // render the lava detection area
        if (renderLavaDetection.get() && avoidLava.get()) {
            renderLavaDetectionArea(event);
        }

        // render the tunnel path
        if (renderPath.get() && startPos != null) {
            BlockPos currentPos = mc.player.getBlockPos();
            int pathLength = infiniteTunnel.get() ? 20 : Math.min(20, tunnelLength.get() - blocksMined);

            for (int i = 1; i <= pathLength; i++) {
                BlockPos pathBlock = currentPos.offset(currentDirection, i);

                if (pickaxeType.get() == PickaxeType.AMETHYST) {
                    // for amethyst pickaxe, show the 3x3 area
                    for (int x = -1; x <= 1; x++) {
                        for (int y = 0; y <= 2; y++) {
                            BlockPos renderPos = pathBlock.add(
                                currentDirection == Direction.NORTH || currentDirection == Direction.SOUTH ? x : 0,
                                y,
                                currentDirection == Direction.EAST || currentDirection == Direction.WEST ? x : 0
                            );
                            event.renderer.box(renderPos, pathColor.get(), pathColor.get(), ShapeMode.Lines, 0);
                        }
                    }
                } else {
                    // for normal pickaxe, show the 2x2 tunnel
                    event.renderer.box(pathBlock, pathColor.get(), pathColor.get(), ShapeMode.Lines, 0);
                    event.renderer.box(pathBlock.up(), pathColor.get(), pathColor.get(), ShapeMode.Lines, 0);
                }
            }
        }

        // render the y level line
        if (renderYLevel.get() && maintainYLevel.get()) {
            BlockPos playerPos = mc.player.getBlockPos();
            // render a horizontal line at the start y level
            for (int x = -10; x <= 10; x++) {
                for (int z = -10; z <= 10; z++) {
                    BlockPos yPos = new BlockPos(playerPos.getX() + x, startYLevel, playerPos.getZ() + z);
                    if (Math.abs(x) == 10 || Math.abs(z) == 10) {
                        event.renderer.box(yPos, yLevelColor.get(), yLevelColor.get(), ShapeMode.Lines, 0);
                    }
                }
            }
        }
    }

    private void renderLavaDetectionArea(Render3DEvent event) {
        if (mc.player == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        int scanRange = lavaDetectionRange.get();
        int scanWidth = lavaDetectionWidth.get();
        int scanHeight = lavaDetectionHeight.get();

        // render the 3D detection area
        for (int distance = 1; distance <= scanRange; distance++) {
            for (int side = -scanWidth; side <= scanWidth; side++) {
                for (int vertical = -scanHeight; vertical <= scanHeight; vertical++) {
                    BlockPos scanPos = calculateScanPosition(playerPos, distance, side, vertical);

                    // only render the outline of the detection area
                    if (Math.abs(side) == scanWidth || Math.abs(vertical) == scanHeight || distance == scanRange) {
                        event.renderer.box(scanPos, lavaDetectionColor.get(), lavaDetectionColor.get(), ShapeMode.Lines, 0);
                    }
                }
            }
        }

        // highlight where we detected lava
        if (detectedLavaPos != null) {
            event.renderer.box(detectedLavaPos, new SettingColor(255, 0, 0, 200), new SettingColor(255, 0, 0, 200), ShapeMode.Both, 0);
        }
    }

    @Override
    public String getInfoString() {
        String pickaxeInfo = pickaxeType.get() == PickaxeType.AMETHYST ? "ᴀᴍᴇᴛʜʏѕᴛ" : "Normal";
        String statusInfo = "";

        if (isPearlThroughActive) {
            statusInfo = " | 🌟 Pearl-Through (" + (pearlThroughCooldownTicks / 20) + "s)";
        } else if (playerDetected) {
            if (isMovingAway) {
                statusInfo = " | 🏃‍♂️ Moving Away (" + moveAwaySteps + "/" + moveAwayDistance.get() + ")";
            } else {
                statusInfo = " | 🚨 PLAYER DETECTED!";
            }
        } else if (isLavaAvoidance) {
            statusInfo = " | 🔥 3D Lava Avoid (Phase " + lavaAvoidancePhase + ")";
        } else if (isBreakingBlockingStones) {
            statusInfo = " | 🔨 Breaking Stones";
        } else if (isBackingUp) {
            statusInfo = " | ⬅️ Enhanced Backup";
        } else if (isAvoiding) {
            statusInfo = " | 🔄 Avoiding " + dangerType;
        } else if (isSmoothLooking) {
            statusInfo = " | 👁 Smooth Look";
        } else if (emptyAreaDetected && enablePearlThrough.get()) {
            statusInfo = " | 🕳️ Empty Area (M-Click)";
        }

        String enhancedFeatures = "";
        if (avoidLava.get()) {
            enhancedFeatures += " | 3D-Lava(" + lavaDetectionRange.get() + ":" + lavaDetectionWidth.get() + ":" + lavaDetectionHeight.get() + ")";
        }
        if (enablePearlThrough.get()) {
            enhancedFeatures += " | Pearl-Through";
        }

        return "Blocks: " + blocksMined + (infiniteTunnel.get() ? "" : "/" + tunnelLength.get()) +
            " | " + pickaxeInfo + enhancedFeatures + statusInfo;
    }
}
