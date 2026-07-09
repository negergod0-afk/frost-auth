package com.zenya.module.modules.misc;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public final class AutoMine extends Module {
    private final Setting<Boolean> lockView = new Setting<>("Lock View", true);
    private final Setting<Float> pitch = new Setting<>("Pitch", 0.0f, -180.0f, 180.0f);
    private final Setting<Float> yaw = new Setting<>("Yaw", 0.0f, -180.0f, 180.0f);

    public AutoMine() {
        super("Auto Mine", Category.MISC);
        setDescription("Automatically mines the block you are looking at, with optional fixed yaw and pitch.");
        addSetting(lockView);
        addSetting(pitch);
        addSetting(yaw);
    }

    @Override
    public void onDisable() {
        if (mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }
        if (mc.currentScreen != null) {
            mc.interactionManager.cancelBlockBreaking();
            return;
        }

        if (lockView.getValue()) {
            mc.player.setYaw(yaw.getValue());
            mc.player.setPitch(pitch.getValue());
        }

        if (mc.player.isUsingItem()
                || mc.crosshairTarget == null
                || mc.crosshairTarget.getType() != HitResult.Type.BLOCK
                || !(mc.crosshairTarget instanceof BlockHitResult hit)) {
            mc.interactionManager.cancelBlockBreaking();
            return;
        }

        if (mc.world.getBlockState(hit.getBlockPos()).isAir()) {
            mc.interactionManager.cancelBlockBreaking();
            return;
        }

        if (mc.interactionManager.updateBlockBreakingProgress(hit.getBlockPos(), hit.getSide())) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }
}
