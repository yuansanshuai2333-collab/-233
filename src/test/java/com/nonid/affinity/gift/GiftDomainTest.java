package com.nonid.affinity.gift;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GiftDomainTest {
    final UUID viewer = new UUID(27, 1), other = new UUID(27, 2), mob = new UUID(27, 3);
    final String creature = "entity:" + mob;
    GiftContent content() { return new GiftContent(UUID.randomUUID(), viewer, creature, "测试伙伴", "minecraft:pig", 0, 0, 0,
            List.of(new GiftContent.Reward("minecraft:diamond", 3)), List.of(new GiftContent.Reward("minecraft:stick", 1))); }
    Random hit() { return new Random(0) { @Override public int nextInt(int bound) { return 0; } }; }
    Random miss() { return new Random(0) { @Override public int nextInt(int bound) { return bound - 1; } }; }
    @Test void fullEarnedDayRequiredAndPausedFractionSurvivesRestart() {
        var cycle = new GiftCycle(viewer, creature);
        assertEquals(GiftCycle.Step.WAITING, cycle.advance(12000, true, 1, hit(), this::content));
        assertEquals(GiftCycle.Step.PAUSED, cycle.advance(24000, false, 2, hit(), this::content));
        cycle = GiftCycle.fromNbt(cycle.toNbt()); assertEquals(12000, cycle.progress());
        assertEquals(GiftCycle.Step.PREPARED, cycle.advance(12000, true, 3, hit(), this::content));
        UUID gift = cycle.gift(); assertEquals(GiftCycle.Step.PAUSED, cycle.advance(24000, false, 4, hit(), this::content)); assertEquals(gift, cycle.gift());
    }
    @Test void noSixDayGuaranteeAndSixtyPercentCapForBothPhases() {
        var cycle = new GiftCycle(viewer, creature);
        for (int day = 1; day <= 20; day++) assertEquals(GiftCycle.Step.FAILED_ROLL, cycle.advance(24000, true, day, miss(), this::content));
        assertEquals(60, cycle.chancePercent()); assertNull(cycle.gift());
        cycle.advance(24000, true, 21, hit(), this::content); assertTrue(cycle.claimed(cycle.gift())); assertEquals(10, cycle.chancePercent());
        for (int day = 22; day <= 40; day++) cycle.advance(24000, true, day, miss(), this::content);
        assertEquals(60, cycle.chancePercent()); assertEquals(GiftCycle.Phase.RESTING, cycle.phase());
        assertEquals(GiftCycle.Step.REST_FINISHED, cycle.advance(24000, true, 41, hit(), this::content)); assertEquals(GiftCycle.Phase.PREPARING, cycle.phase()); assertEquals(0, cycle.progress());
    }
    @Test void phaseSuccessDoesNotJudgeAnotherPhaseInSameTick() {
        var cycle = new GiftCycle(viewer, creature); cycle.advance(24000, true, 1, hit(), this::content); cycle.claimed(cycle.gift());
        assertEquals(GiftCycle.Step.REST_FINISHED, cycle.advance(24000, true, 2, hit(), this::content));
        assertEquals(GiftCycle.Step.WAITING, cycle.advance(24000, true, 2, hit(), this::content)); assertNull(cycle.gift());
        assertEquals(0, cycle.progress());
    }
    @Test void hugeJumpRetainsBacklogThroughPauseAndRestart() {
        var c = new GiftCycle(viewer, creature); c.advance(Long.MAX_VALUE,true,1,miss(),this::content);
        var left = new java.math.BigInteger(c.exactProgress()); c.advance(Long.MAX_VALUE,true,2,miss(),this::content);
        assertEquals(left.add(java.math.BigInteger.valueOf(Long.MAX_VALUE-GiftCycle.DAY)).toString(),c.exactProgress());
        c = GiftCycle.fromNbt(c.toNbt()); var saved = c.exactProgress(); c.advance(24000,false,3,hit(),this::content); assertEquals(saved,c.exactProgress());
        assertEquals(GiftCycle.Step.PREPARED,c.advance(0,true,4,hit(),this::content)); var id=c.gift();
        c.advance(Long.MAX_VALUE,true,5,hit(),this::content); assertEquals(id,c.gift()); assertEquals(0,c.progress());
    }
    @Test void multiDayRestRetainsTimeForPreparation() {
        var c=new GiftCycle(viewer,creature); c.advance(24000,true,1,hit(),this::content); c.claimed(c.gift());
        assertEquals(GiftCycle.Step.REST_FINISHED,c.advance(72000,true,2,hit(),this::content)); assertEquals(48000,c.progress());
        assertEquals(GiftCycle.Step.PREPARED,c.advance(0,true,3,hit(),this::content));
    }
    @Test void creditUsesEligibilityAndPhaseAtTimeOfCommand() {
        var c=new GiftCycle(viewer,creature); c.creditExact(java.math.BigInteger.valueOf(12000),false,true); assertEquals(0,c.progress());
        c.advance(24000,true,1,hit(),this::content); c.creditExact(java.math.BigInteger.valueOf(72000),true,true);
        assertEquals(0,c.progress()); c.claimed(c.gift()); assertEquals(0,c.progress());
        c.creditExact(java.math.BigInteger.valueOf(12000),true,true); assertEquals(12000,c.progress());
    }
    @Test void restartRecoveryCannotRestartOriginalBequestProtectionDeadline() {
        var g=new GiftEnvelope(content()); g.died(new GiftEnvelope.Location("minecraft:overworld",0,1,0),"fire",null);
        g.place(new GiftEnvelope.Location("minecraft:overworld",.5,1,.5),0,1000); var id=UUID.randomUUID(); g.reserve(viewer,id,true);
        g.emission(new GiftEnvelope.Location("minecraft:overworld",.5,1,.5),100); g=GiftEnvelope.fromNbt(g.toNbt()); g.issued(id,500);
        assertEquals(300,g.protectedUntil()); assertFalse(g.protectedDrop(id,500)); assertNotNull(g.deliveryLocation());
    }
    @Test void samePlaceMarkersShowCountAndOneNextGiftWithoutEditingSource() {
        var place=new GiftEnvelope.Location("minecraft:overworld",.5,200,.5); var a=UUID.randomUUID(); var b=UUID.randomUUID();
        var source=List.of(new GiftService.Marker(a,"one",place),new GiftService.Marker(b,"two",place));
        var groups=GiftMarkers.aggregate(source); assertEquals(1,groups.size()); assertEquals(2,groups.getFirst().count());
        assertEquals(a,groups.getFirst().nextGift()); assertEquals("one",groups.getFirst().nextName()); assertEquals(2,source.size());
        assertEquals(b,GiftMarkers.aggregate(source.subList(1,2)).getFirst().nextGift());
    }
    @Test void disabledPoolAndFailedAdapterDoNotRetryAnEarnedDayEveryTick() {
        var cycle = new GiftCycle(viewer, creature);
        assertEquals(GiftCycle.Step.DISABLED, cycle.advance(24000, true, 1, hit(), null)); assertEquals(0, cycle.progress());
        assertThrows(IllegalStateException.class, () -> cycle.advance(24000, true, 2, hit(), () -> { throw new IllegalStateException("QA adapter failure"); }));
        assertEquals(GiftCycle.Step.WAITING, cycle.advance(1, true, 3, hit(), this::content));
    }
    @Test void onePairOneFrozenGiftAndOtherViewerCannotReserve() {
        var ledger = new GiftLedger(); var cycle = ledger.track(viewer, creature); var content = content(); cycle.advance(24000, true, 1, hit(), () -> content); var gift = ledger.prepared(content);
        assertThrows(IllegalArgumentException.class, () -> ledger.prepared(content));
        assertFalse(gift.reserve(other, UUID.randomUUID(), false)); assertEquals(GiftEnvelope.State.READY, gift.state());
        var drop = UUID.randomUUID(); assertTrue(gift.reserve(viewer, drop, false)); assertFalse(gift.reserve(viewer, UUID.randomUUID(), false));
        assertTrue(ledger.issued(gift, drop)); assertFalse(ledger.issued(gift, drop)); assertEquals(GiftCycle.Phase.RESTING, cycle.phase()); assertNull(ledger.get(other, creature));
        var loaded = GiftLedger.fromNbt(ledger.toNbt()); assertEquals(content, loaded.gift(content.id()).content()); assertEquals(gift.token(), loaded.gift(content.id()).token());
    }
    @Test void deathTransferRetainsContentAndRevivalRemovesPlaceOnly() {
        var gift = new GiftEnvelope(content()); var frozen = gift.content();
        assertTrue(gift.died(new GiftEnvelope.Location("minecraft:overworld", 1, 2, 3), "minecraft:player_attack", viewer));
        assertFalse(gift.died(new GiftEnvelope.Location("minecraft:overworld", 2, 3, 4), "other", other));
        assertTrue(gift.place(new GiftEnvelope.Location("minecraft:overworld", 2, 2, 3), 100, 60 * 24000L));
        assertFalse(gift.place(new GiftEnvelope.Location("minecraft:overworld", 30, 2, 3), 100, 60 * 24000L));
        gift = GiftEnvelope.fromNbt(gift.toNbt()); assertEquals(frozen, gift.content()); assertEquals(viewer, gift.killer());
        assertTrue(gift.revive()); assertNull(gift.placed()); assertNull(gift.death()); assertEquals(frozen, gift.content());
    }
    @Test void unplacedDoesNotExpireAndPlacedExpiryHasExactRuntimeBoundary() {
        var gift = new GiftEnvelope(content()); gift.died(new GiftEnvelope.Location("minecraft:overworld", 0, 1, 0), "void", null);
        assertFalse(gift.expire(999999999)); gift.place(new GiftEnvelope.Location("minecraft:overworld", .5, 1, .5), 100, 60 * 24000L);
        assertFalse(gift.expire(100 + 60 * 24000L - 1)); assertTrue(gift.expire(100 + 60 * 24000L)); assertFalse(gift.expire(Long.MAX_VALUE)); assertNull(gift.placed());
    }
    @Test void claimedWhileDeadRevivesIntoFreshRestAndUnclaimedIntoReady() {
        var cycle = new GiftCycle(viewer, creature); cycle.advance(24000, true, 1, hit(), this::content); var id = cycle.gift();
        assertTrue(cycle.death()); assertEquals(GiftCycle.Phase.DECEASED, cycle.phase()); assertTrue(cycle.revive()); assertEquals(GiftCycle.Phase.READY, cycle.phase()); assertEquals(id, cycle.gift());
        cycle.death(); cycle.claimed(id); assertTrue(cycle.revive()); assertEquals(GiftCycle.Phase.RESTING, cycle.phase()); assertEquals(0, cycle.progress()); assertEquals(10, cycle.chancePercent());
    }
    @Test void failedSpawnRollbackPreservesOriginalNonceAndPayload() {
        var gift = new GiftEnvelope(content()); var token = gift.token(); var drop = UUID.randomUUID();
        gift.reserve(viewer, drop, false); gift = GiftEnvelope.fromNbt(gift.toNbt()); assertTrue(gift.rollback(drop)); assertEquals(token, gift.token());
        assertFalse(gift.rollback(drop)); assertEquals(GiftEnvelope.State.READY, gift.state()); assertTrue(gift.reserve(viewer, UUID.randomUUID(), false));
    }
    @Test void protectionOnlyOriginalBequestDropAndEndsAtTwoHundredRuntimeTicks() {
        var gift = new GiftEnvelope(content()); gift.died(new GiftEnvelope.Location("minecraft:overworld", 0, 1, 0), "cactus", null); gift.place(new GiftEnvelope.Location("minecraft:overworld", .5, 1, .5), 0, 1000);
        var drop = UUID.randomUUID(); gift.reserve(viewer, drop, true); gift.issued(drop, 100); gift = GiftEnvelope.fromNbt(gift.toNbt());
        assertTrue(gift.protectedDrop(drop, 299)); assertFalse(gift.protectedDrop(drop, 300)); assertFalse(gift.protectedDrop(UUID.randomUUID(), 101));
        var normal = new GiftEnvelope(content()); var otherDrop = UUID.randomUUID(); normal.reserve(viewer, otherDrop, false); normal.issued(otherDrop, 100); assertFalse(normal.protectedDrop(otherDrop, 101));
    }
    @Test void partialOpeningRetriesOnlyUndeliveredItemsAndBlocksCopiedTicket() {
        var gift = new GiftEnvelope(content()); var drop = UUID.randomUUID(); gift.reserve(viewer, drop, false); gift.issued(drop, 0);
        assertFalse(gift.startOpen(UUID.randomUUID(), gift.content().rewards())); assertTrue(gift.startOpen(gift.token(), gift.content().rewards()));
        assertTrue(gift.delivered(1)); gift = GiftEnvelope.fromNbt(gift.toNbt()); assertEquals(2, gift.remaining().getFirst().count());
        var overflow = UUID.randomUUID(); assertTrue(gift.reserveReward(overflow, 2)); assertFalse(gift.delivered(1));
        gift = GiftEnvelope.fromNbt(gift.toNbt()); assertEquals(overflow, gift.rewardDrop()); assertTrue(gift.rewardSpawned(overflow)); assertEquals(GiftEnvelope.State.OPENED, gift.state());
        assertFalse(gift.rewardSpawned(overflow)); assertFalse(gift.startOpen(gift.token(), gift.content().rewards()));
    }
    @Test void unresolvedInterruptedTicketIsRetainedRatherThanRecreated() {
        var ledger = new GiftLedger(); var content = content(); var cycle = ledger.track(viewer, creature); cycle.advance(24000, true, 1, hit(), () -> content); var gift = ledger.prepared(content);
        var drop = UUID.randomUUID(); gift.reserve(viewer, drop, false); var loaded = GiftLedger.fromNbt(ledger.toNbt());
        assertEquals(GiftEnvelope.State.DELIVERING, loaded.gift(content.id()).state()); assertFalse(loaded.gift(content.id()).reserve(viewer, UUID.randomUUID(), false)); assertEquals(content.id(), loaded.get(viewer, creature).gift());
    }
    @Test void queryDoesNotCreatePairOrTicketAndDifferentPairsRemainIndependent() {
        var ledger = new GiftLedger(); var before = ledger.toNbt(); assertNull(ledger.get(viewer, creature)); assertNull(ledger.gift(UUID.randomUUID())); assertTrue(ledger.owned(viewer).isEmpty()); assertEquals(before, ledger.toNbt());
        var a = ledger.track(viewer, creature); var b = ledger.track(other, creature); a.advance(100, true, 1, hit(), this::content); assertEquals(0, b.progress());
    }

    @Test void heldTicketConfirmsPersistedReservationOnlyOnceAfterRoundTrip() {
        var data=new GiftLedger();var c=content();data.track(viewer,creature).advance(24000,true,1,hit(),()->c);var g=data.prepared(c);g.reserve(viewer,UUID.randomUUID(),false);g.emission(new GiftEnvelope.Location("minecraft:overworld",1,2,3),data.clock());
        data=GiftLedger.fromNbt(data.toNbt());g=data.gift(c.id());var before=data.toNbt();
        assertFalse(data.confirmHeldTicket(g,UUID.randomUUID()));assertEquals(before,data.toNbt());
        assertTrue(data.confirmHeldTicket(g,g.token()));assertEquals(GiftCycle.Phase.RESTING,data.get(viewer,creature).phase());assertFalse(data.confirmHeldTicket(g,g.token()));
        assertTrue(g.startOpen(g.token(),g.content().rewards()));assertTrue(g.delivered(3));assertEquals(GiftEnvelope.State.OPENED,g.state());assertFalse(data.confirmHeldTicket(g,g.token()));
    }
    @Test void incompleteReservationAndUnreservedValidTokenDoNotIssue() {
        var data=new GiftLedger();var c=content();data.track(viewer,creature).advance(24000,true,1,hit(),()->c);var g=data.prepared(c);
        assertFalse(data.confirmHeldTicket(g,g.token()));assertEquals(GiftEnvelope.State.READY,g.state());g.reserve(viewer,UUID.randomUUID(),false);assertFalse(data.confirmHeldTicket(g,g.token()));assertEquals(GiftEnvelope.State.DELIVERING,g.state());
    }
    @Test void conflictingCycleDoesNotPartiallyCommitTicketRecovery() {
        var data=new GiftLedger();var c=content();var pair=data.track(viewer,creature);pair.advance(24000,true,1,hit(),()->c);var g=data.prepared(c);g.reserve(viewer,UUID.randomUUID(),false);g.emission(new GiftEnvelope.Location("minecraft:overworld",1,2,3),0);pair.claimed(c.id());
        var before=data.toNbt();assertFalse(data.confirmHeldTicket(g,g.token()));assertEquals(before,data.toNbt());assertEquals(GiftEnvelope.State.DELIVERING,g.state());
    }
    @Test void bearerConfirmationNeverRenewsBequestProtectionOrChangesClaimant() {
        var data=new GiftLedger();var c=content();data.track(viewer,creature).advance(24000,true,1,hit(),()->c);var g=data.prepared(c);g.died(new GiftEnvelope.Location("minecraft:overworld",0,1,0),"fire",null);g.place(new GiftEnvelope.Location("minecraft:overworld",.5,1,.5),0,1000);g.reserve(viewer,UUID.randomUUID(),true);g.emission(new GiftEnvelope.Location("minecraft:overworld",.5,1,.5),0);for(int n=0;n<500;n++)data.tick();assertTrue(data.confirmHeldTicket(g,g.token()));assertEquals(200,g.protectedUntil());assertEquals(viewer,g.claimant());assertFalse(g.protectedDrop(g.drop(),data.clock()));
    }
}
