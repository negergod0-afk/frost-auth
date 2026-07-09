package com.zenya.mixin;

import com.zenya.module.modules.render.Freecam;
import com.zenya.module.modules.misc.Freelook;
import com.zenya.module.modules.render.NoRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void onChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self != MinecraftClient.getInstance().player) {
            return;
        }

        if (Freecam.instance != null && Freecam.instance.isEnabled()) {
            Freecam.instance.updateRotation(cursorDeltaX * 0.15D * Freecam.instance.getLookSensitivity(), cursorDeltaY * 0.15D * Freecam.instance.getLookSensitivity());
            ci.cancel();
            return;
        }

        if (Freelook.instance != null && Freelook.instance.isCameraActive()) {
            Freelook.instance.consumeMouseDelta(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        }
    }

    @Inject(method = "isSneaking", at = @At("HEAD"), cancellable = true)
    private void onIsSneaking(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.instance != null && Freecam.instance.isEnabled()) {
            if ((Object) this == MinecraftClient.getInstance().player) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "shouldRender(D)Z", at = @At("HEAD"), cancellable = true)
    private void onShouldRender(double distance, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (NoRender.hideNoRenderEntity(self)) {
            cir.setReturnValue(false);
            return;
        }

        if (Freecam.instance != null && Freecam.instance.isEnabled()) {
            // 10 chunks = 160 blocks. Squared: 25600.
            if (distance < 25600.0D) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "isInvisible", at = @At("HEAD"), cancellable = true)
    private void zenya$showInvisible(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.showInvisibleEntities()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
    private void zenya$showInvisibleTo(PlayerEntity player, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.showInvisibleEntities()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void zenya$hideGlowing(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.hideGlowing()) {
            cir.setReturnValue(false);
        }
    }

}
