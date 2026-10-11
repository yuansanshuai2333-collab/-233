package com.nonid.affinity.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AffinityIncreaseTimelineTest {
 @Test void configuredMaximumIsNotTheFriendThreshold(){assertEquals(.25,AffinityIncreaseTimeline.shownRatio(50,80,200,200));assertEquals(.4,AffinityIncreaseTimeline.shownRatio(50,80,200,1000));}
 @Test void timelineIncludesOnlyTheApproved2500Milliseconds(){assertFalse(AffinityIncreaseTimeline.active(-1));assertTrue(AffinityIncreaseTimeline.active(2499));assertFalse(AffinityIncreaseTimeline.active(2500));assertEquals(0,AffinityIncreaseTimeline.opacity(2500));assertEquals(.5,AffinityIncreaseTimeline.opacity(100));assertEquals(.5,AffinityIncreaseTimeline.opacity(2250));}
 @Test void negativeAndCappedActualValuesStayWithinTheBar(){assertEquals(0,AffinityIncreaseTimeline.shownRatio(-20,10,100,200));assertEquals(.1,AffinityIncreaseTimeline.shownRatio(-20,10,100,1000));assertEquals(1,AffinityIncreaseTimeline.ratio(Integer.MAX_VALUE,100));assertEquals(0,AffinityIncreaseTimeline.ratio(1,0));}
}
