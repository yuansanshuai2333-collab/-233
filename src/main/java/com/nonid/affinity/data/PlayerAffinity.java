package com.nonid.affinity.data;

import net.minecraft.nbt.NbtCompound;

/**
 * 单个玩家对单只生物的好感记录。
 *
 * <p>好感度范围由配置决定，本类只负责保存与裁剪，不做业务判定。
 */
public final class PlayerAffinity {

    public static final int UNKNOWN = Integer.MIN_VALUE;

    private int affinity;
    private static final long PRECISION = 1_000_000_000L;
    private long affinityFraction;
    private long bond;
    private record DeferredAttack(int loss, int min, int max) {}
    private final java.util.Map<java.util.UUID, DeferredAttack> deferredAttacks = new java.util.LinkedHashMap<>();
    public void deferEscapeAttack(java.util.UUID id, int loss, int min, int max) {
        if (loss > 0 && loss <= 2 && min <= max) deferredAttacks.putIfAbsent(id, new DeferredAttack(loss, min, max));
    }
    public boolean removeDeferredEscapeAttack(java.util.UUID id) { return deferredAttacks.remove(id) != null; }
    private FriendAttackStreak friendAttacks = new FriendAttackStreak();
    public FriendAttackStreak friendAttacks() { return friendAttacks; }
    private boolean befriended;
    private boolean tamedByUs;
    private long lastMatingTick = -1L;
    private int matingCount;
    private int peakCount;
    private int rejectionCount;
    /** Recorded accept decisions since this field was introduced; -1 means legacy history is unknown. */
    private int agreementCount;
    private int generation;
    private int requestCooldownTicks;

    /**
     * Whether the thank-you gift for reaching the affinity cap has already been handed over.
     *
     * <p>Persisted so the gift cannot be claimed repeatedly by hovering at the cap.
     */
    private boolean capGiftGiven;

    /**
     * How many thank-you tokens this player has ACTUALLY received from this creature.
     *
     * <p>Not the inventory balance: the panel must show what this creature gave, which is a
     * different number once the player moves items around. Saves written before this field existed
     * report 0, since the history cannot be reconstructed.
     */
    private int tokensReceived;

    // ------------------------------------------------------------------
    // Close-friend free trades (1.2.0)
    //
    // These live on the RELATION, which lives inside the creature's own attachment. That is the
    // point, not an accident: the attachment is registered with copyOnDeath, so Fabric's
    // MOB_CONVERSION transfer carries it to the converted entity as the SAME object. A villager ->
    // zombie villager -> villager round trip therefore keeps the allowance, the applied-event
    // markers and any pending cure fact, without this module hand-copying anything. Keying them on
    // the creature's UUID instead would break on the second conversion, because the UUID is new.
    //
    // `observedCureEpoch` is the part that must never be lost: it is the record that a REAL cure
    // happened, written at conversion time (which needs no player to be online) and only APPLIED to
    // the allowance later. `lastCureSourceId` is the dedup marker that keeps a repeated observation
    // of one conversion from counting twice; it is persisted for the same reason.
    // ------------------------------------------------------------------

    /** Whether the free-trade fields below have been written for this relation yet. */
    private boolean quotaInitialized;
    private int remainingFreeTrades;
    private long lastAppliedHeroEpoch;
    private long lastAppliedCureEpoch;
    private long observedCureEpoch;
    private String lastCureSourceId = "";
    private long quotaRevision;
    private int totalFreeTrades;
    private long totalCompletions;

    public PlayerAffinity(int affinity) {
        this.affinity = affinity;
        // A relation built by this constructor is one this module just created, so its free-trade numbers are
        // known rather than unknown: zero allowance, both markers at zero. That is deliberately different from a
        // relation READ from a save (see fromNbt), where absence means "written before the feature existed" and
        // the markers have to be baselined instead of being treated as new.
        this.quotaInitialized = true;
    }

    public int getAffinity() {
        return this.affinity;
    }

    public void setAffinity(int value, int min, int max) {
        this.affinity = clamp(value, min, max);
        this.affinityFraction = 0;
    }

    public double getExactAffinity() { return affinity + affinityFraction / (double) PRECISION; }
    public double getBond() { return bond / (double) PRECISION; }
    public void addBond(double delta) {
        if (!Double.isFinite(delta)) return;
        long units = Math.round(delta * PRECISION);
        if (units > 0 && bond > Long.MAX_VALUE - units) bond = Long.MAX_VALUE;
        else if (units < 0 && units < -bond) bond = 0;
        else bond += units;
    }
    /** Fractional rewards keep the legacy integer field and wire/display format intact. */
    public double addPreciseAffinity(double delta, int min, int max) {
        if (!Double.isFinite(delta)) return 0;
        double before = getExactAffinity();
        double next = Math.max(min, Math.min(max, before + delta));
        long scaled = Math.round(next * PRECISION);
        affinity = (int) Math.floorDiv(scaled, PRECISION);
        affinityFraction = Math.floorMod(scaled, PRECISION);
        return getExactAffinity() - before;
    }

    /**
     * Clamp with explicit bounds. Deliberately not using MathHelper.clamp: relying on a
     * vanilla helper whose argument order changed between versions caused a real bug here
     * (negative deltas were silently reset to zero), so the logic is local and obvious.
     */
    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    public int addAffinity(int delta, int min, int max) {
        int before = this.affinity;
        long next = (long) before + delta;
        this.affinity = (int) Math.max(min, Math.min((long) max, next));
        if (next < min || next >= max) affinityFraction = 0;
        long applied = (long) this.affinity - before;
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, applied));
    }

    /** Whether the cap gift was already given for this relation. */
    /** Tokens actually granted by this creature to this player. */
    public int getTokensReceived() {
        return this.tokensReceived;
    }
    public int getAgreementCount(){return agreementCount;}
    public void recordAgreement(){agreementCount=agreementCount<0?1:increment(agreementCount);}

    /** Record one more token granted by this creature. */
    public void addTokenReceived() {
        this.tokensReceived = increment(this.tokensReceived);
    }

    public boolean isCapGiftGiven() {
        return this.capGiftGiven;
    }

    public void setCapGiftGiven(boolean value) {
        this.capGiftGiven = value;
    }

    public boolean isBefriended() {
        return this.befriended;
    }

    public void setBefriended(boolean value) {
        this.befriended = value;
    }

    public boolean isTamedByUs() {
        return this.tamedByUs;
    }

    public void setTamedByUs(boolean value) {
        this.tamedByUs = value;
    }

    public long getLastMatingTick() {
        return this.lastMatingTick;
    }

    public void setLastMatingTick(long tick) {
        this.lastMatingTick = tick;
    }

    public int getMatingCount() {
        return this.matingCount;
    }

    public void incrementMatingCount() {
        this.matingCount = increment(this.matingCount);
    }

    public int getPeakCount() {
        return this.peakCount;
    }

    public void incrementPeakCount() {
        this.peakCount = increment(this.peakCount);
    }

    public int getRejectionCount() {
        return this.rejectionCount;
    }

    public void incrementRejectionCount() {
        this.rejectionCount = increment(this.rejectionCount);
    }

    private static int increment(int value) {
        return value == Integer.MAX_VALUE ? value : Math.max(0, value) + 1;
    }

    public int getGeneration() {
        return this.generation;
    }

    public void setGeneration(int value) {
        this.generation = Math.max(0, value);
    }

    public int getRequestCooldownTicks() {
        return this.requestCooldownTicks;
    }

    public void setRequestCooldownTicks(int ticks) {
        this.requestCooldownTicks = Math.max(0, ticks);
    }

    public boolean tickCooldown() {
        if (this.requestCooldownTicks > 0) {
            this.requestCooldownTicks--;
        }
        return this.requestCooldownTicks > 0;
    }

    // ------------------------------------------------------------------
    // Close-friend free trades: plain accessors. The RULES live in
    // com.nonid.affinity.service.FreeTradeQuota / FreeTradeAccounts, so this class only stores.
    // ------------------------------------------------------------------

    /** False for a relation read from a save written before this feature existed. */
    public boolean isQuotaInitialized() {
        return this.quotaInitialized;
    }

    public void setQuotaInitialized(boolean value) {
        this.quotaInitialized = value;
    }

    public int getRemainingFreeTrades() {
        return this.remainingFreeTrades;
    }

    public void setRemainingFreeTrades(int value) {
        this.remainingFreeTrades = Math.max(0, Math.min(3, value));
    }

    public long getLastAppliedHeroEpoch() {
        return this.lastAppliedHeroEpoch;
    }

    public void setLastAppliedHeroEpoch(long value) {
        this.lastAppliedHeroEpoch = Math.max(0L, value);
    }

    public long getLastAppliedCureEpoch() {
        return this.lastAppliedCureEpoch;
    }

    public void setLastAppliedCureEpoch(long value) {
        this.lastAppliedCureEpoch = Math.max(0L, value);
    }

    /**
     * Cures observed for this pair that have not been applied to the allowance yet.
     *
     * <p>This is the persisted cure FACT. It is written when the conversion completes -- which needs
     * no player to be online -- and applied later, at the next quote or trade. Losing it would lose a
     * real event; applying it early would let the attachment transfer overwrite it.
     */
    public long getObservedCureEpoch() {
        return this.observedCureEpoch;
    }

    public void setObservedCureEpoch(long value) {
        this.observedCureEpoch = Math.max(0L, value);
    }

    /** The conversion this relation has already recorded, for deduplication. Never a UUID of the villager. */
    public String getLastCureSourceId() {
        return this.lastCureSourceId;
    }

    public void setLastCureSourceId(String value) {
        this.lastCureSourceId = value == null ? "" : value;
    }

    public long getQuotaRevision() {
        return this.quotaRevision;
    }

    public void setQuotaRevision(long value) {
        this.quotaRevision = Math.max(0L, value);
    }

    public int getTotalFreeTrades() {
        return this.totalFreeTrades;
    }

    public void setTotalFreeTrades(int value) {
        this.totalFreeTrades = Math.max(0, value);
    }

    public long getTotalCompletions() {
        return this.totalCompletions;
    }

    public void setTotalCompletions(long value) {
        this.totalCompletions = Math.max(0L, value);
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        if (!deferredAttacks.isEmpty()) {
            var pending = new net.minecraft.nbt.NbtList();
            for (var attack : deferredAttacks.values()) {
                var entry = new NbtCompound(); entry.putInt("loss", attack.loss());
                entry.putInt("min", attack.min()); entry.putInt("max", attack.max()); pending.add(entry);
            }
            tag.put("pending_escape_attacks", pending);
        }
        if (affinityFraction != 0) tag.putLong("affinity_fraction", affinityFraction);
        if (bond != 0) tag.putLong("relationship_bond", bond);
        tag.put("friend_attacks", friendAttacks.toNbt());
        tag.putInt("agreement_count",this.agreementCount);
        tag.putInt("affinity", this.affinity);        if (this.befriended) {
            tag.putBoolean("befriended", true);
        }
        if (this.tamedByUs) {
            tag.putBoolean("tamed_by_us", true);
        }
        if (this.lastMatingTick >= 0L) {
            tag.putLong("last_mating_tick", this.lastMatingTick);
        }
        if (this.matingCount != 0) {
            tag.putInt("mating_count", this.matingCount);
        }
        if (this.peakCount != 0) {
            tag.putInt("peak_count", this.peakCount);
        }
        if (this.rejectionCount != 0) {
            tag.putInt("rejection_count", this.rejectionCount);
        }
        if (this.generation != 0) {
            tag.putInt("generation", this.generation);
        }
        if (this.requestCooldownTicks > 0) {
            tag.putInt("request_cooldown_ticks", this.requestCooldownTicks);
        }
        if (this.capGiftGiven) {
            tag.putBoolean("cap_gift_given", true);
        }
        if (this.tokensReceived != 0) {
            tag.putInt("tokens_received", this.tokensReceived);
        }
        if (this.quotaInitialized) {
            // Written as a group: the marker is what tells a later load "these numbers are real", and a
            // partially written group would be read as "not initialised" and re-migrated.
            tag.putBoolean("quota_initialized", true);
            tag.putInt("free_trades_left", this.remainingFreeTrades);
            tag.putLong("last_hero_epoch", this.lastAppliedHeroEpoch);
            tag.putLong("last_cure_epoch", this.lastAppliedCureEpoch);
            tag.putLong("observed_cure_epoch", this.observedCureEpoch);
            if (!this.lastCureSourceId.isEmpty()) {
                tag.putString("last_cure_source", this.lastCureSourceId);
            }
            tag.putLong("quota_revision", this.quotaRevision);
            tag.putInt("total_free_trades", this.totalFreeTrades);
            tag.putLong("total_completions", this.totalCompletions);
        }
        return tag;
    }

    public static PlayerAffinity fromNbt(NbtCompound tag) {
        PlayerAffinity affinity = new PlayerAffinity(tag.getInt("affinity", 0));
        affinity.affinityFraction = Math.max(0, Math.min(PRECISION - 1, tag.getLong("affinity_fraction", 0L)));
        affinity.bond = Math.max(0, tag.getLong("relationship_bond", 0L));
        affinity.friendAttacks = FriendAttackStreak.fromNbt(tag.getCompoundOrEmpty("friend_attacks"));
        affinity.befriended = tag.getBoolean("befriended", false);
        affinity.tamedByUs = tag.getBoolean("tamed_by_us", false);
        affinity.lastMatingTick = tag.contains("last_mating_tick") ? tag.getLong("last_mating_tick", -1L) : -1L;
        affinity.matingCount = Math.max(0, tag.getInt("mating_count", 0));
        affinity.peakCount = Math.max(0, tag.getInt("peak_count", 0));
        affinity.rejectionCount = Math.max(0, tag.getInt("rejection_count", 0));
        affinity.agreementCount = Math.max(-1, tag.getInt("agreement_count", -1));
        affinity.generation = Math.max(0, tag.getInt("generation", 0));
        affinity.setRequestCooldownTicks(tag.getInt("request_cooldown_ticks", 0));
        affinity.capGiftGiven = tag.getBoolean("cap_gift_given", false);
        // Absent in older saves: 0 means "no recorded history", which the panel states plainly.
        affinity.tokensReceived = Math.max(0, tag.getInt("tokens_received", 0));
        // Absent in saves written before the free-trade feature: the relation must NOT be given three
        // trades retroactively, so it is left uninitialised and migrated with a fixed baseline the first
        // time the allowance is asked for (see FreeTradeAccounts).
        affinity.quotaInitialized = tag.getBoolean("quota_initialized", false);
        if (affinity.quotaInitialized) {
            affinity.remainingFreeTrades = Math.max(0, Math.min(3, tag.getInt("free_trades_left", 0)));
            affinity.lastAppliedHeroEpoch = Math.max(0L, tag.getLong("last_hero_epoch", 0L));
            affinity.lastAppliedCureEpoch = Math.max(0L, tag.getLong("last_cure_epoch", 0L));
            affinity.observedCureEpoch = Math.max(0L, tag.getLong("observed_cure_epoch", 0L));
            affinity.lastCureSourceId = tag.getString("last_cure_source", "");
            affinity.quotaRevision = Math.max(0L, tag.getLong("quota_revision", 0L));
            affinity.totalFreeTrades = Math.max(0, tag.getInt("total_free_trades", 0));
            affinity.totalCompletions = Math.max(0L, tag.getLong("total_completions", 0L));
        }
        // NoN running sessions do not survive entity/world reload. Consume saved actual-attack
        // obligations once into this loaded relation; never replay their already-paid bond loss.
        for (var element : tag.getListOrEmpty("pending_escape_attacks")) {
            if (!(element instanceof NbtCompound entry)) continue;
            int loss = entry.getInt("loss", 0), min = entry.getInt("min", -200), max = entry.getInt("max", 200);
            if (loss > 0 && loss <= 2 && min <= max) affinity.addAffinity(-loss, min, max);
        }
        return affinity;
    }
}
