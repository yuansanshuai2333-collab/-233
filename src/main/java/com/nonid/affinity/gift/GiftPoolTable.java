package com.nonid.affinity.gift;

import java.util.*;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/** Explicit tables only. No production fallback list or invented ordinary probabilities. */
public final class GiftPoolTable {
    public enum Pool {
        FEATURE(12), ENVIRONMENT(6), UTILITY(6), PRECIOUS(3), ULTIMATE(3);
        public final int maximum; Pool(int maximum) { this.maximum = maximum; }
    }
    private static final double[] ULTIMATE_CHANCE = {0.00005, 0.0001, 0.0002, 0.0005};
    public static double ultimateChance(int tier) { if (tier < 0 || tier > 3) throw new IllegalArgumentException("Invalid gift tier"); return ULTIMATE_CHANCE[tier]; }
    public record Selection(Pool pool, double chance, List<String> items, List<Double> quantities) {
        public Selection {
            Objects.requireNonNull(pool); items = List.copyOf(items); quantities = List.copyOf(quantities);
            if (!Double.isFinite(chance) || chance < 0 || chance > 1 || items.isEmpty() || new HashSet<>(items).size() != items.size()
                    || quantities.size() != pool.maximum || quantities.stream().anyMatch(value -> !Double.isFinite(value) || value < 0)
                    || quantities.stream().mapToDouble(Double::doubleValue).sum() <= 0) throw new IllegalArgumentException("Unconfirmed or invalid gift table");
            items.forEach(item -> new GiftContent.Reward(item, 1));
            if (pool == Pool.ULTIMATE && items.contains("minecraft:nether_star")) throw new IllegalArgumentException("Nether star is excluded");
        }
    }
    private GiftPoolTable() {}
    /** Each call selects at most one pool; multiple channels require an explicitly approved caller policy. */
    public static Optional<GiftContent.Reward> draw(List<Selection> selections, int tier, Predicate<String> registered, RandomGenerator random) {
        if (tier < 0 || tier > 3) throw new IllegalArgumentException("Invalid gift tier");
        double total = 0; var used = EnumSet.noneOf(Pool.class);
        for (var selection : selections) {
            if (!used.add(selection.pool())) throw new IllegalArgumentException("Duplicate pool");
            if (selection.pool() == Pool.ULTIMATE && Double.compare(selection.chance(), ULTIMATE_CHANCE[tier]) != 0)
                throw new IllegalArgumentException("Unapproved ultimate probability");
            total += selection.chance();
        }
        if (total > 1.00000000001) throw new IllegalArgumentException("Overlapping pool probabilities");
        double roll = random.nextDouble(), boundary = 0;
        for (var selection : selections) {
            boundary += selection.chance(); if (roll >= boundary) continue;
            var eligible = selection.items().stream().filter(registered).toList(); if (eligible.isEmpty()) return Optional.empty();
            String id = eligible.get(random.nextInt(eligible.size())); // Equal probability within the selected pool.
            double sum = selection.quantities().stream().mapToDouble(Double::doubleValue).sum(), countRoll = random.nextDouble() * sum;
            int count = 1;
            for (double weight : selection.quantities()) { countRoll -= weight; if (countRoll < 0) break; count++; }
            return Optional.of(new GiftContent.Reward(id, Math.min(count, selection.pool().maximum)));
        }
        return Optional.empty();
    }
    /** Missing frozen items do not redraw rare items. Only the frozen ordinary fallback may be used. */
    public static List<GiftContent.Reward> resolve(GiftContent gift, Predicate<String> registered) {
        var result = gift.rewards().stream().filter(item -> registered.test(item.item())).toList();
        boolean missing = result.size() != gift.rewards().size();
        if (!missing) return result;
        var resolved = new ArrayList<>(result); gift.fallback().stream().filter(item -> registered.test(item.item())).forEach(resolved::add);
        return List.copyOf(resolved);
    }
}
