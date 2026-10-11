package com.nonid.affinity.mixin;

import net.minecraft.village.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to the merchant behind a trade window, and to the window's own trade inventory.
 *
 * <p>{@code MerchantScreenHandler} keeps both fields private and exposes no getters for them. The server side needs
 * them to answer "which villager is this window for" and "which recipe has this window selected", and it must read
 * the handler the server itself created rather than trust anything the client says about it -- so the fields are
 * opened here instead of being guessed at through {@code villager.getCustomer()}, which would be ambiguous with two
 * players trading at once.
 *
 * <p><b>Accessors only.</b> A {@code default} method here would make Mixin treat this as an interface mixin with a
 * real body and refuse to prepare it at all ("target type mismatch: MerchantScreenHandler is not an interface"),
 * which fails at STARTUP while every earlier check -- compilation, tests, the jar listing -- stays green. The
 * composite read lives in {@code VillagerFreeTrades.selectionOfHandler}, which calls both accessors.
 *
 * <p>Read-only, and read-only on purpose: this accessor does not let anything write to the window.
 */
@Mixin(net.minecraft.screen.MerchantScreenHandler.class)
public interface MerchantScreenHandlerAccess {

    /** The merchant this window is trading with (a {@code VillagerEntity} for a village trade). */
    @Accessor("merchant")
    Merchant noncreatureaffinity$merchant();

    /** The window's own trade inventory (inputs, output and the selected recipe). */
    @Accessor("merchantInventory")
    net.minecraft.village.MerchantInventory noncreatureaffinity$merchantInventory();
}
