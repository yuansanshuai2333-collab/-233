package com.nonid.affinity.reunion;

import com.nonid.affinity.service.ReplayIsolation;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.*;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import java.util.*;

/** Brief, bounded loading of the last known chunk; always teleport the original UUID. */
public final class ReunionRecall {
    private static final Map<UUID,Request> PENDING=new HashMap<>();
    private static final ChunkTicketType TICKET=Registry.register(Registries.TICKET_TYPE,
            ReunionData.id("reunion_recall"),new ChunkTicketType(220,2));
    private record Request(UUID player,UUID mob,String bond,long fuel,Hand hand,ServerWorld source,
                           ServerWorld destination,ChunkPos chunk,int started) {}
    public static void register(){
        ServerEntityEvents.ENTITY_LOAD.register((e,w)->{if(e instanceof MobEntity m)remember(m);});
        ServerEntityEvents.ENTITY_UNLOAD.register((e,w)->{if(e instanceof MobEntity m)remember(m);});
        ServerTickEvents.END_SERVER_TICK.register(ReunionRecall::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s->PENDING.clear());
    }
    public static void remember(MobEntity mob){
        if(!(mob.getEntityWorld() instanceof ServerWorld w)||!mob.isAlive()||ReplayIsolation.isActor(mob.getUuid()))return;
        var record=ReunionData.record(w.getServer(),mob.getUuidAsString());
        if(record.isEmpty()||record.getBoolean("dead",false))return;
        String dimension=w.getRegistryKey().getValue().toString();var pos=mob.getChunkPos();
        if(dimension.equals(record.getString("dimension","")) && record.getInt("cx",Integer.MIN_VALUE)==pos.x
                && record.getInt("cz",Integer.MIN_VALUE)==pos.z)return;
        record.putString("dimension",dimension);record.putInt("cx",pos.x);record.putInt("cz",pos.z);
        ReunionData.save(w.getServer(),mob.getUuidAsString(),record);
    }
    private static MobEntity find(MinecraftServer server,UUID id){
        for(var w:server.getWorlds())if(w.getEntity(id) instanceof MobEntity m)return m;
        return null;
    }
    public static void request(ServerPlayerEntity player,ItemStack stack,Hand hand){
        if(!ReunionData.item(stack).contains("mob")){ReunionService.message(player,"bind_hint");return;}
        if(PENDING.containsKey(player.getUuid())){ReunionService.message(player,"searching");return;}
        if(!ReunionService.permitted(player,stack))return;
        var data=ReunionData.item(stack);var server=player.getEntityWorld().getServer();
        var record=ReunionData.record(server,data.getString("mob",""));
        if(record.getBoolean("dead",false)){ReunionService.message(player,"dead_recall");return;}
        UUID id;try{id=UUID.fromString(data.getString("mob",""));}catch(IllegalArgumentException e){return;}
        var mob=find(server,id);
        if(mob!=null){move(player,stack,mob);return;}
        var dim=Identifier.tryParse(record.getString("dimension",""));
        var source=dim==null?null:server.getWorld(RegistryKey.of(RegistryKeys.WORLD,dim));
        if(source==null){ReunionService.message(player,"location_unknown");return;}
        var chunk=new ChunkPos(record.getInt("cx",0),record.getInt("cz",0));
        if(Math.abs((long)chunk.x)>1875000 || Math.abs((long)chunk.z)>1875000){ReunionService.message(player,"unavailable");return;}
        source.getChunkManager().addTicket(TICKET,chunk,31);
        PENDING.put(player.getUuid(),new Request(player.getUuid(),id,data.getString("bond",""),ReunionData.fuel(data),hand,
                source,player.getEntityWorld(),chunk,server.getTicks()));
        ReunionService.message(player,"searching");
    }
    private static void release(Request r){
        PENDING.remove(r.player());
        if(PENDING.values().stream().noneMatch(p->p.source()==r.source()&&p.chunk().equals(r.chunk())))
            r.source().getChunkManager().removeTicket(TICKET,r.chunk(),31);
    }
    private static void tick(MinecraftServer server){
        if(server.getTicks()%20==0){
            for(String id:ReunionData.ledger(server).getKeys())try{var mob=find(server,UUID.fromString(id));if(mob!=null)remember(mob);}catch(IllegalArgumentException ignored){}
        }
        for(var r:List.copyOf(PENDING.values())){
            var p=server.getPlayerManager().getPlayer(r.player());
            if(p==null||!p.isAlive()||p.getEntityWorld()!=r.destination()){release(r);continue;}
            var stack=p.getStackInHand(r.hand());var item=ReunionData.item(stack);
            if(!r.bond().equals(item.getString("bond",""))||r.fuel()!=ReunionData.fuel(item)) {release(r);continue;}
            var mob=find(server,r.mob());
            if(mob!=null){release(r);move(p,stack,mob);}
            else if(server.getTicks()-r.started()>=200){release(r);ReunionService.message(p,"not_found");}
        }
    }
    private static boolean move(ServerPlayerEntity p,ItemStack stack,MobEntity mob){
        if(!ReunionService.permitted(p,stack))return false;
        if(!mob.isAlive()||mob.isRemoved()||ReplayIsolation.isActor(mob.getUuid())||!ReunionService.owns(p,mob)){
            ReunionService.message(p,"unavailable");return false;
        }
        if(com.nonid.affinity.non.NonBridge.isActorBusy((ServerWorld)mob.getEntityWorld(),mob)
                || com.nonid.affinity.non.NonBridge.isActorBusy(p.getEntityWorld(),p)){
            ReunionService.message(p,"unavailable");return false;
        }
        if(mob.hasVehicle()||mob.hasPassengers()||mob.isLeashed()){
            ReunionService.message(p,"recall_attached");return false;
        }
        if(mob.getEntityWorld()==p.getEntityWorld()&&mob.squaredDistanceTo(p)<=100){ReunionService.message(p,"nearby");return false;}
        if(p.getEntityWorld().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL&&!mob.getType().isAllowedInPeaceful()){
            ReunionService.message(p,"unsupported");return false;
        }
        // Evaluate geometry on an unspawned copy: checking locations must never move the live entity.
        Vec3d destination=null;
        try {
            var probe=mob.getType().create(p.getEntityWorld(),net.minecraft.entity.SpawnReason.COMMAND);
            if(probe instanceof MobEntity geometry){
                geometry.readData(net.minecraft.storage.NbtReadView.create(net.minecraft.util.ErrorReporter.EMPTY,
                        p.getEntityWorld().getRegistryManager(),ReunionService.snapshot(mob)));
                geometry.setPose(mob.getPose());
                if(ReunionService.place(p.getEntityWorld(),p,geometry))destination=geometry.getEntityPos();
            }
        } catch(RuntimeException e){ReunionService.message(p,"unsupported");return false;}
        if(destination==null){ReunionService.message(p,"no_space");return false;}
        boolean moved=mob.teleport(p.getEntityWorld(),destination.x,destination.y,destination.z,Set.of(),mob.getYaw(),mob.getPitch(),false);
        if(!moved){ReunionService.message(p,"unavailable");return false;}
        ReunionService.spendOne(p,stack);
        var actual=find(p.getEntityWorld().getServer(),mob.getUuid());
        if(actual!=null){
            actual.setVelocity(Vec3d.ZERO);actual.fallDistance=0;actual.getNavigation().stop();
            // A successful explicit recall relocates the owner's WAIT order to the actual landing.
            // Native pets keep their vanilla controls; failed teleports never reach this update.
            if(!com.nonid.affinity.service.CompanionService.usesNativeControl(actual)
                    && actual instanceof com.nonid.affinity.data.AffinityHolder holder){
                var affinity=holder.peekAffinityData();
                if(affinity!=null && affinity.getCompanionMode()==com.nonid.affinity.data.CompanionMode.WAIT)
                    affinity.setAnchor(actual.getEntityWorld().getRegistryKey().getValue().toString(),
                            actual.getX(),actual.getY(),actual.getZ());
            }
            remember(actual);
        }
        ReunionSuccessFeedback.send(p,actual,false);return true;
    }
    private ReunionRecall(){}
}
