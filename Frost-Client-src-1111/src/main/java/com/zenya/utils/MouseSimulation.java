package com.zenya.utils;

import net.minecraft.client.MinecraftClient;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class MouseSimulation {
    public static final ExecutorService clickExecutor = new ThreadPoolExecutor(
            1,
            2,
            5L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64),
            runnable -> {
                Thread thread = new Thread(runnable, "zenya-click-sim");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.DiscardPolicy()
    );
    public static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void mousePress(int keyCode) {
        if (keyCode == 0) {
            mc.options.attackKey.setPressed(true);
        } else if (keyCode == 1) {
            mc.options.useKey.setPressed(true);
        }
    }

    public static void mouseRelease(int keyCode) {
        if (keyCode == 0) {
            mc.options.attackKey.setPressed(false);
        } else if (keyCode == 1) {
            mc.options.useKey.setPressed(false);
        }
    }

    public static void mouseClick(int keyCode, int millis) {
        clickExecutor.submit(() -> {
            try {
                mousePress(keyCode);
                Thread.sleep(millis);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                mouseRelease(keyCode);
            }
        });
    }

    public static void mouseClick(int keyCode) {
        mouseClick(keyCode, 35);
    }
}
