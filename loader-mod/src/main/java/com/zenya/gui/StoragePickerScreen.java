package com.zenya.gui;

import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.setting.StorageBlocksSetting;
import com.zenya.utils.ZenyaFont;
import com.zenya.utils.renderer.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StoragePickerScreen extends Screen {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 360;
    private static final int HEADER_H = 32;
    private static final int FOOTER_H = 36;
    private static final int PAD = 12;
    private static final int CELL = 48;
    private static final int GAP = 8;
    private static final int COLS = 4;

    private static final int C_BG = 0xFF000000;
    private static final int C_PANEL = 0xFF0A0A0A;
    private static final int C_BORDER = 0xFF222222;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_MUTED = 0xFF8A8F9A;

    private final StorageBlocksSetting setting;
    private final Screen parent;
    private final Set<String> tempSelected;
    private final Map<String, Color> tempColors;

    private float openAnim = 0f;
    private long lastNano = 0L;
    private int panelX, panelY;
    private boolean draggingPanel = false;
    private int dragOffX, dragOffY;

    private String colorEntry = null;
    private int pickerX, pickerY;
    private boolean draggingPicker = false;
    private int pickerDragOffX, pickerDragOffY;
    private float[] pickerHSV = new float[3];
    private int pickerAlpha = 255;
    private enum DragMode { NONE, SV, HUE, ALPHA }
    private DragMode dragMode = DragMode.NONE;
    private int pSvX, pSvY, pHueX, pHueY, pAlphaX, pAlphaY;

    private int gridX, gridY;
    private int btnY, btnH, btnSaveX, btnCancelX, btnBW;
    private int hoverIndex = -1;

    public StoragePickerScreen(Screen parent, StorageBlocksSetting setting) {
        super(Text.literal("Blocks"));
        this.parent = parent;
        this.setting = setting;
        this.tempSelected = new LinkedHashSet<>(setting.getSelected());
        this.tempColors = new LinkedHashMap<>();
        for (StorageBlocksSetting.Entry e : setting.getOptions()) {
            tempColors.put(e.value(), setting.getColor(e.value()));
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = lastNano == 0L ? 0.016f : Math.min(0.1f, (now - lastNano) / 1_000_000_000f);
        lastNano = now;
        openAnim += (1f - openAnim) * (1f - (float) Math.exp(-16f * dt));

        if (panelX == 0 && panelY == 0) {
            panelX = (width - PANEL_W) / 2;
            panelY = (height - PANEL_H) / 2;
        }

        int accent = ZenyaPlus.getAccentARGB();
        float scale = 0.88f + 0.12f * openAnim;
        float slide = (1f - openAnim) * 16f;
        int cx = panelX + PANEL_W / 2;
        int cy = panelY + PANEL_H / 2;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(cx, cy + slide);
        ctx.getMatrices().scale(scale, scale);
        ctx.getMatrices().translate(-cx, -cy);

        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, C_BG);
        RenderUtil.drawRoundedRect(ctx, panelX, panelY, PANEL_W, PANEL_H, 14f, C_PANEL, false);
        RenderUtil.drawOutline(ctx, panelX, panelY, PANEL_W, PANEL_H, 14f, 1f, C_BORDER, false);

        ZenyaFont.draw(ctx, textRenderer, "Blocks", panelX + PAD, panelY + 10, C_TEXT, false);
        String count = tempSelected.size() + " / " + setting.getOptions().size();
        ZenyaFont.draw(ctx, textRenderer, count, panelX + PANEL_W - PAD - ZenyaFont.width(textRenderer, count), panelY + 10, C_MUTED, false);
        ctx.fill(panelX + PAD, panelY + HEADER_H, panelX + PANEL_W - PAD, panelY + HEADER_H + 1, C_BORDER);

        gridX = panelX + PAD;
        gridY = panelY + HEADER_H + 10;
        int footerTop = panelY + PANEL_H - FOOTER_H;
        List<StorageBlocksSetting.Entry> entries = setting.getOptions();
        hoverIndex = -1;

        for (int i = 0; i < entries.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = gridX + col * (CELL + GAP);
            int y = gridY + row * (CELL + GAP);
            if (y + CELL > footerTop - 4) break;

            StorageBlocksSetting.Entry entry = entries.get(i);
            boolean selected = tempSelected.contains(entry.value());
            boolean hovered = hit(mx, my, x, y, CELL, CELL);
            if (hovered) hoverIndex = i;

            int fill = selected ? blend(0xFF151515, accent, 0.35f) : (hovered ? 0xFF121212 : 0xFF0D0D0D);
            RenderUtil.drawRoundedRect(ctx, x, y, CELL, CELL, 10f, fill, false);
            if (selected) {
                RenderUtil.drawOutline(ctx, x, y, CELL, CELL, 10f, 1f, accent, false);
            }

            try {
                Block b = Registries.BLOCK.get(Identifier.of(entry.value()));
                if (b != null && b != net.minecraft.block.Blocks.AIR) {
                    ctx.drawItem(new net.minecraft.item.ItemStack(b), x + (CELL - 16) / 2, y + (CELL - 16) / 2);
                } else {
                    String label = entry.label();
                    if (label.length() > 10) label = label.substring(0, 9) + "…";
                    int lw = ZenyaFont.width(textRenderer, label);
                    ZenyaFont.draw(ctx, textRenderer, label, x + (CELL - lw) / 2, y + (CELL - textRenderer.fontHeight) / 2 + 1,
                            selected ? C_TEXT : C_MUTED, false);
                }
            } catch (Exception e) {
                String label = entry.label();
                if (label.length() > 10) label = label.substring(0, 9) + "…";
                int lw = ZenyaFont.width(textRenderer, label);
                ZenyaFont.draw(ctx, textRenderer, label, x + (CELL - lw) / 2, y + (CELL - textRenderer.fontHeight) / 2 + 1,
                        selected ? C_TEXT : C_MUTED, false);
            }

            Color cv = tempColors.get(entry.value());
            if (cv != null) {
                int dot = 8;
                int dx = x + CELL - dot - 5;
                int dy = y + 5;
                ctx.fill(dx - 1, dy - 1, dx + dot + 1, dy + dot + 1, 0xFF000000);
                ctx.fill(dx, dy, dx + dot, dy + dot, 0xFF000000 | (cv.getRGB() & 0xFFFFFF));
            }
        }

        ctx.fill(panelX + PAD, footerTop, panelX + PANEL_W - PAD, footerTop + 1, C_BORDER);
        btnH = 26;
        btnBW = 72;
        btnY = footerTop + (FOOTER_H - btnH) / 2;
        btnSaveX = panelX + PAD;
        btnCancelX = panelX + PANEL_W - PAD - btnBW;

        boolean hSave = hit(mx, my, btnSaveX, btnY, btnBW, btnH);
        boolean hCancel = hit(mx, my, btnCancelX, btnY, btnBW, btnH);
        RenderUtil.drawRoundedRect(ctx, btnSaveX, btnY, btnBW, btnH, 8f, hSave ? 0xFF151515 : C_BG, false);
        RenderUtil.drawOutline(ctx, btnSaveX, btnY, btnBW, btnH, 8f, 1f, accent, false);
        RenderUtil.drawRoundedRect(ctx, btnCancelX, btnY, btnBW, btnH, 8f, hCancel ? 0xFF151515 : 0xFF111111, false);
        drawCenter(ctx, "Save", btnSaveX, btnY, btnBW, btnH, C_TEXT);
        drawCenter(ctx, "Cancel", btnCancelX, btnY, btnBW, btnH, C_MUTED);

        ctx.getMatrices().popMatrix();
        super.render(ctx, mx, my, delta);

        if (colorEntry != null) drawHsvPicker(ctx, mx, my);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x();
        int my = (int) click.y();
        int btn = click.button();

        if (colorEntry != null) {
            if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (hitSv(mx, my)) { dragMode = DragMode.SV; applySvDrag(mx, my); return true; }
                if (hitHue(mx, my)) { dragMode = DragMode.HUE; applyHueDrag(mx, my); return true; }
                if (hitAlpha(mx, my)) { dragMode = DragMode.ALPHA; applyAlphaDrag(mx, my); return true; }
                if (!isInsidePicker(mx, my)) { commitColor(); colorEntry = null; }
                return true;
            }
            if (btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                commitColor();
                colorEntry = null;
                return true;
            }
            return true;
        }

        if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (hit(mx, my, panelX, panelY, PANEL_W, HEADER_H)) {
                draggingPanel = true;
                dragOffX = mx - panelX;
                dragOffY = my - panelY;
                return true;
            }
            if (hoverIndex >= 0) {
                List<StorageBlocksSetting.Entry> entries = setting.getOptions();
                if (hoverIndex < entries.size()) {
                    StorageBlocksSetting.Entry entry = entries.get(hoverIndex);
                    int col = hoverIndex % COLS;
                    int row = hoverIndex / COLS;
                    int x = gridX + col * (CELL + GAP);
                    int y = gridY + row * (CELL + GAP);
                    if (mx >= x + CELL - 16 && mx <= x + CELL - 2 && my >= y + 2 && my <= y + 14) {
                        openColorPicker(entry.value(), mx, my);
                        return true;
                    }
                    if (tempSelected.contains(entry.value())) tempSelected.remove(entry.value());
                    else tempSelected.add(entry.value());
                    return true;
                }
            }
            if (hit(mx, my, btnSaveX, btnY, btnBW, btnH)) { save(); return true; }
            if (hit(mx, my, btnCancelX, btnY, btnBW, btnH)) { dismiss(); return true; }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double ox, double oy) {
        if (draggingPanel) {
            panelX = (int) click.x() - dragOffX;
            panelY = (int) click.y() - dragOffY;
            return true;
        }
        if (colorEntry != null && dragMode != DragMode.NONE) {
            int mx = (int) click.x();
            int my = (int) click.y();
            switch (dragMode) {
                case SV -> applySvDrag(mx, my);
                case HUE -> applyHueDrag(mx, my);
                case ALPHA -> applyAlphaDrag(mx, my);
                default -> { }
            }
            return true;
        }
        if (draggingPicker) {
            pickerX = (int) click.x() - pickerDragOffX;
            pickerY = (int) click.y() - pickerDragOffY;
            return true;
        }
        return super.mouseDragged(click, ox, oy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingPanel = false;
        draggingPicker = false;
        dragMode = DragMode.NONE;
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (colorEntry != null) { colorEntry = null; return true; }
            dismiss();
            return true;
        }
        return super.keyPressed(input);
    }

    private void save() {
        setting.setValue(new LinkedHashSet<>(tempSelected));
        for (Map.Entry<String, Color> e : tempColors.entrySet()) {
            setting.setColor(e.getKey(), e.getValue());
        }
        dismiss();
    }

    private void dismiss() {
        if (client != null) client.setScreen(parent);
    }

    private void openColorPicker(String value, int mx, int my) {
        colorEntry = value;
        pickerX = mx;
        pickerY = my;
        Color c = tempColors.getOrDefault(value, Color.WHITE);
        pickerHSV = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        pickerAlpha = c.getAlpha();
    }

    private void commitColor() {
        if (colorEntry == null) return;
        int rgb = Color.HSBtoRGB(pickerHSV[0], pickerHSV[1], pickerHSV[2]) & 0xFFFFFF;
        tempColors.put(colorEntry, new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, pickerAlpha));
    }

    private void drawHsvPicker(DrawContext ctx, int mx, int my) {
        int pw = 150;
        int ph = 140;
        int px = Math.max(2, Math.min(pickerX, width - pw - 2));
        int py = Math.max(2, Math.min(pickerY, height - ph - 2));

        RenderUtil.drawRoundedRect(ctx, px, py, pw, ph, 12f, C_BG, false);
        RenderUtil.drawOutline(ctx, px, py, pw, ph, 12f, 1f, C_BORDER, false);
        RenderUtil.drawRoundedRect(ctx, px + 4, py + 4, pw - 8, 8, 4f, 0xFF151515, false);

        int innerX = px + 10;
        int innerY = py + 18;
        pSvX = innerX; pSvY = innerY;
        int svW = 100;
        int svH = 70;
        int hueW = 12;

        for (int xi = 0; xi < svW; xi++) {
            float sat = xi / (float) svW;
            int top = 0xFF000000 | Color.HSBtoRGB(pickerHSV[0], sat, 1f);
            ctx.fillGradient(pSvX + xi, pSvY, pSvX + xi + 1, pSvY + svH, top, 0xFF000000);
        }

        pHueX = innerX + svW + 6; pHueY = innerY;
        for (int yi = 0; yi < svH; yi++) {
            int rgb = 0xFF000000 | Color.HSBtoRGB(yi / (float) svH, 1f, 1f);
            ctx.fill(pHueX, pHueY + yi, pHueX + hueW, pHueY + yi + 1, rgb);
        }

        int alphaBarW = svW + 6 + hueW;
        pAlphaX = innerX; pAlphaY = innerY + svH + 8;
        int curRgb = Color.HSBtoRGB(pickerHSV[0], pickerHSV[1], pickerHSV[2]) & 0xFFFFFF;
        for (int xi = 0; xi < alphaBarW; xi++) {
            int a = (int) ((xi / (float) alphaBarW) * 255);
            ctx.fill(pAlphaX + xi, pAlphaY, pAlphaX + xi + 1, pAlphaY + 8, (a << 24) | curRgb);
        }
    }

    private boolean isInsidePicker(int mx, int my) {
        int pw = 150, ph = 140;
        int px = Math.max(2, Math.min(pickerX, width - pw - 2));
        int py = Math.max(2, Math.min(pickerY, height - ph - 2));
        return hit(mx, my, px, py, pw, ph);
    }

    private boolean hitSv(int mx, int my) { return hit(mx, my, pSvX, pSvY, 100, 70); }
    private boolean hitHue(int mx, int my) { return hit(mx, my, pHueX, pHueY, 12, 70); }
    private boolean hitAlpha(int mx, int my) { return hit(mx, my, pAlphaX, pAlphaY, 118, 8); }

    private void applySvDrag(int mx, int my) {
        pickerHSV[1] = clamp((mx - pSvX) / 100f, 0f, 1f);
        pickerHSV[2] = clamp(1f - (my - pSvY) / 70f, 0f, 1f);
        commitColor();
    }

    private void applyHueDrag(int mx, int my) {
        pickerHSV[0] = clamp((my - pHueY) / 70f, 0f, 1f);
        commitColor();
    }

    private void applyAlphaDrag(int mx, int my) {
        pickerAlpha = (int) clamp((mx - pAlphaX) / 118f * 255f, 0f, 255f);
        commitColor();
    }

    private static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int blend(int base, int accent, float t) {
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int ar = (accent >> 16) & 0xFF, ag = (accent >> 8) & 0xFF, ab = accent & 0xFF;
        return 0xFF000000 | ((int) (br + (ar - br) * t) << 16) | ((int) (bg + (ag - bg) * t) << 8) | (int) (bb + (ab - bb) * t);
    }

    private void drawCenter(DrawContext ctx, String text, int x, int y, int w, int h, int color) {
        int tw = ZenyaFont.width(textRenderer, text);
        ZenyaFont.draw(ctx, textRenderer, text, x + (w - tw) / 2, y + (h - textRenderer.fontHeight) / 2, color, false);
    }
}
