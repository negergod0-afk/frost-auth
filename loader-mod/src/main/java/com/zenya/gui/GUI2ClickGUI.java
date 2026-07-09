package com.zenya.gui;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.ModuleManager;
import com.zenya.setting.*;
import com.zenya.utils.ZenyaFont;
import com.zenya.utils.renderer.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.*;

/**
 * GUI 2 — classic floating-panel ClickGUI.
 * Draggable category panels, smooth animations, fully inline settings.
 */
public class GUI2ClickGUI extends Screen {

    // ── Layout ───────────────────────────────────────────────────────────
    private static final int PANEL_W    = 200;
    private static final int HEADER_H   = 36;
    private static final int MOD_H      = 26;
    private static final int SET_H      = 22;
    private static final int SLIDER_H   = 30;
    private static final int STORAGE_GRID_COLS = 6;
    private static final int STORAGE_CELL_H = 28;
    private static final int BLOCKS_GRID_COLS = 5;
    private static final int BLOCKS_GRID_CELL_H = 24;
    private static final int BLOCKS_GRID_MAX = 200;
    private static final int LIST_MAX   = 120;   // max px for blocks/mobs inline list
    private static final int MAX_VIS_H  = Integer.MAX_VALUE / 4;
    private static final int RADIUS     = 18; // Make corners more rounded and modern
    private static final int SEARCH_W   = 230;
    private static final int SEARCH_H   = 30;
    private static final int TGL_W      = 32; // Make toggle slightly wider
    private static final int TGL_H      = 16;

    // ── Static colours ────────────────────────────────────────────────────
    private static final int C_DIV      = 0xFF2A2A2A;
    private static final int C_TEXT     = 0xFFEEEEEE;
    private static final int C_MUTED    = 0xFF888888;
    private static final int C_DIM      = 0xFF505050;
    private static final int C_SUB      = 0xFF161616;

    private int dynSrchBg = 0xEE1A1A1A;
    private int dynSrchBd = 0xFF2A2A2A;
    private int dynTrack  = 0xFF363636;
    private int dynHover  = 0xFF252525;

    // Base BG values (same as GUI 1 bases).
    private static final int BASE_PANEL  = 0xFF1E2330;
    private static final int BASE_HEADER = 0xFF222737;

    private static int curPanel  = BASE_PANEL;
    private static int curHeader = BASE_HEADER;
    // Smoothly interpolated accent + tint floats.
    private static final float[] curAccRgb  = { 239, 68, 68 };
    private static final float[] curTintRgb = { 0, 0, 0 };
    private static int curAcc     = 0xFFEF4444;
    private static int curAccDim  = 0xFF991B1B;
    private static int curAccFaint= 0x33EF4444;
    private static int curOn      = 0xFF1A2538;
    private static int curSubSel  = 0xFF1E2D40;

    /** Called every render frame to smoothly lerp colours toward the current theme. */
    private void refreshTheme(float dt) {
        com.zenya.module.modules.client.Themes themes =
                com.zenya.module.modules.client.Themes.getInstance();

        int targetAcc  = 0xFFEF4444;
        int targetTint = 0;
        boolean rainbow = false;
        if (themes != null) {
            com.zenya.module.modules.client.Themes.Theme t =
                    com.zenya.module.modules.client.Themes.currentTheme();
            if (!t.name().equalsIgnoreCase("Dark")) {
                targetAcc  = t.accentArgb();
                targetTint = t.palette()[0];
            }
            rainbow = "Rainbow".equalsIgnoreCase(themes.selectedSetting().getValue());
        }

        // already cycles smoothly via HSV. Lerping in RGB space between adjacent
        // hues lags + produces ugly mid-colours, perceived as "ruckeln".
        float[] tA = { (targetAcc>>16)&0xFF, (targetAcc>>8)&0xFF, targetAcc&0xFF };
        if (rainbow) {
            curAccRgb[0] = tA[0]; curAccRgb[1] = tA[1]; curAccRgb[2] = tA[2];
        } else {
            for (int i=0;i<3;i++) curAccRgb[i] = exp(curAccRgb[i], tA[i], dt, 9f);
        }
        int aR=(int)curAccRgb[0], aG=(int)curAccRgb[1], aB=(int)curAccRgb[2];
        curAcc      = 0xFF000000|(aR<<16)|(aG<<8)|aB;
        curAccDim   = darken(curAcc, 0.45f);
        curAccFaint = (0x33<<24)|(curAcc&0x00FFFFFF);
        // active rows pop visually instead of looking like a black bar inside the panel.
        curOn       = blend(curPanel, curAcc, 0.18f);
        curSubSel   = blend(curPanel, curAcc, 0.14f);

        // Lerp BG tint.
        float[] tT = { (targetTint>>16)&0xFF, (targetTint>>8)&0xFF, targetTint&0xFF };
        for (int i=0;i<3;i++) curTintRgb[i] = exp(curTintRgb[i], tT[i], dt, 9f);
        int tintArgb = 0xFF000000|((int)curTintRgb[0]<<16)|((int)curTintRgb[1]<<8)|(int)curTintRgb[2];

        if (com.zenya.module.modules.client.ZenyaPlus.blackBackground()) {
            curPanel  = 0xFF111111;
            // text only, no seam between header and body.
            curHeader = curPanel;
        } else {
            curPanel  = blend(BASE_PANEL,  tintArgb, 0.42f);
            curHeader = curPanel;
        }
        
        // Apply background opacity
        int alpha = (com.zenya.module.modules.client.ZenyaPlus.getBackgroundARGB() >> 24) & 0xFF;
        curPanel = (alpha << 24) | (curPanel & 0x00FFFFFF);
        curHeader = (alpha << 24) | (curHeader & 0x00FFFFFF);

        // Dynamic UI colours derived from current panel/header.
        dynSrchBg = (0xEE << 24) | (darken(curPanel, 0.08f) & 0x00FFFFFF);
        dynSrchBd = curHeader;
        dynTrack  = blend(curPanel, 0xFF000000, 0.25f);
        dynHover  = blend(curPanel, 0xFFFFFFFF, 0.08f);
    }

    private static int acc()    { return curAcc; }
    private static int accDim() { return curAccDim; }

    private static int darken(int argb, float amount) {
        int r=(argb>>16)&0xFF, g=(argb>>8)&0xFF, b=argb&0xFF;
        return 0xFF000000|((int)(r*(1f-amount))<<16)|((int)(g*(1f-amount))<<8)|(int)(b*(1f-amount));
    }

    // ── Panel order ───────────────────────────────────────────────────────
    // Same categories as GUI 1 (CLIENT is shown as "Other", handled separately below).
    private static final Category[] ORDER = {
            Category.COMBAT, Category.DONUT, Category.SMPS, Category.MISC, Category.RENDER
    };
    private static final int OTHER_IDX = ORDER.length;

    // ── Per-panel state ───────────────────────────────────────────────────
    private static final int N = ORDER.length + 1; // +1 for "Other" (CLIENT)
    private final int[]     px       = new int[N];
    private final int[]     py       = new int[N];
    private final int[]     scroll   = new int[N];
    private final int[]     maxSc    = new int[N];
    private final boolean[] collapsed= new boolean[N];

    // saved positions (survive GUI close/reopen)
    private static final int[] SPX = new int[N];
    private static final int[] SPY = new int[N];
    private static boolean posInit = false;

    // ── GUI scale (0.3 – 1.0, saved across opens) ─────────────────────────
    private static float guiScale = 0.65f;
    private boolean scaleDragging = false;
    private boolean scaleBarDragging = false;   // dragging the whole bar
    private int     scaleBarDragStartX, scaleBarDragStartY;
    private static int scaleBarOX = 0, scaleBarOY = 0;
    private static final int SCALE_W = 160;
    private static final int SCALE_H = 28;
    private static boolean scaleCollapsed = false;
    private final int[] resizeBtnBounds = new int[4];

    // ── Drag ──────────────────────────────────────────────────────────────
    private int dragPanel = -1, dragOx, dragOy;

    // ── Module expand ─────────────────────────────────────────────────────
    private final Map<Module, Boolean> expanded = new HashMap<>();

    // ── Setting expand (for Mode, OptionSelect, Blocks, Mobs, OptionMulti)
    private Setting<?> openListSetting = null;   // inline radio/check list
    private String     listSearchBuf   = "";
    private boolean    listSearchFocus = false;

    // ── Colour picker ─────────────────────────────────────────────────────
    private Setting<Color> openColor = null;
    private final float[]  cHSV      = new float[3];
    private int cAlpha = 255;
    private enum CDrag { NONE, SV, HUE, ALPHA }
    private CDrag cDrag = CDrag.NONE;
    private int cSvX, cSvY, cSvW, cSvH;
    private int cHueX, cHueY, cHueW, cHueH;
    private int cAlX, cAlY, cAlW, cAlH;

    // ── String editing ────────────────────────────────────────────────────
    private Setting<?> strFocus  = null;
    private String     strBuf    = "";

    private Module bindListening = null;
    private Setting<Integer> bindListeningSetting = null;

    // ── Slider drag ───────────────────────────────────────────────────────
    private Setting<?> sliderDrag = null;
    private int slTrkX, slTrkW;

    // ── Search bar ────────────────────────────────────────────────────────
    private String  search      = "";
    private boolean searchFocus = false;
    private final int[] searchBounds  = new int[4];
    private final int[] searchGrip    = new int[4]; // drag handle on left
    private final int[] searchResizer = new int[4]; // resize handle on right
    // Saved position offset from screen centre (persist across opens).
    private static int  searchOX = 0, searchOY = 0;
    // Dynamic size (persist across opens).
    private static int  searchDynW = SEARCH_W;
    private static int  searchDynH = SEARCH_H;
    private boolean searchDragging = false;
    private boolean searchResizing = false;
    private int     searchDragStartX, searchDragStartY;
    private int     searchResizeStartX, searchResizeStartW;
    private int     searchResizeStartY, searchResizeStartH;

    // ── Theme overlay ──────────────────────────────────────────────────────
    private final int[]       themeBtnBounds    = new int[4];
    private final int[]       themesOverlayBounds = new int[4];
    private static boolean    themesOpen        = false;
    private static int        themesScroll      = 0;
    private final List<int[]>  themesHitR       = new ArrayList<>();
    private final List<Runnable> themesHitA     = new ArrayList<>();

    // ── Animation map ────────────────────────────────────────────────────
    private final Map<Object, float[]> anims = new HashMap<>();

    // ── Hit targets (rebuilt every frame) ────────────────────────────────
    private record HitTarget(Module mod, Setting<?> set, int kind) {}
    // kind: 1=modToggle 2=slider 3=bool 4=action 5=listItem 6=colorOpen 7=strField
    //       8=threshold-toggle 9=threshold-slider 10=confirmBool
    private final List<int[]>      hitR = new ArrayList<>();
    private final List<HitTarget>  hitH = new ArrayList<>();
    private final List<Object>     hitExtra = new ArrayList<>();  // for listItem: value

    // ── Open-animation ────────────────────────────────────────────────────
    private float openT  = 0f;
    private long  lastNs = 0L;

    // ── Constructor ──────────────────────────────────────────────────────
    public GUI2ClickGUI() { super(Text.literal("Frost Client")); }

    @Override public boolean shouldPause()                              { return false; }
    @Override public void renderBackground(DrawContext c,int a,int b,float d) {}
    @Override protected void applyBlur(DrawContext c)                  {}

    // ── Init ─────────────────────────────────────────────────────────────
    @Override
    public void init() {
        if (!posInit) {
            posInit = true;
            int gap = PANEL_W + 10;
            for (int i = 0; i < N; i++) {
                SPX[i] = 30 + i * gap;
                SPY[i] = 55;
            }
        }
        System.arraycopy(SPX, 0, px, 0, N);
        System.arraycopy(SPY, 0, py, 0, N);
        // Reset entrance animations so panels always fly in on open.
        openT  = 0f;
        lastNs = 0L;
        for (int i = 0; i < N; i++) {
            anims.remove("pentr" + i);
        }
    }

    // ── Render ───────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 1f/60f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        openT = exp(openT, 1f, dt, 10f);
        refreshTheme(dt);

        int dimMax = (int)(com.zenya.module.modules.client.ZenyaPlus.backgroundDim() * 255f) & 0xFF;
        int dimA = (int)(dimMax * ease(openT));
        ctx.fill(0, 0, this.width, this.height, dimA << 24);

        //  that setting was removed, so the slider now just persists locally for
        //  the lifetime of the screen.)

        // Scale slider + search bar rendered FIRST (screen-space, not scaled) so
        // bars bleed over module lists / setting popups that extend to the
        // right side of the screen. drawDeferredElements() forces the queued
        // text batches to flush NOW so the panels can paint over them; without
        // that flush, text renders at frame-end on top of everything.
        if (!themesOpen) {
            renderScaleBar(ctx, mx, my);
            renderSearchBar(ctx, mx, my);
            ctx.drawDeferredElements();
        }

        // Reset hover tracking BEFORE the panel loop — renderModule() sets it
        // back if the cursor is over a row this frame. Description pill is drawn
        // at the very end so it sees the up-to-date value.
        hoveredModule = null;

        float s = guiScale;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(s, s);
        int smx = Math.round(mx / s);
        int smy = Math.round(my / s);

        hitR.clear(); hitH.clear(); hitExtra.clear();

        for (int i = 0; i < N; i++) renderPanel(ctx, i, smx, smy, dt);

        // Theme overlay inside scale matrix so it respects GUI scale.
        if (themesOpen) renderThemesOverlay(ctx, smx, smy, dt);

        ctx.getMatrices().popMatrix();

        // Description pill — drawn AFTER the panels so it reads the freshly
        // updated hoveredModule, and in screen space (outside the scale matrix).
        if (!themesOpen) renderHoverDescription(ctx);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Panel
    // ─────────────────────────────────────────────────────────────────────
    private void renderPanel(DrawContext ctx, int idx, int mx, int my, float dt) {
        List<Module> mods = modsFor(idx);
        int bx = px[idx], by = py[idx];

        float entrT = anim("pentr"+idx, 1f, dt, 14f);
        float entrAlpha = ease(entrT);
        float entrSlide = (1f - ease(entrT)) * 10f;

        // ── Content height ────────────────────────────────────────────────
        int contentH = 0;
        if (!collapsed[idx]) {
            for (Module m : mods) {
                contentH += MOD_H;
                if (isExpanded(m)) contentH += settingsHeight(m);
            }
        }
        int targetVisH = Math.min(contentH, MAX_VIS_H);

        // Animate collapse/expand of the body height.
        float colT   = anim("col"+idx, collapsed[idx]?1f:0f, dt, 22f);
        int   visH   = (int)(targetVisH * (1f - ease(colT)));
        int   panelH = HEADER_H + visH;

        // Push matrix for entrance slide.
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(0, entrSlide);

        // Shadow (fades in with entrance).
        for (int s = 8; s >= 1; s--) {
            int a = (int)(14 * entrAlpha * ease(openT) * (1f - s/9f));
            RenderUtil.drawRoundedRect(ctx, bx-s, by-s, PANEL_W+s*2, panelH+s*2, RADIUS+s, a<<24, false);
        }

        // Accent rim glow when hovered.
        boolean panelHover = isH(mx,my, bx, by, PANEL_W, panelH);
        float panHovT = anim("phov"+idx, panelHover?1f:0f, dt, 16f);
        if (panHovT > 0.01f) {
            int rimA = (int)(30 * panHovT * entrAlpha);
            RenderUtil.drawRoundedRect(ctx, bx-1, by-1, PANEL_W+2, panelH+2, RADIUS+1, (rimA<<24)|(acc()&0x00FFFFFF), false);
        }

        // Header + body with correct corner rounding.
        int hdrColor = wA(curHeader, (int)(255*entrAlpha));
        int bdy      = wA(curPanel,  (int)(255*entrAlpha));
        // Flipped: bottom corners use the full radius, top stays squarer.
        float topR = 4f;
        float botR = RADIUS;
        if (visH == 0) {
            RenderUtil.drawRoundedRect(ctx, bx, by, PANEL_W, HEADER_H, topR, topR, botR, botR, false, hdrColor);
        } else {
            RenderUtil.drawRoundedRect(ctx, bx, by, PANEL_W, HEADER_H, topR, topR, 0f, 0f, false, hdrColor);
            RenderUtil.drawRoundedRect(ctx, bx, by + HEADER_H, PANEL_W, visH, 0f, 0f, botR, botR, false, bdy);
        }

        // Accent left bar.
        int accA = (int)(255 * entrAlpha);
        RenderUtil.drawRoundedRect(ctx, bx, by+7, 3, HEADER_H-14, 2f, wA(acc(), accA), false);

        // Category label.
        String lbl = (idx == OTHER_IDX ? "Other" : ORDER[idx].getName()).toUpperCase(Locale.ROOT);
        ZenyaFont.draw(ctx, textRenderer, lbl,
                bx+14, by+(HEADER_H - textRenderer.fontHeight)/2+1, wA(C_TEXT, (int)(255*entrAlpha)), false);

        boolean chevHover = isH(mx,my, bx+PANEL_W-32, by+4, 28, HEADER_H-8);
        float chevHovT = anim("chev"+idx, chevHover?1f:0f, dt, 16f);
        if (chevHovT > 0.01f) {
            int pillA = (int)(30 * chevHovT * entrAlpha);
            RenderUtil.drawRoundedRect(ctx, bx+PANEL_W-32, by+6, 28, HEADER_H-12, 4f,
                    (pillA<<24)|(acc()&0x00FFFFFF), false);
        }
        int chevCol = blend(wA(C_MUTED, (int)(200*entrAlpha)), wA(acc(), (int)(255*entrAlpha)), chevHovT);
        chevron(ctx, bx+PANEL_W-18, by+HEADER_H/2, colT, chevCol);

        if (visH > 0 && !mods.isEmpty()) {
            maxSc[idx]  = Math.max(0, contentH - targetVisH);
            scroll[idx] = clamp(scroll[idx], 0, maxSc[idx]);

            int top = by + HEADER_H, bot = top + visH;
            ctx.enableScissor(bx, top, bx+PANEL_W, bot);

            int cy = top - scroll[idx];
            for (Module m : mods) cy = renderModule(ctx, idx, m, bx, cy, mx, my, dt, top, bot);

            ctx.disableScissor();

            if (contentH > targetVisH && maxSc[idx] > 0) {
                float frac = targetVisH / (float) contentH;
                int sh  = Math.max(16, (int)(targetVisH * frac));
                int sy  = top + (int)((targetVisH - sh) * (float)scroll[idx] / maxSc[idx]);
                RenderUtil.drawRoundedRect(ctx, bx+PANEL_W-5, sy, 3, sh, 1.5f, wA(0xFFFFFF, (int)(100*entrAlpha)), false);
            }
        }

        ctx.getMatrices().popMatrix();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Module row
    // ─────────────────────────────────────────────────────────────────────
    private int renderModule(DrawContext ctx, int pidx, Module m, int bx, int cy,
                              int mx, int my, float dt, int top, int bot) {
        boolean vis    = cy+MOD_H > top && cy < bot;
        boolean en     = m.isEnabled();
        boolean exp    = isExpanded(m);
        boolean hover  = vis && isH(mx,my, bx, cy, PANEL_W, MOD_H) && sliderDrag==null && openColor==null;
        if (hover) hoveredModule = m;

        float hT = anim("mh"+m.getName(), hover?1f:0f, dt, 26f);
        float eT = anim("me"+m.getName(), en?1f:0f, dt, 20f);

        if (vis) {
            // scissor, so the panel's rounded bottom corners stay intact and we don't need
            // any post-row corner restoration hack.
            int bg = blend(blend(curPanel, dynHover, hT), curOn, eT);
            ctx.fill(bx, cy, bx + PANEL_W, cy + MOD_H, bg);
            if (hT > 0.01f) {
                int glowA = (int)(18 * ease(hT));
                ctx.fill(bx, cy, bx + PANEL_W, cy + MOD_H, (glowA<<24)|(acc()&0x00FFFFFF));
            }

            // Indicator dot.
            int dot = blend(C_DIM, acc(), eT);
            RenderUtil.drawRoundedRect(ctx, bx+10, cy+MOD_H/2-3, 6, 6, 3f, dot, false);

            // Name.
            int nc = blend(C_MUTED, C_TEXT, Math.max(hT, eT));
            ZenyaFont.draw(ctx, textRenderer, m.getName(), bx+22, cy+(MOD_H-textRenderer.fontHeight)/2+1, nc, false);

            // Toggle.
            float toggleT = anim("mt"+m.getName(), en?1f:0f, dt, 22f);
            toggle(ctx, bx+PANEL_W-TGL_W-8, cy+(MOD_H-TGL_H)/2, TGL_W, TGL_H, toggleT);

            // Hit: whole row (left=toggle, right-click=expand).
            hitR.add(new int[]{bx, cy, PANEL_W, MOD_H});
            hitH.add(new HitTarget(m, null, 1));
            hitExtra.add(null);
        }
        cy += MOD_H;

        // force the cursor to match the clipped height so modules below slide
        // smoothly with the reveal instead of jumping to full height instantly.
        float xT = anim("mx"+m.getName(), isExpanded(m)?1f:0f, dt, 24f);
        // 0.02 cuts off the long exponential tail so the panel doesn't keep painting
        // tiny slivers (and leaking RenderUtil-drawn toggles/colours which bypass the
        // scissor) for half a second after the user collapses a module.
        if (xT > 0.02f) {
            int fullH  = settingsHeight(m);
            int clipH  = Math.round(fullH * xT);
            int startY = cy;
            if (clipH > 0) {
                int clipBot = startY + clipH;
                int scTop = Math.max(startY, top);
                int scBot = Math.min(clipBot, bot);
                if (scTop < scBot) {
                    int settBg = blend(curPanel, 0xFF000000, 0.12f);
                    int sAlpha = (int) (255 * xT);
                    ctx.enableScissor(bx, scTop, bx + PANEL_W, scBot);
                    RenderUtil.drawRoundedRect(ctx, bx, startY, PANEL_W, fullH,
                            0f, 0f, 4f, 4f, false, wA(settBg, sAlpha));
                    // Pass the *scissor* bottom (not the animated content height) so per-row
                    // widgets (rounded rects, swatches) bypass the scissor and leak out below.
                    renderSettings(ctx, m, bx, startY, mx, my, dt, xT, startY, scBot);
                    ctx.disableScissor();
                }
            }
            // so the rest of the module list slides in lockstep with the reveal.
            cy = startY + clipH;
        }
        return cy;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Settings
    // ─────────────────────────────────────────────────────────────────────
    private int renderSettings(DrawContext ctx, Module m, int bx, int cy,
                                int mx, int my, float dt, float xT, int top, int bot) {
        int alpha = (int)(255 * ease(xT));
        int ix = bx + 4, iw = PANEL_W - 8;

        // ── Bind row (always first) ──────────────────────────────────────────
        cy = renderBindRow(ctx, m, ix, cy, iw, mx, my, dt, alpha, top, bot);

        for (Setting<?> s : m.getSettings()) {
            if (!s.isVisible()) continue;

            // ── Section ──────────────────────────────────────────────────
            if (s instanceof SectionSetting) {
                if (cy+SET_H > top && cy < bot) {
                    ctx.fill(ix+4, cy+SET_H/2, ix+iw-4, cy+SET_H/2+1, wA(C_DIV, alpha));
                    ZenyaFont.draw(ctx, textRenderer, s.getName(),
                            ix+8, cy+(SET_H-textRenderer.fontHeight)/2, wA(C_DIM, alpha), false);
                }
                cy += SET_H; continue;
            }

            // ── Action ───────────────────────────────────────────────────
            if (s instanceof ActionSetting as) {
                cy = renderActionRow(ctx, m, as, ix, cy, iw, mx, my, dt, alpha, top, bot);
                continue;
            }

            // ── ConfirmBoolean ────────────────────────────────────────────
            if (s instanceof ConfirmBooleanSetting cbs) {
                cy = renderBoolRow(ctx, m, cbs, ix, cy, iw, mx, my, dt, alpha, top, bot, 10);
                continue;
            }

            // ── Boolean ───────────────────────────────────────────────────
            if (s.getValue() instanceof Boolean) {
                @SuppressWarnings("unchecked") var bs = (Setting<Boolean>) s;
                cy = renderBoolRow(ctx, m, bs, ix, cy, iw, mx, my, dt, alpha, top, bot, 3);
                continue;
            }

            // ── ThresholdSetting ──────────────────────────────────────────
            if (s instanceof ThresholdSetting ts) {
                cy = renderThresholdRow(ctx, m, ts, ix, cy, iw, mx, my, dt, alpha, top, bot);
                continue;
            }

            // ── Double / Float slider ─────────────────────────────────────
            if (s.getValue() instanceof Double || s.getValue() instanceof Float) {
                cy = renderSliderRow(ctx, m, s, ix, cy, iw, mx, my, dt, alpha, top, bot);
                continue;
            }

            // ── Integer slider ────────────────────────────────────────────
            if (s.getValue() instanceof Integer && !(s instanceof ThresholdSetting)) {
                cy = renderIntSliderRow(ctx, m, s, ix, cy, iw, mx, my, dt, alpha, top, bot);
                continue;
            }

            // ── Color ─────────────────────────────────────────────────────
            if (s.getValue() instanceof Color) {
                @SuppressWarnings("unchecked") var cs = (Setting<Color>) s;
                cy = renderColorRow(ctx, m, cs, ix, cy, iw, mx, my, dt, alpha, top, bot);
                continue;
            }

            // ── ModeSetting → inline radio list ───────────────────────────
            if (s instanceof ModeSetting ms) {
                cy = renderModeRow(ctx, m, ms, ix, cy, iw, mx, my, dt, alpha, top, bot);
                continue;
            }

            // ── OptionSelectSetting → inline radio list ────────────────────
            if (s instanceof OptionSelectSetting oss) {
                cy = renderOptionSelectRow(ctx, m, oss, ix, cy, iw, mx, my, dt, alpha, top, bot);
                continue;
            }

            // ── OptionMultiSelectSetting → inline check list ───────────────
            if (s instanceof OptionMultiSelectSetting omss) {
                cy = renderOptionMultiRow(ctx, m, omss, ix, cy, iw, mx, my, dt, alpha, top, bot);
                continue;
            }

            // ── StorageBlocksSetting → grid picker + selected list ─────────
            if (s instanceof StorageBlocksSetting sbs) {
                cy = renderStorageBlocksRow(ctx, m, sbs, ix, cy, iw, mx, my, dt, alpha, top, bot);
                continue;
            }

            // ── BlocksSetting → inline block list ─────────────────────────
            if (s instanceof BlocksSetting bls) {
                cy = renderBlocksRow(ctx, m, bls, ix, cy, iw, mx, my, dt, alpha, top, bot);
                continue;
            }

            // ── MobsSetting → inline mob list ─────────────────────────────
            if (s instanceof MobsSetting mbs) {
                cy = renderMobsRow(ctx, m, mbs, ix, cy, iw, mx, my, dt, alpha, top, bot);
                continue;
            }

            // ── String ────────────────────────────────────────────────────
            if (s.getValue() instanceof String) {
                cy = renderStringRow(ctx, m, s, ix, cy, iw, mx, my, dt, alpha, top, bot);
                continue;
            }

            cy += SET_H;
        }

        cy += 1;
        return cy;
    }

    // ── Row renderers ─────────────────────────────────────────────────────

    /** Bind row rendered at the top of every module's expanded settings.
     *  Click → "..." (listening). Press a key → that key becomes the module's bind.
     *  Press ESC while listening → clears the bind. */
    private int renderBindRow(DrawContext ctx, Module m, int ix, int cy, int iw,
                               int mx, int my, float dt, int alpha, int top, int bot) {
        if (cy + SET_H > top && cy < bot) {
            boolean listening = bindListening == m;
            boolean h = isH(mx, my, ix, cy, iw, SET_H);
            float hT = anim("bindh" + m.getName(), h ? 1f : 0f, dt, 14f);
            int rowBg = blend(curPanel, dynHover, hT);
            if (listening) rowBg = blend(rowBg, acc(), 0.35f);
            RenderUtil.drawRoundedRect(ctx, ix, cy, iw, SET_H - 2, 5f, wA(rowBg, alpha), false);

            // Label.
            ZenyaFont.draw(ctx, textRenderer, "Bind",
                    ix + 6, cy + (SET_H - textRenderer.fontHeight) / 2,
                    wA(C_TEXT, alpha), false);

            // Right-aligned key pill.
            String keyText = listening ? "..."
                    : (m.getBind() == 0 ? "None" : ClickGUI.getKeyDisplayNameStatic(m.getBind()));
            int kw = ZenyaFont.width(textRenderer, keyText) + 12;
            int kh = SET_H - 8;
            int kx = ix + iw - kw - 6;
            int ky = cy + (SET_H - kh) / 2;
            int pillCol = listening ? wA(acc(), alpha)
                                    : wA(blend(curPanel, 0xFF000000, 0.35f), alpha);
            RenderUtil.drawRoundedRect(ctx, kx, ky, kw, kh, kh / 2f, pillCol, false);
            int kTextCol = listening ? wA(0xFFFFFFFF, alpha) : wA(acc(), alpha);
            ZenyaFont.draw(ctx, textRenderer, keyText,
                    kx + (kw - ZenyaFont.width(textRenderer, keyText)) / 2,
                    ky + (kh - textRenderer.fontHeight) / 2 + 1,
                    kTextCol, false);

            hitR.add(new int[]{ix, cy, iw, SET_H});
            hitH.add(new HitTarget(m, null, 200));
            hitExtra.add(null);
        }
        return cy + SET_H;
    }

    private int renderActionRow(DrawContext ctx, Module m, ActionSetting s,
                                 int ix, int cy, int iw, int mx, int my, float dt,
                                 int alpha, int top, int bot) {
        if (cy+SET_H > top && cy < bot) {
            boolean h = isH(mx,my, ix, cy, iw, SET_H);
            float hT  = anim("ah"+m.getName()+s.getName(), h?1f:0f, dt, 14f);
            RenderUtil.drawRoundedRect(ctx, ix, cy, iw, SET_H-2, 5f, wA(blend(curPanel,dynHover,hT),alpha), false);
            ZenyaFont.draw(ctx, textRenderer, s.getName(),
                    ix+6, cy+(SET_H-textRenderer.fontHeight)/2, wA(acc(),alpha), false);
            hitR.add(new int[]{ix, cy, iw, SET_H}); hitH.add(new HitTarget(m,s,4)); hitExtra.add(null);
        }
        return cy + SET_H;
    }

    private int renderBoolRow(DrawContext ctx, Module m, Setting<Boolean> s,
                               int ix, int cy, int iw, int mx, int my, float dt,
                               int alpha, int top, int bot, int kind) {
        if (cy+SET_H > top && cy < bot) {
            boolean val = Boolean.TRUE.equals(s.getValue());
            float   vT  = anim("bv"+m.getName()+s.getName(), val?1f:0f, dt, 12f);
            boolean h   = isH(mx,my, ix, cy, iw, SET_H);
            float   hT  = anim("bh"+m.getName()+s.getName(), h?1f:0f, dt, 14f);
            RenderUtil.drawRoundedRect(ctx, ix, cy, iw, SET_H-2, 5f, wA(blend(curPanel,dynHover,hT),alpha), false);
            ZenyaFont.draw(ctx, textRenderer, s.getDisplayName(),
                    ix+6, cy+(SET_H-textRenderer.fontHeight)/2, wA(C_MUTED,alpha), false);
            toggle(ctx, ix+iw-TGL_W-4, cy+(SET_H-TGL_H)/2, TGL_W, TGL_H, vT);
            hitR.add(new int[]{ix, cy, iw, SET_H}); hitH.add(new HitTarget(m,s,kind)); hitExtra.add(null);
        }
        return cy + SET_H;
    }

    private int renderThresholdRow(DrawContext ctx, Module m, ThresholdSetting ts,
                                    int ix, int cy, int iw, int mx, int my, float dt,
                                    int alpha, int top, int bot) {
        if (cy+SET_H > top && cy < bot) {
            float enT = anim("ten"+m.getName()+ts.getName(), ts.isEnabled()?1f:0f, dt, 12f);
            boolean h = isH(mx,my, ix, cy, iw, SET_H);
            float hT  = anim("thh"+m.getName()+ts.getName(), h?1f:0f, dt, 14f);
            RenderUtil.drawRoundedRect(ctx, ix, cy, iw, SET_H-2, 5f, wA(blend(curPanel,dynHover,hT),alpha), false);
            // Toggle.
            toggle(ctx, ix+4, cy+(SET_H-TGL_H)/2, TGL_W, TGL_H, enT);
            // Name.
            ZenyaFont.draw(ctx, textRenderer, ts.getDisplayName(),
                    ix+TGL_W+10, cy+(SET_H-textRenderer.fontHeight)/2, wA(C_MUTED,alpha), false);
            hitR.add(new int[]{ix, cy, TGL_W+8, SET_H}); hitH.add(new HitTarget(m,ts,8)); hitExtra.add(null);
        }
        cy += SET_H;
        // Slider for the integer value (always shown when threshold is enabled).
        if (ts.isEnabled() && cy+SET_H > top && cy < bot) {
            cy = renderRawSlider(ctx, m, ts, ix, cy, iw, mx, my, dt, alpha, top, bot,
                    ts.getValue(), ts.getMin(), ts.getMax());
        } else if (ts.isEnabled()) {
            cy += SET_H;
        }
        return cy;
    }

    private int renderSliderRow(DrawContext ctx, Module m, Setting<?> s,
                                 int ix, int cy, int iw, int mx, int my, float dt,
                                 int alpha, int top, int bot) {
        if (cy+SLIDER_H <= top || cy >= bot) { return cy+SLIDER_H; }
        double val = s.getValue() instanceof Double d ? d : ((Float)s.getValue()).doubleValue();
        double mn  = s.getMin()  instanceof Double d ? d : s.getMin()  instanceof Float f ? f.doubleValue() : 0.0;
        double mx2 = s.getMax()  instanceof Double d ? d : s.getMax()  instanceof Float f ? f.doubleValue() : 1.0;
        return renderRawSlider(ctx, m, s, ix, cy, iw, mx, my, dt, alpha, top, bot, val, mn, mx2);
    }

    private int renderIntSliderRow(DrawContext ctx, Module m, Setting<?> s,
                                    int ix, int cy, int iw, int mx, int my, float dt,
                                    int alpha, int top, int bot) {
        if (cy+SLIDER_H <= top || cy >= bot) { return cy+SLIDER_H; }
        
        // Handle "Bind" as a keybind setting
        if (s.getName().toLowerCase(Locale.ROOT).contains("bind")) {
            boolean listening = bindListeningSetting == s;
            boolean h = isH(mx, my, ix, cy, iw, SET_H);
            float hT = anim("bindh" + m.getName() + s.getName(), h ? 1f : 0f, dt, 14f);
            int rowBg = blend(curPanel, dynHover, hT);
            if (listening) rowBg = blend(rowBg, acc(), 0.35f);
            RenderUtil.drawRoundedRect(ctx, ix, cy, iw, SET_H - 2, 5f, wA(rowBg, alpha), false);

            ZenyaFont.draw(ctx, textRenderer, s.getDisplayName(),
                    ix + 6, cy + (SET_H - textRenderer.fontHeight) / 2,
                    wA(C_TEXT, alpha), false);

            String keyText = listening ? "..."
                    : ((Integer)s.getValue() == 0 ? "None" : ClickGUI.getKeyDisplayNameStatic((Integer)s.getValue()));
            int kw = ZenyaFont.width(textRenderer, keyText) + 12;
            int kh = SET_H - 8;
            int kx = ix + iw - kw - 6;
            int ky = cy + (SET_H - kh) / 2;
            int pillCol = listening ? wA(acc(), alpha)
                                    : wA(blend(curPanel, 0xFF000000, 0.35f), alpha);
            RenderUtil.drawRoundedRect(ctx, kx, ky, kw, kh, kh / 2f, pillCol, false);
            int kTextCol = listening ? wA(0xFFFFFFFF, alpha) : wA(acc(), alpha);
            ZenyaFont.draw(ctx, textRenderer, keyText,
                    kx + (kw - ZenyaFont.width(textRenderer, keyText)) / 2,
                    ky + (kh - textRenderer.fontHeight) / 2 + 1,
                    kTextCol, false);

            hitR.add(new int[]{ix, cy, iw, SET_H});
            hitH.add(new HitTarget(m, s, 201)); // special kind for setting-bind
            hitExtra.add(null);
            return cy + SET_H;
        }

        double val = ((Integer)s.getValue()).doubleValue();
        double mn  = s.getMin()  instanceof Integer i ? i.doubleValue() : 0.0;
        double mx2 = s.getMax()  instanceof Integer i ? i.doubleValue() : 100.0;
        return renderRawSlider(ctx, m, s, ix, cy, iw, mx, my, dt, alpha, top, bot, val, mn, mx2);
    }

    private int renderRawSlider(DrawContext ctx, Module m, Setting<?> s,
                                 int ix, int cy, int iw, int mx, int my, float dt,
                                 int alpha, int top, int bot,
                                 double val, Object mnObj, Object mxObj) {
        double mn  = mnObj instanceof Number n ? n.doubleValue() : 0.0;
        double mx2 = mxObj instanceof Number n ? n.doubleValue() : 1.0;
        double frac = mx2 > mn ? Math.max(0.0, Math.min(1.0, (val-mn)/(mx2-mn))) : 0.0;

        boolean drag = sliderDrag == s;
        boolean h    = drag || isH(mx,my, ix, cy, iw, SLIDER_H);
        float   hT   = anim("slh"+m.getName()+s.getName(), h?1f:0f, dt, 14f);

        RenderUtil.drawRoundedRect(ctx, ix, cy, iw, SLIDER_H-2, 3f, wA(blend(curPanel,dynHover,hT),alpha), false);
        // Label + value.
        String valStr = fmtNum(val);
        int vw = ZenyaFont.width(textRenderer, valStr);
        ZenyaFont.draw(ctx, textRenderer, s.getDisplayName(),
                ix+6, cy+5, wA(C_MUTED,alpha), false);
        ZenyaFont.draw(ctx, textRenderer, valStr,
                ix+iw-vw-6, cy+5, wA(C_TEXT,alpha), false);
        // Track.
        int tx = ix+6, ty = cy+19, tw = iw-12, th = 3;
        RenderUtil.drawRoundedRect(ctx, tx, ty, tw, th, 1.5f, wA(dynTrack,alpha), false);
        int fw = (int)(tw*frac);
        if (fw>0) RenderUtil.drawRoundedRect(ctx, tx, ty, fw, th, 1.5f, wA(acc(),alpha), false);
        // Knob.
        int kx = tx+fw, kr = 5;
        RenderUtil.drawRoundedRect(ctx, kx-kr, ty-kr+th/2, kr*2, kr*2, kr, wA(0xFFFFFFFF,alpha), false);

        slTrkX = tx; slTrkW = tw;
        hitR.add(new int[]{ix, cy, iw, SLIDER_H, tx, ty, tw}); hitH.add(new HitTarget(m,s,2)); hitExtra.add(null);
        return cy + SLIDER_H;
    }

    private int renderColorRow(DrawContext ctx, Module m, Setting<Color> s,
                                int ix, int cy, int iw, int mx, int my, float dt,
                                int alpha, int top, int bot) {
        boolean open = openColor == s;
        if (cy+SET_H > top && cy < bot) {
            boolean h = isH(mx,my, ix, cy, iw, SET_H);
            float hT  = anim("ch"+m.getName()+s.getName(), h?1f:0f, dt, 14f);
            RenderUtil.drawRoundedRect(ctx, ix, cy, iw, SET_H-2, 5f, wA(blend(curPanel,dynHover,hT),alpha), false);
            ZenyaFont.draw(ctx, textRenderer, s.getDisplayName(),
                    ix+6, cy+(SET_H-textRenderer.fontHeight)/2, wA(C_MUTED,alpha), false);
            Color cv = s.getValue();
            if (cv != null) {
                int sw2 = (cv.getAlpha()<<24)|(cv.getRed()<<16)|(cv.getGreen()<<8)|cv.getBlue();
                // White "selected" border first (slightly larger), THEN the coloured swatch on
                // every time the picker was open.
                if (open) RenderUtil.drawRoundedRect(ctx, ix+iw-23, cy+3, 18, SET_H-8, 2f, 0xFFFFFFFF, false);
                RenderUtil.drawRoundedRect(ctx, ix+iw-22, cy+4, 16, SET_H-10, 2f, sw2, false);
            }
            hitR.add(new int[]{ix, cy, iw, SET_H}); hitH.add(new HitTarget(m,s,6)); hitExtra.add(null);
        }
        cy += SET_H;

        if (open) {
            cy = renderColorPicker(ctx, s, ix, cy, iw, alpha, mx, my);
        }
        return cy;
    }

    // ── Color picker constants ─────────────────────────────────────────
    private static final int CP_PAD    = 6;   // inner padding
    private static final int CP_SV_H   = 68;  // saturation-value area height
    private static final int CP_BAR_H  = 10;  // hue & alpha bar height
    private static final int CP_GAP    = 6;   // gap between sections
    private static final int CP_PREV_H = 18;  // preview row height
    // Total extra height claimed by the picker (must match settingsHeight):
    // PAD + SV + GAP + BAR + GAP + BAR + GAP + PREVIEW + PAD = 6+68+6+10+6+10+6+18+6 = 136
    private static final int CP_TOTAL  = CP_PAD + CP_SV_H + CP_GAP + CP_BAR_H + CP_GAP
                                       + CP_BAR_H + CP_GAP + CP_PREV_H + CP_PAD;

    private int renderColorPicker(DrawContext ctx, Setting<Color> s,
                                   int ix, int cy, int iw, int alpha, int mx, int my) {
        Color cv = s.getValue();
        if (cv != null && cDrag == CDrag.NONE) {
            float[] h = Color.RGBtoHSB(cv.getRed(), cv.getGreen(), cv.getBlue(), null);
            cHSV[0] = h[0]; cHSV[1] = h[1]; cHSV[2] = h[2];
            cAlpha  = cv.getAlpha();
        }

        // ── Container background ──────────────────────────────────────────
        int containerBg = blend(curPanel, 0xFF000000, 0.22f);
        RenderUtil.drawRoundedRect(ctx, ix, cy, iw, CP_TOTAL, 6f, wA(containerBg, alpha), false);

        int pad = CP_PAD;
        int innerX = ix + pad;
        int innerW = iw - pad * 2;
        int curY = cy + pad;

        // ── SV field ──────────────────────────────────────────────────────
        int svW = innerW, svH = CP_SV_H;
        int svX = innerX, svY = curY;
        cSvX = svX; cSvY = svY; cSvW = svW; cSvH = svH;

        // Draw SV gradient column by column
        for (int xi = 0; xi < svW; xi++) {
            float sat = xi / (float) svW;
            int top    = Color.HSBtoRGB(cHSV[0], sat, 1f) | 0xFF000000;
            int bottom = 0xFF000000;
            ctx.fillGradient(svX + xi, svY, svX + xi + 1, svY + svH, top, bottom);
        }

        // SV cursor: circle knob with outline for visibility on any colour
        int kx2 = svX + (int)(cHSV[1] * svW);
        int ky2 = svY + (int)((1f - cHSV[2]) * svH);
        int knobR = 4;
        RenderUtil.drawRoundedRect(ctx, kx2 - knobR - 1, ky2 - knobR - 1,
                (knobR + 1) * 2, (knobR + 1) * 2, knobR + 1, 0xCC000000, false);
        RenderUtil.drawRoundedRect(ctx, kx2 - knobR, ky2 - knobR,
                knobR * 2, knobR * 2, knobR, 0xFFFFFFFF, false);

        curY += svH + CP_GAP;

        // ── Hue bar ───────────────────────────────────────────────────────
        cHueX = innerX; cHueY = curY; cHueW = innerW; cHueH = CP_BAR_H;

        // Rounded hue bar via segments
        int hueSegs = Math.max(1, cHueW);
        for (int i = 0; i < hueSegs; i++) {
            int c = Color.HSBtoRGB(i / (float) hueSegs, 1f, 1f) | 0xFF000000;
            ctx.fill(cHueX + i, cHueY + 1, cHueX + i + 1, cHueY + cHueH - 1, c);
        }
        // Round caps on the hue bar
        int hcapR = (cHueH - 2) / 2;
        int hStartCol = Color.HSBtoRGB(0f, 1f, 1f) | 0xFF000000;
        int hEndCol = Color.HSBtoRGB(1f, 1f, 1f) | 0xFF000000;
        RenderUtil.drawRoundedRect(ctx, cHueX, cHueY + 1, hcapR * 2, cHueH - 2, hcapR, hStartCol, false);
        RenderUtil.drawRoundedRect(ctx, cHueX + cHueW - hcapR * 2, cHueY + 1, hcapR * 2, cHueH - 2, hcapR, hEndCol, false);

        // Hue handle: circular knob
        int hkx = cHueX + (int)(cHSV[0] * cHueW);
        hkx = Math.max(cHueX + hcapR, Math.min(cHueX + cHueW - hcapR, hkx));
        int hKnobR = cHueH / 2 + 1;
        RenderUtil.drawRoundedRect(ctx, hkx - hKnobR, cHueY + cHueH / 2 - hKnobR,
                hKnobR * 2, hKnobR * 2, hKnobR, 0xFFFFFFFF, false);
        // inner coloured dot
        int hueDotCol = Color.HSBtoRGB(cHSV[0], 1f, 1f) | 0xFF000000;
        RenderUtil.drawRoundedRect(ctx, hkx - hKnobR + 2, cHueY + cHueH / 2 - hKnobR + 2,
                hKnobR * 2 - 4, hKnobR * 2 - 4, hKnobR - 2, hueDotCol, false);

        curY += cHueH + CP_GAP;

        // ── Alpha bar ─────────────────────────────────────────────────────
        cAlX = innerX; cAlY = curY; cAlW = innerW; cAlH = CP_BAR_H;

        // Checkerboard background (for transparency preview)
        int sq = 3;
        for (int xi = 0; xi < cAlW; xi += sq) {
            for (int yi = 0; yi < cAlH - 2; yi += sq) {
                boolean dark = ((xi / sq) + (yi / sq)) % 2 == 0;
                int bgc = dark ? 0xFF3A3A3A : 0xFF555555;
                int x2 = Math.min(cAlX + xi + sq, cAlX + cAlW);
                int y2 = Math.min(cAlY + 1 + yi + sq, cAlY + cAlH - 1);
                ctx.fill(cAlX + xi, cAlY + 1 + yi, x2, y2, bgc);
            }
        }
        // Alpha gradient overlay
        int pr = Color.HSBtoRGB(cHSV[0], cHSV[1], cHSV[2]) & 0x00FFFFFF;
        for (int i = 0; i < cAlW; i++) {
            int a = (int)(i / (float) cAlW * 255);
            ctx.fill(cAlX + i, cAlY + 1, cAlX + i + 1, cAlY + cAlH - 1, (a << 24) | pr);
        }

        // Alpha handle: circular knob
        int akx = cAlX + (int)(cAlpha / 255f * cAlW);
        akx = Math.max(cAlX + hcapR, Math.min(cAlX + cAlW - hcapR, akx));
        int aKnobR = cAlH / 2 + 1;
        RenderUtil.drawRoundedRect(ctx, akx - aKnobR, cAlY + cAlH / 2 - aKnobR,
                aKnobR * 2, aKnobR * 2, aKnobR, 0xFFFFFFFF, false);
        // inner coloured dot with current alpha
        int alphaDot = (cAlpha << 24) | pr;
        RenderUtil.drawRoundedRect(ctx, akx - aKnobR + 2, cAlY + cAlH / 2 - aKnobR + 2,
                aKnobR * 2 - 4, aKnobR * 2 - 4, aKnobR - 2, alphaDot, false);

        curY += cAlH + CP_GAP;

        // ── Preview swatch + hex code ─────────────────────────────────────
        int prevH = CP_PREV_H;
        int swatchSz = prevH - 4;
        int swatchCol = (cAlpha << 24) | pr;
        RenderUtil.drawRoundedRect(ctx, innerX, curY + 2, swatchSz, swatchSz, 3f, 0xFF2A2A2A, false);
        RenderUtil.drawRoundedRect(ctx, innerX + 1, curY + 3, swatchSz - 2, swatchSz - 2, 2f, swatchCol, false);

        // Hex label
        String hex = String.format("#%02X%02X%02X", (pr >> 16) & 0xFF, (pr >> 8) & 0xFF, pr & 0xFF);
        if (cAlpha < 255) hex = String.format("#%02X%s", cAlpha, hex.substring(1));
        ZenyaFont.draw(ctx, textRenderer, hex,
                innerX + swatchSz + 6, curY + (prevH - textRenderer.fontHeight) / 2 + 1,
                wA(C_TEXT, alpha), false);

        // Alpha percentage on the right
        String alphaPercent = (int)(cAlpha / 255f * 100f) + "%";
        int apW = ZenyaFont.width(textRenderer, alphaPercent);
        ZenyaFont.draw(ctx, textRenderer, alphaPercent,
                innerX + innerW - apW, curY + (prevH - textRenderer.fontHeight) / 2 + 1,
                wA(C_MUTED, alpha), false);

        cy += CP_TOTAL;
        return cy;
    }

    // ── Mode / OptionSelect (inline radio list) ───────────────────────────
    private int renderModeRow(DrawContext ctx, Module m, ModeSetting ms,
                               int ix, int cy, int iw, int mx, int my, float dt,
                               int alpha, int top, int bot) {
        boolean open = openListSetting == ms;
        cy = renderListHeader(ctx, m, ms, ms.getValue(), ix, cy, iw, mx, my, dt, alpha, top, bot);
        if (open) {
            for (String mode : ms.getModes()) {
                if (cy+SET_H > top && cy < bot) {
                    boolean sel = mode.equalsIgnoreCase(ms.getValue());
                    cy = renderRadioItem(ctx, m, ms, mode, mode, sel, ix, cy, iw, mx, my, dt, alpha);
                } else { cy += SET_H; }
            }
        }
        return cy;
    }

    private int renderOptionSelectRow(DrawContext ctx, Module m, OptionSelectSetting oss,
                                       int ix, int cy, int iw, int mx, int my, float dt,
                                       int alpha, int top, int bot) {
        boolean open = openListSetting == oss;
        String summary = oss.getSummary();
        cy = renderListHeader(ctx, m, oss, summary, ix, cy, iw, mx, my, dt, alpha, top, bot);
        if (open) {
            for (var opt : oss.getOptions()) {
                if (cy+SET_H > top && cy < bot) {
                    boolean sel = oss.isSelected(opt);
                    cy = renderRadioItem(ctx, m, oss, opt.label(), opt.value(), sel, ix, cy, iw, mx, my, dt, alpha);
                } else { cy += SET_H; }
            }
        }
        return cy;
    }

    private int renderOptionMultiRow(DrawContext ctx, Module m, OptionMultiSelectSetting omss,
                                      int ix, int cy, int iw, int mx, int my, float dt,
                                      int alpha, int top, int bot) {
        boolean open = openListSetting == omss;
        String summary = omss.getSummary();
        cy = renderListHeader(ctx, m, omss, summary, ix, cy, iw, mx, my, dt, alpha, top, bot);
        if (open) {
            for (var opt : omss.getOptions()) {
                if (cy+SET_H > top && cy < bot) {
                    boolean sel = omss.isSelected(opt);
                    cy = renderCheckItem(ctx, m, omss, opt.label(), opt.value(), sel, ix, cy, iw, mx, my, dt, alpha);
                } else { cy += SET_H; }
            }
        }
        return cy;
    }

    // ── StorageBlocksSetting → grid picker + Selected Blocks section ──────
    private int renderStorageBlocksRow(DrawContext ctx, Module m, StorageBlocksSetting sbs,
                                        int ix, int cy, int iw, int mx, int my, float dt,
                                        int alpha, int top, int bot) {
        int selectedCount = sbs.getSelectedEntries().size();
        String summary = selectedCount == 0 ? "None" : selectedCount + " selected";
        cy = renderListHeader(ctx, m, sbs, summary, ix, cy, iw, mx, my, dt, alpha, top, bot);
        if (openListSetting != sbs) return cy;

        // as a tooltip. Selection = accent border. Click toggles.
        List<StorageBlocksSetting.Entry> opts = sbs.getOptions();
        int cols = STORAGE_GRID_COLS;
        int gap = 3;
        int gridX = ix + 2;
        int cellW = (iw - 4 - gap * (cols - 1)) / cols;
        int rowH = STORAGE_CELL_H + gap;
        int accent = acc();
        StorageBlocksSetting.Entry hoveredEntry = null;
        int hoveredX = 0, hoveredY = 0;

        for (int i = 0; i < opts.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = gridX + col * (cellW + gap);
            int cellY = cy + row * rowH;
            if (cellY + STORAGE_CELL_H <= top || cellY >= bot) continue;
            StorageBlocksSetting.Entry entry = opts.get(i);
            boolean selected = sbs.isSelected(entry.value());
            boolean h = isH(mx, my, cx, cellY, cellW, STORAGE_CELL_H);
            float hT = anim("sbg" + m.getName() + entry.value(), h ? 1f : 0f, dt, 14f);
            float sT = anim("sbg-sel" + m.getName() + entry.value(), selected ? 1f : 0f, dt, 14f);

            // Background: panel tone + hover lift; soft accent tint when selected
            int bg = blend(curPanel, dynHover, hT);
            if (sT > 0f) {
                int accentTint = (accent & 0x00FFFFFF) | 0x33000000;
                bg = blend(bg, accentTint, sT);
            }
            RenderUtil.drawRoundedRect(ctx, cx, cellY, cellW, STORAGE_CELL_H, 5f, wA(bg, alpha), false);

            // Accent border when selected
            if (sT > 0.01f) {
                int border = wA(accent, (int)(alpha * sT));
                int sw = 1;
                ctx.fill(cx, cellY, cx + cellW, cellY + sw, border);
                ctx.fill(cx, cellY + STORAGE_CELL_H - sw, cx + cellW, cellY + STORAGE_CELL_H, border);
                ctx.fill(cx, cellY, cx + sw, cellY + STORAGE_CELL_H, border);
                ctx.fill(cx + cellW - sw, cellY, cx + cellW, cellY + STORAGE_CELL_H, border);
            }

            int iconX = cx + (cellW - 16) / 2;
            int iconY = cellY + (STORAGE_CELL_H - 16) / 2;
            ctx.drawItem(entry.icon(), iconX, iconY);

            // Capture hover for tooltip rendered AFTER the loop (so it draws on top)
            if (h) { hoveredEntry = entry; hoveredX = mx; hoveredY = my; }

            hitR.add(new int[]{cx, cellY, cellW, STORAGE_CELL_H});
            hitH.add(new HitTarget(m, sbs, 300));
            hitExtra.add(entry.value());
        }
        int rows = (opts.size() + cols - 1) / cols;
        cy += rows * rowH;

        if (selectedCount == 0) return cy;

        // "Selected Blocks" subheader.
        if (cy + SET_H > top && cy < bot) {
            ZenyaFont.draw(ctx, textRenderer, "Selected Blocks",
                    ix + 6, cy + (SET_H - textRenderer.fontHeight) / 2,
                    wA(acc(), alpha), false);
        }
        cy += SET_H;

        for (StorageBlocksSetting.Entry entry : sbs.getSelectedEntries()) {
            Setting<Color> colorSet = sbs.colorSettingFor(entry.value());
            boolean pickerOpen = openColor == colorSet;
            if (cy + SET_H > top && cy < bot) {
                boolean h = isH(mx, my, ix, cy, iw, SET_H);
                float hT = anim("sbs" + m.getName() + entry.value(), h ? 1f : 0f, dt, 14f);
                RenderUtil.drawRoundedRect(ctx, ix, cy, iw, SET_H - 2, 5f, wA(blend(curPanel, dynHover, hT), alpha), false);
                ctx.drawItem(entry.icon(), ix + 4, cy + (SET_H - 16) / 2 - 1);
                ZenyaFont.draw(ctx, textRenderer, entry.label(),
                        ix + 24, cy + (SET_H - textRenderer.fontHeight) / 2,
                        wA(C_MUTED, alpha), false);
                Color cv = sbs.getColor(entry.value());
                int swatchArgb = (cv.getAlpha() << 24) | (cv.getRed() << 16) | (cv.getGreen() << 8) | cv.getBlue();
                RenderUtil.drawRoundedRect(ctx, ix + iw - 22, cy + 4, 16, SET_H - 10, 2f, swatchArgb, false);
                if (pickerOpen) {
                    RenderUtil.drawRoundedRect(ctx, ix + iw - 23, cy + 3, 18, SET_H - 8, 2f, 0xFFFFFFFF, false);
                }
                hitR.add(new int[]{ix, cy, iw, SET_H});
                hitH.add(new HitTarget(m, sbs, 301));
                hitExtra.add(entry.value());
            }
            cy += SET_H;
            if (pickerOpen) {
                cy = renderColorPicker(ctx, colorSet, ix, cy, iw, alpha, mx, my);
            }
        }
        return cy;
    }

    private int renderBlocksRow(DrawContext ctx, Module m, BlocksSetting bs,
                                 int ix, int cy, int iw, int mx, int my, float dt,
                                 int alpha, int top, int bot) {
        // Clicking the header routes through case 100 below, which opens that screen.
        return renderListHeader(ctx, m, bs, bs.getSummary(), ix, cy, iw, mx, my, dt, alpha, top, bot);
    }

    private int renderMobsRow(DrawContext ctx, Module m, MobsSetting ms,
                               int ix, int cy, int iw, int mx, int my, float dt,
                               int alpha, int top, int bot) {
        return renderListHeader(ctx, m, ms, ms.getSummary(), ix, cy, iw, mx, my, dt, alpha, top, bot);
    }

    private int renderMobsRowLegacyUnused(DrawContext ctx, Module m, MobsSetting ms,
                               int ix, int cy, int iw, int mx, int my, float dt,
                               int alpha, int top, int bot) {
        boolean open = openListSetting == ms;
        cy = renderListHeader(ctx, m, ms, ms.getSummary(), ix, cy, iw, mx, my, dt, alpha, top, bot);
        if (open) {
            cy = renderListSearch(ctx, m, ms, ix, cy, iw, mx, my, alpha, top, bot);
            List<EntityType<?>> mobs = listSearchBuf.isEmpty() ? ms.getAvailableMobs()
                    : ms.filter(listSearchBuf);
            int shown = 0;
            for (EntityType<?> mob : mobs) {
                if (shown++ > 80) break;
                if (cy+SET_H > top && cy < bot) {
                    boolean sel = ms.contains(mob);
                    cy = renderCheckItem(ctx, m, ms, ms.getDisplayName(mob), mob, sel, ix, cy, iw, mx, my, dt, alpha);
                } else { cy += SET_H; }
            }
        }
        return cy;
    }

    private int renderStringRow(DrawContext ctx, Module m, Setting<?> s,
                                 int ix, int cy, int iw, int mx, int my, float dt,
                                 int alpha, int top, int bot) {
        if (cy+SET_H <= top || cy >= bot) return cy+SET_H;
        boolean focused = strFocus == s;
        boolean h = isH(mx,my, ix, cy, iw, SET_H);
        float hT  = anim("sth"+m.getName()+s.getName(), h?1f:0f, dt, 14f);
        RenderUtil.drawRoundedRect(ctx, ix, cy, iw, SET_H-2, 5f,
                wA(focused?0xFF1E2D3F:blend(curPanel,dynHover,hT),alpha), false);
        ZenyaFont.draw(ctx, textRenderer, s.getDisplayName(),
                ix+6, cy+(SET_H-textRenderer.fontHeight)/2, wA(C_MUTED,alpha), false);
        String disp = focused
                ? strBuf + ((System.nanoTime()/500_000_000L%2==0)?"|":" ")
                : String.valueOf(s.getValue());
        int dw = ZenyaFont.width(textRenderer, disp);
        ZenyaFont.draw(ctx, textRenderer, disp,
                ix+iw-dw-6, cy+(SET_H-textRenderer.fontHeight)/2,
                wA(focused?acc():C_TEXT, alpha), false);
        hitR.add(new int[]{ix, cy, iw, SET_H}); hitH.add(new HitTarget(m,s,7)); hitExtra.add(null);
        return cy + SET_H;
    }

    // ── Shared list-header row (shows name + summary + chevron) ──────────
    private int renderListHeader(DrawContext ctx, Module m, Setting<?> s, String summary,
                                  int ix, int cy, int iw, int mx, int my, float dt,
                                  int alpha, int top, int bot) {
        boolean open = openListSetting == s;
        if (cy+SET_H > top && cy < bot) {
            boolean h = isH(mx,my, ix, cy, iw, SET_H);
            float hT  = anim("lhh"+m.getName()+s.getName(), h?1f:0f, dt, 14f);
            int listBg = blend(blend(curPanel, dynHover, hT), 0xFF000000|(curAccFaint&0x00FFFFFF), open?0.15f:0f);
            RenderUtil.drawRoundedRect(ctx, ix, cy, iw, SET_H-2, 5f, wA(listBg,alpha), false);
            ZenyaFont.draw(ctx, textRenderer, s.getName(),
                    ix+6, cy+(SET_H-textRenderer.fontHeight)/2, wA(C_MUTED,alpha), false);
            int sw2 = ZenyaFont.width(textRenderer, summary);
            ZenyaFont.draw(ctx, textRenderer, summary,
                    ix+iw-sw2-14, cy+(SET_H-textRenderer.fontHeight)/2, wA(C_TEXT,alpha), false);
            miniChevron(ctx, ix+iw-6, cy+SET_H/2, open, wA(C_MUTED,alpha));
            hitR.add(new int[]{ix, cy, iw, SET_H}); hitH.add(new HitTarget(m,s,100)); hitExtra.add(null);
        }
        return cy + SET_H;
    }

    // ── Radio item (Mode / OptionSelect) ─────────────────────────────────
    private int renderRadioItem(DrawContext ctx, Module m, Setting<?> s, String label, Object value,
                                 boolean sel, int ix, int cy, int iw, int mx, int my, float dt, int alpha) {
        int rx = ix+10, rw = iw-10;
        boolean h = isH(mx,my, rx, cy, rw, SET_H);
        float hT  = anim("ri"+m.getName()+s.getName()+label, h?1f:0f, dt, 14f);
        float sT  = anim("rs"+m.getName()+s.getName()+label, sel?1f:0f, dt, 12f);
        int bg    = blend(blend(C_SUB, dynHover, hT), curSubSel, sT);
        RenderUtil.drawRoundedRect(ctx, rx, cy, rw, SET_H-2, 3f, wA(bg,alpha), false);
        ZenyaFont.draw(ctx, textRenderer, label,
                rx+8, cy+(SET_H-textRenderer.fontHeight)/2, wA(blend(C_DIM,C_TEXT,Math.max(hT,sT)),alpha), false);
        // Radio dot.
        int dotX = rx+rw-12, dotY = cy+SET_H/2;
        int dotOuter = wA(sel?acc():dynTrack, alpha);
        RenderUtil.drawRoundedRect(ctx, dotX-5, dotY-5, 10, 10, 5f, dotOuter, false);
        if (sel) RenderUtil.drawRoundedRect(ctx, dotX-3, dotY-3, 6, 6, 3f, wA(0xFF000000,alpha), false);
        hitR.add(new int[]{rx, cy, rw, SET_H}); hitH.add(new HitTarget(m,s,5)); hitExtra.add(value);
        return cy + SET_H;
    }

    // ── Check item (BlocksSetting / MobsSetting / OptionMulti) ───────────
    private int renderCheckItem(DrawContext ctx, Module m, Setting<?> s, String label, Object value,
                                 boolean sel, int ix, int cy, int iw, int mx, int my, float dt, int alpha) {
        int rx = ix+10, rw = iw-10;
        boolean h = isH(mx,my, rx, cy, rw, SET_H);
        float hT  = anim("ci"+m.getName()+s.getName()+label, h?1f:0f, dt, 14f);
        float sT  = anim("cs"+m.getName()+s.getName()+label, sel?1f:0f, dt, 12f);
        int bg    = blend(blend(C_SUB, dynHover, hT), curSubSel, sT);
        RenderUtil.drawRoundedRect(ctx, rx, cy, rw, SET_H-2, 3f, wA(bg,alpha), false);
        ZenyaFont.draw(ctx, textRenderer, label,
                rx+8, cy+(SET_H-textRenderer.fontHeight)/2, wA(blend(C_DIM,C_TEXT,Math.max(hT,sT)),alpha), false);
        // Checkbox.
        int cbX = rx+rw-14, cbY = cy+(SET_H-10)/2;
        RenderUtil.drawRoundedRect(ctx, cbX, cbY, 10, 10, 2f, wA(sel?acc():dynTrack,alpha), false);
        if (sel) {
            // Checkmark.
            ctx.fill(cbX+2, cbY+5, cbX+4, cbY+7, wA(0xFF000000,alpha));
            ctx.fill(cbX+4, cbY+7, cbX+8, cbY+3, wA(0xFF000000,alpha));
        }
        hitR.add(new int[]{rx, cy, rw, SET_H}); hitH.add(new HitTarget(m,s,5)); hitExtra.add(value);
        return cy + SET_H;
    }

    // ── Inline search field for blocks/mobs ──────────────────────────────
    private int renderListSearch(DrawContext ctx, Module m, Setting<?> s,
                                  int ix, int cy, int iw, int mx, int my, int alpha, int top, int bot) {
        if (cy+SET_H <= top || cy >= bot) return cy+SET_H;
        boolean focused = listSearchFocus && openListSetting == s;
        int brd = focused ? acc() : dynSrchBd;
        RenderUtil.drawRoundedRect(ctx, ix+10, cy, iw-10, SET_H-2, 3f, wA(dynSrchBg,alpha), false);
        RenderUtil.drawRoundedRect(ctx, ix+9, cy-1, iw-9, SET_H, 3f, wA(brd,alpha), false);
        String disp = listSearchBuf.isEmpty() && !focused ? "Search..."
                : listSearchBuf + (focused && System.nanoTime()/500_000_000L%2==0 ? "|" : "");
        int tc = listSearchBuf.isEmpty() && !focused ? C_DIM : C_TEXT;
        ZenyaFont.draw(ctx, textRenderer, disp, ix+16, cy+(SET_H-textRenderer.fontHeight)/2, wA(tc,alpha), false);
        hitR.add(new int[]{ix+10, cy, iw-10, SET_H}); hitH.add(new HitTarget(m,s,99)); hitExtra.add(null);
        return cy + SET_H;
    }

    // ── Scale bar ─────────────────────────────────────────────────────────
    private final int[] scaleBarBounds = new int[4];
    /** Module the cursor is hovering this frame, used to draw the description pill. */
    private Module hoveredModule = null;

    /** Draws a pill above the GUI-scale slider showing the hovered module's description. */
    private void renderHoverDescription(DrawContext ctx) {
        Module m = hoveredModule;
        if (m == null) return;
        String desc = m.getDescription();
        if (desc == null || desc.isEmpty()) return;
        // Slot above the scale bar — wide enough to fit a 2-line description.
        int maxPillW = Math.min(360, this.width - 40);
        int innerW = maxPillW - 24;
        java.util.List<String> lines = wrapText(textRenderer, desc, innerW);
        if (lines.isEmpty()) return;
        int lh = textRenderer.fontHeight + 1;
        int padX = 12, padY = 8;
        int pillH = lines.size() * lh + padY * 2;
        // Width: hug the longest line.
        int textW = 0;
        for (String ln : lines) {
            int w = ZenyaFont.width(textRenderer, ln);
            if (w > textW) textW = w;
        }
        int pillW = Math.min(maxPillW, textW + padX * 2);

        int sx = this.width / 2 - pillW / 2;
        // Sit just above the scale bar with a small gap.
        int scaleTop = scaleBarBounds[3] > 0 ? scaleBarBounds[1] : (this.height - 60);
        int sy = scaleTop - pillH - 6;
        if (sy < 4) sy = 4;

        RenderUtil.drawRoundedRect(ctx, sx - 1, sy - 1, pillW + 2, pillH + 2, 8f, dynSrchBd, false);
        RenderUtil.drawRoundedRect(ctx, sx,     sy,     pillW,     pillH,     7f, dynSrchBg, false);

        // Module name accent on top, description in muted text below.
        int ty = sy + padY;
        for (String ln : lines) {
            int lw = ZenyaFont.width(textRenderer, ln);
            ZenyaFont.draw(ctx, textRenderer, ln, sx + (pillW - lw) / 2, ty, C_TEXT, false);
            ty += lh;
        }
    }

    private void renderScaleBar(DrawContext ctx, int mx, int my) {
        if (scaleCollapsed) {
            scaleBarBounds[0] = 0; scaleBarBounds[1] = 0;
            scaleBarBounds[2] = 0; scaleBarBounds[3] = 0;
            return;
        }

        // Anchor directly above the search bar with the same hotbar clearance, so
        // the scale bar always sits above the search field with a small gap regardless
        // of GUI scale.
        int searchH = Math.max(20, searchDynH);
        int hotbarClearance = 50;
        int searchY = this.height - searchH - hotbarClearance + searchOY;
        searchY = clamp(searchY, 4, this.height - searchH - hotbarClearance);

        int sx = this.width / 2 - SCALE_W / 2 + scaleBarOX;
        int sy = searchY - SCALE_H - 8 + scaleBarOY;
        sx = clamp(sx, 4, this.width - SCALE_W - 4);
        sy = clamp(sy, 4, this.height - SCALE_H - 4);
        scaleBarBounds[0] = sx; scaleBarBounds[1] = sy;
        scaleBarBounds[2] = SCALE_W; scaleBarBounds[3] = SCALE_H;

        // Background pill.
        RenderUtil.drawRoundedRect(ctx, sx-1, sy-1, SCALE_W+2, SCALE_H+2,
                SCALE_H/2f+1, scaleDragging ? wA(acc(), 0xAA) : dynSrchBd, false);
        RenderUtil.drawRoundedRect(ctx, sx, sy, SCALE_W, SCALE_H, SCALE_H/2f, dynSrchBg, false);

        // Small [ bracket on the left (min scale indicator).
        { int bx2=sx+7, by2=sy+SCALE_H/2-4;
          ctx.fill(bx2,by2,   bx2+4,by2+2, C_DIM); // top bar
          ctx.fill(bx2,by2,   bx2+2,by2+8, C_DIM); // left bar
          ctx.fill(bx2,by2+6, bx2+4,by2+8, C_DIM); // bottom bar
        }
        // Large ] bracket on the right (max scale indicator).
        { int bx2=sx+SCALE_W-12, by2=sy+SCALE_H/2-6;
          ctx.fill(bx2,   by2,    bx2+6,by2+2,  C_MUTED); // top bar
          ctx.fill(bx2+4, by2,    bx2+6,by2+12, C_MUTED); // right bar
          ctx.fill(bx2,   by2+10, bx2+6,by2+12, C_MUTED); // bottom bar
        }

        // Track.
        int trkX = sx + 18, trkY = sy + SCALE_H/2 - 2;
        int trkW = SCALE_W - 34, trkH = 4;
        RenderUtil.drawRoundedRect(ctx, trkX, trkY, trkW, trkH, 2f, dynTrack, false);

        // Fill.
        float frac = (guiScale - 0.3f) / (1.0f - 0.3f);
        int fillW = (int)(trkW * frac);
        if (fillW > 0) RenderUtil.drawRoundedRect(ctx, trkX, trkY, fillW, trkH, 2f, acc(), false);

        // Knob.
        int kx = trkX + fillW, kr = 6;
        boolean hoverKnob = Math.abs(mx - kx) < kr+4 && Math.abs(my - (trkY+trkH/2)) < kr+4;
        int knobColor = scaleDragging || hoverKnob ? acc() : 0xFFFFFFFF;
        RenderUtil.drawRoundedRect(ctx, kx-kr, trkY+trkH/2-kr, kr*2, kr*2, kr, knobColor, false);

        // Percentage label above track.
        String pct = (int)(guiScale * 100) + "%";
        int pw = ZenyaFont.width(textRenderer, pct);
        ZenyaFont.draw(ctx, textRenderer, pct,
                trkX + trkW/2 - pw/2, trkY - textRenderer.fontHeight - 2, C_MUTED, false);
    }

    // ── Search bar ────────────────────────────────────────────────────────
    private void renderSearchBar(DrawContext ctx, int mx, int my) {
        int sw = Math.max(120, searchDynW);
        int sh = Math.max(20, searchDynH);
        int sx = this.width/2 - sw/2 + searchOX;
        // Clearance above the vanilla hotbar + health/armor rows. These are anchored
        // to the bottom of the screen in scaled-coordinate space, so this works for
        // every GUI scale instead of overlapping the hotbar at large scales.
        int hotbarClearance = 50;
        int sy = this.height - sh - hotbarClearance + searchOY;

        sx = clamp(sx, 4, this.width  - sw - 4);
        int maxSy = this.height - sh - hotbarClearance;
        sy = clamp(sy, 4, maxSy);

        searchBounds[0]=sx; searchBounds[1]=sy; searchBounds[2]=sw; searchBounds[3]=sh;

        // Border + body.
        RenderUtil.drawRoundedRect(ctx, sx-1, sy-1, sw+2, sh+2, RADIUS+1f,
                searchFocus ? wA(acc(),0xBB) : dynSrchBd, false);
        RenderUtil.drawRoundedRect(ctx, sx, sy, sw, sh, RADIUS, dynSrchBg, false);

        // had no function. Zero the bounds so the leftover click handler
        // never triggers, and reclaim the left padding for the text.
        int gW = 8;
        searchGrip[0]=0; searchGrip[1]=0; searchGrip[2]=0; searchGrip[3]=0;

        // Text content.
        String disp = search.isEmpty() && !searchFocus ? "Search for module..."
                : search + (searchFocus && System.nanoTime()/500_000_000L%2==0 ? "|" : "");
        int tc = search.isEmpty() && !searchFocus ? C_DIM : C_TEXT;
        ZenyaFont.draw(ctx, textRenderer, disp, sx+gW+2, sy+(sh-textRenderer.fontHeight)/2+1, tc, false);

        // Nudged 4px in from the right edge for a tidier gap.
        int rW = 14, rH = sh;
        int rX = sx + sw - rW - 4, rY = sy;
        searchResizer[0]=rX; searchResizer[1]=rY; searchResizer[2]=rW; searchResizer[3]=rH;
        boolean hoverResizer = isH(mx,my, rX, rY, rW, rH) || searchResizing;
        int rCol = hoverResizer ? acc() : C_DIM;
        int rCX  = rX + rW/2, rCY = rY + rH/2;
        for (int d = -3; d <= 3; d += 3) {
            ctx.fill(rCX+d, rCY-4, rCX+d+1, rCY+5, rCol);
        }

        // ── Theme button (palette icon) left of the search bar ──────────────
        int tbW = sh, tbH = sh;
        int tbX = sx - tbW - 6, tbY = sy;
        themeBtnBounds[0]=tbX; themeBtnBounds[1]=tbY; themeBtnBounds[2]=tbW; themeBtnBounds[3]=tbH;
        boolean hoverThemeBtn = isH(mx,my, tbX,tbY,tbW,tbH);
        int tbBg  = hoverThemeBtn ? blend(0xFF1A1A1A, acc(), 0.25f) : (dynSrchBg | 0xFF000000);
        int tbBrd = themesOpen ? acc() : (hoverThemeBtn ? wA(acc(),0xAA) : dynSrchBd);
        RenderUtil.drawRoundedRect(ctx, tbX-1, tbY-1, tbW+2, tbH+2, RADIUS+1f, tbBrd, false);
        RenderUtil.drawRoundedRect(ctx, tbX, tbY, tbW, tbH, RADIUS, tbBg, false);
        int icA = (themesOpen || hoverThemeBtn) ? 255 : 150;
        int icX2 = tbX + tbW/2, icY2 = tbY + tbH/2;
        RenderUtil.drawRoundedRect(ctx, icX2-7, icY2-4, 5, 5, 2.5f, wA(acc(), icA), false);
        RenderUtil.drawRoundedRect(ctx, icX2+2, icY2-4, 5, 5, 2.5f, wA(accDim(), icA), false);
        RenderUtil.drawRoundedRect(ctx, icX2-3, icY2+2, 5, 5, 2.5f, wA(0xFFDDDDDD, icA), false);

        // ── Scale toggle button right of the search bar ───────────────────────
        int rbW = sh, rbH = sh;
        int rbX = sx + sw + 6, rbY = sy;
        resizeBtnBounds[0]=rbX; resizeBtnBounds[1]=rbY;
        resizeBtnBounds[2]=rbW; resizeBtnBounds[3]=rbH;
        boolean hoverScaleBtn = isH(mx, my, rbX, rbY, rbW, rbH);
        int rbBg  = hoverScaleBtn ? blend(dynSrchBg, acc(), 0.25f) : (dynSrchBg | 0xFF000000);
        int rbBrd = scaleCollapsed ? acc() : (hoverScaleBtn ? wA(acc(), 0xAA) : dynSrchBd);
        RenderUtil.drawRoundedRect(ctx, rbX-1, rbY-1, rbW+2, rbH+2, RADIUS+1f, rbBrd, false);
        RenderUtil.drawRoundedRect(ctx, rbX, rbY, rbW, rbH, RADIUS, rbBg, false);
        int icY = rbY + rbH/2, icX = rbX + rbW/2;
        int iconCol = scaleCollapsed ? acc() : (hoverScaleBtn ? acc() : C_MUTED);
        // Scale icon: two small squares with a line between (□–□)
        // Left square
        ctx.fill(icX-8, icY-4, icX-3, icY+4, iconCol);
        ctx.fill(icX-7, icY-3, icX-4, icY+3, (dynSrchBg & 0x00FFFFFF) | 0xFF000000);
        // Right square
        ctx.fill(icX+3, icY-4, icX+8, icY+4, iconCol);
        ctx.fill(icX+4, icY-3, icX+7, icY+3, (dynSrchBg & 0x00FFFFFF) | 0xFF000000);
        // Connecting line
        ctx.fill(icX-3, icY-1, icX+3, icY+1, iconCol);
    }

    // ── Theme overlay ─────────────────────────────────────────────────────
    private void renderThemesOverlay(DrawContext ctx, int mx, int my, float dt) {
        // Use scaled screen dimensions (we're inside the scale matrix).
        int sw = Math.round(this.width / guiScale);
        int sh = Math.round(this.height / guiScale);
        int ow = Math.min(500, sw - 20), oh = Math.min(380, sh - 20);
        int ox = sw/2 - ow/2;
        int oy = sh/2 - oh/2;
        themesOverlayBounds[0]=ox; themesOverlayBounds[1]=oy;
        themesOverlayBounds[2]=ow; themesOverlayBounds[3]=oh;
        themesHitR.clear(); themesHitA.clear();

        float ovT = anim("thOverlay", 1f, dt, 16f);
        int   baseA = (int)(255 * ease(ovT));

        // Opaque backdrop (scaled space) to hide the underlying GUI panels.
        int bgArgb = com.zenya.module.modules.client.ZenyaPlus.getBackgroundARGB();
        ctx.fill(0, 0, sw, sh, wA(bgArgb, (int)(255 * ease(ovT))));

        // Shadow.
        for (int s = 10; s >= 1; s--) {
            int a = (int)(18 * ease(ovT) * (1f - s/11f));
            RenderUtil.drawRoundedRect(ctx, ox-s, oy-s, ow+s*2, oh+s*2, RADIUS+s, a<<24, false);
        }

        // Accent rim.
        RenderUtil.drawRoundedRect(ctx, ox-1, oy-1, ow+2, oh+2, RADIUS+1f, wA(acc(), (int)(50*ease(ovT))), false);

        // Panel body.
        RenderUtil.drawRoundedRect(ctx, ox, oy, ow, oh, RADIUS, wA(curPanel, baseA), false);

        // Header bar.
        int hdrH2 = 38;
        RenderUtil.drawRoundedRect(ctx, ox, oy, ow, hdrH2, RADIUS, RADIUS, 0f, 0f, false, wA(curHeader, baseA));
        // Accent left bar.
        RenderUtil.drawRoundedRect(ctx, ox, oy+8, 3, hdrH2-16, 2f, wA(acc(), baseA), false);
        // Title.
        ZenyaFont.draw(ctx, textRenderer, "THEMES",
                ox+14, oy+(hdrH2-textRenderer.fontHeight)/2+1, wA(C_TEXT, baseA), false);

        // Close button (×).
        int cbS = 16, cbX = ox+ow-cbS-10, cbY = oy+(hdrH2-cbS)/2;
        boolean hoverClose = isH(mx,my, cbX-2,cbY-2,cbS+4,cbS+4);
        float closeHT = anim("thClose", hoverClose?1f:0f, dt, 14f);
        int closeC = wA(blend(C_DIM, C_TEXT, closeHT), baseA);
        // × pixel art
        ctx.fill(cbX,   cbY,   cbX+2, cbY+2, closeC);
        ctx.fill(cbX+14,cbY,   cbX+16,cbY+2, closeC);
        ctx.fill(cbX+2, cbY+2, cbX+4, cbY+4, closeC);
        ctx.fill(cbX+12,cbY+2, cbX+14,cbY+4, closeC);
        ctx.fill(cbX+4, cbY+4, cbX+6, cbY+6, closeC);
        ctx.fill(cbX+10,cbY+4, cbX+12,cbY+6, closeC);
        ctx.fill(cbX+6, cbY+6, cbX+10,cbY+8, closeC);
        ctx.fill(cbX+4, cbY+8, cbX+6, cbY+10, closeC);
        ctx.fill(cbX+10,cbY+8, cbX+12,cbY+10, closeC);
        ctx.fill(cbX+2, cbY+10,cbX+4, cbY+12, closeC);
        ctx.fill(cbX+12,cbY+10,cbX+14,cbY+12, closeC);
        ctx.fill(cbX,   cbY+12,cbX+2, cbY+14, closeC);
        ctx.fill(cbX+14,cbY+12,cbX+16,cbY+14, closeC);
        themesHitR.add(new int[]{cbX-2,cbY-2,cbS+4,cbS+4});
        themesHitA.add(() -> { themesOpen = false; anims.remove("thOverlay"); });

        // Divider below header.
        ctx.fill(ox, oy+hdrH2, ox+ow, oy+hdrH2+1, wA(dynSrchBd, baseA));

        // Card grid.
        int pad = 14, gap = 10, cols = 3;
        int cardW = (ow - pad*2 - gap*(cols-1)) / cols;
        int cardH = 92;
        int contentTop = oy + hdrH2 + 1;
        int contentH2  = oh - hdrH2 - 1;

        ctx.enableScissor(ox, contentTop, ox+ow, contentTop+contentH2);

        var themes = com.zenya.module.modules.client.Themes.ALL;
        String sel = com.zenya.module.modules.client.Themes.getInstance() != null
                ? com.zenya.module.modules.client.Themes.getInstance().selectedSetting().getValue() : "Dark";

        int rows       = (themes.size() + cols - 1) / cols;
        int totalH2    = rows * (cardH + gap) + pad;
        int maxSc2     = Math.max(0, totalH2 - contentH2);
        themesScroll   = clamp(themesScroll, 0, maxSc2);

        int startY = contentTop + pad - themesScroll;
        for (int i = 0; i < themes.size(); i++) {
            var theme = themes.get(i);
            int col   = i % cols;
            int row   = i / cols;
            int cx    = ox + pad + col * (cardW + gap);
            int cy    = startY + row * (cardH + gap);
            boolean isSelected = theme.name().equalsIgnoreCase(sel);
            boolean hover2     = isH(mx,my, cx,cy,cardW,cardH);
            float   hT2 = anim("th-h-"+theme.name(), hover2?1f:0f, dt, 14f);
            float   sT2 = anim("th-s-"+theme.name(), isSelected?1f:0f, dt, 14f);
            if (cy + cardH >= contentTop && cy < contentTop + contentH2) {
                drawThemeCard(ctx, cx, cy, cardW, cardH, theme, hT2, sT2, baseA);
            }
            int fi = i;
            themesHitR.add(new int[]{cx, cy, cardW, cardH});
            themesHitA.add(() -> {
                com.zenya.module.modules.client.Themes.apply(themes.get(fi));
                // Close overlay + all open panels/pickers.
                themesOpen = false;
                anims.remove("thOverlay");
                openListSetting = null;
                openColor = null;
            });
        }

        ctx.disableScissor();

        // Scrollbar.
        if (maxSc2 > 0) {
            float frac2 = (float)contentH2 / totalH2;
            int   sbH   = Math.max(16, (int)(contentH2 * frac2));
            int   sbY   = contentTop + (int)((contentH2 - sbH) * (float)themesScroll / maxSc2);
            RenderUtil.drawRoundedRect(ctx, ox+ow-5, sbY, 3, sbH, 1.5f, wA(0xFFFFFF, (int)(80*ease(ovT))), false);
        }
    }

    private void drawThemeCard(DrawContext ctx, int x, int y, int w, int h,
            com.zenya.module.modules.client.Themes.Theme theme,
            float hoverT, float selectedT, int baseA) {
        int cardR = 6;

        // Selection glow layers.
        if (selectedT > 0.01f) {
            for (int i = 0; i < 8; i++) {
                int a = (int)(16 * selectedT * (baseA/255f) * (1f - i/9f));
                int s = (i+1)*2;
                RenderUtil.drawRoundedRect(ctx, x-s, y-s, w+s*2, h+s*2, cardR+s,
                        (a<<24)|(acc()&0x00FFFFFF), false);
            }
        }

        // Card background.
        int cardBg = blend(curHeader, blend(curHeader, 0xFFFFFFFF, 0.07f), hoverT);
        RenderUtil.drawRoundedRect(ctx, x, y, w, h, cardR, wA(cardBg, baseA), false);

        // Selection outline (accent ring).
        if (selectedT > 0.01f) {
            int sa = (int)(255 * selectedT * baseA/255f);
            RenderUtil.drawRoundedRect(ctx, x-2, y-2, w+4, h+4, cardR+2, wA(acc(), sa), false);
            RenderUtil.drawRoundedRect(ctx, x, y, w, h, cardR, wA(cardBg, baseA), false);
        }

        int stripPad = 6, stripH = 34;
        int stripX   = x + stripPad, stripY = y + 8;
        int stripW   = w - stripPad*2;
        int swatches = theme.palette().length;
        int swatchW  = stripW / swatches;
        float swR    = 4f;
        for (int i = 0; i < swatches; i++) {
            int sx2 = stripX + i * swatchW;
            int sw2 = (i == swatches-1) ? (stripX + stripW - sx2) : swatchW;
            float tl = (i==0) ? swR : 0f,   bl = (i==0) ? swR : 0f;
            float tr = (i==swatches-1) ? swR : 0f, br = (i==swatches-1) ? swR : 0f;
            int col  = theme.palette()[i];
            ctx.fill(sx2 + (i==0?1:0), stripY+1,
                     sx2 + sw2 - (i==swatches-1?1:0), stripY+stripH-1, col);
            for (int p = 0; p < 3; p++) {
                RenderUtil.drawRoundedRect(ctx, sx2, stripY, sw2, stripH, tl, tr, br, bl, false, col);
            }
        }

        // Name + description.
        int textX = x + stripPad, textY = stripY + stripH + 8;
        ZenyaFont.draw(ctx, textRenderer, theme.name(),        textX, textY,   wA(C_TEXT,  baseA), false);
        ZenyaFont.draw(ctx, textRenderer, theme.description(), textX, textY + textRenderer.fontHeight + 3,
                wA(C_MUTED, baseA), false);

        // Checkmark circle (top-right).
        if (selectedT > 0.01f) {
            int cmSize = 16, cmX = x+w-12-cmSize, cmY = y+10;
            int alpha8 = (int)(255 * selectedT * baseA/255f);
            // Smooth filled circle (4 passes).
            for (int p = 0; p < 4; p++) {
                RenderUtil.drawRoundedRect(ctx, cmX, cmY, cmSize, cmSize, cmSize/2f,
                        wA(0xFFFFFFFF, alpha8), false);
            }
            // ✓ pixel art.
            int cx2 = cmX + cmSize/2, cy2 = cmY + cmSize/2 + 1;
            int dark = wA(0xFF14171F, alpha8);
            for (int i=0; i<3; i++) { int px2=cx2-3+i, py2=cy2-1+i; ctx.fill(px2,py2,px2+2,py2+2,dark); }
            for (int i=0; i<5; i++) { int px2=cx2-1+i, py2=cy2+1-i; ctx.fill(px2,py2,px2+2,py2+2,dark); }
        }
    }

    // ── Toggle switch ─────────────────────────────────────────────────────
    private void toggle(DrawContext ctx, int x, int y, int w, int h, float t) {
        int track = blend(dynTrack, acc(), t);
        RenderUtil.drawRoundedRect(ctx, x, y, w, h, h/2f, track, false);
        int kx = x+2+(int)((w-h+2)*ease(t)), ks = h-4;
        RenderUtil.drawRoundedRect(ctx, kx, y+2, ks, ks, ks/2f, 0xFFFFFFFF, false);
    }

    // ── Chevrons ──────────────────────────────────────────────────────────
    private void chevron(DrawContext ctx, int cx, int cy, float t, int c) {
        // Bigger 2px-wide ∨ (expanded) or ∧ (collapsed)
        boolean up = t > 0.5f;
        for (int i = 0; i <= 5; i++) {
            int x = cx - 5 + i;
            int y = up ? (cy + 2 - i) : (cy - 2 + i);
            ctx.fill(x, y, x+2, y+2, c);
        }
        for (int i = 0; i <= 5; i++) {
            int x = cx + i;
            int y = up ? (cy - 3 + i) : (cy + 3 - i);
            ctx.fill(x, y, x+2, y+2, c);
        }
    }
    private void miniChevron(DrawContext ctx, int cx, int cy, boolean down, int c) {
        if (down) {
            ctx.fill(cx-3,cy-1,cx-2,cy,c); ctx.fill(cx-2,cy,cx-1,cy+1,c);
            ctx.fill(cx-1,cy+1,cx,cy+2,c); ctx.fill(cx,cy,cx+1,cy+1,c);
            ctx.fill(cx+1,cy-1,cx+2,cy,c);
        } else {
            ctx.fill(cx-3,cy+1,cx-2,cy+2,c); ctx.fill(cx-2,cy,cx-1,cy+1,c);
            ctx.fill(cx-1,cy-1,cx,cy,c);     ctx.fill(cx,cy,cx+1,cy+1,c);
            ctx.fill(cx+1,cy+1,cx+2,cy+2,c);
        }
    }

    // ── Mouse ─────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        bindListening = null;
        bindListeningSetting = null;
        strFocus = null;
        int btn = click.button();
        // Raw screen coords for scale-bar + search-bar (drawn without scale).
        int rawMx = (int)click.x(), rawMy = (int)click.y();
        // Scaled coords for everything inside the matrix (panels, hitR, color picker).
        int mx = Math.round(rawMx / guiScale), my = Math.round(rawMy / guiScale);

        // Theme button (unscaled).
        if (isH(rawMx,rawMy, themeBtnBounds[0],themeBtnBounds[1],themeBtnBounds[2],themeBtnBounds[3])) {
            themesOpen = !themesOpen;
            if (!themesOpen) anims.remove("thOverlay");
            return true;
        }

        if (themesOpen) {
            for (int i = themesHitR.size()-1; i >= 0; i--) {
                int[] r = themesHitR.get(i);
                if (isH(mx,my, r[0],r[1],r[2],r[3])) {
                    themesHitA.get(i).run();
                    return true;
                }
            }
            // Click outside overlay → close.
            if (!isH(mx,my, themesOverlayBounds[0],themesOverlayBounds[1],themesOverlayBounds[2],themesOverlayBounds[3])) {
                themesOpen = false;
                anims.remove("thOverlay");
            }
            return true;
        }

        if (isH(rawMx,rawMy, resizeBtnBounds[0],resizeBtnBounds[1],resizeBtnBounds[2],resizeBtnBounds[3])) {
            scaleCollapsed = !scaleCollapsed;
            if (scaleCollapsed) scaleDragging = false;
            return true;
        }

        if (!scaleCollapsed && isH(rawMx,rawMy, scaleBarBounds[0],scaleBarBounds[1],scaleBarBounds[2],scaleBarBounds[3])) {
            // Check if near the knob → slider drag; otherwise → bar reposition drag.
            int trkX2 = scaleBarBounds[0] + 20, trkW2 = SCALE_W - 36;
            int kx2 = trkX2 + (int)(trkW2 * (guiScale - 0.3f) / 0.7f);
            if (Math.abs(rawMx - kx2) < 16) {
                scaleDragging = true; applyScaleAt(rawMx);
            }
            return true;
        }

        if (isH(rawMx,rawMy, searchGrip[0],searchGrip[1],searchGrip[2],searchGrip[3])) {
            return true;
        }
        if (isH(rawMx,rawMy, searchResizer[0],searchResizer[1],searchResizer[2],searchResizer[3])) {
            searchResizing = true;
            searchResizeStartX = rawMx;
            searchResizeStartW = searchDynW;
            searchResizeStartY = rawMy;
            searchResizeStartH = searchDynH;
            return true;
        }
        // Search bar (unscaled).
        if (isH(rawMx,rawMy, searchBounds[0],searchBounds[1],searchBounds[2],searchBounds[3])) {
            searchFocus = true; listSearchFocus = false; strFocus = null; return true;
        }
        searchFocus = false;

        // Close string edit.
        if (strFocus != null && btn == 0) {
            @SuppressWarnings("unchecked") var ss = (Setting<String>) strFocus;
            ss.setValue(strBuf); strFocus = null; return true;
        }

        // Color picker areas (scaled coords).
        if (openColor != null && btn == 0) {
            if (isH(mx,my, cSvX,cSvY,cSvW,cSvH)) { cDrag=CDrag.SV;  applyPickerAt(mx,my); return true; }
            if (isH(mx,my, cHueX,cHueY,cHueW,cHueH)) { cDrag=CDrag.HUE; applyPickerAt(mx,my); return true; }
            if (isH(mx,my, cAlX,cAlY,cAlW,cAlH)) { cDrag=CDrag.ALPHA; applyPickerAt(mx,my); return true; }
        }

        for (int i=0; i<N; i++) {
            if (isH(mx,my, px[i],py[i],PANEL_W,HEADER_H)) {
                if (btn==0 && mx >= px[i]+PANEL_W-32) {
                    collapsed[i] = !collapsed[i]; openListSetting=null; openColor=null;
                }
                // Dragging the rest of the header moves the panel (handled in mouseDragged).
                return true;
            }
        }

        for (int i=hitR.size()-1; i>=0; i--) {
            int[] r = hitR.get(i);
            if (mx<r[0]||mx>=r[0]+r[2]||my<r[1]||my>=r[1]+r[3]) continue;
            HitTarget ht = hitH.get(i);
            Object    ex = hitExtra.get(i);

            switch (ht.kind()) {
                case 1 -> {
                    // so left-click does nothing. Right-click still expands settings.
                    if (ht.mod() instanceof com.zenya.module.modules.client.ZenyaPlus) {
                        if (btn == 1) {
                            boolean wasExp = isExpanded(ht.mod());
                            expanded.put(ht.mod(), !wasExp);
                            if (!wasExp) { openListSetting = null; openColor = null; }
                        }
                        // left-click intentionally a no-op
                    }
                    // Themes left-click → open the themes overlay directly (instead of toggling
                    // the module enabled/disabled, which doesn't visually mean anything).
                    else if (ht.mod() instanceof com.zenya.module.modules.client.Themes) {
                        if (btn == 1) {
                            boolean wasExp = isExpanded(ht.mod());
                            expanded.put(ht.mod(), !wasExp);
                            if (!wasExp) { openListSetting = null; openColor = null; }
                        } else {
                            themesOpen = true;
                        }
                    }
                    else if (btn == 1) {
                        boolean wasExp = isExpanded(ht.mod());
                        expanded.put(ht.mod(), !wasExp);
                        if (!wasExp) { openListSetting = null; openColor = null; }
                    } else { // left-click → toggle module on/off
                        ht.mod().toggle();
                    }
                }
                case 2 -> { sliderDrag = ht.set(); applySliderAt(mx); }
                case 3, 10 -> {
                    @SuppressWarnings("unchecked") var bs = (Setting<Boolean>) ht.set();
                    bs.setValue(!Boolean.TRUE.equals(bs.getValue()));
                }
                case 4 -> { if (ht.set() instanceof ActionSetting as) as.trigger(); }
                case 5 -> handleListItem(ht.mod(), ht.set(), ex);
                case 6 -> { // color
                    @SuppressWarnings("unchecked") var cs = (Setting<Color>) ht.set();
                    if (openColor == cs) { openColor = null; cDrag = CDrag.NONE; }
                    else { openColor = cs; cDrag = CDrag.NONE; }
                    openListSetting = null;
                }
                case 7 -> {
                    // Friends "Names" gets its own popup picker (Add / Clear / row-delete / Save).
                    // Everything else falls back to the inline string editor.
                    if (ht.mod() instanceof com.zenya.module.modules.client.Friends
                            && ht.set().matchesName("Names")) {
                        this.client.setScreen(new FriendsPickerScreen(this));
                    } else {
                        strFocus = ht.set();
                        strBuf   = String.valueOf(ht.set().getValue());
                        searchFocus = false;
                    }
                }
                case 8 -> { if (ht.set() instanceof ThresholdSetting ts) ts.setEnabled(!ts.isEnabled()); }
                case 9 -> { sliderDrag = ht.set(); applySliderAt(mx); }
                case 99 -> { // list search field
                    listSearchFocus = true; listSearchBuf = ""; searchFocus = false;
                }
                case 100 -> { // list header toggle
                    // Blocks/Mobs settings live in a dedicated popup screen instead of inline.
                    if (ht.set() instanceof BlocksSetting bs) {
                        com.zenya.module.Module clicked = ht.mod();
                        if (clicked instanceof com.zenya.module.modules.render.BlockESP espModule) {
                            this.client.setScreen(new BlockPickerScreen(this, bs, espModule));
                        } else {
                            com.zenya.ZenyaClient.LOGGER.warn("[BlockPicker] Couldn't open: module is {} (not BlockESP)",
                                    clicked == null ? "null" : clicked.getClass().getSimpleName());
                        }
                    } else if (ht.set() instanceof MobsSetting ms) {
                        com.zenya.module.Module clicked = ht.mod();
                        if (clicked instanceof com.zenya.module.modules.render.MobESP mobModule) {
                            this.client.setScreen(new MobPickerScreen(this, ms, mobModule));
                        } else {
                            com.zenya.ZenyaClient.LOGGER.warn("[MobPicker] Couldn't open: module is {} (not MobESP)",
                                    clicked == null ? "null" : clicked.getClass().getSimpleName());
                        }
                    } else if (openListSetting == ht.set()) {
                        openListSetting = null; listSearchBuf = ""; listSearchFocus = false;
                    } else {
                        openListSetting = ht.set(); listSearchBuf = ""; listSearchFocus = false;
                        openColor = null;
                    }
                }
                case 200 -> { // Bind row → start listening for a key, or cancel
                    if (bindListening == ht.mod()) bindListening = null;
                    else                            bindListening = ht.mod();
                }
                case 201 -> { bindListeningSetting = (Setting<Integer>) ht.set(); }
                case 300 -> { // Storage grid icon → left-click toggles selection.
                    if (ht.set() instanceof StorageBlocksSetting sbs && ex instanceof String key) {
                        if (btn == 1) {
                            // Right-click on a selected grid icon also opens its colour picker.
                            if (sbs.isSelected(key)) {
                                Setting<Color> cs = sbs.colorSettingFor(key);
                                openColor = (openColor == cs) ? null : cs;
                                cDrag = CDrag.NONE;
                            }
                        } else {
                            sbs.toggle(key);
                            // Closing the colour picker if it pointed at a deselected entry.
                            if (!sbs.isSelected(key) && openColor == sbs.colorSettingFor(key)) {
                                openColor = null;
                            }
                        }
                    }
                }
                case 301 -> { // Selected-blocks row → right-click toggles colour picker.
                    if (ht.set() instanceof StorageBlocksSetting sbs && ex instanceof String key) {
                        Setting<Color> cs = sbs.colorSettingFor(key);
                        if (btn == 1) {
                            openColor = (openColor == cs) ? null : cs;
                            cDrag = CDrag.NONE;
                        } else {
                            // Left-click also opens it (friendlier than only right-click).
                            openColor = (openColor == cs) ? null : cs;
                            cDrag = CDrag.NONE;
                        }
                    }
                }
            }
            return true;
        }

        // Click on module row body (not toggle) → expand.
        // (Handled inside case 1 above with position split.)

        return super.mouseClicked(click, doubled);
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private void handleListItem(Module m, Setting<?> s, Object value) {
        if (s instanceof ModeSetting ms && value instanceof String v) {
            ms.setValue(v);
        } else if (s instanceof OptionSelectSetting oss && value instanceof String v) {
            oss.select(v);
        } else if (s instanceof OptionMultiSelectSetting omss && value instanceof String v) {
            omss.toggle(v);
        } else if (s instanceof BlocksSetting bs && value instanceof Block b) {
            bs.toggle(b);
        } else if (s instanceof MobsSetting mbs && value instanceof EntityType<?> et) {
            mbs.toggle((EntityType) et);
        }
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            if (sliderDrag != null) sliderDrag = null;
            if (cDrag != CDrag.NONE)  cDrag = CDrag.NONE;
            if (scaleDragging)    scaleDragging    = false;
            if (scaleBarDragging) scaleBarDragging = false;
            if (searchDragging)   searchDragging   = false;
            if (searchResizing)   searchResizing   = false;
            if (dragPanel >= 0) {
                SPX[dragPanel] = px[dragPanel]; SPY[dragPanel] = py[dragPanel];
                dragPanel = -1;
            }
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double ox, double oy) {
        int btn = click.button();
        int rawMx = (int)click.x(), rawMy = (int)click.y();
        int mx = Math.round(rawMx / guiScale), my = Math.round(rawMy / guiScale);
        if (btn != 0) return false;

        // Scale bar drag (raw coords).
        if (scaleDragging && !scaleCollapsed) { applyScaleAt(rawMx); return true; }
        scaleDragging = false;
        // Scale bar reposition.
        if (scaleBarDragging && !scaleCollapsed) {
            scaleBarOX = rawMx - scaleBarDragStartX;
            scaleBarOY = rawMy - scaleBarDragStartY;
            return true;
        }

        // Search bar drag.
        if (searchDragging) {
            searchOX = rawMx - searchDragStartX;
            searchOY = rawMy - searchDragStartY;
            return true;
        }
        if (searchResizing) {
            int deltaX = rawMx - searchResizeStartX;
            int deltaY = rawMy - searchResizeStartY;
            searchDynW = Math.max(120, Math.min(500, searchResizeStartW + deltaX * 2));
            searchDynH = Math.max(20,  Math.min(60,  searchResizeStartH + deltaY));
            return true;
        }

        // Color picker drag.
        if (openColor != null && cDrag != CDrag.NONE) {
            applyPickerAt(mx, my); return true;
        }
        if (openColor != null) {
            if (isH(mx,my, cSvX,cSvY,cSvW,cSvH))   { cDrag=CDrag.SV;    applyPickerAt(mx,my); return true; }
            if (isH(mx,my, cHueX,cHueY,cHueW,cHueH)){ cDrag=CDrag.HUE;   applyPickerAt(mx,my); return true; }
            if (isH(mx,my, cAlX,cAlY,cAlW,cAlH))     { cDrag=CDrag.ALPHA; applyPickerAt(mx,my); return true; }
        }

        // Slider drag.
        if (sliderDrag != null) { applySliderAt(mx); return true; }

        // Panel drag (start or continue).
        if (dragPanel >= 0) {
            px[dragPanel] = mx - dragOx; py[dragPanel] = my - dragOy; return true;
        }
        for (int i=0; i<N; i++) {
            if (isH(mx,my, px[i], py[i], PANEL_W, HEADER_H)) {
                dragPanel = i; dragOx = mx-px[i]; dragOy = my-py[i]; return true;
            }
        }
        return super.mouseDragged(click, ox, oy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hA, double vA) {
        // Overlay scroll.
        int imxS = Math.round((float)mx / guiScale), imyS = Math.round((float)my / guiScale);
        if (themesOpen && isH(imxS,imyS, themesOverlayBounds[0],themesOverlayBounds[1],themesOverlayBounds[2],themesOverlayBounds[3])) {
            themesScroll = Math.max(0, themesScroll - (int)(vA*12));
            return true;
        }
        int imx = Math.round((float)mx / guiScale), imy = Math.round((float)my / guiScale);
        for (int i=0; i<N; i++) {
            int panH = HEADER_H + (collapsed[i] ? 0 : Math.min(contentH(i), MAX_VIS_H));
            if (isH(imx,imy, px[i], py[i], PANEL_W, panH)) {
                scroll[i] = clamp(scroll[i]-(int)(vA*12), 0, maxSc[i]); return true;
            }
        }
        return true;
    }

    // ── Keyboard ──────────────────────────────────────────────────────────
    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        // the listening state and applies the bind.
        if (bindListening != null) {
            int newBind = (k == GLFW.GLFW_KEY_ESCAPE) ? 0 : k;
            bindListening.setBind(newBind);
            // ActivatableModules (Freelook, NameProtect, …) also need their activation key set so
            // hold-mode etc. actually react to the configured key. ZenyaClient skips the toggle path
            // when bind == activationKey so the module isn't double-fired.
            if (bindListening instanceof com.zenya.module.ActivatableModule am) {
                am.setActivationKey(newBind);
            }
            bindListening = null;
            return true;
        }
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            if (themesOpen)             { themesOpen = false; anims.remove("thOverlay"); return true; }
            if (openColor != null)      { openColor = null; return true; }
            if (openListSetting != null){ openListSetting = null; listSearchBuf=""; return true; }
            if (listSearchFocus)        { listSearchFocus = false; return true; }
            if (strFocus != null)       { strFocus = null; return true; }
            if (searchFocus)            { searchFocus = false; return true; }
            this.client.setScreen(null); return true;
        }
        if (bindListeningSetting != null) {
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                bindListeningSetting.setValue(0);
            } else {
                bindListeningSetting.setValue(k);
            }
            bindListeningSetting = null;
            return true;
        }

        if (strFocus != null) {
            if (input.isPaste())                       { strBuf += getClipboardText(); return true; }
            if (k == GLFW.GLFW_KEY_BACKSPACE && !strBuf.isEmpty()) {
                strBuf = strBuf.substring(0, strBuf.length()-1); return true;
            }
            if (k == GLFW.GLFW_KEY_ENTER) {
                @SuppressWarnings("unchecked") var ss = (Setting<String>) strFocus;
                ss.setValue(strBuf); strFocus = null; return true;
            }
            return true;
        }
        if (listSearchFocus) {
            if (input.isPaste())                       { listSearchBuf += getClipboardText(); return true; }
            if (k == GLFW.GLFW_KEY_BACKSPACE && !listSearchBuf.isEmpty()) {
                listSearchBuf = listSearchBuf.substring(0, listSearchBuf.length()-1); return true;
            }
            return true;
        }
        if (searchFocus) {
            if (input.isPaste())                       { search += getClipboardText(); return true; }
            if (k == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                search = search.substring(0, search.length()-1); return true;
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        int cp = input.codepoint();
        if (cp >= 32 && cp < 127) {
            if (strFocus != null)     { strBuf += (char)cp; return true; }
            if (listSearchFocus)      { listSearchBuf += (char)cp; return true; }
            if (searchFocus)          { search += (char)cp; return true; }
        }
        return false;
    }

    @Override
    public void close() {
        System.arraycopy(px, 0, SPX, 0, N);
        System.arraycopy(py, 0, SPY, 0, N);
        super.close();
    }

    private String getClipboardText() {
        String clip = net.minecraft.client.MinecraftClient.getInstance().keyboard.getClipboard();
        if (clip == null || clip.isEmpty()) return "";
        StringBuilder b = new StringBuilder(clip.length());
        clip.codePoints()
                .filter(cp -> !Character.isISOControl(cp))
                .forEach(b::appendCodePoint);
        return b.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private List<Module> modsFor(int idx) {
        Category cat = (idx == OTHER_IDX) ? Category.CLIENT : ORDER[idx];
        List<Module> r = new ArrayList<>();
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (m.getCategory() != cat) continue;
            if (!search.isEmpty() && !m.getName().toLowerCase(Locale.ROOT)
                    .contains(search.toLowerCase(Locale.ROOT))) continue;
            r.add(m);
        }
        return r;
    }

    private boolean isExpanded(Module m) { return Boolean.TRUE.equals(expanded.get(m)); }

    private int settingsHeight(Module m) {
        int h = SET_H; // always-visible Bind row at the top
        for (Setting<?> s : m.getSettings()) {
            if (!s.isVisible()) continue;
            if (s instanceof SectionSetting || s instanceof ActionSetting
                    || s instanceof ConfirmBooleanSetting
                    || s.getValue() instanceof Boolean
                    || s.getValue() instanceof Color
                    || (s instanceof ModeSetting && openListSetting != s)
                    || (s instanceof OptionSelectSetting && openListSetting != s)
                    || s.getValue() instanceof String) {
                h += SET_H;
                if (s.getValue() instanceof Color && openColor == s) h += CP_TOTAL;
            } else if (s instanceof ThresholdSetting ts) {
                h += SET_H;
                if (ts.isEnabled()) h += SLIDER_H;
            } else if (s.getValue() instanceof Double || s.getValue() instanceof Float
                    || (s.getValue() instanceof Integer && !(s instanceof ThresholdSetting))) {
                h += SLIDER_H;
            } else if (s instanceof ModeSetting ms && openListSetting == s) {
                h += SET_H + ms.getModes().size() * SET_H;
            } else if (s instanceof OptionSelectSetting oss && openListSetting == s) {
                h += SET_H + oss.getOptions().size() * SET_H;
            } else if (s instanceof OptionMultiSelectSetting omss && openListSetting == s) {
                h += SET_H + (SET_H + Math.min(omss.getOptions().size(), 20)*SET_H);
            } else if (s instanceof BlocksSetting) {
                h += SET_H;
            } else if (s instanceof MobsSetting) {
                h += SET_H;
            } else if (s instanceof StorageBlocksSetting sbs) {
                h += SET_H; // header
                if (openListSetting == s) {
                    int rows = (sbs.getOptions().size() + STORAGE_GRID_COLS - 1) / STORAGE_GRID_COLS;
                    h += rows * (STORAGE_CELL_H + 3);
                    int sel = sbs.getSelectedEntries().size();
                    if (sel > 0) {
                        h += SET_H + sel * SET_H;
                        for (StorageBlocksSetting.Entry entry : sbs.getSelectedEntries()) {
                            if (openColor == sbs.colorSettingFor(entry.value())) {
                                h += CP_TOTAL;
                                break;
                            }
                        }
                    }
                }
            } else {
                h += SET_H;
            }
        }
        return h;
    }

    private int contentH(int idx) {
        List<Module> mods = modsFor(idx);
        int h = 0;
        for (Module m : mods) {
            h += MOD_H;
            if (isExpanded(m)) h += settingsHeight(m);
        }
        return h;
    }

    private void applySliderAt(int mx) {
        if (sliderDrag == null) return;
        double frac = Math.max(0.0, Math.min(1.0, (double)(mx - slTrkX) / slTrkW));
        applyFrac(sliderDrag, frac);
    }

    private void applyScaleAt(int mx) {
        int trkX = scaleBarBounds[0] + 20;
        int trkW = SCALE_W - 36;
        float frac = (float) Math.max(0.0, Math.min(1.0, (double)(mx - trkX) / trkW));
        guiScale = 0.3f + frac * (1.0f - 0.3f);
        guiScale = Math.round(guiScale * 100f) / 100f;
    }

    @SuppressWarnings("unchecked")
    private <T> void applyFrac(Setting<T> s, double frac) {
        if (s.getValue() instanceof Double) {
            double mn = ((Number)s.getMin()).doubleValue(), mx2 = ((Number)s.getMax()).doubleValue();
            double v = Math.round((mn+(mx2-mn)*frac)*100.0)/100.0;
            ((Setting<Double>)(Setting<?>)s).setValue(v);
        } else if (s.getValue() instanceof Float) {
            float mn = ((Number)s.getMin()).floatValue(), mx2 = ((Number)s.getMax()).floatValue();
            float v = Math.round((mn+(mx2-mn)*(float)frac)*100f)/100f;
            ((Setting<Float>)(Setting<?>)s).setValue(v);
        } else if (s.getValue() instanceof Integer) {
            int mn = ((Number)s.getMin()).intValue(), mx2 = ((Number)s.getMax()).intValue();
            int v = (int)Math.round(mn+(mx2-mn)*frac);
            ((Setting<Integer>)(Setting<?>)s).setValue(v);
        }
    }

    private void applyPickerAt(int mx, int my) {
        if (openColor == null) return;
        switch (cDrag) {
            case SV    -> { cHSV[1]=clamp01((float)(mx-cSvX)/cSvW); cHSV[2]=1f-clamp01((float)(my-cSvY)/cSvH); }
            case HUE   -> cHSV[0]=clamp01((float)(mx-cHueX)/cHueW);
            case ALPHA -> cAlpha =(int)(clamp01((float)(mx-cAlX)/cAlW)*255);
            default    -> {}
        }
        int rgb = Color.HSBtoRGB(cHSV[0],cHSV[1],cHSV[2]);
        openColor.setValue(new Color((rgb>>16)&0xFF,(rgb>>8)&0xFF,rgb&0xFF,cAlpha));
    }

    private void applyPickerAt(int mx) { applyPickerAt(mx, 0); }

    private boolean isH(int mx,int my, int x,int y,int w,int h) {
        return mx>=x&&mx<x+w&&my>=y&&my<y+h;
    }
    private static int wA(int c, int a) { return (a<<24)|(c&0x00FFFFFF); }
    private static int blend(int a, int b, float t) {
        int ar=(a>>16)&0xFF,ag=(a>>8)&0xFF,ab=a&0xFF,aa=(a>>24)&0xFF;
        int br=(b>>16)&0xFF,bg=(b>>8)&0xFF,bb=b&0xFF,ba=(b>>24)&0xFF;
        return ((int)(aa+(ba-aa)*t)<<24)|((int)(ar+(br-ar)*t)<<16)|((int)(ag+(bg-ag)*t)<<8)|(int)(ab+(bb-ab)*t);
    }
    private static float ease(float t) { return t*t*(3f-2f*t); }
    private static float exp(float c, float tgt, float dt, float s) {
        return c+(tgt-c)*(1f-(float)Math.exp(-s*dt));
    }
    private static float clamp01(float v) { return Math.max(0f,Math.min(1f,v)); }
    private static int clamp(int v,int mn,int mx) { return Math.max(mn,Math.min(mx,v)); }
    private float anim(Object key, float tgt, float dt, float spd) {
        float[] a = anims.computeIfAbsent(key, k->new float[]{tgt});
        a[0] = exp(a[0],tgt,dt,spd); return a[0];
    }
    /** Wraps a description string into lines that fit within maxW pixels at the current font. */
    private static java.util.List<String> wrapText(net.minecraft.client.font.TextRenderer tr, String s, int maxW) {
        java.util.List<String> out = new java.util.ArrayList<>(4);
        if (s == null || s.isEmpty() || maxW <= 0) return out;
        StringBuilder cur = new StringBuilder();
        String[] words = s.split(" ");
        for (String w : words) {
            String trial = cur.length() == 0 ? w : cur + " " + w;
            if (ZenyaFont.width(tr, trial) <= maxW) {
                if (cur.length() > 0) cur.append(' ');
                cur.append(w);
            } else {
                if (cur.length() > 0) out.add(cur.toString());
                cur.setLength(0);
                cur.append(w);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static String fmtNum(double v) {
        if (v==(long)v) return String.valueOf((long)v);
        return String.format("%.2f",v);
    }
}
