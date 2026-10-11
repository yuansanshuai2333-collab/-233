package com.nonid.affinity.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReunionNoticeTimingTest {
 @Test void detailsAppearAfterEmotionAndEndTogether(){
  assertFalse(ReunionNoticeTiming.title(219));assertTrue(ReunionNoticeTiming.title(220));
  assertFalse(ReunionNoticeTiming.detail(649));assertTrue(ReunionNoticeTiming.detail(650));
  assertFalse(ReunionNoticeTiming.active(4100));assertFalse(ReunionNoticeTiming.detail(4100));assertFalse(ReunionNoticeTiming.title(4100));
 }
 @Test void fadeRemainsBoundedAndEndsTransparent(){
  assertEquals(0,ReunionNoticeTiming.opacity(-1));assertEquals(0,ReunionNoticeTiming.opacity(0));assertEquals(1,ReunionNoticeTiming.opacity(180));assertEquals(.5f,ReunionNoticeTiming.opacity(3900));assertEquals(0,ReunionNoticeTiming.opacity(4100));
 }
}
