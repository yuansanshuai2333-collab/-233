package com.nonid.affinity.data;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FriendAttackStreakTest {
    @Test void exactTenSecondIntervalsContinueAndThirdEscalates() {
        var state = new FriendAttackStreak(); assertEquals(0, state.attack(0, true));
        assertEquals(1, state.attack(200, true)); assertEquals(2, state.attack(400, false)); assertEquals(2, state.attack(600, false));
    }
    @Test void overTenButUnderThirtyResetsCountWithoutForgiveness() {
        var state = new FriendAttackStreak(); state.attack(0, true);
        assertEquals(1, state.attack(201, false)); assertEquals(1, state.attack(202, false)); assertEquals(2, state.attack(203, false));
    }
    @Test void thirtySecondsEndsSessionAndOnlyFriendsCanStartAnother() {
        var state = new FriendAttackStreak(); state.attack(0, true);
        assertEquals(0, state.attack(600, false)); assertEquals(0, state.attack(601, false));
        assertEquals(0, state.attack(602, true)); assertEquals(1, state.attack(603, true));
    }
    @Test void distinctAttacksInOneTickCountAndReloadDoesNotRestoreTolerance() {
        var state = new FriendAttackStreak(); state.attack(0, true); assertEquals(1, state.attack(0, true));
        state = FriendAttackStreak.fromNbt(state.toNbt()); assertEquals(2, state.attack(1, false));
        assertEquals(2, state.attack(1, false)); assertEquals(2, state.attack(2, false));
    }
    @Test void missingAndNonFriendRecordsNeverAcquirePenaltySession() {
        var state = new FriendAttackStreak(); assertEquals(0, state.attack(1, false)); assertEquals(0, state.attack(2, false));
    }
}
