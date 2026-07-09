package com.zenya.module.modules.misc;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import net.minecraft.client.option.SimpleOption;

public final class CustomFOV extends Module {
    
    private final Setting<Integer> fov = new Setting<>("FOV", 90, 30, 180);
    
    public CustomFOV() {
        super("Custom FOV", Category.MISC);
        setDescription("Allows you to set custom FOV from 30 to 180.");
        addSetting(fov);
    }
    
    @Override
    public void onTick() {
        if (mc.options == null || mc.player == null) return;
        
        SimpleOption<Integer> fovOption = mc.options.getFov();
        if (fovOption != null) {
            fovOption.setValue(fov.getValue());
        }
    }
    
    @Override
    public void onDisable() {
        if (mc.options == null) return;
        SimpleOption<Integer> fovOption = mc.options.getFov();
        if (fovOption != null) {
            fovOption.setValue(90);
        }
    }
}
