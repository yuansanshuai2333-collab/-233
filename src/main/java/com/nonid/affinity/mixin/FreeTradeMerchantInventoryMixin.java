package com.nonid.affinity.mixin;

import com.nonid.affinity.service.FreeTradeQuote;
import com.nonid.affinity.service.VillagerFreeTrades;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.village.Merchant;
import net.minecraft.village.MerchantInventory;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the sell item into the output slot even when the player is paying nothing.
 *
 * <p><b>Why vanilla cannot do this.</b> Three separate checks make an empty-payment trade impossible, all confirmed
 * from the 1.21.11 sources:
 *
 * <ul>
 *   <li>{@code MerchantInventory.updateOffers} returns early -- clearing the output -- as soon as the first input
 *       candidate is empty;</li>
 *   <li>{@code TradeOffer.matchesBuyItems} starts with {@code firstBuyItem.matches(stack)}, which is false for an
 *       empty stack, so an offer can never match with nothing in the slots;</li>
 *   <li>{@code TradeOffer.getFirstBuyItemCount} clamps to a minimum of ONE, so a "price of zero" cannot be written
 *       into the price field at all.</li>
 * </ul>
 *
 * <p>So the free branch is a separate path by necessity, and this is its entry: after vanilla has finished -- which
 * includes every case where it DID find a valid paid offer -- the hook only acts when the output is still empty and
 * the SELECTED offer is free-eligible for the current customer. A paid offer is therefore left exactly as vanilla
 * produced it.
 *
 * <p>It also covers the two-material case with materials in the slots: if the player is holding only one of the two
 * materials, vanilla matches nothing and this still offers the trade, and the completion path (which skips
 * depletion) leaves those materials with the player rather than eating them.
 */
@Mixin(MerchantInventory.class)
public abstract class FreeTradeMerchantInventoryMixin {

    @Shadow @Final private Merchant merchant;
    @Shadow @Final private DefaultedList<ItemStack> inventory;
    @Shadow private TradeOffer tradeOffer;
    @Shadow private int offerIndex;
    @Shadow private int merchantRewardedExperience;

    @Shadow
    public abstract ItemStack getStack(int slot);

    @Shadow
    public abstract void setStack(int slot, ItemStack stack);

    @Inject(method = "updateOffers", at = @At("RETURN"))
    private void noncreatureaffinity$offerFreeOutput(CallbackInfo ci) {
        // Vanilla produced something: leave it alone. This includes a paid output the player can afford.
        if (this.tradeOffer != null && !this.getStack(2).isEmpty()) {
            return;
        }
        if (!(this.merchant instanceof VillagerEntity villager)) {
            return;
        }
        PlayerEntity customer = this.merchant.getCustomer();
        if (!(customer instanceof ServerPlayerEntity player)) {
            return;
        }
        TradeOfferList offers = this.merchant.getOffers();
        if (this.offerIndex < 0 || this.offerIndex >= offers.size()) {
            return;
        }
        TradeOffer selected = offers.get(this.offerIndex);
        if (selected == null || selected.isDisabled()) {
            // Out of stock is out of stock: a free trade does not bypass the uses limit.
            return;
        }
        FreeTradeQuote quote = VillagerFreeTrades.quote(villager, player, selected,
                VillagerFreeTrades.tradeAvailable(villager, player, selected));
        if (!quote.free()) {
            return;
        }
        this.tradeOffer = selected;
        this.setStack(2, selected.copySellItem());
        this.merchantRewardedExperience = selected.getMerchantExperience();
        this.merchant.onSellingItem(this.getStack(2));
    }
}
