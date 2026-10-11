package com.nonid.affinity.client;

import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;

/** Actual paged server rows only. An unfinished index never pretends to cover the whole list. */
public final class CompanionSearchIndex {
    public record Row(String id, String name, String type) {}
    public record Result(List<Row> rows, boolean complete, int total) {
        public Result { rows = List.copyOf(rows); }
    }
    private final NavigableMap<String, Row> rows = new TreeMap<>();
    private long next;
    private int total;
    private boolean complete;
    public void clear() { rows.clear(); next = 0; total = 0; complete = false; }
    public boolean append(long cursor, long nextCursor, int total, boolean more, List<Row> page) {
        if (complete || cursor != next || total < 0 || page.size() > 64 || nextCursor < cursor || nextCursor > total
                || nextCursor - cursor != page.size() || more && nextCursor <= cursor || !more && nextCursor != total) return false;
        if (cursor > 0 && this.total != total) return false;
        for (var row : page) if (!com.nonid.affinity.data.FamilyGraph.validId(row.id()) || row.name() == null || row.type() == null
                || row.name().length() > 256 || row.type().length() > 256 || rows.containsKey(row.id())) return false;
        var unique = new HashSet<String>(); for (var row : page) if (!unique.add(row.id())) return false;
        for (var row : page) rows.put(row.id(), row);
        this.total = total; next = nextCursor; complete = !more; return true;
    }
    public Result search(String query, Function<String, String> speciesName) {
        String filter = normalized(query); var found = new ArrayList<Row>();
        for (var row : rows.values()) {
            String localized = speciesName.apply(row.type());
            if (filter.isEmpty() || normalized(row.name()).contains(filter) || normalized(row.type()).contains(filter)
                    || normalized(localized == null ? "" : localized).contains(filter)) found.add(row);
        }
        return new Result(found, complete, total);
    }
    private static String normalized(String value) { return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT); }
    public long nextCursor() { return next; }
}
