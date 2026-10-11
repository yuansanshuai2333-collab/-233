package com.nonid.affinity.client;

import com.nonid.affinity.service.FreeTradeStatus;
import org.jetbrains.annotations.Nullable;

/** Show the courtesy panel only for a server-confirmed free offer. */
public final class FreeTradeStatusText {
    private FreeTradeStatusText() {}
    public record Panel(java.util.List<Line> lines, int color) {}
    public record Line(String key, Object[] args) {}
    private static final String KEY = "noncreatureaffinity.trade.";
    private static Line line(String key, Object... args) { return new Line(key, args); }

    @Nullable
    public static Panel panelFor(@Nullable FreeTradeStatus status) {
        if (status == null || !status.showAsFree()) return null;
        return new Panel(java.util.List.of(line(KEY + "free"), line(KEY + "free_materials"),
                line(KEY + "free_remaining", status.remaining(), status.total())), 0xFF55FF55);
    }

    @Nullable
    public static FreeTradeStatus statusFor(int windowId, int offerIndex) {
        return FreeTradeStatusStore.statusFor(windowId, offerIndex);
    }
}
