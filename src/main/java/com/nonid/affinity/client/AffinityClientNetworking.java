package com.nonid.affinity.client;

import com.nonid.affinity.net.FreeTradeStatusPayload;
import com.nonid.affinity.net.FreeTradeWindowPayload;
import com.nonid.affinity.net.RequestStatePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client receivers. Only handles display state; nothing here affects gameplay.
 *
 * <p>The free-trade receivers do not decide anything either: they extract the payload's fields, hand them to the
 * store, and let the store's rules about identity, request correlation and quote order decide. A payload that the
 * store refuses is silently dropped -- there is no fallback path that shows something anyway.
 */
@Environment(EnvType.CLIENT)
public final class AffinityClientNetworking {

    private AffinityClientNetworking() {
    }

    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(com.nonid.affinity.net.RelationshipStagePayload.ID,
                (payload, context) -> context.client().execute(() ->
                        ClientRequestState.updateStage(payload.targetUuid(), payload.label())));
        ClientPlayNetworking.registerGlobalReceiver(com.nonid.affinity.net.FamilyLabelPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        ClientRequestState.updateFamily(payload.targetUuid(), payload.label())));
        ClientPlayNetworking.registerGlobalReceiver(
                com.nonid.affinity.net.AffinityDataPayload.ID,
                (payload, context) -> context.client().execute(() -> ClientRequestState.updateDetector(
                        payload.targetUuid(), payload.found(), payload.affinity(),
                        payload.maxAffinity(), payload.matings(), payload.friend(),
                        payload.companionMode(), payload.friendThreshold(),
                        payload.tokensReceived())));
        ClientPlayNetworking.registerGlobalReceiver(
                com.nonid.affinity.net.AnimationMenuPayload.ID,
                (payload, context) -> context.client().execute(() -> context.client().setScreen(
                        new AnimationChoiceScreen(context.client().currentScreen,
                                payload.mobName(), payload.rows()))));
        ClientPlayNetworking.registerGlobalReceiver(RequestStatePayload.ID,
                (payload, context) -> context.client().execute(() ->
                        ClientRequestState.update(payload)));

        // The trade window's identity. The store accepts it only when it answers the request this client is still
        // waiting on, which is what keeps a reused syncId from adopting a previous window's identity.
        ClientPlayNetworking.registerGlobalReceiver(FreeTradeWindowPayload.ID,
                (payload, context) -> context.client().execute(() -> FreeTradeStatusStore.bind(
                        payload.windowId(), payload.villagerUuid(), payload.windowInstanceToken(),
                        payload.requestId())));

        // One offer's status. Everything the window shows comes from here.
        //
        // The selection is re-read from the game's own screen BEFORE the status is judged, and that ordering is the
        // point: the per-tick follower only makes the wait correct once per tick, while a reply can arrive in the
        // tick the player clicked. Judged against the previous recipe, that reply would be refused as "about an
        // offer we have moved on from", and the server would not send it again because from its side nothing
        // changed -- leaving the window on its waiting line with its own answer in hand. Reading the selection here
        // judges every reply against the selection the game holds at that moment.
        ClientPlayNetworking.registerGlobalReceiver(FreeTradeStatusPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    net.minecraft.client.gui.screen.Screen screen = context.client().currentScreen;
                    int screenRecipe = screen instanceof net.minecraft.client.gui.screen.ingame.MerchantScreen
                            merchantScreen
                            ? FreeTradeWindowClient.recipeIndex(merchantScreen.getScreenHandler())
                            : -1;
                    FreeTradeWindowClient.acceptFromServer(payload.windowId(), payload.villagerUuid(),
                            payload.windowInstanceToken(), payload.requestId(), payload.offerIndex(),
                            payload.toStatus(), screenRecipe);
                }));
    }
}
