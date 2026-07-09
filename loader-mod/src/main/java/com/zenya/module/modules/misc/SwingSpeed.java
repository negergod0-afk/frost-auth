package com.zenya.module.modules.misc;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

public final class SwingSpeed extends Module {

    public static SwingSpeed instance;

    private final Setting<Float> swingSpeed = new Setting<>("Swing Speed", 1.0f, 0.1f, 2.0f);

    public SwingSpeed() {
        super("SwingSpeed", Category.MISC);
        setDescription("Adjusts how quickly your arm swing animation plays when attacking or breaking blocks for a smoother visual feel.");
        instance = this;
        addSetting(swingSpeed);
    }

    public float getSwingSpeed() {
        float value = swingSpeed.getValue() == null ? 1.0f : swingSpeed.getValue();
        if (value < 0.1f) {
            return 0.1f;
        }
        return Math.min(value, 2.0f);
    }
}
