package com.zenya.module.modules.donut;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public final class RegionMap extends Module {
    private static final int MAP_SIZE = 9;
    private static final int CELL_SIZE = 10;
    private static final int CELL_GAP = 1;
    private static final int PANEL_SIZE = 106;
    private static final double REGION_SIZE = 50000.0D;
    private static final double MAP_OFFSET = 225000.0D;

    private static final String[] REGION_TYPE_NAMES = {"EU Central", "EU West", "NA East", "NA West", "Asia", "Oceania"};
    private static final Color[] REGION_TYPE_COLORS = {
            new Color(159, 206, 99),
            new Color(0, 166, 99),
            new Color(79, 173, 234),
            new Color(47, 110, 186),
            new Color(245, 194, 66),
            new Color(252, 136, 3)
    };
    private static final int[][] REGION_LAYOUT = {
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
    private static final Map<Integer, RegionInfo> REGION_MAP = createRegionMap();

    private static RegionMap INSTANCE;

    private final Setting<Integer> backgroundOpacity = new Setting<>("Background Opacity", 90, 0, 255);

    private int panelX = 12;
    private int panelY = 148;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private boolean lastMouseDown;

    public RegionMap() {
        super("Region Map", Category.DONUT);
        INSTANCE = this;
        setDescription("Renders a draggable Donut world region map.");
        addSetting(backgroundOpacity);
    }

    @Override
    public void onDisable() {
        dragging = false;
        lastMouseDown = false;
    }

    public static void renderHud(DrawContext context) {
        RegionMap module = INSTANCE;
        if (module == null || !module.isEnabled()) return;
        module.render(context);
    }

    private void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        int width = getPanelWidth();
        int height = getPanelHeight();
        handleDrag(client, width, height);
        panelX = MathHelper.clamp(panelX, 0, Math.max(0, client.getWindow().getScaledWidth() - width));
        panelY = MathHelper.clamp(panelY, 0, Math.max(0, client.getWindow().getScaledHeight() - height));

        int mapX = panelX;
        int mapY = panelY;
        int cellStartX = mapX + 4;
        int cellStartY = mapY + 4;
        int legendX = mapX + PANEL_SIZE + 4;
        int accent = ZenyaPlus.getAccentARGB();

        int bgColor = (backgroundOpacity.getValue() << 24) | 0x000000;
        com.zenya.utils.renderer.RenderUtil.drawRoundedRect(context, mapX, mapY, width, height, 8f, bgColor, false);
        com.zenya.utils.renderer.RenderUtil.drawOutline(context, mapX, mapY, width, height, 8f, 1.5f, accent, false);

        for (int row = 0; row < MAP_SIZE; row++) {
            for (int col = 0; col < MAP_SIZE; col++) {
                RegionInfo info = REGION_MAP.get(row * MAP_SIZE + col);
                if (info == null) continue;
                int x = cellStartX + col * (CELL_SIZE + CELL_GAP);
                int y = cellStartY + row * (CELL_SIZE + CELL_GAP);
                Color c = REGION_TYPE_COLORS[MathHelper.clamp(info.regionType, 0, REGION_TYPE_COLORS.length - 1)];
                com.zenya.utils.renderer.RenderUtil.drawRoundedRect(context, x, y, CELL_SIZE, CELL_SIZE, 2f, new Color(c.getRed(), c.getGreen(), c.getBlue(), 214).getRGB(), false);
                drawScaledText(context, String.valueOf(info.regionId), x + 1.5f, y + 2.5f, 0.5f, 0xFF101010);
            }
        }

        drawPlayerDot(context, cellStartX, cellStartY);
        drawLegend(context, legendX, mapY + 4);
    }

    private void drawLegend(DrawContext context, int x, int y) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        context.drawText(tr, "Regions", x + 4, y, 0xFFE6ECF5, false);
        y += 12;
        for (int i = 0; i < REGION_TYPE_NAMES.length; i++) {
            com.zenya.utils.renderer.RenderUtil.drawRoundedRect(context, x + 4, y + 2, 6, 6, 2f, REGION_TYPE_COLORS[i].getRGB(), false);
            drawScaledText(context, REGION_TYPE_NAMES[i], x + 14, y + 1, 0.65f, 0xFFE6ECF5);
            y += 10;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        double px = client.player.getX();
        double pz = client.player.getZ();
        int region = getRegionIdAt(px, pz);
        String type = getRegionTypeName(px, pz);
        y += 4;
        drawScaledText(context, "R:" + (region >= 0 ? region : "?") + " " + type, x + 4, y, 0.65f, 0xFFE6ECF5);
        drawScaledText(context, "X:" + (int) Math.floor(px) + " Z:" + (int) Math.floor(pz), x + 4, y + 10, 0.65f, 0xFFE6ECF5);
    }

    private void drawPlayerDot(DrawContext context, int mapX, int mapY) {
        MinecraftClient client = MinecraftClient.getInstance();
        double gx = MathHelper.clamp((client.player.getX() + MAP_OFFSET) / REGION_SIZE, 0.0D, 8.99D);
        double gz = MathHelper.clamp((client.player.getZ() + MAP_OFFSET) / REGION_SIZE, 0.0D, 8.99D);
        int x = (int) Math.round(mapX + gx * (CELL_SIZE + CELL_GAP));
        int y = (int) Math.round(mapY + gz * (CELL_SIZE + CELL_GAP));
        com.zenya.utils.renderer.RenderUtil.drawRoundedRect(context, x - 2, y - 2, 5, 5, 2.5f, 0xFFFFFFFF, false);
        com.zenya.utils.renderer.RenderUtil.drawRoundedRect(context, x - 1, y - 1, 3, 3, 1.5f, ZenyaPlus.getAccentARGB(), false);
    }

    private void handleDrag(MinecraftClient client, int width, int height) {
        long window = client.getWindow().getHandle();
        double mx = client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
        double my = client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
        boolean down = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean hovered = mx >= panelX && mx <= panelX + width && my >= panelY && my <= panelY + height;

        if (down && !lastMouseDown && hovered) {
            dragging = true;
            dragOffsetX = (int) mx - panelX;
            dragOffsetY = (int) my - panelY;
        }
        if (!down) dragging = false;
        if (dragging) {
            panelX = (int) mx - dragOffsetX;
            panelY = (int) my - dragOffsetY;
        }
        lastMouseDown = down;
    }

    private void drawScaledText(DrawContext context, String text, float x, float y, float scale, int color) {
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        context.drawText(MinecraftClient.getInstance().textRenderer, text, 0, 0, color, false);
        matrices.popMatrix();
    }

    private int getPanelWidth() {
        return 188;
    }

    private int getPanelHeight() {
        return Math.max(PANEL_SIZE, 80);
    }

    private static int[] worldToGrid(double x, double z) {
        return new int[]{(int) Math.floor((x + MAP_OFFSET) / REGION_SIZE), (int) Math.floor((z + MAP_OFFSET) / REGION_SIZE)};
    }

    private static int getRegionIdAt(double x, double z) {
        int[] grid = worldToGrid(x, z);
        if (grid[0] < 0 || grid[0] >= MAP_SIZE || grid[1] < 0 || grid[1] >= MAP_SIZE) return -1;
        RegionInfo info = REGION_MAP.get(grid[1] * MAP_SIZE + grid[0]);
        return info == null ? -1 : info.regionId;
    }

    private static String getRegionTypeName(double x, double z) {
        int[] grid = worldToGrid(x, z);
        if (grid[0] < 0 || grid[0] >= MAP_SIZE || grid[1] < 0 || grid[1] >= MAP_SIZE) return "Unknown";
        RegionInfo info = REGION_MAP.get(grid[1] * MAP_SIZE + grid[0]);
        return info == null ? "Unknown" : REGION_TYPE_NAMES[MathHelper.clamp(info.regionType, 0, REGION_TYPE_NAMES.length - 1)];
    }

    private static Map<Integer, RegionInfo> createRegionMap() {
        HashMap<Integer, RegionInfo> map = new HashMap<>();
        for (int i = 0; i < REGION_LAYOUT.length; i++) {
            map.put(i, new RegionInfo(REGION_LAYOUT[i][0], REGION_LAYOUT[i][1]));
        }
        return map;
    }

    private record RegionInfo(int regionId, int regionType) {}
}
