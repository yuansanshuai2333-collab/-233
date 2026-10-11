package com.nonid.affinity.gift;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.util.Identifier;

/** Persistent unique delivery/opening ticket. A doubtful interrupted delivery is never automatically copied. */
public final class GiftEnvelope {
    public enum State { READY, DELIVERING, ISSUED, OPENING, OPENED, LOST }
    public record Location(String dimension, double x, double y, double z) {
        public Location {
            if (Identifier.tryParse(dimension) == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Invalid gift location");
        }
        public NbtCompound toNbt() { var n = new NbtCompound(); n.putString("dimension", dimension); n.putDouble("x", x); n.putDouble("y", y); n.putDouble("z", z); return n; }
        public static Location fromNbt(NbtCompound n) { return new Location(n.getString("dimension", ""), n.getDouble("x", Double.NaN), n.getDouble("y", Double.NaN), n.getDouble("z", Double.NaN)); }
    }
    private final GiftContent content;
    private final UUID token;
    private State state = State.READY;
    private Location death, placed, deliveryLocation, rewardLocation;
    private long emissionClock = -1;
    private String cause = "";
    private UUID killer, drop, claimant, actor;
    private boolean bequestDelivery;
    private long placedAt = -1, lifetime, protectedUntil;
    private List<GiftContent.Reward> remaining = List.of();
    private UUID rewardDrop;
    private int rewardCount, searchCursor;
    private boolean searchUnloaded, fallbackApplied;
    public boolean fallbackApplied() { return fallbackApplied; }
    public GiftEnvelope(GiftContent content) { this(content, UUID.randomUUID()); }
    private GiftEnvelope(GiftContent content, UUID token) { this.content = Objects.requireNonNull(content); this.token = Objects.requireNonNull(token); }
    public GiftContent content() { return content; }
    public UUID token() { return token; }
    public State state() { return state; }
    public Location deliveryLocation() { return deliveryLocation; }
    public Location rewardLocation() { return rewardLocation; }
    public void emission(Location location, long clock) {
        if (state != State.DELIVERING || clock < 0 || clock > Long.MAX_VALUE - 200) throw new IllegalStateException("Invalid gift emission");
        deliveryLocation = Objects.requireNonNull(location); emissionClock = clock; protectedUntil = bequestDelivery ? clock + 200 : 0;
    }
    public void rewardLocation(Location location) { if (rewardDrop == null) throw new IllegalStateException("No reward reservation"); rewardLocation = Objects.requireNonNull(location); }
    public Location death() { return death; }
    public Location placed() { return placed; }
    public long placedAt() { return placedAt; }
    public String cause() { return cause; }
    public UUID killer() { return killer; }
    public UUID drop() { return drop; }
    public UUID claimant() { return claimant; }
    public UUID actor() { return actor; }
    public void actor(UUID physicalActor) { if (actor != null && !actor.equals(physicalActor)) throw new IllegalStateException("Gift source already frozen"); actor = Objects.requireNonNull(physicalActor); }
    public long protectedUntil() { return protectedUntil; }
    public boolean bequestDelivery() { return bequestDelivery; }
    public List<GiftContent.Reward> remaining() { return remaining; }
    public UUID rewardDrop() { return rewardDrop; }
    public int rewardCount() { return rewardCount; }
    public int searchCursor(){return searchCursor;}
    public boolean searchUnloaded(){return searchUnloaded;}
    public void searchProgress(GiftPlacement.Result result){searchCursor=result.cursor();searchUnloaded=result.unloaded();}
    public boolean died(Location anchor, String source, UUID actualKiller) {
        if (state != State.READY || death != null) return false;
        death = Objects.requireNonNull(anchor); cause = Objects.requireNonNull(source); killer = actualKiller; return true;
    }
    public boolean place(Location location, long clock, long lifetimeTicks) {
        if (state != State.READY || death == null || placed != null || clock < 0 || lifetimeTicks < 1 || lifetimeTicks > Long.MAX_VALUE - clock) return false;
        if (!death.dimension().equals(location.dimension())) return false;
        double x = death.x() - location.x(), y = death.y() - location.y(), z = death.z() - location.z();
        if (x*x + y*y + z*z > 256.000001) return false;
        searchCursor=0; searchUnloaded=false; placed = location; placedAt = clock; lifetime = lifetimeTicks; return true;
    }
    public boolean expire(long clock) {
        if (!expired(clock)) return false;
        state = State.LOST; placed = null; return true;
    }
    public boolean expired(long clock) {
        return state == State.READY && placed != null && clock >= placedAt && clock - placedAt >= lifetime;
    }
    public boolean lost() { if (state != State.READY || death == null) return false; state = State.LOST; placed = null; return true; }
    public boolean revive() {
        if (state != State.READY || death == null) return false;
        searchCursor=0; searchUnloaded=false; death = null; placed = null; placedAt = -1; lifetime = 0; return true;
    }
    public boolean reserve(UUID viewer, UUID physicalDrop, boolean fromBequest) {
        if (state != State.READY || !content.viewer().equals(viewer) || physicalDrop == null || fromBequest != (death != null)
                || fromBequest && placed == null) return false;
        state = State.DELIVERING; drop = physicalDrop; claimant = viewer; bequestDelivery = fromBequest; return true;
    }
    public boolean rollback(UUID physicalDrop) {
        if (state != State.DELIVERING || !Objects.equals(drop, physicalDrop)) return false;
        state = State.READY; drop = null; claimant = null; bequestDelivery = false; deliveryLocation = null; emissionClock = -1; protectedUntil = 0; return true;
    }
    public boolean issued(UUID physicalDrop, long clock) {
        if (state != State.DELIVERING || !Objects.equals(drop, physicalDrop) || clock < 0 || clock > Long.MAX_VALUE - 200) return false;
        state = State.ISSUED; placed = null; if (emissionClock < 0) protectedUntil = bequestDelivery ? clock + 200 : 0; return true;
    }
    /** A bearer ticket confirms only a persisted delivery reservation, never physical originality. */
    public boolean canConfirmHeldTicket(UUID proof) {
        return state == State.DELIVERING && token.equals(proof) && drop != null
                && content.viewer().equals(claimant) && deliveryLocation != null && emissionClock >= 0;
    }
    public boolean protectedDrop(UUID physicalDrop, long clock) {
        return bequestDelivery && Objects.equals(drop, physicalDrop) && state == State.ISSUED && clock >= 0 && clock < protectedUntil;
    }
    public boolean startOpen(UUID proof, List<GiftContent.Reward> resolved) {
        return startOpen(proof,resolved,!resolved.equals(content.rewards()));
    }
    public boolean startOpen(UUID proof, List<GiftContent.Reward> resolved, boolean applied) {
        if (!token.equals(proof) || state != State.ISSUED || resolved.isEmpty()) return false;
        remaining = List.copyOf(resolved); fallbackApplied = applied; state = State.OPENING; return true;
    }
    /** Repair only undelivered rows. A reserved drop is never discarded or replayed. */
    public boolean repairRemaining(java.util.function.Predicate<GiftContent.Reward> available) {
        if (state != State.OPENING || rewardDrop != null) return false;
        var kept = new ArrayList<GiftContent.Reward>(); boolean missing = false;
        for (var row : remaining) { if (available.test(row)) kept.add(row); else missing = true; }
        if (!missing) return false;
        if (!fallbackApplied) {
            fallbackApplied = true;
            for (var row : content.fallback()) if (available.test(row)) kept.add(row);
        }
        remaining = List.copyOf(kept); if (remaining.isEmpty()) state = State.OPENED;
        return true;
    }
    private static boolean sameKind(GiftContent.Reward a, GiftContent.Reward b) { return a.withCount(1).equals(b.withCount(1)); }
    /** Old opening records have no provenance flag: ambiguous fallback tails must never be paid again. */
    private static boolean legacyFallbackApplied(GiftContent content, List<GiftContent.Reward> remaining) {
        if (remaining.isEmpty()) return true;
        int offset = content.rewards().size()-remaining.size();
        if (offset<0) return true;
        for (int i=0;i<remaining.size();i++) {
            var left=remaining.get(i);var right=content.rewards().get(offset+i);
            if (!sameKind(left,right) || left.count()>right.count() || i>0 && left.count()!=right.count()) return true;
        }
        var tail=remaining.getLast();
        return content.fallback().stream().anyMatch(row -> sameKind(tail,row));
    }
    public boolean delivered(int count) {
        if (state != State.OPENING || rewardDrop != null || remaining.isEmpty() || count < 1 || count > remaining.getFirst().count()) return false;
        var list = new ArrayList<>(remaining); var head = list.removeFirst();
        if (count < head.count()) list.addFirst(head.withCount(head.count() - count));
        remaining = List.copyOf(list); if (remaining.isEmpty()) state = State.OPENED; return true;
    }
    public boolean reserveReward(UUID entity, int count) {
        if (state != State.OPENING || remaining.isEmpty() || rewardDrop != null || entity == null || count < 1 || count > remaining.getFirst().count()) return false;
        rewardDrop = entity; rewardCount = count; return true;
    }
    public boolean rewardSpawned(UUID entity) {
        if (!Objects.equals(rewardDrop, entity)) return false;
        int count = rewardCount; rewardDrop = null; rewardCount = 0; rewardLocation = null; return delivered(count);
    }
    public boolean rewardFailed(UUID entity) {
        if (!Objects.equals(rewardDrop, entity)) return false;
        rewardDrop = null; rewardCount = 0; rewardLocation = null; return true;
    }
    public NbtCompound toNbt() {
        var n = new NbtCompound(); n.put("content", content.toNbt()); n.putString("token", token.toString()); n.putString("state", state.name());
        if (death != null) n.put("death", death.toNbt()); if (placed != null) n.put("placed", placed.toNbt());
        if (deliveryLocation != null) n.put("delivery_location", deliveryLocation.toNbt()); if (rewardLocation != null) n.put("reward_location", rewardLocation.toNbt()); n.putLong("emission_clock", emissionClock);
        n.putInt("search_cursor",searchCursor); n.putBoolean("search_unloaded",searchUnloaded);
        n.putString("cause", cause); if (killer != null) n.putString("killer", killer.toString()); if (drop != null) n.putString("drop", drop.toString()); if (claimant != null) n.putString("claimant", claimant.toString());
        if (actor != null) n.putString("actor", actor.toString());
        n.putBoolean("bequest_delivery", bequestDelivery); n.putLong("placed_at", placedAt); n.putLong("lifetime", lifetime); n.putLong("protected_until", protectedUntil);
        n.putBoolean("fallback_applied",fallbackApplied);
        var rewards = new NbtList(); remaining.forEach(item -> rewards.add(item.toNbt())); n.put("remaining", rewards);
        if (rewardDrop != null) { n.putString("reward_drop", rewardDrop.toString()); n.putInt("reward_count", rewardCount); } return n;
    }
    public static GiftEnvelope fromNbt(NbtCompound n) {
        var gift = new GiftEnvelope(GiftContent.fromNbt(n.getCompoundOrEmpty("content")), UUID.fromString(n.getString("token", "")));
        gift.state = State.valueOf(n.getString("state", "")); if (n.contains("death")) gift.death = Location.fromNbt(n.getCompoundOrEmpty("death")); if (n.contains("placed")) gift.placed = Location.fromNbt(n.getCompoundOrEmpty("placed"));
        if (n.contains("delivery_location")) gift.deliveryLocation = Location.fromNbt(n.getCompoundOrEmpty("delivery_location")); if (n.contains("reward_location")) gift.rewardLocation = Location.fromNbt(n.getCompoundOrEmpty("reward_location")); gift.emissionClock = n.getLong("emission_clock", -1);
        gift.searchCursor=n.getInt("search_cursor",0);gift.searchUnloaded=n.getBoolean("search_unloaded",false);if(gift.searchCursor<0||gift.searchCursor>GiftPlacement.maxCursor())throw new IllegalArgumentException("Invalid gift placement cursor");
        gift.cause = n.getString("cause", ""); if (gift.cause.length() > 256) throw new IllegalArgumentException("Invalid gift cause");
        if (n.contains("killer")) gift.killer = UUID.fromString(n.getString("killer", "")); if (n.contains("drop")) gift.drop = UUID.fromString(n.getString("drop", "")); if (n.contains("claimant")) gift.claimant = UUID.fromString(n.getString("claimant", ""));
        if (n.contains("actor")) gift.actor = UUID.fromString(n.getString("actor", ""));
        gift.bequestDelivery = n.getBoolean("bequest_delivery", false); gift.placedAt = n.getLong("placed_at", -1); gift.lifetime = n.getLong("lifetime", 0); gift.protectedUntil = n.getLong("protected_until", 0);
        var rewards = new ArrayList<GiftContent.Reward>(); for (var row : n.getListOrEmpty("remaining")) if (row instanceof NbtCompound reward) rewards.add(GiftContent.Reward.fromNbt(reward)); gift.remaining = List.copyOf(rewards);
        gift.fallbackApplied = n.contains("fallback_applied") ? n.getBoolean("fallback_applied",false)
                : gift.state == State.OPENING && legacyFallbackApplied(gift.content,gift.remaining);
        if (n.contains("reward_drop")) { gift.rewardDrop = UUID.fromString(n.getString("reward_drop", "")); gift.rewardCount = n.getInt("reward_count", 0); }
        if (gift.placed != null && (gift.death == null || gift.placedAt < 0 || gift.lifetime < 1)
                || gift.protectedUntil < 0 || gift.state == State.OPENING && gift.remaining.isEmpty()
                || gift.state == State.OPENED && !gift.remaining.isEmpty()
                || gift.rewardDrop != null && (gift.state != State.OPENING || gift.rewardCount < 1 || gift.rewardCount > gift.remaining.getFirst().count())
                || (gift.state == State.DELIVERING || gift.state == State.ISSUED || gift.state == State.OPENING || gift.state == State.OPENED) && (gift.drop == null || !gift.content.viewer().equals(gift.claimant))) throw new IllegalArgumentException("Invalid gift transaction");
        return gift;
    }
}
