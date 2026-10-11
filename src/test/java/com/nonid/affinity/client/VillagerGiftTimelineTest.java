package com.nonid.affinity.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VillagerGiftTimelineTest {
 @Test void popHoldAndFadeHaveSeparateBoundaries(){
  assertFalse(VillagerGiftTimeline.active(-1));assertTrue(VillagerGiftTimeline.active(0));
  assertEquals(.86f,VillagerGiftTimeline.scale(0),.0001);assertEquals(1,VillagerGiftTimeline.scale(250),.0001);
  assertEquals(1,VillagerGiftTimeline.opacity(3250),.0001);assertEquals(.5,VillagerGiftTimeline.opacity(3425),.0001);
  assertTrue(VillagerGiftTimeline.active(3599));assertFalse(VillagerGiftTimeline.active(3600));
 }
}
