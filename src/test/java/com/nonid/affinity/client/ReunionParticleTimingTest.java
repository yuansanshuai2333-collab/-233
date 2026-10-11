package com.nonid.affinity.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReunionParticleTimingTest {
 @Test void everyParticleEndsTransparentAndOpacityIsBounded(){
  for(int life:new int[]{28,32,40}){
   for(int age=-1;age<=life+1;age++){float a=ReunionParticleTiming.alpha(age,life,3,9);assertTrue(a>=0&&a<=1);}
   assertEquals(0,ReunionParticleTiming.alpha(0,life,3,9));assertEquals(0,ReunionParticleTiming.alpha(life,life,3,9));
   assertTrue(ReunionParticleTiming.alpha(life-1,life,3,9)<ReunionParticleTiming.alpha(life-9,life,3,9));
  }
 }
 @Test void staggerDoesNotBecomeAnUnboundedEmitter(){
  assertEquals(0,ReunionParticleTiming.healStart(0));assertEquals(23,ReunionParticleTiming.healStart(18));
  assertEquals(15,ReunionParticleTiming.sparkStart(11));
  for(int i=1;i<19;i++)assertTrue(ReunionParticleTiming.healStart(i)>ReunionParticleTiming.healStart(i-1));
 }
}
