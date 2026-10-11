package com.nonid.affinity.service;

import java.util.*;
import java.util.function.BiPredicate;

/** Runtime-scoped admission and terminal protection backed by the animation engine, never a TTL. */
public final class MatingSessionLifecycle<S, W> {
    private final GroupMatingSessions sessions;
    private final BiPredicate<S, W> belongs;
    private final BiPredicate<W, UUID> live;
    private final Map<UUID, W> running = new LinkedHashMap<>(), terminal = new LinkedHashMap<>();
    private final Set<UUID> pinned = new HashSet<>();
    private S owner;
    public MatingSessionLifecycle(GroupMatingSessions sessions, BiPredicate<S, W> belongs,
                                  BiPredicate<W, UUID> live) {
        this.sessions = sessions; this.belongs = belongs; this.live = live;
    }
    public void start(S server) { close(); owner = Objects.requireNonNull(server); }
    public boolean current(W world) { return owner != null && world != null && belongs.test(owner, world); }
    public boolean admissible(W world, UUID id) {
        return id != null && current(world) && !sessions.isHandled(id)
                && (!running.containsKey(id) || running.get(id) == world) && live.test(world, id);
    }
    public boolean begin(W world, UUID id) {
        if (!admissible(world, id)) return false;
        running.put(id, world); sessions.begin(id); return true;
    }
    public void recordOurStart(W world, UUID id, UUID player, UUID creature, boolean forced, long tick) {
        if (begin(world, id)) sessions.recordOurStart(id, player, creature, forced, tick);
    }
    public void observe(W world, UUID id, Collection<UUID> players, Collection<UUID> creatures, long tick) {
        if (running.get(id) == world && admissible(world, id)) sessions.observe(id, players, creatures, tick);
    }
    public Map<UUID, W> runningWorlds() { return Map.copyOf(running); }
    /** Null means rejected/reentrant; an empty batch still pins an unknown live terminal instance. */
    public List<MatingSessions.Session> take(W world, UUID id) {
        if (id == null || !current(world) || sessions.isHandled(id)
                || running.containsKey(id) && running.get(id) != world) return null;
        if (!running.containsKey(id) && !live.test(world, id)) return null;
        terminal.put(id, world); pinned.add(id);
        try { return sessions.takeAll(id); }
        catch (RuntimeException | Error failure) { pinned.remove(id); throw failure; }
        finally { running.remove(id); }
    }
    public void finish(UUID id) { pinned.remove(id); }
    public void discard(W world, UUID id) {
        var batch = take(world, id);
        if (batch == null) return;
        try { release(batch); } finally { finish(id); }
    }
    /** Try every outstanding feedback obligation while retaining the original failure. */
    public static void release(List<MatingSessions.Session> batch) {
        Throwable first = null;
        for (var session : batch) {
            try { session.settleEscapeFeedback(false); }
            catch (RuntimeException | Error failure) {
                if (first == null) first = failure; else if (first != failure) first.addSuppressed(failure);
            }
        }
        if (first instanceof RuntimeException failure) throw failure;
        if (first instanceof Error failure) throw failure;
    }
    public void sweep() {
        for (var entry : List.copyOf(terminal.entrySet())) {
            if (!pinned.contains(entry.getKey()) && !live.test(entry.getValue(), entry.getKey())) {
                sessions.releaseTerminal(entry.getKey()); terminal.remove(entry.getKey());
            }
        }
    }
    public int terminalCount() { return terminal.size(); }
    public int pinnedCount() { return pinned.size(); }
    public void close() {
        owner = null; // Close admission before feedback callbacks can reenter.
        try { sessions.clear(); }
        finally { running.clear(); terminal.clear(); pinned.clear(); }
    }
}
