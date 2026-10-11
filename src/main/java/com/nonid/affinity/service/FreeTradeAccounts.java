package com.nonid.affinity.service;

import com.nonid.affinity.data.PlayerAffinity;
import java.util.Objects;

/**
 * The bridge between a relation's stored fields and the {@link FreeTradeQuota} state machine.
 *
 * <p>It exists so that {@code data.PlayerAffinity} only has to STORE numbers, while every rule about what those
 * numbers mean -- when a relation is migrated, when a cure fact counts, when it is applied -- stays in code that a
 * plain unit test can drive. Nothing here touches a world, an entity or an item.
 *
 * <p><b>The three things this class is responsible for.</b>
 *
 * <ol>
 *   <li><b>Reading a state, migrating once.</b> A relation from a save written before this feature existed has no
 *       quota fields at all. It must not be handed three free trades retroactively, so it is migrated with a FIXED
 *       baseline: the allowance starts at zero and the markers start at the events that were already true at that
 *       moment. The baseline is then persisted, so it is not recomputed on the next load and a genuinely new event
 *       is still recognised as new.</li>
 *   <li><b>Recording a cure FACT.</b> A successful cure is recorded when the conversion completes, which needs no
 *       player to be online: only the converter's UUID is stored. The record is the {@code observedCureEpoch}
 *       counter, and the dedup marker is the conversion's own source id. Both are persisted, and both live on the
 *       relation -- which lives inside the creature's attachment, so Fabric's conversion transfer carries them to
 *       the converted individual.</li>
 *   <li><b>Applying facts before spending.</b> {@link #synchronizedState} is the only way the trade layer gets a
 *       state to act on: it applies every pending event first. Without that ordering, a trade could consume an
 *       allowance and a later synchronisation of an older event would restore it.</li>
 * </ol>
 */
public final class FreeTradeAccounts {

    private FreeTradeAccounts() {
    }

    /**
     * The relation's state, migrated first if this relation predates the feature.
     *
     * <p><b>The migration baseline is ZERO, never "the epoch at the first quote".</b> Taking the current hero epoch
     * here -- which the previous version of this method did -- quietly consumed a real event: upgrade the save,
     * acquire the hero effect, then open an old villager, and the acquisition is taken as the baseline and never
     * pays out. Zero is the enable boundary: the player's epoch counter starts at zero when the feature is
     * installed, so anything above zero is an acquisition this module actually saw. See
     * {@link FreeTradeQuota.State#forNewRelation()} for both directions of that argument.
     *
     * <p>It also means the migration is INDEPENDENT of when the relation is first touched, so a villager that is
     * not loaded yet cannot swallow an event that happens after the upgrade.
     */
    public static FreeTradeQuota.State stateOf(PlayerAffinity relation) {
        Objects.requireNonNull(relation, "relation");
        if (!relation.isQuotaInitialized()) {
            // A pre-feature save: zero allowance, both markers at the enable boundary. Nothing fabricated and
            // nothing swallowed. Written immediately, so the boundary is fixed from here on.
            FreeTradeQuota.State migrated = FreeTradeQuota.State.forNewRelation();
            store(relation, migrated);
            return migrated;
        }
        return new FreeTradeQuota.State(relation.getRemainingFreeTrades(), relation.getLastAppliedHeroEpoch(),
                relation.getLastAppliedCureEpoch(), relation.getQuotaRevision(), 0L,
                relation.getTotalFreeTrades(), relation.getTotalCompletions());
    }

    /**
     * Write a state back to the relation's stored fields.
     *
     * <p>Every write goes through here, so the "initialised" marker can never be set without numbers, or numbers
     * written without the marker.
     */
    public static void store(PlayerAffinity relation, FreeTradeQuota.State state) {
        FreeTradeQuota.State safe = state.sanitized();
        relation.setRemainingFreeTrades(safe.remaining());
        relation.setLastAppliedHeroEpoch(safe.lastHeroEpoch());
        relation.setLastAppliedCureEpoch(safe.lastCureEpoch());
        relation.setQuotaRevision(safe.quotaRevision());
        relation.setTotalFreeTrades(safe.totalFreeTrades());
        relation.setTotalCompletions(safe.totalCompletions());
        relation.setQuotaInitialized(true);
    }

    /**
     * The state to act on: stored state with every pending event applied.
     *
     * <p>This is the method the quote path and the commit path must both call BEFORE deciding anything. It is what
     * implements "process the events first, then consume the allowance": the hero epoch and the recorded cure
     * epoch are folded in here, at most granting the full allowance once, and the applied markers move together so
     * the same event cannot be paid out twice.
     *
     * @param currentHeroEpoch the player's hero epoch right now
     */
    public static FreeTradeQuota.Result synchronizedState(PlayerAffinity relation, long currentHeroEpoch) {
        FreeTradeQuota.State state = stateOf(relation);
        FreeTradeQuota.Result result = FreeTradeQuota.applyObservedEvents(
                state, currentHeroEpoch, relation.getObservedCureEpoch());
        store(relation, result.state());
        return result;
    }

    /**
     * Record that a cure really happened, if this conversion has not already been recorded.
     *
     * <p><b>The player does not have to be online.</b> Nothing here looks a player up: the converter's UUID was
     * read from the converting entity, and only the fact is stored. Synchronising it into an allowance is a
     * separate, later step, which is what "offline only delays the sync, never the record" means.
     *
     * <p><b>The dedup marker is persisted</b> ({@code lastCureSourceId}), because the same conversion can be
     * observed more than once -- a repeated event, or a load in the middle of one -- and a cure that counted twice
     * would hand out a second allowance for one event.
     *
     * @param sourceId an id for THIS conversion (the converting entity's UUID); never the converted villager's id
     */
    public static CureFact recordCure(PlayerAffinity relation, String sourceId) {
        Objects.requireNonNull(relation, "relation");
        CureFactRules.Outcome outcome =
                CureFactRules.record(relation.getObservedCureEpoch(), relation.getLastCureSourceId(), sourceId);
        if (outcome.recorded()) {
            relation.setObservedCureEpoch(outcome.observedCureEpoch());
            relation.setLastCureSourceId(outcome.lastSourceId());
            // A relation that had never been initialised is not "migrated" any more: a real cure has been seen,
            // and the fact must survive. Without this, the first state read would take the cure as its migration
            // baseline and the event would be swallowed.
            if (!relation.isQuotaInitialized()) {
                store(relation, FreeTradeQuota.State.forNewRelation());
            }
        }
        return new CureFact(outcome.recorded(), outcome.reason(), relation.getObservedCureEpoch());
    }

    /** What happened when a cure was offered to a relation. */
    public record CureFact(boolean recorded, CureFactRules.Refusal refusal, long observedCureEpoch) {
    }
}
