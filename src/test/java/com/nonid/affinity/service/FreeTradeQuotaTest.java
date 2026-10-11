package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nonid.affinity.service.FreeTradeQuota.Ineligible;
import com.nonid.affinity.service.FreeTradeQuota.RefreshSource;
import com.nonid.affinity.service.FreeTradeQuota.State;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The close-friend courtesy allowance: which event refreshes it, which must not, and when a trade may be free.
 *
 * <p>Every assertion is a rule the implementation plan states, because the interesting failures are all "something
 * refreshed the allowance that should not have" -- a bug a running game hides very well.
 *
 * <p>The refresh entry point takes BOTH event epochs at once. The tests below include the sequence that made the
 * two-marker version wrong: cure applied immediately, hero synchronised later.
 */
class FreeTradeQuotaTest {

    private static final int BASE = 20;

    private static State fresh() {
        return State.forNewRelation();
    }

    /** The usual call: synchronise both observed epochs. */
    private static State sync(State state, long heroEpoch, long cureEpoch) {
        return FreeTradeQuota.applyObservedEvents(state, heroEpoch, cureEpoch).state();
    }

    // ------------------------------------------------------------------
    // Refresh events
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a new hero epoch sets the allowance to three, and the same notification twice does not")
    void heroEpochGrantsAndDoesNotRepeat() {
        FreeTradeQuota.Result first = FreeTradeQuota.applyObservedEvents(fresh(), 1L, 0L);
        assertTrue(first.changed(), "the first observed epoch is a real event");
        assertEquals(RefreshSource.HERO_EPOCH, first.source());
        assertEquals(3, first.state().remaining());
        assertEquals(1L, first.state().lastHeroEpoch());
        assertEquals(1L, first.state().quotaRevision(), "a refresh advances the revision");

        FreeTradeQuota.Result repeat = FreeTradeQuota.applyObservedEvents(first.state(), 1L, 0L);
        assertFalse(repeat.changed(), "the same notification must not grant again");
        assertEquals(RefreshSource.ALREADY_APPLIED, repeat.source());
        assertEquals(3, repeat.state().remaining());

        FreeTradeQuota.Result stale = FreeTradeQuota.applyObservedEvents(first.state(), 0L, 0L);
        assertFalse(stale.changed(), "a stale epoch is not a new event");
        assertEquals(1L, stale.state().lastHeroEpoch(), "and must not move the marker backwards");
    }

    @Test
    @DisplayName("restarting or upgrading the hero effect must NOT refresh, because the epoch only rises on acquire")
    void extendingOrUpgradingTheHeroEffectDoesNotRefresh() {
        State afterFirst = sync(fresh(), 5L, 0L);
        State spent = new State(1, afterFirst.lastHeroEpoch(), afterFirst.lastCureEpoch(),
                afterFirst.quotaRevision(), afterFirst.offerRevision(), 2, 2);

        FreeTradeQuota.Result extended = FreeTradeQuota.applyObservedEvents(spent, 5L, 0L);
        assertFalse(extended.changed(), "an unchanged epoch is not an event");
        assertEquals(1, extended.state().remaining(), "and the spent allowance must stay spent");
    }

    @Test
    @DisplayName("a cure refreshes this pair only, and a repeated completion callback does not")
    void cureRefreshesAndDoesNotRepeat() {
        FreeTradeQuota.Result cure = FreeTradeQuota.applyObservedEvents(fresh(), 0L, 1L);
        assertTrue(cure.changed());
        assertEquals(RefreshSource.CURE, cure.source());
        assertEquals(3, cure.state().remaining());
        assertEquals(1L, cure.state().lastCureEpoch());

        assertFalse(FreeTradeQuota.applyObservedEvents(cure.state(), 0L, 1L).changed(),
                "the completion callback may fire more than once for one cure");
        assertFalse(FreeTradeQuota.applyObservedEvents(cure.state(), 0L, 0L).changed());
    }

    @Test
    @DisplayName("two different events in a row still leave three, never six")
    void twoEventsDoNotStack() {
        State afterHero = sync(fresh(), 1L, 0L);
        State afterCure = sync(afterHero, 1L, 1L);
        assertEquals(3, afterCure.remaining(), "a refresh SETS three; it must not add");
        assertEquals(2L, afterCure.quotaRevision(), "both events were applied");

        State nearlySpent = new State(1, 0L, 0L, 0L, 0L, 2, 2);
        assertEquals(3, sync(nearlySpent, 0L, 9L).remaining(), "one left, new cure -> three, not four");
        assertEquals(3, sync(nearlySpent, 9L, 0L).remaining(), "one left, new hero -> three, not four");
    }

    @Test
    @DisplayName("both events already true, then spent, then re-synced: no second grant is banked")
    void bothEventsAlreadyTrueCannotBeBanked() {
        // The defect the single-entry design removes. Hero 1 and cure 1 are both already true when the allowance is
        // first granted, the player spends two, and the next synchronisation sees exactly the same two epochs. It
        // must grant nothing: the two events were already paid for by the first call.
        State granted = sync(fresh(), 1L, 1L);
        assertEquals(3, granted.remaining());
        assertEquals(1L, granted.lastHeroEpoch());
        assertEquals(1L, granted.lastCureEpoch(), "both markers advance together");

        State spent = FreeTradeQuota.spend(FreeTradeQuota.spend(granted));
        assertEquals(1, spent.remaining());

        State resynced = sync(spent, 1L, 1L);
        assertEquals(1, resynced.remaining(), "an old event must not refund what was spent");
        assertEquals(spent.quotaRevision(), resynced.quotaRevision(), "and nothing was applied");
    }

    @Test
    @DisplayName("a cure applied immediately carries the hero epoch that was already true, so hero cannot pay twice")
    void cureAppliedImmediatelyCarriesTheHeroEpoch() {
        // The reviewer's sequence, in two calls. First quote: hero is already true but has not been synchronised;
        // the cure arrives. Because BOTH epochs are passed, the single grant also marks hero 1 as handled.
        State firstQuote = sync(fresh(), 1L, 1L);
        assertEquals(3, firstQuote.remaining());
        assertEquals(1L, firstQuote.lastHeroEpoch());

        // The player spends two, and only now does the "delayed" hero synchronisation arrive. It is old news.
        State spent = FreeTradeQuota.spend(FreeTradeQuota.spend(firstQuote));
        State delayedHero = sync(spent, 1L, 1L);
        assertEquals(1, delayedHero.remaining(), "the delayed hero event must NOT grant a second three");

        // And a hero event that really happens AFTER the spending still grants.
        State reallyNew = sync(delayedHero, 2L, 1L);
        assertEquals(3, reallyNew.remaining(), "a genuinely new event still refreshes");
    }

    @Test
    @DisplayName("when both epochs are new at once the call reports it and still grants only once")
    void bothNewAtOnceGrantsOnce() {
        FreeTradeQuota.Result both = FreeTradeQuota.applyObservedEvents(fresh(), 4L, 2L);
        assertTrue(both.changed());
        assertTrue(both.bothEventsAtOnce());
        assertEquals(3, both.state().remaining());
        assertEquals(1L, both.state().quotaRevision(), "one refresh, not two");
        assertEquals(4L, both.state().lastHeroEpoch());
        assertEquals(2L, both.state().lastCureEpoch());
    }

    @Test
    @DisplayName("losing close-friend status suspends the allowance but never resets or refunds it")
    void suspensionKeepsTheRemainder() {
        State state = new State(2, 4L, 7L, 3L, 0L, 1, 4);
        assertFalse(FreeTradeQuota.isFreeEligible(false, state.remaining(), -5, true),
                "not a close friend: never free");
        assertEquals(2, state.remaining(), "and the count is untouched");

        State unchanged = sync(state, 4L, 7L);
        assertEquals(2, unchanged.remaining());
        assertTrue(FreeTradeQuota.isFreeEligible(true, unchanged.remaining(), -5, true),
                "back to close friend with the same remainder");
    }

    // ------------------------------------------------------------------
    // Eligibility and spending
    // ------------------------------------------------------------------

    @Test
    @DisplayName("eligibility: close friend + allowance + a price that reached zero + an offerable trade")
    void eligibilityRules() {
        assertTrue(FreeTradeQuota.isFreeEligible(true, 3, 0, true), "exactly zero is free");
        assertTrue(FreeTradeQuota.isFreeEligible(true, 3, -1, true), "below zero is free");
        assertNull(FreeTradeQuota.refusalReason(true, 1, -4, true));

        assertEquals(Ineligible.NOT_CLOSE_FRIEND, FreeTradeQuota.refusalReason(false, 3, -5, true));
        assertEquals(Ineligible.NO_ALLOWANCE_LEFT, FreeTradeQuota.refusalReason(true, 0, -5, true));
        assertEquals(Ineligible.PRICE_NOT_FREE, FreeTradeQuota.refusalReason(true, 3, 1, true),
                "a one-item price is a normal trade");
        assertEquals(Ineligible.TRADE_NOT_AVAILABLE, FreeTradeQuota.refusalReason(true, 3, -5, false));
    }

    @Test
    @DisplayName("a demanded trade that still costs something does not consume an allowance")
    void aPriceAboveZeroLeavesTheAllowanceAlone() {
        // raw demand 1 with the default 0.05 multiplier adds floor(20 * 1 * 0.05) = 1, so the trade still costs 2
        // after a 19-item discount and must be paid normally.
        int effective = FreeTradeQuota.effectiveFirstPayment(BASE, 1, 0.05F, -19);
        assertEquals(2, effective, "base 20 + demand 1 - 19: still costs two");
        assertFalse(FreeTradeQuota.isFreeEligible(true, 3, effective, true));

        State state = sync(fresh(), 1L, 0L);
        assertEquals(3, state.remaining(), "nothing was spent");
    }

    @Test
    @DisplayName("spending stops at zero and is only ever done on a delivered trade")
    void spendingIsBounded() {
        State state = sync(fresh(), 1L, 0L);
        state = FreeTradeQuota.spend(state);
        assertEquals(2, state.remaining());
        assertEquals(1, state.totalFreeTrades());
        assertEquals(1L, state.totalCompletions());
        state = FreeTradeQuota.spend(state);
        state = FreeTradeQuota.spend(state);
        assertEquals(0, state.remaining());
        assertEquals(3, state.totalFreeTrades());

        State extra = FreeTradeQuota.spend(state);
        assertEquals(0, extra.remaining(), "the allowance cannot go negative");
        assertEquals(3, extra.totalFreeTrades(), "and the running total must not move either");

        State paid = FreeTradeQuota.recordPaidCompletion(state);
        assertEquals(0, paid.remaining());
        assertEquals(3, paid.totalFreeTrades());
        assertEquals(4L, paid.totalCompletions());
    }

    @Test
    @DisplayName("a refresh does not erase the running totals, but does advance the quota revision")
    void refreshKeepsTheTotalsAndAdvancesTheRevision() {
        State state = new State(0, 1L, 1L, 2L, 0L, 3, 5);
        State refreshed = sync(state, 1L, 2L);
        assertEquals(3, refreshed.remaining());
        assertEquals(3, refreshed.totalFreeTrades(), "the lifetime count survives a refresh");
        assertEquals(5L, refreshed.totalCompletions());
        assertEquals(3L, refreshed.quotaRevision(), "and the quota revision moved");
        assertEquals(0L, refreshed.offerRevision(), "a refresh of the allowance does not version prices");
    }

    @Test
    @DisplayName("each successful spend advances the quota revision, and a refused one does not")
    void spendingAdvancesTheQuotaRevision() {
        // The defect this pins down: the revision was carried through a purchase unchanged, so a quote cached
        // before it still matched the state after it -- exactly the check that is supposed to keep a fourth trade
        // from being served for free.
        State state = sync(fresh(), 1L, 0L);
        long before = state.quotaRevision();

        state = FreeTradeQuota.spend(state);
        assertTrue(state.quotaRevision() > before, "a successful spend changes the allowance, so it re-versions");
        long first = state.quotaRevision();

        state = FreeTradeQuota.spend(state);
        long second = state.quotaRevision();
        assertTrue(second > first, "the second spend re-versions again");

        state = FreeTradeQuota.spend(state);
        long third = state.quotaRevision();
        assertTrue(third > second, "and so does the third");
        assertEquals(0, state.remaining());
        assertEquals(first != second && second != third, true, "all three revisions differ");

        // The fourth attempt changes nothing, so it must not claim a new revision either.
        State refused = FreeTradeQuota.spend(state);
        assertEquals(third, refused.quotaRevision(), "a refused spend is not a change");
        assertEquals(0, refused.remaining());

        // A paid completion is not an allowance change: it must leave the quota revision alone.
        assertEquals(third, FreeTradeQuota.recordPaidCompletion(state).quotaRevision());

        // Price-only invalidation moves the OTHER counter, so the two questions stay separate.
        State repriced = FreeTradeQuota.invalidateOffers(state);
        assertEquals(third, repriced.quotaRevision(), "prices changed, not the allowance");
        assertEquals(1L, repriced.offerRevision());
    }

    // ------------------------------------------------------------------
    // Persistence and initialisation contracts
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a state read from disk is clamped into the shape the rules allow")
    void persistedStateIsSanitized() {
        State tampered = new State(99, -5L, -1L, -2L, -3L, -3, -4);
        State safe = tampered.sanitized();
        assertEquals(3, safe.remaining(), "the allowance is capped at three");
        assertEquals(0L, safe.lastHeroEpoch());
        assertEquals(0L, safe.lastCureEpoch());
        assertEquals(0L, safe.quotaRevision());
        assertEquals(0, safe.totalFreeTrades());
        assertEquals(0L, safe.totalCompletions());

        assertEquals(0, new State(-4, 0L, 0L, 0L, 0L, 0, 0).sanitized().remaining());
    }

    @Test
    @DisplayName("a NEW relation is not handed three, and its first real event still grants")
    void newRelationContract() {
        // A relation created because the player walks up to a villager they have never met. The markers stay at
        // zero, so an event that is ALREADY true (hero 5) is recognised as new and grants.
        State created = State.forNewRelation();
        assertEquals(0, created.remaining(), "meeting somebody is not an event");
        assertFalse(FreeTradeQuota.isFreeEligible(true, created.remaining(), -5, true));

        State granted = sync(created, 5L, 0L);
        assertEquals(3, granted.remaining(),
                "a hero effect acquired before this relation existed is still usable");
    }

    @Test
    @DisplayName("a MIGRATED relation starts at the enable boundary (zero) and does not replay an older effect")
    void migratedRelationContract() {
        // The enable boundary is ZERO, not "the epoch at the first quote". What that buys:
        //   - an effect the player was ALREADY enjoying at enable time never bumped the counter, so it is still 0
        //     and cannot fabricate an allowance;
        //   - an effect acquired AFTER the enable raised the counter, so it is > 0 and DOES pay.
        State migrated = State.forNewRelation();
        assertEquals(0, migrated.remaining(), "an old save must not be handed three free trades");

        State resynced = sync(migrated, 0L, 0L);
        assertEquals(0, resynced.remaining(), "the effect already active at enable time is not replayed");
        assertFalse(FreeTradeQuota.applyObservedEvents(migrated, 0L, 0L).changed(),
                "the synchronisation itself reports no change");

        // A cure that happens AFTER the enable is a real event and grants.
        assertEquals(3, sync(migrated, 0L, 1L).remaining());
        // As does a hero effect acquired after the enable (a higher epoch).
        assertEquals(3, sync(migrated, 1L, 0L).remaining());
    }

    @Test
    @DisplayName("an old villager that loads LATE must not swallow an event that happened after the enable")
    void migratedRelationLoadingLateDoesNotSwallowAnEvent() {
        // The scenario that rules out "use the newest epoch at load time as the baseline":
        //   enable the feature while the player has no hero effect  -> baseline 0
        //   the old villager is not loaded yet
        //   the player then really acquires hero epoch 1
        //   only afterwards does that villager load and get its migrated state
        // A baseline of 0 makes epoch 1 new and payable. A baseline taken at load time (1) would mark it handled
        // and silently lose a real event -- which is exactly the defect this test now pins.
        State villagerLoadsLate = State.forNewRelation();
        assertEquals(0, villagerLoadsLate.remaining());

        State granted = sync(villagerLoadsLate, 1L, 0L);
        assertEquals(3, granted.remaining(),
                "the genuinely new event must still pay out for a villager that loaded later");
        assertEquals(1L, granted.lastHeroEpoch());

        // And the same with a cure that happened after the enable:
        State cureBaseline = State.forNewRelation();
        assertEquals(3, sync(cureBaseline, 0L, 1L).remaining());

        // An effect that predates the enable is not replayed either -- because it never moved the counter, so the
        // observed epoch is still 0 and 0 > 0 is false.
        State enabledWhileEffectActive = State.forNewRelation();
        assertEquals(0, sync(enabledWhileEffectActive, 0L, 0L).remaining());
    }

    // ------------------------------------------------------------------
    // The one price formula
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the effective price mirrors vanilla's float arithmetic, demand multiplier included")
    void effectivePriceMatchesVanillaArithmetic() {
        // vanilla: clamp(base + max(0, floor(base * rawDemand * priceMultiplier)) + specialPrice, 1, itemMax)
        // this:    the same sum, unclamped, so zero and negatives are visible
        assertEquals(20, FreeTradeQuota.effectiveFirstPayment(20, 0, 0.05F, 0));
        assertEquals(11, FreeTradeQuota.effectiveFirstPayment(20, 10, 0.05F, -19),
                "raw 10 with multiplier 0.05 -> floor(20*10*0.05) = 10");
        assertEquals(0, FreeTradeQuota.effectiveFirstPayment(20, 0, 0.05F, -20), "exactly free");
        assertEquals(-1, FreeTradeQuota.effectiveFirstPayment(20, 0, 0.05F, -21), "and free below zero");
        assertEquals(1, FreeTradeQuota.effectiveFirstPayment(1, 0, 0.05F, 0),
                "a one-item base with no discount is not free");

        // A NEGATIVE raw demand is ignored, exactly as vanilla ignores it.
        assertEquals(20, FreeTradeQuota.effectiveFirstPayment(20, -5, 0.05F, 0));
    }

    @Test
    @DisplayName("the same raw demand gives different prices under different multipliers")
    void theMultiplierBelongsToTheCalculation() {
        // The reviewer's two examples. Adding raw demand straight to the price would make these equal.
        assertEquals(2, FreeTradeQuota.demandCost(20, 2, 0.05F));
        assertEquals(8, FreeTradeQuota.demandCost(20, 2, 0.2F));

        assertEquals(-1, FreeTradeQuota.effectiveFirstPayment(20, 2, 0.05F, -23),
                "20 + 2 - 23");
        assertEquals(5, FreeTradeQuota.effectiveFirstPayment(20, 2, 0.2F, -23),
                "20 + 8 - 23");

        // The first is free, the second is not -- the difference comes only from the multiplier.
        assertTrue(FreeTradeQuota.isFreeEligible(true, 3,
                FreeTradeQuota.effectiveFirstPayment(20, 2, 0.05F, -23), true));
        assertFalse(FreeTradeQuota.isFreeEligible(true, 3,
                FreeTradeQuota.effectiveFirstPayment(20, 2, 0.2F, -23), true));
    }

    @Test
    @DisplayName("the allowance works from the price, not from the configured percentage")
    void eligibilityUsesThePriceNotTheConfiguration() {
        int cheapWithHalfDiscount = FreeTradeQuota.effectiveFirstPayment(2, 0, 0.05F, -2);
        assertEquals(0, cheapWithHalfDiscount);
        assertTrue(FreeTradeQuota.isFreeEligible(true, 3, cheapWithHalfDiscount, true),
                "the price reached zero, so it qualifies regardless of the configured percentage");

        int demandedWithFullDiscount = FreeTradeQuota.effectiveFirstPayment(20, 10, 0.05F, -19);
        assertEquals(11, demandedWithFullDiscount);
        assertFalse(FreeTradeQuota.isFreeEligible(true, 3, demandedWithFullDiscount, true),
                "a demand penalty keeps it payable, so it must not consume an allowance");
    }
}
