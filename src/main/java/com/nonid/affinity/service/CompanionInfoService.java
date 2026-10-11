package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.net.*;
import com.nonid.affinity.non.NonBridge;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.Locale;

/** Read-only and loaded-world-only; no relation creation, binding, save writes or chunk lookup. */
public final class CompanionInfoService {
    private CompanionInfoService() {}
    public static final double RANGE = 32;
    public static CompanionInfoData read(ServerPlayerEntity player, CompanionInfoQuery query) {
        var world = player.getEntityWorld();
        if (!player.isAlive() || !world.getRegistryKey().getValue().toString().equals(query.dimension()))
            return CompanionInfoData.unavailable(query, "invalid");
        var entity = world.getEntity(query.target());
        if (!(entity instanceof LivingEntity target) || target instanceof PlayerEntity || !target.isAlive() || target.isRemoved())
            return CompanionInfoData.unavailable(query, "invalid");
        if (player.squaredDistanceTo(target) > RANGE * RANGE) return CompanionInfoData.unavailable(query, "out_of_range");
        return snapshot(target, player.getUuid(), query);
    }
    /** The server, not a client flag, chooses which fields the current hands authorize. */
    public static CompanionInfoData forHands(ServerPlayerEntity player,CompanionInfoData d) {
        if(com.nonid.affinity.item.AffinityDetectorItem.isHeldBy(player))return d;
        return new CompanionInfoData(d.query(),d.status(),d.name(),d.type(),d.stage(),d.affinity(),d.maximum(),d.friendThreshold(),
                -1,-1,-1,-1,"unknown","unknown","unknown","unknown",d.family(),-1,-1,-1,-1,-1,-1);
    }
    /** Called only with an already loaded entity. Also observes offline players' existing relations. */
    public static CompanionInfoData snapshot(LivingEntity target, java.util.UUID viewer, CompanionInfoQuery query) {
        var player = target.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld world
                ? world.getServer().getPlayerManager().getPlayer(viewer) : null;
        var data = target instanceof com.nonid.affinity.data.AffinityHolder holder ? holder.peekAffinityData() : null;
        var relation = data == null ? null : data.get(viewer);
        var config = NonCreatureAffinity.getConfig();
        String owner = "unknown", control = "none", mode = "unknown", restriction = "unknown";
        if (target instanceof MobEntity mob) {
            var nativeOwner = CompanionService.vanillaOwner(mob);
            var bondOwner = CompanionService.companionOwner(mob);
            var effectiveOwner = nativeOwner != null ? nativeOwner : bondOwner;
            owner = effectiveOwner == null ? "unowned" : effectiveOwner.equals(viewer) ? "self" : "other";
            boolean nativeControl = CompanionService.usesNativeControl(mob);
            control = nativeControl ? "native" : "relationship";
            if (nativeOwner != null && bondOwner != null && !nativeOwner.equals(bondOwner)) {
                owner = "unknown"; restriction = "ownership_conflict";
            } else if (nativeControl) {
                restriction = "native";
                if (mob instanceof net.minecraft.entity.passive.TameableEntity pet && pet.isTamed())
                    mode = pet.isSitting() ? "sit" : "native";
            } else if (owner.equals("other")) restriction = "other_owner";
            else if (!RelationshipBehavior.controls(mob, viewer)) restriction = "locked";
            else if (!owner.equals("self")) restriction = "unbound";
            else { restriction = "controllable"; mode = CompanionService.modeOf(mob).name().toLowerCase(Locale.ROOT); }
        }
        if (target instanceof MobEntity mob && "controllable".equals(restriction)) {
            if (mob.hasVehicle() || mob.hasPassengers()) restriction = "riding";
            else if (mob.isLeashed()) restriction = "leashed";
        }
        if (target.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld && NonBridge.isActorBusy(serverWorld, target)) restriction = "interaction";
        String name = target.hasCustomName() ? target.getName().getString() : "";
        if (name.length() > 256) name = name.substring(0, 256);
        return new CompanionInfoData(query, relation == null ? "no_relation" : "ready", name,
                Registries.ENTITY_TYPE.getId(target.getType()).toString(),
                RelationshipStageQuery.stage(relation, config).name().toLowerCase(Locale.ROOT),
                relation == null ? 0 : relation.getAffinity(), config.maxAffinity, config.friendThreshold,
                target.getHealth(), target.getMaxHealth(), target.getArmor(),
                target.getAttributes().hasAttribute(EntityAttributes.ATTACK_DAMAGE) ? target.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) : -1,
                owner, control, mode, restriction, player == null ? "" : FamilyService.label(target, player), player != null && player.getEntityWorld() == target.getEntityWorld() ? Math.sqrt(player.squaredDistanceTo(target)) : -1,
                -1, relation == null ? 0 : relation.getMatingCount(), relation == null ? -1 : relation.getAgreementCount(),
                relation == null ? 0 : relation.getRejectionCount(), relation == null ? 0 : relation.getTokensReceived());
    }
}

