package com.zenya.utils;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public final class KeyUtils {
    public static boolean isKeyPressed(int keyCode) {
        if (keyCode <= 8) {
            return GLFW.glfwGetMouseButton((long)MinecraftClient.getInstance().getWindow().getHandle(), (int)keyCode) == 1;
        }
        return GLFW.glfwGetKey((long)MinecraftClient.getInstance().getWindow().getHandle(), (int)keyCode) == 1;
    }
}
