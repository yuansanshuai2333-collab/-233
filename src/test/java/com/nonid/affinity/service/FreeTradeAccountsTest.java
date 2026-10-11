package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nonid.affinity.data.PlayerAffinity;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The free-trade account: what is stored, what survives a save/reload, and what a pending cure fact does.
 *
 * <p>These tests exist because the parts they cover are the ones a running game hides:
 *
 * <ul>
 *   <li>a cure that happened while nobody was looking must not be lost, and must not be counted twice;</li>
 *   <li>the record must survive a reload that happens BETWEEN the cure and the allowance being synchronised;</li>
 *   <li>it must survive the villager being converted again, which is why it lives on the relation (transferred
 *       with the individual) and why its dedup id is the conversion's, never the villager's;</li>
 *   <li>several old events must produce ONE allowance of three, not one per event.</li>
 * </ul>
 */
class FreeTradeAccountsTest {

    /** Exactly what a save/reload does: write the NBT, read it back. */
    private static PlayerAffinity reload(PlayerAffinity relation) {
        NbtCompound tag = relation.toNbt();
        return PlayerAffinity.fromNbt(tag);
    }

    /** Synchronise pending events, spend one trade if there is one, and write the result back. */
    private static FreeTradeQuota.State spendOne(PlayerAffinity relation, long heroEpoch) {
        FreeTradeQuota.State synced = FreeTradeAccounts.synchronizedState(relation, heroEpoch).state();
        FreeTradeQuota.State spent = FreeTradeQuota.spend(synced);
        FreeTradeAccounts.store(relation, spent);
        return spent;
    }

    @Test
    @DisplayName("T1. a cure recorded but not yet applied survives a reload, and applies exactly once")
    void pendingCureSurvivesReloadAndAppliesOnce() {
        PlayerAffinity relation = new PlayerAffinity(0);
        FreeTradeAccounts.CureFact fact = FreeTradeAccounts.recordCure(relation, "zombie-1");
        assertTrue(fact.recorded(), "the first observation of a conversion is a real cure");
        assertEquals(1L, fact.observedCureEpoch());

        // The player quits (or the world unloads) BEFORE the allowance was synchronised.
        PlayerAffinity loaded = reload(relation);
        assertEquals(1L, loaded.getObservedCureEpoch(), "the pending fact must be in the save");
        assertEquals("zombie-1", loaded.getLastCureSourceId(), "so must the dedup marker");
        assertEquals(0, loaded.getRemainingFreeTrades(), "nothing has been granted yet");

        // First sync after the reload: the cure pays out, once.
        FreeTradeQuota.Result first = FreeTradeAccounts.synchronizedState(loaded, 0L);
        assertTrue(first.changed());
        assertEquals(FreeTradeQuota.FULL_ALLOWANCE, first.state().remaining());

        // A second sync of the same, already applied event does nothing.
        FreeTradeQuota.Result second = FreeTradeAccounts.synchronizedState(loaded, 0L);
        assertFalse(second.changed(), "an applied event must not pay out again");
        assertEquals(FreeTradeQuota.FULL_ALLOWANCE, second.state().remaining());
    }

    @Test
    @DisplayName("T1b. re-observing the SAME conversion after a reload does not record a second cure")
    void repeatedObservationOfOneConversionIsDeduplicatedAcrossReloads() {
        PlayerAffinity relation = new PlayerAffinity(0);
        assertTrue(FreeTradeAccounts.recordCure(relation, "zombie-1").recorded());
        PlayerAffinity loaded = reload(relation);

        // The same conversion reported again (a repeated event, or a load in the middle of one).
        FreeTradeAccounts.CureFact again = FreeTradeAccounts.recordCure(loaded, "zombie-1");
        assertFalse(again.recorded(), "one conversion is one cure, however often it is observed");
        assertEquals(1L, again.observedCureEpoch());

        // ...and it is still only one allowance: apply it, spend all three, and nothing comes back.
        FreeTradeAccounts.synchronizedState(loaded, 0L);
        spendOne(loaded, 0L);
        spendOne(loaded, 0L);
        FreeTradeQuota.State emptied = spendOne(loaded, 0L);
        assertEquals(0, emptied.remaining());
        assertFalse(FreeTradeAccounts.synchronizedState(loaded, 0L).changed());
    }

    @Test
    @DisplayName("T2. after another infection and cure the fact still belongs to the SAME relation")
    void aSecondConversionIsANewFactOnTheSameRelation() {
        PlayerAffinity relation = new PlayerAffinity(0);
        FreeTradeAccounts.recordCure(relation, "zombie-1");
        FreeTradeAccounts.synchronizedState(relation, 0L);
        spendOne(relation, 0L);
        FreeTradeQuota.State afterSpending = spendOne(relation, 0L);
        assertEquals(1, afterSpending.remaining(), "one of the three is left");
        assertEquals(2, afterSpending.totalFreeTrades());

        // The individual was converted again, so the ENTITY is new -- but the relation, and therefore the
        // account, moved with it. Nothing here re-creates the relation: that is the point.
        PlayerAffinity afterConversion = reload(relation);
        FreeTradeAccounts.CureFact second = FreeTradeAccounts.recordCure(afterConversion, "zombie-2");
        assertTrue(second.recorded(), "a DIFFERENT conversion is a new cure even though it is the same villager");
        assertEquals(2L, second.observedCureEpoch());

        FreeTradeQuota.Result refreshed = FreeTradeAccounts.synchronizedState(afterConversion, 0L);
        assertTrue(refreshed.changed());
        assertEquals(FreeTradeQuota.FULL_ALLOWANCE, refreshed.state().remaining(),
                "the new event restores the allowance; the earlier spending is not undone, it is just superseded");
        assertEquals(2, afterConversion.getTotalFreeTrades(), "the two spent trades stay counted");
    }

    @Test
    @DisplayName("T3. several old events then trading: one allowance of three, never a batch of top-ups")
    void severalOldEventsGrantOnceAndAreNotReplayedAfterSpending() {
        PlayerAffinity relation = new PlayerAffinity(0);
        // Three cures happened while the player was away; the player also gained hero epoch 2. Only one of the
        // three cures is a different conversion from the others, so record three distinct ones.
        FreeTradeAccounts.recordCure(relation, "zombie-1");
        FreeTradeAccounts.recordCure(relation, "zombie-2");
        FreeTradeAccounts.recordCure(relation, "zombie-3");
        assertEquals(3L, relation.getObservedCureEpoch());

        // The player starts trading.
        FreeTradeQuota.Result applied = FreeTradeAccounts.synchronizedState(relation, 2L);
        assertTrue(applied.changed());
        assertEquals(FreeTradeQuota.FULL_ALLOWANCE, applied.state().remaining(), "three events, ONE allowance");
        assertEquals(3L, applied.state().lastCureEpoch());
        assertEquals(2L, applied.state().lastHeroEpoch());

        // Two of the three are spent; the already-applied events must NOT come back.
        spendOne(relation, 2L);
        FreeTradeQuota.State afterTwo = spendOne(relation, 2L);
        assertEquals(1, afterTwo.remaining());
        assertEquals(1, FreeTradeAccounts.synchronizedState(relation, 2L).state().remaining(),
                "an applied event must not top the allowance back up");
        assertEquals(2, afterTwo.totalFreeTrades());
    }

    @Test
    @DisplayName("T4. upgrade, THEN acquire the hero effect, THEN open an old villager: the event still pays")
    void anEventAcquiredAfterTheUpgradeIsNotSwallowedByTheMigration() {
        // The order the previous migration got wrong: the baseline was read from the FIRST QUOTE, so an event
        // acquired in between became the baseline and never paid.
        NbtCompound oldTag = new NbtCompound();
        oldTag.putInt("affinity", 40);
        PlayerAffinity relation = PlayerAffinity.fromNbt(oldTag);

        // The player acquires the hero effect after the upgrade, while this villager is not even loaded.
        long heroEpochAfterUpgrade = 1L;

        // Only now does the old relation get its state -- and the event must still be new.
        FreeTradeQuota.Result granted = FreeTradeAccounts.synchronizedState(relation, heroEpochAfterUpgrade);
        assertTrue(granted.changed(), "the acquisition after the upgrade must not be consumed as a baseline");
        assertEquals(FreeTradeQuota.FULL_ALLOWANCE, granted.state().remaining());
        assertEquals(1L, granted.state().lastHeroEpoch());

        // And an effect that was ALREADY active when the feature was installed cannot fabricate one: it never
        // bumped the counter, so the epoch is still zero.
        PlayerAffinity another = PlayerAffinity.fromNbt(oldTag);
        assertFalse(FreeTradeAccounts.synchronizedState(another, 0L).changed(),
                "an effect predating the feature is not a new acquisition");
    }

    @Test
    @DisplayName("migration: an old save gets ZERO allowance with the enable boundary, and a later event still pays")
    void oldSaveIsMigratedWithoutFabricatingAnAllowance() {
        // A relation as written by a pre-1.2.0 version: no quota fields at all.
        NbtCompound oldTag = new NbtCompound();
        oldTag.putInt("affinity", 40);
        PlayerAffinity relation = PlayerAffinity.fromNbt(oldTag);
        assertFalse(relation.isQuotaInitialized());

        // Migration happens at the enable boundary: zero allowance, both markers at zero. Nothing is granted...
        FreeTradeQuota.State migrated = FreeTradeAccounts.stateOf(relation);
        assertEquals(0, migrated.remaining(), "an old save must not be handed three trades");
        assertEquals(0L, migrated.lastHeroEpoch());

        // ...and the boundary is PERSISTED, so a later reload does not move it.
        PlayerAffinity loaded = reload(relation);
        assertTrue(loaded.isQuotaInitialized());
        assertEquals(0L, FreeTradeAccounts.stateOf(loaded).lastHeroEpoch());

        // Re-acquiring the effect later is a genuinely new event and does pay.
        FreeTradeQuota.Result granted = FreeTradeAccounts.synchronizedState(loaded, 1L);
        assertTrue(granted.changed());
        assertEquals(FreeTradeQuota.FULL_ALLOWANCE, granted.state().remaining());
    }

    @Test
    @DisplayName("a cure seen before any state existed is not swallowed by the migration")
    void aCureRecordedBeforeInitialisationSurvives() {
        PlayerAffinity relation = new PlayerAffinity(0);
        FreeTradeAccounts.recordCure(relation, "zombie-1");
        // Recording a fact makes the relation real: the allowance is zero and the fact is pending.
        assertTrue(relation.isQuotaInitialized());
        assertEquals(0, relation.getRemainingFreeTrades());

        FreeTradeQuota.Result applied = FreeTradeAccounts.synchronizedState(relation, 0L);
        assertTrue(applied.changed(), "the recorded cure must still pay out");
        assertEquals(FreeTradeQuota.FULL_ALLOWANCE, applied.state().remaining());
    }

    @Test
    @DisplayName("a conversion with no source, and a cure with no converter, are refused distinctly")
    void missingSourceAndMissingConverterAreDifferentRefusals() {
        PlayerAffinity relation = new PlayerAffinity(0);
        FreeTradeAccounts.CureFact fact = FreeTradeAccounts.recordCure(relation, "");
        assertFalse(fact.recorded());
        assertNotNull(fact.refusal());
        assertEquals(CureFactRules.Refusal.NO_SOURCE, fact.refusal());
        assertEquals(0L, relation.getObservedCureEpoch());

        assertEquals(CureFactRules.Refusal.NO_CONVERTER, CureFactRules.refusalForConverter(null));
        assertEquals(CureFactRules.Refusal.NO_CONVERTER, CureFactRules.refusalForConverter("  "));
        assertNull(CureFactRules.refusalForConverter("player-uuid"));
    }
}
