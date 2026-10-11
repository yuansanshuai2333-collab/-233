package com.nonid.affinity.non.compat;

/** A synchronous NON debug choice may bypass the addon's autonomous-request gate only. */
public final class DebugStartScope {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<java.util.UUID> INITIATOR = new ThreadLocal<>();
    private DebugStartScope() {}
    public static boolean active() { return Boolean.TRUE.equals(ACTIVE.get()); }
    public static java.util.UUID initiator() { return INITIATOR.get(); }
    public static void runAs(java.util.UUID player, Runnable action) {
        var previous = INITIATOR.get();
        if (player == null) INITIATOR.remove(); else INITIATOR.set(player);
        try { run(action); }
        finally { if (previous == null) INITIATOR.remove(); else INITIATOR.set(previous); }
    }
    public static void run(Runnable action) {
        Boolean previous = ACTIVE.get();
        ACTIVE.set(true);
        try { action.run(); }
        finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }
}
