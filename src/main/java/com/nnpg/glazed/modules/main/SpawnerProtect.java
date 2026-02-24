package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.PickaxeItem;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;

public class SpawnerProtect extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");

    private final Setting<Boolean> webhook = sgWebhook.add(new BoolSetting.Builder()
            .name("webhook")
            .description("Enable webhook notifications")
            .defaultValue(false)
            .build());

    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
            .name("webhook-url")
            .description("Discord webhook URL for notifications")
            .defaultValue("")
            .visible(webhook::get)
            .build());

    private final Setting<Boolean> selfPing = sgWebhook.add(new BoolSetting.Builder()
            .name("self-ping")
            .description("Ping yourself in the webhook message")
            .defaultValue(false)
            .visible(webhook::get)
            .build());

    private final Setting<String> discordId = sgWebhook.add(new StringSetting.Builder()
            .name("discord-id")
            .description("Your Discord user ID for pinging")
            .defaultValue("")
            .visible(() -> webhook.get() && selfPing.get())
            .build());

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
            .name("notifications")
            .description("Show chat feedback.")
            .defaultValue(true)
            .build());

    private final Setting<Integer> spawnerRange = sgGeneral.add(new IntSetting.Builder()
            .name("spawner-range")
            .description("Range to check for remaining spawners")
            .defaultValue(16)
            .min(1)
            .max(50)
            .sliderMax(50)
            .build());

    private final Setting<Integer> emergencyDistance = sgGeneral.add(new IntSetting.Builder()
            .name("emergency-distance")
            .description("Distance in blocks where player triggers immediate disconnect (0 to disable).")
            .defaultValue(7)
            .min(0)
            .max(20)
            .sliderMax(20)
            .build());

    private final Setting<Integer> minDetectionRange = sgGeneral.add(new IntSetting.Builder()
            .name("min-detection-range")
            .description("Minimum distance to detect a player (ignore players closer than this).")
            .defaultValue(0)
            .min(0)
            .max(50)
            .sliderMax(50)
            .build());

    private final Setting<Integer> maxDetectionRange = sgGeneral.add(new IntSetting.Builder()
            .name("max-detection-range")
            .description("Maximum distance to detect a player.")
            .defaultValue(50)
            .min(1)
            .max(100)
            .sliderMax(100)
            .build());

    private final Setting<Integer> spawnerCheckDelay = sgGeneral.add(new IntSetting.Builder()
            .name("spawner-check-delay-ms")
            .description("Delay in milliseconds before confirming all spawners are gone")
            .defaultValue(3000)
            .min(1000)
            .max(10000)
            .sliderMax(10000)
            .build());

    private final Setting<Integer> spawnerTimeout = sgGeneral.add(new IntSetting.Builder()
            .name("spawner-timeout-ms")
            .description("Time in milliseconds before skipping a spawner that can't be mined")
            .defaultValue(4000)
            .min(4000)
            .max(30000)
            .sliderMax(30000)
            .build());

    private final Setting<Boolean> depositToEChest = sgGeneral.add(new BoolSetting.Builder()
            .name("deposit-to-echest")
            .description("Deposit spawners into ender chest after mining.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> enableWhitelist = sgWhitelist.add(new BoolSetting.Builder()
            .name("enable-whitelist")
            .description("Enable player whitelist (whitelisted players won't trigger protection)")
            .defaultValue(false)
            .build());

    private final Setting<List<String>> whitelistPlayers = sgWhitelist.add(new StringListSetting.Builder()
            .name("whitelisted-players")
            .description("List of player names to ignore")
            .defaultValue(new ArrayList<>())
            .visible(enableWhitelist::get)
            .build());

    private final Setting<List<Item>> depositBlacklist = sgGeneral.add(new ItemListSetting.Builder()
            .name("deposit-blacklist")
            .description("Items that will never be deposited into the ender chest.")
            .defaultValue(Arrays.asList(
                    Items.ENDER_PEARL,
                    Items.END_CRYSTAL,
                    Items.OBSIDIAN,
                    Items.RESPAWN_ANCHOR,
                    Items.GLOWSTONE,
                    Items.TOTEM_OF_UNDYING))
            .visible(depositToEChest::get)
            .build());

    private enum State {
        IDLE,
        GOING_TO_SPAWNERS,
        GOING_TO_CHEST,
        OPENING_CHEST,
        DEPOSITING_ITEMS,
        DISCONNECTING,
        WORLD_CHANGED_ONCE,
        WORLD_CHANGED_TWICE
    }

    private State currentState = State.IDLE;
    private String detectedPlayer = "";
    private long detectionTime = 0;
    private boolean spawnersMinedSuccessfully = false;
    private boolean itemsDepositedSuccessfully = false;
    private int tickCounter = 0;
    private int transferDelayCounter = 0;
    private int lastProcessedSlot = -1;

    private boolean sneaking = false;
    private BlockPos currentTarget = null;
    private long noSpawnerStartTime = -1;
    private long currentTargetStartTime = -1;

    private BlockPos targetChest = null;
    private int chestOpenAttempts = 0;
    private boolean emergencyDisconnect = false;
    private String emergencyReason = "";

    private World trackedWorld = null;
    private int worldChangeCount = 0;
    // If there are this many or more other players online, do not activate
    // protection
    private final int PLAYER_COUNT_THRESHOLD = 3;
    private float targetYaw, targetPitch;
    private boolean rotating = false;
    private final float ROTATION_SPEED = 8.0f;
    private long respawnWaitStart = -1;
    private final Set<BlockPos> invalidSpawners = new HashSet<>();
    public SpawnerProtect() {
        super(GlazedAddon.CATEGORY, "spawner-protect",
                "Breaks spawners and puts them in your inv when a player is detected");
    }

    @Override
    public void onActivate() {
        resetState();
        configureLegitMining();

        if (mc.world != null) {
            trackedWorld = mc.world;
            worldChangeCount = 0;
            if (notifications.get())
                info("SpawnerProtect activated - Monitoring world: " + mc.world.getRegistryKey().getValue());
            if (notifications.get())
                info("Monitoring for players...");
        }

        if (notifications.get())
            ChatUtils.warning(
                    "Make sure to have an empty inventory with only a silk touch pickaxe and an ender chest nearby!");
    }

    private void resetState() {
        currentState = State.IDLE;
        detectedPlayer = "";
        detectionTime = 0;
        spawnersMinedSuccessfully = false;
        itemsDepositedSuccessfully = false;
        tickCounter = 0;
        transferDelayCounter = 0;
        lastProcessedSlot = -1;
        sneaking = false;
        currentTarget = null;
        noSpawnerStartTime = -1;
        currentTargetStartTime = -1;
        targetChest = null;
        chestOpenAttempts = 0;
        emergencyDisconnect = false;
        emergencyReason = "";
        invalidSpawners.clear();
        rotating = false;
    }

    private void configureLegitMining() {
        if (notifications.get())
            info("Manual mining mode activated");
    }

    private void disableAutoReconnectIfEnabled() {
        Module autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect != null && autoReconnect.isActive()) {
            autoReconnect.toggle();
            if (notifications.get())
                info("AutoReconnect disabled due to player detection");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null)
            return;
        if (rotating) {
            float yaw = mc.player.getYaw();
            float pitch = mc.player.getPitch();

            float yawDiff = wrapDegrees(targetYaw - yaw);
            float pitchDiff = targetPitch - pitch;

            float newYaw = yaw + Math.signum(yawDiff) * Math.min(Math.abs(yawDiff), ROTATION_SPEED);
            float newPitch = pitch + Math.signum(pitchDiff) * Math.min(Math.abs(pitchDiff), ROTATION_SPEED);

            mc.player.setYaw(newYaw);
            mc.player.setPitch(newPitch);

            if (Math.abs(yawDiff) < 1f && Math.abs(pitchDiff) < 1f) {
                rotating = false;
            }
        }

        if (currentState == State.GOING_TO_SPAWNERS) {
            mc.options.sneakKey.setPressed(true);
        } else {
            mc.options.sneakKey.setPressed(mc.options.sneakKey.isPressed());
        }

        tickCounter++;

        if (mc.world != trackedWorld) {
            handleWorldChange();
            return;
        }

        if (currentState == State.WORLD_CHANGED_ONCE) {
            return;
        }

        if (currentState == State.WORLD_CHANGED_TWICE) {
            currentState = State.IDLE;
            if (notifications.get())
                info("Returned to spawner world - resuming player monitoring");
        }

        if (checkEmergencyDisconnect()) {
            return;
        }

        if (transferDelayCounter > 0) {
            transferDelayCounter--;
            return;
        }

        switch (currentState) {
            case IDLE:
                checkForPlayers();
                break;
            case GOING_TO_SPAWNERS:
                handleGoingToSpawners();
                break;
            case GOING_TO_CHEST:
                handleGoingToChest();
                break;
            case OPENING_CHEST:
                handleOpeningChest();
                break;
            case DEPOSITING_ITEMS:
                handleDepositingItems();
                break;
            case DISCONNECTING:
                handleDisconnecting();
                break;
            case WORLD_CHANGED_ONCE:
            case WORLD_CHANGED_TWICE:
                break;
        }
    }

    private void handleWorldChange() {
        worldChangeCount++;
        trackedWorld = mc.world;

        if (worldChangeCount == 1) {
            currentState = State.WORLD_CHANGED_ONCE;
            if (notifications.get())
                info("World changed (TP to spawn) - pausing player detection until return");
        } else if (worldChangeCount == 2) {
            currentState = State.WORLD_CHANGED_TWICE;
            worldChangeCount = 0;
            if (notifications.get())
                info("World changed (back to spawners) - will resume monitoring");
        }
    }

    private float wrapDegrees(float value) {
        value %= 360.0f;
        if (value >= 180.0f)
            value -= 360.0f;
        if (value < -180.0f)
            value += 360.0f;
        return value;
    }

    private boolean checkEmergencyDisconnect() {
        if (emergencyDistance.get() <= 0)
            return false;

        long otherPlayers = mc.world.getPlayers().stream().filter(p -> p != mc.player).count();
        if (otherPlayers >= PLAYER_COUNT_THRESHOLD)
            return false;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player == null || !(player instanceof AbstractClientPlayerEntity))
                continue;

            String playerName = player.getGameProfile().getName();

            if (isPlayerWhitelisted(playerName)) {
                continue;
            }

            double distance = mc.player.distanceTo(player);
            if (distance <= emergencyDistance.get()) {
                if (notifications.get())
                    info("EMERGENCY: Player " + playerName + " came too close (" + String.format("%.1f", distance)
                            + " blocks)!");

                emergencyDisconnect = true;
                emergencyReason = "User " + playerName + " came too close";

                toggle();
                if (mc.world != null) {
                    mc.world.disconnect();
                }

                detectedPlayer = playerName;
                detectionTime = System.currentTimeMillis();

                disableAutoReconnectIfEnabled();

                currentState = State.DISCONNECTING;
                return true;
            }
        }
        return false;
    }

    private void checkForPlayers() {
        // nigga
        long otherPlayers = mc.world.getPlayers().stream().filter(p -> p != mc.player).count();
        if (otherPlayers >= PLAYER_COUNT_THRESHOLD)
            return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player == null || !(player instanceof AbstractClientPlayerEntity))
                continue;

            double distance = mc.player.getPos().distanceTo(player.getPos());
            if (distance < minDetectionRange.get() || distance > maxDetectionRange.get())
                continue;

            String playerName = player.getGameProfile().getName();

            if (isPlayerWhitelisted(playerName)) {
                continue;
            }

            detectedPlayer = playerName;
            detectionTime = System.currentTimeMillis();

            if (notifications.get())
                info("SpawnerProtect: Player detected at " + String.format("%.1f", distance) + " blocks - "
                        + detectedPlayer);

            disableAutoReconnectIfEnabled();

            currentState = State.GOING_TO_SPAWNERS;
            if (notifications.get())
                info("Player detected! Starting protection sequence...");

            break;
        }
    }

    private boolean isPlayerWhitelisted(String playerName) {
        // Check global admin list first
        AdminList adminList = Modules.get().get(AdminList.class);
        if (adminList != null && adminList.isActive() && adminList.isAdmin(playerName)) {
            return true;
        }

        if (!enableWhitelist.get() || whitelistPlayers.get().isEmpty()) {
            return false;
        }

        return whitelistPlayers.get().stream()
                .anyMatch(whitelistedName -> whitelistedName.equalsIgnoreCase(playerName));
    }

    private FindItemResult findSilkTouchPickaxe() {
        return InvUtils.find(stack -> {
            if (!(stack.getItem() instanceof PickaxeItem))
                return false;

            var enchantments = stack.getEnchantments();
            for (var entry : enchantments.getEnchantmentEntries()) {
                if (entry.getKey().matchesKey(Enchantments.SILK_TOUCH))
                    return true;
            }
            return false;
        });
    }

    private void handleGoingToSpawners() {
        mc.options.sneakKey.setPressed(true);

        // Check if current target is still valid before doing anything else
        if (currentTarget != null && mc.world.getBlockState(currentTarget).getBlock() != Blocks.SPAWNER) {
            currentTarget = null;
            currentTargetStartTime = -1;
            stopBreaking();
            noSpawnerStartTime = -1;
            return;
        }

        if (currentTarget == null) {
            currentTarget = findNearestSpawner();

            if (currentTarget == null) {
                // Start the delay timer when no spawners are found
                if (noSpawnerStartTime == -1) {
                    noSpawnerStartTime = System.currentTimeMillis();
                    if (notifications.get())
                        info("No spawners found, waiting " + spawnerCheckDelay.get() + "ms to confirm...");
                    return;
                }
                
                // Check if the delay has passed
                long elapsed = System.currentTimeMillis() - noSpawnerStartTime;
                if (elapsed < spawnerCheckDelay.get()) {
                    // Still waiting, keep checking for new spawners
                    return;
                }
                
                // Delay passed, confirm no spawners and move to chest
                invalidSpawners.clear();
                stopBreaking();
                currentState = State.GOING_TO_CHEST;
                noSpawnerStartTime = -1;
                if (notifications.get())
                    info("No more spawners in range after delay, moving to ender chest...");
                return;
            }
            
            // Found a new spawner, start the timer
            currentTargetStartTime = System.currentTimeMillis();
            if (notifications.get())
                info("Found spawner at " + currentTarget + ", distance: " + 
                    String.format("%.1f", Math.sqrt(currentTarget.getSquaredDistance(mc.player.getPos()))));
        }
        
        // Reset the no-spawner timer when we find a spawner
        noSpawnerStartTime = -1;

        // Check if we've been trying to mine this spawner for too long
        if (currentTargetStartTime != -1) {
            long timeTrying = System.currentTimeMillis() - currentTargetStartTime;
            if (timeTrying > spawnerTimeout.get()) {
                if (notifications.get())
                    info("Timeout mining spawner at " + currentTarget + " after " + spawnerTimeout.get() + "ms, skipping...");
                invalidSpawners.add(currentTarget);
                currentTarget = null;
                currentTargetStartTime = -1;
                stopBreaking();
                return;
            }
        }

        Direction side = getExposedFaceSide(currentTarget);
        
        // Start rotating towards the spawner
        lookAtBlock(currentTarget, side);

        // Mine if we're looking at the spawner
        if (mc.crosshairTarget instanceof BlockHitResult hit
                && hit.getBlockPos().equals(currentTarget)) {
            
            FindItemResult pickaxe = findSilkTouchPickaxe();
            if (!pickaxe.found()) {
                stopBreaking();
                currentState = State.GOING_TO_CHEST;
                if (notifications.get())
                    info("No silk touch pickaxe found, moving to ender chest...");
                return;
            }

            InvUtils.swap(pickaxe.slot(), true);

            mc.options.attackKey.setPressed(true);
            mc.interactionManager.updateBlockBreakingProgress(currentTarget, hit.getSide());
        }
        // Don't immediately mark as invalid - let the timeout handle it
    }

    private BlockPos findNearestSpawner() {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        // Use spawnerRange setting for distance check (squared for efficiency)
        double maxDistanceSq = spawnerRange.get() * spawnerRange.get();

        for (BlockPos pos : BlockPos.iterate(
                playerPos.add(-spawnerRange.get(), -spawnerRange.get(), -spawnerRange.get()),
                playerPos.add(spawnerRange.get(), spawnerRange.get(), spawnerRange.get()))) {

            if (mc.world.getBlockState(pos).getBlock() != Blocks.SPAWNER) continue;
            if (invalidSpawners.contains(pos)) continue;

            double distanceSq = pos.getSquaredDistance(mc.player.getPos());
            if (distanceSq > maxDistanceSq) continue;

            if (distanceSq < nearestDistance) {
                nearestDistance = distanceSq;
                nearest = pos.toImmutable();
            }
        }

        return nearest;
    }
    private boolean hasLineOfSight(BlockPos pos, Direction side) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetPos = Vec3d.ofCenter(pos).add(Vec3d.of(side.getVector()).multiply(0.5));

        // Use COLLIDER shape type which is more lenient for mining checks
        BlockHitResult result = mc.world.raycast(new net.minecraft.world.RaycastContext(
                eyePos,
                targetPos,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        if (result == null) return true; // No hit means clear path

        // If we hit the spawner itself, we have line of sight
        if (result.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                && result.getBlockPos().equals(pos)) {
            return true;
        }
        
        // If we hit a block that is air or non-solid, allow it
        if (result.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
            BlockPos hitPos = result.getBlockPos();
            // Allow if we hit air or pass-through blocks
            if (mc.world.getBlockState(hitPos).isAir()) {
                return true;
            }
            // Allow if we hit a non-full block (like tall grass, water, etc.)
            if (!mc.world.getBlockState(hitPos).isFullCube(mc.world, hitPos)) {
                return true;
            }
            // Special case: if the spawner is directly below us, allow mining
            if (hitPos.equals(mc.player.getBlockPos()) || hitPos.equals(mc.player.getBlockPos().down())) {
                return true;
            }
        }

        return false;
    }
    private void lookAtBlock(BlockPos pos) {
        lookAtBlock(pos, Direction.UP);
    }

    private void lookAtBlock(BlockPos pos, Direction side) {
        Vec3d targetPos = Vec3d.ofCenter(pos).add(Vec3d.of(side.getVector()).multiply(0.5));
        Vec3d playerPos = mc.player.getEyePos();
        Vec3d dir = targetPos.subtract(playerPos).normalize();

        targetYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        targetPitch = (float) Math.toDegrees(-Math.asin(dir.y));
        rotating = true;
    }

    private Direction getExposedFaceSide(BlockPos pos) {
        // Check if player is standing directly on this spawner
        BlockPos playerBlockPos = mc.player.getBlockPos();
        if (pos.equals(playerBlockPos.down())) {
            // Player is standing on the spawner, try to find any exposed side
            // or return DOWN if we need to mine from below (unlikely but possible)
        }
        
        for (Direction side : Direction.values()) {
            BlockPos neighbor = pos.offset(side);
            if (mc.world.getBlockState(neighbor).isAir()
                    || !mc.world.getBlockState(neighbor).isFullCube(mc.world, neighbor)) {
                return side;
            }
        }

        return Direction.UP;
    }

    private void stopBreaking() {
        mc.options.attackKey.setPressed(false);
    }

    private void handleGoingToChest() {
        if (!depositToEChest.get()) {
            currentState = State.DISCONNECTING;
            if (notifications.get())
                info("Deposit to ender chest disabled, disconnecting...");
            return;
        }

        if (targetChest == null) {
            targetChest = findNearestEnderChest();
            if (targetChest == null) {
                if (notifications.get())
                    info("No ender chest found nearby!");
                currentState = State.DISCONNECTING;
                return;
            }
            if (notifications.get())
                info("Found ender chest at " + targetChest);
        }

        moveTowardsBlock(targetChest);

        if (mc.player.getBlockPos().getSquaredDistance(targetChest) <= 9) {
            currentState = State.OPENING_CHEST;
            chestOpenAttempts = 0;
            if (notifications.get())
                info("Reached ender chest. Attempting to open...");
        }

        if (tickCounter > 600) {
            if (notifications.get())
                ChatUtils.error("Timed out trying to reach ender chest!");
            currentState = State.DISCONNECTING;
        }
    }

    private BlockPos findNearestEnderChest() {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos nearestChest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.iterate(
                playerPos.add(-16, -8, -16),
                playerPos.add(16, 8, 16))) {

            if (mc.world.getBlockState(pos).getBlock() == Blocks.ENDER_CHEST) {
                double distance = pos.getSquaredDistance(mc.player.getPos());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestChest = pos.toImmutable();
                }
            }
        }

        return nearestChest;
    }

    private void moveTowardsBlock(BlockPos target) {
        Vec3d playerPos = mc.player.getPos();
        Vec3d targetPos = Vec3d.ofCenter(target);
        Vec3d direction = targetPos.subtract(playerPos).normalize();

        double yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z));
        mc.player.setYaw((float) yaw);

        KeyBinding.setKeyPressed(mc.options.forwardKey.getDefaultKey(), true);
    }

    private void handleOpeningChest() {
        if (targetChest == null) {
            currentState = State.GOING_TO_CHEST;
            return;
        }

        if (sneaking) {
        }
        mc.options.sneakKey.setPressed(false);

        KeyBinding.setKeyPressed(mc.options.forwardKey.getDefaultKey(), false);
        KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), true);

        if (chestOpenAttempts < 20) {
            lookAtBlock(targetChest);
        }

        if (chestOpenAttempts % 5 == 0) {
            if (mc.interactionManager != null && mc.player != null) {
                mc.interactionManager.interactBlock(
                        mc.player,
                        Hand.MAIN_HAND,
                        new BlockHitResult(
                                Vec3d.ofCenter(targetChest),
                                Direction.UP,
                                targetChest,
                                false));
                if (notifications.get())
                    info("Right-clicking ender chest... (attempt " + (chestOpenAttempts / 5 + 1) + ")");
            }
        }

        chestOpenAttempts++;

        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), false);
            currentState = State.DEPOSITING_ITEMS;
            lastProcessedSlot = -1;
            tickCounter = 0;
            if (notifications.get())
                info("Ender chest opened successfully! Made by GLZD ");
        }

        if (chestOpenAttempts > 200) {
            KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), false);
            if (notifications.get())
                ChatUtils.error("Failed to open ender chest after multiple attempts!");
            currentState = State.DISCONNECTING;
        }
    }

    private void handleDepositingItems() {
        if (!depositToEChest.get()) {
            currentState = State.DISCONNECTING;
            if (notifications.get())
                info("Deposit to ender chest disabled, skipping deposit.");
            return;
        }

        mc.options.sneakKey.setPressed(false);

        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            GenericContainerScreenHandler handler = (GenericContainerScreenHandler) mc.player.currentScreenHandler;

            if (!hasItemsToDeposit()) {
                itemsDepositedSuccessfully = true;
                if (notifications.get())
                    info("All items deposited successfully!");
                mc.player.closeHandledScreen();
                transferDelayCounter = 10;
                currentState = State.DISCONNECTING;
                return;
            }

            transferItemsToChest(handler);

        } else {
            currentState = State.OPENING_CHEST;
            chestOpenAttempts = 0;
        }

        if (tickCounter > 900) {
            if (notifications.get())
                ChatUtils.error("Timed out depositing items!");
            currentState = State.DISCONNECTING;
        }
    }

    private boolean isVitalItem(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() == Items.AIR)
            return true;

        // Check if item is in the blacklist setting
        if (depositBlacklist.get().contains(stack.getItem()))
            return true;

        // Silk touch pickaxes are no longer vital so they can be deposited at the end

        if (stack.getItem() == Items.ENDER_CHEST)
            return true;

        return false;
    }

    private boolean hasItemsToDeposit() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && !isVitalItem(stack)) {
                return true;
            }
        }
        return false;
    }

    private void transferItemsToChest(GenericContainerScreenHandler handler) {
        int totalSlots = handler.slots.size();
        int chestSlots = totalSlots - 36;
        int playerInventoryStart = chestSlots;

        // Check if chest is full before depositing
        if (isChestFull(handler, chestSlots)) {
            if (notifications.get())
                error("Ender chest is full! Disconnecting for safety.");
            currentState = State.DISCONNECTING;
            return;
        }

        // Phase 1: Spawners first
        for (int i = 0; i < 36; i++) {
            int slotId = playerInventoryStart + i;
            ItemStack stack = handler.getSlot(slotId).getStack();

            if (!stack.isEmpty() && stack.getItem() == Items.SPAWNER) {
                depositSlot(handler, slotId, stack);
                return;
            }
        }

        // Phase 2: Other non-vital items
        for (int i = 0; i < 36; i++) {
            int slotId = playerInventoryStart + i;
            ItemStack stack = handler.getSlot(slotId).getStack();

            if (!stack.isEmpty() && !isVitalItem(stack)) {
                depositSlot(handler, slotId, stack);
                return;
            }
        }

        if (lastProcessedSlot >= playerInventoryStart) {
            lastProcessedSlot = playerInventoryStart - 1;
            transferDelayCounter = 3;
        }
    }

    private void depositSlot(GenericContainerScreenHandler handler, int slotId, ItemStack stack) {
        if (notifications.get())
            info("Transferring item from slot " + slotId + ": " + stack.getItem().toString());

        if (mc.interactionManager != null) {
            mc.interactionManager.clickSlot(
                    handler.syncId,
                    slotId,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player);
        }

        lastProcessedSlot = slotId;
        transferDelayCounter = 2;
    }

    private boolean isChestFull(GenericContainerScreenHandler handler, int chestSlots) {
        // If there's any empty slot, it's not full
        for (int i = 0; i < chestSlots; i++) {
            if (handler.getSlot(i).getStack().isEmpty())
                return false;
        }
        // If all slots are non-empty, we consider it possibly full.
        // Note: It might still have stackable space, but requirement says "deconnect if
        // full".
        // A more robust check would check if any stackable item can fit, but this is
        // usually what users mean.
        return true;
    }

    private void handleDisconnecting() {
        KeyBinding.setKeyPressed(mc.options.forwardKey.getDefaultKey(), false);
        KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), false);

        sendWebhookNotification();

        if (emergencyDisconnect) {
            if (notifications.get())
                info("SpawnerProtect: " + emergencyReason + ". Successfully disconnected.");
        } else {
            if (notifications.get())
                info("SpawnerProtect: " + detectedPlayer + " detected. Successfully disconnected.");
        }

        if (mc.world != null) {
            mc.world.disconnect();
        }

        if (notifications.get())
            info("Disconnected due to player detection.");
        toggle();
    }

    private void sendWebhookNotification() {
        if (!webhook.get() || webhookUrl.get() == null || webhookUrl.get().trim().isEmpty()) {
            if (notifications.get())
                info("Webhook disabled or URL not configured.");
            return;
        }

        String webhookUrlValue = webhookUrl.get().trim();

        long discordTimestamp = detectionTime / 1000L;

        String messageContent = "";
        if (selfPing.get() && discordId.get() != null && !discordId.get().trim().isEmpty()) {
            messageContent = String.format("<@%s>", discordId.get().trim());
        }

        String embedJson = createWebhookPayload(messageContent, discordTimestamp);

        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrlValue))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(embedJson))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    if (notifications.get())
                        info("Webhook notification sent successfully!");
                } else {
                    if (notifications.get())
                        ChatUtils.error("Failed to send webhook notification. Status: " + response.statusCode());
                }
            } catch (Exception e) {
                if (notifications.get())
                    ChatUtils.error("Failed to send webhook notification: " + e.getMessage());
            }
        }).start();
    }

    private String createWebhookPayload(String messageContent, long discordTimestamp) {
        String title = emergencyDisconnect ? "SpawnerProtect Emergency Alert" : "SpawnerProtect Alert";
        String description;

        if (emergencyDisconnect) {
            description = String.format(
                    "**Player Detected:** %s\\n**Detection Time:** <t:%d:R>\\n**Reason:** %s\\n**Disconnected:** Yes",
                    escapeJson(detectedPlayer), discordTimestamp, escapeJson(emergencyReason));
        } else {
            description = String.format(
                    "**Player Detected:** %s\\n**Detection Time:** <t:%d:R>\\n**Spawners Mined:** %s\\n**Items Deposited:** %s\\n**Disconnected:** Yes",
                    escapeJson(detectedPlayer), discordTimestamp,
                    spawnersMinedSuccessfully ? "✅ Success" : "❌ Failed",
                    itemsDepositedSuccessfully ? "✅ Success" : "❌ Failed");
        }

        int color = emergencyDisconnect ? 16711680 : 16766720;

        return String.format("""
                {
                    "username": "Glazed Webhook",
                    "avatar_url": "https://i.imgur.com/OL2y1cr.png",
                    "content": "%s",
                    "embeds": [{
                        "title": "%s",
                        "description": "%s",
                        "color": %d,
                        "timestamp": "%s",
                        "footer": {
                            "text": "Sent by Glazed"
                        }
                    }]
                }""",
                escapeJson(messageContent),
                title,
                description,
                color,
                Instant.now().toString());
    }

    private String escapeJson(String input) {
        if (input == null)
            return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public void onDeactivate() {
        stopBreaking();
        KeyBinding.setKeyPressed(mc.options.forwardKey.getDefaultKey(), false);
        KeyBinding.setKeyPressed(mc.options.jumpKey.getDefaultKey(), false);
    }
}