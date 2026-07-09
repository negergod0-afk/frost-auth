package com.zenya.module.modules.render;

import com.zenya.module.Category;
import com.zenya.module.Module;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public final class FullBright extends Module {

    private static final double FULL_BRIGHT_GAMMA = 16.0;

    private double previousGamma = 1.0;
    private boolean gammaApplied = false;

    public FullBright() {
        super("FullBright", Category.RENDER);
        setDescription("Maxes out the game gamma and applies night vision for fully lit caves and dark areas.");
    }

    @Override
    public void onEnable() {
        // Configs auto-enable modules during ModuleManager.init(), which runs
        // before MinecraftClient.options is populated. If options is still null
        // the game loop spins up.
        tryApplyGamma();
    }

    @Override
    public void onDisable() {
        if (!gammaApplied) return;
        if (mc.options != null) {
            setGamma(previousGamma);
        }
        gammaApplied = false;
    }

    @Override
    public void onTick() {
        // Two reasons we re-check every tick:
        if (mc.options == null || mc.player == null) return;

        if (!gammaApplied) {
            // First successful tick after a deferred enable. Snapshot the
            // pre-existing gamma so onDisable can restore it later.
            tryApplyGamma();
            return;
        }

        try {
            double current = mc.options.getGamma().getValue();
            if (Math.abs(current - FULL_BRIGHT_GAMMA) > 1.0E-4D) {
                setGamma(FULL_BRIGHT_GAMMA);
            }
        } catch (Exception ignored) {
        }

        // Apply night vision effect every tick
        try {
            StatusEffectInstance nightVision = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (nightVision == null || nightVision.getDuration() < 400) {
                mc.player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NIGHT_VISION,
                    Integer.MAX_VALUE,
                    0,
                    false,
                    false,
                    false
                ));
            }
        } catch (Exception ignored) {
        }
    }

    private void tryApplyGamma() {
        if (mc.options == null) return;
        try {
            if (!gammaApplied) {
                previousGamma = mc.options.getGamma().getValue();
            }
            setGamma(FULL_BRIGHT_GAMMA);
            gammaApplied = true;
        } catch (Exception ignored) {}
    }

    private void setGamma(double gamma) {
        SimpleOption<Double> opt = mc.options.getGamma();
        try {
            opt.setValue(gamma);
        } catch (Exception ignored) {
            // If setting higher values fails, just set to max allowed
            if (gamma > 1.0D) {
                opt.setValue(1.0D);
            }
        }
    }
}
