package com.nonid.affinity.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.nonid.affinity.NonCreatureAffinity;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Mod configuration, stored as JSON at {@code config/noncreatureaffinity.json}.
 *
 * <p>All gameplay thresholds and gains live here so players can tune them freely.
 * This mod keeps its own file and never writes into the NeedsOfNature config,
 * so the two cannot overwrite each other.
 */
public final class AffinityConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CONFIG_VERSION = 1;

    // ------------------------------------------------------------------
    // Affinity basics
    // ------------------------------------------------------------------

    /** Maximum affinity of a single relation. */
    public int maxAffinity = 100;
    /** Minimum affinity of a single relation. */
    public int minAffinity = 0;
    /** Affinity used when a relation is created for the first time. */
    public int initialAffinity = 0;

    // ------------------------------------------------------------------
    // Affinity gains and losses
    // ------------------------------------------------------------------

    /** Affinity gained after one completed mating. */
    public int affinityPerMating = 10;
    /** Server-owned multiplier for subsequent positive hidden relationship bond gains only. */
    public int bondGrowthPercent = 100;
    /** Extra bonus when the player started the mating themselves. */
    public int affinityBonusPlayerInitiated = 3;
    /** Affinity lost when the player rejects a request. */
    public int affinityLossOnReject = 15;

    /**
     * Affinity lost when a request is ignored for its whole window.
     *
     * <p>Charged only on a real timeout. An acceptance that failed because the creature was too far
     * away, had no animation, or did not meet a condition is deliberately NOT charged.
     */
    public int affinityLossOnTimeout = 10;
    /** Affinity lost per in-game day without interaction (0 disables decay). */
    public int affinityDecayPerDay = 0;

    // ------------------------------------------------------------------
    // Villager trade benefits
    // ------------------------------------------------------------------

    /**
     * Largest trade discount an adult villager may give, as a percentage of the offer's own base price.
     *
     * <p>Reached at the friend threshold and scaled linearly below it (affinity / threshold), so half the
     * threshold gives half of this. Bounded 0-100: 100 means a friend's price can fall to the minimum of one
     * item, and 0 disables the affinity discount entirely.
     *
     * <p>Deliberately a percentage of an offer's BASE price, and applied ON TOP of whatever vanilla already
     * computed (gossip, curing a zombie villager, hero of the village, demand): vanilla keeps its own figure in
     * the offer's special price, this module adds its own on top, and each is undone separately. That way the
     * two stack instead of overwriting each other, and a discount can never be applied twice.
     */
    public int villagerDiscountMaxPercent = 20;

    // ------------------------------------------------------------------
    // Friend status and taming
    // ------------------------------------------------------------------

    /** Affinity required to become a friend. */
    public int friendThreshold = 75;
    /** Current affinity needed to defend a player who is attacked. */
    public int familiarThreshold = 25;
    /** Current affinity needed to use companion commands and following. */
    public int controlThreshold = 50;
    /** Automatically tame tameable creatures when they become friends. */
    public boolean autoTameOnFriend = true;
    /** Bind the taming owner to the player who triggered it. */
    public boolean tameOwnerIsTriggerPlayer = true;
    /** Friendly (non-tameable) creatures attack the player's enemies. */
    public boolean friendlyMobsDefendPlayer = true;
    /** Radius used by friendly creatures to look for threats. */
    public double friendlyDefendRadius = 12.0;
    /** Damage multiplier friendly creatures apply (1.0 = vanilla). */
    public double friendlyAttackDamageMultiplier = 1.0;

    // ------------------------------------------------------------------
    // Requests
    // ------------------------------------------------------------------

    /** Arousal required before a friendly creature may ask for mating. */
    public int requestEnergyThreshold = 150;
    /** Legacy serialized field only since 1.4.15; requests follow familiarThreshold. Retained for old config files. */
    public int requestAffinityThreshold = 60;
    /** Legacy scan configuration retained for file compatibility; no invitation scheduler uses it. */
    public int requestCheckIntervalTicks = 100;
    /** Legacy scan probability retained; native NoN decides when to attempt an interaction. */
    public double requestChance = 0.25;
    /** Legacy scan range retained; native approach is NoN-owned and answers retain their 16-block limit. */
    public double requestRange = 8.0;
    /** How long a request waits for an answer, in ticks. */
    public int requestTimeoutTicks = 600;
    /** Cooldown after a request was answered or expired, in ticks. */
    public int requestCooldownTicks = 300;

    // ------------------------------------------------------------------
    // Rejection punishment
    // ------------------------------------------------------------------

    /** Trigger one forced mating after a rejected request. */
    public boolean forceMatingOnReject = true;
    /**
     * Arousal needed, as a fraction of the creature's own maximum, before a rejection leads to a
     * forced mating. Below this the creature simply goes on cooldown.
     *
     * <p>Expressed as a ratio because different creatures can have different maximum arousal.
     */
    public double forcedMatingEnergyRatio = 0.75;
    /** Legacy field retained; requests now use the per-pair requestCooldownTicks after rejection. */
    public int rejectionArousalCooldownTicks = 1200;
    /** Let the player escape a forced mating (kept for player agency). */
    public boolean forcedMatingEscapable = true;
    /**
     * Extra affinity lost when the player actually breaks out of a FORCED mating.
     *
     * <p>On top of the rejection penalty, which was already charged when the request was turned down: this is
     * the cost of having to fight the creature off. 0 disables it, which restores the older behaviour where an
     * escape only forfeited the reward. Never stacked twice for one animation instance, and never applied to a
     * mating the player started voluntarily.
     */
    public int affinityLossOnEscape = 25;
    /**
     * Start a forced mating as a NON mob attack, so the player gets NON's own A/D struggle screen with real
     * progress instead of having to attack (which is not possible while an animation runs).
     *
     * <p>NeedsOfNature only sends that screen for animations started with an attack context, so this works by
     * telling NON that the creature is attacking the player (the same shape NON itself uses for mob attacks).
     * Consequences, confirmed from NON's source rather than assumed: the escape may cost health (NON's escape
     * damage), grants its short post-escape invulnerability, is counted in its escape statistics, may make the
     * creature re-engage (NON's post-escape reattack), enables its Give Up chain, and -- when NON's
     * destroyed-skin feature is on -- accumulates destroyed-skin damage on the player at animation start.
     *
     * <p><b>These consequences were reviewed and accepted on 2026-09-22</b>, so the default stays on. Turning
     * this off falls back to a plain mating animation where the only way out is this module's own break-free
     * key, at exactly the same affinity cost.
     */
    public boolean nonStruggleScreen = true;
    /** Grace period before a forced mating starts, in ticks. */
    public int forcedMatingGraceTicks = 60;

    // ------------------------------------------------------------------
    // Offspring
    // ------------------------------------------------------------------

    /** Offspring starting affinity as a percentage of the parent's affinity. */
    public int offspringAffinityPercentOfParent = 100;
    /** Offspring become friends/tamed automatically. */
    public boolean offspringBefriended = true;
    /** Offspring generation is parent generation + 1. */
    public boolean offspringInheritGeneration = true;
    /** Radius used to look for the parent of a newborn creature. */
    public double offspringParentSearchRadius = 16.0;

    // ------------------------------------------------------------------
    // Interaction and client
    // ------------------------------------------------------------------

    /** Search radius of the request key. */
    public double interactRange = 6.0;
    /** Show the affinity HUD. */
    public boolean hudEnabled = true;
    /** Local playback preference; does not affect other players or resurrection visuals. */
    public boolean reunionReviveSound = true;
    /** Notify the player when affinity changes. */
    public boolean notifyOnAffinityChange = true;
    /** Also build relations with creatures tamed through vanilla means. */
    public boolean allowVanillaTamedCreatures = true;

    // ------------------------------------------------------------------
    // Debug
    // ------------------------------------------------------------------

    /** Verbose logging. */
    public boolean debugLogging = false;
    // ---- companion following ----

    /** Speed handed to the creature's own navigation while following. */
    public double companionFollowSpeed = 1.0;

    /** Allow flying companions to recall into clear air behind their owner. */
    public boolean flyingCompanionRecall = true;

    /** Prevent attributable player damage to that player's non-hostile friends. */
    public boolean preventFriendDamage = false;

    /** Distance at which a following companion starts moving toward its owner. */
    public double companionFollowStartDistance = 6.0;

    /** Distance at which a following companion stops again. */
    public double companionFollowStopDistance = 3.0;

    /** How often companion movement is re-evaluated, in ticks. */
    public int companionFollowIntervalTicks = 10;

    /** How far a WAIT companion may stray from its anchor before being steered back. */
    public double companionWaitRadius = 4.0;

    /**
     * There is deliberately NO wander radius.
     *
     * <p>WANDER hands movement entirely to the creature's own AI and is never pulled back to an
     * anchor, so a radius for it would be an option with no behaviour behind it. One existed and was
     * editable in the config screen while doing nothing; it was removed rather than left as a control
     * that lies about the game.
     */

    /** How long a recorded combat fact may be used, in server ticks. */
    public long companionEventMaxAgeTicks = 100L;

    /**
     * Distance from the owner beyond which a companion breaks off and returns.
     *
     * <p>The user's rule is ten blocks, and it outranks continuing a chase.
     */
    public double companionRecallDistance = 10.0;

    /**
     * How long a companion may make no progress before an engagement is abandoned.
     *
     * <p>Guards against chasing an unreachable target forever. It is NOT a cap on how long a fight
     * may last: an engagement that keeps closing distance continues indefinitely.
     */
    public long companionEngageStallTicks = 200L;

    /**
     * How long a target abandoned because the fight STALLED stays off limits.
     *
     * <p>Required for the same reason as the recall window, with one extra twist: an unreachable enemy
     * keeps being republished by the hostility scan, so a window that new facts could override would
     * be undone on the very next scan and the companion would walk back into the same dead fight.
     */
    public long companionStallSuppressTicks = 200L;

    /**
     * How long a target abandoned by a recall stays off limits.
     *
     * <p>Required to break the chase-recall loop: without it the next scan re-selects the same enemy
     * and the recall achieves nothing.
     */
    public long companionReleaseSuppressTicks = 200L;

    /** How long the companion keeps travelling back before it may engage anything new. */
    public long companionReturnTicks = 60L;

    /**
     * Extra margin required to START a fight, on top of the recall distance.
     *
     * <p>Re-entry is stricter than exit: a target already being fought is released beyond the recall
     * distance, but starting a fresh fight requires being inside the distance minus this margin.
     * Without the gap a target sitting on the boundary is dropped and re-acquired every round.
     */
    public double companionReengageMargin = 1.0;


    /** How far from the owner (or itself, for self-defence) a companion will respond. */
    public double companionResponseRange = 16.0;

    /**
     * Reach within which a WAIT companion may answer an attacker without moving.
     *
     * <p>Measured from where it stands; if the attacker is outside this, WAIT does not attack and
     * must not walk toward it.
     */
    public double companionStationaryReach = 3.0;

    /** Radius around the owner in which an ATTACK companion looks for threats. */
    public double companionAttackRadius = 16.0;

    /**
     * Distance from the owner beyond which an ATTACK companion gives up the chase.
     *
     * <p>Without this the companion would follow a fleeing target indefinitely and leave the owner
     * behind.
     */
    public double companionAttackLeashRadius = 24.0;

    /** Show affinity in creature name tags (debug only). */
    public boolean debugShowAffinityInName = false;

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    private transient int loadedVersion = CONFIG_VERSION;

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("noncreatureaffinity.json");
    }

    public static AffinityConfig load() {
        return load(configPath());
    }

    static AffinityConfig load(Path path) {
        return load(path, new AffinityConfig());
    }

    public static AffinityConfig loadOrKeep(AffinityConfig current) {
        return load(configPath(), current);
    }

    static AffinityConfig load(Path path, AffinityConfig fallback) {
        AffinityConfig config;
        if (!Files.notExists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, AffinityConfig.class);
                if (config == null) throw new JsonSyntaxException("Expected a configuration object");
            } catch (JsonSyntaxException exception) {
                NonCreatureAffinity.LOGGER.error("[NoN Affinity] invalid config: {}", exception.toString());
                config = new AffinityConfig();
                if (!backupBroken(path)) return fallback;
                config.trySave(path);
                return config;
            } catch (IOException exception) {
                // A locked/unreadable file is not evidence of corrupt JSON. Never move or replace it.
                NonCreatureAffinity.LOGGER.error(
                        "[NoN Affinity] cannot read config; keeping fallback settings without changing the file: {}",
                        exception.toString());
                return fallback;
            }
            config.sanitize();
        } else {
            config = new AffinityConfig();
        }
        // The reader is closed before replacing its file (important on Windows).
        config.trySave(path);
        return config;
    }

    private static boolean backupBroken(Path path) {
        try {
            Path backup = path.resolveSibling(
                    path.getFileName() + ".invalid-" + System.currentTimeMillis() + ".json");
            Files.move(path, backup);
            NonCreatureAffinity.LOGGER.warn("[NoN Affinity] broken config backed up as {}",
                    backup.getFileName());
            return true;
        } catch (IOException exception) {
            NonCreatureAffinity.LOGGER.error(
                    "[NoN Affinity] cannot back up invalid config; preserving original file: {}",
                    exception.toString());
            return false;
        }
    }

    /** Detached editor draft; modifying it must never mutate the running server's configuration. */
    public AffinityConfig copy() {
        return GSON.fromJson(GSON.toJson(this), AffinityConfig.class);
    }

    /** Kept for compatibility; callers requiring confirmation should use trySave(). */
    public void save() {
        trySave();
    }

    public boolean trySave() {
        return trySave(configPath());
    }

    boolean trySave(Path path) {
        try {
            // Serialize first, so even serialization failure cannot touch the destination.
            AtomicConfigFile.write(path, GSON.toJson(this));
            return true;
        } catch (IOException | RuntimeException exception) {
            NonCreatureAffinity.LOGGER.error("[NoN Affinity] failed to save config: {}",
                    exception.toString());
            return false;
        }
    }
    /** Clamp all values into sane ranges after loading. */
    public void sanitize() {
        bondGrowthPercent = Math.max(25, Math.min(400, bondGrowthPercent));
        // Math.min/max do not repair NaN; a non-finite range can disable distance comparisons.
        AffinityConfig defaults = new AffinityConfig();
        for (java.lang.reflect.Field field : AffinityConfig.class.getFields()) {
            if (field.getType() != double.class || java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            try {
                if (!Double.isFinite(field.getDouble(this))) field.setDouble(this, field.getDouble(defaults));
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Public configuration field is inaccessible", exception);
            }
        }
        this.maxAffinity = Math.max(1, this.maxAffinity);
        this.minAffinity = Math.min(0, Math.min(this.minAffinity, this.maxAffinity - 1));
        this.initialAffinity = clamp(this.initialAffinity);
        // Zero affinity always means first meeting; relationship permissions require positive affinity.
        this.friendThreshold = Math.max(1, clamp(this.friendThreshold));
        this.controlThreshold = Math.max(1, Math.min(this.controlThreshold, Math.max(1, this.friendThreshold)));
        this.familiarThreshold = Math.max(1, Math.min(this.familiarThreshold, this.controlThreshold));
        this.requestAffinityThreshold = clamp(this.requestAffinityThreshold);
        this.offspringAffinityPercentOfParent =
                Math.max(0, Math.min(100, this.offspringAffinityPercentOfParent));
        this.offspringParentSearchRadius = Math.max(2.0, this.offspringParentSearchRadius);

        // A gain larger than the whole range just slams the value to the cap on the first
        // mating, after which every further mating grants nothing.
        this.affinityPerMating = Math.max(0, Math.min(this.affinityPerMating, this.maxAffinity));
        this.affinityBonusPlayerInitiated = Math.max(0, this.affinityBonusPlayerInitiated);
        this.affinityLossOnReject = Math.max(0, this.affinityLossOnReject);
        this.affinityLossOnEscape = Math.max(0, this.affinityLossOnEscape);
        this.affinityLossOnTimeout = Math.max(0, this.affinityLossOnTimeout);
        this.affinityDecayPerDay = Math.max(0, this.affinityDecayPerDay);
        this.villagerDiscountMaxPercent =
                Math.max(0, Math.min(100, this.villagerDiscountMaxPercent));

        this.requestEnergyThreshold = Math.max(0, this.requestEnergyThreshold);
        this.requestCheckIntervalTicks = Math.max(1, this.requestCheckIntervalTicks);
        this.requestChance = Math.min(1.0, Math.max(0.0, this.requestChance));
        this.requestRange = Math.max(1.0, this.requestRange);
        this.requestTimeoutTicks = Math.max(20, this.requestTimeoutTicks);
        this.requestCooldownTicks = Math.max(0, this.requestCooldownTicks);

        this.forcedMatingGraceTicks = Math.max(0, this.forcedMatingGraceTicks);
        this.forcedMatingEnergyRatio = Math.min(1.0, Math.max(0.0, this.forcedMatingEnergyRatio));
        this.rejectionArousalCooldownTicks = Math.max(0, this.rejectionArousalCooldownTicks);
        this.companionFollowSpeed = clampDouble(this.companionFollowSpeed, 0.1, 3.0);
        this.companionFollowStartDistance = clampDouble(this.companionFollowStartDistance, 1.0, 64.0);
        this.companionFollowStopDistance = clampDouble(this.companionFollowStopDistance, 0.5, 32.0);
        // The stop distance must stay below the start distance, otherwise the creature jitters.
        if (this.companionFollowStopDistance >= this.companionFollowStartDistance) {
            this.companionFollowStopDistance = Math.max(0.5, this.companionFollowStartDistance - 1.0);
        }
        this.companionFollowIntervalTicks = Math.max(1, Math.min(this.companionFollowIntervalTicks, 200));
        this.companionWaitRadius = clampDouble(this.companionWaitRadius, 1.0, 32.0);
        this.companionEventMaxAgeTicks = Math.max(20L, Math.min(this.companionEventMaxAgeTicks, 1200L));
        this.companionRecallDistance = clampDouble(this.companionRecallDistance, 2.0, 32.0);
        this.companionEngageStallTicks = Math.max(20L, Math.min(this.companionEngageStallTicks, 2400L));
        this.companionStallSuppressTicks =
                Math.max(20L, Math.min(this.companionStallSuppressTicks, 2400L));
        this.companionReleaseSuppressTicks =
                Math.max(20L, Math.min(this.companionReleaseSuppressTicks, 2400L));
        this.companionReturnTicks = Math.max(0L, Math.min(this.companionReturnTicks, 1200L));
        this.companionReengageMargin = clampDouble(this.companionReengageMargin, 0.0, 5.0);
        this.companionResponseRange = clampDouble(this.companionResponseRange, 4.0, 64.0);
        this.companionStationaryReach = clampDouble(this.companionStationaryReach, 1.0, 12.0);
        this.companionAttackRadius = clampDouble(this.companionAttackRadius, 4.0, 64.0);
        this.companionAttackLeashRadius =
                clampDouble(this.companionAttackLeashRadius, 4.0, 128.0);
        // The chase limit must not be tighter than the search radius, or a threat found at the edge
        // would be abandoned immediately.
        if (this.companionAttackLeashRadius < this.companionAttackRadius) {
            this.companionAttackLeashRadius = this.companionAttackRadius;
        }
        this.friendlyDefendRadius = Math.max(1.0, this.friendlyDefendRadius);
        this.friendlyAttackDamageMultiplier = Math.max(0.0, this.friendlyAttackDamageMultiplier);
        this.interactRange = Math.max(1.0, this.interactRange);
    }

    public int clamp(int value) {
        return Math.max(this.minAffinity, Math.min(this.maxAffinity, value));
    }

    /** Clamp a double into the inclusive range. */
    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public int getLoadedVersion() {
        return this.loadedVersion;
    }
}
