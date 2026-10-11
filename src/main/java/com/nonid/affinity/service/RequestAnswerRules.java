package com.nonid.affinity.service;

/** Answer-time checks shared by acceptance and rejection, including the interval before timeout cleanup. */
public final class RequestAnswerRules {
    private RequestAnswerRules() {}
    public static boolean allowed(boolean playerAvailable, boolean creatureAvailable, boolean sameWorld,
                                  double squaredDistance, double range) {
        return playerAvailable && creatureAvailable && sameWorld
                && Double.isFinite(squaredDistance) && squaredDistance >= 0
                && Double.isFinite(range) && range > 0 && squaredDistance <= range * range;
    }

    public static boolean expired(long now, long deadline) {
        return deadline <= 0 || now >= deadline;
    }
}
