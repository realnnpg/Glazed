package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

public class RegionMap extends Module {
    // Configuration groups
    private final SettingGroup displaySettings = settings.createGroup("Display");
    private final SettingGroup positionSettings = settings.createGroup("Position");
    private final SettingGroup visualSettings = settings.createGroup("Visual");
    private final SettingGroup themeSettings = settings.createGroup("Theme");

    // Position and size settings
    private final Setting<Integer> mapPosX = positionSettings.add(new IntSetting.Builder()
        .name("position-x")
        .description("Horizontal position of the region map.")
        .defaultValue(15)
        .min(0)
        .sliderMax(1920)
        .build());

    private final Setting<Integer> mapPosY = positionSettings.add(new IntSetting.Builder()
        .name("position-y")
        .description("Vertical position of the region map.")
        .defaultValue(15)
        .min(0)
        .sliderMax(1080)
        .build());

    private final Setting<Integer> cellDimension = positionSettings.add(new IntSetting.Builder()
        .name("cell-size")
        .description("Size of each map cell.")
        .defaultValue(22)
        .range(12, 50)
        .sliderRange(12, 50)
        .build());

    // Display toggles
    private final Setting<Boolean> enableCoordinates = displaySettings.add(new BoolSetting.Builder()
        .name("enable-coordinates")
        .description("Display player coordinates below map.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> enableRegionLabels = displaySettings.add(new BoolSetting.Builder()
        .name("enable-region-labels")
        .description("Show region type labels with colors.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> enableMapGrid = displaySettings.add(new BoolSetting.Builder()
        .name("enable-grid")
        .description("Draw grid lines between regions.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> enablePlayerIndicator = displaySettings.add(new BoolSetting.Builder()
        .name("enable-player-indicator")
        .description("Show player position and direction on map.")
        .defaultValue(true)
        .build());

    // Visual settings
    private final Setting<Double> mapTransparency = visualSettings.add(new DoubleSetting.Builder()
        .name("transparency")
        .description("Map background transparency level.")
        .defaultValue(0.75)
        .range(0.1, 1.0)
        .sliderRange(0.1, 1.0)
        .build());

    private final Setting<Double> labelTextSize = visualSettings.add(new DoubleSetting.Builder()
        .name("label-size")
        .description("Size of region number labels.")
        .defaultValue(0.9)
        .range(0.4, 2.5)
        .sliderRange(0.4, 2.5)
        .build());

    // Theme colors
    private final Setting<SettingColor> mapBackgroundColor = themeSettings.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Map background color.")
        .defaultValue(new SettingColor(25, 25, 25, 180))
        .build());

    private final Setting<SettingColor> playerIndicatorColor = themeSettings.add(new ColorSetting.Builder()
        .name("player-color")
        .description("Player direction indicator color.")
        .defaultValue(new SettingColor(255, 50, 50, 255))
        .build());

    private final Setting<SettingColor> gridLineColor = themeSettings.add(new ColorSetting.Builder()
        .name("grid-color")
        .description("Grid line color.")
        .defaultValue(new SettingColor(15, 15, 15, 255))
        .build());

    // Map data structures
    private final MapDataManager mapData;
    private final RegionRenderer regionRenderer;
    private final PlayerTracker playerTracker;

    public RegionMap() {
        super(GlazedAddon.esp, "Region Map",
            "DonutSMP region map and shows you your location");

        this.mapData = new MapDataManager();
        this.regionRenderer = new RegionRenderer();
        this.playerTracker = new PlayerTracker();
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!isValidRenderState()) return;

        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        MapRenderContext ctx = new MapRenderContext(
            mapPosX.get(), mapPosY.get(),
            cellDimension.get(), mapTransparency.get()
        );

        regionRenderer.renderMapBackground(ctx);
        regionRenderer.renderRegionCells(ctx, mapData);

        if (enableMapGrid.get()) {
            regionRenderer.renderGridLines(ctx, gridLineColor.get());
        }

        regionRenderer.renderRegionNumbers(ctx, mapData, labelTextSize.get());

        if (enablePlayerIndicator.get()) {
            playerTracker.renderPlayerPosition(ctx, playerPos,
                mc.player.getYaw(), playerIndicatorColor.get());
        }

        if (enableCoordinates.get()) {
            renderPlayerInfo(ctx, playerPos);
        }

        if (enableRegionLabels.get()) {
            renderRegionLegend(ctx);
        }
    }

    private boolean isValidRenderState() {
        return mc.player != null && mc.world != null;
    }

    private void renderPlayerInfo(MapRenderContext ctx, Vec3d pos) {
        int infoY = ctx.mapY + ctx.getMapHeight() + 8;

        TextRenderer.get().begin(1.0, false, true);

        String coordsText = String.format("Position: X: %d, Z: %d", (int)pos.x, (int)pos.z);
        TextRenderer.get().render(coordsText, ctx.mapX, infoY, Color.WHITE, false);

        int currentRegionId = mapData.getRegionAt(pos.x, pos.z);
        if (currentRegionId != -1) {
            String regionInfo = String.format("Current Region: %d (%s)",
                currentRegionId, mapData.getRegionTypeName(pos.x, pos.z));
            TextRenderer.get().render(regionInfo, ctx.mapX, infoY + 15, Color.WHITE, false);
        }

        TextRenderer.get().end();
    }

    private void renderRegionLegend(MapRenderContext ctx) {
        int legendStartY = ctx.mapY + ctx.getMapHeight() +
            (enableCoordinates.get() ? 45 : 15);

        String[] regionTypes = mapData.getRegionTypeNames();
        Color[] regionTypeColors = mapData.getRegionTypeColors();

        Renderer2D.COLOR.begin();
        for (int i = 0; i < regionTypes.length; i++) {
            int legendY = legendStartY + i * 16;
            Renderer2D.COLOR.quad(ctx.mapX, legendY, 14, 14, regionTypeColors[i]);
        }
    Renderer2D.COLOR.render();

        TextRenderer.get().begin(1.0, false, true);
        for (int i = 0; i < regionTypes.length; i++) {
            int legendY = legendStartY + i * 16 + 3;
            TextRenderer.get().render(regionTypes[i], ctx.mapX + 18, legendY, Color.WHITE, false);
        }
        TextRenderer.get().end();
    }

    // Map data management class
    private static class MapDataManager {
        private static final int MAP_SIZE = 9;
        private static final double REGION_SIZE = 50000.0;
        private static final double MAP_OFFSET = 225000.0;

        private final Map<Integer, RegionInfo> regionMap;
        private final String[] regionTypeNames;
        private final Color[] regionTypeColors;

        public MapDataManager() {
            this.regionMap = new HashMap<>();
            this.regionTypeNames = new String[]{
                "EU Central", "EU West", "NA East", "NA West", "Asia", "Oceania"
            };
            this.regionTypeColors = new Color[]{
                new Color(159, 206, 99, 255),  // EU Central
                new Color(0, 166, 99, 255),    // EU West
                new Color(79, 173, 234, 255),  // NA East
                new Color(47, 110, 186, 255),  // NA West
                new Color(245, 194, 66, 255),  // Asia
                new Color(252, 136, 3, 255)    // Oceania
            };

            initializeRegionData();
        }

        private void initializeRegionData() {
            int[][] regionLayout = {
                {82, 5}, {100, 3}, {101, 3}, {102, 3}, {103, 2}, {104, 2}, {105, 2}, {106, 2}, {91, 2},
                {83, 5}, {44, 3}, {75, 3}, {42, 3}, {41, 2}, {40, 2}, {39, 2}, {38, 2}, {92, 2},
                {84, 5}, {45, 3}, {14, 3}, {13, 3}, {12, 2}, {11, 2}, {10, 2}, {37, 2}, {93, 2},
                {85, 5}, {46, 5}, {74, 5}, {3, 3}, {2, 2}, {1, 2}, {25, 2}, {36, 2}, {94, 2},
                {86, 4}, {47, 4}, {72, 4}, {71, 4}, {5, 2}, {4, 2}, {24, 2}, {35, 2}, {95, 2},
                {87, 4}, {51, 1}, {17, 1}, {9, 0}, {8, 0}, {7, 0}, {23, 0}, {34, 0}, {96, 2},
                {88, 4}, {54, 1}, {18, 1}, {61, 0}, {62, 0}, {21, 0}, {22, 0}, {33, 0}, {97, 0},
                {89, 0}, {26, 1}, {27, 0}, {28, 0}, {29, 0}, {30, 0}, {59, 0}, {32, 0}, {98, 0},
                {90, 0}, {107, 1}, {108, 1}, {109, 1}, {110, 1}, {111, 1}, {112, 1}, {113, 1}, {99, 0}
            };

            for (int i = 0; i < regionLayout.length; i++) {
                int row = i / MAP_SIZE;
                int col = i % MAP_SIZE;
                int regionId = regionLayout[i][0];
                int regionType = regionLayout[i][1];

                regionMap.put(i, new RegionInfo(regionId, regionType, row, col));
            }
        }

        public RegionInfo getRegionInfo(int gridIndex) {
            return regionMap.get(gridIndex);
        }

        public int getRegionAt(double worldX, double worldZ) {
            int[] gridPos = worldToGrid(worldX, worldZ);
            if (isValidGridPosition(gridPos[0], gridPos[1])) {
                int index = gridPos[1] * MAP_SIZE + gridPos[0];
                RegionInfo info = regionMap.get(index);
                return info != null ? info.regionId : -1;
            }
            return -1;
        }

        public String getRegionTypeName(double worldX, double worldZ) {
            int[] gridPos = worldToGrid(worldX, worldZ);
            if (isValidGridPosition(gridPos[0], gridPos[1])) {
                int index = gridPos[1] * MAP_SIZE + gridPos[0];
                RegionInfo info = regionMap.get(index);
                return info != null ? regionTypeNames[info.regionType] : "Unknown";
            }
            return "Unknown";
        }

        public Color getRegionColor(int regionType) {
            return regionType >= 0 && regionType < regionTypeColors.length ?
                regionTypeColors[regionType] : Color.WHITE;
        }

        public String[] getRegionTypeNames() {
            return regionTypeNames.clone();
        }

        public Color[] getRegionTypeColors() {
            return regionTypeColors.clone();
        }

        public int[] worldToGrid(double worldX, double worldZ) {
            int gridX = (int)((worldX + MAP_OFFSET) / REGION_SIZE);
            int gridZ = (int)((worldZ + MAP_OFFSET) / REGION_SIZE);
            return new int[]{gridX, gridZ};
        }

        public double[] worldToCellPosition(double worldX, double worldZ) {
            double cellX = ((worldX + MAP_OFFSET) % REGION_SIZE) / REGION_SIZE;
            double cellZ = ((worldZ + MAP_OFFSET) % REGION_SIZE) / REGION_SIZE;
            return new double[]{cellX, cellZ};
        }

        private boolean isValidGridPosition(int gridX, int gridZ) {
            return gridX >= 0 && gridX < MAP_SIZE && gridZ >= 0 && gridZ < MAP_SIZE;
        }

        public int getMapSize() {
            return MAP_SIZE;
        }
    }

    // Region information container
    private static class RegionInfo {
        final int regionId;
        final int regionType;
        final int gridRow;
        final int gridCol;

        RegionInfo(int regionId, int regionType, int gridRow, int gridCol) {
            this.regionId = regionId;
            this.regionType = regionType;
            this.gridRow = gridRow;
            this.gridCol = gridCol;
        }
    }

    // Rendering context
    private class MapRenderContext {
        final int mapX, mapY;
        final int cellSize;
        final double transparency;

        MapRenderContext(int x, int y, int cellSize, double transparency) {
            this.mapX = x;
            this.mapY = y;
            this.cellSize = cellSize;
            this.transparency = transparency;
        }

        int getMapWidth() {
            return mapData.getMapSize() * cellSize;
        }

        int getMapHeight() {
            return mapData.getMapSize() * cellSize;
        }
    }

    // Region rendering class
    private class RegionRenderer {

        void renderMapBackground(MapRenderContext ctx) {
            Color bgColor = new Color(mapBackgroundColor.get());
            bgColor.a = (int)(ctx.transparency * 255.0);

            Renderer2D.COLOR.begin();
            Renderer2D.COLOR.quad(ctx.mapX, ctx.mapY, ctx.getMapWidth(), ctx.getMapHeight(), bgColor);
    Renderer2D.COLOR.render();
        }

        void renderRegionCells(MapRenderContext ctx, MapDataManager dataManager) {
            Renderer2D.COLOR.begin();

            int mapSize = dataManager.getMapSize();
            for (int row = 0; row < mapSize; row++) {
                for (int col = 0; col < mapSize; col++) {
                    int index = row * mapSize + col;
                    RegionInfo regionInfo = dataManager.getRegionInfo(index);

                    if (regionInfo != null) {
                        int cellX = ctx.mapX + col * ctx.cellSize;
                        int cellY = ctx.mapY + row * ctx.cellSize;

                        Color regionColor = dataManager.getRegionColor(regionInfo.regionType);
                        regionColor.a = (int)(ctx.transparency * 255.0);

                        Renderer2D.COLOR.quad(cellX + 1, cellY + 1,
                            ctx.cellSize - 2, ctx.cellSize - 2, regionColor);
                    }
                }
            }

            Renderer2D.COLOR.render();
        }

        void renderGridLines(MapRenderContext ctx, SettingColor gridColor) {
            Renderer2D.COLOR.begin();
            Color lineColor = new Color(gridColor);

            int mapSize = mapData.getMapSize();

            // Vertical grid lines
            for (int i = 0; i <= mapSize; i++) {
                int lineX = ctx.mapX + i * ctx.cellSize;
                Renderer2D.COLOR.quad(lineX, ctx.mapY, 1, ctx.getMapHeight(), lineColor);
            }

            // Horizontal grid lines
            for (int i = 0; i <= mapSize; i++) {
                int lineY = ctx.mapY + i * ctx.cellSize;
                Renderer2D.COLOR.quad(ctx.mapX, lineY, ctx.getMapWidth(), 1, lineColor);
            }

            Renderer2D.COLOR.render();
        }

        void renderRegionNumbers(MapRenderContext ctx, MapDataManager dataManager, double textScale) {
            TextRenderer.get().begin(textScale, false, true);

            int mapSize = dataManager.getMapSize();
            for (int row = 0; row < mapSize; row++) {
                for (int col = 0; col < mapSize; col++) {
                    int index = row * mapSize + col;
                    RegionInfo regionInfo = dataManager.getRegionInfo(index);

                    if (regionInfo != null) {
                        int cellX = ctx.mapX + col * ctx.cellSize;
                        int cellY = ctx.mapY + row * ctx.cellSize;

                        String numberText = String.valueOf(regionInfo.regionId);
                        double textWidth = TextRenderer.get().getWidth(numberText) * textScale;
                        double textHeight = TextRenderer.get().getHeight() * textScale;

                        double centeredX = cellX + (ctx.cellSize - textWidth) / 2.0;
                        double centeredY = cellY + (ctx.cellSize - textHeight) / 2.0;

                        TextRenderer.get().render(numberText, centeredX, centeredY, Color.WHITE, false);
                    }
                }
            }

            TextRenderer.get().end();
        }
    }

    // Player tracking class
    private class PlayerTracker {

        void renderPlayerPosition(MapRenderContext ctx, Vec3d playerPos, float yaw, SettingColor indicatorColor) {
            int[] gridPos = mapData.worldToGrid(playerPos.x, playerPos.z);

            if (gridPos[0] >= 0 && gridPos[0] < mapData.getMapSize() &&
                gridPos[1] >= 0 && gridPos[1] < mapData.getMapSize()) {

                double[] cellPos = mapData.worldToCellPosition(playerPos.x, playerPos.z);

                int indicatorX = ctx.mapX + gridPos[0] * ctx.cellSize + (int)(cellPos[0] * ctx.cellSize);
                int indicatorY = ctx.mapY + gridPos[1] * ctx.cellSize + (int)(cellPos[1] * ctx.cellSize);

                double rotationAngle = Math.toRadians(-yaw - 90.0);
                renderDirectionalIndicator(indicatorX, indicatorY, rotationAngle, indicatorColor);
            }
        }

        private void renderDirectionalIndicator(int centerX, int centerY, double angle, SettingColor color) {
            Renderer2D.COLOR.begin();
            Color indicatorCol = new Color(color);
            int arrowSize = 9;

            // Arrow tip
            int tipX = centerX + (int)(Math.cos(angle) * arrowSize);
            int tipY = centerY - (int)(Math.sin(angle) * arrowSize);

            // Arrow base points
            double leftBaseAngle = angle + Math.toRadians(135.0);
            double rightBaseAngle = angle - Math.toRadians(135.0);

            int leftBaseX = centerX + (int)(Math.cos(leftBaseAngle) * arrowSize);
            int leftBaseY = centerY - (int)(Math.sin(leftBaseAngle) * arrowSize);
            int rightBaseX = centerX + (int)(Math.cos(rightBaseAngle) * arrowSize);
            int rightBaseY = centerY - (int)(Math.sin(rightBaseAngle) * arrowSize);

            drawTriangleFilled(tipX, tipY, leftBaseX, leftBaseY, rightBaseX, rightBaseY, indicatorCol);
            Renderer2D.COLOR.render();
        }

        private void drawTriangleFilled(int x1, int y1, int x2, int y2, int x3, int y3, Color color) {
            int minY = Math.min(y1, Math.min(y2, y3));
            int maxY = Math.max(y1, Math.max(y2, y3));

            for (int scanY = minY; scanY <= maxY; scanY++) {
                int leftX = Integer.MAX_VALUE;
                int rightX = Integer.MIN_VALUE;

                // Check intersections with all three edges
                int[] intersections = {
                    getEdgeIntersection(x1, y1, x2, y2, scanY),
                    getEdgeIntersection(x2, y2, x3, y3, scanY),
                    getEdgeIntersection(x3, y3, x1, y1, scanY)
                };

                for (int intersection : intersections) {
                    if (intersection != Integer.MAX_VALUE) {
                        leftX = Math.min(leftX, intersection);
                        rightX = Math.max(rightX, intersection);
                    }
                }

                if (leftX <= rightX) {
                    Renderer2D.COLOR.quad(leftX, scanY, rightX - leftX + 1, 1, color);
                }
            }
        }

        private int getEdgeIntersection(int x1, int y1, int x2, int y2, int scanY) {
            if (scanY >= Math.min(y1, y2) && scanY <= Math.max(y1, y2)) {
                if (y1 == y2) {
                    return (x1 + x2) / 2;
                } else {
                    return x1 + (x2 - x1) * (scanY - y1) / (y2 - y1);
                }
            }
            return Integer.MAX_VALUE;
        }
    }
}
