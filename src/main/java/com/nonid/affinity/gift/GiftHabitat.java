package com.nonid.affinity.gift;

import java.math.BigInteger;
import java.util.*;
import net.minecraft.nbt.NbtCompound;

/** Loaded daylight only. A brief trip retains both the established home and earned visits. */
public final class GiftHabitat {
    public static final long ADAPTATION = 3 * GiftCycle.DAY;
    private String stable = "";
    private final Map<String, Long> visits = new LinkedHashMap<>();
    public String stable() { return stable; }
    public long progress(String place) { return visits.getOrDefault(place, 0L); }
    public void advance(String place, BigInteger elapsed) {
        if (!valid(place) || elapsed.signum() < 0) throw new IllegalArgumentException("Invalid habitat credit");
        if (elapsed.signum() == 0 || place.equals(stable)) return;
        long earned = elapsed.min(BigInteger.valueOf(ADAPTATION)).longValue();
        long total = Math.min(ADAPTATION, progress(place) + earned);
        if (total == ADAPTATION) { stable = place; visits.clear(); }
        else visits.put(place, total);
    }
    private static boolean valid(String place) {
        if (place == null || place.length() > 256) return false;
        var parts = place.split("\\|", -1);
        return parts.length == 3 && net.minecraft.util.Identifier.tryParse(parts[0]) != null
                && GiftProductionTable.ENVIRONMENTS.contains(parts[1]) && GiftProductionTable.STRUCTURES.contains(parts[2]);
    }
    public NbtCompound toNbt() {
        var n = new NbtCompound(); n.putString("stable", stable);
        var saved = new NbtCompound(); visits.forEach(saved::putLong); n.put("visits", saved); return n;
    }
    public static GiftHabitat fromNbt(NbtCompound n) {
        var h = new GiftHabitat(); h.stable = n.getString("stable", "");
        if (!h.stable.isEmpty() && !valid(h.stable)) throw new IllegalArgumentException("Invalid saved habitat");
        var saved = n.getCompoundOrEmpty("visits");
        for (var key : saved.getKeys()) {
            long value = saved.getLong(key, -1);
            if (!valid(key) || value < 0 || value >= ADAPTATION) throw new IllegalArgumentException("Invalid habitat visit");
            h.visits.put(key, value);
        }
        return h;
    }
}
