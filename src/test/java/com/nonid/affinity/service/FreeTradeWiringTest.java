package com.nonid.affinity.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Source-level checks on the three places that must agree about "can this be taken right now", plus the wiring of
 * the trade window's display path.
 *
 * <p>Why a source check: these facts live in mixin handlers, in a client entry point and in a JSON registration
 * list, none of which can be executed without a live screen and a player. The behaviour they protect is, however,
 * exactly the kind that regresses silently -- a mixin that is written but never registered still compiles, a guard
 * that drifts back to "the slots are not empty" still looks reasonable, and a client receiver that is never
 * registered leaves the window showing vanilla's price. The pure decisions themselves are covered by
 * {@link FreeTradeGuardTest}, {@link TradeWindowRegistryTest} and the client store's own test.
 */
class FreeTradeWiringTest {

    private static final Path SERVICE = Path.of("src/main/java/com/nonid/affinity/service");
    private static final Path MIXIN = Path.of("src/main/java/com/nonid/affinity/mixin");
    private static final Path CLIENT = Path.of("src/main/java/com/nonid/affinity/client");
    private static final Path NET = Path.of("src/main/java/com/nonid/affinity/net");
    private static final Path RESOURCES = Path.of("src/main/resources");

    /**
     * The client renderer mixin.
     *
     * <p>Deliberately NOT in {@code com.nonid.affinity.client}: a mixin config owns its whole package, and Mixin then
     * refuses to load any class in it directly -- which killed the client at startup when this mixin shared a package
     * with the mod's client entrypoint. It lives in {@code com.nonid.affinity.mixin.client} instead, whose package is
     * claimed by the client mixin config and holds nothing else.
     */
    private static final Path CLIENT_MIXIN =
            Path.of("src/main/java/com/nonid/affinity/mixin/client/MerchantScreenFreeTradeMixin.java");

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new AssertionError("cannot read " + path.toAbsolutePath(), error);
        }
    }

    @Test
    @DisplayName("payment is decided by vanilla's own match, tried in BOTH slot orders, and consumes nothing")
    void paymentUsesVanillasNonConsumingMatch() {
        String source = read(SERVICE.resolve("VillagerFreeTrades.java"));
        assertTrue(source.contains("offer.matchesBuyItems(firstSlot, secondSlot)")
                        && source.contains("offer.matchesBuyItems(secondSlot, firstSlot)"),
                "both orders must be tried, exactly as depleteBuyItems is tried in the completion path");
        assertTrue(!source.contains(".depleteBuyItems("),
                "a check must never consume the materials; only the completion path may deplete");
        assertTrue(!source.contains("isEmpty() && secondSlot")
                        && !source.contains("firstSlot.isEmpty()"),
                "emptiness is not a proxy for affordability: wrong, too few or a missing second material all fail");
    }

    @Test
    @DisplayName("availability is re-checked from the game, never asserted as true at a call site")
    void availabilityIsRecheckedNotAsserted() {
        String service = read(SERVICE.resolve("VillagerFreeTrades.java"));
        assertTrue(service.contains("public static boolean tradeAvailable("),
                "there must be one real availability check");
        for (String name : new String[] {"FreeTradeSlotGuardMixin.java", "FreeTradeMerchantScreenHandlerMixin.java",
                "FreeTradeMerchantInventoryMixin.java", "FreeTradeOutputSlotMixin.java"}) {
            String source = read(MIXIN.resolve(name));
            assertTrue(!source.contains("offer, true)") && !source.contains("selected, true)"),
                    name + " must pass a real availability result, not a literal true");
        }
    }

    @Test
    @DisplayName("the completion count compares the SAME offer object it started with")
    void theCountUsesTheSameOfferObject() {
        String source = read(MIXIN.resolve("FreeTradeOutputSlotMixin.java"));
        assertTrue(source.contains("noncreatureaffinity$offerOfTake = offer"),
                "the head handler must hold on to the offer");
        assertTrue(source.contains("TradeOffer offer = this.noncreatureaffinity$offerOfTake;"),
                "the tail handler must not look the offer up again: a successful trade that used up the last "
                        + "materials refreshes the offers, so a fresh lookup is null exactly when it matters");
    }

    // ------------------------------------------------------------------
    // The trade window's display path
    // ------------------------------------------------------------------

    @Test
    @DisplayName("every mixin the free-trade path needs is registered, or it never runs")
    void everyFreeTradeMixinIsRegistered() {
        String common = read(RESOURCES.resolve("noncreatureaffinity.mixins.json"));
        assertTrue(common.contains("FreeTradeWindowSelectionMixin"),
                "the selection hook must be listed: it compiles either way, and without the entry the server "
                        + "never learns that the player switched recipe");
        assertTrue(common.contains("MerchantScreenHandlerAccess"),
                "the accessor must be listed: without it every read of the merchant behind a window throws at "
                        + "runtime while the build stays green");
        assertTrue(common.contains("MerchantInventoryMerchantAccessor"));

        String client = read(RESOURCES.resolve("noncreatureaffinity.mixin.client.mixins.json"));
        assertTrue(client.contains("\"client\""),
                "a MerchantScreen mixin only resolves on the client, so it belongs in a client-only list");
        assertTrue(client.contains("MerchantScreenFreeTradeMixin"));
        assertTrue(read(RESOURCES.resolve("fabric.mod.json"))
                        .contains("noncreatureaffinity.mixin.client.mixins.json"),
                "a mixin configuration that is not named in fabric.mod.json is never loaded");
    }

    @Test
    @DisplayName("the client renderer reads the panel through an accessor on the class that declares it")
    void theRendererReadsGeometryThroughAnAccessorOnTheDeclaringClass() {
        // Three failures were found only by STARTING THE CLIENT, each one aborting the mixin before anything drew:
        //   @Shadow field x was not located in the target class MerchantScreen ... No refMap loaded
        //   No candidates were found matching textRenderer ... in MerchantScreen
        //   No candidates were found matching x:I in MerchantScreen
        // They add up to one rule: neither a shadowed field nor an accessor walks up to a superclass. x, y,
        // backgroundWidth and backgroundHeight are declared by HandledScreen, so the accessor TARGETS HandledScreen;
        // the font and the scaled screen size come from MinecraftClient, which holds the font the screen draws with
        // and reports the size it is laid out against.
        String mixin = read(CLIENT_MIXIN);
        assertFalse(mixin.contains("@Shadow"),
                "no shadowed member may be used for these fields: the shadow never resolves here and the failure"
                        + " aborts the whole mixin at startup");
        assertFalse(mixin.contains("@Accessor"),
                "the renderer must not declare accessors itself; they belong in the accessor interface");
        assertTrue(mixin.contains("HandledScreenAccess"),
                "the panel geometry must be read through the accessor");

        String accessor = read(Path.of("src/main/java/com/nonid/affinity/mixin/client/HandledScreenAccess.java"));
        assertTrue(accessor.contains("@Mixin(net.minecraft.client.gui.screen.ingame.HandledScreen.class)"),
                "the accessor must target the class that DECLARES x/y/backgroundWidth/backgroundHeight: an accessor"
                        + " does not see inherited fields, and targeting the subclass crashed the client");
        for (String member : new String[] {"x", "y", "backgroundWidth", "backgroundHeight"}) {
            assertTrue(accessor.contains("@Accessor(\"" + member + "\")"),
                    member + " must be reachable through the accessor, or the panel cannot be placed");
        }
        assertTrue(mixin.contains("client.textRenderer"),
                "the font must come from the client, which is the font the screen draws with");
        assertTrue(mixin.contains("getScaledWidth()") && mixin.contains("getScaledHeight()"),
                "and the screen size from the window, which reports the same scaled size the screen is laid out for");
    }

    @Test
    @DisplayName("the client receives the window identity and the status, and sends the bind and the close")
    void theClientWiresBothDirections() {
        String receivers = read(CLIENT.resolve("AffinityClientNetworking.java"));
        assertTrue(receivers.contains("FreeTradeWindowPayload.ID"),
                "without this receiver the client never learns which window it is looking at");
        assertTrue(receivers.contains("FreeTradeStatusPayload.ID"),
                "without this receiver nothing the server decides can reach the screen");

        String lifecycle = read(CLIENT.resolve("NonCreatureAffinityClient.java"));
        assertTrue(lifecycle.contains("ScreenEvents.AFTER_INIT"),
                "the bind request has to be sent when the trade screen opens");
        assertTrue(lifecycle.contains("ScreenEvents.remove"),
                "the close report has to be sent when the screen goes away");

        String client = read(CLIENT.resolve("FreeTradeWindowClient.java"));
        assertTrue(client.contains("FreeTradeBindRequestPayload"),
                "the opening request must carry the window and its own request id");
        assertTrue(client.contains("FreeTradeClosePayload"),
                "the close must carry the window's whole identity, not just its number");
    }

    @Test
    @DisplayName("the client follows the recipe the screen moves to, so a switch cannot leave it waiting forever")
    void theClientFollowsTheScreenSelection() {
        // The defect this pins, reported after the first dev.3 review: the client noted its selection only when the
        // screen OPENED. The player then clicked another recipe, the server correctly sent a status for it, and the
        // store refused it as "about an offer we have moved on from" -- so the strip read "quote updating" for the
        // rest of the session. Every later status is for the new recipe too, so nothing could ever end the wait.
        String lifecycle = read(CLIENT.resolve("NonCreatureAffinityClient.java"));
        assertTrue(lifecycle.contains("ScreenEvents.beforeTick"),
                "the selection has to be followed while the window is open, not only when it opens");
        assertTrue(lifecycle.contains("followScreenSelection"),
                "and the tick callback must be the one that follows it");

        String client = read(CLIENT.resolve("FreeTradeWindowClient.java"));
        assertTrue(client.contains("FreeTradeStatusStore.selectOffer(recipeIndex(handler))"),
                "following the selection means noting the recipe the game's own handler reports");
        assertTrue(client.contains("public static void followScreenSelection(Screen screen)"),
                "the per-tick entry point must exist and be reachable from the screen event");

        // And the store must actually move the wait and drop the old answer, or noting it would change nothing.
        String store = read(CLIENT.resolve("FreeTradeStatusStore.java"));
        assertTrue(store.contains("pendingOfferIndex = offerIndex"),
                "noting a selection must move what the window is waiting for");
        assertTrue(store.contains("FreeTradeStatusStore.current = null;"),
                "and drop the previous recipe's answer instead of showing it for the new recipe");
    }

    @Test
    @DisplayName("a status is judged against the screen's recipe as it is NOW, not as of the last tick")
    void theReceiverSyncsBeforeJudging() {
        // The second review's timing finding: per-tick following still leaves a one-tick gap, because a reply can
        // arrive in the same tick as the click. Judged against the pre-click recipe it would be refused, and the
        // server would never send it again. The receiver must therefore note the screen's recipe BEFORE accepting.
        String receivers = read(CLIENT.resolve("AffinityClientNetworking.java"));
        int syncAt = receivers.indexOf("FreeTradeWindowClient.recipeIndex(");
        int acceptAt = receivers.indexOf("FreeTradeWindowClient.acceptFromServer(");
        assertTrue(syncAt > 0, "the receiver must read the recipe the screen is showing");
        assertTrue(acceptAt > 0, "and judge the status through the entry point that notes it first");
        assertTrue(syncAt < acceptAt,
                "the recipe must be read BEFORE the status is judged, or the reply can still be refused as stale");

        String client = read(CLIENT.resolve("FreeTradeWindowClient.java"));
        int selectAt = client.indexOf("FreeTradeStatusStore.selectOffer(screenRecipe)");
        int judgeAt = client.indexOf("FreeTradeStatusStore.accept(windowId, villagerUuid, instanceToken");
        assertTrue(selectAt > 0 && judgeAt > 0 && selectAt < judgeAt,
                "acceptFromServer must note the selection first and judge second");
    }

    @Test
    @DisplayName("the close carries the whole identity to the server, which checks all of it")
    void theCloseCarriesAndChecksTheWholeIdentity() {
        String payload = read(NET.resolve("FreeTradeClosePayload.java"));
        assertTrue(payload.contains("windowInstanceToken") && payload.contains("requestId"),
                "a close that carries only the syncId can name a window that was opened after the one it means");

        String networking = read(NET.resolve("AffinityNetworking.java"));
        assertTrue(networking.contains("handleTradeClose(context.player(),"),
                "the receiver must pass every field through, not just the window number");
        assertTrue(networking.contains("payload.windowInstanceToken()") && networking.contains("payload.requestId()"));

        String serving = read(SERVICE.resolve("TradeWindows.java"));
        assertTrue(serving.contains("closeMatching"),
                "the server must offer an identity-checking close for client-reported messages");
        String registry = read(SERVICE.resolve("TradeWindowRegistry.java"));
        assertTrue(registry.contains("window.instanceToken() != instanceToken")
                        && registry.contains("window.requestId() != requestId"),
                "and it must compare the token and the request, not only the number");
    }

    @Test
    @DisplayName("the close identity is CAPTURED at open time, not looked up from the store when the screen closes")
    void theCloseIdentityIsCapturedAtOpenTime() {
        // The second review's finding: putting the identity on the wire is not enough if it is read out of the
        // global cache at close time. Vanilla reuses syncIds, so a late removal callback from an old screen would
        // read a NEWER window's token and request out of the store and close that newer window.
        String lifecycle = read(CLIENT.resolve("NonCreatureAffinityClient.java"));
        assertTrue(lifecycle.contains("openedRequestId(openedWindow)"),
                "the request id must be captured when the screen is opened");
        assertTrue(lifecycle.contains("onScreenClosed(openedWindow, openedRequest)"),
                "and that captured value is what the removal callback must carry");

        String client = read(CLIENT.resolve("FreeTradeWindowClient.java"));
        assertTrue(client.contains("public static long openedRequestId(int windowId)"),
                "there must be an explicit capture point");
        assertTrue(client.contains("public static void onScreenClosed(int windowId, long requestId)"),
                "and the close must require the captured request id");
        assertFalse(client.contains("identityFor(windowId);"),
                "reading the identity from the store by number alone is the defect this pins");

        String store = read(CLIENT.resolve("FreeTradeStatusStore.java"));
        assertTrue(store.contains("requestId != FreeTradeStatusStore.requestId"),
                "the store must refuse an identity whose request id is not the current opening's");
    }

    @Test
    @DisplayName("the strip is wrapped from the RENDERED text, because the long lines have no arguments to split")
    void theStripWrapsRenderedText() {
        // The second review's finding: the layout used to break lines by splitting String ARGUMENTS at spaces. The
        // lines that actually overflow -- "Both payment materials are waived - nothing is taken from you", and the
        // Chinese ones -- are literal text in the language file with no arguments at all, so the wrapper could never
        // break them and the sentence stayed wider than the screen. Position clamping cannot fix that.
        String mixin = read(CLIENT_MIXIN);
        assertTrue(mixin.contains("wrapLines("),
                "the rendered Text must be wrapped by the font renderer's own line breaking");
        assertTrue(mixin.contains("Text.translatable("),
                "and wrapping must happen on the translated text, so the language file's own wording is what is "
                        + "measured");
        assertTrue(mixin.contains("drawText("),
                "wrapped lines are OrderedText, so they are drawn with the OrderedText overload");

        String layout = read(CLIENT.resolve("FreeTradeStatusLayout.java"));
        assertFalse(layout.contains("args()"),
                "the layout must not look at translation arguments: splitting those cannot break a literal line, "
                        + "and it would also duplicate the template text the language file already owns");
        assertFalse(layout.contains("import net.minecraft."),
                "the layout must stay free of Minecraft types so its rules remain testable without a game");
    }

    @Test
    @DisplayName("a half-built window is reported as waiting, and readiness never uses getTradeOffer")
    void ahalfBuiltWindowIsReportedAsWaiting() {
        // The defect reported from play: opening a qualifying window and touching nothing said "trade unavailable",
        // because the normal decision ran over a window that was still being built -- a default recipe index, no
        // offer list yet, or a villager that had not been told its customer. The fix is that the wait is decided
        // FIRST, from the window's own history, and short-circuits to "waiting".
        String serving = read(SERVICE.resolve("VillagerFreeTrades.java"));
        int historyAt = serving.indexOf("TradeWindows.hasEverResolved(");
        int quoteAt = serving.indexOf("boolean available = offerAvailable(");
        assertTrue(historyAt > 0,
                "the wait must be decided by whether this window has ever produced an answer");
        assertTrue(quoteAt > 0 && historyAt < quoteAt,
                "and that must happen BEFORE the real quote is computed, or the half-built window is judged");
        assertTrue(serving.contains("FreeTradeStatus.updating("),
                "and a window that is not ready must be reported as waiting, never as unavailable");

        // getTradeOffer() must NOT be used as the readiness signal: it is null whenever the payment slots are empty,
        // which is the normal state right after opening, so it would leave every fresh window on "waiting" until the
        // player happened to insert materials.
        int waitBlock = historyAt;
        String waitSource = serving.substring(Math.max(0, waitBlock - 300),
                Math.min(serving.length(), waitBlock + 500));
        assertFalse(waitSource.contains("getTradeOffer"),
                "readiness must not depend on getTradeOffer(): it is null for an empty payment slot, which is the"
                        + " state a freshly opened window is always in");

        // The rules themselves are covered by FreeTradeStatusDecideTest; this pins that the server routes through it.
        assertTrue(serving.contains("FreeTradeStatus.decide("),
                "the server must route the decision through the one place that separates waiting from a verdict");
    }

    @Test
    @DisplayName("the free offer is actually shown in the output slot, and showing it costs nothing")
    void theFreeOfferIsShownInTheOutputSlot() {
        // Vanilla fills the output slot from the payment slots, so with both empty -- the state a freshly opened
        // window is always in -- it clears the slot and resolves no offer. A free trade is the one case where a trade
        // is available with nothing to pay, so the item has to be placed there or the free offer cannot be claimed
        // without first inserting materials, which is the "still needs a click" problem.
        //
        // The placement is owned by ONE mixin, which is what this pins. Production of the output and spending of the
        // allowance are deliberately separate: the mixin shows the item, the completion hook spends exactly one
        // allowance when it is taken.
        String mixin = read(MIXIN.resolve("FreeTradeMerchantInventoryMixin.java"));
        assertTrue(mixin.contains("@Inject(method = \"updateOffers\""),
                "the free output must be produced from vanilla's own refresh, on its return");
        assertTrue(read(SERVICE.resolve("VillagerFreeTrades.java")).contains("refreshTradeInventory("),
                "and the refresh must actually be triggered, or the mixin never gets its chance while a window sits"
                        + " open and the player's eligibility changes");

        // Previewing must not consume anything: the allowance is spent by the completion hook, not by the producer.
        assertFalse(mixin.contains("commitFreeTrade") || mixin.contains("FreeTradeAccounts.store"),
                "showing the item must not spend the allowance: only taking it may");
        assertTrue(read(MIXIN.resolve("FreeTradeOutputSlotMixin.java")).contains("commitFreeTrade"),
                "and the completion hook is where the allowance is spent, exactly once");
    }

    @Test
    @DisplayName("customer identity is not the availability result, or out-of-stock is relabelled as 'not your trade'")
    void customerIdentityIsSeparateFromAvailability() {
        // The defect this pins: the availability result was passed as the customer argument. Out of stock makes the
        // trade unavailable, so every out-of-stock refusal was silently re-labelled "this is not your trade",
        // overwriting the OUT_OF_STOCK reason the server had just worked out.
        String serving = read(SERVICE.resolve("VillagerFreeTrades.java"));
        assertTrue(serving.contains("boolean currentCustomer = villager.getCustomer() == player;"),
                "the customer identity must be read from the villager, which is the only thing that answers it");
        int customerAt = serving.indexOf("boolean currentCustomer = villager.getCustomer() == player;");
        String decideCall = serving.substring(customerAt, Math.min(serving.length(), customerAt + 400));
        assertTrue(decideCall.contains("currentCustomer"),
                "and THAT value must be the one handed to the decision");
        assertFalse(decideCall.contains("decide(window.windowId(), offerIndex, true, available"),
                "the availability result must never be passed as the customer identity again");

        // The client-side rule about what the flag means is covered by FreeTradeStatusDecideTest.
    }

    @Test
    @DisplayName("a working window that stops working is unavailable, not 'still coming up'")
    void aWindowThatStopsWorkingIsNotWaiting() {
        // The over-correction this pins: treating every missing condition as "not initialised yet" meant a villager
        // that had died, been removed, or taken another customer kept showing a reassuring "quote updating" line for
        // ever. Initialisation is decided by the window's HISTORY; a failure after it has resolved is a real failure.
        String serving = read(SERVICE.resolve("VillagerFreeTrades.java"));
        int historyAt = serving.indexOf("TradeWindows.hasEverResolved(player, window.windowId())");
        int unusableAt = serving.indexOf("windowStillUsable(villager, player)");
        assertTrue(historyAt > 0, "the wait must be decided by whether the window has ever produced an answer");
        assertTrue(unusableAt > historyAt,
                "and the genuine-failure check must come AFTER it, so a window that worked and then broke is not "
                        + "sent back to the waiting line");
        assertTrue(serving.contains("Reason.TRADE_UNAVAILABLE"),
                "a working window that lost its customer or its villager is genuinely unavailable");
        assertTrue(read(SERVICE.resolve("TradeWindowRegistry.java")).contains("everResolved"),
                "the registry must carry the per-window history that makes the distinction possible");
    }

    @Test
    @DisplayName("the first verdict is reachable: readiness is not gated behind a previous verdict")
    void theFirstVerdictIsReachable() {
        // The deadlock, pinned at the source level as well as in WindowShapeTest: the method used to return the
        // waiting status whenever the window had never been resolved, while the only call that marks it resolved sat
        // after that return. Every fresh window waited for a verdict that only a verdict could unlock.
        String serving = read(SERVICE.resolve("VillagerFreeTrades.java"));
        int classifyAt = serving.indexOf("WindowShape shape = classify(");
        int resolveAt = serving.indexOf("TradeWindows.noteResolved(player, window.windowId())");
        assertTrue(classifyAt > 0, "the shape of the answer must be decided in one place");
        assertTrue(resolveAt > 0, "and a computed verdict must be recorded as the window's first success");
        assertTrue(classifyAt < resolveAt,
                "the verdict must be computed BEFORE it is recorded; recording first, or gating the verdict on the "
                        + "record, deadlocks a window that has never been resolved");

        // The shape decision must not read the history as a precondition. classify() takes it, but the READY branch
        // must be reachable with everResolved == false -- that is what WindowShapeTest asserts directly.
        assertTrue(serving.contains("public static WindowShape classify("),
                "the ordering rule must be a pure method, or it can only be exercised with a live server -- which is "
                        + "why the deadlock was not caught");
    }

    @Test
    @DisplayName("the keeping-open-a-window refresh is triggered, without anyone writing the output themselves")
    void theRefreshIsTriggered() {
        // Removing the duplicate writer also removed the only thing that CALLED the refresh. Without a call from the
        // per-tick pass, a window left open while eligibility or the allowance changes would update its status line
        // and keep an empty output slot: the free offer would only appear after switching recipe or touching the
        // payment slots -- the "you have to click something first" regression, from the other direction.
        String serving = read(SERVICE.resolve("VillagerFreeTrades.java"));
        int tickAt = serving.indexOf("public static void tickOpenWindows(");
        assertTrue(tickAt > 0, "the per-tick pass must exist");
        String tick = serving.substring(tickAt, serving.indexOf("\n    }", tickAt));
        assertTrue(tick.contains("refreshTradeInventory(player)"),
                "the per-tick pass must refresh the trade inventory, or nothing re-derives the output");
        assertTrue(tick.contains("sendStatus("),
                "and it must still report the status");
        int refreshAt = tick.indexOf("refreshTradeInventory(player)");
        int sendAt = tick.indexOf("sendStatus(player, window, observed)");
        assertTrue(refreshAt > 0 && sendAt > 0 && refreshAt < sendAt,
                "refreshing first means the status that goes out describes the state the player is about to see");

        // The refresh must be a TRIGGER only: one call into vanilla's own flow, and no writing of the output slot,
        // recipe or experience from this side.
        int methodAt = serving.indexOf("public static boolean refreshTradeInventory(");
        assertTrue(methodAt > 0, "there must be exactly one refresh entry point");
        String method = serving.substring(methodAt, Math.min(serving.length(), methodAt + 900));
        assertTrue(method.contains("updateOffers()"),
                "and it must run vanilla's own refresh, which the mod's mixin hooks");
        assertFalse(method.contains("setStack(2") || method.contains("setTradeOffer"),
                "but it must not write the item or the recipe itself: that is the mixin's job");
        assertFalse(serving.contains("showFreePreview"), "and the old second writer must not come back");
    }

    @Test
    @DisplayName("the shift-click loop guards the inconsistent pair too, because vanilla does not ask again")
    void theShiftLoopGuardsTheInconsistentPair() {
        // The finding this pins: canTakeItems is consulted ONCE before the shift-click loop, and the loop then calls
        // quickMove for every further portion -- so the one-shot check never sees the later iterations. The batch
        // guard must therefore apply the whole rule itself, including "an item with no recipe recorded behind it".
        // Reading an absent recipe as "nothing to refuse" let the loop keep moving items.
        String batch = read(MIXIN.resolve("FreeTradeMerchantScreenHandlerMixin.java"));
        int nullAt = batch.indexOf("if (offer == null)");
        assertTrue(nullAt > 0, "the batch guard must handle an unrecorded recipe explicitly");
        String branch = batch.substring(nullAt, Math.min(batch.length(), nullAt + 900));
        assertTrue(branch.contains("updateOffers()"),
                "it must refresh, so the inconsistent item leaves the screen");
        assertTrue(branch.contains("cir.setReturnValue(ItemStack.EMPTY)"),
                "and it must END the loop: returning EMPTY is what stops vanilla moving another item");
        assertTrue(batch.contains("FreeTradeGuard.verdict("),
                "and it must ask the same shared rule the click path asks");

        // The class's own documentation must state the truth about the one-shot check, since the earlier claim that
        // every move passes through canTakeItems was wrong.
        assertTrue(batch.contains("consulted ONCE")
                        || batch.contains("consulted once"),
                "the documentation must record that canTakeItems is consulted once, not per iteration");
    }

    @Test
    @DisplayName("an item with no recorded recipe is refused, because that pair is inconsistent")
    void anItemWithoutARecipeIsRefused() {
        // The caller established that the output slot HAS an item before this guard runs, so an unrecorded recipe is
        // not "nothing to take": it is an inconsistent window. Vanilla and this mod's own mixin both fill the slot
        // and record the recipe together, so the pairing never legitimately happens.
        String guard = read(MIXIN.resolve("FreeTradeSlotGuardMixin.java"));
        int nullAt = guard.indexOf("if (offer == null)");
        assertTrue(nullAt > 0, "the guard must handle an unrecorded recipe explicitly");
        String branch = guard.substring(nullAt, Math.min(guard.length(), nullAt + 2200));
        assertTrue(branch.contains("updateOffers()"),
                "it must refresh, so the inconsistent item leaves the screen");
        assertTrue(branch.contains("cir.setReturnValue(false)"),
                "and it must REFUSE: an inconsistent window hands nothing over");
        assertTrue(branch.contains("return;"),
                "the branch must not fall through to the normal checks");
    }

    @Test
    @DisplayName("the output slot has exactly one writer, and it sets recipe, item and experience together")
    void theOutputSlotHasOneWriter() {
        // Two successive reviews found output being written in two places. The free case already has an owner that
        // sets all three things as one consistent state, and a second writer is what produced the mismatch: vanilla
        // resolves an offer against the PAYMENT MATERIALS, so it can settle on a different recipe than the selected
        // index names, and overwriting only the item left the window showing one recipe's goods while recording
        // another's recipe -- receive A, be charged or credited for B.
        String serving = read(SERVICE.resolve("VillagerFreeTrades.java"));
        assertFalse(serving.contains("showFreePreview"),
                "the service must not fill the output slot at all: that is the mixin's job, and a second writer is "
                        + "what caused the recipe mismatch");
        assertFalse(serving.contains("setStack(2"),
                "and it must not write slot 2 directly");

        String mixin = read(MIXIN.resolve("FreeTradeMerchantInventoryMixin.java"));
        assertTrue(mixin.contains("this.tradeOffer = selected;"),
                "the owner records the recipe");
        assertTrue(mixin.contains("this.setStack(2, selected.copySellItem());"),
                "and the item");
        assertTrue(mixin.contains("this.merchantRewardedExperience = selected.getMerchantExperience();"),
                "and the experience -- all three together, so they cannot disagree");
        assertTrue(mixin.contains("if (this.tradeOffer != null && !this.getStack(2).isEmpty())"),
                "and it stands down when vanilla already produced a paid result");
        assertTrue(mixin.contains("selected.isDisabled()"),
                "while refusing to serve an out-of-stock recipe for free");
    }

    @Test
    @DisplayName("extraction is checked before the item moves, and an empty shelf arrives through the availability rule")
    void extractionIsCheckedBeforeTheItemMoves() {
        // The free output is produced by the mixin, which records the recipe, so the guard always has a recipe to
        // judge -- there is no "empty recipe means free" path, and inventing one would open a route with no
        // eligibility check at all.
        String guard = read(MIXIN.resolve("FreeTradeSlotGuardMixin.java"));
        assertTrue(guard.contains("boolean available = VillagerFreeTrades.tradeAvailable("),
                "the guard must re-establish availability itself, before anything moves");
        assertTrue(guard.contains("FreeTradeGuard.verdict(true, available, free, canPay)"),
                "and it must ask the shared rule");

        // Out of stock is not a second check: tradeAvailable() is false for a disabled offer, so a sold-out recipe
        // is refused through "not available" whatever the player is holding. Two checks for one fact could disagree.
        String rules = read(SERVICE.resolve("FreeTradeGuard.java"));
        assertTrue(rules.contains("!available"),
                "the availability rule must be the one that refuses a sold-out recipe");
        assertTrue(read(SERVICE.resolve("VillagerFreeTrades.java")).contains("offer.isDisabled()"),
                "and availability counts a disabled offer as unsellable, which is what stock exhaustion produces");
    }

    @Test
    @DisplayName("out of stock is a state of its own, mapped on the server and shown on the client")
    void outOfStockIsItsOwnState() {
        String status = read(SERVICE.resolve("FreeTradeStatus.java"));
        assertTrue(status.contains("OUT_OF_STOCK"),
                "without its own reason an empty shelf and a missing recipe are the same sentence to the player");
        assertTrue(status.contains("showAsOutOfStock"));

        String serving = read(SERVICE.resolve("VillagerFreeTrades.java"));
        assertTrue(serving.contains("offerOutOfStock"),
                "the server is the side that can tell an emptied shelf from a vanished recipe");
        assertTrue(serving.contains("Reason.OUT_OF_STOCK"));

        String text = read(CLIENT.resolve("FreeTradeStatusText.java"));
        assertTrue(text.contains("!status.showAsFree()"),
                "non-free states remain server diagnostics, but only an eligible free offer has a panel");
    }

    @Test
    @DisplayName("a price statement names the item, not only the count")
    void priceStatementsNameTheItem() {
        String status = read(SERVICE.resolve("FreeTradeStatus.java"));
        assertTrue(status.contains("firstItemName"),
                "a bare count is not a price: the same number can mean different items");
        String payload = read(NET.resolve("FreeTradeStatusPayload.java"));
        assertTrue(payload.contains("firstItemName"),
                "the name has to travel to the client, which does not hold the offer list");
        String serving = read(SERVICE.resolve("VillagerFreeTrades.java"));
        assertTrue(serving.contains("firstPaymentName"),
                "and it must be built from the offer's own first payment item, not guessed");
    }

    @Test
    @DisplayName("the protocol carries the request id both ways, so an answer can be tied to its question")
    void theProtocolEchoesTheRequestId() {
        for (String name : new String[] {"FreeTradeBindRequestPayload", "FreeTradeWindowPayload",
                "FreeTradeStatusPayload"}) {
            String source = read(NET.resolve(name + ".java"));
            assertTrue(source.contains("requestId"),
                    name + " must carry the request id: the syncId alone cannot tell two openings apart");
        }
        String networking = read(NET.resolve("AffinityNetworking.java"));
        assertTrue(networking.contains("payload.requestId()"),
                "the server must echo the request id it received rather than invent one");
        assertTrue(networking.contains("TradeWindows.register(player, windowId, villager, requestId)"));
    }

    @Test
    @DisplayName("the server resolves the window from its own handler, and never through a global syncId lookup")
    void theServerNeverTrustsASyncIdAlone() {
        String serving = read(SERVICE.resolve("VillagerFreeTrades.java"));
        assertFalse(serving.contains("TradeWindows.playerWithWindow("),
                "a syncId is only unique per player, so resolving a player from it can land on somebody else");

        String selection = read(MIXIN.resolve("FreeTradeWindowSelectionMixin.java"));
        assertFalse(selection.contains("TradeWindows.playerWithWindow("),
                "the selection hook runs inside its own handler and must read the customer from there");
        assertTrue(selection.contains("this.merchant.getCustomer()"),
                "the customer has to come from the handler the hook is inside");
    }

    @Test
    @DisplayName("the status is resent only when the answer changed, and carries the window's quote number")
    void theStatusIsSentOnChangeAndNumbered() {
        String serving = read(SERVICE.resolve("VillagerFreeTrades.java"));
        assertTrue(serving.contains("TradeWindows.lastSent(player, window.windowId())"),
                "an unchanged answer must not be resent");
        assertTrue(serving.contains("TradeWindows.recordSent("),
                "the quote number has to come from the registry, which is what makes it per window");
        assertTrue(serving.contains("tickOpenWindows"),
                "every change source is covered by the per-tick comparison rather than by a list of call sites");
        assertTrue(read(Path.of("src/main/java/com/nonid/affinity/NonCreatureAffinity.java"))
                        .contains("tickOpenWindows"),
                "the tick comparison must be registered, or nothing ever notices a change");
    }
}
