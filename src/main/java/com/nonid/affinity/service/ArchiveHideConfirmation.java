package com.nonid.affinity.service;

import java.util.*;

/** Ephemeral second-confirmation tickets, never persisted as companion facts. */
public final class ArchiveHideConfirmation {
    public record Ticket(UUID nonce, UUID page, String id, long deathVersion, long deadline) {}
    private final Map<UUID, Ticket> tickets = new HashMap<>();
    public Ticket prepare(UUID viewer, UUID page, String id, long deathVersion, long tick) {
        if (viewer == null || page == null || !com.nonid.affinity.data.FamilyGraph.validId(id) || deathVersion < 1 || tick < 0)
            throw new IllegalArgumentException("Invalid hide ticket");
        var ticket = new Ticket(UUID.randomUUID(), page, id, deathVersion, Math.addExact(tick, 600));
        tickets.put(viewer, ticket); return ticket;
    }
    public boolean consume(UUID viewer, UUID page, String id, UUID nonce, long deathVersion, long tick) {
        var ticket = tickets.get(viewer);
        if (ticket == null || !ticket.nonce().equals(nonce)) return false;
        tickets.remove(viewer);
        return ticket.page().equals(page) && ticket.id().equals(id) && ticket.deathVersion() == deathVersion
                && tick >= 0 && tick < ticket.deadline();
    }
    public void cancel(UUID viewer) { tickets.remove(viewer); }
    public void expire(long tick) { tickets.values().removeIf(ticket -> tick < 0 || tick >= ticket.deadline()); }
    public void clear() { tickets.clear(); }
}
