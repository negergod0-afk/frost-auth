package com.zenya.module.modules.combat;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.InventoryUtils;
import com.zenya.utils.MouseSimulation;
import com.zenya.utils.WorldUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public final class ShieldBreaker extends Module {
    private final Setting<Float> range = new Setting<>("Range", 4.5f, 1.0f, 6.0f);
    private final Setting<Boolean> autoSwitch = new Setting<>("Auto Switch", true);
    private final Setting<Boolean> swingHand = new Setting<>("Swing Hand", true);
    private final Setting<Boolean> clickSimulation = new Setting<>("Click Simulation", true);
    private final Setting<Boolean> onlyPlayers = new Setting<>("Only Players", true);

    public ShieldBreaker() {
        super("Shield Breaker", Category.COMBAT);
        addSetting(range);
        addSetting(autoSwitch);
        addSetting(swingHand);
        addSetting(clickSimulation);
        addSetting(onlyPlayers);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;

        HitResult hitResult = mc.crosshairTarget;
        if (!(hitResult instanceof EntityHitResult)) return;
        if (hitResult.getType() != HitResult.Type.ENTITY) return;
        Entity entity = ((EntityHitResult) hitResult).getEntity();
        if (!(entity instanceof PlayerEntity)) {
            if (onlyPlayers.getValue()) return;
        }
        PlayerEntity target = (PlayerEntity) entity;
        if (!target.isBlocking()) return;
        if (target.distanceTo((Entity) mc.player) > range.getValue()) return;

        if (autoSwitch.getValue() && !(mc.player.getMainHandStack().getItem() instanceof AxeItem) && mc.player.getMainHandStack().getItem() != Items.MACE) {
            if (!InventoryUtils.switchToAxe()) return;
        }
        if (mc.player.getAttackCooldownProgress(0.5f) < 0.9f) return;

        if (clickSimulation.getValue()) MouseSimulation.mouseClick(0);
        WorldUtils.hitEntity(entity, swingHand.getValue());
    }
}
