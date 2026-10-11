package com.nonid.affinity.gift;

import java.util.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.world.ServerWorld;

/** Only the ledger-authenticated original box entity can receive bequest protection. */
public final class GiftDropProtection {
    private GiftDropProtection() {}
    public static GiftEnvelope original(ItemEntity item) {
        if (!(item.getEntityWorld() instanceof ServerWorld world) || item.getStack().getItem() != GiftBoxItem.ITEM) return null;
        var data = GiftService.peek(world.getServer()); if (data == null) return null; var ticket = GiftBoxItem.ticket(item.getStack());
        try { var gift = data.gift(UUID.fromString(ticket.getString("gift", ""))); return gift != null && gift.token().toString().equals(ticket.getString("token", "")) && item.getUuid().equals(gift.drop()) ? gift : null; }
        catch (IllegalArgumentException invalid) { return null; }
    }
    public static boolean active(ItemEntity item) {
        var gift = original(item); return gift != null && gift.protectedDrop(item.getUuid(), GiftService.peek(((ServerWorld)item.getEntityWorld()).getServer()).clock());
    }
    public static boolean privatePickup(ItemEntity item, UUID player) {
        var gift = original(item);
        if (gift == null || gift.state() != GiftEnvelope.State.ISSUED) return false;
        // Normal gifts remain claimant-owned until picked up; subsequent voluntary drops have a new entity UUID.
        boolean locked = !gift.bequestDelivery() || active(item);
        return locked && !Objects.equals(gift.claimant(), player);
    }
}
