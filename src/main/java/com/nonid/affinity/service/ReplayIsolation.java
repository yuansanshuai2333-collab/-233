package com.nonid.affinity.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-thread state for marked replays. No classes or installation dependency on Recall Room. */
public final class ReplayIsolation {
    private static final Map<UUID, Set<UUID>> ACTIVE = new HashMap<>();
    private ReplayIsolation() {}

    public static boolean isReplay(Map<String, String> metadata) {
        return metadata != null && "true".equals(metadata.get("recall_room"));
    }

    /** Called only after the engine reports a successful start, never during eligibility probes. */
    public static boolean begin(UUID instance, List<UUID> actors, Map<String, String> metadata) {
        if (instance == null || !isReplay(metadata)) return false;
        Set<UUID> ids = new HashSet<>();
        if (actors != null) for (UUID actor : actors) if (actor != null) ids.add(actor);
        ACTIVE.putIfAbsent(instance, Set.copyOf(ids));
        return true;
    }

    public static boolean end(UUID instance) {
        return instance != null && ACTIVE.remove(instance) != null;
    }

    public static boolean isActor(UUID actor) {
        return actor != null && ACTIVE.values().stream().anyMatch(ids -> ids.contains(actor));
    }

    /** Snapshot only the exact marked participants; unrelated viewers must remain unaffected. */
    public static Set<UUID> participantsWith(UUID actor) {
        var participants = new HashSet<UUID>();
        if (actor != null) for (var ids : ACTIVE.values()) if (ids.contains(actor)) participants.addAll(ids);
        return Set.copyOf(participants);
    }

    public static boolean protects(UUID creature, UUID player) {
        return creature != null && player != null && ACTIVE.values().stream()
                .anyMatch(ids -> ids.contains(creature) && ids.contains(player));
    }

    /** Relation keys come from saved data and may be malformed. */
    public static boolean protectsKey(UUID creature, String key) {
        try { return key != null && protects(creature, UUID.fromString(key)); }
        catch (IllegalArgumentException ignored) { return false; }
    }

    public static void clear() { ACTIVE.clear(); }
}
