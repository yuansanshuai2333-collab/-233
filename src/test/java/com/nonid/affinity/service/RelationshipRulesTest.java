package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nonid.affinity.service.RelationshipRules.BondOutcome;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ownership rules that decide whether a creature may become a player's companion.
 *
 * <p>These are the rules the task calls out as most likely to go wrong: never take another player's pet,
 * never transfer a bond, and treat an unreadable owner reference as "unknown" rather than "free".
 *
 * <p>They live here rather than in a wiring test because {@code MobEntity} does not link in a plain JUnit
 * process, so a test phrased against the entity could not run at all -- and these specific rules would
 * have gone unverified while appearing covered.
 */
class RelationshipRulesTest {

    private static final UUID ALICE = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000000b");

    @Test
    @DisplayName("a creature with no owner is bound to the player who tamed it")
    void unownedCreatureIsBound() {
        assertEquals(BondOutcome.BOUND, RelationshipRules.evaluate(null, null, ALICE, true));
        assertTrue(RelationshipRules.isBonded(
                RelationshipRules.evaluate(null, null, ALICE, true)));
    }

    @Test
    @DisplayName("taming your own already-tamed pet again is a no-op, not a second bond")
    void sameOwnerIsIdempotent() {
        // Both records agreeing on the SAME player must be BOUND, so a repeated call is harmless. If this
        // returned anything else, every re-mount or tracker update would look like a refusal.
        assertEquals(BondOutcome.BOUND,
                RelationshipRules.evaluate(ALICE, ALICE, ALICE, true));
        assertEquals(BondOutcome.BOUND,
                RelationshipRules.evaluate(ALICE, null, ALICE, true));
        assertEquals(BondOutcome.BOUND,
                RelationshipRules.evaluate(null, ALICE, ALICE, true));
    }

    @Test
    @DisplayName("another player's companion is never taken over")
    void otherPlayersCompanionIsRefused() {
        assertEquals(BondOutcome.OTHER_PLAYER_COMPANION,
                RelationshipRules.evaluate(ALICE, null, BOB, true),
                "an existing companion bond must not transfer");
        assertEquals(BondOutcome.OTHER_PLAYER_COMPANION,
                RelationshipRules.evaluate(ALICE, ALICE, BOB, true));
    }

    @Test
    @DisplayName("another player's vanilla pet is never taken over")
    void otherPlayersVanillaPetIsRefused() {
        assertEquals(BondOutcome.OTHER_PLAYER_VANILLA_OWNER,
                RelationshipRules.evaluate(null, ALICE, BOB, true));
    }

    @Test
    @DisplayName("contradictory ownership records are reported as a conflict, not resolved by picking a side")
    void contradictoryRecordsAreAConflict() {
        // This state cannot be produced by normal play; it comes from an older version or an external
        // change. Choosing a winner silently would either steal a pet or hand control to the wrong player.
        assertEquals(BondOutcome.OWNERSHIP_CONFLICT,
                RelationshipRules.evaluate(ALICE, BOB, ALICE, true));
        assertEquals(BondOutcome.OWNERSHIP_CONFLICT,
                RelationshipRules.evaluate(ALICE, BOB, BOB, true));
        assertFalse(RelationshipRules.isBonded(
                RelationshipRules.evaluate(ALICE, BOB, ALICE, true)));
    }

    @Test
    @DisplayName("a creature that cannot hold affinity data is reported as unsupported")
    void unsupportedCreatureIsRefused() {
        assertEquals(BondOutcome.UNSUPPORTED_CREATURE,
                RelationshipRules.evaluate(null, null, ALICE, false));
    }

    @Test
    @DisplayName("a new friend record starts at the threshold and an existing one is never lowered")
    void affinityIsRaisedButNeverLowered() {
        assertEquals(0, RelationshipRules.initialFriendAffinity(0));
        assertEquals(100, RelationshipRules.initialFriendAffinity(100));
        assertEquals(0, RelationshipRules.initialFriendAffinity(-5),
                "a negative threshold must not produce a negative starting affinity");

        // Lower than the threshold: raised.
        assertEquals(100, RelationshipRules.affinityRaisedTo(20, 100));
        // Already above it: preserved, never pulled back down.
        assertEquals(250, RelationshipRules.affinityRaisedTo(250, 100));
        // Exactly at it: unchanged, and the caller can write the value back unconditionally.
        assertEquals(100, RelationshipRules.affinityRaisedTo(100, 100));
    }

    @Test
    @DisplayName("a sitting pet starts in WAIT and anything else in FOLLOW")
    void sittingPetStartsInWait() {
        assertEquals(com.nonid.affinity.data.CompanionMode.WAIT, RelationshipRules.initialMode(true),
                "a pet the player told to stay must not silently start following");
        assertEquals(com.nonid.affinity.data.CompanionMode.FOLLOW, RelationshipRules.initialMode(false));
    }

    @Test
    @DisplayName("a chosen mode always wins and an undecided creature takes the preferred one")
    void chosenModeBeatsThePreference() {
        var wait = com.nonid.affinity.data.CompanionMode.WAIT;
        var follow = com.nonid.affinity.data.CompanionMode.FOLLOW;
        var attack = com.nonid.affinity.data.CompanionMode.ATTACK;

        assertEquals(attack, RelationshipRules.resolveInitialMode(attack, wait),
                "a mode the player picked must never be replaced by a state-derived default");
        assertEquals(follow, RelationshipRules.resolveInitialMode(follow, wait),
                "even an explicitly chosen FOLLOW outranks the sitting preference");
        assertEquals(wait, RelationshipRules.resolveInitialMode(null, wait),
                "an undecided creature takes the preferred mode");
        assertEquals(follow, RelationshipRules.resolveInitialMode(null, null),
                "with no preference the default is FOLLOW");
    }

    @Test
    @DisplayName("the load backfill actually asks for that mode instead of hard-coding FOLLOW")
    void backfillConsultsTheInitialModeRule() throws Exception {
        // The rule above is worthless if the one caller it exists for ignores it. This is not hypothetical:
        // the first version of backfillOnLoad guarded the WAIT mapping with "data != null", and a pet being
        // loaded had no data yet by definition -- so the branch never ran for the old saves it was written
        // for, and every sitting pet came back as FOLLOW. A dedicated production run showed
        // companion_mode: "FOLLOW" on a sitting summoned wolf. Reading the compiled class keeps that
        // observable here, without linking Minecraft types.
        assertTrue(CodeScanner.referencesMethod(classBytes("com.nonid.affinity.service.RelationshipSync"),
                        "com/nonid/affinity/service/RelationshipRules", "initialMode",
                        "(Z)Lcom/nonid/affinity/data/CompanionMode;"),
                "backfillOnLoad must pass RelationshipRules.initialMode(...) into sync, or a sitting pet"
                        + " is backfilled as a follower");
        assertTrue(CodeScanner.referencesMethod(classBytes("com.nonid.affinity.service.RelationshipSync"),
                        "com/nonid/affinity/service/RelationshipRules", "resolveInitialMode",
                        "(Lcom/nonid/affinity/data/CompanionMode;Lcom/nonid/affinity/data/CompanionMode;)"
                                + "Lcom/nonid/affinity/data/CompanionMode;"),
                "sync must resolve the mode through the rule; asking the stored getter whether it is null"
                        + " can never be true, which is how the preference was silently ignored");
    }

    /** Read a compiled class from the test classpath without linking it. */
    private static byte[] classBytes(String className) throws Exception {
        String resource = className.replace('.', '/') + ".class";
        try (var stream = RelationshipRulesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertTrue(stream != null, "class not on the test classpath: " + className);
            return stream.readAllBytes();
        }
    }
}
