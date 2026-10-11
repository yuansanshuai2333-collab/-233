package com.nonid.affinity.client;

import com.nonid.affinity.net.CompanionArchivePayload;
import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.*;

/** Layout adapter only; opening a future tab must explicitly create its read session. */
public final class CompanionArchiveClient {
    private static UUID page;
    private static long sent, received;
    private static Consumer<CompanionArchivePayload.Reply> listener;
    private CompanionArchiveClient() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CompanionArchivePayload.Reply.ID, (reply, context) -> context.client().execute(() -> {
            if (page == null || !page.equals(reply.page()) || reply.sequence() <= received || reply.sequence() > sent) return;
            received = reply.sequence(); if (listener != null) listener.accept(reply);
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }
    public static void open(Consumer<CompanionArchivePayload.Reply> receiver) {
        close(); page = UUID.randomUUID(); sent = received = 0; listener = Objects.requireNonNull(receiver);
    }
    public static boolean request(String operation, String root, long cursor, int limit, UUID nonce) {
        return requestSequence(operation, root, cursor, limit, nonce) > 0;
    }
    /** Returns the actual sent sequence, or zero when no request was sent. */
    public static long requestSequence(String operation, String root, long cursor, int limit, UUID nonce) {
        if (page == null || !ClientPlayNetworking.canSend(CompanionArchivePayload.Request.ID)) return 0;
        long sequence = ++sent;
        ClientPlayNetworking.send(new CompanionArchivePayload.Request(page, sequence, operation, root, cursor, limit, nonce)); return sequence;
    }
    public static void close() {
        if (page != null && ClientPlayNetworking.canSend(CompanionArchivePayload.Request.ID))
            ClientPlayNetworking.send(new CompanionArchivePayload.Request(page, ++sent, "close", "", 0, 1, new UUID(0, 0)));
        clear();
    }
    private static void clear() { page = null; listener = null; sent = received = 0; }
}
