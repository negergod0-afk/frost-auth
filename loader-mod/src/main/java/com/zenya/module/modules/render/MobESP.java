package com.zenya.module.modules.render;

import com.zenya.module.Category;
import com.zenya.module.Module;
import net.minecraft.entity.EntityType;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/** Stub – MobESP module removed. */
public final class MobESP extends Module {
    private final Map<EntityType<?>, Color> colorMap = new HashMap<>();

    public MobESP() {
        super("Mob ESP", Category.RENDER);
    }

    public Map<EntityType<?>, Color> getColorMap()                         { return colorMap; }
    public void setCustomMobColor(EntityType<?> type, Color color)  {
        if (color == null) colorMap.remove(type);
        else colorMap.put(type, color);
    }
}
