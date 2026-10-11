package com.nonid.affinity.service;

import com.nonid.affinity.data.PlayerAffinity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InheritanceSelectionTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private static PlayerAffinity friend(int value) {
        PlayerAffinity relation = new PlayerAffinity(value);
        relation.setBefriended(true);
        return relation;
    }
    @Test void diagonalCandidatesOutsideTheConfiguredSphereAreIgnored() {
        var entries = Map.of(owner.toString(), friend(100));
        assertNull(InheritanceSelection.choose(entries, null, null, false, 50, 6));
        assertNotNull(InheritanceSelection.choose(entries, null, null, false, 36, 6));
    }
    @Test void existingOwnerWinsOverAStrongerRelationToAnotherPlayer() {
        var entries = Map.of(owner.toString(), friend(40), other.toString(), friend(200));
        var chosen = InheritanceSelection.choose(entries, null, owner, false, 1, 6);
        assertEquals(owner, chosen.playerUuid());
        assertEquals(40, chosen.affinity());
    }
    @Test void conflictingOrUnconfirmedOwnershipNeverCreatesAnInheritedBond() {
        var entries = Map.of(owner.toString(), friend(100));
        assertNull(InheritanceSelection.choose(entries, other, owner, false, 1, 6));
        assertNull(InheritanceSelection.choose(entries, null, null, true, 1, 6));
        assertNull(InheritanceSelection.choose(entries, null, other, false, 1, 6));
    }
    @Test void malformedKeyCannotDonateItsAffinityToThePreviouslySelectedPlayer() {
        var entries = new LinkedHashMap<String, PlayerAffinity>();
        entries.put(owner.toString(), friend(40));
        entries.put("broken-player-uuid", friend(200));
        var chosen = InheritanceSelection.choose(entries, null, null, false, 1, 6);
        assertEquals(owner, chosen.playerUuid());
        assertEquals(40, chosen.affinity());
    }
    @Test void malformedHighestEntryDoesNotHideAnotherValidCandidate() {
        var entries = new LinkedHashMap<String, PlayerAffinity>();
        entries.put("broken-player-uuid", friend(200));
        entries.put(owner.toString(), friend(40));
        assertEquals(owner, InheritanceSelection.choose(entries, null, null, false, 1, 6).playerUuid());
    }
}
