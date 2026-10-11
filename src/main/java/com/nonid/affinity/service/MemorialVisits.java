package com.nonid.affinity.service;

import java.util.*;

/** One physical visit, with persisted notification times supplied by the archive. */
public final class MemorialVisits {
    public static final long COOLDOWN_MILLIS = 300_000;
    private static final class Visit {
        final boolean eligible; boolean offered;
        Visit(boolean eligible) { this.eligible = eligible; }
    }
    private final Map<String, Visit> visits = new HashMap<>();
    public List<String> update(Map<String, Long> nearbyLastNotice, boolean canNotify, long now) {
        visits.keySet().retainAll(nearbyLastNotice.keySet());
        var result = new ArrayList<String>();
        for (var entry : new TreeMap<>(nearbyLastNotice).entrySet()) {
            long last = entry.getValue();
            var visit = visits.computeIfAbsent(entry.getKey(), id -> new Visit(last == 0 || now >= last && now - last >= COOLDOWN_MILLIS));
            if (canNotify && visit.eligible && !visit.offered) { visit.offered = true; result.add(entry.getKey()); }
        }
        return List.copyOf(result);
    }
}
