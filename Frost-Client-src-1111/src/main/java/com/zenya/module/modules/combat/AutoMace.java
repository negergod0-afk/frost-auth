package com.zenya.module.modules.combat;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.MathUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class AutoMace extends Module {
    private final Setting<Float> range = new Setting<>("Range", 4.5f, 1.0f, 6.0f);
    private final Setting<Integer> attackDelay = new Setting<>("Attack Delay", 3, 0, 10);
    private final Setting<Float> fovCheck = new Setting<>("FOV Check", 30.0f, 0.0f, 180.0f);
    private int attackCooldown;

    public AutoMace() {
        super("Auto Mace", Category.COMBAT);
        setDescription("Automatically attacks players in your crosshair when holding a mace");
        addSetting(range);
        addSetting(attackDelay);
        addSetting(fovCheck);
    }

    @Override
    public void onEnable() {
        attackCooldown = 0;
    }

    @Override
    public void onDisable() {
        attackCooldown = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (attackCooldown > 0) {
            attackCooldown--;
            return;
        }
        if (!isHoldingMace()) {
            attackCooldown = 0;
            return;
        }
        HitResult hitResult = mc.crosshairTarget;
        if (!(hitResult instanceof EntityHitResult)) return;
        if (mc.crosshairTarget.getType() != HitResult.Type.ENTITY) return;
        Entity entity = ((EntityHitResult) hitResult).getEntity();
        if (!(entity instanceof PlayerEntity)) return;
        if (entity == mc.player) return;
        PlayerEntity target = (PlayerEntity) entity;
        if (!isValidTarget(target) || attackCooldown > 0) return;
        mc.interactionManager.attackEntity((PlayerEntity) mc.player, entity);
        mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        attackCooldown = attackDelay.getValue();
    }

    private boolean isHoldingMace() {
        return mc.player != null && mc.player.getMainHandStack().getItem() == Items.MACE;
    }

    private boolean isValidTarget(PlayerEntity target) {
        if (mc.player == null || !target.isAlive()) return false;
        if ((double) mc.player.distanceTo((Entity) target) > range.getValue()) return false;
        if (fovCheck.getValue() <= 0.0f) return true;
        Vec3d playerLook = mc.player.getRotationVec(1.0f);
        Vec3d targetPos = new Vec3d(target.getX(), target.getY(), target.getZ());
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d toTarget = targetPos.subtract(playerPos).normalize();
        double dot = MathHelper.clamp(playerLook.dotProduct(toTarget), -1.0, 1.0);
        double angle = Math.toDegrees(Math.acos(dot));
        return angle <= fovCheck.getValue();
    }
}
