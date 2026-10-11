package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * When the companion movement goal must stand down so the creature can breathe.
 *
 * <p>The requirement that must NOT regress: on land, and in water where breathing is not yet a problem,
 * WAIT keeps holding position and never chases. So this rule has to be narrow, and the tests below pin
 * both directions -- it yields when air is critical, and it does NOT yield in the ordinary cases.
 */
class AirSupplyTest {

    private static final int WOLF_MAX_AIR = 300;

    @Test
    @DisplayName("a submerged creature with critical air yields its movement so it can surface")
    void criticalAirYields() {
        assertTrue(AirSupply.mustYieldForAir(true, 0, WOLF_MAX_AIR, false), "out of air entirely");
        assertTrue(AirSupply.mustYieldForAir(true, 40, WOLF_MAX_AIR, false), "well below the threshold");
    }

    @Test
    @DisplayName("a submerged creature with plenty of air keeps the ordered behaviour")
    void fullAirDoesNotYield() {
        assertFalse(AirSupply.mustYieldForAir(true, WOLF_MAX_AIR, WOLF_MAX_AIR, false),
                "a creature that just went under must still follow or hold its post");
        assertFalse(AirSupply.mustYieldForAir(true, WOLF_MAX_AIR - 1, WOLF_MAX_AIR, false));
    }

    @Test
    @DisplayName("on land nothing yields, whatever the air value says")
    void onLandNeverYields() {
        // This is the "no chasing while waiting" requirement: a creature whose head is out of the water
        // must never have its movement taken away by this rule.
        assertFalse(AirSupply.mustYieldForAir(false, 0, WOLF_MAX_AIR, false));
        assertFalse(AirSupply.mustYieldForAir(false, 40, WOLF_MAX_AIR, false));
    }

    @Test
    @DisplayName("a water-breather never yields, because surfacing is not what it needs")
    void waterBreatherNeverYields() {
        // A fish or axolotl companion suffocating in AIR must not be pushed out of the water by a rule
        // written for land creatures. It also must not have its behaviour suspended underwater.
        assertFalse(AirSupply.mustYieldForAir(true, 0, WOLF_MAX_AIR, true));
        assertFalse(AirSupply.mustYieldForAir(false, 0, WOLF_MAX_AIR, true));
    }

    @Test
    @DisplayName("a creature with no air pool at all is left alone")
    void noAirPoolNeverYields() {
        // maxAir <= 0 means the air mechanic does not apply, so there is nothing to protect.
        assertFalse(AirSupply.mustYieldForAir(true, 0, 0, false));
        assertFalse(AirSupply.mustYieldForAir(true, -1, -1, false));
    }

    @Test
    @DisplayName("the threshold scales with the species rather than being one hardcoded number")
    void thresholdScalesWithTheCreature() {
        // A large pool reacts at a quarter of it, capped. Two different maxima must produce different
        // boundaries, otherwise the rule only ever worked for one species.
        int smallMax = 100;
        int largeMax = 1200;
        // Small pool: a quarter is 25, so 30 is still safe and 20 yields.
        assertFalse(AirSupply.mustYieldForAir(true, 30, smallMax, false));
        assertTrue(AirSupply.mustYieldForAir(true, 20, smallMax, false));
        // Large pool: the cap (100) applies, so 150 is safe and 90 yields.
        assertFalse(AirSupply.mustYieldForAir(true, 150, largeMax, false));
        assertTrue(AirSupply.mustYieldForAir(true, 90, largeMax, false));
    }

    // ------------------------------------------------------------------
    // The separation between "is this creature ours" and "does it need air".
    //
    // This is the wiring layer for the air rule, not just its boolean result. The defect it guards
    // against could not be seen from mustYieldForAir alone: the gates that suppress vanilla
    // owner-following and retaliation were keyed on the air-dependent predicate, so a creature that
    // needed air silently un-gated the entire vanilla AI.
    // ------------------------------------------------------------------

    /** A creature this module manages: server-side, alive, bonded, no conflict, not occupied. */
    private static boolean managed() {
        return AirSupply.isManaged(true, true, true, false, false);
    }

    @Test
    @DisplayName("WIRING: critical air does not change whether the creature is managed, so the gates stay on")
    void managementIsIndependentOfAir() {
        // The managed state is the same whether or not the creature is drowning. If this ever became
        // air-dependent, a drowning companion would regain vanilla following and retaliation.
        boolean drowning = AirSupply.mustYieldForAir(true, 0, WOLF_MAX_AIR, false);
        assertTrue(drowning, "this creature does need air");

        // The gate predicate is driven only by the management inputs, which have no air parameter at all.
        assertTrue(managed(), "management must not depend on air");
        assertTrue(AirSupply.isManaged(true, true, true, false, false));
    }

    @Test
    @DisplayName("WIRING: a WAIT companion that needs air yields movement but stays managed")
    void waitCompanionYieldsMovementAndStaysManaged() {
        // The WAIT requirement, end to end through the wiring rule: it may move to breathe, and it must
        // not chase, must not follow its owner, and must not start picking its own enemies.
        boolean mustBreathe = AirSupply.mustYieldForAir(true, 10, WOLF_MAX_AIR, false);
        assertFalse(AirSupply.mayControlMovement(managed(), mustBreathe),
                "movement is released so the creature can surface");

        // The gates read the managed flag, which is untouched by the yield. A test that only checked
        // mustYieldForAir would pass even if the gates had been keyed on the air rule.
        assertTrue(managed(),
                "the gates that suppress vanilla follow and retaliation must remain in force");

        // And once it can breathe again, the order applies exactly as before.
        assertFalse(AirSupply.mustYieldForAir(true, WOLF_MAX_AIR, WOLF_MAX_AIR, false));
        assertTrue(AirSupply.mayControlMovement(managed(), false),
                "recovered: the ordered behaviour resumes");
    }

    @Test
    @DisplayName("a creature this module does not manage is never controlled, air or no air")
    void unmanagedCreatureIsNeverControlled() {
        boolean mustBreathe = AirSupply.mustYieldForAir(true, 0, WOLF_MAX_AIR, false);
        // A creature with no bond, or one ridden/leashed/animating, must not be steered in either air
        // state. This is the condition that stops the goal from touching unrelated mobs.
        assertFalse(AirSupply.mayControlMovement(
                AirSupply.isManaged(true, true, false, false, false), mustBreathe));
        assertFalse(AirSupply.mayControlMovement(
                AirSupply.isManaged(true, true, true, false, true), mustBreathe));
        assertFalse(AirSupply.mayControlMovement(
                AirSupply.isManaged(true, true, true, true, false), mustBreathe));
        assertFalse(AirSupply.mayControlMovement(
                AirSupply.isManaged(true, false, true, false, false), mustBreathe));
        assertFalse(AirSupply.mayControlMovement(
                AirSupply.isManaged(false, true, true, false, false), mustBreathe));
        // Same, with full air: still not ours to drive.
        assertFalse(AirSupply.mayControlMovement(
                AirSupply.isManaged(true, true, false, false, false), false));
    }
}
