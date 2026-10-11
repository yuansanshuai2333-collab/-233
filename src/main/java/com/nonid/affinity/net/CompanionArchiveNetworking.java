package com.nonid.affinity.net;

import com.nonid.affinity.data.*;
import com.nonid.affinity.service.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;

/** Layout-independent authenticated transport; every response page has at most 64 rows. */
public final class CompanionArchiveNetworking {
    private static final Map<UUID, Long> LAST = new HashMap<>();
    private CompanionArchiveNetworking() {}
    public static void register() {
        PayloadTypeRegistry.playC2S().register(CompanionArchivePayload.Request.ID, CompanionArchivePayload.Request.CODEC);
        PayloadTypeRegistry.playS2C().register(CompanionArchivePayload.Reply.ID, CompanionArchivePayload.Reply.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CompanionArchivePayload.Request.ID, (request, context) -> context.server().execute(() -> {
            var player = context.player(); if (!ServerPlayNetworking.canSend(player, CompanionArchivePayload.Reply.ID)) return;
            long now = context.server().getTicks(); var last = LAST.get(player.getUuid());
            if (!request.operation().equals("close") && last != null && now >= last && now - last < 5) {
                ServerPlayNetworking.send(player, reply(request, "busy", new NbtCompound())); return;
            }
            if (!request.operation().equals("close")) LAST.put(player.getUuid(), now);
            ServerPlayNetworking.send(player, read(player, request));
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
    }
    private static CompanionArchivePayload.Reply reply(CompanionArchivePayload.Request request, String status, NbtCompound body) {
        return new CompanionArchivePayload.Reply(request.page(), request.sequence(), request.operation(), status, body);
    }
    /** No player UUID is decoded from the request. */
    public static CompanionArchivePayload.Reply read(ServerPlayerEntity player, CompanionArchivePayload.Request request) {
        if (request.page() == null || request.nonce() == null || request.root() == null || request.root().length() > 80
                || request.sequence() < 1 || request.cursor() < 0 || request.limit() < 1 || request.limit() > 64)
            return reply(request, "invalid", new NbtCompound());
        var query = new CompanionArchiveQueries.Query(request.page(), request.sequence(), request.root(), request.cursor(), request.limit());
        var body = new NbtCompound(); String status;
        switch (request.operation()) {
            case "active", "dead", "records" -> {
                var scope = request.operation().equals("active") ? CompanionArchiveQueries.Scope.ACTIVE
                        : request.operation().equals("dead") ? CompanionArchiveQueries.Scope.DECEASED : CompanionArchiveQueries.Scope.RECORDS;
                var result = CompanionArchiveQueries.index(player, query, scope); status = result.status(); var rows = new NbtList();
                for (var entry : result.entries()) {
                    var n = new NbtCompound(); n.put("snapshot", entry.snapshot().toNbt()); n.putBoolean("dead", entry.dead()); n.putBoolean("hidden", entry.hidden());
                    n.putBoolean("was_friend", entry.wasFriend()); n.putBoolean("bound", entry.bound()); n.putBoolean("own_binding", entry.ownBinding());
                    if (entry.death() != null) {
                        var d = entry.death(); var dn = new NbtCompound(); dn.putLong("time", d.time()); dn.putString("dimension", d.dimension());
                        dn.putDouble("x", d.x()); dn.putDouble("y", d.y()); dn.putDouble("z", d.z()); dn.putString("cause", d.cause()); n.put("death", dn);
                    }
                    rows.add(n);
                }
                body.put("rows", rows); page(body, result.total(), result.nextCursor(), result.hasMore());
            }
            case "family" -> {
                var result = CompanionArchiveQueries.family(player, query); status = result.status(); var rows = new NbtList();
                for (var relative : result.nodes()) {
                    var node = relative.node(); var n = new NbtCompound(); n.putString("id", node.id()); n.putString("name", node.name());
                    n.putString("type", node.type()); n.putString("name_kind", node.nameKind().name()); n.putInt("gender", node.gender()); n.putInt("generations", relative.generations()); rows.add(n);
                }
                body.put("rows", rows); page(body, result.total(), result.nextCursor(), result.hasMore());
                // Links are requested separately, avoiding an unbounded response for a damaged many-parent graph.
            }
            case "links" -> {
                // Reuse the same held-item/session/sequence/root visibility checks as the node query.
                var validation = new CompanionArchiveQueries.Query(query.page(), query.sequence(), query.root(), 0, 1);
                var result = CompanionArchiveQueries.family(player, validation); status = result.status(); var rows = new NbtList();
                if (status.equals("ready") || status.equals("no_record")) {
                    var data = CompanionArchive.peek(player.getEntityWorld().getServer()); var graph = data == null ? null : data.family(player.getUuid());
                    var related = graph == null ? Set.<String>of() : graph.relatedIds(query.root());
                    var links = graph == null ? List.<FamilyGraph.Edge>of() : graph.edges().stream().filter(edge -> related.contains(edge.parent()) && related.contains(edge.child())).toList();
                    if (query.cursor() < 0 || query.cursor() > links.size()) status = "invalid";
                    else {
                        int end = (int)Math.min(links.size(), query.cursor() + query.limit());
                        for (var edge : links.subList((int)query.cursor(), end)) { var n = new NbtCompound(); n.putString("parent", edge.parent()); n.putString("child", edge.child()); n.putString("evidence", edge.evidence().name()); rows.add(n); }
                        page(body, links.size(), end, end < links.size());
                    }
                }
                body.put("rows", rows);
            }
            case "history" -> {
                var result = CompanionArchiveQueries.history(player, query); status = result.status(); var rows = new NbtList();
                for (var event : result.events()) {
                    var n = new NbtCompound(); n.putLong("sequence", event.sequence()); n.putString("kind", event.kind().name()); n.putLong("time", event.time());
                    n.putString("dimension", event.dimension()); n.putString("detail", event.detail()); n.putInt("before", event.before()); n.putInt("after", event.after()); n.putInt("event_stage", event.eventStage()); rows.add(n);
                }
                body.put("rows", rows); page(body, result.total(), result.nextCursor(), result.hasMore());
            }
            case "hide_prepare" -> { var result = CompanionArchiveQueries.prepareHide(player, query); status = result.status(); body.putString("nonce", result.nonce().toString()); }
            case "hide_confirm" -> status = CompanionArchiveQueries.confirmHide(player, query, request.nonce());
            case "hide_cancel" -> status = CompanionArchiveQueries.cancelHide(player, query);
            case "close" -> { CompanionArchiveQueries.close(player); status = "closed"; }
            default -> status = "invalid";
        }
        return reply(request, status, body);
    }
    private static void page(NbtCompound body, int total, long next, boolean more) { body.putInt("total", total); body.putLong("next", next); body.putBoolean("more", more); }
}
