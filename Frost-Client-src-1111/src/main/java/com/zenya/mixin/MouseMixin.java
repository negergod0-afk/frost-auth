package com.zenya.mixin;

import com.zenya.module.modules.render.Freecam;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {

    private static double toScaledX(MinecraftClient mc, double rawX) {
        if (mc == null || mc.getWindow() == null) return rawX;
        double w = mc.getWindow().getWidth();
        if (w <= 0.0) return rawX;
        return rawX * (mc.getWindow().getScaledWidth() / w);
    }

    private static double toScaledY(MinecraftClient mc, double rawY) {
        if (mc == null || mc.getWindow() == null) return rawY;
        double h = mc.getWindow().getHeight();
        if (h <= 0.0) return rawY;
        return rawY * (mc.getWindow().getScaledHeight() / h);
    }



    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void zenya$spotifyHudClick(long window, net.minecraft.client.input.MouseInput input, int action, CallbackInfo ci) {
        if (action != org.lwjgl.glfw.GLFW.GLFW_PRESS || input.button() != org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.currentScreen != null || mc.mouse == null) return;
        double mx = toScaledX(mc, mc.mouse.getX());
        double my = toScaledY(mc, mc.mouse.getY());
        if (com.zenya.module.modules.misc.SpotifyHUD.handleClick(mx, my)) {
            ci.cancel();
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void zenya$useScrollForFreecamSpeed(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (Freecam.instance == null || !Freecam.instance.isEnabled()) return;
        if (MinecraftClient.getInstance().currentScreen != null) return;

        double scrollAmount = vertical != 0.0 ? vertical : horizontal;
        if (scrollAmount == 0.0) return;

        Freecam.instance.adjustSpeed(scrollAmount);
        ci.cancel();
    }


}