package com.nonid.affinity.mixin;

import com.nonid.affinity.service.TradeWindows;
import com.nonid.affinity.service.VillagerFreeTrades;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.village.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the server's own record of which recipe each trade window has selected, and tells that window about it.
 *
 * <p><b>Why the server has to track this itself.</b> The window shows ONE offer at a time, and the answer the
 * player sees (free, or a price, or "unavailable") is about that offer. Vanilla keeps the selection in a private
 * field with no getter, so rather than reopen it the selection is recorded here as it changes. The value is the
 * SERVER's, taken from the server's own offer list -- never the index a client claims.
 *
 * <p>Two vanilla methods can move the selection: {@code setRecipeIndex} (the plain index setter) and
 * {@code switchTo} (the selection arriving together with an offer). Both are hooked, because which one runs
 * depends on the path the click took, and a switch that is not observed leaves the window showing the previous
 * recipe's price.
 *
 * <p>The hook does nothing on a client. That is not a defensive check: the same method runs in the client's own
 * copy of the handler, where there is no server, no window record and nobody to notify, and the window record is
 * what this reads.
 */
@Mixin(net.minecraft.screen.MerchantScreenHandler.class)
public abstract class FreeTradeWindowSelectionMixin {

    @Shadow @Final private Merchant merchant;

    /**
     * The selected recipe, as this handler last saw it.
     *
     * <p>Per handler and not static: in single-player the same process hosts both sides, and a static field would
     * be shared by handlers whose selections have nothing to do with each other.
     */
    @Unique private int noncreatureaffinity$selectedIndex = -1;

    @Inject(method = "setRecipeIndex", at = @At("HEAD"))
    private void noncreatureaffinity$recordRecipeIndex(int index, CallbackInfo ci) {
        this.noncreatureaffinity$selectedIndex = index;
        noncreatureaffinity$publish();
    }

    @Inject(method = "switchTo", at = @At("HEAD"))
    private void noncreatureaffinity$recordSwitchTo(int index, CallbackInfo ci) {
        this.noncreatureaffinity$selectedIndex = index;
        noncreatureaffinity$publish();
    }

    /**
     * Tell the window what the newly selected recipe is worth.
     *
     * <p>The selection is stored BEFORE publishing, so the status that goes out describes the recipe the player
     * just moved to. Sending the previous index would show the old price on the new recipe until the next event.
     */
    @Unique
    private void noncreatureaffinity$publish() {
        if (!(this.merchant.getCustomer() instanceof ServerPlayerEntity player)) {
            return;
        }
        if (player.currentScreenHandler != (Object) this) {
            return;
        }
        int windowId = player.currentScreenHandler.syncId;
        TradeWindows.noteSelectedOffer(player, windowId, this.noncreatureaffinity$selectedIndex);
        VillagerFreeTrades.sendStatus(player, windowId, this.noncreatureaffinity$selectedIndex);
    }
}
