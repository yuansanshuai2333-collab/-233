package com.nonid.affinity.client;

import java.util.UUID;

/** Page-local response ordering, independent of Minecraft and the detector HUD cache. */
public final class CompanionInfoLease {
    private final UUID page = UUID.randomUUID();
    private final UUID target;
    private final String dimension;
    private long issued, accepted, age, clock;
    private final java.util.Map<Long,Long> pending = new java.util.HashMap<>();
    private boolean closed;
    public CompanionInfoLease(UUID target, String dimension) { this.target = target; this.dimension = dimension; }
    public UUID page() { return page; }
    public long issue() { pending.put(++issued,clock); return issued; }
    public boolean accept(UUID responsePage, UUID responseTarget, String responseDimension, long sequence) {
        if (closed || !page.equals(responsePage) || !java.util.Objects.equals(target, responseTarget)
                || !dimension.equals(responseDimension) || sequence <= accepted || !pending.containsKey(sequence)) return false;
        pending.keySet().removeIf(value -> value <= sequence);
        accepted = sequence; age = 0; return true;
    }
    public void tick() { age++;clock++;pending.values().removeIf(tick -> clock-tick>=60); }
    public boolean expired() { return age >= 60; }
    public void close() { closed = true;pending.clear(); }
}
