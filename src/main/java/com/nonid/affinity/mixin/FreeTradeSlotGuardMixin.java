package com.nonid.affinity.mixin;

import com.nonid.affinity.service.FreeTradeGuard;
import com.nonid.affinity.service.VillagerFreeTrades;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.TradeOutputSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.village.Merchant;
import net.minecraft.village.MerchantInventory;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Refuses to hand over a free trade whose quote has gone stale, BEFORE the item leaves the slot.
 *
 * <p>Vanilla asks {@code canTakeItems} before extracting in both paths: the click handler checks it before
 * {@code tryTakeStackRange}, and the shift-click branch checks it before calling {@code quickMove}. That makes it
 * the one place where a refusal still means "the item stays where it is".
 *
 * <p>This mixin targets {@link Slot} rather than {@code TradeOutputSlot} because {@code canTakeItems} is declared
 * on {@code Slot} and merely inherited by the trade output slot; the handler returns immediately for every other
 * kind of slot, so the rest of the game's slots are untouched.
 *
 * <p>The check that matters is narrow: the player is paying nothing (so this module is the only reason something is
 * displayed) and the quote is no longer free. A real paid trade, and anything vanilla displayed on its own, are
 * allowed through unchanged.
 */
@Mixin(Slot.class)
public abstract class FreeTradeSlotGuardMixin {

    @Inject(method = "canTakeItems", at = @At("HEAD"), cancellable = true)
    private void noncreatureaffinity$refuseStaleFreeOutput(PlayerEntity player,
                                                           CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof TradeOutputSlot)) {
            return;
        }
        Slot slot = (Slot) (Object) this;
        if (!slot.hasStack() || !(slot.inventory instanceof MerchantInventory merchantInventory)) {
            return;
        }
        Merchant merchant = ((MerchantInventoryMerchantAccessor) merchantInventory)
                .noncreatureaffinity$merchant();
        if (!(merchant instanceof VillagerEntity villager) || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        TradeOffer offer = merchantInventory.getTradeOffer();
        if (offer == null) {
            // There is an item in the output slot but no recipe recorded for it -- the caller established that much
            // before this method was reached, so this is NOT "there is nothing to take". Vanilla never produces this
            // state: it fills the slot and records the offer together, which is also what this mod's free-trade mixin
            // does. So the pair is inconsistent, and an inconsistent window must not hand anything over.
            //
            // An earlier version tried to justify a take here by re-asking the selected recipe whether it was still
            // free. That reasoning rested on a wrong reading of the mixin (see FreeTradeMerchantInventoryMixin: the
            // recipe is always recorded for a free output) and it opened a path with no eligibility check at all.
            // Refusing is the safe direction: refreshing first drops the item from the screen, and the per-tick
            // refresh re-derives it if it really is still on offer. Nothing has moved, so nothing has to be given back.
            merchantInventory.updateOffers();
            cir.setReturnValue(false);
            return;
        }
        boolean available = VillagerFreeTrades.tradeAvailable(villager, serverPlayer, offer);
        // The availability result goes INTO the quote: a literal `true` here was the defect -- it declared the
        // trade offerable without looking, so a recipe or stock change stayed invisible.
        boolean free = VillagerFreeTrades.quote(villager, serverPlayer, offer, available).free();
        boolean canPay = available && VillagerFreeTrades.canPayNow(
                offer, merchantInventory.getStack(0), merchantInventory.getStack(1));
        // The same rule the shift-click loop asks, with the consistency question answered YES here because an absent
        // recipe was handled above. Sharing the rule is what stops the two paths drifting apart.
        if (FreeTradeGuard.verdict(true, available, free, canPay) != FreeTradeGuard.Verdict.REFUSE_STALE) {
            return;
        }
        // Refresh first, so the stale item disappears from the screen instead of staying there being refused, and
        // then refuse this extraction. Nothing has moved yet, so nothing has to be given back.
        merchantInventory.updateOffers();
        cir.setReturnValue(false);
    }
}
