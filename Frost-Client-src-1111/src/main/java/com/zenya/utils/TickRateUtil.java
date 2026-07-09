package com.zenya.utils;

import java.util.Arrays;

public final class TickRateUtil {
    public static final TickRateUtil INSTANCE = new TickRateUtil();
    private final float[] ticks = new float[20];
    private int nextIndex = 0;
    private long lastTime = -1L;

    private TickRateUtil() {
        Arrays.fill(ticks, 0f);
    }

    public void onPacket() {
        if (lastTime != -1L) {
            float timeElapsed = (float) (System.currentTimeMillis() - lastTime) / 1000.0f;
            ticks[nextIndex % ticks.length] = Math.max(0.0f, Math.min(20.0f, 20.0f / timeElapsed));
            nextIndex++;
        }
        lastTime = System.currentTimeMillis();
    }

    public float getTPS() {
        int numTicks = 0;
        float sumTickRates = 0.0f;
        for (float tick : ticks) {
            if (tick > 0.0f) {
                sumTickRates += tick;
                numTicks++;
            }
        }
        return numTicks == 0 ? 20.0f : sumTickRates / numTicks;
    }
}
