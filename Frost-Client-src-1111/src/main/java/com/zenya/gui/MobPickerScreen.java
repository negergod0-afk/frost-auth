package com.zenya.gui;

import com.zenya.module.modules.render.MobESP;
import com.zenya.setting.MobsSetting;
import com.zenya.utils.ZenyaFont;
import com.zenya.utils.renderer.RenderUtil;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Mob picker rewritten to mirror BlockPickerScreen — icon grid with spawn-egg
 * cells, hover tooltip with the mob name, same tabs/search/footer/HSV picker.
 */
public class MobPickerScreen extends Screen {

    // ── Grid panel layout ─────────────────────────────────────────────────────
    private static final int PANEL_W   = 460;
    private static final int PANEL_H   = 400;
    private static final int HEADER_H  = 44;
    private static final int TAB_H     = 32;
    private static final int SEARCH_H  = 30;
    private static final int FOOTER_H  = 48;
    private static final int GRID_PAD  = 18;
    private static final int CELL_SIZE = 32;
    private static final int CELL_GAP  = 6;
    private static final int SCROLL_W  = 3;

    // ── HSV color picker popup layout ─────────────────────────────────────────
    private static final int PICK_PAD    = 10;
    private static final int PICK_SV_W   = 160;
    private static final int PICK_SV_H   = 110;
    private static final int PICK_HUE_W  = 12;
    private static final int PICK_ALPHA_H= 10;
    private static final int PICK_PREV_H = 14;
    private static final int PICK_GAP    = 8;
    private static final int PICK_TOTAL_W= PICK_PAD * 2 + PICK_SV_W + PICK_GAP + PICK_HUE_W;
    private static final int PICK_TOTAL_H= PICK_PAD * 2 + PICK_SV_H + PICK_GAP + PICK_ALPHA_H + PICK_GAP + PICK_PREV_H;

    // ── Colors (same palette as BlockPickerScreen) ────────────────────────────
    private static final int C_PANEL_BG        = 0xFF16181D;
    private static final int C_DIVIDER         = 0xFF24272F;
    private static final int C_TAB_ACTIVE      = 0xFFEF4444;
    private static final int C_TAB_HOVER       = 0x20FFFFFF;
    private static final int C_SEARCH_BG       = 0xFF1E2128;
    private static final int C_SEARCH_FOCUS    = 0xFFEF4444;
    private static final int C_CELL_HOVER      = 0xFF2A2D35;
    private static final int C_CELL_SELECTED   = 0xFF3A1A1F;
    private static final int C_CELL_SEL_BORDER = 0xFFEF4444;
    private static final int C_BTN_NEUTRAL_HV  = 0xFF24272F;
    private static final int C_BTN_SAVE        = 0xFFEF4444;
    private static final int C_BTN_SAVE_HV     = 0xFFDC2626;
    private static final int C_TEXT            = 0xFFFFFFFF;
    private static final int C_TEXT_MUTED      = 0xFF8A8F9A;
    private static final int C_SCROLL          = 0x00000000;
    private static final int C_SCROLL_THUMB    = 0xFF3A3D45;
    private static final int C_POPUP_BG        = 0xFF1E2128;
    private static final int C_POPUP_BORDER    = 0xFF2A2D35;
    private static final int C_KNOB            = 0xFFFFFFFF;
    private static final int C_CHECKER_A       = 0xFF6E7280;
    private static final int C_CHECKER_B       = 0xFFA3A8B5;
    private static final int C_TOOLTIP_BG      = 0xF20E1018;
    private static final int C_TOOLTIP_BORDER  = 0xFF2A2D35;

    // ── State ─────────────────────────────────────────────────────────────────
    private final MobsSetting setting;
    private final Screen parent;
    private final MobESP espModule;
    private final Set<EntityType<?>> tempSelected;
    private final Map<EntityType<?>, Color> tempColors;

    private boolean showSelected  = false;
    private String  searchQuery   = "";
    private boolean searchFocused = false;
    private int     scrollRow     = 0;

    // Color picker
    private EntityType<?> pickerMob = null;
    private int pickerRootX, pickerRootY;
    private float[] pickerHSV = new float[3];
    private int pickerAlpha = 255;
    private enum DragMode { NONE, SV, HUE, ALPHA }
    private DragMode dragMode = DragMode.NONE;
    private int pSvX, pSvY, pHueX, pHueY, pAlphaX, pAlphaY;

    // Layout cache (set during render)
    private int panelX, panelY;
    private int tabAllX, tabAllW, tabSelX, tabSelW, tabRowY;
    private int searchBarX, searchBarY, searchBarW;
    private int gridX, gridY, gridW, gridH, cols, visibleRows;
    private int btnY, btnH, btnClearX, btnCancelX, btnSaveX, btnBW;
    private List<EntityType<?>> displayedMobs = new ArrayList<>();
    private int hoverCell = -1;

    // ── Constructor ───────────────────────────────────────────────────────────

    public MobPickerScreen(Screen parent, MobsSetting setting, MobESP espModule) {
        super(Text.literal("Mob Picker"));
        this.parent = parent;
        this.setting = setting;
        this.espModule = espModule;
        this.tempSelected = new LinkedHashSet<>(setting.getSelectedMobs());
        this.tempColors = new HashMap<>(espModule != null ? espModule.getColorMap() : Map.of());
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext ctx, int mx, int my, float dt) {}
    @Override protected void applyBlur(DrawContext ctx) {}

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        panelX = (this.width  - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;

        drawRoundedRect(ctx, panelX, panelY, PANEL_W, PANEL_H, 8, C_PANEL_BG);

        // Header: left-aligned title, right-aligned counter
        int visibleSelectedCount = getVisibleSelectedCount();
        String countText = showSelected && !searchQuery.isBlank()
                ? visibleSelectedCount + "/" + tempSelected.size()
                : String.valueOf(tempSelected.size());
        String title = "Mob Picker";
        String counter = countText + (tempSelected.size() == 1 ? " mob" : " mobs");
        int titleY = panelY + (HEADER_H - this.textRenderer.fontHeight) / 2 + 1;
        ZenyaFont.draw(ctx, this.textRenderer, title, panelX + GRID_PAD, titleY, C_TEXT, false);
        int counterW = ZenyaFont.width(this.textRenderer, counter);
        ZenyaFont.draw(ctx, this.textRenderer, counter,
                panelX + PANEL_W - GRID_PAD - counterW, titleY, C_TEXT_MUTED, false);

        // Divider under header
        ctx.fill(panelX + GRID_PAD, panelY + HEADER_H,
                panelX + PANEL_W - GRID_PAD, panelY + HEADER_H + 1, C_DIVIDER);

        int cursor = panelY + HEADER_H + 8;

        // Tabs
        tabRowY = cursor;
        String allLabel = "All Mobs";
        String selLabel = "Selected (" + countText + ")";
        tabAllW = ZenyaFont.width(this.textRenderer, allLabel) + 16;
        tabSelW = ZenyaFont.width(this.textRenderer, selLabel) + 16;
        tabAllX = panelX + GRID_PAD;
        tabSelX = tabAllX + tabAllW + 8;

        boolean hAll = hit(mx, my, tabAllX, tabRowY, tabAllW, TAB_H);
        boolean hSel = hit(mx, my, tabSelX, tabRowY, tabSelW, TAB_H);
        if (hAll && showSelected)  ctx.fill(tabAllX, tabRowY, tabAllX + tabAllW, tabRowY + TAB_H, C_TAB_HOVER);
        if (hSel && !showSelected) ctx.fill(tabSelX, tabRowY, tabSelX + tabSelW, tabRowY + TAB_H, C_TAB_HOVER);
        drawCenter(ctx, allLabel, tabAllX, tabRowY, tabAllW, TAB_H, !showSelected ? C_TEXT : C_TEXT_MUTED);
        drawCenter(ctx, selLabel, tabSelX, tabRowY, tabSelW, TAB_H,  showSelected ? C_TEXT : C_TEXT_MUTED);
        int underlineY = tabRowY + TAB_H - 2;
        if (!showSelected) ctx.fill(tabAllX + 4, underlineY, tabAllX + tabAllW - 4, underlineY + 2, C_TAB_ACTIVE);
        else               ctx.fill(tabSelX + 4, underlineY, tabSelX + tabSelW - 4, underlineY + 2, C_TAB_ACTIVE);
        cursor += TAB_H + 4;

        // Search bar
        searchBarX = panelX + GRID_PAD;
        searchBarY = cursor;
        searchBarW = PANEL_W - GRID_PAD * 2;
        drawRoundedRect(ctx, searchBarX, searchBarY, searchBarW, SEARCH_H, 4, C_SEARCH_BG);
        if (searchFocused) {
            ctx.fill(searchBarX, searchBarY + SEARCH_H - 2,
                    searchBarX + searchBarW, searchBarY + SEARCH_H, C_SEARCH_FOCUS);
        }
        String searchDisplay = searchQuery.isEmpty() ? "Search mobs..." : searchQuery;
        if (searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) searchDisplay += "_";
        ZenyaFont.draw(ctx, this.textRenderer, searchDisplay,
                searchBarX + 10, searchBarY + (SEARCH_H - this.textRenderer.fontHeight) / 2 + 1,
                searchQuery.isEmpty() && !searchFocused ? C_TEXT_MUTED : C_TEXT, false);
        cursor += SEARCH_H + 8;

        // Grid
        gridX = panelX + GRID_PAD;
        gridY = cursor;
        int footerTop = panelY + PANEL_H - FOOTER_H;
        gridW = PANEL_W - GRID_PAD * 2 - SCROLL_W - 6;
        gridH = footerTop - gridY - 8;
        cols = Math.max(1, (gridW + CELL_GAP) / (CELL_SIZE + CELL_GAP));
        visibleRows = Math.max(1, gridH / (CELL_SIZE + CELL_GAP));

        List<EntityType<?>> filtered;
        if (showSelected) {
            filtered = new ArrayList<>(tempSelected);
            if (!searchQuery.isEmpty()) {
                filtered.removeIf(m -> !matchesSearch(m, searchQuery));
            }
        } else {
            filtered = setting.filter(searchQuery);
        }
        displayedMobs = filtered;
        int totalRows = (displayedMobs.size() + cols - 1) / Math.max(1, cols);
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, totalRows - visibleRows)));

        hoverCell = -1;
        RenderUtil.setScissor(gridX, gridY, gridW + SCROLL_W + 3, gridH, false);

        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = (scrollRow + row) * cols + col;
                if (idx >= displayedMobs.size()) break;
                EntityType<?> mob = displayedMobs.get(idx);
                int cx = gridX + col * (CELL_SIZE + CELL_GAP);
                int cy = gridY + row * (CELL_SIZE + CELL_GAP);

                boolean selected = tempSelected.contains(mob);
                boolean hovered  = hit(mx, my, cx, cy, CELL_SIZE, CELL_SIZE);
                if (hovered) hoverCell = idx;

                if (selected) {
                    Color custom = tempColors.get(mob);
                    int borderArgb, bgArgb;
                    if (custom != null) {
                        int rgb = custom.getRGB() & 0xFFFFFF;
                        borderArgb = 0xFF000000 | rgb;
                        int r = (rgb >> 16 & 0xFF) / 4;
                        int g = (rgb >> 8  & 0xFF) / 4;
                        int b = (rgb       & 0xFF) / 4;
                        bgArgb = 0xFF000000 | (r << 16) | (g << 8) | b;
                    } else {
                        borderArgb = C_CELL_SEL_BORDER;
                        bgArgb     = C_CELL_SELECTED;
                    }
                    drawRoundedRect(ctx, cx, cy, CELL_SIZE, CELL_SIZE, 5, borderArgb);
                    drawRoundedRect(ctx, cx + 2, cy + 2, CELL_SIZE - 4, CELL_SIZE - 4, 3, bgArgb);
                } else if (hovered) {
                    drawRoundedRect(ctx, cx, cy, CELL_SIZE, CELL_SIZE, 5, C_CELL_HOVER);
                }

                ItemStack stack = getIconStack(mob);
                if (!stack.isEmpty()) {
                    int offset = (CELL_SIZE - 16) / 2;
                    ctx.drawItem(stack, cx + offset, cy + offset);
                }

                // Small custom-color dot in bottom-right
                Color customColor = tempColors.get(mob);
                if (customColor != null) {
                    int dotSize = 5;
                    int dx = cx + CELL_SIZE - dotSize - 1;
                    int dy = cy + CELL_SIZE - dotSize - 1;
                    ctx.fill(dx - 1, dy - 1, dx + dotSize + 1, dy + dotSize + 1, 0xFF000000);
                    ctx.fill(dx, dy, dx + dotSize, dy + dotSize,
                            0xFF000000 | (customColor.getRGB() & 0xFFFFFF));
                }
            }
        }
        RenderUtil.clearScissor(false);

        // Scrollbar
        if (totalRows > visibleRows) {
            float sbX = gridX + gridW + 3;
            float thumbH   = Math.max(10f, gridH * visibleRows / (float) totalRows);
            float thumbOff = (gridH - thumbH) * scrollRow / (float) Math.max(1, totalRows - visibleRows);
            RenderUtil.drawRoundedRect(ctx, sbX, gridY, SCROLL_W, gridH, SCROLL_W / 2.0f, C_SCROLL, false);
            RenderUtil.drawRoundedRect(ctx, sbX, gridY + thumbOff, SCROLL_W, thumbH, SCROLL_W / 2.0f, C_SCROLL_THUMB, false);
        }

        // Footer
        ctx.fill(panelX + GRID_PAD, footerTop, panelX + PANEL_W - GRID_PAD, footerTop + 1, C_DIVIDER);
        btnH = 28;
        btnBW = 72;
        int gap = 6;
        btnY = footerTop + (FOOTER_H - btnH) / 2;
        btnSaveX   = panelX + PANEL_W - GRID_PAD - btnBW;
        btnCancelX = btnSaveX   - gap - btnBW;
        btnClearX  = btnCancelX - gap - btnBW;

        boolean hClear  = hit(mx, my, btnClearX,  btnY, btnBW, btnH);
        boolean hCancel = hit(mx, my, btnCancelX, btnY, btnBW, btnH);
        boolean hSave   = hit(mx, my, btnSaveX,   btnY, btnBW, btnH);
        if (hClear)  drawRoundedRect(ctx, btnClearX,  btnY, btnBW, btnH, 4, C_BTN_NEUTRAL_HV);
        if (hCancel) drawRoundedRect(ctx, btnCancelX, btnY, btnBW, btnH, 4, C_BTN_NEUTRAL_HV);
        drawRoundedRect(ctx, btnSaveX, btnY, btnBW, btnH, 4, hSave ? C_BTN_SAVE_HV : C_BTN_SAVE);
        drawCenter(ctx, "Clear",  btnClearX,  btnY, btnBW, btnH, C_TEXT_MUTED);
        drawCenter(ctx, "Cancel", btnCancelX, btnY, btnBW, btnH, C_TEXT_MUTED);
        drawCenter(ctx, "Save",   btnSaveX,   btnY, btnBW, btnH, C_TEXT);

        super.render(ctx, mx, my, delta);

        if (pickerMob == null && hoverCell >= 0 && hoverCell < displayedMobs.size()) {
            drawTooltip(ctx, setting.getDisplayName(displayedMobs.get(hoverCell)), mx, my);
        }

        if (pickerMob != null) {
            drawHsvPicker(ctx);
        }
    }

    private void drawTooltip(DrawContext ctx, String text, int mx, int my) {
        int textW = ZenyaFont.width(this.textRenderer, text);
        int padX = 6, padY = 4;
        int boxW = textW + padX * 2;
        int boxH = this.textRenderer.fontHeight + padY * 2;
        int x = Math.min(mx + 10, this.width - boxW - 2);
        int y = Math.min(my + 10, this.height - boxH - 2);
        ctx.fill(x - 1, y - 1, x + boxW + 1, y + boxH + 1, C_TOOLTIP_BORDER);
        ctx.fill(x, y, x + boxW, y + boxH, C_TOOLTIP_BG);
        ZenyaFont.draw(ctx, this.textRenderer, text, x + padX, y + padY, C_TEXT, false);
    }

    /** Spawn egg if vanilla provides one for this mob; otherwise generic egg as fallback. */
    private ItemStack getIconStack(EntityType<?> mob) {
        SpawnEggItem egg = SpawnEggItem.forEntity(mob);
        if (egg != null) return new ItemStack(egg);
        return new ItemStack(Items.EGG);
    }

    // ── HSV picker popup (unchanged from previous MobPickerScreen) ────────────

    private void drawHsvPicker(DrawContext ctx) {
        int px = Math.min(pickerRootX, this.width  - PICK_TOTAL_W - 2);
        int py = Math.min(pickerRootY, this.height - PICK_TOTAL_H - 2);
        px = Math.max(2, px); py = Math.max(2, py);

        RenderUtil.drawRoundedRect(ctx, px, py, PICK_TOTAL_W, PICK_TOTAL_H, 6.0f, C_POPUP_BG, false);
        RenderUtil.drawOutline(ctx, px, py, PICK_TOTAL_W, PICK_TOTAL_H, 6.0f, 1.0f, C_POPUP_BORDER, false);

        int innerX = px + PICK_PAD;
        int innerY = py + PICK_PAD;

        // SV square
        pSvX = innerX; pSvY = innerY;
        for (int xi = 0; xi < PICK_SV_W; xi++) {
            float sat = xi / (float) PICK_SV_W;
            int top = 0xFF000000 | Color.HSBtoRGB(pickerHSV[0], sat, 1.0f);
            ctx.fillGradient(pSvX + xi, pSvY, pSvX + xi + 1, pSvY + PICK_SV_H, top, 0xFF000000);
        }
        int crossX = clamp(pSvX + (int)(pickerHSV[1] * PICK_SV_W), pSvX, pSvX + PICK_SV_W - 1);
        int crossY = clamp(pSvY + (int)((1.0f - pickerHSV[2]) * PICK_SV_H), pSvY, pSvY + PICK_SV_H - 1);
        ctx.fill(crossX - 4, crossY - 1, crossX - 1, crossY + 2, 0xFF000000);
        ctx.fill(crossX + 2, crossY - 1, crossX + 5, crossY + 2, 0xFF000000);
        ctx.fill(crossX - 1, crossY - 4, crossX + 2, crossY - 1, 0xFF000000);
        ctx.fill(crossX - 1, crossY + 2, crossX + 2, crossY + 5, 0xFF000000);
        ctx.fill(crossX - 1, crossY - 1, crossX + 2, crossY + 2, C_KNOB);

        // Hue bar
        pHueX = innerX + PICK_SV_W + PICK_GAP; pHueY = innerY;
        for (int yi = 0; yi < PICK_SV_H; yi++) {
            float hue = yi / (float) PICK_SV_H;
            ctx.fill(pHueX, pHueY + yi, pHueX + PICK_HUE_W, pHueY + yi + 1,
                    0xFF000000 | Color.HSBtoRGB(hue, 1.0f, 1.0f));
        }
        int hueHandleY = clamp(pHueY + (int)(pickerHSV[0] * PICK_SV_H), pHueY, pHueY + PICK_SV_H - 1);
        ctx.fill(pHueX - 2, hueHandleY - 1, pHueX + PICK_HUE_W + 2, hueHandleY + 1, C_KNOB);

        // Alpha bar
        int alphaBarW = PICK_SV_W + PICK_GAP + PICK_HUE_W;
        pAlphaX = innerX; pAlphaY = innerY + PICK_SV_H + PICK_GAP;
        int sq = 4;
        for (int xi = 0; xi < alphaBarW; xi += sq)
            for (int yi = 0; yi < PICK_ALPHA_H; yi += sq) {
                int c = ((xi / sq) + (yi / sq)) % 2 == 0 ? C_CHECKER_A : C_CHECKER_B;
                ctx.fill(pAlphaX + xi, pAlphaY + yi,
                        Math.min(pAlphaX + xi + sq, pAlphaX + alphaBarW),
                        Math.min(pAlphaY + yi + sq, pAlphaY + PICK_ALPHA_H), c);
            }
        int curRgbRaw = Color.HSBtoRGB(pickerHSV[0], pickerHSV[1], pickerHSV[2]) & 0xFFFFFF;
        for (int xi = 0; xi < alphaBarW; xi++) {
            int a = (int)((xi / (float) alphaBarW) * 255);
            ctx.fill(pAlphaX + xi, pAlphaY, pAlphaX + xi + 1, pAlphaY + PICK_ALPHA_H, (a << 24) | curRgbRaw);
        }
        int alphaHandleX = clamp(pAlphaX + (int)((pickerAlpha / 255.0f) * alphaBarW), pAlphaX, pAlphaX + alphaBarW - 1);
        ctx.fill(alphaHandleX - 1, pAlphaY - 2, alphaHandleX + 1, pAlphaY + PICK_ALPHA_H + 2, C_KNOB);

        // Preview
        int prevY = pAlphaY + PICK_ALPHA_H + PICK_GAP;
        ctx.fill(innerX, prevY, innerX + PICK_PREV_H, prevY + PICK_PREV_H, 0xFF000000 | curRgbRaw);
        String hex = String.format("#%02X%02X%02X%02X", pickerAlpha,
                (curRgbRaw >> 16) & 0xFF, (curRgbRaw >> 8) & 0xFF, curRgbRaw & 0xFF);
        ZenyaFont.draw(ctx, this.textRenderer, hex,
                innerX + PICK_PREV_H + 6,
                prevY + (PICK_PREV_H - this.textRenderer.fontHeight) / 2 + 1, C_TEXT, false);
    }

    private boolean isInsidePicker(int mx, int my) {
        if (pickerMob == null) return false;
        int px = Math.min(pickerRootX, this.width  - PICK_TOTAL_W - 2);
        int py = Math.min(pickerRootY, this.height - PICK_TOTAL_H - 2);
        return hit(mx, my, Math.max(2, px), Math.max(2, py), PICK_TOTAL_W, PICK_TOTAL_H);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        int btn = click.button();

        if (pickerMob != null) {
            if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (hitSv(mx, my))    { dragMode = DragMode.SV;    applySvDrag(mx, my);    return true; }
                if (hitHue(mx, my))   { dragMode = DragMode.HUE;   applyHueDrag(mx, my);   return true; }
                if (hitAlpha(mx, my)) { dragMode = DragMode.ALPHA;  applyAlphaDrag(mx, my); return true; }
                if (!isInsidePicker(mx, my)) { commitPickerColor(); pickerMob = null; }
                return true;
            }
            if (btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT) { commitPickerColor(); pickerMob = null; return true; }
            return true;
        }

        if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (hit(mx, my, tabAllX, tabRowY, tabAllW, TAB_H)) { showSelected = false; scrollRow = 0; return true; }
            if (hit(mx, my, tabSelX, tabRowY, tabSelW, TAB_H)) { showSelected = true;  scrollRow = 0; return true; }
            searchFocused = hit(mx, my, searchBarX, searchBarY, searchBarW, SEARCH_H);
            if (hoverCell >= 0 && hoverCell < displayedMobs.size()) {
                EntityType<?> mob = displayedMobs.get(hoverCell);
                if (tempSelected.contains(mob)) tempSelected.remove(mob);
                else tempSelected.add(mob);
                return true;
            }
            if (hit(mx, my, btnClearX,  btnY, btnBW, btnH)) { tempSelected.clear(); tempColors.clear(); return true; }
            if (hit(mx, my, btnCancelX, btnY, btnBW, btnH)) { dismiss(); return true; }
            if (hit(mx, my, btnSaveX,   btnY, btnBW, btnH)) { save(); return true; }
        } else if (btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (hoverCell >= 0 && hoverCell < displayedMobs.size()) {
                EntityType<?> mob = displayedMobs.get(hoverCell);
                openPickerForMob(mob, mx, my);
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double ox, double oy) {
        if (pickerMob != null && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int x = (int) click.x(), y = (int) click.y();
            switch (dragMode) {
                case SV    -> { applySvDrag(x, y);    return true; }
                case HUE   -> { applyHueDrag(x, y);   return true; }
                case ALPHA -> { applyAlphaDrag(x, y); return true; }
                default    -> {}
            }
        }
        return super.mouseDragged(click, ox, oy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragMode != DragMode.NONE) { dragMode = DragMode.NONE; return true; }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double ha, double va) {
        if (pickerMob != null) return true;
        scrollRow -= (int) Math.signum(va);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (pickerMob != null) { pickerMob = null; return true; }
            dismiss(); return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { save(); return true; }
        if (searchFocused && input.isPaste()) {
            searchQuery += getClipboardText();
            scrollRow = 0; return true;
        }
        if (searchFocused && key == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            scrollRow = 0; return true;
        }
        return super.keyPressed(input);
    }

    private String getClipboardText() {
        String clip = net.minecraft.client.MinecraftClient.getInstance().keyboard.getClipboard();
        if (clip == null || clip.isEmpty()) return "";
        StringBuilder b = new StringBuilder(clip.length());
        clip.codePoints().filter(cp -> !Character.isISOControl(cp)).forEach(b::appendCodePoint);
        return b.toString();
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (searchFocused) {
            searchQuery += Character.toString(input.codepoint());
            scrollRow = 0; return true;
        }
        return super.charTyped(input);
    }

    // ── Picker helpers ────────────────────────────────────────────────────────

    private void openPickerForMob(EntityType<?> mob, int mx, int my) {
        pickerMob = mob;
        pickerRootX = mx + 6;
        pickerRootY = my + 6;
        dragMode = DragMode.NONE;
        Color existing = tempColors.get(mob);
        if (existing != null) {
            Color.RGBtoHSB(existing.getRed(), existing.getGreen(), existing.getBlue(), pickerHSV);
            pickerAlpha = existing.getAlpha();
        } else {
            pickerHSV[0] = 0.0f; pickerHSV[1] = 1.0f; pickerHSV[2] = 1.0f;
            pickerAlpha = 255;
        }
    }

    private void commitPickerColor() {
        if (pickerMob == null) return;
        int rgb = Color.HSBtoRGB(pickerHSV[0], pickerHSV[1], pickerHSV[2]);
        tempColors.put(pickerMob, new Color((pickerAlpha << 24) | (rgb & 0xFFFFFF), true));
    }

    private boolean hitSv(int mx, int my)    { return pickerMob != null && hit(mx, my, pSvX,    pSvY,    PICK_SV_W,                        PICK_SV_H); }
    private boolean hitHue(int mx, int my)   { return pickerMob != null && hit(mx, my, pHueX,   pHueY,   PICK_HUE_W,                       PICK_SV_H); }
    private boolean hitAlpha(int mx, int my) { return pickerMob != null && hit(mx, my, pAlphaX, pAlphaY, PICK_SV_W + PICK_GAP + PICK_HUE_W, PICK_ALPHA_H); }

    private void applySvDrag(int mx, int my) {
        pickerHSV[1] = clamp01((mx - pSvX) / (float) PICK_SV_W);
        pickerHSV[2] = clamp01(1.0f - (my - pSvY) / (float) PICK_SV_H);
    }
    private void applyHueDrag(int mx, int my) { pickerHSV[0] = clamp01((my - pHueY) / (float) PICK_SV_H); }
    private void applyAlphaDrag(int mx, int my) {
        pickerAlpha = (int)(clamp01((mx - pAlphaX) / (float)(PICK_SV_W + PICK_GAP + PICK_HUE_W)) * 255);
    }

    // ── Save / dismiss ────────────────────────────────────────────────────────

    private void save() {
        if (pickerMob != null) commitPickerColor();
        setting.setValue(new LinkedHashSet<>(tempSelected));
        if (espModule != null) {
            for (EntityType<?> t : setting.getAvailableMobs()) {
                espModule.setCustomMobColor(t, tempColors.getOrDefault(t, null));
            }
        }
        dismiss();
    }

    private void dismiss() { this.client.setScreen(parent); }

    // ── Util ──────────────────────────────────────────────────────────────────

    private int getVisibleSelectedCount() {
        if (!showSelected || searchQuery.isBlank()) return tempSelected.size();
        int count = 0;
        for (EntityType<?> m : tempSelected) {
            if (matchesSearch(m, searchQuery)) count++;
        }
        return count;
    }

    private boolean matchesSearch(EntityType<?> mob, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        return setting.getDisplayName(mob).toLowerCase(Locale.ROOT).contains(q);
    }

    private void drawCenter(DrawContext ctx, String label, int x, int y, int w, int h, int color) {
        ZenyaFont.draw(ctx, this.textRenderer, label,
                x + (w - ZenyaFont.width(this.textRenderer, label)) / 2,
                y + (h - this.textRenderer.fontHeight) / 2 + 1, color, false);
    }

    private boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void drawRoundedRect(DrawContext ctx, int x, int y, int w, int h, int radius, int argb) {
        int r = Math.min(radius, Math.min(w, h) / 2);
        ctx.fill(x, y + r, x + w, y + h - r, argb);
        for (int j = 0; j < r; j++) {
            int dy  = r - j;
            int dx  = (int) Math.ceil(r - Math.sqrt((double) r * r - dy * dy));
            int top = y + j;
            int bot = y + h - j - 1;
            ctx.fill(x + dx, top, x + w - dx, top + 1, argb);
            ctx.fill(x + dx, bot, x + w - dx, bot + 1, argb);
        }
    }

    private static float clamp01(float v) { return v < 0f ? 0f : v > 1f ? 1f : v; }
    private static int   clamp(int v, int lo, int hi) { return v < lo ? lo : v > hi ? hi : v; }
}
