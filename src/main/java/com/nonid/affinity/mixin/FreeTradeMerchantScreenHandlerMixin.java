package com.nonid.affinity.mixin;

import com.nonid.affinity.service.FreeTradeGuard;
import com.nonid.affinity.service.FreeTradeShiftBatch;
import com.nonid.affinity.service.VillagerFreeTrades;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.village.Merchant;
import net.minecraft.village.MerchantInventory;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The per-iteration guard for shift-click trading.
 *
 * <p><b>Why this path needs its own guard.</b> {@code canTakeItems} is consulted ONCE, before the shift-click loop
 * starts; the loop then calls {@code quickMove} again for every further portion, and each iteration moves an item
 * before any completion hook runs. So a batch that starts legitimately and becomes ineligible part-way (the last
 * allowance is used up, or the affinity drops) would otherwise still hand over the next item, and the one-shot check
 * would never see it. Guarding {@code quickMove} covers every iteration: returning {@link ItemStack#EMPTY} ends the
 * loop in the caller, and nothing has moved at that point.
 *
 * <p>Because the one-shot check does NOT cover the later iterations, this path must apply the whole rule itself --
 * including the case where the output holds an item with no recipe recorded behind it. Reading an absent recipe as
 * "nothing to refuse" would have let the loop keep moving items, which is why the shared decision lives in
 * {@link FreeTradeGuard} and both paths ask it.
 *
 * <p>It also enforces the batch boundary: a batch that already spent a free trade STOPS when free trading is over
 * rather than continuing at the normal price. See {@link FreeTradeShiftBatch} for why that is a decision and not a
 * default.
 */
@Mixin(MerchantScreenHandler.class)
public abstract class FreeTradeMerchantScreenHandlerMixin {

    @Shadow @Final private MerchantInventory merchantInventory;

    @Inject(method = "quickMove", at = @At("HEAD"), cancellable = true)
    private void noncreatureaffinity$guardFreeBatch(PlayerEntity player, int slot,
                                                    CallbackInfoReturnable<ItemStack> cir) {
        if (slot != 2 || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        Slot output = ((net.minecraft.screen.ScreenHandler) (Object) this).getSlot(2);
        if (!output.hasStack()) {
            return;
        }
        Merchant merchant = ((MerchantInventoryMerchantAccessor) this.merchantInventory)
                .noncreatureaffinity$merchant();
        if (!(merchant instanceof VillagerEntity villager)) {
            return;
        }
        TradeOffer offer = this.merchantInventory.getTradeOffer();
        // An item in the output slot with no recipe recorded for it. This is not "nothing to take" -- the slot was
        // just checked -- and this loop is the one place vanilla will not ask again on its own.
        if (offer == null) {
            this.merchantInventory.updateOffers();
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        boolean available = VillagerFreeTrades.tradeAvailable(villager, serverPlayer, offer);
        // Re-checked per iteration, availability included: the batch may have consumed the last allowance, and the
        // recipe list or the stock may have changed since the loop began.
        boolean free = VillagerFreeTrades.quote(villager, serverPlayer, offer, available).free();
        if (FreeTradeShiftBatch.mustStopPaidContinuation(free)) {
            // This batch already delivered free trades and free trading is over. Stopping is deliberate: with
            // materials in the inventory vanilla would otherwise keep producing output and charge for it, so one
            // keypress would silently become a purchase. The player acts again for the normal price.
            this.merchantInventory.updateOffers();
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        boolean canPay = available && VillagerFreeTrades.canPayNow(
                offer, this.merchantInventory.getStack(0), this.merchantInventory.getStack(1));
        // The pair is consistent here: an absent recipe was handled above, and this path must reach the decision for
        // exactly the same reason the click path does -- so both ask the same rule.
        if (FreeTradeGuard.verdict(true, available, free, canPay) != FreeTradeGuard.Verdict.REFUSE_STALE) {
            return;
        }
        this.merchantInventory.updateOffers();
        cir.setReturnValue(ItemStack.EMPTY);
    }
}
