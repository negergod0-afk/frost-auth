package com.zenya.sound;

import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class SoundManager {
    private static final Identifier GUI_OPEN = Identifier.of("zenya", "gui_open");
    private static final Identifier GUI_CLOSE = Identifier.of("zenya", "gui_close");
    private static final Identifier MODULE_ENABLE = Identifier.of("zenya", "module_enable");
    private static final Identifier MODULE_DISABLE = Identifier.of("zenya", "module_disable");

    public static void registerSounds() {
        register(GUI_OPEN);
        register(GUI_CLOSE);
        register(MODULE_ENABLE);
        register(MODULE_DISABLE);
    }

    private static void register(Identifier id) {
        Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    private static void play(Identifier id) {
        if (!com.zenya.module.modules.client.ZenyaPlus.soundAnimationsEnabled()) {
            return;
        }
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
            Registries.SOUND_EVENT.getEntry(id).ifPresent(e -> {
                client.player.playSound(e.value(), 1.0f, 1.0f);
            });
        } catch (Exception ignored) {}
    }

    public static void playGuiOpen() {
        play(GUI_OPEN);
    }

    public static void playGuiClose() {
        play(GUI_CLOSE);
    }

    public static void playModuleEnable() {
        play(MODULE_ENABLE);
    }

    public static void playModuleDisable() {
        play(MODULE_DISABLE);
    }

    public static void play(Identifier id, float volume, float pitch) {
        if (!com.zenya.module.modules.client.ZenyaPlus.soundAnimationsEnabled()) {
            return;
        }
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
            Registries.SOUND_EVENT.getEntry(id).ifPresent(e -> {
                client.player.playSound(e.value(), volume, pitch);
            });
        } catch (Exception ignored) {}
    }
}
