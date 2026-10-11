package com.nonid.affinity.net;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.service.FreeTradeStatus;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

/**
 * Server to client: what the trade window is allowed to show for one offer, as the SERVER judged it.
 *
 * <p>The screen never re-derives the free flag, the remaining count or the reason: they all come from here.
 * The regression this closes is "the server recorded a free trade while the screen still showed a price" --
 * the screen was showing vanilla's number because nothing had told it otherwise.
 *
 * <p><b>Field by field, because the window identity takes THREE values and they are not interchangeable:</b>
 *
 * <ul>
 *   <li>{@code windowId} -- the screen handler's {@code syncId}. Used to make sure a payload is for the window
 *       the client currently has open. On its own it is NOT an identity: vanilla reuses small integers, so a
 *       late payload for a closed window can carry the same number as the window open now.</li>
 *   <li>{@code villagerUuid} -- which villager this window belongs to. Also not sufficient by itself: the same
 *       villager can be reopened, and the reopened window is a different window.</li>
 *   <li>{@code windowInstanceToken} -- the SERVER's own counter, allocated once per opened window. This is the
 *       value that distinguishes "the window I am looking at" from "a window that had the same syncId before",
 *       and it is why the client refuses every status until it has received the token for its own window.</li>
 * </ul>
 *
 * <p>{@code requestId} is carried on statuses as well as on the binding and the close, so every message about a
 * window can be checked against the opening it belongs to: the client compares the echo with the request it still
 * has outstanding and drops anything from an opening it has already left behind.
 *
 * <p>{@code offerIndex} is {@code -1} when the server could not name an offer for this window (the recipe list
 * was rebuilt, or no offer is selected). That is not "unknown" for display purposes: it is the explicit
 * "no offer to describe", and the client shows the waiting line for it while it keeps any other offer's state.
 *
 * <p>{@code quoteRevision} is the window's own monotonic quote number: it grows every time this window's answer
 * changes, and never repeats within a window. The client uses it to drop an answer that arrives after a newer one
 * (packets can be reordered by a delayed connection) and to recognise a repeat of what it already shows. It is
 * deliberately NOT the allowance's own revision: that one only moves when the allowance moves, and a price, a
 * stock level or an affinity can change without it.
 *
 * <p>{@code firstItemName} is what a paid trade charges first, in the player's language, so a sentence about the
 * price can name the item. It is carried from the server because the server is where the offer's payment item is
 * decided; the client does not hold the offer list and must not guess.
 */
public record FreeTradeStatusPayload(
        int windowId,
        java.util.UUID villagerUuid,
        long windowInstanceToken,
        long requestId,
        int offerIndex,
        boolean free,
        int remaining,
        int total,
        int paidFirstCount,
        String reason,
        String firstItemName,
        long quoteRevision) implements CustomPayload {

    public static final CustomPayload.Id<FreeTradeStatusPayload> ID =
            new CustomPayload.Id<>(Identifier.of(NonCreatureAffinity.MOD_ID, "free_trade_status"));

    /**
     * The reason is sent as its enum NAME, bounded to the longest name this mod defines.
     *
     * <p>A wire string rather than an ordinal: an enum's order is not a protocol, and inserting a value in the
     * middle of {@link FreeTradeStatus.Reason} would silently re-label every payload. An unrecognised name on the
     * receiving side is read as "trade unavailable", which shows the cautious line instead of a wrong price.
     */
    public static final int MAX_REASON_LENGTH = 32;

    /**
     * Bound for the payment item's display name.
     *
     * <p>Generous on purpose: a vanilla item name is a few characters, but another mod's item can carry a long one,
     * and cutting a name in half would print something that is not the item. The bound exists so a pathological
     * name cannot make the packet unsendable, which would silently leave the window without any status at all.
     */
    public static final int MAX_ITEM_NAME_LENGTH = 128;

    public static final PacketCodec<RegistryByteBuf, FreeTradeStatusPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, FreeTradeStatusPayload::windowId,
            Uuids.PACKET_CODEC, FreeTradeStatusPayload::villagerUuid,
            PacketCodecs.VAR_LONG, FreeTradeStatusPayload::windowInstanceToken,
            PacketCodecs.VAR_LONG, FreeTradeStatusPayload::requestId,
            PacketCodecs.VAR_INT, FreeTradeStatusPayload::offerIndex,
            PacketCodecs.BOOLEAN, FreeTradeStatusPayload::free,
            PacketCodecs.VAR_INT, FreeTradeStatusPayload::remaining,
            PacketCodecs.VAR_INT, FreeTradeStatusPayload::total,
            PacketCodecs.VAR_INT, FreeTradeStatusPayload::paidFirstCount,
            PacketCodecs.string(MAX_REASON_LENGTH), FreeTradeStatusPayload::reason,
            PacketCodecs.string(MAX_ITEM_NAME_LENGTH), FreeTradeStatusPayload::firstItemName,
            PacketCodecs.VAR_LONG, FreeTradeStatusPayload::quoteRevision,
            FreeTradeStatusPayload::new);

    public FreeTradeStatusPayload {
        // A reason name longer than the codec's bound would only fail at send time, on the server, with the window
        // already open. An over-long name is replaced by the cautious one instead: the window then shows
        // "unavailable", which is the same thing the receiving side concludes from an unrecognised name.
        if (reason == null || reason.length() > MAX_REASON_LENGTH) {
            reason = FreeTradeStatus.Reason.TRADE_UNAVAILABLE.name();
        }
        firstItemName = PacketDisplayName.fit(firstItemName, MAX_ITEM_NAME_LENGTH);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** The status a client may display, with an unknown reason name read as "unavailable" rather than guessed. */
    public FreeTradeStatus toStatus() {
        return new FreeTradeStatus(this.windowId, this.offerIndex, this.free, this.remaining, this.total,
                this.paidFirstCount, this.firstItemName, reasonOrUnavailable(), this.quoteRevision);
    }

    /**
     * The reason, or {@link FreeTradeStatus.Reason#TRADE_UNAVAILABLE} when the name is not one this version knows.
     *
     * <p>Named with the {@code OrUnavailable} suffix because the record component {@code reason} already claims the
     * accessor name, and the two are deliberately different questions: one is the raw wire string, this is the
     * validated enum.
     */
    public FreeTradeStatus.Reason reasonOrUnavailable() {
        for (FreeTradeStatus.Reason candidate : FreeTradeStatus.Reason.values()) {
            if (candidate.name().equals(this.reason)) {
                return candidate;
            }
        }
        return FreeTradeStatus.Reason.TRADE_UNAVAILABLE;
    }

    /** Build the payload for one already-computed status. */
    public static FreeTradeStatusPayload of(int windowId, java.util.UUID villagerUuid, long windowInstanceToken,
                                            long requestId, FreeTradeStatus status) {
        return new FreeTradeStatusPayload(windowId, villagerUuid, windowInstanceToken, requestId,
                status.offerIndex(), status.free(), status.remaining(), status.total(), status.paidFirstCount(),
                status.reason().name(), status.firstItemName(), status.quoteRevision());
    }
}
