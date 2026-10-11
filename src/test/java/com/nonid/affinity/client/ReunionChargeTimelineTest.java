package com.nonid.affinity.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReunionChargeTimelineTest {
 @Test void onlySixActualIncreases(){int count=0;for(int a=-1;a<=4;a++)for(int b=-1;b<=4;b++)if(ReunionChargeTimeline.valid(a,b))count++;assertEquals(6,count);assertFalse(ReunionChargeTimeline.valid(3,3));assertFalse(ReunionChargeTimeline.valid(2,1));}
 @Test void finiteFramesExcludePreviewPause(){assertEquals(-1,ReunionChargeTimeline.frame(-1));assertEquals(0,ReunionChargeTimeline.frame(0));assertEquals(65,ReunionChargeTimeline.frame(3299));assertEquals(116,ReunionChargeTimeline.frame(5800));assertEquals(149,ReunionChargeTimeline.frame(7499));assertEquals(-1,ReunionChargeTimeline.frame(7500));}
}
