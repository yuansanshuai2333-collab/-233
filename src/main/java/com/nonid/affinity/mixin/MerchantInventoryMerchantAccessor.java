package com.nonid.affinity.mixin;

import net.minecraft.village.Merchant;
import net.minecraft.village.MerchantInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the merchant a {@link MerchantInventory} belongs to.
 *
 * <p>The field is private and has no getter, and the inventory does not otherwise expose it -- {@code canPlayerUse}
 * uses it internally and that is all. The free-trade guards need it to reach the villager, so it is exposed here
 * rather than guessed at through the customer.
 */
@Mixin(MerchantInventory.class)
public interface MerchantInventoryMerchantAccessor {

    /** The selected recipe index; the trade window's selection lives on the inventory, not the handler. */
    @Accessor("offerIndex")
    int noncreatureaffinity$offerIndex();

    @Accessor("merchant")
    Merchant noncreatureaffinity$merchant();
}
