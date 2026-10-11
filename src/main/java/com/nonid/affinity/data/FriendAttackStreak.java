package com.nonid.affinity.data;

import net.minecraft.nbt.NbtCompound;

/** A friend-started session continues below the threshold, ending after 30 seconds without attacks. */
public final class FriendAttackStreak {
    private long last = -1;
    private int count;
    public int attack(long now, boolean friend) {
        boolean fresh = last < 0 || now < last || now - last >= 600;
        if (fresh && !friend) { last = -1; count = 0; return 0; }
        if (fresh) count = 1;
        else if (now - last <= 200) count = Math.min(3, count + 1);
        else count = 1;
        last = now;
        return fresh ? 0 : count >= 3 ? 2 : 1;
    }
    public NbtCompound toNbt() {
        var tag = new NbtCompound(); tag.putLong("last", last); tag.putInt("count", count); return tag;
    }
    public static FriendAttackStreak fromNbt(NbtCompound tag) {
        var state = new FriendAttackStreak(); state.last = Math.max(-1, tag.getLong("last", -1L));
        state.count = Math.max(0, Math.min(3, tag.getInt("count", 0))); return state;
    }
}
