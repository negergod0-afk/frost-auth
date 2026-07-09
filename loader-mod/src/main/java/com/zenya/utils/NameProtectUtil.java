package com.zenya.utils;

import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;

public final class NameProtectUtil {
    private NameProtectUtil() {}

    public static String replace(String text) {
        if (text == null || text.isEmpty()) return text;
        com.zenya.module.modules.misc.NameProtect nameProtect = (com.zenya.module.modules.misc.NameProtect) com.zenya.module.ModuleManager.INSTANCE.getModuleByName("NameProtect");
        if (nameProtect != null && nameProtect.isEnabled()) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            String username = null;
            if (mc.getSession() != null) {
                username = mc.getSession().getUsername();
            } else if (mc.player != null) {
                username = mc.player.getName().getString();
            }
            if (username != null && !username.isEmpty()) {
                String fake = nameProtect.getFakeName();
                if (fake != null) {
                    text = text.replace(username, fake);
                }
            }
        }
        return text;
    }

    public static OrderedText replace(OrderedText orderedText) {
        if (orderedText == null) return null;
        com.zenya.module.modules.misc.NameProtect nameProtect = (com.zenya.module.modules.misc.NameProtect) com.zenya.module.ModuleManager.INSTANCE.getModuleByName("NameProtect");
        if (nameProtect != null && nameProtect.isEnabled()) {
            StringBuilder sb = new StringBuilder();
            orderedText.accept((index, style, codePoint) -> {
                sb.appendCodePoint(codePoint);
                return true;
            });
            String original = sb.toString();
            String replaced = replace(original);
            if (!original.equals(replaced)) {
                return Text.literal(replaced).asOrderedText();
            }
        }
        return orderedText;
    }

    public static StringVisitable replace(StringVisitable v) {
        if (v == null) return null;
        com.zenya.module.modules.misc.NameProtect nameProtect = (com.zenya.module.modules.misc.NameProtect) com.zenya.module.ModuleManager.INSTANCE.getModuleByName("NameProtect");
        if (nameProtect != null && nameProtect.isEnabled()) {
            String original = v.getString();
            String replaced = replace(original);
            if (!original.equals(replaced)) {
                return Text.literal(replaced);
            }
        }
        return v;
    }
}
