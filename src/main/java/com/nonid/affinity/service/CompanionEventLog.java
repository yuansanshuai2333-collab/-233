package com.nonid.affinity.service;

import com.nonid.affinity.service.policy.CompanionCombatPolicy.Evidence;
import com.nonid.affinity.service.policy.CompanionCombatPolicy.Trigger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side record of combat facts that the pure policy consumes.
 *
 * <p>Damage callbacks only record here; they never set targets or start navigation. The controller
 * reads these facts, asks the policy, and writes at most one target per creature per round.
 *
 * <p>Facts are keyed by the player they concern, so several companions bonded to the same owner each
 * consume the same event independently -- one companion reading its record must not remove it for the
 * others. A companion decides how much it has already seen through its own mode-entry watermark, not
 * by draining a shared queue.
 *
 * <p>Sequence numbers are monotonic within one server session and start over on restart, so events
 * from a previous session can never satisfy a new watermark.
 */
public final class CompanionEventLog {

    /** How many facts are retained per player before the oldest are dropped. */
    private static final int MAX_FACTS_PER_PLAYER = 64;

    /** Monotonic within a session. Restarting the server resets it, which is intended. */
    private static final AtomicLong SEQUENCE = new AtomicLong();

    /** facts about one player, oldest first. */
    private static final Map<UUID, List<Evidence>> OWNER_FACTS = new ConcurrentHashMap<>();

    /** facts about one companion's own self-defence, oldest first. */
    private static final Map<UUID, List<Evidence>> SELF_FACTS = new ConcurrentHashMap<>();

    private CompanionEventLog() {
    }

    /** The next sequence number; also used to stamp a mode entry. */
    public static long nextSequence() {
        return SEQUENCE.incrementAndGet();
    }

    /** The current watermark without consuming a number. */
    public static long peekSequence() {
        return SEQUENCE.get();
    }

    /** Someone attacked the owner. */
    public static void recordOwnerHurt(ServerPlayerEntity owner, LivingEntity attacker,
                                       boolean hostileBeforeAttack) {
        add(OWNER_FACTS, owner.getUuid(), new Evidence(Trigger.OWNER_HURT, owner.getUuid(),
                attacker.getUuid(), worldId(owner), owner.getEntityWorld().getTime(),
                nextSequence(), hostileBeforeAttack, false));
    }

    /** The owner attacked something. */
    public static void recordOwnerAttack(ServerPlayerEntity owner, LivingEntity target,
                                         boolean hostileBeforeAttack) {
        add(OWNER_FACTS, owner.getUuid(), new Evidence(Trigger.OWNER_ATTACK, owner.getUuid(),
                target.getUuid(), worldId(owner), owner.getEntityWorld().getTime(),
                nextSequence(), hostileBeforeAttack, false));
    }

    /** A companion was hurt; principal is the companion, so it still applies when the owner is away. */
    public static void recordSelfDefence(MobEntity companion, LivingEntity attacker) {
        add(SELF_FACTS, companion.getUuid(), new Evidence(Trigger.SELF_DEFENCE, companion.getUuid(),
                attacker.getUuid(), worldId(companion), companion.getEntityWorld().getTime(),
                nextSequence(), true, false));
    }

    /**
     * A fresh hostility scan: the target is hostile to the owner right now.
     *
     * <p>Recorded as a new fact rather than reusing an old hit, because the policy treats
     * "currently hostile" as a live observation with its own validity window.
     */
    public static void recordHostileToOwner(ServerPlayerEntity owner, LivingEntity target) {
        add(OWNER_FACTS, owner.getUuid(), new Evidence(Trigger.HOSTILE_TO_OWNER, owner.getUuid(),
                target.getUuid(), worldId(owner), owner.getEntityWorld().getTime(),
                nextSequence(), true, true));
    }

    /** Owner-scoped facts, newest last. Never mutated by the caller. */
    public static List<Evidence> ownerFacts(UUID ownerId) {
        List<Evidence> facts = OWNER_FACTS.get(ownerId);
        return facts == null ? List.of() : List.copyOf(facts);
    }

    /**
     * Record a hostile-to-owner observation without needing live entities.
     *
     * <p>The four public record methods all take a Minecraft entity purely to read its id, world and
     * clock. This one takes those values directly, so the STORE's own contract -- one shared list per
     * owner, bounded retention, pruning by age -- can be tested. It is the same helper those four use,
     * so what the test exercises is what production stores, not a parallel path.
     *
     * @return the sequence number the fact was stamped with
     */
    static long recordHostileToOwnerForTest(UUID ownerId, UUID targetId, String worldId, long tick) {
        long sequence = nextSequence();
        add(OWNER_FACTS, ownerId, new Evidence(Trigger.HOSTILE_TO_OWNER, ownerId, targetId, worldId,
                tick, sequence, true, true));
        return sequence;
    }

    /** Self-defence facts for one companion, newest last. */
    public static List<Evidence> selfFacts(UUID companionId) {
        List<Evidence> facts = SELF_FACTS.get(companionId);
        return facts == null ? List.of() : List.copyOf(facts);
    }

    /** Expire this world's facts using its own clock; other dimensions may tick independently. */
    public static void prune(String worldId, long nowTick, long maxAgeTicks) {
        java.util.Objects.requireNonNull(worldId);
        pruneMap(OWNER_FACTS, worldId, nowTick, maxAgeTicks);
        pruneMap(SELF_FACTS, worldId, nowTick, maxAgeTicks);
    }

    /** Forget everything about one creature or player, used on unbind and death. */
    public static void forget(UUID id) {
        OWNER_FACTS.remove(id);
        SELF_FACTS.remove(id);
    }

    /** Full reset; only for a server stop. */
    public static void clear() {
        OWNER_FACTS.clear();
        SELF_FACTS.clear();
        SEQUENCE.set(0L);
    }

    private static void add(Map<UUID, List<Evidence>> into, UUID key, Evidence evidence) {
        // Facts for a world that is already gone are pointless, but the dimension check happens in
        // the policy; here the only job is bounded retention.
        List<Evidence> list = into.computeIfAbsent(key, k -> new ArrayList<>());
        synchronized (list) {
            list.add(evidence);
            while (list.size() > MAX_FACTS_PER_PLAYER) {
                list.remove(0);
            }
        }
    }

    private static void pruneMap(Map<UUID, List<Evidence>> map, String worldId,
                                 long nowTick, long maxAgeTicks) {
        map.entrySet().removeIf(entry -> {
            List<Evidence> list = entry.getValue();
            synchronized (list) {
                list.removeIf(e -> worldId.equals(e.worldId()) && nowTick - e.tick() > maxAgeTicks);
                return list.isEmpty();
            }
        });
    }

    @Nullable
    private static String worldId(net.minecraft.entity.Entity entity) {
        return entity.getEntityWorld().getRegistryKey().getValue().toString();
    }
}
