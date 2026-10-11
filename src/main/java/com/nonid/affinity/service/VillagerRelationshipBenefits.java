package com.nonid.affinity.service;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.data.PlayerAffinity;
import java.util.List;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.village.TradeOffer;

/** Benefits are scoped to the specific villager/player relation. */
public final class VillagerRelationshipBenefits {
    private VillagerRelationshipBenefits() {}

    /**
     * The affinity discount for one offer, in items.
     *
     * <p>Scaled by progress toward the friend threshold and capped by the configured percentage, which may be
     * as high as the whole price (100). The result never removes the last item: paying nothing would not be a
     * discount but a different trade, and vanilla's own price arithmetic assumes at least one item.
     *
     * <p>{@code baseCost} is the offer's OWN base price, not its currently displayed one. That is what keeps
     * this independent of vanilla's own reductions (gossip, curing, hero of the village): the module computes
     * its share of the base price and vanilla keeps its own figure, so the two stack instead of one cancelling
     * the other or being applied twice.
     *
     * <p>Note what this does NOT do: it never offsets the offer's DEMAND penalty. A trade the villagers want
     * more of carries {@code max(0, demand)} on top of the base price, and this discount is applied to the base
     * price only, so a 100% cap on a demanded trade still leaves that penalty to pay (vanilla's own reductions
     * behave the same way). See {@link #firstBuyCountWithDiscountOnly(int, int)} for the resulting count.
     *
     * <p>Where the resulting amount is written -- and how it is taken back next cycle -- is
     * {@link VillagerDiscountLedger}'s business, deliberately kept out of this arithmetic so the refresh cycle
     * can be tested on its own.
     */
    public static int discount(int baseCost, int affinity, int threshold, int maxPercent) {
        if (baseCost <= 1 || affinity <= 0 || threshold <= 0 || maxPercent <= 0) return 0;
        double progress = Math.min(1.0, (double) affinity / threshold);
        double share = Math.min(1.0, maxPercent / 100.0);
        return Math.min(baseCost - 1, (int) Math.floor(baseCost * share * progress));
    }

    /**
     * The first-item count left by this module's discount, given vanilla's demand penalty and NOTHING ELSE.
     *
     * <p>Scope matters here, because a wider claim would be false. This is the count produced by
     * {@code base + max(0, demand) - thisModuleDiscount} and it assumes:
     *
     * <ul>
     *   <li>no OTHER reduction is present -- another mod's, or vanilla's gossip/cure reduction applied in the
     *       same special-price slot, lowers the count further (a cure discount of 7 on top of the example below
     *       yields {@code 20 + 10 - 19 - 7 = 4}, not 11); and</li>
     *   <li>the result stays inside the offer's stack limits, since the displayed count is clamped to at most
     *       the item's max count.</li>
     * </ul>
     *
     * <p>What it does state precisely is the rule the user asked about: the discount is a share of the BASE
     * price and does not subsidise the demand penalty, which is added on top and survives. Without demand, a
     * full discount reaches this module's one-item floor.
     */
    public static int firstBuyCountWithDiscountOnly(int baseCost, int demandBonus) {
        return baseCost - discount(baseCost, 1, 1, 100) + Math.max(0, demandBonus);
    }

    public static int discount(TradeOffer offer, VillagerEntity villager, PlayerEntity customer) {
        PlayerAffinity relation = AffinityService.getRelation(villager, customer);
        if (relation == null || villager.isBaby()) return 0;
        return discount(offer.getOriginalFirstBuyItem().getCount(), relation.getAffinity(),
                NonCreatureAffinity.getConfig().friendThreshold,
                NonCreatureAffinity.getConfig().villagerDiscountMaxPercent);
    }

    /**
     * Whether this completion earns the ware gift, independent of the villager object.
     *
     * <p>Separated from {@link #giveCompletionGift} so the gate is reachable from a unit test: the delivery
     * method needs a live villager, this predicate does not. Both must agree -- the delivery checks it first.
     */
    public static boolean qualifiesForCompletionGift(boolean initiatedByPlayer, boolean friendBeforeCompletion,
                                                     boolean child, java.util.function.BooleanSupplier isFriend) {
        if (!initiatedByPlayer || !friendBeforeCompletion || child) return false;
        return isFriend.getAsBoolean();
    }

    /**
     * Deliver the ware gift for one completion, with private visuals or old-client text fallback.
     *
     * <p>Called only by the already deduplicated completion settlement, never by start/peak events.
     *
     * <p>The single line matters when the same completion also hands out a friendship token (the affinity was
     * already capped). That token has a message of its own, and sending both produced two chat lines for one
     * reward -- which reads as two tokens even though only one was given. So instead:
     *
     * <ul>
     *   <li>{@code tokenAlreadyReported} false (the normal case): this method's own "gave you one X" line.</li>
     *   <li>{@code tokenAlreadyReported} true AND this gift delivered: the combined sentence, and the caller
     *       suppresses the token's separate line.</li>
     *   <li>{@code tokenAlreadyReported} true and NO ware to give: the caller keeps the token's line, because
     *       this method reports nothing at all.</li>
     * </ul>
     *
     * @param tokenAlreadyReported whether the caller has already told the player about a friendship token from
     *                            this same completion
     * @return whether a ware was actually handed over, which is what tells the caller to merge its token line
     */
    public static boolean giveCompletionGift(VillagerEntity villager, ServerPlayerEntity player,
                                             boolean initiatedByPlayer, boolean friendBeforeCompletion,
                                             boolean tokenAlreadyReported) {
        if (villager.isBaby()) return false;
        PlayerAffinity relation = AffinityService.getRelation(villager, player);
        if (relation == null) return false;
        if (!qualifiesForCompletionGift(initiatedByPlayer, friendBeforeCompletion, false,
                () -> VillagerFreeTrades.isCloseFriend(villager, player))) {
            return false;
        }
        List<TradeOffer> offers = villager.getOffers().stream()
                .filter(offer -> !offer.getSellItem().isEmpty()).toList();
        if (offers.isEmpty()) return false;
        ItemStack gift = offers.get(villager.getRandom().nextInt(offers.size())).copySellItem();
        gift.setCount(1);
        ItemStack actual = gift.copy();
        Text giftName = actual.getName();
        player.getInventory().insertStack(gift);
        if (!gift.isEmpty() && player.dropItem(gift, false) == null) return false;
        VillagerGiftFeedback.sent(player, villager, actual);
        // The combined key takes (villager, ware); the plain one is the same sentence without the token clause.
        if (!VillagerGiftFeedback.supported(player)) {
            // Preserve the complete old-client sentence; each available visual replaces only its own text.
            player.sendMessage(Text.translatable(tokenAlreadyReported && !FriendshipTokenFeedback.supported(player)
                    ? "message.noncreatureaffinity.villager_gift_with_token"
                    : "message.noncreatureaffinity.villager_gift", villager.getName(), giftName), false);
        } else if (tokenAlreadyReported && !FriendshipTokenFeedback.supported(player)) {
            player.sendMessage(AffinityService.pendingCapGiftMessage(), false);
        }
        NonCreatureAffinity.LOGGER.info(
                "[NoN Affinity] villager trade gift delivered: villager={} player={} item={} mergedWithToken={}",
                villager.getUuid(), player.getUuid(), giftName.getString(), tokenAlreadyReported);
        return true;
    }
}
