package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.data.*;
import com.nonid.affinity.net.CompanionRecord;
import com.nonid.affinity.reunion.ReunionData;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-side API for the confirmed-layout adapter. No client supplies a viewer identity or state. */
public final class CompanionArchiveQueries {
    public enum Scope { ACTIVE, DECEASED, RECORDS }
    public record Query(UUID page, long sequence, String root, long cursor, int limit) {}
    public record Entry(CompanionRecord snapshot, boolean dead, boolean hidden, boolean wasFriend,
                        CompanionArchiveData.Death death, boolean bound, boolean ownBinding) {}
    public record Index(String status, List<Entry> entries, int total, long nextCursor, boolean hasMore) {
        public Index { entries = List.copyOf(entries); }
    }
    public record Family(String status, List<FamilyGraph.Relative> nodes, List<FamilyGraph.Edge> edges, int total, long nextCursor, boolean hasMore) {
        public Family { nodes = List.copyOf(nodes); edges = List.copyOf(edges); }
    }
    public record History(String status, List<CompanionArchiveData.Event> events, int total, long nextCursor, boolean hasMore) {
        public History { events = List.copyOf(events); }
    }
    public record Hide(String status, UUID nonce) {}
    private static final UUID ZERO = new UUID(0, 0);
    private static final class Session {
        final UUID page; final String dimension; final long began;
        long sequence;
        final Map<Scope, List<Entry>> indexes = new EnumMap<>(Scope.class);
        Session(ServerPlayerEntity player, UUID page, long now) { this.page = page; dimension = dimension(player); began = now; }
    }
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final ArchiveHideConfirmation CONFIRMATIONS = new ArchiveHideConfirmation();
    private CompanionArchiveQueries() {}
    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> close(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            CONFIRMATIONS.expire(server.getTicks());
            var iterator = SESSIONS.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next(); var player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player == null || !eligible(player) || expired(player, entry.getValue())) { CONFIRMATIONS.cancel(entry.getKey()); iterator.remove(); }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { SESSIONS.clear(); CONFIRMATIONS.clear(); });
    }
    private static boolean eligible(ServerPlayerEntity player) { return com.nonid.affinity.net.CompanionBrowseNetworking.eligible(player); }
    private static String dimension(ServerPlayerEntity player) { return player.getEntityWorld().getRegistryKey().getValue().toString(); }
    private static boolean expired(ServerPlayerEntity player, Session session) {
        long age = (long)player.getEntityWorld().getServer().getTicks() - session.began;
        return age < 0 || age >= 1200 || !dimension(player).equals(session.dimension);
    }
    public static void close(ServerPlayerEntity player) { SESSIONS.remove(player.getUuid()); CONFIRMATIONS.cancel(player.getUuid()); }
    private static String access(ServerPlayerEntity player, Query query, boolean canOpen) {
        if (!eligible(player)) { close(player); return "detector_required"; }
        if (query == null || query.page() == null || query.sequence() < 1 || query.root() == null || query.root().length() > 80
                || query.cursor() < 0 || query.limit() < 1 || query.limit() > 64) return "invalid";
        var session = SESSIONS.get(player.getUuid());
        if (session == null || !session.page.equals(query.page())) {
            if (!canOpen) return "expired";
            CONFIRMATIONS.cancel(player.getUuid()); session = new Session(player, query.page(), player.getEntityWorld().getServer().getTicks());
            SESSIONS.put(player.getUuid(), session);
        }
        if (expired(player, session)) { close(player); return "expired"; }
        if (query.sequence() <= session.sequence) return "invalid";
        session.sequence = query.sequence(); return "ready";
    }
    /** Index contains only owned, recorded rows. Local translated-species search uses the full paged index. */
    public static Index index(ServerPlayerEntity player, Query query, Scope scope) {
        String status = access(player, query, query != null && query.cursor() == 0 && "".equals(query.root()));
        if (!status.equals("ready") || scope == null) return new Index(scope == null ? "invalid" : status, List.of(), 0, 0, false);
        var session = SESSIONS.get(player.getUuid());
        var rows = session.indexes.get(scope);
        if (query.cursor() == 0 || rows == null) {
        var data = CompanionArchive.peek(player.getEntityWorld().getServer()); var collected = new ArrayList<Entry>();
        if (data != null) for (String id : data.ids(player.getUuid())) {
            var record = data.get(player.getUuid(), id);
            boolean include = switch (scope) {
                case ACTIVE -> !record.dead() && record.snapshot().info().affinity() >= NonCreatureAffinity.getConfig().familiarThreshold;
                case DECEASED -> record.dead() && record.wasFriend() && !record.hidden();
                case RECORDS -> true;
            };
            if (include) collected.add(entry(player, record));
        }
        rows = List.copyOf(collected); session.indexes.put(scope, rows);
        }
        if (query.cursor() > rows.size()) return new Index("invalid", List.of(), rows.size(), 0, false);
        int end = (int)Math.min(rows.size(), query.cursor() + query.limit());
        return new Index("ready", rows.subList((int)query.cursor(), end), rows.size(), end, end < rows.size());
    }
    private static Entry entry(ServerPlayerEntity player, CompanionArchiveData.Record record) {
        // Ledger lookup is read-only and returns a copy; no secret binding credentials leave the server.
        var binding = ReunionData.record(player.getEntityWorld().getServer(), record.snapshot().info().query().target().toString());
        boolean bound = !binding.getString("bond", "").isEmpty();
        return new Entry(record.snapshot(), record.dead(), record.hidden(), record.wasFriend(), record.death(), bound,
                bound && player.getUuidAsString().equals(binding.getString("owner", "")));
    }
    public static Family family(ServerPlayerEntity player, Query query) {
        String status = access(player, query, false); if (!status.equals("ready")) return new Family(status, List.of(), List.of(), 0, 0, false);
        var data = CompanionArchive.peek(player.getEntityWorld().getServer()); var graph = data == null ? null : data.family(player.getUuid());
        if (graph == null || graph.get(query.root()) == null) {
            var record = data == null ? null : data.get(player.getUuid(), query.root());
            if (record == null) return new Family("unavailable", List.of(), List.of(), 0, 0, false);
            var d = record.snapshot().info();
            return new Family("no_record", List.of(new FamilyGraph.Relative(new FamilyGraph.Node(query.root(), d.name(), d.type(), 0), 0)), List.of(), 1, 1, false);
        }
        if (query.cursor() > Integer.MAX_VALUE) return new Family("invalid", List.of(), List.of(), 0, 0, false);
        var page = graph.page(query.root(), (int)query.cursor(), query.limit());
        // Send only links of returned children; endpoints may be on a previous/next page, always from this viewer's graph.
        var returned = new HashSet<String>(); page.nodes().forEach(row -> returned.add(row.node().id()));
        var related = graph.relatedIds(query.root());
        var edges = graph.edges().stream().filter(edge -> returned.contains(edge.child()) && related.contains(edge.parent())).toList();
        return new Family(page.total() <= 1 ? "no_record" : "ready", page.nodes(), edges, page.total(), query.cursor() + page.nodes().size(), page.hasMore());
    }
    public static History history(ServerPlayerEntity player, Query query) {
        String status = access(player, query, false); if (!status.equals("ready")) return new History(status, List.of(), 0, 0, false);
        var data = CompanionArchive.peek(player.getEntityWorld().getServer()); var record = data == null ? null : data.get(player.getUuid(), query.root());
        if (record == null) return new History("unavailable", List.of(), 0, 0, false);
        long cursor = query.cursor() == 0 ? Long.MAX_VALUE : query.cursor(); var rows = record.events(cursor, query.limit());
        long next = rows.isEmpty() ? 0 : rows.getLast().sequence();
        boolean more = next > 1 && !record.events(next, 1).isEmpty();
        return new History(record.eventCount() == 0 ? "no_record" : "ready", rows, record.eventCount(), next, more);
    }
    public static Hide prepareHide(ServerPlayerEntity player, Query query) {
        String status = access(player, query, false); if (!status.equals("ready")) return new Hide(status, ZERO);
        var data = CompanionArchive.peek(player.getEntityWorld().getServer()); var record = data == null ? null : data.get(player.getUuid(), query.root());
        if (record == null || !record.dead() || !record.wasFriend() || record.hidden()) return new Hide("unavailable", ZERO);
        var ticket = CONFIRMATIONS.prepare(player.getUuid(), query.page(), query.root(), record.deathVersion(), player.getEntityWorld().getServer().getTicks());
        return new Hide("confirm_required", ticket.nonce());
    }
    public static String confirmHide(ServerPlayerEntity player, Query query, UUID nonce) {
        String status = access(player, query, false); if (!status.equals("ready")) return status;
        var data = CompanionArchive.peek(player.getEntityWorld().getServer()); var record = data == null ? null : data.get(player.getUuid(), query.root());
        if (record == null || !record.dead() || !record.wasFriend() || record.hidden()) { CONFIRMATIONS.cancel(player.getUuid()); return "unavailable"; }
        if (!CONFIRMATIONS.consume(player.getUuid(), query.page(), query.root(), nonce, record.deathVersion(), player.getEntityWorld().getServer().getTicks())) return "invalid";
        if (!data.hide(player.getUuid(), query.root())) return "unavailable";
        player.getEntityWorld().getServer().getOverworld().setAttached(CompanionArchive.DATA, data); return "hidden";
    }
    public static String cancelHide(ServerPlayerEntity player, Query query) {
        String status = access(player, query, false); if (status.equals("ready")) CONFIRMATIONS.cancel(player.getUuid()); return status;
    }
}
