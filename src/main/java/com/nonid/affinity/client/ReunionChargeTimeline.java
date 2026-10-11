package com.nonid.affinity.client;
/** Only the six actual increases have a successful-charge animation. */
public final class ReunionChargeTimeline {
 private ReunionChargeTimeline(){}
 public static boolean valid(int before,int after){return before>=0&&after<=3&&after>before;}
 public static int frame(long millis){return millis<0||millis>=7500?-1:(int)(millis/50);}
}
