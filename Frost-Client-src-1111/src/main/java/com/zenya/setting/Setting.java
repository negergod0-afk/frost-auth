package com.zenya.setting;

import com.zenya.module.ModuleManager;

import java.util.Objects;
import java.util.function.Supplier;

public class Setting<T> {
    private final String name;
    private final T defaultValue;
    private T value;
    private T min;
    private T max;
    private Supplier<Boolean> visibility = () -> true;

    public Setting(String name, T value) {
        this.name = name;
        this.value = value;
        this.defaultValue = value;
    }

    public Setting(String name, T value, T min, T max) {
        this.name = name;
        this.value = value;
        this.defaultValue = value;
        this.min = min;
        this.max = max;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        if (name == null || name.isBlank()) {
            return "";
        }
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "y-level" -> "Y Level";
            case "maxy" -> "Max Y";
            case "chunkradius" -> "Chunk Radius";
            case "fakename" -> "Fake Name";
            case "mainhand" -> "Main Hand";
            case "offhand" -> "Off Hand";
            case "blockcolors" -> "Block Colors";
            case "mobcolors" -> "Mob Colors";
            case "opacity" -> "Alpha";
            case "outline color" -> "Outline Color";
            case "fill color" -> "Fill Color";
            case "tracer color" -> "Tracer Color";
            case "chest color" -> "Chest Color";
            case "ender chests" -> "Ender Chests";
            case "shulker boxes" -> "Shulker Boxes";
            case "enchanting tables" -> "Enchanting Tables";
            case "trapped chest" -> "Trapped Chest";
            case "ender chest" -> "Ender Chest";
            default -> splitCamelCase(name);
        };
    }

    public T getValue() {
        return value;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public boolean matchesName(String settingName) {
        return name.equalsIgnoreCase(settingName);
    }

    private static String splitCamelCase(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        char prev = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            char next = i + 1 < value.length() ? value.charAt(i + 1) : 0;
            if (i > 0 && c != ' ' && (
                    Character.isUpperCase(c) && Character.isLowerCase(prev)
                            || Character.isUpperCase(c) && Character.isUpperCase(prev) && Character.isLowerCase(next)
                            || Character.isDigit(c) && !Character.isDigit(prev) && prev != ' '
            )) {
                out.append(' ');
            }
            out.append(c);
            prev = c;
        }
        return out.toString();
    }

    public Setting<T> visibleWhen(Supplier<Boolean> visibility) {
        this.visibility = visibility == null ? () -> true : visibility;
        return this;
    }

    public boolean isVisible() {
        try {
            return visibility == null || visibility.get();
        } catch (Exception ignored) {
            return true;
        }
    }

    public void setValue(T value) {
        if (Objects.equals(this.value, value)) {
            return;
        }
        this.value = value;
        ModuleManager.INSTANCE.onSettingChanged();
    }

    public T getMin() {
        return min;
    }

    public T getMax() {
        return max;
    }
}
