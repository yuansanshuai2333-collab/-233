package com.nonid.affinity.service;

import com.nonid.affinity.data.AffinityAttachments;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-owned player choice. Updating it never consumes an outstanding request. */
public final class DirectInteractionPreference {
    private DirectInteractionPreference() {}
    /** Legacy storage only; never consulted by gameplay since 1.4.16. */
    @Deprecated public static boolean enabled(ServerPlayerEntity player) {
        return Boolean.TRUE.equals(player.getAttached(AffinityAttachments.DIRECT_INTERACTION));
    }
    @Deprecated public static void set(ServerPlayerEntity player, boolean enabled) {
        player.setAttached(AffinityAttachments.DIRECT_INTERACTION, enabled);
    }
    public static boolean enabled(ServerPlayerEntity player, net.minecraft.entity.LivingEntity creature) {
        return enabled(player, CompanionDirectory.identityOf(creature));
    }
    public static boolean enabled(ServerPlayerEntity player, String identity) {
        if(identity==null)return false;
        var choices=player.getAttached(AffinityAttachments.CREATURE_DIRECT_INTERACTION);
        return choices!=null && choices.getBoolean(identity,false);
    }
    /** Caller must authenticate directory membership before accepting a client-supplied identity. */
    public static void set(ServerPlayerEntity player,String identity,boolean enabled) {
        if(!validIdentity(identity))throw new IllegalArgumentException("Invalid creature identity");
        var old=player.getAttached(AffinityAttachments.CREATURE_DIRECT_INTERACTION);
        var choices=old==null?new net.minecraft.nbt.NbtCompound():old.copy();
        choices.putBoolean(identity,enabled);player.setAttached(AffinityAttachments.CREATURE_DIRECT_INTERACTION,choices);
    }
    public static boolean validIdentity(String identity){return identity!=null&&identity.matches("(entity|heart):[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    public static boolean permits(boolean enabled, int affinity) { return enabled && affinity > 0; }
}


