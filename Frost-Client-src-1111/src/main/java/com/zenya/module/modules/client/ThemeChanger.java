package com.zenya.module.modules.client;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.ModeSetting;

public final class ThemeChanger extends Module {

    private final ModeSetting theme = new ModeSetting("Theme", "Ocean",
        "Ocean", "Frost", "Lavender", "Emerald", "Gold", "Ruby", "Amethyst", "Mint", "Midnight", "Sakura", "Rose", "Sky", "Forest", "Sunset", "Rainbow");

    public ThemeChanger() {
        super("ThemeChanger", Category.CLIENT);
        setDescription("Quickly switch between multiple premium Frost client color themes.");
        addSetting(theme);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        
        for (Themes.Theme target : Themes.ALL) {
            if (target.name().equalsIgnoreCase(theme.getValue())) {
                Themes.apply(target);
                return;
            }
        }
    }
}
