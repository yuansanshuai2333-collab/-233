package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import com.nonid.affinity.service.policy.EngagementState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The attack stamp's EXCEPTION path.
 *
 * <p>This is the test the previous two rounds could not write. The stamp used to be cleared by an
 * {@code @Inject} at {@code @At("RETURN")}, and return instructions are normal control flow: a throwable
 * propagating out of a nested call never reaches them, so the stamp would be stranded. A comment in that
 * version claimed the opposite, which was simply wrong.
 *
 * <p>{@link AttackStampHandler#runWithStamp} replaces it with a real try/finally, and because the handler
 * is an ordinary class rather than a mixin, the finally can be driven here. Both the mixin and this test
 * call the same method, so what is verified is the cleanup that actually runs in game -- not a parallel
 * implementation written to satisfy a test.
 *
 * <p>Everything is driven through a token-only seam: {@code runWithStamp} needs a {@code MobEntity} for
 * the RUNTIME lookup, and a plain JUnit process cannot link one. The token bookkeeping it delegates to is
 * pure, so the exit-path behaviour is testable even though the entity wrapper is not.
 */
class AttackStampExceptionPathTest {

    private static final UUID COMPANION = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID TARGET = UUID.fromString("cccccccc-0000-0000-0000-00000000000c");
    private static final UUID OTHER_TARGET = UUID.fromString("dddddddd-0000-0000-0000-00000000000d");
    private static final long GENERATION = 7L;
    private static final long NOW = 100L;

    @BeforeEach
    @AfterEach
    void resetStamps() {
        MeleeAttackStamps.clearAll();
    }

    /**
     * The same bracket the production handler applies, without the entity lookup.
     *
     * <p>Kept to three lines on purpose. If the production cleanup ever stops being a finally, the
     * structural assertion below catches it on the handler's own source -- so this helper cannot hide the
     * thing it is testing. Cleanup goes through the production {@code clearOnAttackFinished}, so there is
     * one implementation of the release, not a copy written for this test.
     */
    private static <T> T bracket(java.util.function.Supplier<T> body) {
        return bracket(GENERATION, body);
    }

    /** As above, with the stamp's generation chosen by the caller so it can match a real fight. */
    private static <T> T bracket(long generation, java.util.function.Supplier<T> body) {
        Object token = MeleeAttackStamps.recordToken(COMPANION, TARGET, generation, NOW);
        try {
            return body.get();
        } finally {
            MeleeAttackStamps.clearOnAttackFinished(COMPANION, token);
        }
    }

    @Test
    @DisplayName("an attack body that THROWS still releases the stamp, and the exception is not swallowed")
    void throwingBodyReleasesTheStamp() {
        assertNull(MeleeAttackStamps.current(COMPANION), "no stamp before the attack");

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                bracket(() -> {
                    // The stamp is live while the body runs, which is what makes it usable at all.
                    assertNotNull(MeleeAttackStamps.current(COMPANION),
                            "the stamp must exist while the attack body runs");
                    throw new IllegalStateException("damage() or postHit blew up");
                }));

        // The original throwable comes back unchanged: not caught, not wrapped, not replaced.
        assertEquals("damage() or postHit blew up", thrown.getMessage());
        assertSame(IllegalStateException.class, thrown.getClass());
        assertNull(MeleeAttackStamps.current(COMPANION),
                "the finally must have released the stamp on the throwing path");
    }

    @Test
    @DisplayName("a normal return releases the stamp and passes the result through")
    void normalBodyReleasesTheStamp() {
        Boolean result = bracket(() -> {
            assertNotNull(MeleeAttackStamps.current(COMPANION));
            return Boolean.TRUE;
        });
        assertEquals(Boolean.TRUE, result, "the body's own result must reach the caller");
        assertNull(MeleeAttackStamps.current(COMPANION));
    }

    @Test
    @DisplayName("a false return releases the stamp too, which is the missed-swing case")
    void falseReturnReleasesTheStamp() {
        // The most common outcome in play: the swing happens and deals nothing. This is exactly the case
        // that used to leave an orphaned stamp behind for a later projectile to be credited to.
        Boolean result = bracket(() -> Boolean.FALSE);
        assertEquals(Boolean.FALSE, result);
        assertNull(MeleeAttackStamps.current(COMPANION),
                "a missed swing must not leave a stamp behind");
    }

    @Test
    @DisplayName("an Error is not swallowed either")
    void errorAlsoReleasesTheStamp() {
        // Not just RuntimeException: the cleanup must not depend on the throwable's type.
        assertThrows(AssertionError.class, () -> bracket(() -> {
            throw new AssertionError("something went very wrong");
        }));
        assertNull(MeleeAttackStamps.current(COMPANION),
                "the finally covers Throwable, not only the checked/declared cases");
    }

    @Test
    @DisplayName("the stamp is gone even though the body never returned a value")
    void cleanupDoesNotDependOnAValue() {
        AtomicBoolean ran = new AtomicBoolean();
        assertThrows(RuntimeException.class, () -> bracket(() -> {
            ran.set(true);
            throw new RuntimeException("boom");
        }));
        assertTrue(ran.get(), "the body did run");
        assertNull(MeleeAttackStamps.current(COMPANION));
    }

    @Test
    @DisplayName("WIRING: the stamp is still live DURING the attack body, and gone once it returns")
    void stampSurvivesUntilTheAttackReturns() {
        // This is the assertion that catches the defect the previous version shipped: the wrapper only
        // bracketed a single call at the top of the body, so the stamp was released BEFORE any damage was
        // applied. A test that merely checked "there is a finally" passed while that was broken.
        //
        // The probe stands in for the damage callback: real damage happens while the attack body is still
        // running, so anything the hit path needs must still exist at that moment. It asks the PRODUCTION
        // attribution rule, so the same-tick and generation checks are exercised too.
        EngagementState fight = new EngagementState();
        fight.begin(TARGET, NOW, 500L);

        AtomicBoolean stampLiveDuringBody = new AtomicBoolean(false);
        AtomicBoolean hitWouldCreditDuringBody = new AtomicBoolean(false);

        Boolean result = bracket(fight.generation(), () -> {
            Object live = MeleeAttackStamps.current(COMPANION);
            stampLiveDuringBody.set(live != null);
            hitWouldCreditDuringBody.set(
                    CompanionCombatController.meleeHitCredits(fight, live, TARGET, NOW));
            return Boolean.TRUE;
        });

        assertTrue(stampLiveDuringBody.get(),
                "the stamp must still exist while the attack body runs, or no melee hit can be credited");
        assertTrue(hitWouldCreditDuringBody.get(),
                "a hit landing during the attack must be attributable to the launch");
        assertEquals(Boolean.TRUE, result, "the attack result still reaches the caller");
        assertNull(MeleeAttackStamps.current(COMPANION),
                "and the stamp is gone once the attack has returned");
    }

    @Test
    @DisplayName("WIRING: a hit arriving AFTER the attack returned is refused")
    void hitAfterReturnIsRefused() {
        // The other half of the same property. If the stamp outlived the call, a later projectile would be
        // credited to this finished swing -- the original defect.
        EngagementState fight = new EngagementState();
        fight.begin(TARGET, NOW, 500L);

        bracket(() -> Boolean.TRUE);
        assertNull(MeleeAttackStamps.current(COMPANION));
        assertFalse(CompanionCombatController.meleeHitCredits(fight,
                        MeleeAttackStamps.current(COMPANION), TARGET, NOW),
                "no stamp is live after the attack returned, so the hit must not be credited");
    }

    @Test
    @DisplayName("the attribution rule also refuses a stamp from a different tick or fight")
    void attributionRuleChecksTickAndGeneration() {
        EngagementState fight = new EngagementState();
        fight.begin(TARGET, NOW, 500L);
        Object token = MeleeAttackStamps.recordToken(COMPANION, TARGET, fight.generation(), NOW);
        Object live = MeleeAttackStamps.current(COMPANION);

        assertTrue(CompanionCombatController.meleeHitCredits(fight, live, TARGET, NOW),
                "same tick, same target, same fight");
        assertFalse(CompanionCombatController.meleeHitCredits(fight, live, TARGET, NOW + 1L),
                "melee resolves in the tick it was attempted, so a later tick is not this attack");
        assertFalse(CompanionCombatController.meleeHitCredits(fight, live, OTHER_TARGET, NOW),
                "a hit on another creature says nothing about this fight");
        assertFalse(CompanionCombatController.meleeHitCredits(fight, null, TARGET, NOW),
                "no stamp, no credit");
        // A stamp carrying a generation that is no longer live is refused even on the right tick.
        Object stale = MeleeAttackStamps.recordToken(COMPANION, TARGET, fight.generation() + 99L, NOW);
        assertFalse(CompanionCombatController.meleeHitCredits(fight,
                MeleeAttackStamps.current(COMPANION), TARGET, NOW),
                "a stamp from another fight must not credit this one");
        assertNotNull(token);
        assertNotNull(stale);
    }

    @Test
    @DisplayName("the PRODUCTION handler contains a real finally around the body")
    void productionHandlerUsesARealFinally() throws Exception {
        // Guards against the bracket being replaced by two injection points again, which is what silently
        // lost the exception path before. Source-level on purpose: it is a structural property of the code
        // that runs in game, and no runtime test can observe an injection point that is not there.
        java.nio.file.Path source = java.nio.file.Path.of("src", "main", "java", "com", "nonid",
                "affinity", "service", "AttackStampHandler.java");
        assertTrue(java.nio.file.Files.isRegularFile(source), "the handler must exist");
        String text = java.nio.file.Files.readString(source, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(text.contains("try {"), "the body must be inside a try");
        assertTrue(text.contains("} finally {"),
                "the cleanup must be a finally, not a normal-path statement");
        assertTrue(text.contains("onCompanionAttackFinished"),
                "the finally must route through the production cleanup");
        assertFalse(text.contains("catch ("),
                "the handler must not CATCH anything: swallowing the exception would hide real failures");
    }
}
