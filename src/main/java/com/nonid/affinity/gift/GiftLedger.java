package com.nonid.affinity.gift;

import java.util.*;
import net.minecraft.nbt.NbtCompound;

/** Overworld-owned pair history and tickets, independent of online players or loaded mobs. */
public final class GiftLedger {
    private final Map<UUID, Map<String, GiftCycle>> pairs = new LinkedHashMap<>();
    private final Map<UUID, GiftEnvelope> gifts = new LinkedHashMap<>();
    private final Map<String, GiftHabitat> habitats = new LinkedHashMap<>(), landmarks = new LinkedHashMap<>();
    public GiftHabitat habitat(String creature) { return habitat(habitats, creature); }
    public GiftHabitat landmark(String creature) { return habitat(landmarks, creature); }
    private static GiftHabitat habitat(Map<String, GiftHabitat> records, String creature) {
        if (!com.nonid.affinity.data.FamilyGraph.validId(creature) || creature.startsWith("player:")) throw new IllegalArgumentException("Invalid habitat owner");
        return records.computeIfAbsent(creature, id -> new GiftHabitat());
    }
    private long clock;
    private int expiryDays = 60;
    public long clock() { return clock; }
    public void tick() { if (clock == Long.MAX_VALUE) throw new IllegalStateException("Gift runtime clock exhausted"); clock++; }
    public int expiryDays() { return expiryDays; }
    public void expiryDays(int days) { if (days < 1 || days > Integer.MAX_VALUE / 24000) throw new IllegalArgumentException("Invalid bequest lifetime"); expiryDays = days; }
    public GiftCycle get(UUID viewer, String creature) { var owned = pairs.get(viewer); return owned == null ? null : owned.get(creature); }
    public GiftCycle track(UUID viewer, String creature) { return pairs.computeIfAbsent(viewer, key -> new LinkedHashMap<>()).computeIfAbsent(creature, id -> new GiftCycle(viewer, id)); }
    public List<GiftCycle> cycles() { return pairs.values().stream().flatMap(owned -> owned.values().stream()).toList(); }
    public List<GiftEnvelope> owned(UUID viewer) { return gifts.values().stream().filter(gift -> gift.content().viewer().equals(viewer)).toList(); }
    public List<GiftEnvelope> all() { return List.copyOf(gifts.values()); }
    public GiftEnvelope gift(UUID id) { return gifts.get(id); }
    public GiftEnvelope prepared(GiftContent content) {
        var pair = get(content.viewer(), content.creature());
        if (pair == null || !content.id().equals(pair.gift()) || pair.phase() != GiftCycle.Phase.READY || gifts.containsKey(content.id())) throw new IllegalArgumentException("Unprepared or duplicate gift");
        var envelope = new GiftEnvelope(content); gifts.put(content.id(), envelope); return envelope;
    }
    public boolean issued(GiftEnvelope gift, UUID physicalDrop) {
        if (!gift.issued(physicalDrop, clock)) return false;
        var pair = get(gift.content().viewer(), gift.content().creature());
        if (pair == null || !pair.claimed(gift.content().id())) throw new IllegalStateException("Gift pair changed during delivery"); return true;
    }
    public boolean confirmHeldTicket(GiftEnvelope gift, UUID proof) {
        if (gift == null || gifts.get(gift.content().id()) != gift || !gift.canConfirmHeldTicket(proof)) return false;
        var pair = get(gift.content().viewer(), gift.content().creature());
        if (pair == null || pair.phase() != GiftCycle.Phase.READY || !gift.content().id().equals(pair.gift())) return false;
        return issued(gift, gift.drop());
    }
    public NbtCompound toNbt() {
        var n = new NbtCompound(); n.putInt("schema", 1); n.putLong("clock", clock); n.putInt("expiry_days", expiryDays);
        var savedPairs = new NbtCompound(); pairs.forEach((viewer, owned) -> { var entries = new NbtCompound(); owned.forEach((id, cycle) -> entries.put(id, cycle.toNbt())); savedPairs.put(viewer.toString(), entries); }); n.put("pairs", savedPairs);
        var boxes = new NbtCompound(); gifts.forEach((id, gift) -> boxes.put(id.toString(), gift.toNbt())); n.put("gifts", boxes);
        var homes = new NbtCompound(); habitats.forEach((id, h) -> homes.put(id, h.toNbt())); n.put("habitats", homes);
        var places = new NbtCompound(); landmarks.forEach((id, h) -> places.put(id, h.toNbt())); n.put("landmarks", places); return n;
    }
    public static GiftLedger fromNbt(NbtCompound n) {
        var ledger = new GiftLedger(); ledger.clock = n.getLong("clock", 0); ledger.expiryDays(n.getInt("expiry_days", 60));
        if (ledger.clock < 0) throw new IllegalArgumentException("Invalid gift clock"); int invalid = 0;
        var savedPairs = n.getCompoundOrEmpty("pairs");
        for (String key : savedPairs.getKeys()) {
            try {
                var viewer = UUID.fromString(key); var owned = savedPairs.getCompoundOrEmpty(key); var cycles = new LinkedHashMap<String, GiftCycle>();
                for (String id : owned.getKeys()) try { var cycle = GiftCycle.fromNbt(owned.getCompoundOrEmpty(id)); if (!viewer.equals(cycle.viewer()) || !id.equals(cycle.creature())) throw new IllegalArgumentException("Mismatched pair"); cycles.put(id, cycle); }
                catch (IllegalArgumentException bad) { invalid++; }
                ledger.pairs.put(viewer, cycles);
            } catch (IllegalArgumentException bad) { invalid++; }
        }
        var savedGifts = n.getCompoundOrEmpty("gifts");
        for (String key : savedGifts.getKeys()) try {
            var id = UUID.fromString(key); var gift = GiftEnvelope.fromNbt(savedGifts.getCompoundOrEmpty(key));
            if (!id.equals(gift.content().id())) throw new IllegalArgumentException("Mismatched gift"); ledger.gifts.put(id, gift);
        } catch (IllegalArgumentException bad) { invalid++; }
        for (var cycle : ledger.cycles()) if (cycle.gift() != null) {
            var gift = ledger.gift(cycle.gift());
            if (gift == null || !gift.content().viewer().equals(cycle.viewer()) || !gift.content().creature().equals(cycle.creature())) {
                // Retain doubtful pair state, rather than creating a replacement reward.
                invalid++;
            }
        }
        for (String group : List.of("habitats", "landmarks")) {
            var saved = n.getCompoundOrEmpty(group); var records = group.equals("habitats") ? ledger.habitats : ledger.landmarks;
            for (var id : saved.getKeys()) try {
                if (!com.nonid.affinity.data.FamilyGraph.validId(id) || id.startsWith("player:")) throw new IllegalArgumentException("Invalid habitat owner");
                records.put(id, GiftHabitat.fromNbt(saved.getCompoundOrEmpty(id)));
            } catch (IllegalArgumentException bad) { invalid++; }
        }
        if (invalid > 0) com.nonid.affinity.NonCreatureAffinity.LOGGER.warn("Gift ledger has {} malformed or unresolved records; no replacement gifts generated", invalid);
        return ledger;
    }
}
