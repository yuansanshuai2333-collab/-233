package com.nonid.affinity.client;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GameplayNoticeQueueTest {
 @Test void failuresCannotBeEvictedOrStarvedByStatusFlood(){var q=new GameplayNoticeQueue<String>();q.offer(UUID.randomUUID(),"failure",2,0);for(int i=0;i<100;i++)q.offer(UUID.randomUUID(),"state"+i,0,1);assertEquals(8,q.size());assertEquals("failure",q.poll(2));}
 @Test void highPriorityArrivesIntoFullQueueAndEqualPriorityRemainsFifo(){var q=new GameplayNoticeQueue<String>();for(int i=0;i<8;i++)q.offer(UUID.randomUUID(),"state"+i,0,0);q.offer(UUID.randomUUID(),"failure1",2,1);q.offer(UUID.randomUUID(),"failure2",2,2);assertEquals(8,q.size());assertEquals("failure1",q.poll(3));assertEquals("failure2",q.poll(3));}
 @Test void duplicateReceiptDoesNotRestartOrRequeue(){var q=new GameplayNoticeQueue<String>();var id=UUID.randomUUID();assertTrue(q.offer(id,"first",1,0));assertEquals("first",q.poll(1));assertFalse(q.offer(id,"duplicate",2,20));assertNull(q.poll(21));}
 @Test void ExpiryAndWorldResetDoNotReplayOldPendingNotices(){var q=new GameplayNoticeQueue<String>();q.offer(UUID.randomUUID(),"old",2,0);assertNull(q.poll(20000));q.offer(UUID.randomUUID(),"pending",2,20001);q.clear();assertEquals(0,q.size());assertNull(q.poll(20002));}
}
