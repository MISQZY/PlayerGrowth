package org.misqzy.playergrowth.common.service;

import java.util.Locale;

/**
 * Pure, stateless scale/growth-progress math. Combines what used to be two
 * separate classes ({@code ScaleConverter} and {@code GrowthProgressCalculator})
 * since neither had any state or platform dependency and they were always
 * used together.
 */
public final class ScaleMath {

    /** Metres a player is at scale = 1.0 (vanilla default player height). */
    public static final double METERS_AT_SCALE_ONE = 1.88;

    private ScaleMath() {}

    public static double toMeters(double scale) {
        return scale * METERS_AT_SCALE_ONE;
    }

    public static double fromMeters(double meters) {
        return meters / METERS_AT_SCALE_ONE;
    }

    public static String format(double scale, String unit) {
        return String.format(Locale.US, "%.2f %s", toMeters(scale), unit).trim();
    }

    public static String formatValue(double scale) {
        return String.format(Locale.US, "%.2f", toMeters(scale));
    }

    /** Formats a raw scale/multiplier value (not converted to meters), e.g. {@code 1.00}. */
    public static String formatRaw(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    /** Formats growth progress in {@code [0.0, 1.0]} as a whole-number percentage, e.g. {@code 42%}. */
    public static String formatPercentage(double progress) {
        return String.format(Locale.US, "%.0f%%", Math.max(0.0, Math.min(1.0, progress)) * 100.0);
    }

    /** Progress in [0.0, 1.0]. */
    public static double progress(long playedSeconds, long targetSeconds) {
        if (targetSeconds <= 0 || playedSeconds <= 0) return 0.0;
        return Math.min(1.0, (double) playedSeconds / targetSeconds);
    }

    public static double scaleAtProgress(double progress, double minScale, double maxScale) {
        return minScale + (maxScale - minScale) * progress;
    }

    public static long secondsRemaining(long playedSeconds, long targetSeconds) {
        return Math.max(0L, targetSeconds - playedSeconds);
    }
}
