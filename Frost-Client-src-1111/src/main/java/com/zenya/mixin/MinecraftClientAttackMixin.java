package com.zenya.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientAttackMixin {

    @Inject(method = "handleInputEvents", at = @At("HEAD"), require = 0)
    private void zenya$preInput(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.options == null) return;
        if (mc.currentScreen != null) return;
        boolean pressed = mc.options.attackKey.isPressed();

        // SpearSwap spear = SpearSwap.INSTANCE;
        // if (spear != null && spear.isEnabled()) {
        //     if (pressed) spear.preAttack(); else spear.noAttack();
        // }
    }
}
