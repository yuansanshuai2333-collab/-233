package com.nonid.affinity.mixin;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.service.FreeTradeQuote;
import com.nonid.affinity.service.FreeTradeShiftBatch;
import com.nonid.affinity.service.VillagerFreeTrades;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.TradeOutputSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.village.Merchant;
import net.minecraft.village.MerchantInventory;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The free branch of taking a villager trade's output.
 *
 * <p><b>The method this hooks is the only place a trade is committed.</b> Verified from the 1.21.11 sources: the
 * ordinary click path calls {@code Slot.takeStackRange}, which calls {@code onTakeItem} after taking the stack;
 * the shift-click path calls {@code MerchantScreenHandler.quickMove}, which moves the output into the inventory and
 * then reaches the SAME {@code onTakeItem} at the end. There is no second commit site, so this is where a free
 * trade has to be recognised -- and where the allowance is spent.
 *
 * <p><b>What is skipped, and what is not.</b> Vanilla's body depletes the two payment stacks and then calls
 * {@code merchant.trade}. The free branch keeps every OTHER side effect exactly as vanilla has it -- the craft
 * statistics, the merchant's own {@code trade} (uses, villager experience, level-up timer, the experience orb, the
 * advancement) and the experience sync -- and skips only the depletion. That is the whole difference between a
 * paid and a free trade; nothing is hand-rolled, so nothing vanilla does can be lost by accident.
 *
 * <p>A trade the quote does NOT call free is left completely alone: the handler returns without cancelling, so
 * vanilla's own path runs, including its depletion.
 */
@Mixin(TradeOutputSlot.class)
public abstract class FreeTradeOutputSlotMixin {

    @Shadow @Final private MerchantInventory merchantInventory;
    @Shadow @Final private Merchant merchant;
    @Shadow @Final private PlayerEntity player;

    @Shadow
    protected abstract void onCrafted(ItemStack stack);

    /**
     * The offer this take is about, captured at the start.
     *
     * <p>The previous version re-read {@code merchantInventory.getTradeOffer()} in the tail handler, which is
     * wrong twice over: a successfully paid trade that used up the last materials triggers an offer refresh, so
     * the field can be {@code null} by then (the completion was silently dropped), and after a refresh it can be a
     * DIFFERENT offer (the use counts of two offers were being compared). Holding the object itself fixes both,
     * because {@code use()} moves the count on this very instance.
     */
    @Unique private TradeOffer noncreatureaffinity$offerOfTake;

    @Unique private int noncreatureaffinity$usesBeforeTake = -1;

    @Unique private boolean noncreatureaffinity$freeCommitted;

    @Inject(method = "onTakeItem", at = @At("HEAD"), cancellable = true)
    private void noncreatureaffinity$freeTradeCommit(PlayerEntity taker, ItemStack stack, CallbackInfo ci) {
        this.noncreatureaffinity$freeCommitted = false;
        TradeOffer offer = this.merchantInventory.getTradeOffer();
        this.noncreatureaffinity$offerOfTake = offer;
        this.noncreatureaffinity$usesBeforeTake = offer == null ? -1 : offer.getUses();
        if (offer == null || !(taker instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        if (!(this.merchant instanceof VillagerEntity villager)) {
            return;
        }
        // Re-checked here, on the server, against the state as it is NOW: the quote the screen was showing may be
        // older than the effects, the relation, the allowance or the recipe list it was computed from.
        boolean available = VillagerFreeTrades.tradeAvailable(villager, serverPlayer, offer);
        FreeTradeQuote quote = VillagerFreeTrades.quote(villager, serverPlayer, offer, available);
        if (!quote.free()) {
            // NOT a completion: the guards should have refused this take before the item moved, so reaching here
            // means an extraction slipped past them. Recorded as an anomaly rather than counted as a trade --
            // counting it would inflate the statistics for a trade that did not happen.
            boolean canPay = VillagerFreeTrades.canPayNow(
                    offer, this.merchantInventory.getStack(0), this.merchantInventory.getStack(1));
            if (!canPay) {
                NonCreatureAffinity.LOGGER.warn(
                        "[NoN Affinity] a free trade was taken after its quote became invalid: villager={} "
                                + "player={} offer={} reason={} available={}. No allowance was spent.",
                        villager.getUuid(), serverPlayer.getUuid(),
                        offer.getSellItem().getName().getString(), quote.refusal(), available);
            }
            return;
        }

        this.onCrafted(stack);
        this.merchant.trade(offer);
        taker.incrementStat(Stats.TRADED_WITH_VILLAGER);
        this.merchant.setExperienceFromServer(this.merchant.getExperience() + offer.getMerchantExperience());
        VillagerFreeTrades.commitFreeTrade(villager, serverPlayer, offer);
        this.noncreatureaffinity$freeCommitted = true;
        // Tell the batch that this keypress already delivered a free trade, so it stops instead of rolling into
        // paid purchases when the allowance runs out mid-loop.
        FreeTradeShiftBatch.noteFreeTradeSpent();
        // Recompute the output: with the allowance spent down to zero this clears the slot, which is what makes a
        // fourth trade stop instead of being served for free. With an allowance left it refills, which is what
        // lets one shift-click deliver up to three.
        this.merchantInventory.updateOffers();
        ci.cancel();
    }

    /**
     * Count a PAID completion, but only once vanilla has actually committed it.
     *
     * <p>The previous version recorded it in the HEAD handler, before vanilla had decided anything: a take whose
     * depletion did not match (an item in the slot that the offer no longer accepts, an old quote, a full
     * inventory) still incremented the running totals, so the statistics counted trades that never happened. The
     * check is now the offer's own use count, which only moves inside {@code MerchantEntity.trade}.
     */
    @Inject(method = "onTakeItem", at = @At("TAIL"))
    private void noncreatureaffinity$countPaidCompletion(PlayerEntity taker, ItemStack stack, CallbackInfo ci) {
        if (this.noncreatureaffinity$freeCommitted) {
            return;
        }
        // The SAME object the head handler looked at, not a fresh lookup: a successful trade that consumed the
        // last materials refreshes the offers, so a fresh lookup is null exactly when the completion is real.
        TradeOffer offer = this.noncreatureaffinity$offerOfTake;
        int before = this.noncreatureaffinity$usesBeforeTake;
        if (offer == null || before < 0 || offer.getUses() <= before) {
            return;
        }
        if (!(this.merchant instanceof VillagerEntity villager)) {
            return;
        }
        VillagerFreeTrades.recordPaidCompletion(villager, taker);
    }
}
