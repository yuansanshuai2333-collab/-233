package com.nonid.affinity.client;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompanionInfoLeaseTest {
    @Test void expiredRequestCannotReviveOldNumbers() {
        UUID target=UUID.randomUUID();var lease=new CompanionInfoLease(target,"world");long seq=lease.issue();
        for(int i=0;i<60;i++)lease.tick();
        assertFalse(lease.accept(lease.page(),target,"world",seq));
        assertTrue(lease.accept(lease.page(),target,"world",lease.issue()));
    }
    @Test void rejectsWrongPageTargetDimensionAndUnissuedOrOutOfOrderResponses() {
        UUID target=UUID.randomUUID();var lease=new CompanionInfoLease(target,"world");
        assertFalse(lease.accept(lease.page(),target,"world",1));
        long first=lease.issue(),second=lease.issue();
        assertFalse(lease.accept(UUID.randomUUID(),target,"world",first));
        assertFalse(lease.accept(lease.page(),UUID.randomUUID(),"world",first));
        assertFalse(lease.accept(lease.page(),target,"other",first));
        assertTrue(lease.accept(lease.page(),target,"world",second));
        assertFalse(lease.accept(lease.page(),target,"world",first));
        assertFalse(lease.accept(lease.page(),target,"world",second));
    }
    @Test void expiresAndClosedPageCannotBeRevivedByLateReply() {
        UUID target=UUID.randomUUID();var lease=new CompanionInfoLease(target,"world");
        for(int i=0;i<59;i++)lease.tick();assertFalse(lease.expired());lease.tick();assertTrue(lease.expired());
        long seq=lease.issue();assertTrue(lease.accept(lease.page(),target,"world",seq));assertFalse(lease.expired());
        seq=lease.issue();lease.close();assertFalse(lease.accept(lease.page(),target,"world",seq));
    }
    @Test void reopeningSameCreatureGetsIndependentIdentity() {
        UUID target=UUID.randomUUID();var first=new CompanionInfoLease(target,"world");var next=new CompanionInfoLease(target,"world");
        next.issue();assertFalse(next.accept(first.page(),target,"world",first.issue()));
    }
}
