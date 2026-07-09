package com.zenya;

import com.zenya.module.ModuleManager;
import com.zenya.module.modules.client.Hud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZenyaClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Zenya");
    private static KeyBinding rightShiftKey;
    private static boolean menuKeyPressed = false;
    private static ClientWorld lastWorld = null;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Zenya Client (Yarn mappings)");


        com.zenya.sound.SoundManager.registerSounds();
        ModuleManager.INSTANCE.init();

        net.minecraft.client.option.KeyBinding.Category zenyaCategory =
                new net.minecraft.client.option.KeyBinding.Category(net.minecraft.util.Identifier.of("zenya", "category"));

        rightShiftKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.zenya.toggle_menu",
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                zenyaCategory
        ));

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.currentScreen instanceof com.zenya.gui.ClickGUI) {
                return;
            }
            com.zenya.module.modules.misc.NameTags.renderHud(context, tickCounter.getTickProgress(false));
            com.zenya.module.modules.smps.SpawnerTags.renderHud(context, tickCounter.getTickProgress(false));
            com.zenya.module.modules.donut.DeltaSensor.renderHud(context, tickCounter.getTickProgress(false));
            com.zenya.module.modules.donut.RegionMap.renderHud(context);
            Hud.renderHud(context);
            com.zenya.module.modules.misc.SpotifyHUD.render(context, tickCounter.getTickProgress(false));
        });

        // Inject "Frost Client ▶" button into the vanilla Options screen — no mixin needed.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof GameOptionsScreen) {
                Screens.getButtons(screen).add(
                    ButtonWidget.builder(
                        Text.literal("§b❄ §fFrost Client §7▶"),
                        btn -> client.setScreen(new com.zenya.gui.FrostOptionsScreen(screen))
                    )
                    .dimensions(4, screen.height - 26, 150, 20)
                    .build()
                );
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != lastWorld) {
                lastWorld = client.world;
                ModuleManager.INSTANCE.onWorldChange();
            }

            ModuleManager.INSTANCE.onTick();

            int menuBind = com.zenya.module.modules.client.ZenyaPlus.getMenuBind();
            if (menuBind != 0) {
                try {
                    boolean inGame = client.currentScreen == null
                            || client.currentScreen instanceof com.zenya.gui.ClickGUI;
                    if (inGame) {
                        boolean pressed = org.lwjgl.glfw.GLFW.glfwGetKey(
                                client.getWindow().getHandle(), menuBind) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                        if (pressed && !menuKeyPressed) {
                            if (client.currentScreen instanceof com.zenya.gui.ClickGUI gui) {
                                gui.requestClose();
                            } else {
                                client.setScreen(new com.zenya.gui.ClickGUI());
                            }
                        }
                        menuKeyPressed = pressed;
                    } else {
                        // Any non-game screen is open — treat the key as "not held"
                        // so the very first press after returning to the game is detected.
                        menuKeyPressed = false;
                    }
                } catch (Exception ignored) {}
            }

            if (client.currentScreen == null && client.getWindow() != null) {
                for (com.zenya.module.Module m : ModuleManager.INSTANCE.getModules()) {
                    int bind = m.getBind();
                    com.zenya.module.ActivatableModule activatable =
                            m instanceof com.zenya.module.ActivatableModule a ? a : null;
                    int activationKey = activatable != null ? activatable.getActivationKey() : 0;
                    if (bind != 0) {
                        try {
                            boolean pressed = org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().getHandle(), bind) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                            if (pressed && !m.wasBindPressed && activationKey != bind) {
                                m.onBindPressed();
                            }
                            m.wasBindPressed = pressed;
                        } catch (Exception ignored) {
                        }
                    }

                    if (activatable != null && activationKey != 0) {
                        try {
                            boolean pressed = org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().getHandle(), activationKey) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                            if (pressed && !activatable.wasActivationKeyPressed) {
                                activatable.onActivationKeyPressed();
                            }
                            activatable.wasActivationKeyPressed = pressed;
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        });
    }
}
