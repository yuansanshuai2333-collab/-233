package com.nonid.affinity.data;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.net.CompanionRecord;
import java.util.*;
import net.minecraft.nbt.NbtCompound;

/** Derived directory only. Removing a directory row never removes an entity's relationship. */
public final class CompanionDirectoryData {
    private final Map<UUID,NavigableMap<String,CompanionRecord>> players=new HashMap<>();
    private final Map<String,Set<UUID>> viewers=new HashMap<>();
    public CompanionRecord get(UUID player,String id) { var rows=players.get(player);return rows==null?null:rows.get(id); }
    public List<String> positiveIds(UUID player) {
        var rows=players.get(player);return rows==null?List.of():rows.values().stream().filter(r->r.info().affinity()>=NonCreatureAffinity.getConfig().familiarThreshold).map(CompanionRecord::id).toList();
    }
    public void put(UUID player,CompanionRecord row) {
        players.computeIfAbsent(player,k->new TreeMap<>()).put(row.id(),row);
        viewers.computeIfAbsent(row.id(),k->new HashSet<>()).add(player);
    }
    public Set<UUID> viewers(String id) { return Set.copyOf(viewers.getOrDefault(id,Set.of())); }
    public void remove(UUID player,String id) {
        var rows=players.get(player);if(rows!=null){rows.remove(id);if(rows.isEmpty())players.remove(player);}
        var set=viewers.get(id);if(set!=null){set.remove(player);if(set.isEmpty())viewers.remove(id);}
    }
    public NbtCompound toNbt() {
        var root=new NbtCompound();root.putInt("schema",1);var users=new NbtCompound();
        players.forEach((player,rows)->{var saved=new NbtCompound();rows.forEach((id,row)->saved.put(id,row.toNbt()));users.put(player.toString(),saved);});
        root.put("players",users);return root;
    }
    public static CompanionDirectoryData fromNbt(NbtCompound n) {
        var result=new CompanionDirectoryData();var users=n.getCompoundOrEmpty("players");int invalid=0;
        for(var key:users.getKeys())try {
            var player=UUID.fromString(key);var rows=users.getCompoundOrEmpty(key);
            for(var id:rows.getKeys())try {
                var row=CompanionRecord.fromNbt(rows.getCompoundOrEmpty(id));
                if(!id.equals(row.id())||id.length()>80||row.info().query().dimension().length()>256)throw new IllegalArgumentException("invalid directory identity");
                // A restart cannot prove an entity is currently loaded or alive.
                result.put(player,row.historical(row.availability().equals("dead")?"dead":"historical"));
            } catch(IllegalArgumentException bad){invalid++;}
        }catch(IllegalArgumentException bad){invalid++;}
        if(invalid>0)NonCreatureAffinity.LOGGER.warn("Ignored {} malformed companion directory records; entity relationships were not modified",invalid);
        return result;
    }
}
