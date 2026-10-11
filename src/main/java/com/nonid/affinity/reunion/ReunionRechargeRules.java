package com.nonid.affinity.reunion;

/** Amounts only; identity, ownership, receipts and completion evidence are checked by the caller. */
public final class ReunionRechargeRules {
    public static int completion(boolean exact,boolean sameSpecies,int energy){
        if(energy<0||energy>=3)return 0;
        return Math.min(3-energy,exact?3:sameSpecies?1:0);
    }
    /** Full receipts are eligible for display only; keep invalid/overflow receipts silent. */
    public static boolean fullCompletion(boolean exact,boolean sameSpecies,long fuel,long spent){
        return (exact||sameSpecies)&&fuel>0&&fuel<Long.MAX_VALUE&&spent>=0&&fuel>=spent&&fuel-spent==3;
    }
    public static int materials(int energy,int tokens,int bottles){
        if(energy<0||energy>=3||tokens<=0||bottles<=0)return 0;
        return Math.min(3-energy,Math.min(tokens,bottles));
    }
    private ReunionRechargeRules(){}
}
