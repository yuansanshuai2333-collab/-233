package com.nonid.affinity.mixin;

import com.nonid.affinity.service.VillagerDiscountLedger;
import com.nonid.affinity.service.VillagerRelationshipBenefits;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies this module's affinity discount to a villager's offers, on top of whatever vanilla already computed.
 *
 * <p><b>How it coexists with vanilla.</b> The discount is a share of each offer's BASE price and is written into
 * the offer's special price -- the same slot vanilla uses for gossip, curing a zombie villager, "hero of the
 * village" and demand. The displayed count is
 * {@code clamp(base + max(0, demand) + specialPrice, 1, item max count)}, so the two reductions add up.
 *
 * <p><b>How the contribution is removed.</b> By explicit bookkeeping ({@link VillagerDiscountLedger}), not by
 * inspecting the price. Two earlier versions tried to infer "is my discount still there?" from the number and
 * both failed in different directions:
 *
 * <ul>
 *   <li>adding the amount back overcharges after an external reset;</li>
 *   <li>clamping with {@code min} -- and only restoring when the number was unchanged -- failed to remove the
 *       discount at all, so the discounted price became the next baseline and the discount was applied again on
 *       top: −4, then −8, then −12 with no other mod involved.</li>
 * </ul>
 *
 * <p>Now the baseline each discount was computed from is recorded and removal restores it exactly. The
 * consequence, stated rather than hidden: an external reset inside the same window is undone by that restore,
 * and this module re-applies its discount on the next refresh. Neither failure direction above is possible.
 */
@Mixin(VillagerEntity.class)
public abstract class VillagerTradeRelationshipMixin implements com.nonid.affinity.service.RelationshipDiscountRefresh {

    @Override
    public boolean noncreatureaffinity$refreshRelationshipDiscount(PlayerEntity player) {
        VillagerEntity villager = (VillagerEntity) (Object) this;
        boolean changed = false;
        for (TradeOffer offer : villager.getOffers()) {
            int desired = VillagerRelationshipBenefits.discount(offer, villager, player);
            Integer previous = noncreatureaffinity$discountAmounts.get(offer);
            if (previous == null || previous == desired) continue;
            long id = noncreatureaffinity$idOf(offer);
            int before = offer.getSpecialPrice();
            var record = noncreatureaffinity$ledger.appliedFor(id);
            // Another mod changed the live price: let the normal pricing cycle establish a fresh baseline.
            // The per-tick update must not overwrite an external contribution it cannot identify.
            if (record != null && before != record.netSpecialPrice()) continue;
            int baseline = noncreatureaffinity$ledger.beginCycle(id, before);
            int after = noncreatureaffinity$ledger.apply(id, baseline, desired);
            offer.setSpecialPrice(after);
            noncreatureaffinity$discountAmounts.put(offer, desired);
            changed |= before != after;
        }
        return changed;
    }

    /**
     * The bookkeeping, keyed by the offer's identity.
     *
     * <p>Uses {@link IdentityHashMap} because {@code TradeOffer} does not define value equality: two distinct
     * offers with the same contents must stay distinct entries, and an entry must disappear with its offer.
     */
    @Unique private final Map<TradeOffer, Long> noncreatureaffinity$offerIds = new IdentityHashMap<>();
    @Unique private final Map<TradeOffer, Integer> noncreatureaffinity$discountAmounts = new IdentityHashMap<>();

    @Unique private final VillagerDiscountLedger noncreatureaffinity$ledger = new VillagerDiscountLedger();

    /**
     * A stable id for one offer instance, created on first use.
     *
     * <p>A counter rather than {@code System.identityHashCode}: the ledger keys its records by this value, and a
     * hash collision would silently make one offer's record answer for another.
     */
    @Unique private long noncreatureaffinity$nextOfferId = 1L;

    @Unique
    private long noncreatureaffinity$idOf(TradeOffer offer) {
        Long existing = noncreatureaffinity$offerIds.get(offer);
        if (existing != null) {
            return existing;
        }
        long id = noncreatureaffinity$nextOfferId++;
        noncreatureaffinity$offerIds.put(offer, id);
        return id;
    }

    @Inject(method = "prepareOffersFor", at = @At("HEAD"))
    private void noncreatureaffinity$removePreviousDiscount(PlayerEntity player, CallbackInfo ci) {
        noncreatureaffinity$removeAppliedDiscounts();
    }

    @Inject(method = "prepareOffersFor", at = @At("TAIL"))
    private void noncreatureaffinity$applyDiscount(PlayerEntity player, CallbackInfo ci) {
        VillagerEntity villager = (VillagerEntity) (Object) this;
        for (TradeOffer offer : villager.getOffers()) {
            int amount = VillagerRelationshipBenefits.discount(offer, villager, player);
            // The HEAD handler restored this module's recorded baseline, so the price here is what vanilla's own
            // body left: `prepareOffersFor` writes reputation and "hero of the village" with
            // increaseSpecialPrice (an APPEND, confirmed from the mapped bytecode), and nothing of this module's
            // remains. That value is the baseline for this cycle.
            //
            // No clamp is needed or wanted here. An earlier version guarded this with a min() against the
            // recorded baseline, but the HEAD restore CONSUMES the record, so the guard could only ever compare
            // the price with itself -- dead code that suggested a protection it did not provide.
            int baseline = offer.getSpecialPrice();
            offer.setSpecialPrice(noncreatureaffinity$ledger.apply(
                    noncreatureaffinity$idOf(offer), baseline, amount));
            noncreatureaffinity$discountAmounts.put(offer, amount);
        }
    }

    @Inject(method = "sendOffersToCustomer", at = @At("HEAD"))
    private void noncreatureaffinity$refreshDiscountAfterRestock(CallbackInfo ci) {
        PlayerEntity customer = ((VillagerEntity) (Object) this).getCustomer();
        noncreatureaffinity$removeAppliedDiscounts();
        if (customer != null) noncreatureaffinity$applyDiscount(customer, ci);
    }

    @Inject(method = "clearSpecialPrices", at = @At("TAIL"))
    private void noncreatureaffinity$clearDiscountTracking(CallbackInfo ci) {
        // Vanilla has reset every special price, so no contribution of this module remains in any of them, and
        // the records must not survive to write a stale baseline later.
        noncreatureaffinity$ledger.clear();
        noncreatureaffinity$offerIds.clear();
        noncreatureaffinity$discountAmounts.clear();
    }

    /**
     * Put every recorded baseline back, exactly.
     *
     * <p>An offer without a record is left alone: this module has nothing applied to it, so it has nothing to
     * undo.
     */
    @Unique
    private void noncreatureaffinity$removeAppliedDiscounts() {
        VillagerEntity villager = (VillagerEntity) (Object) this;
        for (TradeOffer offer : villager.getOffers()) {
            long id = noncreatureaffinity$idOf(offer);
            offer.setSpecialPrice(noncreatureaffinity$ledger.beginCycle(id, offer.getSpecialPrice()));
        }
    }
}
