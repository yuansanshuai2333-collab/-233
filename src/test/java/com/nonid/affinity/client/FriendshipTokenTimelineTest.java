package com.nonid.affinity.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FriendshipTokenTimelineTest {
 @Test void hasThreeSecondsAndNoPreviewPauseOrLoop(){
  assertEquals(-1,FriendshipTokenTimeline.frame(-1));
  assertEquals(0,FriendshipTokenTimeline.frame(0));
  assertEquals(0,FriendshipTokenTimeline.frame(49));
  assertEquals(1,FriendshipTokenTimeline.frame(50));
  assertEquals(50,FriendshipTokenTimeline.frame(2500));
  assertEquals(59,FriendshipTokenTimeline.frame(2999));
  assertEquals(-1,FriendshipTokenTimeline.frame(3000));
  assertEquals(-1,FriendshipTokenTimeline.frame(3600));
  assertEquals(-1,FriendshipTokenTimeline.frame(Long.MAX_VALUE));
 }
}
