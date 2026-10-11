package com.nonid.affinity.net;

import com.nonid.affinity.NonCreatureAffinity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

/**
 * Server to client: the identity of the trade window the server believes this player has open.
 *
 * <p><b>Why this payload exists at all.</b> The client has to attach a status to a window before it may show it,
 * and the only value that can carry that identity across a reconnect, a reopen, or a reused {@code syncId} is one
 * the SERVER issues. A window whose syncId is 3 today and 3 tomorrow is the same number and not the same window.
 *
 * <p>The triple is (syncId, instance token, request id) and none of the three is redundant:
 *
 * <ul>
 *   <li>{@code windowId} is what the client compares against the screen it actually has open.</li>
 *   <li>{@code windowInstanceToken} is what decides whether the statuses that follow may be believed. A client
 *       that only compared the syncId would accept a status for a previous window that reused the number.</li>
 *   <li>{@code requestId} is the client's OWN marker, echoed back. A client that only compared the token could
 *       still adopt the identity of a window it did not ask about -- the server answers with a token for whatever
 *       window the player really has, and after a rapid close-and-reopen that need not be the window the screen
 *       now shows. Matching the echo against the request that is still outstanding is what ties an answer to its
 *       question.</li>
 * </ul>
 */
public record FreeTradeWindowPayload(int windowId, java.util.UUID villagerUuid, long windowInstanceToken,
                                     long requestId) implements CustomPayload {

    public static final CustomPayload.Id<FreeTradeWindowPayload> ID =
            new CustomPayload.Id<>(Identifier.of(NonCreatureAffinity.MOD_ID, "free_trade_window"));

    public static final PacketCodec<RegistryByteBuf, FreeTradeWindowPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, FreeTradeWindowPayload::windowId,
            Uuids.PACKET_CODEC, FreeTradeWindowPayload::villagerUuid,
            PacketCodecs.VAR_LONG, FreeTradeWindowPayload::windowInstanceToken,
            PacketCodecs.VAR_LONG, FreeTradeWindowPayload::requestId,
            FreeTradeWindowPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
