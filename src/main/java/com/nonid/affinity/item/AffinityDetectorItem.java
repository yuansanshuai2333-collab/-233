package com.nonid.affinity.item;

import com.nonid.affinity.NonCreatureAffinity;
import java.lang.reflect.Method;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * Affinity detector, styled as a small tablet.
 *
 * <p>Right-click opens companion information. The page retains the client-owned scan toggle.
 *
 * <p>Client-only classes are reached through reflection: items load on both sides, so referencing
 * them directly would break dedicated servers.
 */
public class AffinityDetectorItem extends Item {

    public static final Identifier ID = Identifier.of(NonCreatureAffinity.MOD_ID, "affinity_detector");

    private static Method clientIsActiveMethod;
    private static boolean clientCheckDone;

    public AffinityDetectorItem(Settings settings) {
        super(settings);
    }

    @Override public void appendTooltip(ItemStack stack, TooltipContext context,
            net.minecraft.component.type.TooltipDisplayComponent display,
            java.util.function.Consumer<net.minecraft.text.Text> tooltip,
            net.minecraft.item.tooltip.TooltipType type) {
        tooltip.accept(net.minecraft.text.Text.translatable("tooltip.noncreatureaffinity.detector_open"));
        tooltip.accept(net.minecraft.text.Text.translatable("tooltip.noncreatureaffinity.detector_highlight"));
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient() && isRealClient()) {
            try {
                Class.forName("com.nonid.affinity.client.CompanionInfoClient").getMethod("openDirectory").invoke(null);
            } catch (ReflectiveOperationException failure) {
                NonCreatureAffinity.LOGGER.warn("Could not open companion info", failure);
            }
        } else if (!world.isClient()) {
            // Server: audio feedback only.
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.PLAYERS, 0.5F, 1.4F);
        }
        return ActionResult.SUCCESS;
    }

    /** True only in a real client process, not merely "the client classes happen to be present". */
    private static boolean isRealClient() {
        // Environment-stripped client classes throw before their own guards can run.
        if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType()
                != net.fabricmc.api.EnvType.CLIENT) {
            return false;
        }
        try {
            if (!clientCheckDone) {
                clientCheckDone = true;
                Class<?> stateClass = Class.forName("com.nonid.affinity.client.AffinityDetectorState");
                clientIsActiveMethod = stateClass.getMethod("isActive");
            }
            if (clientIsActiveMethod == null) {
                return false;
            }
            return Boolean.TRUE.equals(clientIsActiveMethod.invoke(null));
        } catch (ReflectiveOperationException exception) {
            clientCheckDone = true;
            clientIsActiveMethod = null;
            return false;
        }
    }

    /** Whether either hand holds a detector (used only for display hints). */
    public static boolean isHeldBy(PlayerEntity player) {
        return player != null
                && (isDetector(player.getStackInHand(Hand.MAIN_HAND))
                    || isDetector(player.getStackInHand(Hand.OFF_HAND)));
    }

    public static boolean isDetector(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof AffinityDetectorItem;
    }
}

