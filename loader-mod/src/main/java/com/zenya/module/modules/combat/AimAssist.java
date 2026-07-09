package com.zenya.module.modules.combat;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.ModeSetting;
import com.zenya.setting.Setting;
import com.zenya.utils.KeyUtils;
import com.zenya.utils.MathUtils;
import com.zenya.utils.TimerUtils;
import com.zenya.utils.WorldUtils;
import com.zenya.utils.rotation.Rotation;
import com.zenya.utils.rotation.RotationUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.item.AxeItem;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class AimAssist extends Module {
    private final Setting<Boolean> stickyAim = new Setting<>("Sticky Aim", false);
    private final Setting<Boolean> onlyWeapon = new Setting<>("Only Weapon", true);
    private final Setting<Boolean> onLeftClick = new Setting<>("On Left Click", false);
    private final ModeSetting aimAt = new ModeSetting("Aim At", "Head", "Head", "Chest", "Legs");
    private final Setting<Boolean> stopAtTargetVert = new Setting<>("Stop at Target Vert", true);
    private final Setting<Boolean> stopAtTargetHoriz = new Setting<>("Stop at Target Horiz", false);
    private final Setting<Float> radius = new Setting<>("Radius", 9999.0f, 0.1f, 9999.0f);
    private final Setting<Boolean> seeOnly = new Setting<>("See Only", true);
    private final Setting<Boolean> lookAtNearest = new Setting<>("Look at Nearest", false);
    private final Setting<Float> fov = new Setting<>("FOV", 180.0f, 5.0f, 360.0f);
    private final Setting<Float> verticalSpeed = new Setting<>("Vertical Speed", 3.0f, 0.0f, 10.0f);
    private final Setting<Float> horizontalSpeed = new Setting<>("Horizontal Speed", 3.0f, 0.0f, 10.0f);
    private final Setting<Float> speedDelay = new Setting<>("Speed Delay", 250.0f, 0.0f, 1000.0f);
    private final Setting<Float> chance = new Setting<>("Chance", 50.0f, 0.0f, 100.0f);
    private final Setting<Boolean> horizontal = new Setting<>("Horizontal", true);
    private final Setting<Boolean> vertical = new Setting<>("Vertical", true);
    private final Setting<Float> waitOnMove = new Setting<>("Wait on Move", 0.0f, 0.0f, 1000.0f);
    private final ModeSetting lerpMode = new ModeSetting("Lerp", "Normal", "Normal", "Smoothstep", "EaseOut");
    private final ModeSetting posMode = new ModeSetting("Pos Mode", "Normal", "Normal", "Lerped");

    private final TimerUtils speedTimer = new TimerUtils();
    private final TimerUtils moveTimer = new TimerUtils();
    private boolean mouseMoved = true;
    private float pitchSpeed;
    private float yawSpeed;

    public AimAssist() {
        super("Aim Assist", Category.COMBAT);
        setDescription("Automatically aims at players for you");
        addSetting(stickyAim);
        addSetting(onlyWeapon);
        addSetting(onLeftClick);
        addSetting(aimAt);
        addSetting(stopAtTargetVert);
        addSetting(stopAtTargetHoriz);
        addSetting(radius);
        addSetting(seeOnly);
        addSetting(lookAtNearest);
        addSetting(fov);
        addSetting(verticalSpeed);
        addSetting(horizontalSpeed);
        addSetting(speedDelay);
        addSetting(chance);
        addSetting(horizontal);
        addSetting(vertical);
        addSetting(waitOnMove);
        addSetting(lerpMode);
        addSetting(posMode);
    }

    @Override
    public void onEnable() {
        mouseMoved = true;
        pitchSpeed = verticalSpeed.getValue();
        yawSpeed = horizontalSpeed.getValue();
        speedTimer.reset();
    }

    @Override
    public void onTick() {
        if (speedTimer.delay(waitOnMove.getValue()) && !mouseMoved) {
            mouseMoved = true;
            speedTimer.reset();
        }
        if (mc.player == null || mc.currentScreen != null) return;
        if (onlyWeapon.getValue() && !mc.player.getMainHandStack().isIn(ItemTags.SWORDS) && !(mc.player.getMainHandStack().getItem() instanceof AxeItem)) return;
        if (onLeftClick.getValue() && !KeyUtils.isKeyPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT)) return;

        PlayerEntity target = WorldUtils.findNearestPlayer((PlayerEntity)mc.player, radius.getValue(), seeOnly.getValue(), true);
        if (stickyAim.getValue() && mc.player.getAttacking() instanceof PlayerEntity) {
            target = (PlayerEntity) mc.player.getAttacking();
        }
        if (target == null) return;

        if (speedTimer.delay(speedDelay.getValue())) {
            pitchSpeed = verticalSpeed.getValue();
            yawSpeed = horizontalSpeed.getValue();
            speedTimer.reset();
        }

        Vec3d baseTargetPos = new Vec3d(target.getX(), target.getY(), target.getZ());
        Vec3d targetPos = posMode.is("Lerped") ? target.getLerpedPos(1.0f) : baseTargetPos;
        if (aimAt.is("Chest")) {
            targetPos = targetPos.add(0.0, -0.5, 0.0);
        } else if (aimAt.is("Legs")) {
            targetPos = targetPos.add(0.0, -1.2, 0.0);
        }
        if (lookAtNearest.getValue()) {
            double offsetX = mc.player.getX() - target.getX() > 0.0 ? 0.29 : -0.29;
            double offsetZ = mc.player.getZ() - target.getZ() > 0.0 ? 0.29 : -0.29;
            targetPos = targetPos.add(offsetX, 0.0, offsetZ);
        }

        Rotation rotation = RotationUtils.getDirection((Entity)mc.player, targetPos);
        double angleToRotation = RotationUtils.getAngleToRotation(rotation);
        if (angleToRotation > (double)fov.getValue() / 2.0) return;

        float yawStrength = yawSpeed / 50.0f;
        float pitchStrength = pitchSpeed / 50.0f;
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        if (lerpMode.is("Smoothstep")) {
            yaw = (float) MathUtils.smoothStepLerp(yawStrength, mc.player.getYaw(), (float)rotation.yaw());
            pitch = (float) MathUtils.smoothStepLerp(pitchStrength, mc.player.getPitch(), (float)rotation.pitch());
        } else if (lerpMode.is("Normal")) {
            yaw = lerp(yawStrength, mc.player.getYaw(), (float)rotation.yaw());
            pitch = lerp(pitchStrength, mc.player.getPitch(), (float)rotation.pitch());
        } else if (lerpMode.is("EaseOut")) {
            yaw = RotationUtils.easeOutBackDegrees(mc.player.getYaw(), (float)rotation.yaw(), yawStrength);
            pitch = RotationUtils.easeOutBackDegrees(mc.player.getPitch(), (float)rotation.pitch(), pitchStrength);
        }

        if (MathUtils.randomInt(1, 100) <= chance.getValue() && mouseMoved) {
            HitResult hitResult = WorldUtils.getHitResult(stickyAim.getValue() ? 9999.0 : radius.getValue());
            if (horizontal.getValue()) {
                if (stopAtTargetHoriz.getValue() && hitResult instanceof EntityHitResult && ((EntityHitResult)hitResult).getEntity() == target) {
                    return;
                }
                mc.player.setYaw(yaw);
            }
            if (vertical.getValue()) {
                if (stopAtTargetVert.getValue() && hitResult instanceof EntityHitResult && ((EntityHitResult)hitResult).getEntity() == target) {
                    return;
                }
                mc.player.setPitch(pitch);
            }
        }
    }

    private float lerp(float delta, float start, float end) {
        return start + MathHelper.wrapDegrees(end - start) * delta;
    }
}
