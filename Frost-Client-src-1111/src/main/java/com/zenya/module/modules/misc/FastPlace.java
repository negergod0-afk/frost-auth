package com.zenya.module.modules.misc;

import com.zenya.mixin.MinecraftClientAccessor;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;

public final class FastPlace extends Module {

    private final Setting<Boolean> onlyXP = new Setting<>("Only XP", false);
    private final Setting<Boolean> allowBlocks = new Setting<>("Blocks", true);
    private final Setting<Boolean> allowItems = new Setting<>("Items", true);
    private final Setting<Float> useDelay = new Setting<>("Delay", 0.0f, 0.0f, 10.0f);

    public FastPlace() {
        super("Fast Place", Category.MISC);
        setDescription("Removes the right-click placement cooldown so blocks and items like experience bottles can be used as fast as you click.");
        addSetting(onlyXP);
        addSetting(allowBlocks);
        addSetting(allowItems);
        addSetting(useDelay);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.currentScreen != null) {
            return;
        }
        if (!mc.options.useKey.isPressed()) {
            return;
        }

        ItemStack mainHand = mc.player.getMainHandStack();
        ItemStack offHand = mc.player.getOffHandStack();
        if (!shouldAffectCooldown(mainHand, offHand)) {
            return;
        }

        MinecraftClientAccessor accessor = (MinecraftClientAccessor) mc;
        int targetCooldown = Math.max(0, useDelay.getValue().intValue());
        if (accessor.zenya$getItemUseCooldown() != targetCooldown) {
            accessor.zenya$setItemUseCooldown(targetCooldown);
        }
    }

    private boolean shouldAffectCooldown(ItemStack mainHand, ItemStack offHand) {
        boolean mainXp = mainHand.isOf(Items.EXPERIENCE_BOTTLE);
        boolean offXp = offHand.isOf(Items.EXPERIENCE_BOTTLE);
        if (onlyXP.getValue()) {
            return mainXp || offXp;
        }

        Item mainItem = mainHand.getItem();
        Item offItem = offHand.getItem();
        if (isFood(mainHand) || isFood(offHand)) {
            return false;
        }
        if (mainHand.isOf(Items.RESPAWN_ANCHOR)
                || mainHand.isOf(Items.GLOWSTONE)
                || offHand.isOf(Items.RESPAWN_ANCHOR)
                || offHand.isOf(Items.GLOWSTONE)) {
            return false;
        }
        if (mainItem instanceof RangedWeaponItem || offItem instanceof RangedWeaponItem) {
            return false;
        }

        boolean hasBlockItem = mainItem instanceof BlockItem || offItem instanceof BlockItem;
        if (hasBlockItem) {
            return allowBlocks.getValue();
        }

        return allowItems.getValue();
    }

    private boolean isFood(ItemStack stack) {
        return stack.getComponents().contains(DataComponentTypes.FOOD);
    }
}
