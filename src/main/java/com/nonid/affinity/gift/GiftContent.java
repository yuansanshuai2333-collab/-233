package com.nonid.affinity.gift;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.util.Identifier;

/** Frozen identifiers and counts, never a request to reroll an unavailable reward. */
public record GiftContent(UUID id, UUID viewer, String creature, String giverName, String giverType,
                          int tier, int wrapping, long preparedAt, List<Reward> rewards, List<Reward> fallback) {
    public record Reward(String item, int count, String enchantment, int level) {
        public Reward(String item, int count) { this(item, count, "", 0); }
        public Reward {
            var key = Identifier.tryParse(item);
            if (key == null || !key.toString().equals(item) || count < 1 || count > 12
                    || enchantment == null || (!enchantment.isEmpty() &&
                    (!item.equals("minecraft:enchanted_book") || count != 1 || Identifier.tryParse(enchantment) == null
                    || !Identifier.of(enchantment).toString().equals(enchantment) || level < 1 || level > 5))
                    || enchantment.isEmpty() && level != 0) throw new IllegalArgumentException("Invalid frozen reward");
        }
        public Reward withCount(int count) { return new Reward(item, count, enchantment, level); }
        NbtCompound toNbt() {
            var n = new NbtCompound(); n.putString("item", item); n.putInt("count", count);
            if (!enchantment.isEmpty()) { n.putString("enchantment", enchantment); n.putInt("level", level); }
            return n;
        }
        static Reward fromNbt(NbtCompound n) {
            return new Reward(n.getString("item", ""), n.getInt("count", 0), n.getString("enchantment", ""), n.getInt("level", 0));
        }
    }
    public GiftContent {
        Objects.requireNonNull(id); Objects.requireNonNull(viewer);
        if (!com.nonid.affinity.data.FamilyGraph.validId(creature) || creature.startsWith("player:")
                || giverName == null || giverName.length() > 256 || Identifier.tryParse(giverType) == null
                || tier < 0 || tier > 3 || wrapping < 0 || wrapping > 3 || preparedAt < 0)
            throw new IllegalArgumentException("Invalid gift identity");
        rewards = List.copyOf(rewards); fallback = List.copyOf(fallback);
        if (rewards.isEmpty() || rewards.size() > 16 || fallback.size() > 16) throw new IllegalArgumentException("Invalid gift content");
    }
    public NbtCompound toNbt() {
        var n = new NbtCompound(); n.putString("id", id.toString()); n.putString("viewer", viewer.toString()); n.putString("creature", creature);
        n.putString("name", giverName); n.putString("type", giverType); n.putInt("tier", tier); n.putInt("wrapping", wrapping); n.putLong("prepared", preparedAt);
        var items = new NbtList(); rewards.forEach(item -> items.add(item.toNbt())); n.put("rewards", items);
        var ordinary = new NbtList(); fallback.forEach(item -> ordinary.add(item.toNbt())); n.put("fallback", ordinary); return n;
    }
    public static GiftContent fromNbt(NbtCompound n) {
        var rewards = new ArrayList<Reward>(); for (var item : n.getListOrEmpty("rewards")) if (item instanceof NbtCompound row) rewards.add(Reward.fromNbt(row));
        var fallback = new ArrayList<Reward>(); for (var item : n.getListOrEmpty("fallback")) if (item instanceof NbtCompound row) fallback.add(Reward.fromNbt(row));
        return new GiftContent(UUID.fromString(n.getString("id", "")), UUID.fromString(n.getString("viewer", "")), n.getString("creature", ""),
                n.getString("name", ""), n.getString("type", ""), n.getInt("tier", -1), n.getInt("wrapping", -1), n.getLong("prepared", -1), rewards, fallback);
    }
}
