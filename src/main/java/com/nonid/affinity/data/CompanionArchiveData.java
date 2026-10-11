package com.nonid.affinity.data;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.net.CompanionRecord;
import java.util.*;
import net.minecraft.nbt.NbtCompound;

/** Permanent viewer-scoped facts. Reading never creates a relationship or a record. */
public final class CompanionArchiveData {
    public enum Kind { ACCEPTED, COMPLETED, REJECTED, STAGE_CHANGED, DEATH, REVIVED }
    public record Event(long sequence, String source, Kind kind, long time, String dimension,
                        String detail, int before, int after, int eventStage) {
        public Event(long sequence, String source, Kind kind, long time, String dimension, String detail, int before, int after) {
            this(sequence, source, kind, time, dimension, detail, before, after, kind == Kind.STAGE_CHANGED && after >= 0 && after <= 4 ? after : -1);
        }
        public Event {
            if (sequence < 1 || time < 0 || source == null || source.isEmpty() || source.length() > 256
                    || eventStage < -1 || eventStage > 4 || kind == null || dimension == null || dimension.length() > 256 || detail == null || detail.length() > 512)
                throw new IllegalArgumentException("Invalid companion event");
        }
    }
    public record Death(long time, String dimension, double x, double y, double z, String cause) {
        public Death {
            if (time < 0 || dimension == null || dimension.length() > 256 || cause == null || cause.length() > 256
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Invalid death fact");
        }
    }
    public static final class Record {
        private CompanionRecord snapshot;
        private boolean wasFriend, dead, hidden;
        private Death death;
        private long life;
        private long lastMemorialTime;
        private final NavigableMap<Long, Event> events = new TreeMap<>();
        private final Set<String> sources = new HashSet<>();
        private Record(CompanionRecord snapshot, boolean wasFriend) { this.snapshot = snapshot; this.wasFriend = wasFriend; }
        public CompanionRecord snapshot() { return snapshot; }
        public boolean wasFriend() { return wasFriend; }
        public boolean dead() { return dead; }
        public boolean hidden() { return hidden; }
        public Death death() { return death; }
        public long deathVersion() { return life; }
        public long lastMemorialTime() { return lastMemorialTime; }
        public List<Event> events(long beforeSequence, int limit) {
            if (beforeSequence < 1 || limit < 1 || limit > 64) throw new IllegalArgumentException("Invalid history page");
            return events.headMap(beforeSequence, false).descendingMap().values().stream().limit(limit).toList();
        }
        public int eventCount() { return events.size(); }
        private boolean append(String source, Kind kind, long time, String dimension, String detail, int before, int after, int eventStage) {
            if (sources.contains(source)) return false;
            long sequence = events.isEmpty() ? 1 : Math.addExact(events.lastKey(), 1);
            var event = new Event(sequence, source, kind, time, dimension, detail, before, after, eventStage);
            events.put(sequence, event); sources.add(source); return true;
        }
    }
    private final Map<UUID, NavigableMap<String, Record>> players = new HashMap<>();
    private final Map<UUID, FamilyGraph> families = new HashMap<>();
    private final Map<String, Set<UUID>> recordedViewers = new HashMap<>();
    public Record get(UUID viewer, String id) {
        var records = players.get(viewer); return records == null ? null : records.get(id);
    }
    public FamilyGraph family(UUID viewer) { return families.get(viewer); }
    /** Only background evidence collection calls this; query paths use family(), never this method. */
    public FamilyGraph recordFamily(UUID viewer) { return families.computeIfAbsent(viewer, key -> new FamilyGraph()); }
    public Set<UUID> viewers(String id) {
        return Set.copyOf(recordedViewers.getOrDefault(id, Set.of()));
    }
    public Set<UUID> kinViewers(String id) {
        var result = new HashSet<UUID>(); families.forEach((viewer, graph) -> { if (graph.get(id) != null) result.add(viewer); });
        return Set.copyOf(result);
    }
    public List<String> ids(UUID viewer) {
        var records = players.get(viewer); return records == null ? List.of() : List.copyOf(records.navigableKeySet());
    }
    public boolean remember(UUID viewer, CompanionRecord snapshot, boolean wasFriend) {
        if (viewer == null || snapshot == null || !validSnapshot(snapshot)) throw new IllegalArgumentException("Invalid archive identity");
        var records = players.computeIfAbsent(viewer, key -> new TreeMap<>()); var prior = records.get(snapshot.id());
        if (prior == null) {
            records.put(snapshot.id(), new Record(snapshot.historical("historical"), wasFriend));
            recordedViewers.computeIfAbsent(snapshot.id(), key -> new HashSet<>()).add(viewer); return true;
        }
        var next = snapshot.historical(prior.dead ? "dead" : "historical");
        var before = prior.snapshot.toNbt(); var after = next.toNbt(); before.remove("observed"); after.remove("observed");
        boolean changed = !before.equals(after) || (wasFriend && !prior.wasFriend);
        if (changed) prior.snapshot = next;
        prior.wasFriend |= wasFriend; return changed;
    }
    private static boolean validSnapshot(CompanionRecord snapshot) {
        return FamilyGraph.validId(snapshot.id()) && !snapshot.id().startsWith("player:") && (!snapshot.id().startsWith("entity:")
                || snapshot.id().equals("entity:" + snapshot.info().query().target()));
    }
    public boolean event(UUID viewer, String id, String source, Kind kind, long time, String dimension, String detail, int before, int after) {
        return event(viewer, id, source, kind, time, dimension, detail, before, after, kind == Kind.STAGE_CHANGED && after >= 0 && after <= 4 ? after : -1);
    }
    public boolean event(UUID viewer, String id, String source, Kind kind, long time, String dimension, String detail, int before, int after, int eventStage) {
        var record = get(viewer, id); return record != null && record.append(source, kind, time, dimension, detail, before, after, eventStage);
    }
    public boolean died(UUID viewer, String id, Death death) {
        var record = get(viewer, id); if (record == null || record.dead) return false;
        long life = Math.addExact(record.life, 1);
        record.append("death:" + life, Kind.DEATH, death.time(), death.dimension(), death.cause(), -1, -1, -1);
        record.life = life; record.dead = true; record.death = death; record.snapshot = record.snapshot.historical("dead"); return true;
    }
    public boolean revived(UUID viewer, String id, long time, String dimension) {
        var record = get(viewer, id); if (record == null || !record.dead) return false;
        record.append("revived:" + record.life, Kind.REVIVED, time, dimension, "", -1, -1, -1);
        record.dead = false; record.hidden = false; record.snapshot = record.snapshot.historical("historical"); return true;
    }
    /** Caller must validate the live second-confirmation token before reaching this operation. */
    public boolean hide(UUID viewer, String id) {
        var record = get(viewer, id); if (record == null || !record.dead || !record.wasFriend || record.hidden) return false;
        record.hidden = true; return true;
    }
    public boolean reminded(UUID viewer, String id, long time) {
        var record = get(viewer, id);
        if (record == null || !record.dead || !record.wasFriend || time <= 0 || record.lastMemorialTime == time) return false;
        record.lastMemorialTime = time; return true;
    }
    public NbtCompound toNbt() {
        var root = new NbtCompound(); root.putInt("schema", 1); var users = new NbtCompound();
        var viewers = new HashSet<>(players.keySet()); viewers.addAll(families.keySet());
        for (UUID viewer : viewers) {
            var saved = new NbtCompound(); var records = new NbtCompound();
            var rows = players.get(viewer);
            if (rows != null) rows.forEach((id, record) -> {
                var n = new NbtCompound(); n.put("snapshot", record.snapshot.toNbt()); n.putBoolean("wasFriend", record.wasFriend);
                n.putBoolean("dead", record.dead); n.putBoolean("hidden", record.hidden); n.putLong("life", record.life);
                if (record.lastMemorialTime > 0) n.putLong("last_memorial", record.lastMemorialTime);
                if (record.death != null) {
                    var d = record.death; var dn = new NbtCompound(); dn.putLong("time", d.time()); dn.putString("dimension", d.dimension());
                    dn.putDouble("x", d.x()); dn.putDouble("y", d.y()); dn.putDouble("z", d.z()); dn.putString("cause", d.cause()); n.put("death", dn);
                }
                var history = new NbtCompound(); record.events.forEach((sequence, event) -> {
                    var en = new NbtCompound(); en.putString("source", event.source()); en.putString("kind", event.kind().name());
                    en.putLong("time", event.time()); en.putString("dimension", event.dimension()); en.putString("detail", event.detail());
                    en.putInt("before", event.before()); en.putInt("after", event.after()); if (event.eventStage() >= 0) en.putInt("event_stage", event.eventStage()); history.put(Long.toString(sequence), en);
                });
                n.put("events", history); records.put(id, n);
            });
            saved.put("records", records); var graph = families.get(viewer); if (graph != null) saved.put("family", graph.toNbt());
            users.put(viewer.toString(), saved);
        }
        root.put("players", users); return root;
    }
    public static CompanionArchiveData fromNbt(NbtCompound root) {
        var data = new CompanionArchiveData(); var users = root.getCompoundOrEmpty("players"); int invalid = 0;
        for (String viewerId : users.getKeys()) try {
            UUID viewer = UUID.fromString(viewerId); var saved = users.getCompoundOrEmpty(viewerId);
            if (saved.contains("family")) data.families.put(viewer, FamilyGraph.fromNbt(saved.getCompoundOrEmpty("family")));
            var records = saved.getCompoundOrEmpty("records");
            for (String id : records.getKeys()) try {
                var n = records.getCompoundOrEmpty(id); var snapshot = CompanionRecord.fromNbt(n.getCompoundOrEmpty("snapshot"));
                if (!id.equals(snapshot.id()) || !validSnapshot(snapshot)) throw new IllegalArgumentException("Invalid archive identity");
                var record = new Record(snapshot.historical("historical"), n.getBoolean("wasFriend", false));
                record.life = Math.max(0, n.getLong("life", 0));
                record.lastMemorialTime = Math.max(0, n.getLong("last_memorial", 0));
                if (n.contains("death")) {
                    var d = n.getCompoundOrEmpty("death"); record.death = new Death(d.getLong("time", 0), d.getString("dimension", ""), d.getDouble("x", 0), d.getDouble("y", 0), d.getDouble("z", 0), d.getString("cause", ""));
                    record.dead = n.getBoolean("dead", false); record.hidden = record.dead && n.getBoolean("hidden", false);
                    if (record.dead) record.snapshot = record.snapshot.historical("dead");
                }
                var history = n.getCompoundOrEmpty("events");
                for (String sequenceId : history.getKeys()) try {
                    var en = history.getCompoundOrEmpty(sequenceId);
                    var event = new Event(Long.parseLong(sequenceId), en.getString("source", ""), Kind.valueOf(en.getString("kind", "")),
                            en.getLong("time", 0), en.getString("dimension", ""), en.getString("detail", ""), en.getInt("before", -1), en.getInt("after", -1), en.getInt("event_stage", Kind.valueOf(en.getString("kind", "")) == Kind.STAGE_CHANGED && en.getInt("after", -1) >= 0 && en.getInt("after", -1) <= 4 ? en.getInt("after", -1) : -1));
                    if (!record.sources.add(event.source())) throw new IllegalArgumentException("Duplicate event source");
                    record.events.put(event.sequence(), event);
                } catch (IllegalArgumentException bad) { invalid++; }
                data.players.computeIfAbsent(viewer, key -> new TreeMap<>()).put(id, record);
                data.recordedViewers.computeIfAbsent(id, key -> new HashSet<>()).add(viewer);
            } catch (IllegalArgumentException bad) { invalid++; }
        } catch (IllegalArgumentException bad) { invalid++; }
        if (invalid > 0) NonCreatureAffinity.LOGGER.warn("Ignored {} malformed companion archive facts; entity relationships were not changed", invalid);
        return data;
    }
}
