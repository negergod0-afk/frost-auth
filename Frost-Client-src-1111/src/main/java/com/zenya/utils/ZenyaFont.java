package com.zenya.utils;

import com.zenya.module.modules.client.ZenyaPlus;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ZenyaFont {

    /** The custom font Identifier matching {@code assets/zenya/font/zenya.json}. */
    public static final Identifier ID = Identifier.of("zenya", "zenya");
    /** Larger SemiBold variant — used for headings like the Themes view title. */
    public static final Identifier HEADING_ID = Identifier.of("zenya", "zenya_heading");

    private static final float MC_FONT_SCALE = 1.15f;

    private static final StyleSpriteSource.Font FONT_SOURCE = new StyleSpriteSource.Font(ID);
    private static final StyleSpriteSource.Font HEADING_FONT_SOURCE = new StyleSpriteSource.Font(HEADING_ID);
    private static final Style STYLE = Style.EMPTY.withFont(FONT_SOURCE);
    private static final Style HEADING_STYLE = Style.EMPTY.withFont(HEADING_FONT_SOURCE);

    private ZenyaFont() {}

    private static boolean mc() { return ZenyaPlus.useMinecraftFont(); }

    /** Wrap a plain string in {@link Text}. When the user picked MC font in
     *  Zenya+, returns a styleless literal so the vanilla font is used. */
    public static Text text(String s) {
        Text base = Text.literal(s == null ? "" : s);
        return mc() ? base : base.copy().setStyle(STYLE);
    }

    /** Wrap a plain string in {@link Text} with the Zenya HEADING font style.
     *  Falls back to a plain literal (vanilla font) when MC font is selected. */
    public static Text heading(String s) {
        Text base = Text.literal(s == null ? "" : s);
        return mc() ? base : base.copy().setStyle(HEADING_STYLE);
    }

    public static void drawHeading(DrawContext context, TextRenderer tr, String s, int x, int y, int color) {
        if (mc()) { drawScaled(context, tr, Text.literal(s), x, y, color, false, MC_FONT_SCALE * 1.5f); return; }
        context.drawText(tr, heading(s), x, y, color, false);
    }

    public static int headingWidth(TextRenderer tr, String s) {
        if (s == null) return 0;
        if (mc()) return Math.round(tr.getWidth(s) * MC_FONT_SCALE * 1.5f);
        return tr.getWidth(heading(s));
    }

    /** Wrap an existing {@link Text} so the Zenya font is used. Preserves other style bits.
     *  When MC font is selected, returns the original Text untouched. */
    public static Text wrap(Text t) {
        if (t == null) return null;
        if (mc()) return t;
        Style merged = t.getStyle().withFont(FONT_SOURCE);
        return t.copy().setStyle(merged);
    }

    /** {@code context.drawText(textRenderer, str, x, y, color, shadow)} but with the Zenya font. */
    public static void draw(DrawContext context, TextRenderer tr, String s, int x, int y, int color, boolean shadow) {
        if (mc()) { drawScaled(context, tr, Text.literal(s == null ? "" : s), x, y, color, shadow, MC_FONT_SCALE); return; }
        context.drawText(tr, text(s), x, y, color, shadow);
    }

    /** Variant for already-styled {@link Text}. Forces the Zenya font onto the text. */
    public static void draw(DrawContext context, TextRenderer tr, Text t, int x, int y, int color, boolean shadow) {
        if (mc()) { drawScaled(context, tr, t == null ? Text.empty() : t, x, y, color, shadow, MC_FONT_SCALE); return; }
        context.drawText(tr, wrap(t), x, y, color, shadow);
    }

    /** Variant for {@link OrderedText}. We can't restyle OrderedText, so this passes through. */
    public static void draw(DrawContext context, TextRenderer tr, OrderedText t, int x, int y, int color, boolean shadow) {
        if (mc()) { drawScaledOrdered(context, tr, t, x, y, color, shadow, MC_FONT_SCALE); return; }
        context.drawText(tr, t, x, y, color, shadow);
    }

    /** {@code context.drawTextWithShadow(textRenderer, str, x, y, color)} but with the Zenya font. */
    public static void drawShadow(DrawContext context, TextRenderer tr, String s, int x, int y, int color) {
        if (mc()) { drawScaled(context, tr, Text.literal(s == null ? "" : s), x, y, color, true, MC_FONT_SCALE); return; }
        context.drawTextWithShadow(tr, text(s), x, y, color);
    }

    public static void drawShadow(DrawContext context, TextRenderer tr, Text t, int x, int y, int color) {
        if (mc()) { drawScaled(context, tr, t == null ? Text.empty() : t, x, y, color, true, MC_FONT_SCALE); return; }
        context.drawTextWithShadow(tr, wrap(t), x, y, color);
    }

    public static void drawShadow(DrawContext context, TextRenderer tr, OrderedText t, int x, int y, int color) {
        if (mc()) { drawScaledOrdered(context, tr, t, x, y, color, true, MC_FONT_SCALE); return; }
        context.drawTextWithShadow(tr, t, x, y, color);
    }

    /** Centred draw helper — many of our HUD components draw text centred at a given point. */
    public static void drawCentered(DrawContext context, TextRenderer tr, String s, int centerX, int y, int color, boolean shadow) {
        int w = width(tr, s);
        if (mc()) { drawScaled(context, tr, Text.literal(s == null ? "" : s), centerX - w / 2, y, color, shadow, MC_FONT_SCALE); return; }
        Text t = text(s);
        context.drawText(tr, t, centerX - w / 2, y, color, shadow);
    }

    /** Width of a string when laid out in the Zenya font. */
    public static int width(TextRenderer tr, String s) {
        if (s == null) return 0;
        if (mc()) return Math.round(tr.getWidth(s) * MC_FONT_SCALE);
        return tr.getWidth(text(s));
    }

    public static int width(TextRenderer tr, Text t) {
        if (t == null) return 0;
        if (mc()) return Math.round(tr.getWidth(t) * MC_FONT_SCALE);
        return tr.getWidth(wrap(t));
    }

    public static int width(TextRenderer tr, OrderedText t) {
        if (t == null) return 0;
        if (mc()) return Math.round(tr.getWidth(t) * MC_FONT_SCALE);
        return tr.getWidth(t);
    }

    /** Convert a plain string to an {@link OrderedText} suitable for the {@code drawTrimmed} family. */
    public static OrderedText ordered(String s) {
        return text(s).asOrderedText();
    }

    // ── Scaled MC-font draw helpers ─────────────────────────────────────────
    // MC's font is a fixed-pixel bitmap; the only way to draw it larger is to
    // scale the matrix. We translate to (x, y) first so the scaling pivots on
    // the text's top-left corner instead of the screen origin.

    private static void drawScaled(DrawContext ctx, TextRenderer tr, Text t,
                                   int x, int y, int color, boolean shadow, float scale) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) x, (float) y);
        ctx.getMatrices().scale(scale, scale);
        ctx.drawText(tr, t, 0, 0, color, shadow);
        ctx.getMatrices().popMatrix();
    }

    private static void drawScaledOrdered(DrawContext ctx, TextRenderer tr, OrderedText t,
                                          int x, int y, int color, boolean shadow, float scale) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) x, (float) y);
        ctx.getMatrices().scale(scale, scale);
        if (shadow) ctx.drawTextWithShadow(tr, t, 0, 0, color);
        else        ctx.drawText(tr, t, 0, 0, color, false);
        ctx.getMatrices().popMatrix();
    }
}
