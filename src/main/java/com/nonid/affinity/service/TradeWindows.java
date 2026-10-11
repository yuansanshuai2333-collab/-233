package com.nonid.affinity.service;

import java.util.List;
import java.util.UUID;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The villager-shaped face of {@link TradeWindowRegistry}: it turns "the player and the villager the server's own
 * handler reports" into the UUID pair the registry keys on, and nothing more.
 *
 * <p>Every rule about window identity, request correlation, quote numbering and forgetting lives in the registry,
 * which knows nothing about Minecraft and is therefore covered directly by unit tests. This class exists so the
 * call sites in mixins and networking can name a {@code VillagerEntity} instead of having to read the entity's UUID
 * every time -- and so that reading the villager from the SERVER's handler is the only way to reach the registry:
 * nothing here accepts a villager, a window number or a request id from a client.
 *
 * <p>{@link WindowState} and {@link StatusForWindow} are the carrier types the call sites already use; they are
 * built from registry records so the registry itself never has to mention an entity.
 */
public final class TradeWindows {

    private TradeWindows() {
    }

    /** One open window: its syncId, the villager behind it, and the identity values that go with it. */
    public record WindowState(int windowId, UUID villagerUuid, long instanceToken, long requestId,
                              VillagerEntity villager) {
    }

    /**
     * A status ready to be sent: the window's instance token, the request it answers, and what to show.
     *
     * <p>Separate from the payload so the server side never has to know the wire format, and so the decision can be
     * exercised without a network.
     */
    public record StatusForWindow(long instanceToken, long requestId, FreeTradeStatus status) {
    }

    /**
     * Register the window this player has just opened, allocating its instance token.
     *
     * @param requestId the id of the client's opening request, echoed back in the binding and every status
     */
    public static WindowState register(ServerPlayerEntity player, int windowId, VillagerEntity villager,
                                       long requestId) {
        TradeWindowRegistry.Record record = TradeWindowRegistry.register(player.getUuid(), windowId,
                villager.getUuid(), requestId);
        return new WindowState(record.windowId(), record.villagerUuid(), record.instanceToken(), record.requestId(),
                villager);
    }

    /** @see TradeWindowRegistry#noteSelectedOffer(UUID, int, int) */
    public static boolean noteSelectedOffer(ServerPlayerEntity player, int windowId, int offerIndex) {
        return TradeWindowRegistry.noteSelectedOffer(player.getUuid(), windowId, offerIndex);
    }

    /** @see TradeWindowRegistry#selectedOfferIndex(UUID, int) */
    public static int selectedOfferIndex(ServerPlayerEntity player, int windowId) {
        return TradeWindowRegistry.selectedOfferIndex(player.getUuid(), windowId);
    }

    /** The window this player has open with this villager, when it is the one the caller means, or null. */
    @Nullable
    public static WindowState current(ServerPlayerEntity player, int windowId, VillagerEntity villager) {
        TradeWindowRegistry.Record record = TradeWindowRegistry.current(player.getUuid(), windowId,
                villager == null ? null : villager.getUuid());
        return record == null ? null : new WindowState(record.windowId(), record.villagerUuid(),
                record.instanceToken(), record.requestId(), villager);
    }

    /** @see TradeWindowRegistry#isCurrent(UUID, int) */
    public static boolean isCurrent(ServerPlayerEntity player, int windowId) {
        return TradeWindowRegistry.isCurrent(player.getUuid(), windowId);
    }

    /** @see TradeWindowRegistry#recordSent(UUID, int, FreeTradeStatus) */
    public static long recordSent(ServerPlayerEntity player, int windowId, FreeTradeStatus status) {
        return TradeWindowRegistry.recordSent(player.getUuid(), windowId, status);
    }

    /**
     * Whether this window has ever produced a real answer, or is still being built.
     *
     * @see TradeWindowRegistry#hasEverResolved(UUID, int)
     */
    public static boolean hasEverResolved(ServerPlayerEntity player, int windowId) {
        return TradeWindowRegistry.hasEverResolved(player.getUuid(), windowId);
    }

    /** @see TradeWindowRegistry#noteResolved(UUID, int) */
    public static void noteResolved(ServerPlayerEntity player, int windowId) {
        TradeWindowRegistry.noteResolved(player.getUuid(), windowId);
    }

    /** @see TradeWindowRegistry#lastSent(UUID, int) */
    @Nullable
    public static FreeTradeStatus lastSent(ServerPlayerEntity player, int windowId) {
        return TradeWindowRegistry.lastSent(player.getUuid(), windowId);
    }

    /** @see TradeWindowRegistry#close(UUID, int) */
    public static boolean close(ServerPlayerEntity player, int windowId) {
        return TradeWindowRegistry.close(player.getUuid(), windowId);
    }

    /**
     * Forget this player's window only when the identity the client reported matches the record exactly.
     *
     * @see TradeWindowRegistry#closeMatching(UUID, int, long, long)
     */
    public static boolean closeMatching(ServerPlayerEntity player, int windowId, long instanceToken,
                                       long requestId) {
        return TradeWindowRegistry.closeMatching(player.getUuid(), windowId, instanceToken, requestId);
    }

    /**
     * Forget this player's window, as the server itself observed it: the screen is gone, or it is no longer the
     * merchant screen this record was made for.
     *
     * <p>Deliberately does not consult the syncId alone. The server never has to trust a client's word that a window
     * closed: it can see for itself that the player is no longer in that handler, which also covers a disconnect, a
     * death, a teleport and a plugin closing the screen.
     */
    public static boolean forgetWindow(ServerPlayerEntity player, int windowId) {
        return TradeWindowRegistry.close(player.getUuid(), windowId);
    }

    /** @see TradeWindowRegistry#forget(UUID) */
    public static void forget(UUID playerUuid) {
        TradeWindowRegistry.forget(playerUuid);
    }

    /** @see TradeWindowRegistry#trackedPlayers() */
    public static List<UUID> trackedPlayers() {
        return TradeWindowRegistry.trackedPlayers();
    }

    /** @see TradeWindowRegistry#trackedWindowId(UUID) */
    public static int trackedWindowId(UUID playerUuid) {
        return TradeWindowRegistry.trackedWindowId(playerUuid);
    }

    /**
     * The UUID of the player whose open window carries this syncId, or null.
     *
     * <p>See {@link TradeWindowRegistry#playerWithWindow(int)}: a syncId is not an identity, so this only ever finds
     * a CANDIDATE that the caller must confirm from the window's own customer.
     */
    @Nullable
    public static UUID playerWithWindow(int windowId) {
        return TradeWindowRegistry.playerWithWindow(windowId);
    }

    /** @see TradeWindowRegistry#forgetAll() */
    public static void forgetAll() {
        TradeWindowRegistry.forgetAll();
    }
}
