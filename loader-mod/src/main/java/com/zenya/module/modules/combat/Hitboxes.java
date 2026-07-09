package com.zenya.module.modules.combat;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

public final class Hitboxes extends Module {
    private final Setting<Float> expandAmount = new Setting<>("Expand", 0.1f, 0.0f, 1.0f);
    private final Setting<Boolean> playersOnly = new Setting<>("Players Only", true);

    public Hitboxes() {
        super("Hitboxes", Category.COMBAT);
        addSetting(expandAmount);
        addSetting(playersOnly);
    }

    public float getExpand() {
        return expandAmount.getValue();
    }

    public boolean isPlayersOnly() {
        return playersOnly.getValue();
    }
}
