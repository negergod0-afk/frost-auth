package com.zenya.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.registry.Registries;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

public final class InventoryUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void setSelectedSlot(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
    }

    public static boolean switchToHotbar(Predicate<Item> item) {
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; ++i) {
            ItemStack itemStack = inv.getStack(i);
            if (!item.test(itemStack.getItem())) continue;
            inv.setSelectedSlot(i);
            return true;
        }
        return false;
    }

    public static boolean switchToHotbar(Item item) {
        return InventoryUtils.switchToHotbar((Item i) -> i == item);
    }

    public static boolean hasItemInHotbar(Predicate<Item> item) {
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; ++i) {
            ItemStack itemStack = inv.getStack(i);
            if (!item.test(itemStack.getItem())) continue;
            return true;
        }
        return false;
    }

    public static int countItem(Predicate<Item> item) {
        PlayerInventory inv = mc.player.getInventory();
        int count = 0;
        for (int i = 0; i < 36; ++i) {
            ItemStack itemStack = inv.getStack(i);
            if (!item.test(itemStack.getItem())) continue;
            count += itemStack.getCount();
        }
        return count;
    }

    public static int countItemInInventory(Predicate<Item> item) {
        PlayerInventory inv = mc.player.getInventory();
        int count = 0;
        for (int i = 9; i < 36; ++i) {
            ItemStack itemStack = inv.getStack(i);
            if (!item.test(itemStack.getItem())) continue;
            count += itemStack.getCount();
        }
        return count;
    }

    public static int findSwordSlot() {
        PlayerInventory playerInventory = mc.player.getInventory();
        for (int itemIndex = 0; itemIndex < 9; ++itemIndex) {
            if (!playerInventory.getStack(itemIndex).isIn(ItemTags.SWORDS)) continue;
            return itemIndex;
        }
        return -1;
    }

    public static boolean switchToSword() {
        int itemIndex = InventoryUtils.findSwordSlot();
        if (itemIndex != -1) {
            InventoryUtils.setSelectedSlot(itemIndex);
            return true;
        }
        return false;
    }

    public static int findPotionSlot(StatusEffect type, int duration, int amplifier) {
        PlayerInventory inv = mc.player.getInventory();
        StatusEffectInstance potion = new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(type), duration, amplifier);
        for (int i = 0; i < 9; ++i) {
            ItemStack itemStack = inv.getStack(i);
            if (!(itemStack.getItem() instanceof PotionItem)) continue;
            String s = ((PotionContentsComponent)itemStack.get(DataComponentTypes.POTION_CONTENTS)).toString();
            if (!s.contains(potion.toString())) continue;
            return i;
        }
        return -1;
    }

    public static boolean hasPotion(StatusEffect type, int duration, int amplifier, ItemStack itemStack) {
        StatusEffectInstance potion = new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(type), duration, amplifier);
        return itemStack.getItem() instanceof PotionItem && ((PotionContentsComponent)itemStack.get(DataComponentTypes.POTION_CONTENTS)).toString().contains(potion.toString());
    }

    public static int findFirstTotemSlot() {
        PlayerInventory inv = mc.player.getInventory();
        for (int index = 9; index < 36; ++index) {
            if (inv.getStack(index).getItem() != Items.TOTEM_OF_UNDYING) continue;
            return index;
        }
        return -1;
    }

    public static boolean switchToAxe() {
        int itemIndex = InventoryUtils.findAxeSlot();
        if (itemIndex != -1) {
            mc.player.getInventory().setSelectedSlot(itemIndex);
            return true;
        }
        return false;
    }

    public static int findRandomTotemSlot() {
        PlayerInventory inventory = mc.player.getInventory();
        Random random = new Random();
        ArrayList<Integer> totemIndexes = new ArrayList<Integer>();
        for (int i = 9; i < 36; ++i) {
            if (inventory.getStack(i).getItem() != Items.TOTEM_OF_UNDYING) continue;
            totemIndexes.add(i);
        }
        if (!totemIndexes.isEmpty()) {
            return totemIndexes.get(random.nextInt(totemIndexes.size()));
        }
        return -1;
    }

    public static int findPotionByName(String potion) {
        PlayerInventory inventory = mc.player.getInventory();
        Random random = new Random();
        int slotIndex = random.nextInt(27) + 9;
        for (int i = 0; i < 27; ++i) {
            int index = (slotIndex + i) % 36;
            ItemStack itemStack = inventory.getStack(index);
            if (!(itemStack.getItem() instanceof PotionItem) || index >= 36) continue;
            if (!((PotionContentsComponent)itemStack.get(DataComponentTypes.POTION_CONTENTS)).toString().contains(potion.toString())) {
                return -1;
            }
            return index;
        }
        return -1;
    }

    public static int findPotionEffect(StatusEffect effect, int duration, int amplifier) {
        PlayerInventory inv = mc.player.getInventory();
        StatusEffectInstance instance = new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(effect), duration, amplifier);
        for (int index = 9; index < 34; ++index) {
            if (!(inv.getStack(index).getItem() instanceof PotionItem)) continue;
            String s = ((PotionContentsComponent)inv.getStack(index).get(DataComponentTypes.POTION_CONTENTS)).toString();
            if (!s.contains(instance.toString())) continue;
            return index;
        }
        return -1;
    }

    public static List<Integer> findEmptySlots() {
        PlayerInventory inventory = mc.player.getInventory();
        ArrayList<Integer> slots = new ArrayList<Integer>();
        for (int i = 0; i < 9; ++i) {
            if (inventory.getStack(i).isEmpty()) {
                slots.add(i);
            }
        }
        return slots;
    }

    public static int findAxeSlot() {
        PlayerInventory playerInventory = mc.player.getInventory();
        for (int itemIndex = 0; itemIndex < 9; ++itemIndex) {
            if (!(playerInventory.getStack(itemIndex).getItem() instanceof AxeItem)) continue;
            return itemIndex;
        }
        return -1;
    }

    public static int countItem(Item item) {
        return InventoryUtils.countItem((Item i) -> i == item);
    }
}
