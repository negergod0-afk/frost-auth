package com.zenya.setting;

import net.minecraft.item.ItemStack;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StorageBlocksSetting extends Setting<Set<String>> {

    public record Entry(String value, String label, ItemStack icon, Color defaultColor) {}

    private final List<Entry> options;
    private final Map<String, Color> colors = new LinkedHashMap<>();
    private final Map<String, Setting<Color>> colorSettings = new LinkedHashMap<>();

    public StorageBlocksSetting(String name, Entry... options) {
        super(name, new LinkedHashSet<>());
        this.options = List.of(options);
        for (Entry entry : options) {
            colors.put(entry.value(), entry.defaultColor());
        }
    }

    public List<Entry> getOptions() {
        return options;
    }

    public Entry findEntry(String value) {
        for (Entry entry : options) {
            if (entry.value().equalsIgnoreCase(value)) {
                return entry;
            }
        }
        return null;
    }

    public Set<String> getSelected() {
        return getValue();
    }

    public boolean isSelected(String value) {
        return getValue().contains(value);
    }

    public List<Entry> getSelectedEntries() {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : options) {
            if (isSelected(entry.value())) {
                out.add(entry);
            }
        }
        return out;
    }

    public void toggle(String value) {
        Entry entry = findEntry(value);
        if (entry == null) {
            return;
        }
        LinkedHashSet<String> next = new LinkedHashSet<>(getValue());
        if (next.contains(entry.value())) {
            next.remove(entry.value());
        } else {
            next.add(entry.value());
        }
        setValue(next);
    }

    public Color getColor(String value) {
        Color c = colors.get(value);
        if (c != null) return c;
        Entry e = findEntry(value);
        return e != null ? e.defaultColor() : Color.WHITE;
    }

    public void setColor(String value, Color color) {
        if (findEntry(value) == null || color == null) {
            return;
        }
        colors.put(value, color);
        // Re-publish the value to trigger onSettingChanged → config save.
        setValue(new LinkedHashSet<>(getValue()));
    }

    /**
     * Returns a thin {@link Setting} wrapper around a single entry's colour. Used to feed
     * the existing colour-picker UI (which expects a {@code Setting<Color>}) — writes go
     * straight back into our internal colour map, reads always pull the live value.
     */
    public Setting<Color> colorSettingFor(String value) {
        return colorSettings.computeIfAbsent(value, key -> new Setting<Color>(labelFor(key), getColor(key)) {
            @Override
            public Color getValue() {
                return getColor(value);
            }

            @Override
            public void setValue(Color color) {
                setColor(value, color);
            }
        });
    }

    private String labelFor(String value) {
        Entry e = findEntry(value);
        return e != null ? e.label() : value;
    }

    /** Returns an unmodifiable snapshot of the color map for serialization. */
    public Map<String, Color> getColorsSnapshot() {
        return new LinkedHashMap<>(colors);
    }

    /** Restores colors loaded from config. Unknown keys are ignored. */
    public void restoreColors(Map<String, Color> loaded) {
        if (loaded == null) return;
        for (Map.Entry<String, Color> entry : loaded.entrySet()) {
            if (findEntry(entry.getKey()) != null && entry.getValue() != null) {
                colors.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
