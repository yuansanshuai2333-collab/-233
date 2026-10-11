package com.nonid.affinity.client;
public final class ReunionNoticeTiming {
 private ReunionNoticeTiming(){}
 public static boolean active(long ms){return ms>=0&&ms<4100;}
 public static float opacity(long ms){return active(ms)?Math.max(0,Math.min(1,Math.min(ms/180f,(4100-ms)/400f))):0;}
 public static boolean title(long ms){return ms>=220&&active(ms);}
 public static boolean detail(long ms){return ms>=650&&active(ms);}
}
