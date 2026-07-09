package com.zenya.gui;

import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.module.modules.render.BlockESP;
import com.zenya.setting.BlocksSetting;
import com.zenya.utils.ZenyaFont;
import com.zenya.utils.renderer.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BlockPickerScreen extends Screen {

    private static final int PANEL_W = 560;
    private static final int PANEL_H = 400;
    private static final int PANEL_R = 16;
    private static final int HEADER_H = 30;
    private static final int SEARCH_H = 28;
    private static final int FOOTER_H = 36;
    private static final int LEFT_W = 150;
    private static final int RIGHT_W = 130;
    private static final int PAD = 12;
    private static final int ROW_H = 20;

    private static final int C_BG = 0xFF0A0A0A;
    private static final int C_PANEL = 0xFF121212;
    private static final int C_BORDER = 0xFF252525;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_MUTED = 0xFF8A8F9A;
    private static final int C_ROW_HOVER = 0xFF1A1A1A;
    private static final int C_ROW_SEL = 0xFF222222;

    private final BlocksSetting setting;
    private final Screen parent;
    private final BlockESP espModule;
    private final Set<Block> tempSelected;
    private final Map<Block, Color> tempColors;

    private String searchQuery = "";
    private boolean searchFocused = false;
    private int centerScroll = 0;
    private int rightScroll = 0;
    private Block activeBlock = null;

    private int panelX, panelY;
    private boolean draggingPanel = false;
    private int dragOffX, dragOffY;

    private float[] pickerHSV = new float[3];
    private int pickerAlpha = 255;
    private int pickerX, pickerY;
    private boolean draggingPicker = false;
    private int pickerDragOffX, pickerDragOffY;
    private enum DragMode { NONE, SV, HUE, ALPHA }
    private DragMode dragMode = DragMode.NONE;
    private int pSvX, pSvY, pHueX, pHueY, pAlphaX, pAlphaY;

    private float openAnim = 0f;
    private long openNano = 0L;
    private List<Block> filteredBlocks = new ArrayList<>();
    private int hoverCenter = -1;
    private int hoverRight = -1;

    public BlockPickerScreen(Screen parent, BlocksSetting setting, BlockESP espModule) {
        super(Text.literal("Block ESP"));
        this.parent = parent;
        this.setting = setting;
        this.espModule = espModule;
        this.tempSelected = new LinkedHashSet<>(setting.getSelectedBlocks());
        this.tempColors = new HashMap<>(espModule != null ? espModule.getColorMap() : Map.of());
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = openNano == 0L ? 0.016f : Math.min(0.1f, (now - openNano) / 1_000_000_000f);
        openNano = now;
        openAnim += (1f - openAnim) * (1f - (float) Math.exp(-16f * dt));

        if (panelX == 0 && panelY == 0) {
            panelX = (width - PANEL_W) / 2;
            panelY = (height - PANEL_H) / 2;
        }

        int accent = ZenyaPlus.getAccentARGB();
        float scale = 0.88f + 0.12f * openAnim;
        float slide = (1f - openAnim) * 18f;
        int pivotX = panelX + PANEL_W / 2;
        int pivotY = panelY + PANEL_H / 2;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(pivotX, pivotY + slide);
        ctx.getMatrices().scale(scale, scale);
        ctx.getMatrices().translate(-pivotX, -pivotY);

        // Draw shadow/dim behind panel
        ctx.fill(0, 0, this.width, this.height, 0x55000000);
        // Main panel with proper rounded corners (no sharp fill override)
        RenderUtil.drawRoundedRect(ctx, panelX, panelY, PANEL_W, PANEL_H, PANEL_R, C_PANEL, false);
        RenderUtil.drawOutline(ctx, panelX, panelY, PANEL_W, PANEL_H, PANEL_R, 1f, C_BORDER, false);

        // Header (draggable)
        ZenyaFont.draw(ctx, textRenderer, "Block ESP", panelX + PAD, panelY + 10, C_TEXT, false);
        String count = tempSelected.size() + " selected";
        ZenyaFont.draw(ctx, textRenderer, count, panelX + PANEL_W - PAD - ZenyaFont.width(textRenderer, count), panelY + 10, C_MUTED, false);
        ctx.fill(panelX + PAD, panelY + HEADER_H, panelX + PANEL_W - PAD, panelY + HEADER_H + 1, C_BORDER);

        // Search (top)
        int searchY = panelY + HEADER_H + 8;
        int searchW = PANEL_W - PAD * 2;
        int searchX = panelX + PAD;
        RenderUtil.drawRoundedRect(ctx, searchX, searchY, searchW, SEARCH_H, 10f, 0xFF0F0F0F, false);
        RenderUtil.drawOutline(ctx, searchX, searchY, searchW, SEARCH_H, 10f, 1f, searchFocused ? accent : C_BORDER, false);
        String searchDisplay = searchQuery.isEmpty() ? "Search blocks..." : searchQuery;
        if (searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) searchDisplay += "_";
        ZenyaFont.draw(ctx, textRenderer, searchDisplay, searchX + 10, searchY + 8,
                searchQuery.isEmpty() && !searchFocused ? C_MUTED : C_TEXT, false);

        int bodyY = searchY + SEARCH_H + 10;
        int footerTop = panelY + PANEL_H - FOOTER_H;
        int bodyH = footerTop - bodyY - 8;
        int centerX = panelX + PAD + LEFT_W + 8;
        int centerW = PANEL_W - PAD * 2 - LEFT_W - RIGHT_W - 16;
        int rightX = centerX + centerW + 8;

        // Left — color picker
        RenderUtil.drawRoundedRect(ctx, panelX + PAD, bodyY, LEFT_W, bodyH, 10f, 0xFF080808, false);
        ZenyaFont.draw(ctx, textRenderer, "Color", panelX + PAD + 8, bodyY + 6, C_MUTED, false);
        if (activeBlock != null) {
            drawEmbeddedPicker(ctx, mx, my, panelX + PAD + 6, bodyY + 22, LEFT_W - 12, bodyH - 28);
        } else {
            ZenyaFont.draw(ctx, textRenderer, "Select a block", panelX + PAD + 8, bodyY + 40, C_MUTED, false);
        }

        // Center — all blocks (text only)
        RenderUtil.drawRoundedRect(ctx, centerX, bodyY, centerW, bodyH, 10f, 0xFF080808, false);
        ZenyaFont.draw(ctx, textRenderer, "All Blocks", centerX + 8, bodyY + 6, C_MUTED, false);
        filteredBlocks = setting.filter(searchQuery);
        int listY = bodyY + 22;
        int visibleCenter = Math.max(1, (bodyH - 26) / ROW_H);
        centerScroll = Math.max(0, Math.min(centerScroll, Math.max(0, filteredBlocks.size() - visibleCenter)));
        hoverCenter = -1;
        RenderUtil.setScissor(centerX + 4, listY, centerW - 8, bodyH - 28, false);
        for (int i = 0; i < visibleCenter; i++) {
            int idx = centerScroll + i;
            if (idx >= filteredBlocks.size()) break;
            Block block = filteredBlocks.get(idx);
            int rowY = listY + i * ROW_H;
            boolean hovered = hit(mx, my, centerX + 4, rowY, centerW - 8, ROW_H - 2);
            boolean selected = tempSelected.contains(block);
            if (hovered) hoverCenter = idx;
            if (selected || block.equals(activeBlock)) {
                RenderUtil.drawRoundedRect(ctx, centerX + 4, rowY, centerW - 8, ROW_H - 2, 6f,
                        block.equals(activeBlock) ? blend(C_ROW_SEL, accent, 0.25f) : C_ROW_SEL, false);
            } else if (hovered) {
                RenderUtil.drawRoundedRect(ctx, centerX + 4, rowY, centerW - 8, ROW_H - 2, 6f, C_ROW_HOVER, false);
            }
            String label = trimLabel(setting.getDisplayName(block), centerW - 14 - 20);
            RenderUtil.drawRoundedRect(ctx, centerX + 10, rowY + 2, 16, 16, 4f, 0xFF151515, false);
            ctx.drawItem(new net.minecraft.item.ItemStack(block), centerX + 10, rowY + 2);
            ZenyaFont.draw(ctx, textRenderer, label, centerX + 30, rowY + 5, selected ? C_TEXT : C_MUTED, false);
        }
        RenderUtil.clearScissor(false);

        // Right — picked blocks
        RenderUtil.drawRoundedRect(ctx, rightX, bodyY, RIGHT_W, bodyH, 10f, 0xFF080808, false);
        ZenyaFont.draw(ctx, textRenderer, "Picked", rightX + 8, bodyY + 6, C_MUTED, false);
        List<Block> picked = new ArrayList<>(tempSelected);
        int visibleRight = Math.max(1, (bodyH - 26) / ROW_H);
        rightScroll = Math.max(0, Math.min(rightScroll, Math.max(0, picked.size() - visibleRight)));
        hoverRight = -1;
        RenderUtil.setScissor(rightX + 4, listY, RIGHT_W - 8, bodyH - 28, false);
        for (int i = 0; i < visibleRight; i++) {
            int idx = rightScroll + i;
            if (idx >= picked.size()) break;
            Block block = picked.get(idx);
            int rowY = listY + i * ROW_H;
            boolean hovered = hit(mx, my, rightX + 4, rowY, RIGHT_W - 8, ROW_H - 2);
            if (hovered) hoverRight = idx;
            if (block.equals(activeBlock)) {
                RenderUtil.drawRoundedRect(ctx, rightX + 4, rowY, RIGHT_W - 8, ROW_H - 2, 6f, blend(C_ROW_SEL, accent, 0.25f), false);
            } else if (hovered) {
                RenderUtil.drawRoundedRect(ctx, rightX + 4, rowY, RIGHT_W - 8, ROW_H - 2, 6f, C_ROW_HOVER, false);
            }
            String label = trimLabel(setting.getDisplayName(block), RIGHT_W - 14 - 20);
            RenderUtil.drawRoundedRect(ctx, rightX + 10, rowY + 2, 16, 16, 4f, 0xFF151515, false);
            ctx.drawItem(new net.minecraft.item.ItemStack(block), rightX + 10, rowY + 2);
            ZenyaFont.draw(ctx, textRenderer, label, rightX + 30, rowY + 5, C_TEXT, false);
        }
        RenderUtil.clearScissor(false);

        // Footer
        ctx.fill(panelX + PAD, footerTop, panelX + PANEL_W - PAD, footerTop + 1, C_BORDER);
        int btnH = 26;
        int btnW = 72;
        int btnY = footerTop + (FOOTER_H - btnH) / 2;
        int saveX = panelX + PAD;
        int cancelX = panelX + PANEL_W - PAD - btnW;
        boolean hSave = hit(mx, my, saveX, btnY, btnW, btnH);
        boolean hCancel = hit(mx, my, cancelX, btnY, btnW, btnH);
        RenderUtil.drawRoundedRect(ctx, saveX, btnY, btnW, btnH, 8f, hSave ? 0xFF151515 : 0xFF000000, false);
        RenderUtil.drawOutline(ctx, saveX, btnY, btnW, btnH, 8f, 1f, accent, false);
        RenderUtil.drawRoundedRect(ctx, cancelX, btnY, btnW, btnH, 8f, hCancel ? C_ROW_HOVER : 0xFF111111, false);
        drawCenter(ctx, "Save", saveX, btnY, btnW, btnH, C_TEXT);
        drawCenter(ctx, "Cancel", cancelX, btnY, btnW, btnH, C_MUTED);

        ctx.getMatrices().popMatrix();
        super.render(ctx, mx, my, delta);
    }

    private void drawEmbeddedPicker(DrawContext ctx, int mx, int my, int areaX, int areaY, int areaW, int areaH) {
        if (pickerX == 0 && pickerY == 0) {
            pickerX = areaX;
            pickerY = areaY;
        }
        pickerX = clamp(pickerX, areaX, areaX + areaW - 120);
        pickerY = clamp(pickerY, areaY, areaY + areaH - 130);

        int pw = 120;
        int ph = 128;
        RenderUtil.drawRoundedRect(ctx, pickerX, pickerY, pw, ph, 10f, 0xFF000000, false);
        RenderUtil.drawOutline(ctx, pickerX, pickerY, pw, ph, 10f, 1f, C_BORDER, false);

        int innerX = pickerX + 6;
        int innerY = pickerY + 16;
        int svW = 78;
        int svH = 58;
        int hueW = 10;
        int alphaH = 8;

        pSvX = innerX; pSvY = innerY;
        for (int xi = 0; xi < svW; xi++) {
            float sat = xi / (float) svW;
            int top = 0xFF000000 | Color.HSBtoRGB(pickerHSV[0], sat, 1f);
            ctx.fillGradient(pSvX + xi, pSvY, pSvX + xi + 1, pSvY + svH, top, 0xFF000000);
        }
        int crossX = clamp(pSvX + (int) (pickerHSV[1] * svW), pSvX, pSvX + svW - 1);
        int crossY = clamp(pSvY + (int) ((1f - pickerHSV[2]) * svH), pSvY, pSvY + svH - 1);
        ctx.fill(crossX - 1, crossY - 1, crossX + 2, crossY + 2, 0xFFFFFFFF);

        pHueX = innerX + svW + 4; pHueY = innerY;
        for (int yi = 0; yi < svH; yi++) {
            int rgb = 0xFF000000 | Color.HSBtoRGB(yi / (float) svH, 1f, 1f);
            ctx.fill(pHueX, pHueY + yi, pHueX + hueW, pHueY + yi + 1, rgb);
        }

        int alphaBarW = svW + 4 + hueW;
        pAlphaX = innerX; pAlphaY = innerY + svH + 6;
        int curRgb = Color.HSBtoRGB(pickerHSV[0], pickerHSV[1], pickerHSV[2]) & 0xFFFFFF;
        for (int xi = 0; xi < alphaBarW; xi++) {
            int a = (int) ((xi / (float) alphaBarW) * 255);
            ctx.fill(pAlphaX + xi, pAlphaY, pAlphaX + xi + 1, pAlphaY + alphaH, (a << 24) | curRgb);
        }

        int swatchY = pAlphaY + alphaH + 6;
        ctx.fill(innerX, swatchY, innerX + 14, swatchY + 10, 0xFF000000 | curRgb);
        ZenyaFont.draw(ctx, textRenderer, trimLabel(setting.getDisplayName(activeBlock), 60), innerX + 18, swatchY + 1, C_MUTED, false);

        // Drag handle bar
        RenderUtil.drawRoundedRect(ctx, pickerX + 4, pickerY + 4, pw - 8, 8, 4f, 0xFF151515, false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x();
        int my = (int) click.y();
        int btn = click.button();

        if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (hit(mx, my, panelX, panelY, PANEL_W, HEADER_H)) {
                draggingPanel = true;
                dragOffX = mx - panelX;
                dragOffY = my - panelY;
                return true;
            }
            if (activeBlock != null && hit(mx, my, pickerX + 4, pickerY + 4, 112, 8)) {
                draggingPicker = true;
                pickerDragOffX = mx - pickerX;
                pickerDragOffY = my - pickerY;
                return true;
            }
            if (activeBlock != null) {
                if (hitSv(mx, my)) { dragMode = DragMode.SV; applySvDrag(mx, my); commitColor(); return true; }
                if (hitHue(mx, my)) { dragMode = DragMode.HUE; applyHueDrag(mx, my); commitColor(); return true; }
                if (hitAlpha(mx, my)) { dragMode = DragMode.ALPHA; applyAlphaDrag(mx, my); commitColor(); return true; }
            }

            searchFocused = hit(mx, my, panelX + PAD, panelY + HEADER_H + 8, PANEL_W - PAD * 2, SEARCH_H);

            int footerTop = panelY + PANEL_H - FOOTER_H;
            int btnH = 26, btnW = 72;
            int btnY = footerTop + (FOOTER_H - btnH) / 2;
            if (hit(mx, my, panelX + PAD, btnY, btnW, btnH)) { save(); return true; }
            if (hit(mx, my, panelX + PANEL_W - PAD - btnW, btnY, btnW, btnH)) { dismiss(); return true; }

            if (hoverCenter >= 0 && hoverCenter < filteredBlocks.size()) {
                Block block = filteredBlocks.get(hoverCenter);
                if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    if (tempSelected.contains(block)) {
                        setActiveBlock(block);
                    } else {
                        tempSelected.add(block);
                        setActiveBlock(block);
                    }
                } else if (btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    tempSelected.remove(block);
                }
                return true;
            }
            if (hoverRight >= 0) {
                List<Block> picked = new ArrayList<>(tempSelected);
                if (hoverRight < picked.size()) {
                    Block block = picked.get(hoverRight);
                    if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        setActiveBlock(block);
                    } else if (btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        tempSelected.remove(block);
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double ox, double oy) {
        int mx = (int) click.x();
        int my = (int) click.y();
        if (draggingPanel) {
            panelX = mx - dragOffX;
            panelY = my - dragOffY;
            return true;
        }
        if (draggingPicker) {
            pickerX = mx - pickerDragOffX;
            pickerY = my - pickerDragOffY;
            return true;
        }
        if (dragMode != DragMode.NONE) {
            switch (dragMode) {
                case SV -> applySvDrag(mx, my);
                case HUE -> applyHueDrag(mx, my);
                case ALPHA -> applyAlphaDrag(mx, my);
                default -> { }
            }
            commitColor();
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
    public boolean mouseScrolled(double mx, double my, double ha, double va) {
        int bodyY = panelY + HEADER_H + 8 + SEARCH_H + 10;
        int centerX = panelX + PAD + LEFT_W + 8;
        int centerW = PANEL_W - PAD * 2 - LEFT_W - RIGHT_W - 16;
        int rightX = centerX + centerW + 8;
        if (hit((int) mx, (int) my, centerX, bodyY, centerW, panelY + PANEL_H - FOOTER_H - bodyY - 8)) {
            centerScroll -= (int) Math.signum(va);
            return true;
        }
        if (hit((int) mx, (int) my, rightX, bodyY, RIGHT_W, panelY + PANEL_H - FOOTER_H - bodyY - 8)) {
            rightScroll -= (int) Math.signum(va);
            return true;
        }
        return super.mouseScrolled(mx, my, ha, va);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { dismiss(); return true; }
        if (input.key() == GLFW.GLFW_KEY_ENTER) { save(); return true; }
        if (searchFocused && input.isPaste()) {
            searchQuery += getClipboardText();
            centerScroll = 0;
            return true;
        }
        if (searchFocused && input.key() == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            centerScroll = 0;
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (searchFocused) {
            searchQuery += Character.toString(input.codepoint());
            centerScroll = 0;
            return true;
        }
        return super.charTyped(input);
    }

    private void setActiveBlock(Block block) {
        activeBlock = block;
        Color existing = tempColors.get(block);
        if (existing != null) {
            pickerHSV = Color.RGBtoHSB(existing.getRed(), existing.getGreen(), existing.getBlue(), null);
            pickerAlpha = existing.getAlpha();
        } else {
            pickerHSV = new float[]{0.33f, 1f, 1f};
            pickerAlpha = 255;
        }
    }

    private void commitColor() {
        if (activeBlock == null) return;
        int rgb = Color.HSBtoRGB(pickerHSV[0], pickerHSV[1], pickerHSV[2]) & 0xFFFFFF;
        tempColors.put(activeBlock, new Color((pickerAlpha << 24) | rgb, true));
    }

    private void save() {
        commitColor();
        setting.setValue(new LinkedHashSet<>(tempSelected));
        if (espModule != null) {
            for (Block b : setting.getAvailableBlocks()) {
                espModule.setCustomBlockColor(b, tempColors.getOrDefault(b, null));
            }
        }
        dismiss();
    }

    private void dismiss() {
        client.setScreen(parent);
    }

    private String getClipboardText() {
        String clip = client.keyboard.getClipboard();
        if (clip == null) return "";
        StringBuilder b = new StringBuilder();
        clip.codePoints().filter(cp -> !Character.isISOControl(cp)).forEach(b::appendCodePoint);
        return b.toString();
    }

    private boolean hitSv(int mx, int my) { return hit(mx, my, pSvX, pSvY, 78, 58); }
    private boolean hitHue(int mx, int my) { return hit(mx, my, pHueX, pHueY, 10, 58); }
    private boolean hitAlpha(int mx, int my) { return hit(mx, my, pAlphaX, pAlphaY, 92, 8); }

    private void applySvDrag(int mx, int my) {
        pickerHSV[1] = clamp01((mx - pSvX) / 78f);
        pickerHSV[2] = clamp01(1f - (my - pSvY) / 58f);
    }

    private void applyHueDrag(int mx, int my) {
        pickerHSV[0] = clamp01((my - pHueY) / 58f);
    }

    private void applyAlphaDrag(int mx, int my) {
        pickerAlpha = (int) (clamp01((mx - pAlphaX) / 92f) * 255);
    }

    private static String trimLabel(String s, int maxW) {
        if (s == null) return "";
        if (s.length() > 18) s = s.substring(0, 17) + "…";
        return s;
    }

    private void drawCenter(DrawContext ctx, String label, int x, int y, int w, int h, int color) {
        ZenyaFont.draw(ctx, textRenderer, label, x + (w - ZenyaFont.width(textRenderer, label)) / 2,
                y + (h - textRenderer.fontHeight) / 2, color, false);
    }

    private static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float clamp01(float v) { return v < 0f ? 0f : Math.min(1f, v); }

    private static int blend(int base, int accent, float t) {
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int ar = (accent >> 16) & 0xFF, ag = (accent >> 8) & 0xFF, ab = accent & 0xFF;
        return 0xFF000000 | ((int) (br + (ar - br) * t) << 16) | ((int) (bg + (ag - bg) * t) << 8) | (int) (bb + (ab - bb) * t);
    }
}
