package com.zenya.module.modules.misc;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

public final class NameProtect extends Module {

    public static NameProtect instance;

    public final Setting<String> fakeName = new Setting<>("FakeName", "§5\uD83D\uDCF9§3+§7NPedro");

    public NameProtect() {
        super("NameProtect", Category.MISC);
        instance = this;
        addSetting(fakeName);
    }

    public String getFakeName() {
        return this.fakeName.getValue();
    }
}
