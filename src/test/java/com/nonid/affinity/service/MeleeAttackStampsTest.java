package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The attack-stamp lifecycle, which is what makes a late hit rejectable.
 *
 * <p>Two faults are covered here, both reachable in real play:
 *
 * <ul>
 *   <li><b>a melee attempt that does not land leaves its stamp behind.</b> The stamp would then sit
 *       there for the rest of the round, and any later damage on the same target -- typically an arrow
 *       already in flight -- would be credited to a swing that had already finished, renewing a fight
 *       nothing had hit.</li>
 *   <li><b>a stamp outlives the fight it belonged to.</b> After the fight ends and a new one starts
 *       against the SAME creature, the victim id matches, so only the generation and the stamp's own
 *       existence can reject it.</li>
 * </ul>
 *
 * <p>Scope, stated plainly: this exercises the stamp unit and the pure decision it exposes. It does not
 * fire a real {@code tryAttack} (that needs a booted game), so what is proven is the lifecycle and the
 * decision, not that the mixin is positioned correctly on the real method. The mixin's anchor is checked
 * separately by {@code AttackStampMixinContractTest}, and the end-to-end behaviour is an in-game
 * acceptance item.
 */
class MeleeAttackStampsTest {

    private static final UUID COMPANION = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID OTHER_COMPANION = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000000b");
    private static final UUID TARGET = UUID.fromString("cccccccc-0000-0000-0000-00000000000c");
    private static final UUID OTHER_TARGET = UUID.fromString("dddddddd-0000-0000-0000-00000000000d");

    private static final long GENERATION = 7L;
    private static final long NOW = 100L;

    @BeforeEach
    @AfterEach
    void resetStamps() {
        MeleeAttackStamps.clearAll();
    }

    @Test
    @DisplayName("a stamp is live only while its attack call is running")
    void stampIsScopedToTheAttackCall() {
        Object token = MeleeAttackStamps.recordToken(COMPANION, TARGET, GENERATION, NOW);
        assertNotNull(MeleeAttackStamps.current(COMPANION), "the stamp exists during the attack call");
        assertTrue(MeleeAttackStamps.credits(MeleeAttackStamps.current(COMPANION), TARGET, GENERATION,
                NOW));

        // The attack call returns: the stamp goes with it.
        assertTrue(MeleeAttackStamps.clearToken(COMPANION, token), "the finishing call must clear it");
        assertNull(MeleeAttackStamps.current(COMPANION), "nothing may remain after the call returns");
        assertFalse(MeleeAttackStamps.credits(MeleeAttackStamps.current(COMPANION), TARGET, GENERATION,
                        NOW),
                "an orphaned stamp must not authorise anything");
    }

    @Test
    @DisplayName("a stamp from an earlier tick is refused, so a stranded stamp cannot authorise a later hit")
    void stampFromAnEarlierTickIsRefused() {
        // Melee damage resolves in the same tick as the attempt, so a stamp from an earlier tick is by
        // definition not the attack that just landed. This bound is what keeps the design safe even if a
        // throwable out of a nested call skipped the cleanup injection.
        MeleeAttackStamps.recordToken(COMPANION, TARGET, GENERATION, NOW);
        assertFalse(MeleeAttackStamps.credits(MeleeAttackStamps.current(COMPANION), TARGET, GENERATION,
                        NOW + 1L),
                "a stamp issued last tick must not credit a hit landing this tick");
        assertTrue(MeleeAttackStamps.credits(MeleeAttackStamps.current(COMPANION), TARGET, GENERATION,
                        NOW),
                "on its own tick it is still valid");
    }

    @Test
    @DisplayName("REGRESSION: a melee attempt that never landed cannot credit a later projectile on the same target")
    void failedMeleeCannotCreditALaterProjectile() {
        // The companion swings at the enemy and misses: tryAttack runs and returns without any damage.
        Object token = MeleeAttackStamps.recordToken(COMPANION, TARGET, GENERATION, NOW);
        MeleeAttackStamps.clearToken(COMPANION, token);   // the RETURN edge of the attack call

        // Twenty ticks later an arrow it fired earlier finally lands on that SAME enemy. Before the
        // lifecycle was bound to the attack call, the leftover stamp matched the target and the hit was
        // credited, renewing a fight in which nothing had actually connected.
        assertFalse(MeleeAttackStamps.credits(MeleeAttackStamps.current(COMPANION), TARGET, GENERATION,
                        NOW + 20L),
                "a finished attempt must not authorise a later hit on the same target");
    }

    @Test
    @DisplayName("REGRESSION: after a re-engagement, an in-flight projectile from the previous fight is rejected")
    void oldProjectileCannotRenewANewFightOnTheSameTarget() {
        // Fight one, generation 7: the companion launches an attack at the enemy.
        MeleeAttackStamps.recordToken(COMPANION, TARGET, GENERATION, NOW);

        // The fight ends and a new one begins against the SAME creature, so the victim id still matches.
        // Ending the fight clears any launch still in flight.
        MeleeAttackStamps.clear(COMPANION);
        long newGeneration = GENERATION + 1L;

        // The old projectile, or a stale report from the old attempt, arrives now.
        assertFalse(MeleeAttackStamps.credits(MeleeAttackStamps.current(COMPANION), TARGET,
                        newGeneration, NOW + 5L),
                "a stamp from the previous fight must not renew the fight that replaced it");
    }

    @Test
    @DisplayName("a hit on a different creature is never credited, even with a live stamp")
    void mismatchedVictimIsRejected() {
        MeleeAttackStamps.recordToken(COMPANION, TARGET, GENERATION, NOW);
        assertFalse(MeleeAttackStamps.credits(MeleeAttackStamps.current(COMPANION), OTHER_TARGET,
                        GENERATION, NOW),
                "a hit on another creature says nothing about this fight");
    }

    @Test
    @DisplayName("a newer attempt replaces the older stamp, and the older call cannot delete the newer one")
    void newerAttemptSurvivesTheOlderCallReturning() {
        Object first = MeleeAttackStamps.recordToken(COMPANION, TARGET, GENERATION, NOW);
        // A second attempt begins before the first has signalled its end. Only one attack call can be in
        // progress for one creature, so the newer stamp is the only valid one.
        Object second = MeleeAttackStamps.recordToken(COMPANION, TARGET, GENERATION, NOW + 1L);
        assertNotNull(second);

        // The older call now returns. It must not delete the newer stamp, or a hit from the attack that
        // is actually running would be refused.
        assertFalse(MeleeAttackStamps.clearToken(COMPANION, first),
                "the older call's token must no longer own the stamp");
        assertNotNull(MeleeAttackStamps.current(COMPANION), "the newer stamp must survive");
        assertTrue(MeleeAttackStamps.credits(MeleeAttackStamps.current(COMPANION), TARGET, GENERATION,
                NOW + 1L));

        // And the newer call can still clean up after itself.
        assertTrue(MeleeAttackStamps.clearToken(COMPANION, second));
        assertNull(MeleeAttackStamps.current(COMPANION));
    }

    @Test
    @DisplayName("one companion's stamp is never visible to another")
    void stampsAreScopedPerCompanion() {
        MeleeAttackStamps.recordToken(COMPANION, TARGET, GENERATION, NOW);
        assertNull(MeleeAttackStamps.current(OTHER_COMPANION),
                "a second companion must not inherit the first one's launch");
        MeleeAttackStamps.recordToken(OTHER_COMPANION, OTHER_TARGET, GENERATION, NOW);
        assertEquals(2, MeleeAttackStamps.heldCount());
        MeleeAttackStamps.clear(COMPANION);
        assertNotNull(MeleeAttackStamps.current(OTHER_COMPANION),
                "clearing one companion must not clear another");
    }

    @Test
    @DisplayName("clearAll empties the token table as well as the stamp table")
    void clearAllEmptiesBothTables() {
        // Regression: clearAll used to clear only the stamps, so the token table kept growing across
        // server stops inside one JVM and a surviving token could still match a later clearToken call.
        MeleeAttackStamps.recordToken(COMPANION, TARGET, GENERATION, NOW);
        MeleeAttackStamps.recordToken(OTHER_COMPANION, OTHER_TARGET, GENERATION, NOW);
        assertEquals(2, MeleeAttackStamps.heldCount());
        assertEquals(2, MeleeAttackStamps.tokenCount());

        MeleeAttackStamps.clearAll();

        assertEquals(0, MeleeAttackStamps.heldCount(), "no stamp may survive a full reset");
        assertEquals(0, MeleeAttackStamps.tokenCount(), "and no token either");
        assertNull(MeleeAttackStamps.current(COMPANION));
    }

    @Test
    @DisplayName("clearing a token with nothing to clear reports that nothing happened")
    void clearingWithoutAStampIsHarmless() {
        assertFalse(MeleeAttackStamps.clearToken(COMPANION, new Object()));
        assertFalse(MeleeAttackStamps.clearToken(COMPANION, null));
        assertFalse(MeleeAttackStamps.clearToken(null, new Object()));
        // And no null-tolerance hole: credits() must not throw on a missing stamp or victim.
        assertFalse(MeleeAttackStamps.credits(null, TARGET, GENERATION, NOW));
        assertFalse(MeleeAttackStamps.credits(MeleeAttackStamps.current(COMPANION), null, GENERATION,
                NOW));
    }
}
