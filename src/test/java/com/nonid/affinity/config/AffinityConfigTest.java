package com.nonid.affinity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Config sanitising tests. {@link AffinityConfig#sanitize()} is pure logic, so it can be
 * tested without a running game.
 */
class AffinityConfigTest {
    @Test
    void editorDraftDoesNotChangeLiveBirthOrRequestSettingsBeforeCommit() {
        AffinityConfig live = new AffinityConfig();
        live.friendThreshold = 60;
        live.maxAffinity = 200;
        live.requestRange = 6;
        AffinityConfig draft = live.copy();
        draft.friendThreshold = 30;
        draft.offspringBefriended = false;
        draft.requestRange = 12;
        draft.sanitize();
        assertEquals(60, live.friendThreshold);
        assertEquals(6, live.requestRange);
        assertTrue(live.offspringBefriended);
        assertEquals(200, draft.maxAffinity);
        assertEquals(30, draft.friendThreshold);
    }

    @Test
    void nonFiniteSettingsAreRepairedBeforeComparisonsOrJsonSaving() throws Exception {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            AffinityConfig config = new AffinityConfig();
            for (var field : AffinityConfig.class.getFields()) {
                if (field.getType() == double.class) field.setDouble(config, invalid);
            }
            config.sanitize();
            for (var field : AffinityConfig.class.getFields()) {
                if (field.getType() == double.class) assertTrue(Double.isFinite(field.getDouble(config)), field.getName());
            }
            assertEquals(new AffinityConfig().requestRange, config.copy().requestRange);
        }
    }

    @Test
    void defaultsAreAlreadySane() {
        AffinityConfig config = new AffinityConfig();
        config.sanitize();

        assertEquals(100, config.maxAffinity);
        assertEquals(0, config.minAffinity);
        assertEquals(75, config.friendThreshold);
        assertEquals(0.25, config.requestChance, 0.0001);
    }

    @Test
    void clampRespectsBounds() {
        AffinityConfig config = new AffinityConfig();
        config.minAffinity = 0;
        config.maxAffinity = 50;

        assertEquals(0, config.clamp(-10));
        assertEquals(25, config.clamp(25));
        assertEquals(50, config.clamp(999));
    }

    @Test
    void negativeAndOutOfRangeValuesAreRepaired() {
        AffinityConfig config = new AffinityConfig();
        config.maxAffinity = -5;
        config.minAffinity = 100;
        config.initialAffinity = 9999;
        config.friendThreshold = -20;
        config.affinityPerMating = -7;
        config.requestChance = 5.0;
        config.requestCheckIntervalTicks = 0;
        config.requestTimeoutTicks = 1;
        config.requestRange = 0.1;
        config.friendlyDefendRadius = -3.0;
        config.interactRange = 0.0;
        config.offspringAffinityPercentOfParent = 400;
        config.offspringParentSearchRadius = 0.0;
        config.affinityDecayPerDay = -1;

        config.sanitize();

        assertTrue(config.maxAffinity >= 1, "max affinity must stay positive");
        assertTrue(config.minAffinity < config.maxAffinity, "min must stay below max");
        assertTrue(config.initialAffinity >= config.minAffinity
                && config.initialAffinity <= config.maxAffinity, "initial affinity inside bounds");
        assertTrue(config.friendThreshold >= config.minAffinity
                && config.friendThreshold <= config.maxAffinity, "friend threshold inside bounds");
        assertEquals(0, config.affinityPerMating);
        assertEquals(1.0, config.requestChance, 0.0001);
        assertEquals(1, config.requestCheckIntervalTicks);
        assertEquals(20, config.requestTimeoutTicks);
        assertTrue(config.requestRange >= 1.0);
        assertTrue(config.friendlyDefendRadius >= 1.0);
        assertTrue(config.interactRange >= 1.0);
        assertEquals(100, config.offspringAffinityPercentOfParent);
        assertTrue(config.offspringParentSearchRadius >= 2.0);
        assertEquals(0, config.affinityDecayPerDay);
    }

    @Test
    void percentAndChanceAreBoundedBothWays() {
        AffinityConfig config = new AffinityConfig();
        config.requestChance = -2.0;
        config.offspringAffinityPercentOfParent = -50;
        config.sanitize();

        assertEquals(0.0, config.requestChance, 0.0001);
        assertEquals(0, config.offspringAffinityPercentOfParent);
    }
}

