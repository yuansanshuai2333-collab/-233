package com.nonid.affinity.service;

import com.nonid.affinity.net.FriendshipTokenPayload;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

/** Called only after a real successful inventory insertion or fallback item drop. */
public final class FriendshipTokenFeedback {
    private FriendshipTokenFeedback() {}
    public static boolean supported(ServerPlayerEntity player) {
        return ServerPlayNetworking.canSend(player,FriendshipTokenPayload.ID);
    }
    public static void sent(ServerPlayerEntity player,Entity giver) {
        if(supported(player)) ServerPlayNetworking.send(player,new FriendshipTokenPayload(
                UUID.randomUUID(),giver.getUuid(),giver.getEntityWorld().getRegistryKey().getValue()));
    }
}
