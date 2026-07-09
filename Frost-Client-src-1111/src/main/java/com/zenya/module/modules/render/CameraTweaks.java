package com.zenya.module.modules.render;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.ModuleManager;
import com.zenya.setting.Setting;

public final class CameraTweaks extends Module {
    private final Setting<Boolean> clip = new Setting<>("Clip", true);
    private final Setting<Float> cameraDistance = new Setting<>("Camera Distance", 4.0f, 0.0f, 20.0f);
    private final Setting<Boolean> scrollingEnabled = new Setting<>("Scroll Zoom", true);
    private final Setting<Float> scrollSensitivity = new Setting<>("Scroll Sensitivity", 1.0f, 0.01f, 5.0f);

    private double distance;
    private float lastSliderValue = -1.0f;

    public CameraTweaks() {
        super("Camera Tweaks", Category.RENDER);
        setDescription("Modify third-person camera clip, distance, scroll-zoom");
        addSetting(clip);
        addSetting(cameraDistance);
        addSetting(scrollingEnabled);
        addSetting(scrollSensitivity);
    }

    @Override
    public void onEnable() {
        this.distance = cameraDistance.getValue();
        this.lastSliderValue = cameraDistance.getValue();
    }

    public static CameraTweaks get() {
        Module m = ModuleManager.INSTANCE.getModuleByName("Camera Tweaks");
        return m instanceof CameraTweaks ? (CameraTweaks) m : null;
    }

    public boolean shouldClip() {
        return clip.getValue();
    }

    public double getDistance() {
        return distance;
    }

    @Override
    public void onTick() {
        float sliderVal = cameraDistance.getValue();
        if (sliderVal != lastSliderValue) {
            this.distance = sliderVal;
            this.lastSliderValue = sliderVal;
        }
    }

    public boolean isScrollingEnabled() {
        return scrollingEnabled.getValue();
    }

    public boolean onMouseScroll(double d) {
        if (!isEnabled() || !scrollingEnabled.getValue()) return false;
        float sens = scrollSensitivity.getValue();
        distance = Math.max(0.0, Math.min(20.0, distance - d * sens * 0.1));
        cameraDistance.setValue((float) distance);
        lastSliderValue = (float) distance;
        return true;
    }
}
