package com.zenya.mixin;

import com.zenya.module.modules.render.Freecam;
import com.zenya.module.modules.misc.Freelook;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract float clipToSpace(float distance);
    @Shadow protected abstract void moveBy(float x, float y, float z);

    @Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
    private void onClipToSpace(float desiredCameraDistance, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
        com.zenya.module.modules.render.CameraTweaks ct = com.zenya.module.modules.render.CameraTweaks.get();
        if (ct != null && ct.isEnabled() && ct.shouldClip()) {
            cir.setReturnValue((float) ct.getDistance());
        }
    }

    @ModifyVariable(method = "clipToSpace", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float modifyCamDistance(float desiredCameraDistance) {
        com.zenya.module.modules.render.CameraTweaks ct = com.zenya.module.modules.render.CameraTweaks.get();
        if (ct != null && ct.isEnabled()) {
            return (float) ct.getDistance();
        }
        return desiredCameraDistance;
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void onUpdate(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (Freecam.instance != null && Freecam.instance.isEnabled()) {
            Freecam.instance.updateCameraMovement();
            double x = Freecam.instance.getInterpolatedX(tickDelta);
            double y = Freecam.instance.getInterpolatedY(tickDelta);
            double z = Freecam.instance.getInterpolatedZ(tickDelta);
            float yaw = Freecam.instance.getInterpolatedYaw(tickDelta);
            float pitch = Freecam.instance.getInterpolatedPitch(tickDelta);
            
            this.setPos(x, y, z);
            this.setRotation(yaw, pitch);
            return;
        }

        if (Freelook.instance != null && Freelook.instance.isCameraActive()) {
            Vec3d basePos = focusedEntity.getCameraPosVec(tickDelta);
            this.setPos(basePos.x, basePos.y, basePos.z);
            this.setRotation(Freelook.instance.getCameraYaw(), Freelook.instance.getCameraPitch());
            float distance = Freelook.instance.shouldWallClip()
                    ? Freelook.instance.getDistance()
                    : this.clipToSpace(Freelook.instance.getDistance());
            this.moveBy(-distance, 0.0f, 0.0f);
        }
    }

    @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true)
    private void onIsThirdPerson(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.instance != null && Freecam.instance.isEnabled()) {
            cir.setReturnValue(true);
            return;
        }
        if (Freelook.instance != null && Freelook.instance.isCameraActive()) {
            cir.setReturnValue(true);
        }
    }
}
