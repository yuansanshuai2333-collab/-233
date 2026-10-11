package com.nonid.affinity.net;

import com.nonid.affinity.NonCreatureAffinity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client to server: "I have opened the trade window with syncId N -- tell me its identity and its prices".
 *
 * <p>The syncId is a CLAIM, and the server treats it as one. It is checked against the handler the server itself
 * has this player in, so a client cannot name a window the server never gave it and receive a token for it.
 *
 * <p><b>Why a request id travels beside the syncId.</b> The syncId alone cannot tell two different windows apart:
 * vanilla reuses small integers, so the same villager reopened, or a second merchant opened later, can carry the
 * number of a window the player has already closed. The answer to the first request would then be indistinguishable
 * from the answer to the second, and a screen could adopt the older window's identity -- and with it the older
 * window's free state. Every opening therefore generates its own request id, the server echoes that exact value,
 * and the client only accepts an identity it asked for.
 *
 * <p>The bind is also the moment the server allocates the window's instance token, which is why there is no
 * separate "open" message: the token and the first status travel back together, so the client never has to show
 * a window it cannot identify.
 *
 * <p>A repeated request carrying an id the server is already holding for the same open window is answered with the
 * SAME token rather than a fresh one: a retry, or a screen re-initialised without the window being reopened, must
 * not give one window two identities.
 */
public record FreeTradeBindRequestPayload(int windowId, long requestId) implements CustomPayload {

    public static final CustomPayload.Id<FreeTradeBindRequestPayload> ID =
            new CustomPayload.Id<>(Identifier.of(NonCreatureAffinity.MOD_ID, "free_trade_bind"));

    public static final PacketCodec<RegistryByteBuf, FreeTradeBindRequestPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, FreeTradeBindRequestPayload::windowId,
            PacketCodecs.VAR_LONG, FreeTradeBindRequestPayload::requestId,
            FreeTradeBindRequestPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
