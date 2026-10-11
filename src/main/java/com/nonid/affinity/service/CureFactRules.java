package com.nonid.affinity.service;

/**
 * Whether one observed conversion may be recorded as a cure for a relation, and why not.
 *
 * <p>Pure on purpose: this is the rule that decides whether a real cure counts, and the two ways it can be
 * recorded wrongly are both silent in a running game -- the same conversion observed twice (a second allowance
 * for one event) and a cure keyed on something that changes (an allowance that belongs to a different individual
 * after the next conversion). Neither needs a world to check.
 *
 * <p><b>What the source id is, and is not.</b> It identifies a CONVERSION, not a villager. The converted
 * villager's UUID is exactly the value that must not be used: villager -> zombie villager -> villager mints a new
 * one every time, so an id taken after the conversion cannot be compared with anything later. The converting
 * entity's UUID is stable for the one conversion it belongs to and different for the next one, which is what
 * deduplication needs.
 *
 * <p><b>The relation, not a global map, holds the marker.</b> The relation travels with the individual across
 * conversions (Fabric transfers the parent attachment), so the marker does too. A global table keyed by entity id
 * would be lost on precisely the conversion this feature cares about.
 */
public final class CureFactRules {

    /** Why a cure observation was refused. */
    public enum Refusal {
        /** No converting entity could be identified, so there is no id to deduplicate on. */
        NO_SOURCE,
        /** This exact conversion has already been recorded for this relation. */
        ALREADY_RECORDED,
        /** A converting entity existed but no player did the curing, so there is nobody to credit. */
        NO_CONVERTER
    }

    /** The outcome of offering one cure observation to a relation. */
    public record Outcome(boolean recorded, long observedCureEpoch, String lastSourceId, Refusal reason) {
    }

    private CureFactRules() {
    }

    /**
     * Decide the relation's new cure-fact fields.
     *
     * @param observedCureEpoch the relation's current pending-cure counter
     * @param lastSourceId      the conversion id already recorded, or empty
     * @param sourceId          the id of the conversion being recorded (the converting entity's UUID)
     */
    public static Outcome record(long observedCureEpoch, String lastSourceId, String sourceId) {
        long current = Math.max(0L, observedCureEpoch);
        String previous = lastSourceId == null ? "" : lastSourceId;
        if (sourceId == null || sourceId.isBlank()) {
            return new Outcome(false, current, previous, Refusal.NO_SOURCE);
        }
        if (sourceId.equals(previous)) {
            // Seen again: the same conversion, reported twice. Counting it again would hand out a second
            // allowance for one cure.
            return new Outcome(false, current, previous, Refusal.ALREADY_RECORDED);
        }
        return new Outcome(true, current + 1, sourceId, null);
    }

    /**
     * Whether a cure performed by this player may be credited to this relation at all.
     *
     * <p>A cure with no player behind it (an entity converted by something other than a player) is NOT a cure for
     * anyone's allowance. It is separated from {@link #record} so the caller can tell "nobody to credit" apart
     * from "already recorded" -- two very different log lines and two very different bugs.
     */
    public static Refusal refusalForConverter(String converterUuid) {
        return converterUuid == null || converterUuid.isBlank() ? Refusal.NO_CONVERTER : null;
    }
}
