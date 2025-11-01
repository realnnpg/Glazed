package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BeehiveESP extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<SettingColor> beehiveColor = sgGeneral.add(new ColorSetting.Builder()
        .name("beehive-color")
        .description("Full beehive box color")
        .defaultValue(new SettingColor(255, 215, 0, 100)) // Golden color for honey
        .build());

    private final Setting<ShapeMode> beehiveShapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Beehive box render mode")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw tracers to full beehives")
        .defaultValue(false)
        .build());

    private final Setting<SettingColor> tracerColor = sgGeneral.add(new ColorSetting.Builder()
        .name("tracer-color")
        .description("Beehive tracer color")
        .defaultValue(new SettingColor(255, 215, 0, 200))
        .visible(tracers::get)
        .build());

    private final Setting<Boolean> beehiveChat = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Announce full beehives in chat")
        .defaultValue(true)
        .build());

    private final SettingGroup sgFiltering = settings.createGroup("Honey Levels");

    private final Setting<Boolean> includeLevel0 = sgFiltering.add(new BoolSetting.Builder()
        .name("level-0")
        .description("Include empty beehives (0% honey)")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> includeLevel1 = sgFiltering.add(new BoolSetting.Builder()
        .name("level-1")
        .description("Include beehives with 20% honey")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> includeLevel2 = sgFiltering.add(new BoolSetting.Builder()
        .name("level-2")
        .description("Include beehives with 40% honey")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> includeLevel3 = sgFiltering.add(new BoolSetting.Builder()
        .name("level-3")
        .description("Include beehives with 60% honey")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> includeLevel4 = sgFiltering.add(new BoolSetting.Builder()
        .name("level-4")
        .description("Include beehives with 80% honey")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> includeLevel5 = sgFiltering.add(new BoolSetting.Builder()
        .name("level-5")
        .description("Include full beehives (100% honey - harvestable)")
        .defaultValue(true)
        .build());

    private final SettingGroup sgHiveTypes = settings.createGroup("Hive Types");

    private final Setting<Boolean> includeBeehives = sgHiveTypes.add(new BoolSetting.Builder()
        .name("beehives")
        .description("Include crafted beehives")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> includeBeeNests = sgHiveTypes.add(new BoolSetting.Builder()
        .name("bee-nests")
        .description("Include natural bee nests")
        .defaultValue(true)
        .build());

    private final SettingGroup sgRange = settings.createGroup("Range");

    private final Setting<Integer> minY = sgRange.add(new IntSetting.Builder()
        .name("min-y")
        .description("Minimum Y level to scan for beehives")
        .defaultValue(50)
        .min(-64)
        .max(128)
        .sliderRange(-64, 128)
        .build());

    private final Setting<Integer> maxY = sgRange.add(new IntSetting.Builder()
        .name("max-y")
        .description("Maximum Y level to scan for beehives")
        .defaultValue(200)
        .min(-64)
        .max(320)
        .sliderRange(-64, 320)
        .build());

    private final SettingGroup sgThreading = settings.createGroup("Threading");

    private final Setting<Boolean> useThreading = sgThreading.add(new BoolSetting.Builder()
        .name("enable-threading")
        .description("Use multi-threading for chunk scanning (better performance)")
        .defaultValue(true)
        .build());

    private final Setting<Integer> threadPoolSize = sgThreading.add(new IntSetting.Builder()
        .name("thread-pool-size")
        .description("Number of threads to use for scanning")
        .defaultValue(2)
        .min(1)
        .max(8)
        .sliderRange(1, 8)
        .visible(useThreading::get)
        .build());

    private final Setting<Boolean> limitChatSpam = sgThreading.add(new BoolSetting.Builder()
        .name("limit-chat-spam")
        .description("Reduce chat spam when using threading")
        .defaultValue(true)
        .visible(useThreading::get)
        .build());

    // Thread-safe collection
    private final Set<BlockPos> beehivePositions = ConcurrentHashMap.newKeySet();

    // Threading
    private ExecutorService threadPool;

    public BeehiveESP() {
        super(GlazedAddon.esp, "BeehiveESP", "ESP for beehives and bee nests when full of honey with threading and tracer support.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null) return;

        // Initialize thread pool
        if (useThreading.get()) {
            threadPool = Executors.newFixedThreadPool(threadPoolSize.get());
        }

        beehivePositions.clear();

        if (useThreading.get()) {
            // Scan chunks asynchronously
            for (Chunk chunk : Utils.chunks()) {
                if (chunk instanceof WorldChunk worldChunk) {
                    threadPool.submit(() -> scanChunkForBeehives(worldChunk));
                }
            }
        } else {
            // Scan chunks synchronously
            for (Chunk chunk : Utils.chunks()) {
                if (chunk instanceof WorldChunk worldChunk) scanChunkForBeehives(worldChunk);
            }
        }
    }

    @Override
    public void onDeactivate() {
        // Shutdown thread pool
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
            threadPool = null;
        }

        beehivePositions.clear();
    }

    @EventHandler
    private void onChunkLoad(ChunkDataEvent event) {
        if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
            threadPool.submit(() -> scanChunkForBeehives(event.chunk()));
        } else {
            scanChunkForBeehives(event.chunk());
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        BlockPos pos = event.pos;
        BlockState state = event.newState;

        // Create a task for block update processing
        Runnable updateTask = () -> {
            boolean isBeehive = isValidBeehive(state, pos.getY());
            if (isBeehive) {
                boolean wasAdded = beehivePositions.add(pos);
                if (wasAdded && beehiveChat.get() && (!useThreading.get() || !limitChatSpam.get())) {
                    int honeyLevel = getHoneyLevel(state);
                    String hiveType = getHiveTypeName(state);
                    String honeyDescription = getHoneyLevelDescription(honeyLevel);
                    info("§e[§6Beehive ESP§e] §6" + hiveType + " " + honeyDescription + " at " + pos.toShortString());
                }
            } else {
                beehivePositions.remove(pos);
            }
        };

        if (useThreading.get() && threadPool != null && !threadPool.isShutdown()) {
            threadPool.submit(updateTask);
        } else {
            updateTask.run();
        }
    }

    private void scanChunkForBeehives(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        int xStart = cpos.getStartX();
        int zStart = cpos.getStartZ();
        int yMin = Math.max(chunk.getBottomY(), minY.get());
        int yMax = Math.min(chunk.getBottomY() + chunk.getHeight(), maxY.get());

        Set<BlockPos> chunkBeehives = new HashSet<>();
        int foundCount = 0;

        for (int x = xStart; x < xStart + 16; x++) {
            for (int z = zStart; z < zStart + 16; z++) {
                for (int y = yMin; y < yMax; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (isValidBeehive(state, y)) {
                        chunkBeehives.add(pos);
                        foundCount++;
                    }
                }
            }
        }

        // Remove old beehive positions from this chunk
        beehivePositions.removeIf(pos -> {
            ChunkPos blockChunk = new ChunkPos(pos);
            return blockChunk.equals(cpos) && !chunkBeehives.contains(pos);
        });

        // Add new beehive positions
        int newBlocks = 0;
        for (BlockPos pos : chunkBeehives) {
            if (beehivePositions.add(pos)) {
                newBlocks++;
            }
        }

        // Provide chunk-level feedback to reduce spam
        if (beehiveChat.get() && foundCount > 0) {
            if (useThreading.get() && limitChatSpam.get()) {
                if (newBlocks > 0) {
                    info("§e[§6Beehive ESP§e] §6Chunk " + cpos.x + "," + cpos.z + "§e: §6" + newBlocks + " new beehives found");
                }
            } else {
                for (BlockPos pos : chunkBeehives) {
                    if (!beehivePositions.contains(pos)) {
                        BlockState state = chunk.getBlockState(pos);
                        int honeyLevel = getHoneyLevel(state);
                        String hiveType = getHiveTypeName(state);
                        String honeyDescription = getHoneyLevelDescription(honeyLevel);
                        info("§e[§6Beehive ESP§e] §6" + hiveType + " " + honeyDescription + " at " + pos.toShortString());
                    }
                }
            }
        }
    }

    private boolean isValidBeehive(BlockState state, int y) {
        if (y < minY.get() || y > maxY.get()) return false;

        // Check if it's a beehive or bee nest
        boolean isBeehive = includeBeehives.get() && state.isOf(Blocks.BEEHIVE);
        boolean isBeeNest = includeBeeNests.get() && state.isOf(Blocks.BEE_NEST);

        if (!isBeehive && !isBeeNest) return false;

        // Check honey level
        int honeyLevel = getHoneyLevel(state);

        if (includeLevel0.get() && honeyLevel == 0) return true;
        if (includeLevel1.get() && honeyLevel == 1) return true;
        if (includeLevel2.get() && honeyLevel == 2) return true;
        if (includeLevel3.get() && honeyLevel == 3) return true;
        if (includeLevel4.get() && honeyLevel == 4) return true;
        if (includeLevel5.get() && honeyLevel == 5) return true;

        return false;
    }

    private int getHoneyLevel(BlockState state) {
        if (state.isOf(Blocks.BEEHIVE) || state.isOf(Blocks.BEE_NEST)) {
            return state.get(BeehiveBlock.HONEY_LEVEL);
        }
        return -1;
    }

    private String getHiveTypeName(BlockState state) {
        if (state.isOf(Blocks.BEEHIVE)) {
            return "Beehive";
        } else if (state.isOf(Blocks.BEE_NEST)) {
            return "Bee Nest";
        }
        return "Hive";
    }

    private String getHoneyLevelDescription(int honeyLevel) {
        switch (honeyLevel) {
            case 0:
                return "(Empty - 0%)";
            case 1:
                return "(20% Honey)";
            case 2:
                return "(40% Honey)";
            case 3:
                return "(60% Honey)";
            case 4:
                return "(80% Honey)";
            case 5:
                return "(Full - 100% - HARVESTABLE)";
            default:
                return "(Unknown Level)";
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        // Use interpolated position for smooth movement
        Vec3d playerPos = mc.player.getLerpedPos(event.tickDelta);
        Color side = new Color(beehiveColor.get());
        Color outline = new Color(beehiveColor.get());
        Color tracerColorValue = new Color(tracerColor.get());

        // Get render distance
        int renderDistance = mc.options.getViewDistance().getValue() * 16;

        for (BlockPos pos : beehivePositions) {
            // Check if within render distance - this applies to both ESP box and tracers
            if (!isWithinRenderDistance(playerPos, pos, renderDistance)) continue;

            // Get the actual block state to determine honey level for color variation
            BlockState state = mc.world.getBlockState(pos);
            int honeyLevel = getHoneyLevel(state);

            // Adjust color intensity based on honey level (brighter for fuller hives)
            Color levelAdjustedSide, levelAdjustedOutline;
            float intensity = (honeyLevel + 1) / 6.0f; // Scale from 1/6 to 6/6

            switch (honeyLevel) {
                case 0:
                    // Very dim for empty hives
                    levelAdjustedSide = new Color((int)(side.r * 0.2f), (int)(side.g * 0.2f), (int)(side.b * 0.2f), side.a);
                    levelAdjustedOutline = new Color((int)(outline.r * 0.2f), (int)(outline.g * 0.2f), (int)(outline.b * 0.2f), outline.a);
                    break;
                case 1:
                    // Dim
                    levelAdjustedSide = new Color((int)(side.r * 0.4f), (int)(side.g * 0.4f), (int)(side.b * 0.4f), side.a);
                    levelAdjustedOutline = new Color((int)(outline.r * 0.4f), (int)(outline.g * 0.4f), (int)(outline.b * 0.4f), outline.a);
                    break;
                case 2:
                    // Medium dim
                    levelAdjustedSide = new Color((int)(side.r * 0.6f), (int)(side.g * 0.6f), (int)(side.b * 0.6f), side.a);
                    levelAdjustedOutline = new Color((int)(outline.r * 0.6f), (int)(outline.g * 0.6f), (int)(outline.b * 0.6f), outline.a);
                    break;
                case 3:
                    // Medium
                    levelAdjustedSide = new Color((int)(side.r * 0.8f), (int)(side.g * 0.8f), (int)(side.b * 0.8f), side.a);
                    levelAdjustedOutline = new Color((int)(outline.r * 0.8f), (int)(outline.g * 0.8f), (int)(outline.b * 0.8f), outline.a);
                    break;
                case 4:
                    // Almost full - normal brightness
                    levelAdjustedSide = side;
                    levelAdjustedOutline = outline;
                    break;
                case 5:
                    // Full - bright golden glow
                    levelAdjustedSide = new Color(Math.min(255, (int)(side.r * 1.3f)),
                        Math.min(255, (int)(side.g * 1.3f)),
                        Math.min(255, (int)(side.b * 1.1f)),
                        side.a);
                    levelAdjustedOutline = new Color(Math.min(255, (int)(outline.r * 1.3f)),
                        Math.min(255, (int)(outline.g * 1.3f)),
                        Math.min(255, (int)(outline.b * 1.1f)),
                        outline.a);
                    break;
                default:
                    levelAdjustedSide = side;
                    levelAdjustedOutline = outline;
            }

            // Render ESP box
            event.renderer.box(pos, levelAdjustedSide, levelAdjustedOutline, beehiveShapeMode.get(), 0);

            // Render tracer if enabled
            if (tracers.get()) {
                Vec3d blockCenter = Vec3d.ofCenter(pos);

                // Start tracer from slightly in front of camera to make it visible in first person
                Vec3d startPos;
                if (mc.options.getPerspective().isFirstPerson()) {
                    // First person: start tracer slightly forward from camera
                    Vec3d lookDirection = mc.player.getRotationVector();
                    startPos = new Vec3d(
                        playerPos.x + lookDirection.x * 0.5,
                        playerPos.y + mc.player.getEyeHeight(mc.player.getPose()) + lookDirection.y * 0.5,
                        playerPos.z + lookDirection.z * 0.5
                    );
                } else {
                    // Third person: use normal eye position
                    startPos = new Vec3d(
                        playerPos.x,
                        playerPos.y + mc.player.getEyeHeight(mc.player.getPose()),
                        playerPos.z
                    );
                }

                event.renderer.line(startPos.x, startPos.y, startPos.z,
                    blockCenter.x, blockCenter.y, blockCenter.z, tracerColorValue);
            }
        }
    }

    private boolean isWithinRenderDistance(Vec3d playerPos, BlockPos blockPos, int renderDistance) {
        double dx = playerPos.x - blockPos.getX() - 0.5;
        double dz = playerPos.z - blockPos.getZ() - 0.5;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        return horizontalDistance <= renderDistance;
    }
}
