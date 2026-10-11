package com.nonid.affinity.service;

/** Per-request feedback. Observes a start without changing any engine decision. */
public final class InteractionStartFeedback {
    public enum Reason {
        UNKNOWN, BUSY, NO_MATCH, REQUIREMENTS, SPACE, SUPPORT, WALL, WATER,
        UNAVAILABLE, ENGINE_ERROR
    }

    private static final ThreadLocal<Attempt> CURRENT = new ThreadLocal<>();

    private InteractionStartFeedback() { }

    public static Attempt begin() {
        Attempt attempt = new Attempt(CURRENT.get());
        CURRENT.set(attempt);
        return attempt;
    }

    public static void report(Reason reason) {
        Attempt attempt = CURRENT.get();
        if (attempt != null && attempt.reason == Reason.UNKNOWN) attempt.reason = reason;
    }

    /** Kept independent of NON types so absent-engine installations can still load the feedback service. */
    public static void reportFallback(String refusal) {
        report(switch (refusal == null ? "" : refusal) {
            case "BUSY" -> Reason.BUSY;
            case "NO_ANIMATION" -> Reason.NO_MATCH;
            case "ERROR" -> Reason.ENGINE_ERROR;
            case "CONDITIONS" -> Reason.REQUIREMENTS;
            default -> Reason.UNKNOWN;
        });
    }

    /** Candidate enumeration must not supply the reason for a different selected candidate. */
    public static void observeEngine(boolean enabled) {
        Attempt attempt = CURRENT.get();
        if (attempt != null) attempt.observingEngine = enabled;
    }

    public static void engineMessage(String key) {
        Attempt attempt = CURRENT.get();
        if (attempt == null || !attempt.observingEngine) return;
        Reason reason = switch (key) {
            case "start_cancelled_not_enough_space" -> Reason.SPACE;
            case "start_cancelled_no_valid_center_support" -> Reason.SUPPORT;
            case "start_cancelled_no_valid_wall" -> Reason.WALL;
            case "start_cancelled_water_requirement_not_met", "start_cancelled_not_allowed_in_water",
                    "start_cancelled_block_and_water_requirement" -> Reason.WATER;
            default -> Reason.UNKNOWN;
        };
        if (reason != Reason.UNKNOWN) report(reason);
    }

    public static final class Attempt implements AutoCloseable {
        private final Attempt previous;
        private Reason reason = Reason.UNKNOWN;
        private boolean observingEngine;

        private Attempt(Attempt previous) { this.previous = previous; }
        public Reason reason() { return reason; }

        @Override
        public void close() {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
