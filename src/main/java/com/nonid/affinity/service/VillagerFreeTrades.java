package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.data.AffinityHolder;
import com.nonid.affinity.data.CreatureAffinityData;
import com.nonid.affinity.data.FreeTradeHeroEpoch;
import com.nonid.affinity.data.PlayerAffinity;
import com.nonid.affinity.mixin.MerchantInventoryMerchantAccessor;
import com.nonid.affinity.mixin.MerchantScreenHandlerAccess;
import com.nonid.affinity.mixin.ZombieVillagerCureAccessor;
import com.nonid.affinity.net.FreeTradeStatusPayload;
import com.nonid.affinity.service.FreeTradeStatus.Reason;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.village.Merchant;
import net.minecraft.village.MerchantInventory;
import net.minecraft.village.TradeOffer;
import org.jetbrains.annotations.Nullable;

/**
 * The server-side coordinator for close-friend free trades: the only place that turns the pure rules into
 * something a villager screen does.
 *
 * <p>Everything decision-shaped lives in {@link FreeTradeQuota}, {@link FreeTradeAccounts}, {@link CureFactRules}
 * and {@link FreeTradeQuote}; this class supplies the game objects and the ordering, and nothing else, so the
 * rules stay testable without a world.
 */
public final class VillagerFreeTrades {

    private VillagerFreeTrades() {
    }

    /** Whether this player counts as a close friend of this villager RIGHT NOW. */
    public static boolean isCloseFriend(VillagerEntity villager, PlayerEntity player) {
        PlayerAffinity relation = AffinityService.getRelation(villager, player);
        if (relation == null) {
            return false;
        }
        // Current stage, never the persisted history flag: demotion suspends the allowance.
        return RelationshipStages.friend(relation.getAffinity(), NonCreatureAffinity.getConfig());
    }

    /**
     * Whether this offer is still sellable to this player, re-checked at extraction time rather than assumed.
     *
     * <p>Generating an output slot and extracting from it are different moments: between them the recipe list can
     * be rebuilt by a restock, the last use can be taken, another player can become the customer, or the villager
     * can be removed. Passing {@code true} here (which is what this call site used to do) made every one of those
     * changes invisible, so a stale free offer stayed "eligible".
     *
     * <p>Deliberately no world change and no consumption: it only reads.
     */
    public static boolean tradeAvailable(VillagerEntity villager, PlayerEntity player, TradeOffer offer) {
        if (villager.isRemoved() || !villager.isAlive()) {
            return false;
        }
        if (villager.getCustomer() != player) {
            return false;
        }
        if (offer.isDisabled()) {
            // uses >= maxUses: out of stock is out of stock, free or not.
            return false;
        }
        // Identity-based: TradeOffer does not define value equality, so this asks "is this still one of the
        // villager's current recipes", which is exactly the question after a restock replaced the list.
        return villager.getOffers().contains(offer);
    }

    /**
     * Whether the player's CURRENT payment slots actually satisfy this offer, without consuming anything.
     *
     * <p>Uses vanilla's own {@code matchesBuyItems}, tried in both orders because the completion path tries
     * {@code depleteBuyItems} in both orders. Emptiness is not a proxy for this: a wrong item, too few of the
     * right one, or a missing second material all leave a slot non-empty and the trade unpayable.
     */
    public static boolean canPayNow(TradeOffer offer, ItemStack firstSlot, ItemStack secondSlot) {
        return offer.matchesBuyItems(firstSlot, secondSlot) || offer.matchesBuyItems(secondSlot, firstSlot);
    }

    /**
     * The one quote for one offer, with every pending event applied first.
     *
     * <p>Applying pending events here (and again before a commit) is what makes the ordering safe: an event that
     * was recorded but not yet synchronised is folded in BEFORE anything is decided or spent, so a trade can never
     * be consumed and then have an older event restore it.
     *
     * @param tradeAvailable whether the trade may still be offered at all, from
     *                       {@link #tradeAvailable(VillagerEntity, PlayerEntity, TradeOffer)}. NOT "materials are
     *                       in the slots": a free trade is exactly the case where they need not be.
     */
    public static FreeTradeQuote quote(VillagerEntity villager, PlayerEntity player, TradeOffer offer,
                                       boolean tradeAvailable) {
        PlayerAffinity relation = AffinityService.getRelation(villager, player);
        int remaining = 0;
        long revision = 0L;
        if (relation != null) {
            FreeTradeQuota.State state =
                    FreeTradeAccounts.synchronizedState(relation, FreeTradeHeroEpoch.of(player)).state();
            remaining = state.remaining();
            revision = state.quotaRevision();
        }
        return FreeTradeQuote.quote(isCloseFriend(villager, player), remaining, revision,
                offer.getOriginalFirstBuyItem().getCount(), offer.getDemandBonus(), offer.getPriceMultiplier(),
                offer.getSpecialPrice(), offer.getOriginalFirstBuyItem().getMaxCount(), tradeAvailable);
    }

    /**
     * Commit one free trade: spend exactly one allowance and write it back.
     *
     * <p>Called only after the goods have actually been delivered, never from a preview, a match check or a
     * "can this be taken" question. The pending events are applied first, for the same reason as in
     * {@link #quote}.
     */
    public static void commitFreeTrade(VillagerEntity villager, ServerPlayerEntity player, TradeOffer offer) {
        PlayerAffinity relation =
                AffinityService.relationOrCreate(villager, player, NonCreatureAffinity.getConfig());
        if (relation == null) {
            return;
        }
        FreeTradeQuota.State synced =
                FreeTradeAccounts.synchronizedState(relation, FreeTradeHeroEpoch.of(player)).state();
        FreeTradeAccounts.store(relation, FreeTradeQuota.spend(synced));
        NonCreatureAffinity.LOGGER.info(
                "[NoN Affinity] free trade taken: villager={} player={} offer={} left={} total={}",
                villager.getUuid(), player.getUuid(), offer.getSellItem().getName().getString(),
                Math.max(0, synced.remaining() - 1), synced.totalFreeTrades() + 1);
    }

    // ------------------------------------------------------------------
    // Telling the window what the server decided
    // ------------------------------------------------------------------

    /**
     * What can be said about an open trade window right now.
     *
     * <p>Separated from {@link #statusForWindow} so the ordering rule can be exercised without a game. That is not
     * ceremony: an earlier version of this logic deadlocked every fresh window, and no test caught it because the
     * rule existed only inside a method that needs a live server.
     */
    public enum WindowShape {
        /** Nothing can be judged yet and this window has never been judged: it is still coming up. */
        WAITING,
        /** The window is no longer usable: the villager is gone, or it has taken another customer. */
        OBJECT_UNAVAILABLE,
        /** The villager has no trade list at all: a shelf that was there and is now empty. */
        NO_OFFERS,
        /** The window works but names no recipe yet. Still waiting, because the next status names one. */
        NO_RECIPE,
        /** Everything needed is present, so a real verdict can be computed right now. */
        READY;

        /**
         * Whether this outcome is the window's "still coming" line.
         *
         * <p>Two of the five outcomes are that line -- a window that has never been judged, and a working window that
         * has not named a recipe yet -- and naming the property here is what stops a caller having to remember which.
         * The distinction that matters to the player is only ever "is this a refusal or not".
         */
        public boolean isWaiting() {
            return this == WAITING || this == NO_RECIPE;
        }
    }

    /**
     * Classify a window without touching the game.
     *
     * <p><b>The rule this encodes, and the mistake it prevents.</b> A window is "still coming up" only when it has
     * never produced an answer AND something needed is not ready yet. A previous success must NOT be the precondition
     * for the first success: doing that leaves a fresh window waiting for a verdict that only a verdict could unlock,
     * so it waits for ever. Readiness is therefore decided from what is present right now, and the history is
     * consulted only to choose the words for a window that cannot be judged.
     *
     * @param everResolved whether this window has ever produced a real answer
     * @param windowUsable whether the villager is present and is still this player's customer
     * @param hasOffers    whether the villager has a trade list to index into
     * @param recipeNamed  whether the window names a recipe that exists in that list
     */
    public static WindowShape classify(boolean everResolved, boolean windowUsable, boolean hasOffers,
                                       boolean recipeNamed) {
        if (!windowUsable) {
            // Before the first verdict this is the ordinary state of a window being opened -- the villager is only
            // told who its customer is as part of opening it. After one, it is a change that happened.
            return everResolved ? WindowShape.OBJECT_UNAVAILABLE : WindowShape.WAITING;
        }
        if (!hasOffers) {
            return everResolved ? WindowShape.NO_OFFERS : WindowShape.WAITING;
        }
        if (!recipeNamed) {
            // A working window that has not named a recipe yet. Always waiting: there is nothing to refuse.
            return WindowShape.NO_RECIPE;
        }
        // Everything present, so a verdict is computable RIGHT NOW, whether or not one has been reached before. This
        // is the branch whose absence made the first quote impossible.
        return WindowShape.READY;
    }

    /**
     * The status for one window, with the window's instance token, or null when there is nothing to say.
     *
     * <p>Returns null only for "this is not a window I track", which the caller must treat as "send nothing":
     * inventing a status for an unknown window is how a screen ends up describing somebody else's trade.
     *
     * <p>The quote number is left at zero here and filled in by {@link #sendStatus} at the moment the answer is
     * actually sent: it counts SENT answers, so a status that is computed and then found unchanged must not
     * consume a number.
     *
     * @param offerIndex the recipe the window has selected, or -1 when it has none
     */
    @Nullable
    public static TradeWindows.StatusForWindow statusForWindow(TradeWindows.WindowState window,
                                                              ServerPlayerEntity player, int offerIndex) {
        VillagerEntity villager = window.villager();
        List<TradeOffer> offers = villager.getOffers();
        // The decision about WHICH KIND of answer to give is made here, in one place, from what is present now.
        WindowShape shape = classify(TradeWindows.hasEverResolved(player, window.windowId()),
                windowStillUsable(villager, player) && isThisVillagersWindow(villager, player),
                !offers.isEmpty(),
                offerIndex >= 0 && offerIndex < offers.size());
        switch (shape) {
            case OBJECT_UNAVAILABLE, NO_OFFERS -> {
                return realStatus(window, offerIndex, null, Reason.TRADE_UNAVAILABLE, false);
            }
            case WAITING, NO_RECIPE -> {
                return waitingFor(window, offerIndex);
            }
            case READY -> {
                // A verdict is computable; fall through to it.
            }
        }

        TradeOffer offer = offers.get(offerIndex);
        boolean available = offerAvailable(villager, player, offer);
        FreeTradeQuote quote = quote(villager, player, offer, available);
        // The quote cannot express "the shelf is empty but the recipe is fine" (it reports TRADE_NOT_AVAILABLE for
        // both an empty shelf and a missing recipe), so the distinction is made here, where the offer object is
        // still in hand, and handed to the status as the reason to use when the quote refuses on availability.
        Reason unavailableReason = offerOutOfStock(villager, player, offer)
                ? Reason.OUT_OF_STOCK
                : Reason.TRADE_UNAVAILABLE;
        // The customer identity is its own argument and is NOT the availability result. The two were once the same
        // value here, which quietly relabelled every availability refusal -- out of stock included -- as "this is
        // not your trade", overwriting the OUT_OF_STOCK reason the paragraph above exists to preserve.
        boolean currentCustomer = villager.getCustomer() == player;
        FreeTradeStatus verdict = FreeTradeStatus.decide(window.windowId(), offerIndex, true, currentCustomer,
                quote, quote.allowanceLeft(), FreeTradeQuota.FULL_ALLOWANCE, firstPaymentName(offer),
                unavailableReason);
        PlayerAffinity relation = AffinityService.getRelation(villager, player);
        boolean everGranted = relation != null && (relation.getLastAppliedHeroEpoch() > 0
                || relation.getLastAppliedCureEpoch() > 0 || relation.getTotalFreeTrades() > 0
                || relation.getRemainingFreeTrades() > 0);
        verdict = verdict.withAllowanceHistory(everGranted);
        // The verdict above IS the first success, so this is where the window earns its history -- recorded after the
        // verdict exists, never as a requirement for producing one.
        TradeWindows.noteResolved(player, window.windowId());
        return new TradeWindows.StatusForWindow(window.instanceToken(), window.requestId(), verdict);
    }

    /**
     * A status for a window that has already produced an answer, with an explicit reason.
     *
     * <p>Always a refusal or an out-of-stock notice: this is the "the window worked and something changed" answer, so
     * it carries the reason the caller established rather than re-deriving anything.
     *
     * @param offer               the offer the verdict is about, or null when there is none to name
     * @param outOfStock          whether the shelf, rather than the trade, is the problem
     * @param fallbackUnavailable the reason to use when no better one can be established
     */
    private static TradeWindows.StatusForWindow realStatus(TradeWindows.WindowState window, int offerIndex,
                                                           @Nullable TradeOffer offer,
                                                           Reason fallbackUnavailable, boolean outOfStock) {
        Reason reason = outOfStock ? Reason.OUT_OF_STOCK : fallbackUnavailable;
        FreeTradeStatus status = new FreeTradeStatus(window.windowId(), Math.max(-1, offerIndex), false, 0,
                FreeTradeQuota.FULL_ALLOWANCE, 0, offer == null ? "" : firstPaymentName(offer), reason, 0L);
        return new TradeWindows.StatusForWindow(window.instanceToken(), window.requestId(), status);
    }

    /** The waiting status for a window, so every "still coming" answer is built in one place. */
    private static TradeWindows.StatusForWindow waitingFor(TradeWindows.WindowState window, int offerIndex) {
        return new TradeWindows.StatusForWindow(window.instanceToken(), window.requestId(),
                FreeTradeStatus.updating(window.windowId(), offerIndex));
    }

    /**
     * Whether the object behind this window is still there and still this player's to trade with.
     *
     * <p>False is a change that happened to a window that was working: the villager died, was removed, or took
     * somebody else as its customer. It is not a window that has not finished coming up -- that case is decided
     * before this is ever asked, by whether the window has produced an answer at all.
     */
    private static boolean windowStillUsable(VillagerEntity villager, ServerPlayerEntity player) {
        return !villager.isRemoved() && villager.isAlive() && villager.getCustomer() == player;
    }

    /**
     * Whether the window being judged is really this villager's window for this player.
     *
     * <p>Read-only, and it does not consume anything: it asks the server's own handler and changes nothing. Asked
     * only once a window has already produced an answer, to confirm the answer is still about the same trade: two
     * players trading at once, or a swap to another villager's window, must not be described with this villager's
     * offers.
     */
    private static boolean isThisVillagersWindow(VillagerEntity villager, ServerPlayerEntity player) {
        if (!(player.currentScreenHandler instanceof MerchantScreenHandler handler)) {
            return false;
        }
        return ((MerchantScreenHandlerAccess) handler).noncreatureaffinity$merchant() == villager;
    }

    /**
     * Whether this recipe has simply been traded out, as opposed to being gone or otherwise unusable.
     *
     * <p>Three states look alike from the quote's side and are not alike to the player:
     *
     * <ul>
     *   <li><b>Out of stock</b> -- the recipe is still one of the villager's offers and the player is still its
     *       customer, but every use has been taken. A restock restores it, so the window should say "out of stock"
     *       rather than "unavailable".</li>
     *   <li><b>Traded out with no restock coming</b> -- vanilla disables all offers when {@code canRefreshTrades} is
     *       false; nothing will bring this back on its own, so it is not described as a temporary shelf state.</li>
     *   <li><b>Not on offer at all</b> -- the recipe was replaced by a restock, or this player is no longer the
     *       customer. Those are "unavailable", not "out of stock".</li>
     * </ul>
     *
     * <p>Read-only: it asks the offer and the villager, and changes neither.
     */
    public static boolean offerOutOfStock(VillagerEntity villager, PlayerEntity player, TradeOffer offer) {
        if (villager.isRemoved() || !villager.isAlive() || villager.getCustomer() != player) {
            return false;
        }
        if (!villager.getOffers().contains(offer)) {
            // Identity-based, exactly as tradeAvailable is: TradeOffer has no value equality, so this is "is this
            // still one of the villager's recipes", which is the question after a restock replaced the list.
            return false;
        }
        if (villager.isLeveledMerchant() && !villager.canRefreshTrades()) {
            // Traded out for good: every offer is disabled and no restock is due. "Unavailable" is the honest word.
            return false;
        }
        return offer.isDisabled();
    }

    /**
     * What a paid trade charges first, in the player's language, or an empty string when there is nothing to name.
     *
     * <p>Carried to the client so a sentence about the price can say which item it is about. A bare number is not a
     * price: a window that shows "20" while the trade may charge 20 emeralds or 20 of something else has told the
     * player nothing they can act on.
     */
    public static String firstPaymentName(TradeOffer offer) {
        ItemStack first = offer.getOriginalFirstBuyItem();
        if (first == null || first.isEmpty()) {
            return "";
        }
        return first.getName().getString();
    }

    /**
     * Send the current status for one offer of one open window, if it is not already what the window was told.
     *
     * <p>Call it whenever the answer can have changed: when the window opens, when the player switches recipe,
     * after a completed trade, after an allowance refresh, and after anything that can move an affinity or a
     * price. The client displays only what it was told, so a moment that changes the answer and does not send is
     * a window that shows a stale price.
     *
     * <p><b>The comparison is the point, not an optimisation.</b> The quote number counts CHANGES, so a status
     * that is identical to the last one sent must not go out again with a higher number: the number is the
     * client's way of telling a fresher answer from an older one, and a stream of identical statuses carrying
     * ever-higher numbers would make every reordered packet look fresh. Comparing the whole status also makes
     * this the one mechanism that covers every source of change -- an affinity drop, a restock, a price move, a
     * configuration reload -- without each of them needing its own send call, and without the client having to be
     * told twice about the same thing.
     *
     * @param offerIndex the selected recipe, or -1 for "none selected"
     */
    public static void sendStatus(ServerPlayerEntity player, TradeWindows.WindowState window, int offerIndex) {
        TradeWindows.StatusForWindow computed = statusForWindow(window, player, offerIndex);
        if (computed == null) {
            return;
        }
        FreeTradeStatus status = computed.status();
        FreeTradeStatus last = TradeWindows.lastSent(player, window.windowId());
        if (status.equals(last)) {
            // The window already shows exactly this. Nothing is sent, and no quote number is consumed.
            return;
        }
        if (!ServerPlayNetworking.canSend(player, FreeTradeStatusPayload.ID)) {
            return;
        }
        long quoteNumber = TradeWindows.recordSent(player, window.windowId(), status);
        // The sent status carries the number, and the record of what was sent carries the SAME object, number
        // included: remembering the unnumbered one would make the next comparison see a difference that is only
        // the number itself, and the window would be resent the same answer on every tick.
        FreeTradeStatus numbered = new FreeTradeStatus(status.windowId(), status.offerIndex(), status.free(),
                status.remaining(), status.total(), status.paidFirstCount(), status.firstItemName(),
                status.reason(), quoteNumber);
        ServerPlayNetworking.send(player, FreeTradeStatusPayload.of(window.windowId(), window.villagerUuid(),
                computed.instanceToken(), computed.requestId(), numbered));
    }

    /**
     * Send the status for one offer of the window this player currently has open.
     *
     * <p>Resolves the window from the SERVER's own view of the player's screen: the caller names only the window
     * number, and a number that does not belong to a window this module registered sends nothing.
     *
     * @param offerIndex the selected recipe, or -1 for "none selected"
     */
    public static void sendStatus(ServerPlayerEntity player, int windowId, int offerIndex) {
        VillagerEntity villager = villagerOfWindow(player);
        if (villager == null) {
            return;
        }
        TradeWindows.WindowState window = TradeWindows.current(player, windowId, villager);
        if (window == null) {
            return;
        }
        sendStatus(player, window, offerIndex);
    }

    /**
     * The recipe the server's own handler for this player has selected, or -1.
     *
     * <p>The value lives on the handler's trade inventory, not on the handler itself, and it is the SERVER's copy:
     * a client that claimed a different recipe would be answered about the one its own window is really showing.
     *
     * <p>Two accessors are read in sequence rather than one composite accessor on the handler, because the mixin
     * interface can only hold accessors: a {@code default} method there is rejected at startup.
     *
     * <p><b>Why the cast is guarded.</b> The accessors only exist once the mixin has been applied to the class the
     * running game loaded. If it has not been -- the mod absent, a mixin config that failed to apply -- the cast
     * throws, and an exception raised on the per-tick path would break the window instead of merely failing to
     * describe it. Returning -1 says "no recipe observed", which the caller already handles by showing the window's
     * waiting line.
     */
    public static int selectionOfHandler(ServerPlayerEntity player) {
        if (!(player.currentScreenHandler instanceof MerchantScreenHandler handler)) {
            return -1;
        }
        try {
            MerchantInventory inventory = ((MerchantScreenHandlerAccess) handler)
                    .noncreatureaffinity$merchantInventory();
            return ((MerchantInventoryMerchantAccessor) inventory).noncreatureaffinity$offerIndex();
        } catch (ClassCastException notApplied) {
            NonCreatureAffinity.LOGGER.warn(
                    "[NoN Affinity] the trade-window accessors are not applied to {}; the free-trade display "
                            + "cannot read this window's selected recipe.",
                    handler.getClass().getName());
            return -1;
        }
    }

    /**
     * The one change check for every open trade window: drop the ones whose screen is gone, and tell the rest
     * anything that moved.
     *
     * <p><b>Why a periodic comparison instead of a send call at every possible source of change.</b> The answer a
     * window shows depends on the affinity, the allowance, the hero effect, the prices, the stock and the
     * configuration -- and each of those has several places it can move, including ones this module does not own
     * (a cure performed by a command, a demand change, a restock on the villager's own schedule). Listing them all
     * would be a list that is wrong the moment anything is added. Comparing what the window WOULD be told against
     * what it was last told is one mechanism that cannot miss a source, and it never sends an unchanged answer, so
     * an idle window costs a comparison per tick and produces no traffic at all.
     *
     * <p>Only players who actually have a window open are visited: the check starts from the server's own record
     * of open windows, so no villager is scanned and no world is searched.
     *
     * <p>The same pass is what notices a window that closed without the client saying so -- a disconnect, a death,
     * a teleport, another mod closing the screen. The server does not have to trust a close message to stop
     * believing a window is open.
     */
    public static void tickOpenWindows(MinecraftServer server) {
        List<UUID> tracked = TradeWindows.trackedPlayers();
        if (tracked.isEmpty()) {
            return;
        }
        for (UUID playerUuid : tracked) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
            if (player == null) {
                // The record outlived the connection: nothing will ever answer for it again.
                TradeWindows.forget(playerUuid);
                continue;
            }
            int windowId = TradeWindows.trackedWindowId(playerUuid);
            if (windowId < 0) {
                continue;
            }
            boolean stillOpen = player.currentScreenHandler instanceof MerchantScreenHandler handler
                    && handler.syncId == windowId;
            if (!stillOpen) {
                // The screen is gone (or replaced). Dropping the record here is what keeps a stale window from
                // being answered: nothing else will ever clear it, and a reused syncId would otherwise match it.
                TradeWindows.forgetWindow(player, windowId);
                continue;
            }
            VillagerEntity villager = villagerOfWindow(player);
            if (villager == null) {
                TradeWindows.forgetWindow(player, windowId);
                continue;
            }
            TradeWindows.WindowState window = TradeWindows.current(player, windowId, villager);
            if (window == null) {
                continue;
            }
            int observed = TradeWindows.selectedOfferIndex(player, windowId);
            if (observed < 0) {
                observed = selectionOfHandler(player);
            }
            // Refresh the trade inventory before reporting, so the window's output slot is re-derived from the
            // CURRENT state every tick.
            //
            // This is a TRIGGER, not a second writer: updateOffers() is where the mod's mixin produces the free
            // output, and it only runs when something calls it. Without a call from here, a window that stays open
            // while the player's eligibility or allowance changes would update its status line but keep an empty
            // output slot -- the free offer would only appear after switching recipe or touching the payment slots.
            // That is the "you have to click something first" regression, arriving from the other direction: the
            // previous delivery had a writer here, and removing it removed the trigger with it.
            //
            // Called unconditionally, because the refresh is also what CLEARS an output that is no longer justified.
            // It is cheap: it reads the payment slots, and with them empty it clears the slot and resolves nothing.
            if (villager instanceof RelationshipDiscountRefresh refresh
                    && refresh.noncreatureaffinity$refreshRelationshipDiscount(player)) {
                player.sendTradeOffers(windowId, villager.getOffers(), villager.getVillagerData().level(),
                        villager.getExperience(), villager.isLeveledMerchant(), villager.canRefreshTrades());
            }
            refreshTradeInventory(player);
            sendStatus(player, window, observed);
        }
    }

    /**
     * Re-derive this window's trade output from the current state.
     *
     * <p>The single entry point for that, so the mod never writes the output slot, recipe or experience itself: the
     * refresh runs vanilla's own logic and the mod's mixin, which own those three and set them together.
     *
     * @return whether a window was refreshed
     */
    public static boolean refreshTradeInventory(ServerPlayerEntity player) {
        if (!(player.currentScreenHandler instanceof MerchantScreenHandler handler)) {
            return false;
        }
        ((MerchantScreenHandlerAccess) handler).noncreatureaffinity$merchantInventory().updateOffers();
        return true;
    }

    /** Forget this player's window record: the connection is gone or the server is stopping. */
    public static void forgetWindow(UUID playerUuid) {
        TradeWindows.forget(playerUuid);
    }

    // ------------------------------------------------------------------
    // The free trade's output slot
    // ------------------------------------------------------------------

    // There is deliberately no "fill the output slot" method here any more.
    //
    // An earlier version had one, called every tick after vanilla's refresh. It was wrong, and the way it was wrong is
    // worth recording: vanilla's updateOffers() matches an offer against the PAYMENT MATERIALS, so it can resolve a
    // different recipe than the selected index names. Writing the selected recipe's own item over that left the
    // window showing one recipe's goods while the inventory recorded another's recipe -- the player would receive A
    // and be charged or credited for B. Comparing item counts did not help, because the defect was the RECIPE
    // ATTRIBUTION, not the amount.
    //
    // The free case already has exactly one owner, and it sets all three things together:
    // FreeTradeMerchantInventoryMixin, hooked on updateOffers's return, writes the selected offer, its sell item and
    // its experience as one consistent state. Anything else writing to that slot is a second source of truth, so
    // there is none.
    //
    // What this class DOES provide is the TRIGGER: refreshTradeInventory, called every tick for an open window, so a
    // window left open while the player's eligibility or allowance changes re-derives its output. Triggering the
    // refresh and writing the output are different jobs, and conflating them is what produced the defect above.

    /**
     * The trade window this player has open, as the server's own handler describes it, or null.
     *
     * <p>The villager is read from the handler the server created -- not from anything the client sent, and not
     * from {@code villager.getCustomer()}, which would be ambiguous with two players trading at once.
     */
    @Nullable
    public static VillagerEntity villagerOfWindow(ServerPlayerEntity player) {
        if (!(player.currentScreenHandler instanceof MerchantScreenHandler handler)) {
            return null;
        }
        Merchant merchant = ((MerchantScreenHandlerAccess) handler).noncreatureaffinity$merchant();
        return merchant instanceof VillagerEntity villager ? villager : null;
    }

    /**
     * Whether this offer can be traded at all right now -- the "the trade itself is on offer" half of the
     * decision, with the relationship half left to {@link FreeTradeQuote}.
     *
     * <p>Extends {@link #tradeAvailable} with the villager's own restock state. Vanilla disables every offer when
     * a villager has been traded out ({@code canRefreshTrades} is false), which the window shows as out of stock;
     * without that check a fully traded-out villager would still be described with a price. The check is guarded
     * by {@code isLeveledMerchant} because the restock question only exists for leveled merchants and vanilla's
     * own implementation throws for the others.
     *
     * <p>Read-only: no depletion, no restock, no offer regeneration.
     */
    public static boolean offerAvailable(VillagerEntity villager, PlayerEntity player, TradeOffer offer) {
        if (!tradeAvailable(villager, player, offer)) {
            return false;
        }
        return !villager.isLeveledMerchant() || villager.canRefreshTrades();
    }

    /** Record a successful PAID completion, so the running totals keep their meaning. */
    public static void recordPaidCompletion(VillagerEntity villager, PlayerEntity player) {
        PlayerAffinity relation = AffinityService.getRelation(villager, player);
        if (relation == null) {
            return;
        }
        FreeTradeQuota.State synced =
                FreeTradeAccounts.synchronizedState(relation, FreeTradeHeroEpoch.of(player)).state();
        FreeTradeAccounts.store(relation, FreeTradeQuota.recordPaidCompletion(synced));
    }

    // ------------------------------------------------------------------
    // The cure event
    // ------------------------------------------------------------------

    /**
     * Register the conversion hook that records a cure as a FACT.
     *
     * <p><b>Why the fact is recorded here and not applied here.</b> Fabric fires this event from inside
     * {@code MobEntity.convertTo}, on the {@code spawnEntity} call -- AFTER the conversion finalizer and BEFORE the
     * old entity is discarded. Fabric's own attachment transfer for the same event lands in the same dispatch, and
     * the order between two listeners is not promised. So this must not depend on it: the fact is written into the
     * relation, and the relation is the object Fabric transfers (the parent attachment is {@code copyOnDeath}), so
     * whichever object survives carries it. Applying it to an allowance is left to the next quote or commit, which
     * is also what lets the player be offline while the cure happens.
     *
     * <p><b>No player lookup.</b> The converter's UUID is read from the converting entity; whether that player is
     * online, in this world, or asleep is irrelevant to recording the event.
     */
    public static void registerCureWatcher() {
        ServerLivingEntityEvents.MOB_CONVERSION.register(
                (previous, converted, conversionContext) -> recordCure(previous, converted));
    }

    /**
     * Record one conversion as a cure for the converting player's relation with the survivor.
     *
     * @return what was recorded, or null when this conversion is not a cure this module tracks
     */
    @Nullable
    public static FreeTradeAccounts.CureFact recordCure(MobEntity previous, MobEntity converted) {
        if (!(previous instanceof ZombieVillagerEntity zombie) || !(converted instanceof VillagerEntity villager)) {
            // Infection (villager -> zombie villager) is not a cure and must not refresh anything. The relation
            // still travels with the individual, because that is Fabric's transfer and not this method's doing.
            return null;
        }
        UUID converter = ((ZombieVillagerCureAccessor) zombie).noncreatureaffinity$converter();
        if (CureFactRules.refusalForConverter(converter == null ? null : converter.toString()) != null) {
            return null;
        }
        // The source id identifies the CONVERSION, so it is the converting entity's id -- never the survivor's,
        // whose UUID is new after every conversion and therefore useless for telling two cures apart.
        String sourceId = previous.getUuidAsString();
        int defaultAffinity = NonCreatureAffinity.getConfig().initialAffinity;

        // Write to the previous entity's data AND to the survivor's, comparing identity rather than assuming
        // which listener ran first:
        //   - transfer not yet run: the survivor may have none, and the previous entity's object is what will be
        //     carried over;
        //   - transfer already run: both names refer to the SAME object (the transfer copies the reference), so
        //     the second write is skipped.
        // The dedup marker is per object, so whichever path applies, one conversion counts once.
        CreatureAffinityData previousData = ((AffinityHolder) previous).affinityData();
        FreeTradeAccounts.CureFact fact = FreeTradeAccounts.recordCure(
                previousData.getOrCreate(converter, defaultAffinity), sourceId);

        CreatureAffinityData convertedData = ((AffinityHolder) converted).affinityDataRaw();
        if (convertedData == null) {
            convertedData = ((AffinityHolder) converted).affinityData();
        }
        if (convertedData != previousData) {
            FreeTradeAccounts.recordCure(convertedData.getOrCreate(converter, defaultAffinity), sourceId);
        }

        if (fact.recorded()) {
            NonCreatureAffinity.LOGGER.info(
                    "[NoN Affinity] cure recorded for free trades: converter={} conversion={} survivor={} pending={}",
                    converter, sourceId, villager.getUuid(), fact.observedCureEpoch());
        }
        return fact;
    }
}
