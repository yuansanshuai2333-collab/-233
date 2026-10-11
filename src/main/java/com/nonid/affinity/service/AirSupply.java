package com.nonid.affinity.service;

/**
 * When the companion movement goal must stand down so the creature can breathe.
 *
 * <p>Why this exists, from the actual registration rather than from a summary: vanilla registers
 * {@code SwimGoal} and {@code BreatheAirGoal} at priority 0 for every {@code PathAwareEntity}, and the
 * module's {@code CompanionMovementGoal} is also priority 0. A review previously recorded the conflict
 * as being with {@code SwimGoal}. Reading the mapped bytecode shows that is only half right:
 *
 * <ul>
 *   <li>{@code SwimGoal.setControls} claims <b>JUMP only</b> (it calls
 *       {@code getJumpControl().setActive()} and never touches movement);</li>
 *   <li>{@code BreatheAirGoal} claims <b>MOVE</b> (+ LOOK);</li>
 *   <li>{@code CompanionMovementGoal} claims <b>MOVE + JUMP</b>.</li>
 * </ul>
 *
 * <p>So BOTH vanilla goals are at risk. A goal selector holds one running goal per control flag, so
 * while the movement goal runs it occupies MOVE and JUMP: the creature loses its automatic ascent
 * (SwimGoal) AND its drive to reach air (BreatheAirGoal). A companion held or steered underwater can
 * therefore drown while its own vanilla air goal is unable to start.
 *
 * <p>The rule here is deliberately narrow. It stands the movement goal down only when air is actually
 * the pressing need, so the "hold position, do not chase" behaviour is unchanged on land and in shallow
 * water -- which is the requirement that must not regress. It does not claim to solve every water
 * interaction: the creature's own AI takes over while it surfaces, and how that reads in play is an
 * in-game acceptance item.
 *
 * <p>Pure and Minecraft-free, so the decision is unit-testable with plain values.
 */
public final class AirSupply {

    /** Below this many air ticks the creature is suffocating and must be allowed to surface. */
    private static final int CRITICAL_AIR = 100;

    private AirSupply() {
    }

    /**
     * Whether this module is managing the creature at all -- the condition the GATES depend on.
     *
     * <p>Deliberately free of any air parameter, and that is the whole point of this method existing
     * separately. "Is this creature ours" and "does it need to breathe" are two different questions, and
     * the gates that suppress vanilla owner-following and retaliation must be driven by the FIRST one
     * only. When they were driven by the second, a creature that needed air silently un-gated the entire
     * vanilla AI: it would start following its owner and picking its own enemies again, which is exactly
     * the behaviour the four modes exist to replace. The creature was supposed to surface, not to resume
     * being a vanilla pet.
     *
     * <p>Pure, so the separation is testable without a world, an entity or a goal selector.
     *
     * @param isServerSide      the creature is in a server world
     * @param alive             the creature is alive
     * @param hasOwner          the creature has a companion bond
     * @param ownershipConflict its recorded ownership contradicts itself
     * @param externallyOccupied something else currently owns its movement (ridden, leashed, sitting,
     *                            animating)
     */
    public static boolean isManaged(boolean isServerSide, boolean alive, boolean hasOwner,
                                    boolean ownershipConflict, boolean externallyOccupied) {
        return isServerSide && alive && hasOwner && !ownershipConflict && !externallyOccupied;
    }

    /**
     * Whether the movement goal may take and hold movement this tick.
     *
     * <p>Two independent conditions: the creature must be ours to manage, and it must not urgently need
     * air. Expressed as a combination of two named rules rather than one fused predicate, so neither can
     * silently borrow the other's inputs again.
     */
    public static boolean mayControlMovement(boolean managed, boolean mustYieldForAir) {
        return managed && !mustYieldForAir;
    }

    /**
     * Whether the movement goal must yield so the creature can get air.
     *
     * @param submergedInWater whether the creature's body is under water
     * @param air             current air supply in ticks
     * @param maxAir          this creature's maximum air supply
     * @param canBreatheInWater whether the creature type breathes water (fish, axolotl, drowned), in
     *                          which case surfacing is not merely unnecessary but harmful
     * @return true when the movement goal must not steer this tick
     */
    public static boolean mustYieldForAir(boolean submergedInWater, int air, int maxAir,
                                          boolean canBreatheInWater) {
        if (canBreatheInWater || !submergedInWater) {
            // A water-breather has no business surfacing, and a creature whose head is out of the water
            // is not drowning. Neither case may take movement away from the mode the player ordered.
            return false;
        }
        if (maxAir <= 0) {
            // No meaningful supply: this creature does not use the air mechanic at all, so there is
            // nothing to protect and the mode keeps its movement.
            return false;
        }
        // A quarter of the supply, capped, so a creature with a large pool reacts promptly instead of
        // waiting until it is nearly out. Uses a fraction rather than a bare constant so the rule holds
        // for whatever maximum the species has.
        int threshold = Math.min(CRITICAL_AIR, Math.max(1, maxAir / 4));
        return air < threshold;
    }
}
