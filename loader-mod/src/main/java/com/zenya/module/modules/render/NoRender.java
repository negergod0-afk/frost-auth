package com.zenya.module.modules.render;

import net.minecraft.world.biome.Biome;
import net.minecraft.sound.SoundEvent;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;

public class NoRender {
    public static boolean isActive() { return false; }
    public static boolean hideFog() { return false; }
    public static boolean hideBlindness() { return false; }
    public static boolean hideFireOverlay() { return false; }
    public static boolean hideWaterOverlay() { return false; }
    public static boolean shouldCancelWeatherParticle() { return false; }
    public static boolean hideShadows() { return false; }
    public static boolean hideFireSmoke() { return false; }
    public static boolean hideVignette() { return false; }
    public static boolean hidePumpkin() { return false; }
    public static boolean hidePortalMultiplier() { return false; }
    public static boolean hideLightning() { return false; }
    public static boolean hideItemActivationEffects() { return false; }
    public static boolean hideElderGuardian() { return false; }
    public static boolean hideRainGradient() { return false; }
    public static boolean hideThunder() { return false; }
    public static boolean shouldCancelWeatherSound(SoundEvent sound) { return false; }
    public static boolean hideAllPrecipitation() { return false; }
    public static boolean hideTotemAnimation() { return false; }
    public static boolean hideNametags() { return false; }
    
    // Missing methods:
    public static boolean hideLightningFlash() { return false; }
    public static boolean hideNoRenderEntity(Entity self) { return false; }
    public static boolean showInvisibleEntities() { return false; }
    public static boolean hideGlowing() { return false; }
    public static boolean hidePotionIcons() { return false; }
    public static boolean hideCrosshair() { return false; }
    public static boolean hideBossBar() { return false; }
    public static boolean hideScoreboard() { return false; }
    public static boolean hideTitle() { return false; }
    public static boolean hideHeldItemName() { return false; }
    public static boolean hideSpyglassOverlay() { return false; }
    public static boolean hidePortalOverlay() { return false; }
    public static boolean hideNausea() { return false; }
    public static boolean hidePumpkinOverlay() { return false; }
    public static boolean hidePowderedSnowOverlay() { return false; }
    public static boolean hideInWallOverlay() { return false; }
    public static boolean hideLiquidOverlay() { return false; }

    public static Biome.Precipitation filterPrecipitation(Biome.Precipitation p) { return p; }
    public static BlockState filterBlockState(BlockState state) { return state; }
}
