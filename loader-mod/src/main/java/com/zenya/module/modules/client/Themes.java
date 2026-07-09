package com.zenya.module.modules.client;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public final class Themes extends Module {
    public static final List<Theme> ALL = new ArrayList<>();

    private static final Theme OCEAN = theme("Ocean", "Cold clean blue", 0xFF3B82F6,
            0xFF0B1220, 0xFF0E7490, 0xFF38BDF8, 0xFF60A5FA);
    private static final Theme FROST = theme("Frost", "Sharp ice red", 0xFFEF4444,
            0xFF111827, 0xFF7F1D1D, 0xFFEF4444, 0xFFFCA5A5);
    private static final Theme LAVENDER = theme("Lavender", "Soft violet glow", 0xFFA78BFA,
            0xFF171322, 0xFF6D28D9, 0xFFA78BFA, 0xFFE9D5FF);
    private static final Theme EMERALD = theme("Emerald", "Bright green glass", 0xFF10B981,
            0xFF071711, 0xFF047857, 0xFF10B981, 0xFFA7F3D0);
    private static final Theme GOLD = theme("Gold", "Warm premium gold", 0xFFFBBF24,
            0xFF181308, 0xFFB45309, 0xFFFBBF24, 0xFFFDE68A);
    private static final Theme RUBY = theme("Ruby", "Deep red contrast", 0xFFE11D48,
            0xFF18080D, 0xFF9F1239, 0xFFE11D48, 0xFFFB7185);
    private static final Theme AMETHYST = theme("Amethyst", "Purple crystal", 0xFF8B5CF6,
            0xFF151022, 0xFF5B21B6, 0xFF8B5CF6, 0xFFC4B5FD);
    private static final Theme MINT = theme("Mint", "Fresh cyan mint", 0xFF34D399,
            0xFF071714, 0xFF0F766E, 0xFF34D399, 0xFF99F6E4);
    private static final Theme MIDNIGHT = theme("Midnight", "Quiet blue night", 0xFF64748B,
            0xFF020617, 0xFF1E293B, 0xFF64748B, 0xFFCBD5E1);
    private static final Theme SAKURA = theme("Sakura", "Light pink bloom", 0xFFFB7185,
            0xFF1A0B11, 0xFFBE123C, 0xFFFB7185, 0xFFFFCDD5);
    private static final Theme ROSE = theme("Rose", "Hot rose neon", 0xFFFF007F,
            0xFF180611, 0xFFBE185D, 0xFFFF007F, 0xFFF9A8D4);
    private static final Theme SKY = theme("Sky", "Clear cyan blue", 0xFF00CCFF,
            0xFF07131A, 0xFF0369A1, 0xFF00CCFF, 0xFFBAE6FD);
    private static final Theme FOREST = theme("Forest", "Dark green field", 0xFF228B22,
            0xFF07120A, 0xFF166534, 0xFF22C55E, 0xFFBBF7D0);
    private static final Theme SUNSET = theme("Sunset", "Orange red dusk", 0xFFFF4500,
            0xFF190B05, 0xFFC2410C, 0xFFFF4500, 0xFFFED7AA);
    private static final Theme RAINBOW = theme("Rainbow", "Animated spectrum", 0xFFFFFFFF,
            0xFFEF4444, 0xFFF59E0B, 0xFF10B981, 0xFF3B82F6, 0xFF8B5CF6);

    static {
        ALL.add(OCEAN);
        ALL.add(FROST);
        ALL.add(LAVENDER);
        ALL.add(EMERALD);
        ALL.add(GOLD);
        ALL.add(RUBY);
        ALL.add(AMETHYST);
        ALL.add(MINT);
        ALL.add(MIDNIGHT);
        ALL.add(SAKURA);
        ALL.add(ROSE);
        ALL.add(SKY);
        ALL.add(FOREST);
        ALL.add(SUNSET);
        ALL.add(RAINBOW);
    }

    private static final class Holder {
        private static final Themes INSTANCE = new Themes();
    }

    public static Themes getInstance() {
        return Holder.INSTANCE;
    }

    private final Setting<String> themeSetting;

    private Themes() {
        super("Themes", Category.CLIENT);
        setDescription("Choose the Frost client colour theme.");
        themeSetting = new Setting<>("Theme", OCEAN.name());
        addSetting(themeSetting);
        ZenyaPlus.setAccentColor(new Color(OCEAN.accentArgb(), true));
    }

    public static Color getAccent() {
        return new Color(currentTheme().accentArgb(), true);
    }

    public static boolean isRainbow() {
        return "Rainbow".equalsIgnoreCase(Holder.INSTANCE.themeSetting.getValue());
    }

    public static int rainbowAt(int index, float speed) {
        float hue = (System.currentTimeMillis() % 6000L) / 6000.0f + index * Math.max(0.01f, speed);
        return 0xFF000000 | (Color.HSBtoRGB(hue % 1.0f, 0.85f, 1.0f) & 0x00FFFFFF);
    }

    public static Theme currentTheme() {
        String selected = Holder.INSTANCE.themeSetting.getValue();
        for (Theme theme : ALL) {
            if (theme.name().equalsIgnoreCase(selected)) return theme;
        }
        return OCEAN;
    }

    public static void apply(Theme theme) {
        if (theme == null) theme = OCEAN;
        Holder.INSTANCE.themeSetting.setValue(theme.name());
        ZenyaPlus.setAccentColor(new Color(theme.accentArgb(), true));
    }

    public Setting<String> selectedSetting() {
        return themeSetting;
    }

    public Theme getActive() {
        return currentTheme();
    }

    public void setActive(Theme theme) {
        apply(theme);
    }

    private static Theme theme(String name, String description, int accentArgb, int... palette) {
        return new Theme(name, description, accentArgb, palette);
    }

    public record Theme(String name, String description, int accentArgb, int[] palette) {
    }
}
