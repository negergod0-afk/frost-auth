package com.zenya.mixin;

import com.zenya.module.modules.render.NoRender;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {

    @Inject(method = "getLightningTicksLeft", at = @At("HEAD"), cancellable = true)
    private void zenya$hideLightningFlash(CallbackInfoReturnable<Integer> cir) {
        if (NoRender.hideLightningFlash()) {
            cir.setReturnValue(0);
        }
    }
}
