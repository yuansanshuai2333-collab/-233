package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nonid.affinity.service.ProjectileStamps.OwnerKind;
import com.nonid.affinity.service.ProjectileStamps.State;
import com.nonid.affinity.service.policy.EngagementState;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The projectile attribution state machine, driven the way the hooks actually drive it.
 *
 * <p>Every test calls {@link ProjectileStamps#observeOwnerAssignment} or
 * {@link ProjectileStamps#markLoadedFromStorage}, which are the single entry points the hooks use. An
 * "instance" here is a plain marker object standing in for the entity: the store keys on entity IDENTITY,
 * and identity is the whole point -- a dimension change creates a new entity carrying the old entity's
 * uuid, so a store keyed on the uuid cannot tell them apart.
 *
 * <p><b>What this file does and does not establish.</b> These are RULE tests: they drive the store and its
 * production entry points directly. They do not execute Minecraft's dimension-change or chunk-unload code,
 * and they are therefore NOT equivalent to the real call chain. What the real chain does is established by
 * disassembly and is recorded in the status report; the end-to-end behaviour is an in-game item.
 *
 * <p>The defect these cover: an earlier version DELETED a projectile's record when it changed hands or when
 * its hit was consumed. A deleted record is indistinguishable from a projectile that was never seen, so the
 * next {@code setOwner} -- and vanilla calls it more than once per projectile -- treated an old arrow as a
 * fresh one and gave it the CURRENT fight's generation.
 */
class ProjectileOwnerLifecycleTest {

    private static final UUID COMPANION_A = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID COMPANION_B = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000000b");
    private static final UUID TARGET = UUID.fromString("cccccccc-0000-0000-0000-00000000000c");
    private static final UUID OTHER_TARGET = UUID.fromString("dddddddd-0000-0000-0000-00000000000d");
    private static final UUID ARROW = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID FRESH_ARROW = UUID.fromString("22222222-0000-0000-0000-000000000002");

    private static final long GEN_ONE = 1L;
    private static final long GEN_TWO = 2L;
    private static final long NOW = 100L;

    /** One projectile entity instance. Identity is what the store keys on. */
    private static final class Projectile {
        private final UUID uuid;

        Projectile(UUID uuid) {
            this.uuid = uuid;
        }
    }

    private static Projectile projectile(UUID uuid) {
        return new Projectile(uuid);
    }

    @BeforeEach
    @AfterEach
    void reset() {
        ProjectileStamps.clearAll();
        CompanionCombatController.clear();
    }

    /** What one launch hook invocation does for a managed companion. */
    private static boolean managedHook(Projectile instance, UUID companion, UUID target,
                                       long generation, long tick) {
        return ProjectileStamps.observeOwnerAssignment(instance, instance.uuid,
                OwnerKind.MANAGED_COMPANION, companion, target, generation, tick);
    }

    /** What one hook invocation does for a player, another mod's mob, or an unmanaged creature. */
    private static boolean otherHook(Projectile instance) {
        return ProjectileStamps.observeOwnerAssignment(instance, instance.uuid,
                OwnerKind.OTHER_ENTITY, null, null, 0L, 0L);
    }

    /** The storage-read seam, as the read hook calls it. */
    private static void loadedFromStorage(Projectile instance) {
        CompanionCombatController.onProjectileLoadedFromStorage(instance, instance.uuid);
    }

    /** The removal seam, as the setRemoved hook calls it. */
    private static void instanceRemoved(Projectile instance) {
        CompanionCombatController.onProjectileInstanceRemoved(instance);
    }

    // ------------------------------------------------------------------
    // Identity: two entities sharing one uuid
    // ------------------------------------------------------------------

    @Test
    @DisplayName("IDENTITY: a shared uuid does not let an old entity's removal erase the new entity's state")
    void removalIsScopedToItsOwnInstance() {
        Projectile oldEntity = projectile(ARROW);
        Projectile newEntity = projectile(ARROW);   // a dimension change: same uuid, new instance

        assertTrue(managedHook(oldEntity, COMPANION_A, TARGET, GEN_ONE, NOW));

        // The new entity reads its data and becomes terminal.
        loadedFromStorage(newEntity);
        assertFalse(ProjectileStamps.stateOfInstance(newEntity).valid());

        // The OLD entity is then removed. Releasing by uuid would wipe the new entity's state.
        instanceRemoved(oldEntity);
        assertNull(ProjectileStamps.stateOfInstance(oldEntity), "the old entity's entry is gone");
        State afterRemoval = ProjectileStamps.stateOfInstance(newEntity);
        assertNotNull(afterRemoval, "the new entity's state must survive the old entity's removal");
        assertFalse(afterRemoval.valid(), "and it must still be terminal");

        // The consequence: the transferred projectile cannot be re-attributed.
        assertFalse(managedHook(newEntity, COMPANION_A, TARGET, GEN_ONE, NOW + 5L));
        assertNull(ProjectileStamps.attributed(ARROW));
    }

    // ------------------------------------------------------------------
    // Lifetime: one removal releases, with no residue
    // ------------------------------------------------------------------

    @Test
    @DisplayName("LIFETIME: read then destroyed ONCE releases everything, with no residue")
    void singleRemovalReleasesWithoutResidue() {
        // The regression this pins. A previous version skipped the first destroying removal for a
        // storage-protected id, on a guess about the cross-dimension call order, so a projectile that was
        // read and then simply destroyed kept its entry until a SECOND removal that never came.
        int baseline = ProjectileStamps.heldCount();
        assertEquals(0, baseline, "the store starts empty");

        Projectile arrow = projectile(ARROW);
        loadedFromStorage(arrow);
        assertNotNull(ProjectileStamps.stateOfInstance(arrow), "the read establishes a terminal state");
        assertEquals(1, ProjectileStamps.heldCount());
        assertEquals(1, ProjectileStamps.uuidIndexCount());

        // No re-assignment. Then the entity is destroyed, exactly once.
        instanceRemoved(arrow);

        assertNull(ProjectileStamps.stateOfInstance(arrow), "one removal must release the state");
        assertNull(ProjectileStamps.stateOf(ARROW), "and the uuid lookup must be gone too");
        assertEquals(0, ProjectileStamps.heldCount(), "no entry remains");
        assertEquals(0, ProjectileStamps.uuidIndexCount(), "no uuid index remains either");
        assertEquals(baseline, ProjectileStamps.heldCount(), "the store is back to its baseline");
    }

    @Test
    @DisplayName("LIFETIME: repeated read/destroy rounds always return to the baseline")
    void repeatedRoundsDoNotAccumulate() {
        int baseline = ProjectileStamps.heldCount();
        for (int round = 0; round < 5; round++) {
            Projectile arrow = projectile(ARROW);
            loadedFromStorage(arrow);
            instanceRemoved(arrow);
            assertEquals(baseline, ProjectileStamps.heldCount(),
                    "round " + round + ": the store must return to its baseline");
            assertEquals(baseline, ProjectileStamps.uuidIndexCount(),
                    "round " + round + ": no uuid index may accumulate");
        }
    }

    @Test
    @DisplayName("LIFETIME: an attributed projectile releases on its first removal too")
    void attributedProjectileReleasesOnFirstRemoval() {
        Projectile arrow = projectile(ARROW);
        assertTrue(managedHook(arrow, COMPANION_A, TARGET, GEN_ONE, NOW));
        instanceRemoved(arrow);
        assertNull(ProjectileStamps.stateOfInstance(arrow));
        assertEquals(0, ProjectileStamps.heldCount());
        assertEquals(0, ProjectileStamps.uuidIndexCount());
    }

    // ------------------------------------------------------------------
    // Attribution rules
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1. A fires, hands over to B, and both hooks fire twice: still invalidated")
    void handOverStaysInvalidatedAcrossRepeatedHooks() {
        Projectile arrow = projectile(ARROW);
        assertTrue(managedHook(arrow, COMPANION_A, TARGET, GEN_ONE, NOW), "A attributes the arrow");
        assertNotNull(ProjectileStamps.attributed(ARROW));

        // The hand-over. Vanilla assigns owners more than once, and the subclass setOwner also delegates to
        // the superclass one, so a single assignment can already reach two hooks.
        assertFalse(managedHook(arrow, COMPANION_B, TARGET, GEN_ONE, NOW), "B's first hook refuses");
        assertFalse(managedHook(arrow, COMPANION_B, TARGET, GEN_ONE, NOW), "B's second hook refuses");

        State state = ProjectileStamps.stateOfInstance(arrow);
        assertNotNull(state, "the terminal state must be kept, not deleted");
        assertFalse(state.valid(), "and it must be terminal");
        assertNull(ProjectileStamps.attributed(ARROW));
        assertFalse(CompanionCombatController.projectileStampCredits(state, COMPANION_B, TARGET));
    }

    @Test
    @DisplayName("2. engagement ends, is cleaned, the fight restarts: the old arrow is NOT re-attributed")
    void oldArrowIsNotRevivedByANewEngagement() {
        Projectile arrow = projectile(ARROW);
        EngagementState fight = new EngagementState();
        fight.begin(TARGET, NOW, 500L);
        long firstGeneration = fight.generation();
        assertTrue(managedHook(arrow, COMPANION_A, TARGET, firstGeneration, NOW));
        assertEquals(firstGeneration, ProjectileStamps.attributed(ARROW).generation());

        fight.end();
        assertEquals(1, ProjectileStamps.markEngagementEnded(COMPANION_A),
                "the live arrow's record is affected by the cleanup");

        fight.begin(TARGET, NOW + 40L, 900L);
        long liveGeneration = fight.generation();
        assertTrue(liveGeneration != firstGeneration, "the new fight really is a new generation");
        managedHook(arrow, COMPANION_A, TARGET, liveGeneration, NOW + 40L);

        State stored = ProjectileStamps.attributed(ARROW);
        assertNull(stored, "an old arrow must not be attributable after its fight ended");
        assertNotNull(ProjectileStamps.stateOf(ARROW),
                "and it must not be merely DELETED: a deleted record is indistinguishable from a "
                        + "projectile that was never seen");

        long before = fight.lastProgressTick();
        boolean credited = stored != null
                && fight.recordHitAtGeneration(TARGET, stored.generation(), NOW + 45L);
        assertFalse(credited, "the old arrow's hit must not be credited");
        assertEquals(before, fight.lastProgressTick(), "and progress must not be refreshed");
    }

    @Test
    @DisplayName("2b. without cleanup the arrow keeps its original generation and is refused")
    void oldArrowKeepsItsGenerationWithoutCleanup() {
        Projectile arrow = projectile(ARROW);
        assertTrue(managedHook(arrow, COMPANION_A, TARGET, GEN_ONE, NOW));

        EngagementState newFight = new EngagementState();
        newFight.begin(TARGET, NOW + 40L, 900L);
        newFight.begin(TARGET, NOW + 40L, 900L);
        long liveGeneration = newFight.generation();

        managedHook(arrow, COMPANION_A, TARGET, liveGeneration, NOW + 40L);
        assertEquals(GEN_ONE, ProjectileStamps.attributed(ARROW).generation(),
                "a later assignment must not overwrite the launch generation");
        assertFalse(newFight.recordHitAtGeneration(TARGET, GEN_ONE, NOW + 45L));
    }

    @Test
    @DisplayName("3. the arrow's hit was already consumed: re-owning cannot authorise another")
    void consumedHitCannotBeAuthorisedAgain() {
        Projectile arrow = projectile(ARROW);
        assertTrue(managedHook(arrow, COMPANION_A, TARGET, GEN_ONE, NOW));
        ProjectileStamps.invalidate(ARROW);   // what the hit path does once it has been credited

        assertFalse(managedHook(arrow, COMPANION_A, TARGET, GEN_ONE, NOW + 1L));
        assertFalse(managedHook(arrow, COMPANION_A, TARGET, GEN_ONE, NOW + 2L));
        assertNull(ProjectileStamps.attributed(ARROW));
    }

    @Test
    @DisplayName("4. a player, a null owner or an unmanaged creature permanently blocks the arrow")
    void nonCompanionOwnersBlockPermanently() {
        Projectile arrow = projectile(ARROW);
        assertTrue(managedHook(arrow, COMPANION_A, TARGET, GEN_ONE, NOW));

        assertFalse(otherHook(arrow));
        assertFalse(ProjectileStamps.stateOfInstance(arrow).valid());
        assertFalse(otherHook(arrow), "a second non-companion assignment changes nothing");
        assertFalse(managedHook(arrow, COMPANION_A, TARGET, GEN_ONE, NOW + 5L),
                "a return to a companion must not revive the projectile");
        assertNull(ProjectileStamps.attributed(ARROW));
    }

    @Test
    @DisplayName("4b. a fresh projectile with a null owner is blocked, not silently ignored")
    void freshProjectileWithNoOwnerIsBlocked() {
        Projectile arrow = projectile(ARROW);
        assertFalse(otherHook(arrow));
        assertNotNull(ProjectileStamps.stateOfInstance(arrow), "the terminal state must exist");
        assertFalse(managedHook(arrow, COMPANION_A, TARGET, GEN_ONE, NOW));
        assertNull(ProjectileStamps.attributed(ARROW));
    }

    @Test
    @DisplayName("5. a NEWLY created projectile is still attributed normally")
    void freshProjectileStillWorks() {
        Projectile used = projectile(ARROW);
        assertTrue(managedHook(used, COMPANION_A, TARGET, GEN_ONE, NOW));
        assertFalse(otherHook(used));

        Projectile fresh = projectile(FRESH_ARROW);
        assertTrue(managedHook(fresh, COMPANION_A, TARGET, GEN_ONE, NOW + 10L),
                "a fresh projectile must still be attributed");
        State state = ProjectileStamps.attributed(FRESH_ARROW);
        assertNotNull(state);
        assertEquals(COMPANION_A, state.ownerId());
        assertEquals(TARGET, state.targetId());
        assertEquals(GEN_ONE, state.generation());
        assertTrue(CompanionCombatController.projectileStampCredits(state, COMPANION_A, TARGET));

        EngagementState fight = new EngagementState();
        fight.begin(TARGET, NOW, 500L);
        assertTrue(fight.recordHitAtGeneration(TARGET, state.generation(), NOW + 11L),
                "a normal ranged hit must still count as progress");
    }

    // ------------------------------------------------------------------
    // Storage: reload and restart
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RELOAD: an arrow read back from storage can never acquire the new fight's generation")
    void reloadedArrowCanNeverBeAttributed() {
        Projectile firstLife = projectile(ARROW);
        EngagementState fight = new EngagementState();
        fight.begin(TARGET, NOW, 500L);
        assertTrue(managedHook(firstLife, COMPANION_A, TARGET, fight.generation(), NOW),
                "the arrow is attributed while it is in flight");

        // Storage keeps only the entity's UUID, so a reload leaves nothing behind for the instance.
        CompanionCombatController.clear();
        assertNull(ProjectileStamps.stateOfInstance(firstLife), "nothing survives in memory");

        // The projectile comes back as a NEW instance: the production seam for that is the read hook.
        Projectile reloaded = projectile(ARROW);
        loadedFromStorage(reloaded);
        assertNotNull(ProjectileStamps.stateOfInstance(reloaded),
                "the reloaded projectile must hold a TERMINAL record, not be left looking unseen");
        assertFalse(ProjectileStamps.stateOfInstance(reloaded).valid());
        assertNull(ProjectileStamps.attributed(ARROW));

        fight.begin(TARGET, NOW + 40L, 900L);
        long liveGeneration = fight.generation();
        assertFalse(managedHook(reloaded, COMPANION_A, TARGET, liveGeneration, NOW + 40L),
                "a reloaded projectile must NOT be re-attributed as a new arrow");
        assertNull(ProjectileStamps.attributed(ARROW));

        long before = fight.lastProgressTick();
        State stored = ProjectileStamps.attributed(ARROW);
        boolean credited = stored != null
                && fight.recordHitAtGeneration(TARGET, stored.generation(), NOW + 45L);
        assertFalse(credited, "the reloaded arrow's hit must not be credited");
        assertEquals(before, fight.lastProgressTick(), "and progress must not be refreshed");

        Projectile fresh = projectile(FRESH_ARROW);
        assertTrue(managedHook(fresh, COMPANION_A, TARGET, liveGeneration, NOW + 41L),
                "a projectile created after the reload is attributed normally");
        State freshState = ProjectileStamps.attributed(FRESH_ARROW);
        assertNotNull(freshState);
        assertTrue(fight.recordHitAtGeneration(TARGET, freshState.generation(), NOW + 42L));
        assertEquals(NOW + 42L, fight.lastProgressTick());
    }

    @Test
    @DisplayName("RELOAD: the terminal state survives repeated assignment and repeated storage reads")
    void reloadedArrowStaysTerminal() {
        Projectile reloaded = projectile(ARROW);
        // Reading custom data can run more than once for one entity -- the subclass implementation also
        // calls the superclass one -- and owners are assigned repeatedly.
        loadedFromStorage(reloaded);
        loadedFromStorage(reloaded);
        assertFalse(managedHook(reloaded, COMPANION_A, TARGET, GEN_ONE, NOW));
        assertFalse(managedHook(reloaded, COMPANION_A, TARGET, GEN_TWO, NOW + 1L));
        assertFalse(otherHook(reloaded));
        assertNull(ProjectileStamps.attributed(ARROW));
    }

    @Test
    @DisplayName("RELOAD: a restart that resets generations cannot make a pre-restart arrow look current")
    void restartIsCoveredByTheReadSeam() {
        Projectile firstLife = projectile(ARROW);
        assertTrue(managedHook(firstLife, COMPANION_A, TARGET, 11L, NOW));
        CompanionCombatController.clear();

        Projectile reloaded = projectile(ARROW);
        loadedFromStorage(reloaded);
        assertFalse(managedHook(reloaded, COMPANION_A, TARGET, 1L, NOW + 5L),
                "a generation restart must not let a pre-restart arrow look current");
        assertNull(ProjectileStamps.attributed(ARROW));
    }

    // ------------------------------------------------------------------
    // Scoping
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the engagement cleanup INVALIDATES live records instead of deleting them")
    void cleanupInvalidatesRatherThanDeletes() {
        Projectile arrow = projectile(ARROW);
        assertTrue(managedHook(arrow, COMPANION_A, TARGET, GEN_ONE, NOW));
        assertEquals(1, ProjectileStamps.markEngagementEnded(COMPANION_A));

        State after = ProjectileStamps.stateOfInstance(arrow);
        assertNotNull(after, "the record must still EXIST after the cleanup");
        assertFalse(after.valid(), "and it must be terminal");
        assertNull(ProjectileStamps.attributed(ARROW));

        assertFalse(managedHook(arrow, COMPANION_A, TARGET, GEN_TWO, NOW + 1L),
                "a terminated projectile must never be re-attributed");
        assertEquals(0, ProjectileStamps.markEngagementEnded(COMPANION_A),
                "nothing live is left to terminate");
        assertNotNull(ProjectileStamps.stateOfInstance(arrow),
                "the terminal record survives a second cleanup");
    }

    @Test
    @DisplayName("LIFETIME: the cleanup is IDEMPOTENT, because final does not mean it runs only once")
    void cleanupIsIdempotent() {
        // setRemoved is final, which only means subclasses cannot override it -- not that it is invoked
        // exactly once. The cleanup must therefore be safe to call repeatedly.
        Projectile arrow = projectile(ARROW);
        loadedFromStorage(arrow);
        assertEquals(1, ProjectileStamps.heldCount());

        instanceRemoved(arrow);
        assertNull(ProjectileStamps.stateOfInstance(arrow));
        assertEquals(0, ProjectileStamps.heldCount());
        assertEquals(0, ProjectileStamps.uuidIndexCount());

        // Repeating the cleanup changes nothing and throws nothing.
        for (int i = 0; i < 3; i++) {
            instanceRemoved(arrow);
            assertEquals(0, ProjectileStamps.heldCount(), "repeat " + i + " must not change the store");
            assertEquals(0, ProjectileStamps.uuidIndexCount());
            assertNull(ProjectileStamps.stateOfInstance(arrow));
            assertNull(ProjectileStamps.attributed(ARROW));
        }

        // And a null instance is tolerated rather than throwing.
        CompanionCombatController.onProjectileInstanceRemoved(null);
        assertEquals(0, ProjectileStamps.heldCount());
    }

    @Test
    @DisplayName("LIFETIME: repeated cleanup for one instance does not disturb another instance's entry")
    void repeatedCleanupIsScopedToItsInstance() {
        Projectile a = projectile(ARROW);
        Projectile b = projectile(FRESH_ARROW);
        assertTrue(managedHook(a, COMPANION_A, TARGET, GEN_ONE, NOW));
        assertTrue(managedHook(b, COMPANION_B, TARGET, GEN_ONE, NOW));

        instanceRemoved(a);
        instanceRemoved(a);
        instanceRemoved(a);

        assertNull(ProjectileStamps.attributed(ARROW));
        assertNotNull(ProjectileStamps.attributed(FRESH_ARROW),
                "repeating one instance's cleanup must not touch another's");
        assertEquals(1, ProjectileStamps.heldCount());
    }

    @Test
    @DisplayName("cleanup does not touch another companion's live projectile")
    void cleanupIsScopedToItsCompanion() {
        Projectile a = projectile(ARROW);
        Projectile b = projectile(FRESH_ARROW);
        assertTrue(managedHook(a, COMPANION_A, TARGET, GEN_ONE, NOW));
        assertTrue(managedHook(b, COMPANION_B, TARGET, GEN_ONE, NOW));

        assertEquals(1, ProjectileStamps.markEngagementEnded(COMPANION_A));
        assertNull(ProjectileStamps.attributed(ARROW));
        assertNotNull(ProjectileStamps.attributed(FRESH_ARROW));
    }

    @Test
    @DisplayName("two projectiles in flight keep independent states")
    void projectilesAreIndependent() {
        Projectile a = projectile(ARROW);
        Projectile b = projectile(FRESH_ARROW);
        assertTrue(managedHook(a, COMPANION_A, TARGET, GEN_ONE, NOW));
        assertTrue(managedHook(b, COMPANION_B, TARGET, GEN_TWO, NOW + 1L));

        assertEquals(GEN_ONE, ProjectileStamps.attributed(ARROW).generation());
        assertEquals(GEN_TWO, ProjectileStamps.attributed(FRESH_ARROW).generation());
        assertEquals(COMPANION_A, ProjectileStamps.attributed(ARROW).ownerId());

        ProjectileStamps.invalidate(ARROW);
        assertNull(ProjectileStamps.attributed(ARROW));
        assertNotNull(ProjectileStamps.attributed(FRESH_ARROW));
        assertTrue(CompanionCombatController.projectileStampCredits(
                ProjectileStamps.attributed(FRESH_ARROW), COMPANION_B, TARGET));
    }

    @Test
    @DisplayName("a managed companion with no fight gets no attribution")
    void noFightMeansNoAttribution() {
        Projectile arrow = projectile(ARROW);
        assertFalse(managedHook(arrow, COMPANION_A, null, GEN_ONE, NOW));
        assertNotNull(ProjectileStamps.stateOfInstance(arrow));
        assertFalse(ProjectileStamps.stateOfInstance(arrow).valid());
        assertNull(ProjectileStamps.attributed(ARROW));
    }

    @Test
    @DisplayName("a stray shot that hit another creature is not credited")
    void strayHitIsNotCredited() {
        Projectile arrow = projectile(ARROW);
        assertTrue(managedHook(arrow, COMPANION_A, TARGET, GEN_ONE, NOW));
        State state = ProjectileStamps.attributed(ARROW);
        assertFalse(CompanionCombatController.projectileStampCredits(state, COMPANION_A, OTHER_TARGET),
                "the projectile was aimed at the target and hit something else");
    }
}
