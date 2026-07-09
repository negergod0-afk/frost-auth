package com.zenya.utils;

public final class TimerUtils {
    private long lastMS;

    public TimerUtils() {
        this.lastMS = System.currentTimeMillis();
    }

    public void reset() {
        this.lastMS = System.currentTimeMillis();
    }

    public boolean delay(long delay) {
        return System.currentTimeMillis() - this.lastMS >= delay;
    }

    public boolean delay(float delay) {
        return System.currentTimeMillis() - this.lastMS >= (long)delay;
    }
}
