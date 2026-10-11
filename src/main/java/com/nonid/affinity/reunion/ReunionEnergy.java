package com.nonid.affinity.reunion;

/** Monotonic fuel receipts: duplicated items cannot spend the same receipt twice. */
public final class ReunionEnergy {
    public static long legacy(long generations) {
        return generations < 0 || generations > Long.MAX_VALUE / 3 ? -1 : generations * 3;
    }
    public static int remaining(long fuel, long spent) {
        if (fuel < 0 || spent < 0 || fuel < spent || fuel - spent > 3) return 0;
        return (int) (fuel - spent);
    }
    /** Invalid receipts must not be treated as empty and consume recharge ingredients. */
    public static boolean canRecharge(long fuel, long spent) {
        return fuel > 0 && fuel < Long.MAX_VALUE && spent >= 0
                && fuel >= spent && fuel - spent < 3;
    }
    private ReunionEnergy() {}
}
