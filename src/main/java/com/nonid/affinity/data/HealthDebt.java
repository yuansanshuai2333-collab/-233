package com.nonid.affinity.data;

import java.util.ArrayDeque;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

/** Missing health, oldest recovery first. A null author is unproven and never earns rewards. */
public final class HealthDebt {
    private record Wound(UUID author, double amount) {}
    private final ArrayDeque<Wound> wounds = new ArrayDeque<>();
    private double total;
    private static double valid(double value) { return Double.isFinite(value) ? Math.max(0, Math.min(Float.MAX_VALUE, value)) : 0; }
    private void add(UUID author, double amount) {
        amount = valid(amount);
        if (amount == 0) return;
        var tail = wounds.peekLast();
        if (tail != null && java.util.Objects.equals(author, tail.author())) {
            wounds.removeLast(); amount += tail.amount(); total -= tail.amount();
        }
        wounds.addLast(new Wound(author, amount)); total += amount;
    }
    public double recover(double amount, UUID user) {
        amount = valid(amount); double eligible = 0;
        while (amount > 0 && !wounds.isEmpty()) {
            var wound = wounds.removeFirst(); double taken = Math.min(amount, wound.amount());
            if (user != null && wound.author() != null && !user.equals(wound.author())) eligible += taken;
            amount -= taken; total -= taken;
            if (taken < wound.amount()) wounds.addFirst(new Wound(wound.author(), wound.amount() - taken));
        }
        if (wounds.isEmpty()) total = 0;
        return eligible;
    }
    public void reconcile(double health, double maxHealth) {
        double missing = valid(maxHealth - health);
        if (missing < total) recover(total - missing, null);
        else if (missing > total) {
            // Missing prior observations belong to unknown older damage, not the current attack.
            wounds.addFirst(new Wound(null, missing - total)); total = missing;
        }
    }
    public double change(double before, double after, double maxHealth, UUID author, UUID recoveringUser) {
        if (!Double.isFinite(before) || !Double.isFinite(after) || !Double.isFinite(maxHealth)) return 0;
        reconcile(before, maxHealth);
        if (after < before) { add(author, before - after); reconcile(after, maxHealth); return 0; }
        double eligible = recover(after - before, recoveringUser);
        reconcile(after, maxHealth); return Math.min(Math.max(0, after - before), eligible);
    }
    public double missing() { return total; }
    public NbtCompound toNbt() {
        var tag = new NbtCompound(); var list = new NbtList();
        for (var wound : wounds) {
            var n = new NbtCompound(); n.putDouble("amount", wound.amount());
            if (wound.author() != null) n.putString("author", wound.author().toString());
            list.add(n);
        }
        tag.put("wounds", list); return tag;
    }
    public static HealthDebt fromNbt(NbtCompound tag) {
        var debt = new HealthDebt();
        for (var element : tag.getListOrEmpty("wounds")) {
            if (!(element instanceof NbtCompound n)) continue;
            UUID author = null;
            try { author = UUID.fromString(n.getString("author", "")); }
            catch (IllegalArgumentException ignored) { /* Unknown attribution stays ineligible. */ }
            debt.add(author, n.getDouble("amount", 0));
        }
        return debt;
    }
}
