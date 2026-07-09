package com.zenya.module.modules.combat;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.InventoryUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public final class ElytraSwap extends Module {
    private final Setting<Boolean> autoSwitch = new Setting<>("Auto Switch", true);
    private final Setting<Boolean> swapBack = new Setting<>("Swap Back", true);

    private int previousSlot = -1;

    public ElytraSwap() {
        super("Elytra Swap", Category.COMBAT);
        addSetting(autoSwitch);
        addSetting(swapBack);
    }

    @Override
    public void onEnable() {
        previousSlot = -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;

        HitResult hitResult = mc.crosshairTarget;
        if (!(hitResult instanceof EntityHitResult)) return;
        if (hitResult.getType() != HitResult.Type.ENTITY) return;
        Entity entity = ((EntityHitResult) hitResult).getEntity();
        if (entity instanceof PlayerEntity && entity.distanceTo((Entity) mc.player) < 4.0f) {
            if (autoSwitch.getValue()) {
                int elytraSlot = findElytraSlot();
                if (elytraSlot != -1) {
                    previousSlot = swapBack.getValue() ? mc.player.getInventory().getSelectedSlot() : -1;
                    mc.player.getInventory().setSelectedSlot(elytraSlot);
                }
            }
        } else if (swapBack.getValue() && previousSlot != -1) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
            previousSlot = -1;
        }
    }

    private int findElytraSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; ++i) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.ELYTRA) return i;
        }
        return -1;
    }

    @Override
    public void onDisable() {
        if (swapBack.getValue() && previousSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1;
    }
}
