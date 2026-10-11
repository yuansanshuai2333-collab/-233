package com.nonid.affinity.data;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealthDebtTest {
    @Test void manyDistinctSourcesRemainConfirmedUntilActualHealingConsumesThem() {
        var debt = new HealthDebt();
        for (int i = 0; i < 300; i++) debt.change(1000 - i, 999 - i, 1000, UUID.randomUUID(), null);
        debt = HealthDebt.fromNbt(debt.toNbt());
        assertEquals(300, debt.change(700, 1000, 1000, null, UUID.randomUUID()));
        assertEquals(0, debt.missing());
    }
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    @Test void mixedSelfAndOtherDamageOnlyRewardsConfirmedOtherPart() {
        var debt = new HealthDebt(); debt.change(20, 16, 20, a, null); debt.change(16, 12, 20, b, null);
        assertEquals(4, debt.change(12, 20, 20, null, a)); assertEquals(0, debt.missing());
        assertEquals(0, debt.change(20, 20, 20, null, a));
    }
    @Test void unknownOldInjuriesDoNotBecomeCreditAfterOneKnownAttack() {
        var debt = new HealthDebt(); debt.change(12, 10, 20, b, null);
        assertEquals(2, debt.change(10, 20, 20, null, a));
    }
    @Test void naturalHealingConsumesOldestWoundsAndCannotBeClaimedAgain() {
        var debt = new HealthDebt(); debt.change(20, 14, 20, b, null);
        assertEquals(0, debt.change(14, 18, 20, null, null));
        assertEquals(2, debt.change(18, 20, 20, null, a));
    }
    @Test void attributedWoundsSurviveSerializationAndRemainUserSpecific() {
        var debt = new HealthDebt(); debt.change(20, 16, 20, a, null); debt.change(16, 10, 20, b, null);
        var copy = HealthDebt.fromNbt(debt.toNbt()); assertEquals(4, copy.change(10, 20, 20, null, b));
        assertEquals(6, debt.change(10, 20, 20, null, a));
    }
    @Test void maxHealthChangesNeverCreateConfirmedDamageOrExceedActualRecovery() {
        var debt = new HealthDebt(); debt.change(20, 16, 20, b, null);
        assertEquals(4, debt.change(16, 40, 40, null, a));
        debt.change(40, 36, 40, b, null); assertEquals(0, debt.change(36, 20, 20, null, a));
        assertEquals(0, debt.missing());
    }
    @Test void invalidAndNegativeObservationsGiveNoReward() {
        var debt = new HealthDebt(); assertEquals(0, debt.change(Double.NaN, 20, 20, b, a));
        assertEquals(0, debt.recover(-1, a)); assertEquals(0, debt.missing());
    }
}
