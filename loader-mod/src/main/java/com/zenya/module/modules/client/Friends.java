package com.zenya.module.modules.client;

import com.zenya.module.Category;
import com.zenya.module.Module;
import java.awt.Color;

/** Stub – Friends module removed. All calls are no-ops / safe defaults. */
public final class Friends extends Module {
    public Friends() {
        super("Friends", Category.CLIENT);
    }

    public static boolean isFriend(String name)        { return false; }
    public static boolean isAutoLog()                  { return false; }
    public static boolean isAntiTriggerbot()           { return false; }
    public static boolean isEspColor()                 { return false; }
    public static Color   getColor()                   { return new Color(0, 200, 255); }
}
