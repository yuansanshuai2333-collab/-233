package com.nonid.affinity.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to a handled screen's panel geometry, for the free-trade status strip.
 *
 * <p><b>Why this targets {@code HandledScreen} and not {@code MerchantScreen}.</b> A mixin accessor only ever sees
 * the members of the class it targets; it does not walk up to a superclass. Targeting the trade screen for
 * {@code x}, {@code y}, {@code backgroundWidth} or {@code backgroundHeight} therefore failed with
 * {@code No candidates were found matching x:I in MerchantScreen} and aborted the whole renderer at startup, because
 * those four fields are declared here, on the base class, and not on the merchant screen. Targeting the class that
 * actually declares them is what makes the accessor resolve.
 *
 * <p>It applies to every handled screen rather than only to the merchant one. That is harmless: nothing calls these
 * accessors except the trade window's renderer, and the interface adds nothing to any screen but four getters.
 *
 * <p>Read-only, and read-only on purpose: this lets nothing write to a screen.
 */
@Mixin(net.minecraft.client.gui.screen.ingame.HandledScreen.class)
public interface HandledScreenAccess {

    /** The panel's left edge, in GUI-space pixels. */
    @Accessor("x")
    int noncreatureaffinity$panelX();

    /** The panel's top edge, in GUI-space pixels. */
    @Accessor("y")
    int noncreatureaffinity$panelY();

    /** The panel's width. */
    @Accessor("backgroundWidth")
    int noncreatureaffinity$panelWidth();

    /** The panel's height. */
    @Accessor("backgroundHeight")
    int noncreatureaffinity$panelHeight();
}
