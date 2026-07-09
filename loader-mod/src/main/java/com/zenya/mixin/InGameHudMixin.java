package com.zenya.mixin;

import com.zenya.module.modules.client.Hud;
import com.zenya.module.modules.render.NoRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void zenya$renderCustomCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        com.zenya.module.modules.render.CustomCrosshair cc = com.zenya.module.modules.render.CustomCrosshair.getInstance();
        if (cc != null && cc.isEnabled()) {
            cc.renderCrosshair(context);
        }
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void zenya$cancelVanillaEffectOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (NoRender.hidePotionIcons() || Hud.hideVanillaPotionEffects()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void zenya$cancelCrosshairInZenyaGui(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof com.zenya.gui.ClickGUI
                || client.currentScreen instanceof com.zenya.gui.BlockPickerScreen
                || client.currentScreen instanceof com.zenya.gui.StoragePickerScreen
                || client.currentScreen instanceof com.zenya.gui.MobPickerScreen
                || client.currentScreen instanceof com.zenya.gui.FriendsPickerScreen) {
            ci.cancel();
        }
        if (NoRender.hideCrosshair()) {
            ci.cancel();
            return;
        }
        // Suppress vanilla crosshair when Custom Crosshair module is on
        if (com.zenya.module.modules.render.CustomCrosshair.customCrosshairActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderBossBarHud", at = @At("HEAD"), cancellable = true)
    private void zenya$cancelBossBar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (NoRender.hideBossBar()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void zenya$cancelScoreboard(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (NoRender.hideScoreboard()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), cancellable = true)
    private void zenya$cancelTitle(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (NoRender.hideTitle()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
    private void zenya$cancelHeldItemTooltip(DrawContext context, CallbackInfo ci) {
        if (NoRender.hideHeldItemName()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void zenya$cancelVignette(DrawContext context, Entity entity, CallbackInfo ci) {
        if (NoRender.hideVignette()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void zenya$cancelSpyglass(DrawContext context, float scale, CallbackInfo ci) {
        if (NoRender.hideSpyglassOverlay()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void zenya$cancelPortal(DrawContext context, float nauseaStrength, CallbackInfo ci) {
        if (NoRender.hidePortalOverlay()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
    private void zenya$cancelNausea(DrawContext context, float nauseaStrength, CallbackInfo ci) {
        if (NoRender.hideNausea()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private void zenya$cancelEquipmentOverlays(DrawContext context, Identifier texture, float opacity, CallbackInfo ci) {
        String path = texture == null ? "" : texture.getPath().toLowerCase(Locale.ROOT);
        if (NoRender.hidePumpkinOverlay() && path.contains("pumpkin")) {
            ci.cancel();
            return;
        }
        if (NoRender.hidePowderedSnowOverlay() && path.contains("powder_snow")) {
            ci.cancel();
        }
    }
}
