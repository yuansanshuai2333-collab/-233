package com.nonid.affinity.service;

/** A completed interaction reaching the reward threshold earns the same token as later completions. */
public final class CompletionRewardRules {
    private CompletionRewardRules() {}
    public static int completionGain(int base, int bonus, boolean playerInitiated) {
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, base)
                + (playerInitiated ? Math.max(0, bonus) : 0));
    }
    public static boolean earnsToken(int gain, int affinityAfter, int rewardThreshold, boolean completedInteraction) {
        return completedInteraction && gain >= 0 && affinityAfter > 0 && affinityAfter >= rewardThreshold;
    }
}
