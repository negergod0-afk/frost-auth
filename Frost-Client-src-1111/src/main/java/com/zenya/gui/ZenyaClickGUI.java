package com.zenya.gui;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.ModuleManager;
import com.zenya.setting.ActionSetting;
import com.zenya.setting.ConfirmBooleanSetting;
import com.zenya.setting.ModeSetting;
import com.zenya.setting.SectionSetting;
import com.zenya.setting.Setting;
import com.zenya.utils.ZenyaFont;
import com.zenya.utils.renderer.RenderUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ZenyaClickGUI extends Screen {

    private static final int PANEL_W = 900;
    private static final int PANEL_H = 600;
    private static final int SIDEBAR_W = 210;
    private static final int HEADER_H = 64;
    private static final int CARD_H = 56;
    private static final int CARD_GAP = 10;
    private static final int CONTENT_PAD = 16;
    private static final float PANEL_RADIUS = 22.0f;
    private static final float CARD_RADIUS = 14.0f;

    private static final int COLOR_DIM = 0x88000000;
    private static int COLOR_PANEL_BG()   { 
        if (com.zenya.gui.ZenyaClickGUI.isBlockOrStorageEspOpen()) return 0xDD1E1E22;
        return 0xDD18181C; 
    }
    private static int COLOR_SIDEBAR_BG() { 
        if (com.zenya.gui.ZenyaClickGUI.isBlockOrStorageEspOpen()) return 0xDD25252A;
        return 0xDD1E1E24; 
    }
    private static final int COLOR_DIVIDER = 0xFF35353A;
    private static final int COLOR_CARD_BG = 0xFF24242A;
    private static final int COLOR_CARD_HOVER = 0xFF425268;
    private static final int COLOR_CARD_ENABLED = 0xFF7F1D1D;
    private static final int COLOR_TEXT = 0xFFF0F0F0;
    private static final int COLOR_TEXT_MUTED = 0xFFB0B0C0;
    private static final int COLOR_TEXT_DIM = 0xFF888899;
    private static int COLOR_ACCENT = 0xFFEF4444;
    private static final int COLOR_ACCENT_STRONG = 0xFFF87171;
    private static final int COLOR_ACCENT_BG = 0xFFB91C1C;
    private static final int COLOR_ACCENT_BG_SOFT = 0xFF7F1D1D;
    private static final int COLOR_CHIP_BG = 0xFF5A6478;
    private static final int COLOR_SEARCH_BG = 0xFF4D566B;
    private static final int COLOR_KNOB_OFF = 0xFFFFFFFF;
    private static final int COLOR_ROW_BORDER = 0xFF3D4A62;

    private static final Identifier MODULE_ENABLE_SOUND = Identifier.of("zenya", "module_enable");
    private static final Identifier MODULE_DISABLE_SOUND = Identifier.of("zenya", "module_disable");

    private static final Category[] CATEGORY_ORDER = Category.values();

    private Category selectedCategory = Category.COMBAT;
    private String searchQuery = "";
    private boolean searchActive = false;
    private int scrollY = 0;

    private float uiScale = 1.0f;

    private final java.util.IdentityHashMap<Object, float[]> anims = new java.util.IdentityHashMap<>();
    private long lastFrameNanos = 0L;
    private float deltaSec = 0f;
    private long openedAtNanos = 0L;
    private static final int ANIM_SLOTS = 6;
    private static final Object GLOBAL_KEY = new Object();

    private void tickAnimations() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        if (openedAtNanos == 0L) openedAtNanos = now;
        deltaSec = Math.min(0.10f, (now - lastFrameNanos) / 1.0e9f);
        lastFrameNanos = now;
    }

    private float animate(Object key, int slot, float target, float speed) {
        float[] arr = anims.computeIfAbsent(key, k -> new float[ANIM_SLOTS]);
        float k = 1f - (float) Math.exp(-deltaSec * speed);
        arr[slot] += (target - arr[slot]) * k;
        return arr[slot];
    }

    private float animValue(Object key, int slot) {
        float[] arr = anims.get(key);
        return arr == null ? 0f : arr[slot];
    }

    private float openedFor() {
        return (System.nanoTime() - openedAtNanos) / 1.0e9f;
    }

    private static float easeOut(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float c1 = 1.70158f;
        float c3 = c1 + 1.1f;
        return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
    }

    private Module settingsTarget = null;
    private long settingsOpenedAtNanos = 0L;
    private int settingsScrollY = 0;

    private boolean configsView = false;
    private long configsOpenedAtNanos = 0L;
    private String configNameBuffer = "";
    private boolean configNameFocused = false;
    private String configShareBuffer = "";
    private boolean configShareFocused = false;
    private int configsListScroll = 0;
    private String configsToast = null;
    private long configsToastShownAt = 0L;
    private Runnable configActionToTrigger = null;
    private final java.util.List<int[]> configsButtonRects = new java.util.ArrayList<>();
    private final java.util.List<Runnable> configsButtonActions = new java.util.ArrayList<>();
    private boolean listeningBind = false;
    private boolean listeningActivationBind = false;
    private Setting<?> draggingSlider = null;

    private Setting<java.awt.Color> expandedColorSetting = null;
    private long colorPickerOpenedAtNanos = 0L;
    private final float[] pickerHSV = new float[3];
    private int pickerAlpha = 255;
    private enum OtherAction { FRIENDS, CONFIGS, HUD }
    private enum ColorDragMode { NONE, SV, HUE, ALPHA }
    private ColorDragMode colorDragMode = ColorDragMode.NONE;
    private int picSvX, picSvY, picSvW, picSvH;
    private int picHueX, picHueY, picHueW, picHueH;
    private int picAlphaX, picAlphaY, picAlphaW, picAlphaH;

    private Category hoverCategory;
    private OtherAction hoverOther;
    private Module hoverModule;
    private boolean hoverBackButton = false;
    private Setting<?> hoverSetting = null;
    private SettingHitKind hoverSettingKind = SettingHitKind.NONE;
    private int hoverSettingX, hoverSettingY, hoverSettingW, hoverSettingH;
    private Setting<String> focusedStringSetting = null;
    private boolean openingSoundPlayed = false;

    public static boolean isBlockOrStorageEspOpen() {
        return lastInstance != null && lastInstance.settingsTarget != null && 
               (lastInstance.settingsTarget.getName().equals("Block ESP") || lastInstance.settingsTarget.getName().equals("Storage ESP"));
    }
    private static ZenyaClickGUI lastInstance;

    private static final Identifier SOUND_GUI_OPEN = Identifier.of("zenya", "gui_open");
    private static final Identifier SOUND_GUI_CLOSE = Identifier.of("zenya", "gui_close");

    private enum SettingHitKind { NONE, TOGGLE, SLIDER, MODE, ACTION, BIND, ACTIVATION_BIND, COLOR_TOGGLE, STRING }

    public ZenyaClickGUI() {
        super(Text.literal("Zenya Client"));
    }

    @Override
    protected void init() {
        super.init();
        lastInstance = this;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
    }

    @Override
    protected void applyBlur(DrawContext context) {
    }

    private float computeUiScale() {
        float step = com.zenya.module.modules.client.ZenyaPlus.menuSizeRaw();
        if (step < 1f) step = 1f;
        if (step > 10f) step = 10f;
        return 0.55f + ((step - 1f) * (1.45f / 9f));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        tickAnimations();
        uiScale = computeUiScale();
        int mx = (int) Math.round(mouseX / uiScale);
        int my = (int) Math.round(mouseY / uiScale);

        // Update accent color with rainbow support
        boolean rainbow = com.zenya.module.modules.client.Themes.isRainbow();
        if (rainbow) {
            COLOR_ACCENT = com.zenya.module.modules.client.Themes.rainbowAt(0, 0.05f);
        } else {
            COLOR_ACCENT = com.zenya.module.modules.client.ZenyaPlus.getAccentARGB();
        }

        // Play opening sound once when GUI first opens
        if (!openingSoundPlayed && openedFor() > 0.02f) {
            playCustomSound(SOUND_GUI_OPEN, 1.0f, 1.0f);
            openingSoundPlayed = true;
        }

        float linearT = Math.min(1f, openedFor() / 0.20f);
        float openT = easeOut(Math.min(1f, openedFor() / 0.30f));
        int baseDimAlpha = (int) (com.zenya.module.modules.client.ZenyaPlus.backgroundDim() * 255f) & 0xFF;
        int dimAlpha = (int) (baseDimAlpha * linearT) & 0xFF;
        int dimColor = (dimAlpha << 24) | (COLOR_DIM & 0x00FFFFFF);

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(uiScale, uiScale);

        int uiW = Math.round(this.width / uiScale);
        int uiH = Math.round(this.height / uiScale);

        context.fill(0, 0, uiW, uiH, dimColor);

        int panelX = (uiW - PANEL_W) / 2;
        float slideY = (1f - openT) * 40f;
        int panelY = (uiH - PANEL_H) / 2 + (int) slideY;

        float cx = panelX + PANEL_W / 2.0f;
        float cy = panelY + PANEL_H / 2.0f;
        context.getMatrices().translate(cx, cy);
        context.getMatrices().scale(openT, openT);
        context.getMatrices().translate(-cx, -cy);

        // Draw main panel background with only right corners rounded
        RenderUtil.drawRoundedRect(
                context,
                panelX, panelY,
                PANEL_W, PANEL_H,
                0, PANEL_RADIUS, PANEL_RADIUS, 0,
                false,
                COLOR_PANEL_BG()
        );

        // Draw sidebar background with only left corners rounded
        RenderUtil.drawRoundedRect(
                context,
                panelX, panelY,
                SIDEBAR_W, PANEL_H,
                PANEL_RADIUS, 0, 0, PANEL_RADIUS,
                false,
                COLOR_SIDEBAR_BG()
        );

        // Removed sidebar divider for a cleaner look

        hoverCategory = null;
        hoverOther = null;
        hoverModule = null;
        hoverBackButton = false;
        hoverSetting = null;
        hoverSettingKind = SettingHitKind.NONE;

        renderSidebar(context, panelX, panelY, mx, my);

        if (configsView) {
            float configsT = easeOut(Math.min(1f,
                    (System.nanoTime() - configsOpenedAtNanos) / 0.30e9f));
            int slideX = (int) ((1f - configsT) * 24f);
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(slideX, 0);
            
            // Draw the overlay with the same outer right corners as the main panel.
            int configsX = panelX + SIDEBAR_W;
            int configsW = PANEL_W - SIDEBAR_W;
            RenderUtil.drawRoundedRect(
                    context,
                    configsX, panelY,
                    configsW, PANEL_H,
                    0, PANEL_RADIUS, PANEL_RADIUS, 0,
                    false,
                    COLOR_PANEL_BG()
            );
            
            renderConfigsHeader(context, panelX + SIDEBAR_W, panelY, mx, my);
            renderConfigsPanel(context, panelX + SIDEBAR_W, panelY + HEADER_H, mx, my);
            context.getMatrices().popMatrix();
        } else if (settingsTarget != null) {
            float settingsT = easeOut(Math.min(1f,
                    (System.nanoTime() - settingsOpenedAtNanos) / 0.30e9f));
            int slideX = (int) ((1f - settingsT) * 24f);
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(slideX, 0);
            
            // Draw the overlay with the same outer right corners as the main panel.
            int settingsX = panelX + SIDEBAR_W;
            int settingsW = PANEL_W - SIDEBAR_W;
            RenderUtil.drawRoundedRect(
                    context,
                    settingsX, panelY,
                    settingsW, PANEL_H,
                    0, PANEL_RADIUS, PANEL_RADIUS, 0,
                    false,
                    COLOR_PANEL_BG()
            );
            
            renderSettingsHeader(context, panelX + SIDEBAR_W, panelY, mx, my);
            renderSettingsPanel(context, panelX + SIDEBAR_W, panelY + HEADER_H, mx, my);
            context.getMatrices().popMatrix();
        } else {
            renderHeader(context, panelX + SIDEBAR_W, panelY, mx, my);
            renderModuleGrid(context, panelX + SIDEBAR_W, panelY + HEADER_H, mx, my);
        }

        context.getMatrices().popMatrix();
    }



    private void renderSidebar(DrawContext context, int panelX, int panelY, int mouseX, int mouseY) {
        int x = panelX;
        
        // Vertically center the category list in the sidebar
        int totalH = CATEGORY_ORDER.length * 40 - 6;
        int cursorY = panelY + (PANEL_H - totalH) / 2;

        for (Category cat : CATEGORY_ORDER) {
            boolean selected = cat == selectedCategory;
            boolean hover = isHover(mouseX, mouseY, x + 12, cursorY, SIDEBAR_W - 24, 34);
            if (hover) {
                if (hoverCategory == null) playClickSound(0.05f, 1.2f);
                hoverCategory = cat;
            }
            renderSidebarItem(context, x + 12, cursorY, SIDEBAR_W - 24, 34, cat.getName(), selected, hover,
                    cat.getIconShape());
            cursorY += 40;
        }
    }

    private void playCustomSound(Identifier id, float volume, float pitch) {
        com.zenya.sound.SoundManager.play(id, volume, pitch);
    }

    private void playClickSound(float volume, float pitch) {
        // Frost only plays custom GUI/module sounds; hover/click vanilla UI sounds are intentionally disabled.
    }

    private static net.minecraft.item.ItemStack otherIcon(OtherAction a) {
        return switch (a) {
            case FRIENDS -> new net.minecraft.item.ItemStack(net.minecraft.item.Items.PLAYER_HEAD);
            case CONFIGS -> new net.minecraft.item.ItemStack(net.minecraft.item.Items.WRITABLE_BOOK);
            case HUD     -> new net.minecraft.item.ItemStack(net.minecraft.item.Items.ITEM_FRAME);
        };
    }

    private void renderSidebarItem(DrawContext context, int x, int y, int w, int h, String label,
                                    boolean selected, boolean hover, String iconShape) {
        Object key = label;
        float hoverT = animate(key, 0, hover ? 1f : 0f, 18f);
        float selectedT = animate(key, 1, selected ? 1f : 0f, 18f);

        int hoverBg = (((int) (hoverT * ((COLOR_CARD_HOVER >>> 24) & 0xFF))) << 24) | (COLOR_CARD_HOVER & 0x00FFFFFF);
        int bg = blend(hoverBg, COLOR_ACCENT_BG, selectedT);
        if (((bg >>> 24) & 0xFF) > 0) {
            RenderUtil.drawRoundedRect(context, x, y, w, h, 14.0f, bg, false);
        }
        // Subtle left accent bar when selected
        if (selectedT > 0.01f) {
            int barH = (int)(h * 0.5f * selectedT);
            int barY = y + (h - barH) / 2;
            context.fill(x + 2, barY, x + 4, barY + barH, COLOR_ACCENT);
        }

        int iconSize = 18;
        int iconX = x + 14 + (int) (hoverT * 3f);
        int iconY = y + h / 2 - iconSize / 2;

        CategoryIconRenderer.draw(context, iconX, iconY, iconSize, iconShape, 0xFFFFFFFF);

        int textColor = blend(COLOR_TEXT, 0xFFFFFFFF, selectedT);
        ZenyaFont.draw(context, this.textRenderer, label,
                iconX + iconSize + 8,
                y + h / 2 - this.textRenderer.fontHeight / 2 + 1,
                textColor, false);
    }

    private static void drawSearchIcon(DrawContext context, int x, int y, int size, int color) {
        drawIconCircle(context, x, y, size, color, 10f, 10f, 5f);
        drawIconLine(context, x, y, size, color, 14f, 14f, 20f, 20f);
    }

    private static void drawIconCircle(DrawContext context, int x, int y, int size, int color,
                                       float cx, float cy, float radius) {
        float scale = size / 24.0f;
        float diameter = radius * 2.0f * scale;
        RenderUtil.drawArc(context,
                x + (cx - radius) * scale,
                y + (cy - radius) * scale,
                diameter,
                Math.max(1.0f, size / 12.0f),
                360.0f,
                0.0f,
                color,
                false);
    }

    private static void drawIconLine(DrawContext context, int x, int y, int size, int color,
                                     float x1, float y1, float x2, float y2) {
        int px1 = iconX(x, size, x1);
        int py1 = iconY(y, size, y1);
        int px2 = iconX(x, size, x2);
        int py2 = iconY(y, size, y2);
        int dx = px2 - px1;
        int dy = py2 - py1;
        int steps = Math.max(1, Math.max(Math.abs(dx), Math.abs(dy)));
        float stroke = Math.max(1.6f, size / 8.5f);
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            drawStrokeDot(context,
                    Math.round(px1 + dx * t),
                    Math.round(py1 + dy * t),
                    stroke,
                    color);
        }
    }

    private static int iconX(int x, int size, float value) {
        return x + Math.round((value / 24.0f) * size);
    }

    private static int iconY(int y, int size, float value) {
        return y + Math.round((value / 24.0f) * size);
    }

    private static void drawStrokeDot(DrawContext context, int x, int y, float stroke, int color) {
        float half = stroke * 0.5f;
        RenderUtil.drawRoundedRect(context, x - half, y - half, stroke, stroke, half, color, false);
    }

    private enum IconShape { SQUARE, RING, CIRCLE, DIAMOND, STAR, PEOPLE, FLOPPY, GRID }

    private static void drawSidebarIcon(DrawContext context, int x, int y, int size, int color, IconShape shape) {
        switch (shape) {
            case SQUARE -> context.fill(x, y, x + size, y + size, color);
            case CIRCLE -> drawDot(context, x, y, size, color);
            case RING -> {
                drawDot(context, x, y, size, color);
                int innerSize = Math.max(2, size - 6);
                int innerX = x + (size - innerSize) / 2;
                int innerY = y + (size - innerSize) / 2;
                context.fill(innerX, innerY, innerX + innerSize, innerY + innerSize, COLOR_PANEL_BG());
            }
            case DIAMOND -> {
                int half = size / 2;
                for (int row = 0; row < size; row++) {
                    int distance = Math.abs(row - half);
                    int leftPad = distance;
                    int rightPad = distance;
                    int rowX1 = x + leftPad;
                    int rowX2 = x + size - rightPad;
                    if (rowX2 > rowX1) context.fill(rowX1, y + row, rowX2, y + row + 1, color);
                }
            }
            case STAR -> {
                int third = Math.max(2, size / 3);
                int gap = (size - third) / 2;
                context.fill(x + gap, y, x + gap + third, y + size, color);
                context.fill(x, y + gap, x + size, y + gap + third, color);
            }
            case PEOPLE -> {
                int small = size - 3;
                drawDot(context, x - 1, y + 1, small, color);
                drawDot(context, x + 4, y + 1, small, color);
            }
            case FLOPPY -> {
                context.fill(x, y, x + size, y + size, color);
                int padX = 2;
                int labelH = Math.max(2, size / 3);
                context.fill(x + padX, y + size - labelH, x + size - padX, y + size, COLOR_PANEL_BG());
            }
            case GRID -> {
                int cell = (size - 1) / 2;
                context.fill(x,             y,             x + cell,         y + cell,         color);
                context.fill(x + cell + 1,  y,             x + cell * 2 + 1, y + cell,         color);
                context.fill(x,             y + cell + 1,  x + cell,         y + cell * 2 + 1, color);
                context.fill(x + cell + 1,  y + cell + 1,  x + cell * 2 + 1, y + cell * 2 + 1, color);
            }
        }
    }



    private void renderHeader(DrawContext context, int x, int y, int mouseX, int mouseY) {
        int headerX = x + CONTENT_PAD;
        int headerY = y + 20;

        String title = selectedCategory == null ? "Zenya Client" : selectedCategory.getName();
        ZenyaFont.draw(context, this.textRenderer, title.toUpperCase(), headerX, headerY, COLOR_TEXT, false);
        
        // Simplified Search Bar Integrated into Header Right
        int searchW = 180;
        int searchX = x + (PANEL_W - SIDEBAR_W) - CONTENT_PAD - searchW; 
        int searchH = 24;
        int searchY = headerY - 4;
        
        RenderUtil.drawRoundedRect(context, searchX, searchY, searchW, searchH, 8, COLOR_SIDEBAR_BG(), false);
        RenderUtil.drawOutline(context, searchX, searchY, searchW, searchH, 8, 1f, searchActive ? COLOR_ACCENT : COLOR_DIVIDER, false);
        
        int searchIconSize = 14;
        int searchIconX = searchX + 8;
        int searchIconY = searchY + (searchH - searchIconSize) / 2;
        drawSearchIcon(context, searchIconX, searchIconY, searchIconSize,
                searchActive ? 0xFFFFFFFF : 0xFF8F94A4);

        int searchTextX = searchX + 30;
        String displayText = searchQuery.isEmpty() ? (searchActive ? "" : "Search...") : searchQuery;
        int textCol = searchQuery.isEmpty() && !searchActive ? COLOR_TEXT_DIM : COLOR_TEXT;
        ZenyaFont.draw(context, this.textRenderer, displayText, searchTextX, searchY + 6, textCol, false);
        
        if (searchActive && (System.currentTimeMillis() / 500) % 2 == 0) {
            int curX = searchTextX + ZenyaFont.width(this.textRenderer, searchQuery);
            context.fill(curX, searchY + 6, curX + 1, searchY + 18, COLOR_TEXT);
        }
    }


    private void renderModuleGrid(DrawContext context, int x, int y, int mouseX, int mouseY) {
        int w = PANEL_W - SIDEBAR_W;
        int h = PANEL_H - HEADER_H;

        List<Module> mods = visibleModules();

        int gridX = x + CONTENT_PAD;
        int gridY = y + CONTENT_PAD - scrollY;
        int gridW = w - CONTENT_PAD * 2;
        int colW = (gridW - CARD_GAP) / 2;

        context.enableScissor(x, y, x + w, y + h);

        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int col = i % 2;
            int row = i / 2;
            int cx = gridX + col * (colW + CARD_GAP);
            int cy = gridY + row * (CARD_H + CARD_GAP);
            boolean hover = isHover(mouseX, mouseY, cx, cy, colW, CARD_H);
            if (hover) hoverModule = m;
            renderModuleCard(context, cx, cy, colW, CARD_H, m, hover);
        }

        if (mods.isEmpty()) {
            String msg = searchQuery.isEmpty() ? "No modules in this category." : "No modules match \"" + searchQuery + "\".";
            ZenyaFont.draw(context, this.textRenderer, msg, gridX, gridY + 12, COLOR_TEXT_DIM, false);
        }

        context.disableScissor();

        int totalRows = (mods.size() + 1) / 2;
        int contentH = totalRows * (CARD_H + CARD_GAP) - CARD_GAP + CONTENT_PAD * 2;
        int viewport = h;
        if (contentH > viewport) {
            int maxScroll = contentH - viewport;
            scrollY = Math.max(0, Math.min(scrollY, maxScroll));
        } else {
            scrollY = 0;
        }
    }

    private void renderModuleCard(DrawContext context, int x, int y, int w, int h, Module m, boolean hover) {
        float hoverT = animate(m, 0, hover ? 1f : 0f, 20f);
        float enabledT = animate(m, 1, m.isEnabled() ? 1f : 0f, 18f);

        if (hover && hoverT < 0.1f) playClickSound(0.02f, 1.4f);

        // Smooth hover glow with eased opacity
        if (hoverT > 0.01f) {
            int glowAlpha = (int)(hoverT * hoverT * 35); // quadratic ease for softer fade
            RenderUtil.drawRoundedRect(context, x, y, w, h, CARD_RADIUS, (glowAlpha << 24) | 0xFFFFFF, false);
        }
        int cardBorder = blend(0xFF2E2E34, COLOR_ACCENT, enabledT);
        RenderUtil.drawOutline(context, x, y, w, h, CARD_RADIUS, 1.0f, cardBorder, false);

        if (enabledT > 0.01f) {
            int barH = (int) (Math.max(6, h - 20) * enabledT);
            int barY = y + (h - barH) / 2;
            RenderUtil.drawRoundedRect(context, x + 4, barY, 2.5f, barH, 1.25f, COLOR_ACCENT, false);
        }

        int titleColor = blend(COLOR_TEXT, COLOR_ACCENT, Math.max(enabledT, hoverT * 0.25f));
        int titleX = x + 16 + (int) (hoverT * 3f);
        ZenyaFont.draw(context, this.textRenderer, m.getDisplayName(),
                titleX, y + 11, titleColor, false);

        String desc = m.getDescription();
        if (desc == null || desc.isEmpty()) desc = defaultDescription(m);
        int descAlpha = (int)(0xB0 + hoverT * 0x20);
        int descColor = (descAlpha << 24) | (COLOR_TEXT_MUTED & 0x00FFFFFF);
        ZenyaFont.draw(context, this.textRenderer, desc,
                titleX, y + 11 + this.textRenderer.fontHeight + 5, descColor, false);
        
        context.getMatrices().popMatrix();
    }

    private String defaultDescription(Module m) {
        String n = m.getDisplayName();
        if (n == null || n.isBlank()) return "";
        StringBuilder sb = new StringBuilder(n.length() + 6);
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && !Character.isUpperCase(n.charAt(i - 1))) sb.append(' ');
            sb.append(c);
        }
        return sb.toString();
    }

    private static final java.util.Set<String> OTHER_MODULE_NAMES = java.util.Set.of(
            "hud", "friends", "cloud configs"
    );

    private List<Module> visibleModules() {
        List<Module> out = new ArrayList<>();
        if (selectedCategory == null) return out;
        String q = searchQuery.toLowerCase(Locale.ROOT);
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (m.getCategory() != selectedCategory) continue;
            if (OTHER_MODULE_NAMES.contains(m.getName().toLowerCase(Locale.ROOT))) continue;
            if (!q.isEmpty()
                    && !m.getName().toLowerCase(Locale.ROOT).contains(q)
                    && !m.getDisplayName().toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(m);
        }
        return out;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int button = click.button();
        double mouseX = click.x() / uiScale;
        double mouseY = click.y() / uiScale;

        int uiW = Math.round(this.width / uiScale);
        int uiH = Math.round(this.height / uiScale);

        int panelX = (uiW - PANEL_W) / 2;
        int panelY = (uiH - PANEL_H) / 2;
        int headerY = panelY + 20;
        int searchW = 180, searchH = 24;
        int searchX = panelX + (PANEL_W - SIDEBAR_W) + SIDEBAR_W - CONTENT_PAD - searchW;
        int searchY = headerY - 4;
        if (mouseX >= searchX && mouseX <= searchX + searchW
                && mouseY >= searchY && mouseY <= searchY + searchH) {
            searchActive = true;
            playClickSound(0.1f, 1.0f);
            return true;
        } else {
            searchActive = false;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (configsView) {
                if (hoverBackButton) {
                    configsView = false;
                    configNameFocused = false;
                    configShareFocused = false;
                    return true;
                }
                for (int i = configsButtonRects.size() - 1; i >= 0; i--) {
                    int[] r = configsButtonRects.get(i);
                    if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                        Runnable a = configsButtonActions.get(i);
                        if (a != null) a.run();
                        return true;
                    }
                }
                configNameFocused = false;
                configShareFocused = false;
                return true;
            }
            if (settingsTarget != null && hoverBackButton) {
                settingsTarget = null;
                listeningBind = false;
                draggingSlider = null;
                settingsScrollY = 0;
                return true;
            }
            if (settingsTarget != null && expandedColorSetting != null
                    && handlePickerMouseDown((int) mouseX, (int) mouseY)) {
                return true;
            }
            if (settingsTarget != null && hoverSettingKind != SettingHitKind.NONE) {
                clickSetting();
                if (hoverSettingKind == SettingHitKind.SLIDER) {
                    applySliderAt((int) mouseX);
                }
                return true;
            }
            if (settingsTarget != null) {
                focusedStringSetting = null;
            }
            if (hoverCategory != null) {
                selectedCategory = hoverCategory;
                settingsTarget = null;
                scrollY = 0;
                return true;
            }
            if (hoverOther != null) {
                handleOther(hoverOther);
                return true;
            }
            if (hoverModule != null) {
                hoverModule.toggle();
                return true;
            }
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (settingsTarget == null && hoverModule != null) {
                settingsTarget = hoverModule;
                settingsOpenedAtNanos = System.nanoTime();
                settingsScrollY = 0;
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (draggingSlider != null) {
            applySliderAt((int) (click.x() / uiScale));
            return true;
        }
        if (colorDragMode != ColorDragMode.NONE) {
            applyPickerDrag((int) (click.x() / uiScale), (int) (click.y() / uiScale));
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingSlider = null;
        colorDragMode = ColorDragMode.NONE;
        return super.mouseReleased(click);
    }

    private void handleOther(OtherAction action) {
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (action) {
            case FRIENDS, HUD -> {
                Module target = findModule(action == OtherAction.FRIENDS ? "Friends" : "Hud");
                if (target != null) {
                    settingsTarget = target;
                    settingsOpenedAtNanos = System.nanoTime();
                    settingsScrollY = 0;
                    listeningBind = false;
                    draggingSlider = null;
                }
            }
            case CONFIGS -> {
                configsView = true;
                configsOpenedAtNanos = System.nanoTime();
                configsListScroll = 0;
                settingsTarget = null;
                configNameFocused = false;
                configShareFocused = false;
            }
        }
    }

    private Module findModule(String name) {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (name.equalsIgnoreCase(m.getName())) return m;
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (configsView) {
            configsListScroll -= (int) (verticalAmount * 24);
            if (configsListScroll < 0) configsListScroll = 0;
        } else if (settingsTarget != null) {
            settingsScrollY -= (int) (verticalAmount * 24);
            if (settingsScrollY < 0) settingsScrollY = 0;
        } else {
            scrollY -= (int) (verticalAmount * 24);
            if (scrollY < 0) scrollY = 0;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();

        if (configsView && (configNameFocused || configShareFocused)) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (configNameFocused && !configNameBuffer.isEmpty()) {
                    configNameBuffer = configNameBuffer.substring(0, configNameBuffer.length() - 1);
                } else if (configShareFocused && !configShareBuffer.isEmpty()) {
                    configShareBuffer = configShareBuffer.substring(0, configShareBuffer.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                configNameFocused = false;
                configShareFocused = false;
                return true;
            }
            return true;
        }
        if (configsView && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            configsView = false;
            return true;
        }

        if (focusedStringSetting != null) {
            String current = focusedStringSetting.getValue() == null ? "" : focusedStringSetting.getValue();
            if (isControlDown() && keyCode == GLFW.GLFW_KEY_C) {
                MinecraftClient.getInstance().keyboard.setClipboard(current);
                return true;
            }
            if (isControlDown() && keyCode == GLFW.GLFW_KEY_X) {
                MinecraftClient.getInstance().keyboard.setClipboard(current);
                focusedStringSetting.setValue("");
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!current.isEmpty()) {
                    int end = current.offsetByCodePoints(current.length(), -1);
                    focusedStringSetting.setValue(current.substring(0, end));
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                focusedStringSetting = null;
                return true;
            }
            if (isControlDown() && keyCode == GLFW.GLFW_KEY_V) {
                String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    focusedStringSetting.setValue(limitStringInput(current + sanitizeStringInput(clip), focusedStringSetting));
                }
                return true;
            }
            return true;
        }

        if (listeningBind && settingsTarget != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                settingsTarget.setBind(0);
            } else {
                settingsTarget.setBind(keyCode);
            }
            listeningBind = false;
            return true;
        }
        if (listeningActivationBind && settingsTarget instanceof com.zenya.module.ActivatableModule am) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                am.setActivationKey(0);
            } else {
                am.setActivationKey(keyCode);
            }
            listeningActivationBind = false;
            return true;
        }

        if (searchActive) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchActive = false;
                searchQuery = "";
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                searchActive = false;
                return true;
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE && settingsTarget != null) {
            settingsTarget = null;
            settingsScrollY = 0;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        int codePoint = input.codepoint();
        if (configsView && configNameFocused) {
            if (codePoint >= 32 && codePoint < 127 && configNameBuffer.length() < 48) {
                configNameBuffer += (char) codePoint;
            }
            return true;
        }
        if (configsView && configShareFocused) {
            if (codePoint >= 32 && codePoint < 127 && configShareBuffer.length() < 64) {
                configShareBuffer += (char) codePoint;
            }
            return true;
        }
        if (focusedStringSetting != null) {
            if (codePoint >= 32 && codePoint < 127) {
                String current = focusedStringSetting.getValue() == null ? "" : focusedStringSetting.getValue();
                focusedStringSetting.setValue(limitStringInput(current + (char) codePoint, focusedStringSetting));
            }
            return true;
        }
        if (!searchActive) return false;
        if (codePoint >= 32 && codePoint < 127 && searchQuery.length() < 32) {
            searchQuery += (char) codePoint;
            return true;
        }
        return false;
    }

    private void renderSettingsHeader(DrawContext context, int x, int y, int mouseX, int mouseY) {
        int w = PANEL_W - SIDEBAR_W;
        context.fill(x + 1, y + HEADER_H, x + w - 1, y + HEADER_H + 1, COLOR_DIVIDER);

        int backW = 28, backH = 28;
        int backX = x + CONTENT_PAD;
        int backY = y + (HEADER_H - backH) / 2;
        boolean hb = isHover(mouseX, mouseY, backX, backY, backW, backH);
        hoverBackButton = hb;
        RenderUtil.drawRoundedRect(context, backX, backY, backW, backH, 6.0f,
                hb ? COLOR_CARD_HOVER : COLOR_CARD_BG, false);
        ZenyaFont.draw(context, this.textRenderer, "<", backX + backW / 2 - 2,
                backY + backH / 2 - this.textRenderer.fontHeight / 2 + 1, COLOR_TEXT, false);

        int titleX = backX + backW + 12;
        ZenyaFont.draw(context, this.textRenderer, settingsTarget.getDisplayName(), titleX,
                y + HEADER_H / 2 - this.textRenderer.fontHeight - 1, COLOR_TEXT, false);
        String sub = settingsTarget.getCategory().getName() + " · " +
                (settingsTarget.isEnabled() ? "Enabled" : "Disabled");
        ZenyaFont.draw(context, this.textRenderer, sub, titleX,
                y + HEADER_H / 2 + 2, COLOR_TEXT_MUTED, false);

        int pillW = 64, pillH = 24;
        int pillX = x + w - CONTENT_PAD - pillW;
        int pillY = y + (HEADER_H - pillH) / 2;
        boolean hp = isHover(mouseX, mouseY, pillX, pillY, pillW, pillH);
        boolean enabled = settingsTarget.isEnabled();
        int pillBg = enabled ? COLOR_ACCENT_BG : (hp ? 0xFF5A6880 : 0xFF4A5878);
        RenderUtil.drawRoundedRect(context, pillX, pillY, pillW, pillH, pillH / 2.0f, pillBg, false);
        int knobSize = pillH - 6;
        float headerKnobT = animate(settingsTarget, 3, enabled ? 1f : 0f, 18f);
        int hPillBorder = blend(COLOR_ROW_BORDER, COLOR_ACCENT_STRONG, headerKnobT);
        RenderUtil.drawOutline(context, pillX, pillY, pillW, pillH, pillH / 2.0f, 1.0f, hPillBorder, false);
        int hKnobMin = pillX + 3;
        int hKnobMax = pillX + pillW - knobSize - 3;
        int knobX = (int) (hKnobMin + (hKnobMax - hKnobMin) * headerKnobT);
        int knobY = pillY + 3;
        int hKnobColor = blend(COLOR_KNOB_OFF, COLOR_ACCENT, headerKnobT);
        drawDot(context, knobX, knobY, knobSize, hKnobColor);

        if (hp) {
            hoverSettingKind = SettingHitKind.TOGGLE;
            hoverSetting = null;
            hoverSettingX = pillX; hoverSettingY = pillY;
            hoverSettingW = pillW; hoverSettingH = pillH;
        }
    }

    private void renderSettingsPanel(DrawContext context, int x, int y, int mouseX, int mouseY) {
        int w = PANEL_W - SIDEBAR_W;
        int h = PANEL_H - HEADER_H;

        int rowX = x + CONTENT_PAD;
        int rowW = w - CONTENT_PAD * 2;
        int rowY = y + CONTENT_PAD - settingsScrollY;
        int gap = 8;

        // Enable scissor to prevent background bleeding from rounded corners
        context.enableScissor(x, y, x + w, y + h);

        rowY = drawActivateRow(context, rowX, rowY, rowW, mouseX, mouseY) + gap;

        rowY = drawBindRow(context, rowX, rowY, rowW, mouseX, mouseY) + gap;

        if (settingsTarget instanceof com.zenya.module.ActivatableModule) {
            rowY = drawActivationBindRow(context, rowX, rowY, rowW, mouseX, mouseY) + gap;
        }

        for (Setting<?> s : settingsTarget.getSettings()) {
            if (!s.isVisible()) continue;
            rowY = drawSettingRow(context, s, rowX, rowY, rowW, mouseX, mouseY) + gap;
        }

        context.disableScissor();

        int contentH = (rowY + settingsScrollY) - (y + CONTENT_PAD);
        int viewport = h - CONTENT_PAD * 2;
        if (contentH > viewport) {
            settingsScrollY = Math.max(0, Math.min(settingsScrollY, contentH - viewport));
        } else {
            settingsScrollY = 0;
        }
    }

    private int drawActivateRow(DrawContext context, int x, int y, int w, int mouseX, int mouseY) {
        int h = 44;
        boolean enabled = settingsTarget.isEnabled();
        boolean hover = isHover(mouseX, mouseY, x, y, w, h);
        int bg = enabled
                ? (hover ? blend(COLOR_ACCENT_BG, COLOR_ACCENT_STRONG, 0.35f) : COLOR_ACCENT_BG)
                : (hover ? COLOR_CARD_HOVER : COLOR_CARD_BG);
        RenderUtil.drawRoundedRect(context, x, y, w, h, CARD_RADIUS, bg, false);
        RenderUtil.drawOutline(context, x, y, w, h, CARD_RADIUS, 1.0f, enabled ? COLOR_ACCENT_STRONG : COLOR_ROW_BORDER, false);

        String label = (enabled ? "Click to deactivate " : "Click to activate ") + settingsTarget.getDisplayName();
        int labelW = ZenyaFont.width(this.textRenderer, label);
        int textColor = enabled ? 0xFFFFFFFF : COLOR_ACCENT;
        ZenyaFont.draw(context, this.textRenderer, label,
                x + (w - labelW) / 2, y + (h - this.textRenderer.fontHeight) / 2 + 1,
                textColor, false);

        if (hover) {
            hoverSetting = null;
            hoverSettingKind = SettingHitKind.TOGGLE;
            hoverSettingX = x; hoverSettingY = y;
            hoverSettingW = w; hoverSettingH = h;
        }
        return y + h;
    }

    private int drawActivationBindRow(DrawContext context, int x, int y, int w, int mouseX, int mouseY) {
        int h = 36;
        com.zenya.module.ActivatableModule am = (com.zenya.module.ActivatableModule) settingsTarget;
        RenderUtil.drawRoundedRect(context, x, y, w, h, CARD_RADIUS, COLOR_CARD_BG, false);
        RenderUtil.drawOutline(context, x, y, w, h, CARD_RADIUS, 1.0f, COLOR_ROW_BORDER, false);
        ZenyaFont.draw(context, this.textRenderer, "Activation Key", x + 14, y + 8, COLOR_TEXT, false);
        ZenyaFont.draw(context, this.textRenderer, "Hold or toggle this module while a key is held",
                x + 14, y + 8 + this.textRenderer.fontHeight + 2, COLOR_TEXT_MUTED, false);

        String label = listeningActivationBind ? "..."
                : (am.getActivationKey() == 0 ? "None" : ClickGUI.getKeyDisplayNameStatic(am.getActivationKey()));
        int chipW = Math.max(48, ZenyaFont.width(this.textRenderer, label) + 18);
        int chipH = 22;
        int chipX = x + w - 14 - chipW;
        int chipY = y + (h - chipH) / 2;
        boolean hover = isHover(mouseX, mouseY, chipX, chipY, chipW, chipH);
        float listenPulse = listeningActivationBind ? 0.5f + 0.5f * (float) Math.sin(openedFor() * 6.0) : 0f;
        int restingBg = hover ? COLOR_CARD_HOVER : COLOR_SEARCH_BG;
        int activeBg = blend(COLOR_ACCENT_BG, COLOR_ACCENT, listenPulse * 0.3f);
        int bg = listeningActivationBind ? activeBg : restingBg;
        RenderUtil.drawRoundedRect(context, chipX, chipY, chipW, chipH, chipH / 2.0f, bg, false);
        ZenyaFont.draw(context, this.textRenderer, label,
                chipX + (chipW - ZenyaFont.width(this.textRenderer, label)) / 2,
                chipY + (chipH - this.textRenderer.fontHeight) / 2 + 1,
                listeningActivationBind ? COLOR_ACCENT : COLOR_TEXT, false);

        if (hover) {
            hoverSettingKind = SettingHitKind.ACTIVATION_BIND;
            hoverSetting = null;
            hoverSettingX = chipX; hoverSettingY = chipY;
            hoverSettingW = chipW; hoverSettingH = chipH;
        }
        return y + h;
    }

    private int drawBindRow(DrawContext context, int x, int y, int w, int mouseX, int mouseY) {
        int h = 36;
        RenderUtil.drawRoundedRect(context, x, y, w, h, CARD_RADIUS, COLOR_CARD_BG, false);
        RenderUtil.drawOutline(context, x, y, w, h, CARD_RADIUS, 1.0f, COLOR_ROW_BORDER, false);
        ZenyaFont.draw(context, this.textRenderer, "Keybind", x + 14, y + 8, COLOR_TEXT, false);
        ZenyaFont.draw(context, this.textRenderer, "Trigger this module from a key press",
                x + 14, y + 8 + this.textRenderer.fontHeight + 2, COLOR_TEXT_MUTED, false);

        String label = listeningBind ? "..."
                : (settingsTarget.getBind() == 0 ? "None" : ClickGUI.getKeyDisplayNameStatic(settingsTarget.getBind()));
        int chipW = Math.max(48, ZenyaFont.width(this.textRenderer, label) + 18);
        int chipH = 22;
        int chipX = x + w - 14 - chipW;
        int chipY = y + (h - chipH) / 2;
        boolean hover = isHover(mouseX, mouseY, chipX, chipY, chipW, chipH);
        float listenPulse = listeningBind
                ? 0.5f + 0.5f * (float) Math.sin(openedFor() * 6.0)
                : 0f;
        int restingBg = hover ? COLOR_CARD_HOVER : COLOR_SEARCH_BG;
        int activeBg = blend(COLOR_ACCENT_BG, COLOR_ACCENT, listenPulse * 0.3f);
        int bg = listeningBind ? activeBg : restingBg;
        RenderUtil.drawRoundedRect(context, chipX, chipY, chipW, chipH, chipH / 2.0f, bg, false);
        ZenyaFont.draw(context, this.textRenderer, label,
                chipX + (chipW - ZenyaFont.width(this.textRenderer, label)) / 2,
                chipY + (chipH - this.textRenderer.fontHeight) / 2 + 1,
                listeningBind ? COLOR_ACCENT : COLOR_TEXT, false);

        if (hover) {
            hoverSettingKind = SettingHitKind.BIND;
            hoverSetting = null;
            hoverSettingX = chipX; hoverSettingY = chipY;
            hoverSettingW = chipW; hoverSettingH = chipH;
        }

        return y + h;
    }

    private int drawSettingRow(DrawContext context, Setting<?> s, int x, int y, int w,
                               int mouseX, int mouseY) {
        if (s instanceof SectionSetting) {
            ZenyaFont.draw(context, this.textRenderer, s.getDisplayName().toUpperCase(Locale.ROOT),
                    x + 4, y + 4, COLOR_TEXT_DIM, false);
            return y + this.textRenderer.fontHeight + 8;
        }

        int h = 36;
        RenderUtil.drawRoundedRect(context, x, y, w, h, CARD_RADIUS, COLOR_CARD_BG, false);
        RenderUtil.drawOutline(context, x, y, w, h, CARD_RADIUS, 1.0f, COLOR_ROW_BORDER, false);
        ZenyaFont.draw(context, this.textRenderer, s.getDisplayName(), x + 14, y + 12, COLOR_TEXT, false);

        if (s instanceof ActionSetting action) {
            String label = (String) action.getValue();
            int btnW = Math.max(56, ZenyaFont.width(this.textRenderer, label) + 18);
            int btnH = 22;
            int btnX = x + w - 14 - btnW;
            int btnY = y + (h - btnH) / 2;
            boolean hov = isHover(mouseX, mouseY, btnX, btnY, btnW, btnH);
            RenderUtil.drawRoundedRect(context, btnX, btnY, btnW, btnH, btnH / 2.0f,
                    hov ? COLOR_ACCENT_BG : COLOR_ACCENT_BG_SOFT, false);
            ZenyaFont.draw(context, this.textRenderer, label,
                    btnX + (btnW - ZenyaFont.width(this.textRenderer, label)) / 2,
                    btnY + (btnH - this.textRenderer.fontHeight) / 2 + 1, COLOR_ACCENT, false);
            if (hov) {
                hoverSetting = s; hoverSettingKind = SettingHitKind.ACTION;
                hoverSettingX = btnX; hoverSettingY = btnY;
                hoverSettingW = btnW; hoverSettingH = btnH;
            }
            return y + h;
        }

        if (s instanceof ModeSetting mode) {
            String value = mode.getValue();
            int chipW = Math.max(64, ZenyaFont.width(this.textRenderer, value) + 22);
            int chipH = 22;
            int chipX = x + w - 14 - chipW;
            int chipY = y + (h - chipH) / 2;
            boolean hov = isHover(mouseX, mouseY, chipX, chipY, chipW, chipH);
            RenderUtil.drawRoundedRect(context, chipX, chipY, chipW, chipH, chipH / 2.0f,
                    hov ? COLOR_CARD_HOVER : COLOR_SEARCH_BG, false);
            ZenyaFont.draw(context, this.textRenderer, value,
                    chipX + (chipW - ZenyaFont.width(this.textRenderer, value)) / 2,
                    chipY + (chipH - this.textRenderer.fontHeight) / 2 + 1, COLOR_ACCENT, false);
            if (hov) {
                hoverSetting = s; hoverSettingKind = SettingHitKind.MODE;
                hoverSettingX = chipX; hoverSettingY = chipY;
                hoverSettingW = chipW; hoverSettingH = chipH;
            }
            return y + h;
        }

        Object val = s.getValue();

        if (val instanceof Boolean b || s instanceof ConfirmBooleanSetting) {
            boolean checked = val instanceof Boolean bv ? bv : Boolean.TRUE.equals(val);
            int pillW = 36, pillH = 18;
            int pillX = x + w - 14 - pillW;
            int pillY = y + (h - pillH) / 2;
            boolean hov = isHover(mouseX, mouseY, pillX, pillY, pillW, pillH);
            int offBg = hov ? 0xFF5A6880 : 0xFF4A5878;
            int bg = checked ? COLOR_ACCENT_BG : offBg;
            RenderUtil.drawRoundedRect(context, pillX, pillY, pillW, pillH, pillH / 2.0f, bg, false);
            float knobT = animate(s, 2, checked ? 1f : 0f, 18f);
            int borderCol = blend(COLOR_ROW_BORDER, COLOR_ACCENT_STRONG, knobT);
            RenderUtil.drawOutline(context, pillX, pillY, pillW, pillH, pillH / 2.0f, 1.0f, borderCol, false);
            int knobSize = pillH - 4;
            int knobMin = pillX + 2;
            int knobMax = pillX + pillW - knobSize - 2;
            int knobX = (int) (knobMin + (knobMax - knobMin) * knobT);
            int knobY = pillY + 2;
            int knobColor = blend(COLOR_KNOB_OFF, COLOR_ACCENT, knobT);
            drawDot(context, knobX, knobY, knobSize, knobColor);
            if (hov) {
                hoverSetting = s; hoverSettingKind = SettingHitKind.TOGGLE;
                hoverSettingX = pillX; hoverSettingY = pillY;
                hoverSettingW = pillW; hoverSettingH = pillH;
            }
            return y + h;
        }

        if (val instanceof Number num && s.getMin() instanceof Number && s.getMax() instanceof Number) {
            double min = ((Number) s.getMin()).doubleValue();
            double max = ((Number) s.getMax()).doubleValue();
            double cur = num.doubleValue();
            double t = max > min ? (cur - min) / (max - min) : 0;
            t = Math.max(0, Math.min(1, t));

            int trackH = 4;
            int trackW = 140;
            int trackX = x + w - 14 - trackW - 56;
            int trackY = y + (h - trackH) / 2;
            String vlabel = formatNumber(cur, num);
            ZenyaFont.draw(context, this.textRenderer, vlabel,
                    x + w - 14 - ZenyaFont.width(this.textRenderer, vlabel),
                    y + (h - this.textRenderer.fontHeight) / 2 + 1, COLOR_TEXT, false);
            RenderUtil.drawRoundedRect(context, trackX, trackY, trackW, trackH, trackH / 2.0f, 0xFF4A5878, false);
            int filled = (int) (trackW * t);
            RenderUtil.drawRoundedRect(context, trackX, trackY, filled, trackH, trackH / 2.0f, COLOR_ACCENT, false);
            int knobR = 6;
            int knobX = trackX + filled - knobR;
            int knobY = trackY + trackH / 2 - knobR;
            drawDot(context, knobX, knobY, knobR * 2, COLOR_ACCENT);

            boolean hov = isHover(mouseX, mouseY, trackX - 8, trackY - 12, trackW + 16, trackH + 24);
            if (hov) {
                hoverSetting = s; hoverSettingKind = SettingHitKind.SLIDER;
                hoverSettingX = trackX; hoverSettingY = trackY;
                hoverSettingW = trackW; hoverSettingH = trackH;
            }
            return y + h;
        }

        if (val instanceof java.awt.Color color) {
            String hex = String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
            int swatchSize = 18;
            int swatchX = x + w - 14 - swatchSize;
            int swatchY = y + (h - swatchSize) / 2;
            int hexW = ZenyaFont.width(this.textRenderer, hex);
            int hexX = swatchX - 8 - hexW;
            ZenyaFont.draw(context, this.textRenderer, hex, hexX,
                    y + (h - this.textRenderer.fontHeight) / 2 + 1, COLOR_TEXT, false);
            RenderUtil.drawRoundedRect(context, swatchX - 1, swatchY - 1, swatchSize + 2, swatchSize + 2,
                    5.0f, 0xFF454A5C, false);
            int swatchColor = 0xFF000000 | (color.getRGB() & 0xFFFFFF);
            RenderUtil.drawRoundedRect(context, swatchX, swatchY, swatchSize, swatchSize, 4.0f, swatchColor, false);

            boolean rowHover = isHover(mouseX, mouseY, x, y, w, h);
            if (rowHover) {
                @SuppressWarnings("unchecked")
                Setting<java.awt.Color> cs = (Setting<java.awt.Color>) s;
                hoverSetting = cs;
                hoverSettingKind = SettingHitKind.COLOR_TOGGLE;
                hoverSettingX = x; hoverSettingY = y;
                hoverSettingW = w; hoverSettingH = h;
            }

            if (expandedColorSetting == s) {
                int extraY = y + h + 6;
                int extraH = drawColorPicker(context, x, extraY, w, mouseX, mouseY);
                return y + h + 6 + extraH;
            }
            return y + h;
        }

        if (val instanceof String textValue) {
            @SuppressWarnings("unchecked")
            Setting<String> stringSetting = (Setting<String>) s;
            boolean focused = focusedStringSetting == stringSetting;
            String shown = focused && (System.currentTimeMillis() / 500L) % 2L == 0L
                    ? textValue + "_"
                    : textValue;
            if (shown == null || shown.isEmpty()) {
                shown = focused ? "_" : "Click to edit";
            }

            int fieldW = Math.max(124, Math.min(210, w / 2));
            int fieldH = 22;
            int fieldX = x + w - 14 - fieldW;
            int fieldY = y + (h - fieldH) / 2;
            boolean hov = isHover(mouseX, mouseY, fieldX, fieldY, fieldW, fieldH);
            RenderUtil.drawRoundedRect(context, fieldX, fieldY, fieldW, fieldH, fieldH / 2.0f,
                    focused ? COLOR_CARD_HOVER : COLOR_SEARCH_BG, false);
            RenderUtil.drawOutline(context, fieldX, fieldY, fieldW, fieldH, fieldH / 2.0f, 1.0f,
                    focused ? COLOR_ACCENT_STRONG : COLOR_ROW_BORDER, false);

            String clipped = trimWithEllipsis(shown, fieldW - 18);
            ZenyaFont.draw(context, this.textRenderer, clipped,
                    fieldX + 9, fieldY + (fieldH - this.textRenderer.fontHeight) / 2 + 1,
                    textValue.isEmpty() && !focused ? COLOR_TEXT_DIM : COLOR_TEXT, false);

            if (hov) {
                hoverSetting = stringSetting;
                hoverSettingKind = SettingHitKind.STRING;
                hoverSettingX = fieldX; hoverSettingY = fieldY;
                hoverSettingW = fieldW; hoverSettingH = fieldH;
            }
            return y + h;
        }

        String fallback = String.valueOf(val);
        if (fallback.length() > 32) fallback = fallback.substring(0, 29) + "...";
        ZenyaFont.draw(context, this.textRenderer, fallback,
                x + w - 14 - ZenyaFont.width(this.textRenderer, fallback),
                y + (h - this.textRenderer.fontHeight) / 2 + 1, COLOR_TEXT_DIM, false);
        return y + h;
    }

    private int drawColorPicker(DrawContext context, int x, int y, int w, int mouseX, int mouseY) {
        int padding = 10;
        int svH = 100;
        int barH = 12;
        int gap = 8;
        int prevRowH = 20;

        int innerX = x + padding;
        int innerY = y + padding;
        int innerW = w - padding * 2;

        int totalH = padding + svH + gap + barH + gap + barH + gap + prevRowH + padding;

        // Container background
        RenderUtil.drawRoundedRect(context, x, y, w, totalH, CARD_RADIUS, COLOR_CARD_BG, false);

        // ── SV field ──────────────────────────────────────────────────────
        picSvX = innerX; picSvY = innerY; picSvW = innerW; picSvH = svH;
        for (int xi = 0; xi < innerW; xi++) {
            float sat = xi / (float) innerW;
            int top = 0xFF000000 | java.awt.Color.HSBtoRGB(pickerHSV[0], sat, 1.0f);
            int bottom = 0xFF000000;
            context.fillGradient(picSvX + xi, picSvY, picSvX + xi + 1, picSvY + svH, top, bottom);
        }

        // SV cursor: circle knob for clean look
        int crossX = picSvX + (int) (pickerHSV[1] * innerW);
        int crossY = picSvY + (int) ((1.0f - pickerHSV[2]) * svH);
        int knobR = 5;
        RenderUtil.drawRoundedRect(context, crossX - knobR - 1, crossY - knobR - 1,
                (knobR + 1) * 2, (knobR + 1) * 2, knobR + 1, 0xCC000000, false);
        RenderUtil.drawRoundedRect(context, crossX - knobR, crossY - knobR,
                knobR * 2, knobR * 2, knobR, 0xFFFFFFFF, false);

        int curY = innerY + svH + gap;

        // ── Hue bar (horizontal) ──────────────────────────────────────────
        picHueX = innerX; picHueY = curY; picHueW = innerW; picHueH = barH;
        for (int i = 0; i < innerW; i++) {
            int rgb = 0xFF000000 | java.awt.Color.HSBtoRGB(i / (float) innerW, 1.0f, 1.0f);
            context.fill(picHueX + i, picHueY + 1, picHueX + i + 1, picHueY + barH - 1, rgb);
        }
        // Round caps
        int hcapR = (barH - 2) / 2;
        int hStartCol = java.awt.Color.HSBtoRGB(0f, 1f, 1f) | 0xFF000000;
        int hEndCol = java.awt.Color.HSBtoRGB(1f, 1f, 1f) | 0xFF000000;
        RenderUtil.drawRoundedRect(context, picHueX, picHueY + 1, hcapR * 2, barH - 2, hcapR, hStartCol, false);
        RenderUtil.drawRoundedRect(context, picHueX + innerW - hcapR * 2, picHueY + 1, hcapR * 2, barH - 2, hcapR, hEndCol, false);

        // Hue knob handle
        int hkx = picHueX + (int) (pickerHSV[0] * innerW);
        hkx = Math.max(picHueX + hcapR, Math.min(picHueX + innerW - hcapR, hkx));
        int hKnobR = barH / 2 + 1;
        RenderUtil.drawRoundedRect(context, hkx - hKnobR, picHueY + barH / 2 - hKnobR,
                hKnobR * 2, hKnobR * 2, hKnobR, 0xFFFFFFFF, false);
        int hueDotCol = java.awt.Color.HSBtoRGB(pickerHSV[0], 1f, 1f) | 0xFF000000;
        RenderUtil.drawRoundedRect(context, hkx - hKnobR + 2, picHueY + barH / 2 - hKnobR + 2,
                hKnobR * 2 - 4, hKnobR * 2 - 4, hKnobR - 2, hueDotCol, false);

        curY += barH + gap;

        // ── Alpha bar ─────────────────────────────────────────────────────
        picAlphaX = innerX; picAlphaY = curY; picAlphaW = innerW; picAlphaH = barH;
        // Checkerboard background
        int sq = 4;
        for (int xi = 0; xi < picAlphaW; xi += sq) {
            for (int yi = 0; yi < barH - 2; yi += sq) {
                boolean dark = ((xi / sq) + (yi / sq)) % 2 == 0;
                int c = dark ? 0xFF3A3A3A : 0xFF555555;
                int x2 = Math.min(picAlphaX + xi + sq, picAlphaX + picAlphaW);
                int y2 = Math.min(picAlphaY + 1 + yi + sq, picAlphaY + barH - 1);
                context.fill(picAlphaX + xi, picAlphaY + 1 + yi, x2, y2, c);
            }
        }
        // Alpha gradient overlay
        int currentRgb = java.awt.Color.HSBtoRGB(pickerHSV[0], pickerHSV[1], pickerHSV[2]) & 0xFFFFFF;
        for (int xi = 0; xi < picAlphaW; xi++) {
            float t = xi / (float) picAlphaW;
            int a = (int) (t * 255);
            int col = (a << 24) | currentRgb;
            context.fill(picAlphaX + xi, picAlphaY + 1, picAlphaX + xi + 1, picAlphaY + barH - 1, col);
        }
        // Alpha knob handle
        int akx = picAlphaX + (int) ((pickerAlpha / 255.0f) * picAlphaW);
        akx = Math.max(picAlphaX + hcapR, Math.min(picAlphaX + picAlphaW - hcapR, akx));
        int aKnobR = barH / 2 + 1;
        RenderUtil.drawRoundedRect(context, akx - aKnobR, picAlphaY + barH / 2 - aKnobR,
                aKnobR * 2, aKnobR * 2, aKnobR, 0xFFFFFFFF, false);
        int alphaDot = (pickerAlpha << 24) | currentRgb;
        RenderUtil.drawRoundedRect(context, akx - aKnobR + 2, picAlphaY + barH / 2 - aKnobR + 2,
                aKnobR * 2 - 4, aKnobR * 2 - 4, aKnobR - 2, alphaDot, false);

        curY += barH + gap;

        // ── Preview swatch + hex ──────────────────────────────────────────
        int swatchSz = prevRowH - 4;
        int swatchCol = (pickerAlpha << 24) | currentRgb;
        RenderUtil.drawRoundedRect(context, innerX, curY + 2, swatchSz, swatchSz, 3f, 0xFF2A2A2A, false);
        RenderUtil.drawRoundedRect(context, innerX + 1, curY + 3, swatchSz - 2, swatchSz - 2, 2f, swatchCol, false);

        String hex = String.format("#%02X%02X%02X", (currentRgb >> 16) & 0xFF, (currentRgb >> 8) & 0xFF, currentRgb & 0xFF);
        if (pickerAlpha < 255) hex = String.format("#%02X%s", pickerAlpha, hex.substring(1));
        ZenyaFont.draw(context, this.textRenderer, hex,
                innerX + swatchSz + 8, curY + (prevRowH - this.textRenderer.fontHeight) / 2 + 1,
                COLOR_TEXT, false);

        String alphaPercent = (int)(pickerAlpha / 255f * 100f) + "%";
        int apW = ZenyaFont.width(this.textRenderer, alphaPercent);
        ZenyaFont.draw(context, this.textRenderer, alphaPercent,
                innerX + innerW - apW, curY + (prevRowH - this.textRenderer.fontHeight) / 2 + 1,
                COLOR_TEXT_MUTED, false);

        return totalH;
    }

    private void applyColorFromHsv() {
        if (expandedColorSetting == null) return;
        int rgb = java.awt.Color.HSBtoRGB(pickerHSV[0], pickerHSV[1], pickerHSV[2]);
        java.awt.Color out = new java.awt.Color((pickerAlpha << 24) | (rgb & 0xFFFFFF), true);
        expandedColorSetting.setValue(out);
    }

    private void openColorPicker(Setting<java.awt.Color> s) {
        expandedColorSetting = s;
        colorPickerOpenedAtNanos = System.nanoTime();
        java.awt.Color current = s.getValue();
        java.awt.Color.RGBtoHSB(current.getRed(), current.getGreen(), current.getBlue(), pickerHSV);
        pickerAlpha = current.getAlpha();
    }

    private void closeColorPicker() {
        expandedColorSetting = null;
        colorDragMode = ColorDragMode.NONE;
    }

    private String formatNumber(double v, Number original) {
        if (original instanceof Integer || original instanceof Long) return String.valueOf((long) Math.round(v));
        return String.format(Locale.ROOT, Math.abs(v) >= 100 ? "%.0f" : "%.2f", v);
    }

    private String limitStringInput(String value, Setting<String> setting) {
        String sanitized = sanitizeStringInput(value);
        if ("Player Name".equalsIgnoreCase(setting.getName())) {
            StringBuilder username = new StringBuilder(sanitized.length());
            for (int i = 0; i < sanitized.length(); i++) {
                char c = sanitized.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '_') {
                    username.append(c);
                }
            }
            sanitized = username.toString();
        }
        int max = "Player Name".equalsIgnoreCase(setting.getName())
                ? 16
                : "Webhook".equalsIgnoreCase(setting.getName()) ? 512 : 64;
        return sanitized.length() <= max ? sanitized : sanitized.substring(0, max);
    }

    private String sanitizeStringInput(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 32 && c < 127) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private boolean isControlDown() {
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private String trimWithEllipsis(String text, int maxWidth) {
        if (text == null || text.isEmpty() || ZenyaFont.width(this.textRenderer, text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String ellipsis = "...";
        int ellipsisW = ZenyaFont.width(this.textRenderer, ellipsis);
        String trimmed = text;
        while (!trimmed.isEmpty() && ZenyaFont.width(this.textRenderer, trimmed) + ellipsisW > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applySliderAt(int mouseX) {
        if (!(draggingSlider instanceof Setting s) || hoverSettingW <= 0) return;
        double t = (mouseX - hoverSettingX) / (double) hoverSettingW;
        t = Math.max(0, Math.min(1, t));
        double min = ((Number) s.getMin()).doubleValue();
        double max = ((Number) s.getMax()).doubleValue();
        double v = min + t * (max - min);
        Object cur = s.getValue();
        if (cur instanceof Integer)      s.setValue((int) Math.round(v));
        else if (cur instanceof Long)    s.setValue((long) Math.round(v));
        else if (cur instanceof Float)   s.setValue((float) v);
        else if (cur instanceof Double)  s.setValue(v);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void clickSetting() {
        switch (hoverSettingKind) {
            case TOGGLE -> {
                if (hoverSetting == null) {
                    settingsTarget.toggle();
                    return;
                }
                Setting s = hoverSetting;
                Object v = s.getValue();
                if (v instanceof Boolean b) s.setValue(!b);
                else if (s instanceof ConfirmBooleanSetting) {
                    Boolean current = (Boolean) s.getValue();
                    s.setValue(!Boolean.TRUE.equals(current));
                }
            }
            case MODE -> {
                if (hoverSetting instanceof ModeSetting mode) mode.cycleNext();
            }
            case ACTION -> {
                if (hoverSetting instanceof ActionSetting a) a.trigger();
            }
            case BIND -> { listeningBind = true; listeningActivationBind = false; }
            case ACTIVATION_BIND -> { listeningActivationBind = true; listeningBind = false; }
            case SLIDER -> {
                draggingSlider = hoverSetting;
            }
            case COLOR_TOGGLE -> {
                @SuppressWarnings("unchecked")
                Setting<java.awt.Color> cs = (Setting<java.awt.Color>) hoverSetting;
                if (expandedColorSetting == cs) closeColorPicker();
                else openColorPicker(cs);
            }
            case STRING -> {
                @SuppressWarnings("unchecked")
                Setting<String> ss = (Setting<String>) hoverSetting;
                focusedStringSetting = ss;
                listeningBind = false;
                listeningActivationBind = false;
            }
            case NONE -> {}
        }
    }

    private boolean handlePickerMouseDown(int mx, int my) {
        if (expandedColorSetting == null) return false;
        if (mx >= picSvX && mx < picSvX + picSvW && my >= picSvY && my < picSvY + picSvH) {
            colorDragMode = ColorDragMode.SV;
            applyPickerDrag(mx, my);
            return true;
        }
        if (mx >= picHueX && mx < picHueX + picHueW && my >= picHueY && my < picHueY + picHueH) {
            colorDragMode = ColorDragMode.HUE;
            applyPickerDrag(mx, my);
            return true;
        }
        if (mx >= picAlphaX && mx < picAlphaX + picAlphaW && my >= picAlphaY && my < picAlphaY + picAlphaH) {
            colorDragMode = ColorDragMode.ALPHA;
            applyPickerDrag(mx, my);
            return true;
        }
        return false;
    }

    private void applyPickerDrag(int mx, int my) {
        if (expandedColorSetting == null) return;
        switch (colorDragMode) {
            case SV -> {
                pickerHSV[1] = clamp01((mx - picSvX) / (float) picSvW);
                pickerHSV[2] = clamp01(1.0f - (my - picSvY) / (float) picSvH);
                applyColorFromHsv();
            }
            case HUE -> {
                pickerHSV[0] = clamp01((my - picHueY) / (float) picHueH);
                applyColorFromHsv();
            }
            case ALPHA -> {
                pickerAlpha = (int) (clamp01((mx - picAlphaX) / (float) picAlphaW) * 255);
                applyColorFromHsv();
            }
            case NONE -> {}
        }
    }

    private static float clamp01(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    private void renderConfigsHeader(DrawContext context, int x, int y, int mouseX, int mouseY) {
        int w = PANEL_W - SIDEBAR_W;
        context.fill(x + 1, y + HEADER_H, x + w - 1, y + HEADER_H + 1, COLOR_DIVIDER);

        int backW = 28, backH = 28;
        int backX = x + CONTENT_PAD;
        int backY = y + (HEADER_H - backH) / 2;
        boolean hb = isHover(mouseX, mouseY, backX, backY, backW, backH);
        hoverBackButton = hb;
        RenderUtil.drawRoundedRect(context, backX, backY, backW, backH, 6.0f,
                hb ? COLOR_CARD_HOVER : COLOR_CARD_BG, false);
        ZenyaFont.draw(context, this.textRenderer, "<", backX + backW / 2 - 2,
                backY + backH / 2 - this.textRenderer.fontHeight / 2 + 1, COLOR_TEXT, false);

        int titleX = backX + backW + 12;
        ZenyaFont.draw(context, this.textRenderer, "Cloud Configs", titleX,
                y + HEADER_H / 2 - this.textRenderer.fontHeight - 1, COLOR_TEXT, false);
        ZenyaFont.draw(context, this.textRenderer, "Save / load / share your settings",
                titleX, y + HEADER_H / 2 + 2, COLOR_TEXT_MUTED, false);
    }

    private void renderConfigsPanel(DrawContext context, int x, int y, int mouseX, int mouseY) {
        int w = PANEL_W - SIDEBAR_W;
        int h = PANEL_H - HEADER_H;

        configsButtonRects.clear();
        configsButtonActions.clear();

        int padding = CONTENT_PAD;
        int rowX = x + padding;
        int rowW = w - padding * 2;
        int rowY = y + padding - configsListScroll;

        context.enableScissor(x, y, x + w, y + h);

        int saveCardH = 64;
        RenderUtil.drawRoundedRect(context, rowX, rowY, rowW, saveCardH, CARD_RADIUS, COLOR_CARD_BG, false);
        ZenyaFont.draw(context, this.textRenderer, "Save current config",
                rowX + 14, rowY + 10, COLOR_TEXT, false);

        int inputW = rowW - 28 - 86;
        int inputH = 22;
        int inputX = rowX + 14;
        int inputY = rowY + saveCardH - inputH - 12;
        drawInput(context, inputX, inputY, inputW, inputH,
                configNameBuffer, configNameFocused,
                "Name (e.g. pvp, donut)",
                mouseX, mouseY, () -> { configNameFocused = true; configShareFocused = false; });

        int btnW = 80, btnH = 22;
        int btnX = rowX + rowW - 14 - btnW;
        int btnY = inputY;
        boolean canSave = !configNameBuffer.trim().isEmpty();
        drawConfigsButton(context, btnX, btnY, btnW, btnH, "Save",
                canSave ? COLOR_ACCENT_BG : COLOR_CARD_HOVER,
                canSave ? COLOR_ACCENT : COLOR_TEXT_DIM,
                mouseX, mouseY, () -> {
                    if (!canSave) return;
                    String name = configNameBuffer.trim();
                    if (com.zenya.utils.ConfigStore.saveAs(name)) {
                        showConfigsToast("Saved as " + com.zenya.utils.ConfigStore.sanitize(name));
                        configNameBuffer = "";
                    } else {
                        showConfigsToast("Save failed");
                    }
                });
        rowY += saveCardH + 10;

        java.util.List<String> configs = com.zenya.utils.ConfigStore.list();
        if (configs.isEmpty()) {
            RenderUtil.drawRoundedRect(context, rowX, rowY, rowW, 40, CARD_RADIUS, COLOR_CARD_BG, false);
            ZenyaFont.draw(context, this.textRenderer, "No saved configs yet.",
                    rowX + 14, rowY + 14, COLOR_TEXT_MUTED, false);
            rowY += 50;
        } else {
            for (String name : configs) {
                rowY = drawConfigRow(context, name, rowX, rowY, rowW, mouseX, mouseY) + 6;
            }
            rowY += 4;
        }

        int shareCardH = 64;
        RenderUtil.drawRoundedRect(context, rowX, rowY, rowW, shareCardH, CARD_RADIUS, COLOR_CARD_BG, false);
        ZenyaFont.draw(context, this.textRenderer, "Share code",
                rowX + 14, rowY + 10, COLOR_TEXT, false);

        int shInputX = rowX + 14;
        int shInputY = rowY + shareCardH - inputH - 12;
        int shInputW = rowW - 28 - 86 - 86 - 8;
        drawInput(context, shInputX, shInputY, shInputW, inputH,
                configShareBuffer, configShareFocused,
                "Paste a code...",
                mouseX, mouseY, () -> { configShareFocused = true; configNameFocused = false; });

        int genBtnX = shInputX + shInputW + 8;
        drawConfigsButton(context, genBtnX, shInputY, 80, inputH, "Generate",
                COLOR_CARD_HOVER, COLOR_TEXT, mouseX, mouseY, () -> {
                    String code = com.zenya.utils.ConfigStore.generateShareCode();
                    if (code != null) {
                        com.zenya.utils.ConfigStore.writeClipboard(code);
                        configShareBuffer = code;
                        showConfigsToast("Copied " + code + " to clipboard");
                    } else {
                        showConfigsToast("Generate failed");
                    }
                });
        int redeemBtnX = genBtnX + 88;
        drawConfigsButton(context, redeemBtnX, shInputY, 80, inputH, "Redeem",
                COLOR_ACCENT_BG, COLOR_ACCENT, mouseX, mouseY, () -> {
                    String code = configShareBuffer.trim();
                    if (code.isEmpty()) {
                        showConfigsToast("Enter a code first");
                        return;
                    }
                    if (com.zenya.utils.ConfigStore.redeemShareCode(code)) {
                        showConfigsToast("Redeemed");
                        configShareBuffer = "";
                    } else {
                        showConfigsToast("Invalid code");
                    }
                });
        rowY += shareCardH + 6;

        context.disableScissor();

        if (configsToast != null && System.currentTimeMillis() - configsToastShownAt < 2200) {
            int tw = ZenyaFont.width(this.textRenderer, configsToast) + 24;
            int tx = x + (w - tw) / 2;
            int ty = y + h - 32;
            RenderUtil.drawRoundedRect(context, tx, ty, tw, 22, 11.0f, COLOR_ACCENT_BG, false);
            ZenyaFont.draw(context, this.textRenderer, configsToast,
                    tx + 12, ty + (22 - this.textRenderer.fontHeight) / 2 + 1,
                    COLOR_ACCENT, false);
        }

        int contentH = (rowY + configsListScroll) - (y + padding);
        int viewport = h - padding * 2;
        if (contentH > viewport) {
            configsListScroll = Math.max(0, Math.min(configsListScroll, contentH - viewport));
        } else {
            configsListScroll = 0;
        }
    }

    private int drawConfigRow(DrawContext context, String name, int x, int y, int w, int mouseX, int mouseY) {
        int h = 36;
        RenderUtil.drawRoundedRect(context, x, y, w, h, CARD_RADIUS, COLOR_CARD_BG, false);
        ZenyaFont.draw(context, this.textRenderer, name, x + 14,
                y + (h - this.textRenderer.fontHeight) / 2 + 1, COLOR_TEXT, false);

        int btnW = 60, btnH = 22;
        int btnY = y + (h - btnH) / 2;
        int delX = x + w - 14 - btnW;
        int loadX = delX - btnW - 6;
        drawConfigsButton(context, loadX, btnY, btnW, btnH, "Load",
                COLOR_ACCENT_BG, COLOR_ACCENT, mouseX, mouseY, () -> {
                    if (com.zenya.utils.ConfigStore.load(name)) showConfigsToast("Loaded " + name);
                    else showConfigsToast("Load failed");
                });
        drawConfigsButton(context, delX, btnY, btnW, btnH, "Delete",
                0xFF5C2A2A, 0xFFFFB4B4, mouseX, mouseY, () -> {
                    if (com.zenya.utils.ConfigStore.delete(name)) showConfigsToast("Deleted " + name);
                    else showConfigsToast("Delete failed");
                });
        return y + h;
    }

    private void drawConfigsButton(DrawContext context, int x, int y, int w, int h, String label,
                                    int bgColor, int textColor, int mouseX, int mouseY, Runnable action) {
        boolean hover = isHover(mouseX, mouseY, x, y, w, h);
        int bg = hover ? blend(bgColor, COLOR_CARD_HOVER, 0.25f) : bgColor;
        RenderUtil.drawRoundedRect(context, x, y, w, h, h / 2.0f, bg, false);
        ZenyaFont.draw(context, this.textRenderer, label,
                x + (w - ZenyaFont.width(this.textRenderer, label)) / 2,
                y + (h - this.textRenderer.fontHeight) / 2 + 1, textColor, false);
        configsButtonRects.add(new int[] { x, y, w, h });
        configsButtonActions.add(action);
    }

    private void drawInput(DrawContext context, int x, int y, int w, int h, String text, boolean focused,
                            String placeholder, int mouseX, int mouseY, Runnable onClick) {
        boolean hover = isHover(mouseX, mouseY, x, y, w, h);
        RenderUtil.drawRoundedRect(context, x, y, w, h, h / 2.0f,
                focused ? COLOR_CARD_HOVER : (hover ? COLOR_CARD_HOVER : COLOR_SEARCH_BG), false);
        if (focused) {
            RenderUtil.drawRoundedRect(context, x - 1, y - 1, w + 2, h + 2, h / 2.0f + 1, COLOR_ACCENT, false);
            RenderUtil.drawRoundedRect(context, x, y, w, h, h / 2.0f, COLOR_CARD_HOVER, false);
        }
        String shown = text == null || text.isEmpty() ? placeholder : text;
        int color = (text == null || text.isEmpty()) ? COLOR_TEXT_DIM : COLOR_TEXT;
        ZenyaFont.draw(context, this.textRenderer, shown,
                x + 12, y + (h - this.textRenderer.fontHeight) / 2 + 1, color, false);
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int caretX = x + 12 + ZenyaFont.width(this.textRenderer, text == null ? "" : text) + 1;
            int caretY = y + 5;
            context.fill(caretX, caretY, caretX + 1, y + h - 5, COLOR_TEXT);
        }
        configsButtonRects.add(new int[] { x, y, w, h });
        configsButtonActions.add(onClick);
    }

    private void showConfigsToast(String msg) {
        configsToast = msg;
        configsToastShownAt = System.currentTimeMillis();
    }

    private static void drawDot(DrawContext context, int x, int y, int size, int color) {
        if (size <= 0) return;
        int cut = Math.max(1, size / 5);
        context.fill(x + cut, y, x + size - cut, y + size, color);
        context.fill(x, y + cut, x + cut, y + size - cut, color);
        context.fill(x + size - cut, y + cut, x + size, y + size - cut, color);
        if (size >= 8) {
            int half = cut / 2;
            if (half >= 1) {
                context.fill(x + half, y + half, x + cut, y + cut, color);
                context.fill(x + size - cut, y + half, x + size - half, y + cut, color);
                context.fill(x + half, y + size - cut, x + cut, y + size - half, color);
                context.fill(x + size - cut, y + size - cut, x + size - half, y + size - half, color);
            }
        }
    }

    @Override
    public void close() {
        playCustomSound(SOUND_GUI_CLOSE, 1.0f, 1.0f);
        super.close();
    }

    private static boolean isHover(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int blend(int a, int b, float t) {
        int aA = (a >>> 24) & 0xFF, aR = (a >>> 16) & 0xFF, aG = (a >>> 8) & 0xFF, aB = a & 0xFF;
        int bA = (b >>> 24) & 0xFF, bR = (b >>> 16) & 0xFF, bG = (b >>> 8) & 0xFF, bB = b & 0xFF;
        int rA = (int) (aA + (bA - aA) * t);
        int rR = (int) (aR + (bR - aR) * t);
        int rG = (int) (aG + (bG - aG) * t);
        int rB = (int) (aB + (bB - aB) * t);
        return (rA << 24) | (rR << 16) | (rG << 8) | rB;
    }
}
