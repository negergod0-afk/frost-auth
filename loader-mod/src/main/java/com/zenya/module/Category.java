package com.zenya.module;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public enum Category {
    COMBAT("Combat"),
    RENDER("Render"),
    DONUT("Donut"),
    SMPS("SMP's"),
    MISC("Misc"),
    CLIENT("Client");

    private final String name;

    Category(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public ItemStack getIcon() {
        // Return empty stack - we'll draw custom white icons instead
        return ItemStack.EMPTY;
    }

    public String getIconShape() {
        return switch (this) {
            case COMBAT -> "combat";
            case RENDER -> "render";
            case DONUT  -> "donut";
            case SMPS   -> "smps";
            case MISC   -> "layers";
            case CLIENT -> "client";
        };
    }
}
