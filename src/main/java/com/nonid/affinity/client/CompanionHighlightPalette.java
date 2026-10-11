package com.nonid.affinity.client;

/** Confirmed palette, normalized against the actual affinity cap, never a stage threshold. */
public final class CompanionHighlightPalette {
    private static final double[] POINTS = {0, .1, .25, .5, .75, 1};
    private static final int[] COLORS = {0xD9CCD7, 0xDEC2D2, 0xE7AFCB, 0xF098C3, 0xF882BB, 0xFF6BB2};
    public static int color(boolean hasRelation, int affinity, int maximum) {
        if (!hasRelation) return 0xFFFFFF;
        if (maximum <= 0 || affinity <= 0) return COLORS[0];
        double value = Math.min(1, (double) affinity / maximum);
        for (int i = 1; i < POINTS.length; i++) {
            if (value > POINTS[i]) continue;
            double fraction = (value - POINTS[i - 1]) / (POINTS[i] - POINTS[i - 1]);
            int result = 0;
            for (int shift : new int[]{16, 8, 0}) {
                int first = (COLORS[i - 1] >> shift) & 255;
                int last = (COLORS[i] >> shift) & 255;
                result |= (int) Math.round(first + (last - first) * fraction) << shift;
            }
            return result;
        }
        return COLORS[COLORS.length - 1];
    }
    private CompanionHighlightPalette() {}
}
