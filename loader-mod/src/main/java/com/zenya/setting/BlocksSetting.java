package com.zenya.setting;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class BlocksSetting extends Setting<Set<Block>> {

    private final List<Block> availableBlocks;
    private final Map<Block, Color> blockColors = new LinkedHashMap<>();
    private long version;

    public BlocksSetting(String name, Block... defaults) {
        super(name, createDefaultSet(defaults));
        this.availableBlocks = Registries.BLOCK.stream()
                .filter(block -> block != Blocks.AIR)
                // Drop blocks that have no item form (fluids, technical blocks, piston-head, etc.).
                // Otherwise the picker grid renders empty "holes" for those entries.
                .filter(block -> !new net.minecraft.item.ItemStack(block).isEmpty())
                .sorted(Comparator.comparing(this::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public void setValue(Set<Block> value) {
        LinkedHashSet<Block> next = new LinkedHashSet<>();
        if (value != null) {
            for (Block block : value) {
                if (block != null && block != Blocks.AIR) {
                    next.add(block);
                }
            }
        }

        if (next.equals(getValue())) {
            return;
        }
        super.setValue(next);
        version++;
    }

    public boolean contains(Block block) {
        return block != null && getValue().contains(block);
    }

    public void toggle(Block block) {
        if (block == null || block == Blocks.AIR) {
            return;
        }

        LinkedHashSet<Block> next = new LinkedHashSet<>(getValue());
        if (!next.add(block)) {
            next.remove(block);
        } else {
            // New block added, ensure it has a color. 
            // We use a default (like red) if not present.
            if (!blockColors.containsKey(block)) {
                blockColors.put(block, new Color(239, 68, 68)); // Frost Red
            }
        }
        setValue(next);
    }

    public Color getColor(Block block) {
        return blockColors.getOrDefault(block, new Color(239, 68, 68));
    }

    public void setColor(Block block, Color color) {
        if (block == null || color == null) return;
        blockColors.put(block, color);
        version++;
    }

    public Map<Block, Color> getColors() {
        return blockColors;
    }

    public void setColors(Map<Block, Color> colors) {
        if (colors == null) return;
        this.blockColors.clear();
        this.blockColors.putAll(colors);
        version++;
    }

    public void clear() {
        if (getValue().isEmpty()) {
            return;
        }
        setValue(Collections.emptySet());
    }

    public int size() {
        return getValue().size();
    }

    public long getVersion() {
        return version;
    }

    public Set<Block> getSelectedBlocks() {
        return Collections.unmodifiableSet(getValue());
    }

    public List<Block> getAvailableBlocks() {
        return availableBlocks;
    }

    public List<Block> filter(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return availableBlocks;
        }

        List<Block> filtered = new ArrayList<>();
        for (Block block : availableBlocks) {
            String displayName = getDisplayName(block).toLowerCase(Locale.ROOT);
            Identifier id = Registries.BLOCK.getId(block);
            String idString = id == null ? "" : id.toString().toLowerCase(Locale.ROOT);
            if (displayName.contains(normalized) || idString.contains(normalized)) {
                filtered.add(block);
            }
        }
        return filtered;
    }

    public String getDisplayName(Block block) {
        try {
            return block.getName().getString();
        } catch (Exception ignored) {
            Identifier id = Registries.BLOCK.getId(block);
            return id == null ? "Block" : id.getPath();
        }
    }

    public String getSummary() {
        if (getValue().isEmpty()) {
            return "None";
        }
        return "Blocks";
    }

    private static Set<Block> createDefaultSet(Block... defaults) {
        LinkedHashSet<Block> selected = new LinkedHashSet<>();
        if (defaults != null) {
            Collections.addAll(selected, defaults);
            selected.remove(null);
            selected.remove(Blocks.AIR);
        }
        return selected;
    }
}
