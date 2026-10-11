package com.nonid.affinity.service;

/**
 * Shared progress-bar rendering for affinity readouts.
 *
 * <p>Kept in one place because the client and server previously used different formulas
 * (divide by 100 versus divide by 10), so the same value produced different bars.
 *
 * <p>Uses the block glyphs that the vanilla boss bar also uses.
 */
public final class AffinityBars {

    /** Default number of blocks in the affinity bar. */
    public static final int DEFAULT_BLOCKS = 10;

    private AffinityBars() {
    }

    /**
     * Build a filled/empty block bar.
     *
     * @param value      current value (clamped into range)
     * @param maxValue   value that fills the bar; values below 1 fall back to 1
     * @param blocks     number of blocks
     */
    public static String bar(int value, int maxValue, int blocks) {
        int safeBlocks = Math.max(1, blocks);
        int safeMax = Math.max(1, maxValue);
        int boundedValue = Math.max(0, Math.min(value, safeMax));
        int filled = (int) Math.round((double) boundedValue / (double) safeMax * safeBlocks);
        filled = Math.max(0, Math.min(safeBlocks, filled));
        StringBuilder builder = new StringBuilder(safeBlocks);
        for (int i = 0; i < safeBlocks; i++) {
            builder.append(i < filled ? '\u2588' : '\u2591');
        }
        return builder.toString();
    }

    /** Pixel width with exact integer arithmetic, including valid nonpositive thresholds. */
    public static int filledPixels(int value, int threshold, int width) {
        int safeWidth = Math.max(0, width);
        if (threshold <= 0) return value >= threshold ? safeWidth : 0;
        int boundedValue = Math.max(0, Math.min(value, threshold));
        return (int) ((long) safeWidth * boundedValue / threshold);
    }

    /**
     * Translation key for a coarse tier word, so progress is readable without numbers.
     *
     * <p>Returns a key rather than display text so both the server notice and the client panel use
     * the same localised string.
     */
    public static String tierKey(int value, int maxValue) {
        int safeMax = Math.max(1, maxValue);
        double ratio = (double) value / (double) safeMax;
        if (ratio >= 1.0) {
            return "tier.noncreatureaffinity.full";
        }
        if (ratio >= 0.75) {
            return "tier.noncreatureaffinity.intimate";
        }
        if (ratio >= 0.5) {
            return "tier.noncreatureaffinity.friendly";
        }
        if (ratio >= 0.25) {
            return "tier.noncreatureaffinity.familiar";
        }
        return value > 0 ? "tier.noncreatureaffinity.stranger" : "tier.noncreatureaffinity.new";
    }
}
