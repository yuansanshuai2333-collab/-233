package com.nonid.affinity.client;

/** Monotonic identities and a game-tick lease prevent stale packets and abandoned banners. */
public final class RequestHudLease {
    private long latest;
    private boolean closed = true;
    private int remaining;
    public boolean receive(long id, boolean active) {
        if (id <= 0 || id < latest || (id == latest && closed && active)) return false;
        latest = id;
        closed = !active;
        remaining = active ? 100 : 0;
        return true;
    }
    public boolean tick() {
        if (remaining > 0) --remaining;
        return remaining > 0;
    }
    public void close() { closed = true; remaining = 0; }
    public void reset() { latest = 0; close(); }
}
