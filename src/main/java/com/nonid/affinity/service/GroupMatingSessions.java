package com.nonid.affinity.service;

import java.util.*;

/** One existing settlement record per real player/creature pair within an observed instance.
 * A terminal instance is consumed atomically before any pair is paid. No world or reward writes here. */
public final class GroupMatingSessions {
    private record Pair(UUID player, UUID creature) {}
    private final Map<UUID, LinkedHashMap<Pair, MatingSessions>> running = new LinkedHashMap<>();
    private final Set<UUID> handled = new HashSet<>();

    public boolean isHandled(UUID id) { return handled.contains(id); }
    /** Only the engine-scoped lifecycle may release a terminal after its pin and live instance are gone. */
    public void releaseTerminal(UUID id) {
        if (running.containsKey(id)) throw new IllegalStateException("Cannot release a running instance");
        handled.remove(id);
    }
    public Set<UUID> runningIds() { return Set.copyOf(running.keySet()); }
    public void begin(UUID id) { if(id!=null && !isHandled(id)) running.computeIfAbsent(id,k->new LinkedHashMap<>()); }
    public void open(UUID id, UUID player, UUID creature, boolean ours, boolean forced, long tick) {
        if(id==null || player==null || creature==null || isHandled(id))return;
        begin(id);
        running.get(id).computeIfAbsent(new Pair(player,creature),k->new MatingSessions()).open(id,player,creature,ours,forced,tick);
    }
    public void recordOurStart(UUID id,UUID player,UUID creature,boolean forced,long tick) {
        open(id,player,creature,true,forced,tick);
        var pairs=running.get(id);
        if(pairs!=null) pairs.get(new Pair(player,creature)).recordOurStart(id,player,creature,forced,tick);
    }
    /** Only an already observed real instance may gain members. Departed pairs remain ineligible even if re-added. */
    public void observe(UUID id, Collection<UUID> players, Collection<UUID> creatures, long tick) {
        var pairs=running.get(id); if(pairs==null || isHandled(id))return;
        var present=new HashSet<Pair>();
        for(UUID p:players) for(UUID c:creatures) if(p!=null && c!=null)present.add(new Pair(p,c));
        for(var entry:pairs.entrySet()) if(!present.contains(entry.getKey()))
            entry.getValue().markInterrupted(entry.getKey().player,entry.getKey().creature);
        for(Pair pair:present) open(id,pair.player,pair.creature,false,false,tick);
    }
    public List<MatingSessions.Session> takeAll(UUID id) {
        if(id==null || !handled.add(id))return List.of();
        var pairs=running.remove(id); if(pairs==null)return List.of();
        var result=new ArrayList<MatingSessions.Session>();
        for(var tracker:pairs.values()) { var session=tracker.take(id); if(session!=null)result.add(session); }
        return List.copyOf(result);
    }
    public int markPlayerEscape(UUID player,UUID creature) {
        int marked=0; for(var pairs:running.values()) for(var tracker:pairs.values())marked+=tracker.markPlayerEscape(player,creature);
        return marked;
    }
    public boolean markPlayerEscapeForInstance(UUID id, UUID player) {
        var pairs=running.get(id);if(pairs==null)return false;
        boolean marked=false;
        for(var entry:pairs.entrySet()) if(player!=null && entry.getKey().player.equals(player))
            marked|=entry.getValue().markPlayerEscapeForInstance(id);
        return marked;
    }
    /** Legacy identity-less signal: only a single pair has an unambiguous escaping player. */
    public boolean markPlayerEscapeForInstance(UUID id) {
        var pairs=running.get(id);if(pairs==null)return false;
        if(pairs.size()==1)return pairs.values().iterator().next().markPlayerEscapeForInstance(id);
        for(var e:pairs.entrySet())e.getValue().markInterrupted(e.getKey().player,e.getKey().creature);
        return !pairs.isEmpty();
    }
    public MatingSessions.Session findForcedForPlayer(UUID player) {
        for(var pairs:running.values())for(var tracker:pairs.values()) {var s=tracker.findForcedForPlayer(player);if(s!=null && !s.interrupted())return s;}
        return null;
    }
    public MatingSessions.Session findActivePair(UUID player,UUID creature) {
        for(var pairs:running.values()) {var tracker=pairs.get(new Pair(player,creature));if(tracker!=null){var s=tracker.findActivePair(player,creature);if(s!=null)return s;}}
        return null;
    }
    public boolean hasForcedPair(UUID player, UUID creature) {
        for (var pairs : running.values()) {
            var tracker = pairs.get(new Pair(player, creature));
            var session = tracker == null ? null : tracker.findActivePair(player, creature);
            if (session != null && session.forced()) return true;
        }
        return false;
    }
    public List<MatingSessions.Session> escapeChargeCandidates(UUID player, UUID creature) {
        var result = new ArrayList<MatingSessions.Session>();
        for (var pairs : running.values()) {
            var tracker = pairs.get(new Pair(player, creature));
            var session = tracker == null ? null : tracker.findUnescapedForcedPair(player, creature);
            if (session != null && session.forced()) result.add(session);
        }
        return List.copyOf(result);
    }
    public void discard(UUID id) { for (var session : takeAll(id)) session.settleEscapeFeedback(false); }
    public void clear() {
        try {
            var batch = new ArrayList<MatingSessions.Session>();
            for (var id : List.copyOf(running.keySet())) batch.addAll(takeAll(id));
            MatingSessionLifecycle.release(batch);
        } finally { running.clear(); handled.clear(); }
    }
}
