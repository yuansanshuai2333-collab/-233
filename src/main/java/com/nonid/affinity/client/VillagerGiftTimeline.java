package com.nonid.affinity.client;
public final class VillagerGiftTimeline {
 private VillagerGiftTimeline(){}
 public static boolean active(long ms){return ms>=0&&ms<3600;}
 public static float opacity(long ms){return active(ms)?(float)Math.min(1,(3600-ms)/350.0):0;}
 public static float scale(long ms){
  double t=Math.clamp(ms/250.0,0,1)-1;
  return (float)(.86+.14*(1+2.70158*t*t*t+1.70158*t*t));
 }
}
