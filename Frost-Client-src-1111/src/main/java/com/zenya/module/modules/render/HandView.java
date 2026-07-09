package com.zenya.module.modules.render;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.SectionSetting;
import com.zenya.setting.Setting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;

public final class HandView extends Module {
    public static HandView instance;

    private final Setting<Boolean> skipSwapping = new Setting<>("Skip Swapping Animation", false);
    private final Setting<Boolean> disableEatingAnimation = new Setting<>("Disable Eating Animation", false);

    private final Setting<Float> mainScaleX = new Setting<>("Main Scale X", 1.0f, 0.0f, 5.0f);
    private final Setting<Float> mainScaleY = new Setting<>("Main Scale Y", 1.0f, 0.0f, 5.0f);
    private final Setting<Float> mainScaleZ = new Setting<>("Main Scale Z", 1.0f, 0.0f, 5.0f);
    private final Setting<Float> mainPosX = new Setting<>("Main Position X", 0.0f, -3.0f, 3.0f);
    private final Setting<Float> mainPosY = new Setting<>("Main Position Y", 0.0f, -3.0f, 3.0f);
    private final Setting<Float> mainPosZ = new Setting<>("Main Position Z", 0.0f, -3.0f, 3.0f);
    private final Setting<Float> mainRotX = new Setting<>("Main Rotation X", 0.0f, -180.0f, 180.0f);
    private final Setting<Float> mainRotY = new Setting<>("Main Rotation Y", 0.0f, -180.0f, 180.0f);
    private final Setting<Float> mainRotZ = new Setting<>("Main Rotation Z", 0.0f, -180.0f, 180.0f);

    private final Setting<Float> offScaleX = new Setting<>("Off Scale X", 1.0f, 0.0f, 5.0f);
    private final Setting<Float> offScaleY = new Setting<>("Off Scale Y", 1.0f, 0.0f, 5.0f);
    private final Setting<Float> offScaleZ = new Setting<>("Off Scale Z", 1.0f, 0.0f, 5.0f);
    private final Setting<Float> offPosX = new Setting<>("Off Position X", 0.0f, -3.0f, 3.0f);
    private final Setting<Float> offPosY = new Setting<>("Off Position Y", 0.0f, -3.0f, 3.0f);
    private final Setting<Float> offPosZ = new Setting<>("Off Position Z", 0.0f, -3.0f, 3.0f);
    private final Setting<Float> offRotX = new Setting<>("Off Rotation X", 0.0f, -180.0f, 180.0f);
    private final Setting<Float> offRotY = new Setting<>("Off Rotation Y", 0.0f, -180.0f, 180.0f);
    private final Setting<Float> offRotZ = new Setting<>("Off Rotation Z", 0.0f, -180.0f, 180.0f);

    private final Setting<Float> armScaleX = new Setting<>("Arm Scale X", 1.0f, 0.0f, 5.0f);
    private final Setting<Float> armScaleY = new Setting<>("Arm Scale Y", 1.0f, 0.0f, 5.0f);
    private final Setting<Float> armScaleZ = new Setting<>("Arm Scale Z", 1.0f, 0.0f, 5.0f);
    private final Setting<Float> armPosX = new Setting<>("Arm Position X", 0.0f, -3.0f, 3.0f);
    private final Setting<Float> armPosY = new Setting<>("Arm Position Y", 0.0f, -3.0f, 3.0f);
    private final Setting<Float> armPosZ = new Setting<>("Arm Position Z", 0.0f, -3.0f, 3.0f);
    private final Setting<Float> armRotX = new Setting<>("Arm Rotation X", 0.0f, -180.0f, 180.0f);
    private final Setting<Float> armRotY = new Setting<>("Arm Rotation Y", 0.0f, -180.0f, 180.0f);
    private final Setting<Float> armRotZ = new Setting<>("Arm Rotation Z", 0.0f, -180.0f, 180.0f);

    public HandView() {
        super("Hand View", Category.RENDER);
        instance = this;
        setDescription("Alters the way items and arms are rendered in first person.");

        addSetting(skipSwapping);
        addSetting(disableEatingAnimation);

        addSetting(new SectionSetting("Main Hand"));
        addSetting(mainScaleX);
        addSetting(mainScaleY);
        addSetting(mainScaleZ);
        addSetting(mainPosX);
        addSetting(mainPosY);
        addSetting(mainPosZ);
        addSetting(mainRotX);
        addSetting(mainRotY);
        addSetting(mainRotZ);

        addSetting(new SectionSetting("Off Hand"));
        addSetting(offScaleX);
        addSetting(offScaleY);
        addSetting(offScaleZ);
        addSetting(offPosX);
        addSetting(offPosY);
        addSetting(offPosZ);
        addSetting(offRotX);
        addSetting(offRotY);
        addSetting(offRotZ);

        addSetting(new SectionSetting("Arm"));
        addSetting(armScaleX);
        addSetting(armScaleY);
        addSetting(armScaleZ);
        addSetting(armPosX);
        addSetting(armPosY);
        addSetting(armPosZ);
        addSetting(armRotX);
        addSetting(armRotY);
        addSetting(armRotZ);
    }

    public static void applyHeldItemTransform(MatrixStack matrices, Hand hand) {
        HandView module = instance;
        if (module == null || !module.isEnabled()) return;

        if (hand == Hand.MAIN_HAND) {
            module.applyTransform(
                    matrices,
                    module.mainRotX.getValue(), module.mainRotY.getValue(), module.mainRotZ.getValue(),
                    module.mainScaleX.getValue(), module.mainScaleY.getValue(), module.mainScaleZ.getValue(),
                    module.mainPosX.getValue(), module.mainPosY.getValue(), module.mainPosZ.getValue()
            );
        } else {
            module.applyTransform(
                    matrices,
                    module.offRotX.getValue(), module.offRotY.getValue(), module.offRotZ.getValue(),
                    module.offScaleX.getValue(), module.offScaleY.getValue(), module.offScaleZ.getValue(),
                    module.offPosX.getValue(), module.offPosY.getValue(), module.offPosZ.getValue()
            );
        }
    }

    public static void applyArmTransform(MatrixStack matrices) {
        HandView module = instance;
        if (module == null || !module.isEnabled()) return;

        module.applyTransform(
                matrices,
                module.armRotX.getValue(), module.armRotY.getValue(), module.armRotZ.getValue(),
                module.armScaleX.getValue(), module.armScaleY.getValue(), module.armScaleZ.getValue(),
                module.armPosX.getValue(), module.armPosY.getValue(), module.armPosZ.getValue()
        );
    }

    public static boolean shouldSkipSwapping() {
        HandView module = instance;
        return module != null && module.isEnabled() && module.skipSwapping.getValue();
    }

    public static boolean shouldDisableEatingAnimation() {
        HandView module = instance;
        return module != null && module.isEnabled() && module.disableEatingAnimation.getValue();
    }

    private void applyTransform(MatrixStack matrices, float rotX, float rotY, float rotZ,
                                float scaleX, float scaleY, float scaleZ,
                                float posX, float posY, float posZ) {
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotZ));
        matrices.scale(scaleX, scaleY, scaleZ);
        matrices.translate(posX, posY, posZ);
    }
}
