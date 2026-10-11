package com.nonid.affinity.non;

import com.nonid.affinity.NonCreatureAffinity;
import java.lang.reflect.Method;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/** Optional NoN factory: preserves its food, drink sound, tint and 16-item stack components. */
public final class NoNLiquidBottles {
    private static Method factory;
    private static boolean checked;
    private NoNLiquidBottles() {}
    public static ItemStack mixed(int count) {
        return create(null, count);
    }
    /** Real native ingredient, used by the optional JEI guide. */
    public static ItemStack source(Identifier species) { return create(species, 1); }
    private static ItemStack create(Identifier species, int count) {
        if (count < 1 || count > 9) return ItemStack.EMPTY;
        if (!checked) {
            checked = true;
            if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("needsofnature")) return ItemStack.EMPTY;
            try {
                factory = Class.forName("com.nonid.NonItemSystem").getDeclaredMethod("createLiquidBottleStack", Identifier.class);
                factory.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException error) {
                NonCreatureAffinity.LOGGER.warn("NoN mixed bottle factory unavailable", error);
            }
        }
        if (factory == null) return ItemStack.EMPTY;
        try {
            ItemStack result = (ItemStack)factory.invoke(null, new Object[]{species});
            if (!com.nonid.affinity.reunion.ReunionRecipe.bottle(result)
                    || !(species==null?"":species.toString()).equals(com.nonid.affinity.reunion.ReunionRecipe.species(result))
                    || result.getMaxCount() < count) return ItemStack.EMPTY;
            return result.copyWithCount(count);
        } catch (ReflectiveOperationException | RuntimeException error) {
            factory = null;
            NonCreatureAffinity.LOGGER.warn("NoN mixed bottle creation failed; recipe disabled", error);
            return ItemStack.EMPTY;
        }
    }
}
