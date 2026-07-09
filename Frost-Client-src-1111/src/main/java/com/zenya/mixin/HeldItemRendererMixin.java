package com.zenya.mixin;

import com.zenya.module.modules.render.HandView;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void frost$applyHeldItemTransform(AbstractClientPlayerEntity player, float tickProgress, float pitch,
                                              Hand hand, float swingProgress, ItemStack item, float equipProgress,
                                              MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                              CallbackInfo ci) {
        HandView.applyHeldItemTransform(matrices, hand);
    }

    @Inject(method = "renderArm", at = @At("HEAD"))
    private void frost$applyArmTransform(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                         Arm arm, CallbackInfo ci) {
        HandView.applyArmTransform(matrices);
    }

    @Inject(method = "shouldSkipHandAnimationOnSwap", at = @At("HEAD"), cancellable = true)
    private void frost$skipHandAnimationOnSwap(ItemStack from, ItemStack to, CallbackInfoReturnable<Boolean> cir) {
        if (HandView.shouldSkipSwapping()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "applyEatOrDrinkTransformation", at = @At("HEAD"), cancellable = true)
    private void frost$disableEatingAnimation(MatrixStack matrices, float tickProgress, Arm arm,
                                              ItemStack stack, PlayerEntity player, CallbackInfo ci) {
        if (HandView.shouldDisableEatingAnimation()) {
            ci.cancel();
        }
    }
}
