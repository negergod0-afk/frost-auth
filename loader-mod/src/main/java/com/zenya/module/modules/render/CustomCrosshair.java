package com.zenya.module.modules.render;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.renderer.RenderUtil;
import java.awt.Color;

public final class CustomCrosshair extends Module {

    private static CustomCrosshair INSTANCE;

    private final com.zenya.setting.ModeSetting mode = new com.zenya.setting.ModeSetting("Look", "FS", "Cross", "Circle", "Dot", "FS");
    private final Setting<Color> color = new Setting<>("Color", new Color(255, 255, 255, 255));
    private final Setting<Integer> size = new Setting<>("Size", 4, 1, 10);
    private final Setting<Integer> thickness = new Setting<>("Thickness", 2, 1, 5);

    public CustomCrosshair() {
        super("Custom Crosshair", Category.RENDER);
        setDescription("Changes your crosshair look.");
        addSetting(mode);
        addSetting(color);
        addSetting(size);
        addSetting(thickness);
        INSTANCE = this;
    }

    public static CustomCrosshair getInstance() {
        return INSTANCE;
    }

    /** Called by InGameHudMixin to cancel the vanilla crosshair when this module is on. */
    public static boolean customCrosshairActive() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }

    private static final net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();

    public void renderCrosshair(net.minecraft.client.gui.DrawContext context) {
        if (mc.currentScreen != null) return;

        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();
        float cx = width / 2.0f;
        float cy = height / 2.0f;
        
        String currentMode = mode.getValue();
        int c = color.getValue().getRGB();
        int s = size.getValue();
        int t = thickness.getValue();
        
        if ("Cross".equals(currentMode)) {
            RenderUtil.drawRoundedRect(context, (int)(cx - t/2f), (int)(cy - s), t, s * 2, 0, c, false);
            RenderUtil.drawRoundedRect(context, (int)(cx - s), (int)(cy - t/2f), s * 2, t, 0, c, false);
        } else if ("Circle".equals(currentMode)) {
            RenderUtil.drawOutline(context, (int)(cx - s), (int)(cy - s), s*2, s*2, s, 1.5f, c, false);
        } else if ("Dot".equals(currentMode)) {
            RenderUtil.drawRoundedRect(context, (int)(cx - t), (int)(cy - t), t*2, t*2, t, c, false);
        } else if ("FS".equals(currentMode)) {
            context.drawText(mc.textRenderer, "FS", (int)(cx - mc.textRenderer.getWidth("FS") / 2.0f), (int)(cy - mc.textRenderer.fontHeight / 2.0f), c, false);
        }
    }
}
