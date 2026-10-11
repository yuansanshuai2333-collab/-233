package com.nonid.affinity.service;
/** Confirmed count decorations; independent of affinity and item inventory. */
public final class CompanionCountStages {
    private CompanionCountStages() {}
    public static int agreement(int count){return count>=10?3:count>=5?2:count>=1?1:0;}
    public static int gifts(int count){return Math.clamp(count,0,6);}
}
