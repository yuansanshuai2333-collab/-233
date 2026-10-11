package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.net.CompanionHighlight;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

public final class CompanionHighlightService {
    public static final int RANGE=32, DURATION_TICKS=200;
    private CompanionHighlightService() {}
    public static CompanionHighlight.Reply read(ServerPlayerEntity player, UUID nonce) {
        var world=player.getEntityWorld();var dimension=world.getRegistryKey().getValue().toString();
        if(!player.isAlive()||player.isSpectator()||!player.isSneaking()||!com.nonid.affinity.item.AffinityDetectorItem.isHeldBy(player))
            return new CompanionHighlight.Reply(nonce,dimension,0,false,List.of());
        var config=NonCreatureAffinity.getConfig();
        // Entity lookup visits loaded sections only. No chunk fetch and no relationship creation.
        var targets=world.getEntitiesByClass(LivingEntity.class,player.getBoundingBox().expand(RANGE),entity->
            entity!=player&&entity.isAlive()&&!entity.isRemoved()&&player.squaredDistanceTo(entity)<=RANGE*RANGE
        ).stream().limit(16384).map(entity->{
            var relation=entity instanceof PlayerEntity?null:AffinityService.getRelation(entity,player);
            return new CompanionHighlight.Target(entity.getUuid(),relation!=null,relation==null?0:relation.getAffinity());
        }).toList();
        return new CompanionHighlight.Reply(nonce,dimension,config.maxAffinity,targets);
    }
}

