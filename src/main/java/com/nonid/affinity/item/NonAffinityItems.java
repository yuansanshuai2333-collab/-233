package com.nonid.affinity.item;

import com.nonid.affinity.NonCreatureAffinity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.Settings;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

/**
 * Registration of the mod's items.
 */
public final class NonAffinityItems {

    /** Stable id for the friendship token; the display name may change, this must not. */
    public static final net.minecraft.util.Identifier FRIENDSHIP_TOKEN_ID =
            net.minecraft.util.Identifier.of(NonCreatureAffinity.MOD_ID, "friendship_token");

    private NonAffinityItems() {
    }

    /** Affinity detector item. */
    public static Item AFFINITY_DETECTOR;
    public static Item REUNION_WISH;

    /**
     * 友情之证 / friendship token.
     *
     * <p>Registered with a stable id (and a stack limit of 64) so the display name can change
     * without touching the id. Food behavior is defined in FriendshipTokenItem;
     * existing acquisition and relationship rules are unchanged.
     */
    public static Item FRIENDSHIP_TOKEN;

    public static void register() {
        var reunionId = com.nonid.affinity.reunion.ReunionData.id("reunion_wish");
        REUNION_WISH = Registry.register(Registries.ITEM, reunionId,
                new com.nonid.affinity.reunion.ReunionItem(new Item.Settings()
                        .registryKey(RegistryKey.of(RegistryKeys.ITEM, reunionId)).maxCount(1)
                        .component(net.minecraft.component.DataComponentTypes.ITEM_MODEL,
                                com.nonid.affinity.reunion.ReunionData.id("reunion_unbound"))));
        RegistryKey<Item> key = RegistryKey.of(Registries.ITEM.getKey(), AffinityDetectorItem.ID);
        AFFINITY_DETECTOR = Registry.register(
                Registries.ITEM,
                AffinityDetectorItem.ID,
                new AffinityDetectorItem(new Item.Settings().registryKey(key).maxCount(1)));
        RegistryKey<Item> tokenKey = RegistryKey.of(Registries.ITEM.getKey(), FRIENDSHIP_TOKEN_ID);
        FRIENDSHIP_TOKEN = Registry.register(
                Registries.ITEM,
                FRIENDSHIP_TOKEN_ID,
                new FriendshipTokenItem(new Item.Settings().registryKey(tokenKey).maxCount(64)));
        NonCreatureAffinity.LOGGER.info("[NoN Affinity] registered item {}", FRIENDSHIP_TOKEN_ID);
    }

    /** Convenience for the creative tab. */
    public static ItemStack detectorStack() {
        return new ItemStack(AFFINITY_DETECTOR);
    }

    /** Convenience for the creative tab. */
    public static ItemStack tokenStack() {
        return new ItemStack(FRIENDSHIP_TOKEN);
    }
}
