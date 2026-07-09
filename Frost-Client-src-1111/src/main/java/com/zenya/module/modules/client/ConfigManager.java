package com.zenya.module.modules.client;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.ActionSetting;
import com.zenya.setting.ModeSetting;
import com.zenya.setting.Setting;

public final class ConfigManager extends Module {
    private final ModeSetting autoSave = new ModeSetting("Auto Save", "On Change", "On Change", "Every Minute", "Every 5 Minutes", "Off");
    private final Setting<Boolean> loadOnStart = new Setting<>("Load On Launch", true);
    private final Setting<Boolean> notifyOnSave = new Setting<>("Save Notification", false);
    private final ActionSetting saveNow = new ActionSetting("Save Now", "Save", () -> {
        com.zenya.module.ModuleManager.INSTANCE.saveConfig();
    });
    private final ActionSetting loadNow = new ActionSetting("Load Now", "Load", () -> {
        com.zenya.module.ModuleManager.INSTANCE.loadConfig();
    });

    public ConfigManager() {
        super("Config Manager", Category.CLIENT);
        setDescription("Manage and share your Frost client configs.");
        addSetting(autoSave);
        addSetting(loadOnStart);
        addSetting(notifyOnSave);
        addSetting(saveNow);
        addSetting(loadNow);
    }

    public String getAutoSaveMode() { return autoSave.getValue(); }
    public boolean isLoadOnStart() { return loadOnStart.getValue(); }
    public boolean isNotifyOnSave() { return notifyOnSave.getValue(); }
}
