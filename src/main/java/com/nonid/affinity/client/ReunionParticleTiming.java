package com.nonid.affinity.client;
/** Timing in ticks derived from the confirmed 50ms art frames. */
public final class ReunionParticleTiming {
 private ReunionParticleTiming(){}
 public static float alpha(int age,int lifetime,float fadeIn,float fadeOut){
  if(age<0||age>=lifetime)return 0;
  return Math.max(0,Math.min(1,Math.min(age/fadeIn,(lifetime-age)/fadeOut)));
 }
 public static int healStart(int index){return Math.round(index*1.3f);}
 public static int sparkStart(int index){return Math.round(index*1.4f);}
}
