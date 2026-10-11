package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ownership rules for companions.
 *
 * <p>The review of 0.7.0 asked for tests against the real decision rather than an isolated uuid
 * comparison, because the bug was in how ownership was decided: resolving the vanilla owner to a
 * loaded entity returned null when the owner was offline, and that null was read as "unowned".
 */
class CompanionOwnershipTest {

    private static final UUID OWNER_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID PLAYER_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Test
    @DisplayName("an offline vanilla owner still blocks other players")
    void offlineVanillaOwnerBlocksOthers() {
        // vanillaOwner is known from the stored reference even though no entity could be resolved.
        assertFalse(CompanionService.mayControl(null, OWNER_A, PLAYER_B),
                "player B must not take control of A's pet while A is offline");
    }

    @Test
    @DisplayName("the vanilla owner may control their own pet")
    void vanillaOwnerMayControlOwnPet() {
        assertTrue(CompanionService.mayControl(null, OWNER_A, OWNER_A));
    }

    @Test
    @DisplayName("another player's companion bond blocks a third player")
    void existingBondBlocksOthers() {
        assertFalse(CompanionService.mayControl(OWNER_A, null, PLAYER_B),
                "an existing companion bond must not transfer silently");
    }

    @Test
    @DisplayName("the bonded player keeps control")
    void bondedPlayerKeepsControl() {
        assertTrue(CompanionService.mayControl(OWNER_A, null, OWNER_A));
    }

    @Test
    @DisplayName("an unowned, unbonded creature may be claimed")
    void unownedCreatureMayBeClaimed() {
        assertTrue(CompanionService.mayControl(null, null, PLAYER_B));
    }

    @Test
    @DisplayName("contradictory ownership blocks both players")
    void conflictBlocksBoth() {
        // Bonded to B while unowned, then tamed by A. That state can only come from an older version
        // or an external change; guessing a winner would silently transfer control, so an operator
        // must repair it instead.
        assertFalse(CompanionService.mayControl(PLAYER_B, OWNER_A, PLAYER_B),
                "the bonded player must be refused while the record contradicts itself");
        assertFalse(CompanionService.mayControl(PLAYER_B, OWNER_A, OWNER_A),
                "the vanilla owner must also be refused until the conflict is cleared");
    }
}
