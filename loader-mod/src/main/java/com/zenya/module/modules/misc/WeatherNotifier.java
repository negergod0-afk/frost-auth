package com.zenya.module.modules.misc;

import com.zenya.module.Category;
import com.zenya.module.Module;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public final class WeatherNotifier extends Module {

    private boolean wasRaining = false;
    private boolean wasThundering = false;
    private float notifyAnim = 0f;

    public WeatherNotifier() {
        super("WeatherNotifier", Category.MISC);
        setDescription("Notifies you about weather changes with smooth vanilla toasts.");
    }

    @Override
    public void onEnable() {
        wasRaining = false;
        wasThundering = false;
        notifyAnim = 0f;
    }

    @Override
    public void onTick() {
        if (mc.world == null) return;

        boolean isRaining = mc.world.isRaining();
        boolean isThundering = mc.world.isThundering();

        if (isRaining && !wasRaining) {
            notify("Rain Started", "The sky is getting wet.", SoundEvents.WEATHER_RAIN);
        } else if (!isRaining && wasRaining) {
            notify("Rain Cleared", "The sky is clear again.", SoundEvents.WEATHER_RAIN_ABOVE);
        }

        if (isThundering && !wasThundering) {
            notify("Thunderstorm", "Lightning nearby — stay safe.", SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER);
        } else if (!isThundering && wasThundering) {
            notify("Storm Ended", "Thunder has faded.", SoundEvents.AMBIENT_CAVE.value());
        }

        wasRaining = isRaining;
        wasThundering = isThundering;
    }

    private void notify(String title, String message, net.minecraft.sound.SoundEvent sound) {
        mc.execute(() -> {
            if (mc.player == null) return;
            float pitch = 0.85f + (notifyAnim % 3) * 0.05f;
            notifyAnim += 1f;
            mc.player.playSound(sound, 0.35f, pitch);
            mc.player.playSound(SoundEvents.UI_TOAST_IN, 0.5f, 1.05f);
            SystemToast.show(
                    mc.getToastManager(),
                    SystemToast.Type.PERIODIC_NOTIFICATION,
                    Text.literal(title),
                    Text.literal(message)
            );
        });
    }
}
