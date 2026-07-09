package com.zenya.module.modules.client;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

import java.awt.Color;

public final class ZenyaPlus extends Module {

    private static ZenyaPlus INSTANCE;

    private final Setting<Boolean> animations = new Setting<>("Animations", true);
    private final Setting<Boolean> soundAnimations = new Setting<>("Sound Animations", true);
    private final Setting<Boolean> mcFont = new Setting<>("Original Font", false);
    private final Setting<Integer> guiBind = new Setting<>("ClickGUI Bind", org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
    private final Setting<Integer> backgroundOpacity = new Setting<>("Background Opacity", 180, 0, 255);

    public ZenyaPlus() {
        super("Frost+", Category.CLIENT);
        setDescription("Global Frost client appearance settings.");
        addSetting(animations);
        addSetting(soundAnimations);
        addSetting(mcFont);
        addSetting(guiBind);
        addSetting(backgroundOpacity);
        INSTANCE = this;
    }

    public static int getAccentARGB() {
        Color c = getAccentColor();
        return (c.getAlpha() << 24) | (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
    }

    private static Color accentColor = new Color(59, 130, 246, 255);

    public static Color getAccentColor() {
        return accentColor;
    }

    public static void setAccentColor(Color color) {
        if (color != null) {
            accentColor = color;
        }
    }

    public static boolean blackBackground() {
        return true;
    }

    public static int getBackgroundARGB() {
        if (INSTANCE == null) return 0xFF0C0D12;
        int opacity = INSTANCE.backgroundOpacity.getValue();
        return (opacity << 24) | 0x0C0D12;
    }

    public static Color getBackgroundColor() {
        if (INSTANCE == null) return new Color(12, 13, 18, 255);
        int opacity = INSTANCE.backgroundOpacity.getValue();
        return new Color(12, 13, 18, opacity);
    }

    public static float backgroundDim() {
        return 0.35f;
    }

    public static boolean blurBackgroundEnabled() {
        return false;
    }

    public static boolean animationsEnabled() {
        if (INSTANCE == null) return true;
        Boolean v = INSTANCE.animations.getValue();
        return v == null || v;
    }

    public static boolean soundAnimationsEnabled() {
        if (INSTANCE == null) return true;
        Boolean v = INSTANCE.soundAnimations.getValue();
        return v == null || v;
    }

    public static int menuSizePercent() { return 5; }
    public static float menuSizeRaw() { return 5f; }
    public static String getGuiStyle() { return "GUI 2"; }
    public static void setGuiStyle(String ignored) {}
    public static void setMenuSize(float ignored) {}

    public static boolean useMinecraftFont() {
        if (INSTANCE == null) return false;
        Boolean v = INSTANCE.mcFont.getValue();
        return v != null && v;
    }

    public static int getMenuBind() {
        if (INSTANCE == null) return org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
        return INSTANCE.guiBind.getValue();
    }

    public static void setMenuBind(int keyCode) {
        if (INSTANCE != null) {
            INSTANCE.guiBind.setValue(keyCode);
        }
    }

    public static float tracerLineWidth() {
        return 1.0f;
    }
}
