package com.zenya.utils;

import java.util.Random;

public final class MathUtils {
    public static Random random = new Random(System.currentTimeMillis());

    public static double roundToPoint(double n, double point) {
        return point * (double)Math.round(n / point);
    }

    public static int randomInt(int start, int bound) {
        return random.nextInt(start, bound);
    }

    public static double smoothStepLerp(double delta, double start, double end) {
        delta = Math.max(0.0, Math.min(1.0, delta));
        double t = delta * delta * (3.0 - 2.0 * delta);
        return start + (end - start) * t;
    }

    public static double goodLerp(float delta, double start, double end) {
        int step = (int)Math.ceil(Math.abs(end - start) * (double)delta);
        if (start < end) {
            return Math.min(start + (double)step, end);
        }
        return Math.max(start - (double)step, end);
    }
}
