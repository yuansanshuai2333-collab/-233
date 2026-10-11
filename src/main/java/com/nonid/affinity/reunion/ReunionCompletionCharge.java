package com.nonid.affinity.reunion;

import com.nonid.affinity.item.NonAffinityItems;
import com.nonid.affinity.service.ReplayIsolation;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Called once per consumed completion session/pair, independent of affinity gain. */
public final class ReunionCompletionCharge {
    public static int award(ServerWorld world,ServerPlayerEntity player,Entity creature){
        if(!player.isAlive()||!creature.isAlive()||player.getEntityWorld()!=world||creature.getEntityWorld()!=world
                ||ReplayIsolation.protects(creature.getUuid(),player.getUuid()))return 0;
        String type=Registries.ENTITY_TYPE.getId(creature.getType()).toString();int changed=0;
        var inventory=player.getInventory();
        for(int slot=0;slot<inventory.size();slot++){
            var stack=inventory.getStack(slot);if(!stack.isOf(NonAffinityItems.REUNION_WISH))continue;
            var item=ReunionData.item(stack);String id=item.getString("mob","");
            var record=ReunionData.record(world.getServer(),id);
            if(!ReunionData.authentic(item,record)||!player.getUuidAsString().equals(record.getString("owner",""))
                    ||!record.getString("type","").equals(item.getString("type","")))continue;
            long fuel=ReunionData.fuel(item),spent=ReunionData.spent(record);
            if(ReunionRechargeRules.fullCompletion(id.equals(creature.getUuidAsString()),type.equals(record.getString("type","")),fuel,spent)){
                ReunionChargeFeedback.fullInteraction(player,stack);continue;
            }
            if(!ReunionEnergy.canRecharge(fuel,spent))continue;
            int energy=ReunionData.energy(item,record);
            int amount=ReunionRechargeRules.completion(id.equals(creature.getUuidAsString()),type.equals(record.getString("type","")),energy);
            if(amount<=0||fuel>Long.MAX_VALUE-amount)continue;
            item.putLong("fuel",fuel+amount);item.putInt("energy",energy+amount);item.putBoolean("dim",false);
            ReunionData.item(stack,item);changed++;ReunionChargeFeedback.completedInteraction(player,stack,energy,energy+amount);
        }
        if(changed>0){inventory.markDirty();player.currentScreenHandler.sendContentUpdates();}
        return changed;
    }
    private ReunionCompletionCharge(){}
}

