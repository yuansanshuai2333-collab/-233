package com.nonid.affinity.net;

import com.nonid.affinity.NonCreatureAffinity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client to server: "the trade window I opened, the one with this identity, is closed".
 *
 * <p><b>Why the syncId alone is not enough.</b> Vanilla hands out small reused integers, so a close built from a
 * number can match a window that was opened AFTER the one it means. The server would then obey it and drop a record
 * the player is still using, which costs that window its status updates and re-issues its identity on the next
 * request. Carrying the instance token AND the opening's request id makes the message name one exact opening: the
 * token distinguishes it from every earlier window, and the request id distinguishes it from a later reopening that
 * happens to share the same number.
 *
 * <p>The server checks all three before dropping anything, and a message that does not match exactly is a no-op
 * rather than an error: a close that overtook a reopening is a late message about a window that is already gone,
 * and the honest response is to ignore it.
 *
 * <p>The server does NOT depend on this message to notice a closed window -- the per-tick check sees that the
 * player is no longer in that handler -- so a lost or refused close costs nothing but a slightly later cleanup.
 */
public record FreeTradeClosePayload(int windowId, long windowInstanceToken, long requestId)
        implements CustomPayload {

    public static final CustomPayload.Id<FreeTradeClosePayload> ID =
            new CustomPayload.Id<>(Identifier.of(NonCreatureAffinity.MOD_ID, "free_trade_close"));

    public static final PacketCodec<RegistryByteBuf, FreeTradeClosePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, FreeTradeClosePayload::windowId,
            PacketCodecs.VAR_LONG, FreeTradeClosePayload::windowInstanceToken,
            PacketCodecs.VAR_LONG, FreeTradeClosePayload::requestId,
            FreeTradeClosePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
