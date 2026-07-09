package com.zenya.gui.hud;

import com.zenya.module.Module;
import com.zenya.module.ModuleManager;
import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.module.modules.client.Hud;
import com.zenya.module.modules.client.Themes;
import com.zenya.utils.renderer.RenderUtil;
import com.zenya.gui.ClickGUI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.zenya.utils.ZenyaFont;

public final class ModuleListHud {

    public static final ModuleListHud INSTANCE = new ModuleListHud();

    // ── different, cleaner, tuffer style ──────────────────────────────────
    // Minimalist cards with a strong accent bar and soft rounding.
    private static final int   CARD_COLOR    = 0xFF000000; // Solid black
    private static final int   OUTLINE_COLOR = 0xFF2A2A2A; // Subtle border
    private static final int   SHADOW_COLOR  = 0x00000000; // Remove shadow for cleaner look
    private static final float CARD_RADIUS   = 12.0f;      // More rounded
    private static final int   PAD_X         = 12;         // spacious padding
    private static final int   PAD_Y         = 5;
    private static final int   GAP           = 4;
    private static final int   TOP_OFFSET    = 10;
    private static final int   RIGHT_OFFSET  = 10;
    // Tuffer accent bar
    private static final int   BAR_W         = 4;
    private static final int   BAR_PAD       = 0;          // full height bar for tuffer look

    private static final List<Module> ENABLED_BUFFER = new ArrayList<>(64);

    private ModuleListHud() {}

    private static List<Module> applyLayout(List<Module> sortedDesc, String layout) {
        if (sortedDesc.size() <= 1) return sortedDesc;
        if ("Bottom".equalsIgnoreCase(layout)) {
            List<Module> reversed = new ArrayList<>(sortedDesc);
            java.util.Collections.reverse(reversed);
            return reversed;
        }
        if ("Middle".equalsIgnoreCase(layout)) {
            int n = sortedDesc.size();
            Module[] out = new Module[n];
            int mid = n / 2;
            int below = mid;
            int above = mid - 1;
            for (int i = 0; i < n; i++) {
                if (i == 0) {
                    out[mid] = sortedDesc.get(0);
                } else if (i % 2 == 1) {
                    if (below + 1 < n) { below++; out[below] = sortedDesc.get(i); }
                    else                { out[above--] = sortedDesc.get(i); }
                } else {
                    if (above >= 0)    { out[above--] = sortedDesc.get(i); }
                    else               { below++; out[below] = sortedDesc.get(i); }
                }
            }
            return java.util.Arrays.asList(out);
        }
        return sortedDesc; // "Top" default
    }

    public void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null || client.getDebugHud().shouldShowDebugHud()) return;
        if (client.currentScreen instanceof com.zenya.gui.ClickGUI) return;

        final TextRenderer textRenderer = client.textRenderer;

        ENABLED_BUFFER.clear();
        for (Module module : ModuleManager.INSTANCE.getModules()) {
            if (module.isEnabled()) ENABLED_BUFFER.add(module);
        }
        if (ENABLED_BUFFER.isEmpty()) return;

        ENABLED_BUFFER.sort(Comparator
                .comparingInt((Module m) -> ZenyaFont.width(textRenderer, m.getName()))
                .reversed());
        List<Module> enabledModules = applyLayout(ENABLED_BUFFER, Hud.moduleListLayout());

        final int accent  = ZenyaPlus.getAccentARGB();
        final boolean rb  = Themes.isRainbow();
        final int screenW = client.getWindow().getScaledWidth();
        final int cardH   = textRenderer.fontHeight + PAD_Y * 2;
        int y             = TOP_OFFSET;

        for (int i = 0; i < enabledModules.size(); i++) {
            Module m = enabledModules.get(i);
            String name = m.getName();
            int textW = ZenyaFont.width(textRenderer, name);
            int cardW = textW + PAD_X * 2;
            int x     = screenW - RIGHT_OFFSET - cardW;

            int col = rb ? Themes.rainbowAt(i, 0.10f) : accent;

            // Deep drop shadow
            RenderUtil.drawRoundedRect(context, x + 2, y + 2, cardW, cardH, CARD_RADIUS, SHADOW_COLOR, false);

            // Main card — very dark, solid
            RenderUtil.drawRoundedRect(context, x, y, cardW, cardH, CARD_RADIUS, CARD_COLOR, false);

            // Tuffer accent bar — full height, left-aligned, pill-shaped
            int barColor = (col & 0x00FFFFFF) | 0xDD000000;
            RenderUtil.drawRoundedRect(context, x, y, BAR_W, cardH, CARD_RADIUS, 0, CARD_RADIUS, 0, false, barColor);
            
            // Subtle top-highlight on the bar for a "tuff" 3D look
            RenderUtil.drawRoundedRect(context, x, y, BAR_W, cardH / 2, CARD_RADIUS, 0, 0, 0, false, 0x33FFFFFF);

            // Border
            RenderUtil.drawOutline(context, x, y, cardW, cardH, CARD_RADIUS, 1.0f, OUTLINE_COLOR, false);

            // Module name — slightly offset past the thick bar
            ZenyaFont.draw(context, textRenderer, name,
                    x + PAD_X + 2, y + PAD_Y + 1, col, false);

            y += cardH + GAP;
        }
    }
}
