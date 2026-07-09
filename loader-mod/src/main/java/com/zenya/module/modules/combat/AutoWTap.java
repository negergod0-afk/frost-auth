package com.zenya.module.modules.combat;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;

public final class AutoWTap extends Module {
    private final Setting<Boolean> onHit = new Setting<>("On Hit", true);
    private final Setting<Integer> releaseTicks = new Setting<>("Release Ticks", 2, 1, 5);
    private final Setting<Integer> cooldown = new Setting<>("Cooldown", 5, 1, 20);

    private int releaseTimer = 0;
    private int cooldownTimer = 0;
    private boolean wasSprinting = false;

    public AutoWTap() {
        super("Auto W-Tap", Category.COMBAT);
        addSetting(onHit);
        addSetting(releaseTicks);
        addSetting(cooldown);
    }

    @Override
    public void onEnable() {
        releaseTimer = 0;
        cooldownTimer = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (cooldownTimer > 0) {
            cooldownTimer--;
            return;
        }
        if (releaseTimer > 0) {
            releaseTimer--;
            mc.options.sprintKey.setPressed(false);
            mc.options.forwardKey.setPressed(false);
            if (releaseTimer == 0) {
                cooldownTimer = cooldown.getValue();
            }
            return;
        }

        if (onHit.getValue() && mc.player.getAttacking() != null && mc.player.getAttackCooldownProgress(0.0f) > 0.9f) {
            if (mc.player.isSprinting()) {
                releaseTimer = releaseTicks.getValue();
            }
        }
    }
}
