package com.nonid.affinity.service;

import com.nonid.affinity.data.*;
import com.nonid.affinity.net.*;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.block.entity.CreakingHeartBlockEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.CreakingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/** Background observations of naturally loaded entities; queries never load chunks or write data. */
public final class CompanionDirectory {
    public static final AttachmentType<CompanionDirectoryData> DATA=AttachmentRegistry.create(
            Identifier.of("noncreatureaffinity","companion_directory"),b->b.initializer(CompanionDirectoryData::new)
                    .persistent(NbtCompoundCodec.of(CompanionDirectoryData::toNbt,CompanionDirectoryData::fromNbt)));
    private static final Map<UUID,LivingEntity> LOADED=new HashMap<>();
    private static final Map<UUID,String> IDENTITIES=new HashMap<>();
    private static final ArrayDeque<UUID> QUEUE=new ArrayDeque<>();
    private static final Set<UUID> QUEUED=new HashSet<>();
    private static final UUID ZERO=new UUID(0,0);
    private CompanionDirectory() {}
    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((e,w)->{
            if(e instanceof LivingEntity living && !(e instanceof PlayerEntity)) {
                if(QUEUED.add(e.getUuid()))QUEUE.add(e.getUuid());
                LOADED.put(e.getUuid(),living);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((e,w)->{
            if(e instanceof LivingEntity living && LOADED.get(e.getUuid())==e) {
                observe(living,"historical");LOADED.remove(e.getUuid());IDENTITIES.remove(e.getUuid());
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((e,source)->{
            if(!(e instanceof PlayerEntity))observe(e,e instanceof CreakingEntity c&&c.getHomePos()!=null?"historical":"dead");
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            // Bound the work per tick, independent of how often somebody opens or pages the UI.
            int budget=Math.min(64,QUEUE.size());
            while(budget-->0){var id=QUEUE.removeFirst();var e=LOADED.get(id);if(e==null){QUEUED.remove(id);continue;}
                QUEUE.addLast(id);if(e.isAlive()&&!e.isRemoved())observe(e,"historical");}
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{LOADED.clear();IDENTITIES.clear();QUEUE.clear();QUEUED.clear();});
    }
    public static CompanionDirectoryData peek(MinecraftServer server) { return server.getOverworld().getAttached(DATA); }
    public static String identityOf(LivingEntity entity) { return identity(entity,false); }
    private static String identity(LivingEntity entity, boolean create) {
        if(entity instanceof CreakingEntity c && c.getHomePos()!=null) {
            var world=c.getEntityWorld();var pos=c.getHomePos();
            if(!world.isChunkLoaded(pos)||!(world.getBlockEntity(pos) instanceof CreakingHeartBlockEntity heart)||!heart.isPuppet(c))return null;
            var access=(CreakingMemory.Heart)heart;var memory=access.relationshipMemory();
            String id=memory.getString("directory_identity","");
            if(id.isEmpty()&&create){id=UUID.randomUUID().toString();memory=memory.copy();memory.putString("directory_identity",id);access.relationshipMemory(memory);}
            return id.isEmpty()?null:"heart:"+id;
        }
        return "entity:"+entity.getUuid();
    }
    public static void observe(LivingEntity entity,String state) {
        if(!(entity.getEntityWorld() instanceof ServerWorld world)||entity instanceof PlayerEntity)return;
        var attached=entity instanceof AffinityHolder h?h.peekAffinityData():null;
        var store=peek(world.getServer());String oldId=IDENTITIES.get(entity.getUuid());
        if((attached==null||attached.isEmpty())&&oldId==null){CompanionArchive.observe(entity);com.nonid.affinity.gift.GiftService.observe(entity);return;}
        String id=identity(entity,attached!=null&&!attached.isEmpty());if(id==null)return;
        IDENTITIES.put(entity.getUuid(),id);
        CompanionArchive.observe(entity);com.nonid.affinity.gift.GiftService.observe(entity);
        if(store==null)store=new CompanionDirectoryData();
        var remaining=new HashSet<>(store.viewers(id));boolean changed=false;long now=System.currentTimeMillis();
        if(attached!=null)for(var item:attached.entries().entrySet()) {
            UUID player;try{player=UUID.fromString(item.getKey());}catch(IllegalArgumentException invalid){continue;}
            remaining.remove(player);
            // Replays cannot persist their temporary view of a participating relationship.
            if(ReplayIsolation.protects(entity.getUuid(),player))continue;
            if(item.getValue().getAffinity()<com.nonid.affinity.NonCreatureAffinity.getConfig().familiarThreshold){
                if(store.get(player,id)!=null){store.remove(player,id);changed=true;}
                continue;
            }
            var q=new CompanionInfoQuery(ZERO,0,entity.getUuid(),world.getRegistryKey().getValue().toString());
            var row=new CompanionRecord(id,now,state,CompanionInfoService.snapshot(entity,player,q));var prior=store.get(player,id);
            if(prior!=null&&prior.availability().equals("dead")&&entity.getHealth()<=0)row=row.historical("dead");
            if(prior!=null){var a=prior.toNbt();var b=row.toNbt();a.remove("observed");b.remove("observed");
                if(a.equals(b)&&now>=prior.observedAt()&&now-prior.observedAt()<5000)continue;}
            store.put(player,row);changed=true;
            if(oldId!=null&&!oldId.equals(id))store.remove(player,oldId);
        }
        for(var player:remaining)if(!ReplayIsolation.protects(entity.getUuid(),player)){store.remove(player,id);changed=true;}
        if(changed)world.getServer().getOverworld().setAttached(DATA,store);
    }
    /** Viewer identity comes from the authenticated connection. No fallback to another player's row. */
    public static CompanionRecord read(ServerPlayerEntity player,String id) {
        var store=peek(player.getEntityWorld().getServer());var row=store==null?null:store.get(player.getUuid(),id);
        int threshold=com.nonid.affinity.NonCreatureAffinity.getConfig().familiarThreshold;
        if(row==null||row.info().affinity()<threshold)return null;
        var entity=LOADED.get(row.info().query().target());
        if(entity!=null&&entity.isAlive()&&!entity.isRemoved()) {
            var relation=AffinityService.getRelation(entity,player);
            if(relation==null||relation.getAffinity()<threshold)return null;
            if(!id.equals(identity(entity,false)))return null;
            // Cross-dimension/unloaded records deliberately remain historical, never remote live telemetry.
            if(entity.getEntityWorld()==player.getEntityWorld()&&player.squaredDistanceTo(entity)<=32*32) {
                var current=CompanionInfoService.snapshot(entity,player.getUuid(),row.info().query());
                return new CompanionRecord(id,System.currentTimeMillis(),"live",current);
            }
        }
        return row.historical(row.availability().equals("dead")?"dead":"historical");
    }
}

