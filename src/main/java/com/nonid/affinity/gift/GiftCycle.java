package com.nonid.affinity.gift;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.nbt.NbtCompound;

/** One pair's earned day progress. The caller supplies only verified elapsed daylight. */
public final class GiftCycle {
    public enum Phase { PREPARING, READY, RESTING, DECEASED }
    public enum Step { PAUSED, WAITING, FAILED_ROLL, REST_FINISHED, PREPARED, DISABLED }
    public static final long DAY = 24000;
    private final UUID viewer;
    private final String creature;
    private Phase phase = Phase.PREPARING;
    private java.math.BigInteger progress = java.math.BigInteger.ZERO;
    private long lastRoll = -1, lastAdvance = -1;
    private int failures;
    private UUID gift;
    private boolean dead;
    public GiftCycle(UUID viewer, String creature) {
        this.viewer = Objects.requireNonNull(viewer); this.creature = Objects.requireNonNull(creature);
        if (!com.nonid.affinity.data.FamilyGraph.validId(creature) || creature.startsWith("player:")) throw new IllegalArgumentException("Invalid gift pair");
    }
    public UUID viewer() { return viewer; }
    public String creature() { return creature; }
    public Phase phase() { return phase; }
    public long progress() { return progress.min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValue(); }
    public String exactProgress() { return progress.toString(); }
    public int chancePercent() { return Math.min(60, 10 + failures * 5); }
    public UUID gift() { return gift; }
    public boolean dead() { return dead; }
    public Step advance(long elapsed, boolean eligible, long judgmentTick, java.util.random.RandomGenerator random, Supplier<GiftContent> prepare) {
        return advanceExact(java.math.BigInteger.valueOf(elapsed), eligible, judgmentTick, random, prepare);
    }
    public Step advanceExact(java.math.BigInteger elapsed, boolean eligible, long judgmentTick, java.util.random.RandomGenerator random, Supplier<GiftContent> prepare) {
        if (elapsed.signum() < 0 || judgmentTick < 0) throw new IllegalArgumentException("Unverified gift clock advance");
        if (judgmentTick <= lastAdvance) return Step.WAITING;
        lastAdvance = judgmentTick;
        if (dead || !eligible || phase == Phase.READY || phase == Phase.DECEASED) return Step.PAUSED;
        if (phase == Phase.PREPARING && prepare == null) return Step.DISABLED;
        creditExact(elapsed, true, prepare != null);
        if (progress.compareTo(java.math.BigInteger.valueOf(DAY)) < 0 || lastRoll == judgmentTick) return Step.WAITING;
        // One judgment per tick; remaining earned days are retained for subsequent eligible ticks.
        lastRoll = judgmentTick;
        if (random.nextInt(100) >= chancePercent()) { progress = progress.subtract(java.math.BigInteger.valueOf(DAY)); failures = Math.min(10, failures + 1); return Step.FAILED_ROLL; }
        if (phase == Phase.RESTING) { phase = Phase.PREPARING; progress = progress.subtract(java.math.BigInteger.valueOf(DAY)); failures = 0; return Step.REST_FINISHED; }
        progress = progress.subtract(java.math.BigInteger.valueOf(DAY)); // A failed content adapter must not reroll this earned day on the next tick.
        var content = Objects.requireNonNull(prepare.get(), "No configured gift content");
        if (!viewer.equals(content.viewer()) || !creature.equals(content.creature())) throw new IllegalArgumentException("Gift belongs to a different pair");
        gift = content.id(); phase = Phase.READY; progress = java.math.BigInteger.ZERO; failures = 0; return Step.PREPARED;
    }
    /** Server-clock credit at the instant it occurs; later eligibility changes cannot backfill it. */
    public void creditExact(java.math.BigInteger elapsed, boolean eligible, boolean configured) {
        if (elapsed.signum() < 0) throw new IllegalArgumentException("Negative gift credit");
        if (dead || !eligible || phase == Phase.READY || phase == Phase.DECEASED || phase == Phase.PREPARING && !configured) return;
        progress = progress.add(elapsed);
    }
    public boolean claimed(UUID identity) {
        if (!Objects.equals(gift, identity) || phase != Phase.READY && phase != Phase.DECEASED) return false;
        gift = null; phase = Phase.RESTING; progress = java.math.BigInteger.ZERO; failures = 0; return true;
    }
    public boolean death() { if (dead) return false; dead = true; if (gift != null) phase = Phase.DECEASED; return true; }
    public boolean revive() {
        if (!dead) return false; dead = false;
        if (gift != null) phase = Phase.READY;
        else { phase = Phase.RESTING; progress = java.math.BigInteger.ZERO; failures = 0; }
        return true;
    }
    public NbtCompound toNbt() {
        var n = new NbtCompound(); n.putString("viewer", viewer.toString()); n.putString("creature", creature); n.putString("phase", phase.name());
        n.putLong("progress", progress()); n.putString("earned_progress", progress.toString()); n.putLong("last_roll", lastRoll); n.putLong("last_advance", lastAdvance); n.putInt("failures", failures); n.putBoolean("dead", dead);
        if (gift != null) n.putString("gift", gift.toString()); return n;
    }
    public static GiftCycle fromNbt(NbtCompound n) {
        var cycle = new GiftCycle(UUID.fromString(n.getString("viewer", "")), n.getString("creature", ""));
        cycle.phase = Phase.valueOf(n.getString("phase", "")); cycle.progress = new java.math.BigInteger(n.getString("earned_progress", Long.toString(n.getLong("progress", 0)))); cycle.lastRoll = n.getLong("last_roll", -1);
        cycle.failures = n.getInt("failures", 0); cycle.dead = n.getBoolean("dead", false);
        cycle.lastAdvance = n.getLong("last_advance", -1);
        if (n.contains("gift")) cycle.gift = UUID.fromString(n.getString("gift", ""));
        if (cycle.progress.signum() < 0 || cycle.failures < 0 || cycle.failures > 10
                || (cycle.phase == Phase.READY || cycle.phase == Phase.DECEASED) != (cycle.gift != null)
                || cycle.phase == Phase.DECEASED && !cycle.dead) throw new IllegalArgumentException("Invalid gift cycle state");
        return cycle;
    }
}
