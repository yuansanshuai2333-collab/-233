package com.nonid.affinity.service;

import java.util.ArrayDeque;

/** Each server click keeps its own frame, including nested inventory operations from other mods. */
public final class FreeTradeShiftBatch {
    private static final ThreadLocal<ArrayDeque<Batch>> BATCHES = new ThreadLocal<>();
    private static final class Batch { boolean freeSpent; }
    private FreeTradeShiftBatch() {}

    public static void begin(boolean serverSide) {
        if (!serverSide) return;
        var frames = BATCHES.get();
        if (frames == null) { frames = new ArrayDeque<>(); BATCHES.set(frames); }
        frames.push(new Batch());
    }
    public static void end(boolean serverSide) {
        if (!serverSide) return;
        var frames = BATCHES.get();
        if (frames == null) return;
        if (!frames.isEmpty()) frames.pop();
        if (frames.isEmpty()) BATCHES.remove();
    }
    /** Always clean up, including cancellation and exceptions, without swallowing third-party failures. */
    public static void run(boolean serverSide, Runnable click) {
        begin(serverSide);
        try { click.run(); }
        finally { end(serverSide); }
    }
    public static void noteFreeTradeSpent() {
        var frames = BATCHES.get();
        if (frames != null && !frames.isEmpty()) frames.peek().freeSpent = true;
    }
    /** A batch that delivered for free must stop before silently turning into a paid purchase. */
    public static boolean mustStopPaidContinuation(boolean quoteFree) {
        var frames = BATCHES.get();
        return frames != null && !frames.isEmpty() && frames.peek().freeSpent && !quoteFree;
    }
    static void beginForTest(boolean freeAlreadySpent) {
        begin(true);
        if (freeAlreadySpent) noteFreeTradeSpent();
    }
}
