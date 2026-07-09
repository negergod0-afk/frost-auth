package com.zenya.gui;

import com.zenya.module.Category;
import com.zenya.utils.renderer.RenderUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public final class CategoryIconRenderer {

    private static final Identifier COMBAT = Identifier.of("zenya", "textures/gui/icons/combat.png");
    private static final Identifier RENDER = Identifier.of("zenya", "textures/gui/icons/render.png");
    private static final Identifier DONUT  = Identifier.of("zenya", "textures/gui/icons/donut.png");
    private static final Identifier SMPS   = Identifier.of("zenya", "textures/gui/icons/smps.png");
    private static final Identifier MISC   = Identifier.of("zenya", "textures/gui/icons/misc.png");
    private static final Identifier LAYERS = Identifier.of("zenya", "textures/gui/icons/layers.png");
    private static final Identifier CLIENT = Identifier.of("zenya", "textures/gui/icons/client.png");

    private CategoryIconRenderer() {}

    public static void draw(DrawContext context, int x, int y, int size, Category category, int color) {
        draw(context, x, y, size, category.getIconShape(), color);
    }

    public static void draw(DrawContext context, int x, int y, int size, String shape, int color) {
        if (((color >>> 24) & 0xFF) == 0) {
            return;
        }
        Identifier texture = resolve(shape);
        if (texture == null) {
            return;
        }
        RenderUtil.drawTexture(context, x, y, size, texture, color, 0f, false);
    }

    private static Identifier resolve(String shape) {
        return switch (shape) {
            case "combat", "sword", "swords" -> COMBAT;
            case "render", "eye" -> RENDER;
            case "donut", "circle" -> DONUT;
            case "smps", "smp", "list" -> SMPS;
            case "layers" -> LAYERS;
            case "misc", "star" -> MISC;
            case "client", "settings" -> CLIENT;
            default -> MISC;
        };
    }
}
