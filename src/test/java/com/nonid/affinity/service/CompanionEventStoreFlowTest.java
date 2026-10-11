package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nonid.affinity.service.policy.CompanionCombatPolicy.Evidence;
import com.nonid.affinity.service.policy.EngagementState;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The event STORE and the control flow of two companions bonded to the same owner.
 *
 * <p>The rule tests can show what one companion decides given a fact. What they cannot show is the
 * property the store is built around: both companions read the SAME owner-scoped facts, and one
 * companion's decision must not consume, clear or disturb what the other is about to read. That is a
 * store-level property, so it is tested against the store.
 *
 * <p>Scope: the store's own contract and two companions' independent decisions over it. Whether the
 * damage callbacks that FILL the store fire in a live game is in-game acceptance work.
 */
class CompanionEventStoreFlowTest {

    private static final UUID OWNER = UUID.fromString("eeeeeeee-0000-0000-0000-00000000000e");
    private static final UUID OTHER_OWNER = UUID.fromString("ffffffff-0000-0000-0000-00000000000f");
    private static final UUID COMPANION_A = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID COMPANION_B = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000000b");
    private static final UUID ENEMY = UUID.fromString("cccccccc-0000-0000-0000-00000000000c");

    private static final String WORLD = "minecraft:overworld";
    private static final double RECALL = 10.0;
    private static final double MARGIN = 1.0;

    @BeforeEach
    @AfterEach
    void resetStore() {
        // The store is process-wide by design, so each test starts from a known empty state rather than
        // inheriting whatever a previous test left behind.
        CompanionEventLog.clear();
    }

    private static Evidence newest(UUID ownerId) {
        List<Evidence> facts = CompanionEventLog.ownerFacts(ownerId);
        assertFalse(facts.isEmpty(), "the store must hold the fact that was just recorded");
        return facts.get(facts.size() - 1);
    }

    @Test
    @DisplayName("one owner-scoped fact is readable by both companions, unmodified")
    void bothCompanionsReadTheSameFact() {
        long sequence = CompanionEventLog.recordHostileToOwnerForTest(OWNER, ENEMY, WORLD, 40L);

        Evidence first = newest(OWNER);
        Evidence second = newest(OWNER);

        assertEquals(ENEMY, first.targetId());
        assertEquals(OWNER, first.principalId(), "an owner fact is scoped to the owner, not a companion");
        assertEquals(40L, first.tick());
        assertEquals(sequence, first.sequence(), "the store owns sequence assignment");
        // Identical reads: one companion's read must not consume anything from the other's view.
        assertEquals(first, second, "two reads must return the same fact, not two different states");
    }

    @Test
    @DisplayName("sequences are strictly increasing, so a newer fact can always supersede an older one")
    void sequencesStrictlyIncrease() {
        long first = CompanionEventLog.recordHostileToOwnerForTest(OWNER, ENEMY, WORLD, 10L);
        long second = CompanionEventLog.recordHostileToOwnerForTest(OWNER, ENEMY, WORLD, 11L);
        long third = CompanionEventLog.recordHostileToOwnerForTest(OWNER, ENEMY, WORLD, 12L);

        assertTrue(second > first && third > second,
                "the candidate ordering depends on sequence numbers being strictly monotonic");
    }

    @Test
    @DisplayName("facts are scoped per owner: another player's events are not visible")
    void factsAreScopedToTheirOwner() {
        CompanionEventLog.recordHostileToOwnerForTest(OWNER, ENEMY, WORLD, 10L);

        assertTrue(CompanionEventLog.ownerFacts(OTHER_OWNER).isEmpty(),
                "a companion must never be driven by another player's facts");
        assertFalse(CompanionEventLog.ownerFacts(OWNER).isEmpty());
    }

    @Test
    @DisplayName("pruning drops aged facts and leaves fresh ones, without touching the survivors")
    void pruningDropsOnlyAgedFacts() {
        CompanionEventLog.recordHostileToOwnerForTest(OWNER, ENEMY, WORLD, 0L);
        long fresh = CompanionEventLog.recordHostileToOwnerForTest(OWNER, ENEMY, WORLD, 90L);

        // maxAge 50 at tick 100: the fact from tick 0 is 100 ticks old and goes; the one from tick 90 is
        // 10 ticks old and stays.
        CompanionEventLog.prune(WORLD, 100L, 50L);

        List<Evidence> remaining = CompanionEventLog.ownerFacts(OWNER);
        assertEquals(1, remaining.size(), "only the aged fact may be dropped");
        assertEquals(fresh, remaining.get(0).sequence(), "the fresh fact must be the survivor");
    }

    @Test
    @DisplayName("retention is bounded, so an offline companion cannot grow the store without limit")
    void retentionIsBounded() {
        for (int i = 0; i < 200; i++) {
            CompanionEventLog.recordHostileToOwnerForTest(OWNER, ENEMY, WORLD, i);
        }
        List<Evidence> facts = CompanionEventLog.ownerFacts(OWNER);
        assertTrue(facts.size() <= 64,
                "the store must cap how many facts one player accumulates, was " + facts.size());
        // The survivors are the NEWEST ones: dropping the oldest is what keeps recent evidence usable.
        assertTrue(facts.get(facts.size() - 1).tick() >= 190L,
                "the newest facts must be the ones retained");
    }

    @Test
    void pruningOneWorldPreservesAnotherWorldWithASlowerClock() {
        String otherWorld = "example:slow_world";
        CompanionEventLog.recordHostileToOwnerForTest(OWNER, ENEMY, otherWorld, 90L);
        CompanionEventLog.recordHostileToOwnerForTest(OTHER_OWNER, ENEMY, WORLD, 900L);
        CompanionEventLog.prune(WORLD, 1000L, 50L);
        assertEquals(1, CompanionEventLog.ownerFacts(OWNER).size(),
                "another world's tick 90 may still be fresh at that world's tick 100");
        assertTrue(CompanionEventLog.ownerFacts(OTHER_OWNER).isEmpty());
        CompanionEventLog.prune(otherWorld, 140L, 50L);
        assertEquals(1, CompanionEventLog.ownerFacts(OWNER).size());
        CompanionEventLog.prune(otherWorld, 141L, 50L);
        assertTrue(CompanionEventLog.ownerFacts(OWNER).isEmpty(),
                "the event must still expire on its own world's clock");
    }

    @Test
    void playerChangingWorldsKeepsIndependentEventClocks() {
        String otherWorld = "example:slow_world";
        long slow = CompanionEventLog.recordHostileToOwnerForTest(OWNER, ENEMY, otherWorld, 90L);
        CompanionEventLog.recordHostileToOwnerForTest(OWNER, ENEMY, WORLD, 900L);
        CompanionEventLog.prune(WORLD, 1000L, 50L);
        assertEquals(List.of(slow), CompanionEventLog.ownerFacts(OWNER).stream()
                .map(Evidence::sequence).toList());
    }

    @Test
    @DisplayName("two companions over one fact list reach independent decisions")
    void twoCompanionsDecideIndependently() {
        long sequence = CompanionEventLog.recordHostileToOwnerForTest(OWNER, ENEMY, WORLD, 40L);
        Evidence fact = newest(OWNER);

        EngagementState a = new EngagementState();
        EngagementState b = new EngagementState();
        a.begin(ENEMY, 40L, sequence);

        // A is engaged and within range, so it keeps its fight.
        assertTrue(a.canEngageUnderMode(fact.targetId(), 120L, fact.sequence(), true, 2.0, 3.0,
                RECALL, MARGIN));

        // B has its OWN state over the SAME fact. A's engagement must not have advanced or consumed
        // anything B needs: a shared queue that one companion drained would leave B unable to see the
        // enemy at all.
        assertTrue(b.canEngageUnderMode(fact.targetId(), 120L, fact.sequence(), true, 2.0, 3.0,
                        RECALL, MARGIN),
                "the second companion must still see the fact the first one used");

        // And A recalling itself leaves B's view untouched.
        a.end();
        a.applyRecallResult(true, 120L, 200L, 60L);
        assertFalse(a.canEngageUnderMode(fact.targetId(), 130L, fact.sequence(), true, 2.0, 3.0,
                        RECALL, MARGIN),
                "A stays suppressed after its own recall");
        assertTrue(b.canEngageUnderMode(fact.targetId(), 130L, fact.sequence(), true, 2.0, 3.0,
                        RECALL, MARGIN),
                "B must be unaffected by A's recall");
        assertEquals(fact, newest(OWNER), "and the shared store must be unchanged by both decisions");
    }
}
