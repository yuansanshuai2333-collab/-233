package com.nonid.affinity.gift;

import java.util.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import net.minecraft.enchantment.*;
import net.minecraft.server.MinecraftServer;

/** Frozen server data becomes a real stack; registry loss never rerolls a prize. */
public final class GiftRewardStacks {
    private GiftRewardStacks() {}
    public static boolean matches(MinecraftServer server, GiftContent.Reward reward, ItemStack actual) {
        if (!Registries.ITEM.getId(actual.getItem()).toString().equals(reward.item())) return false;
        if (reward.enchantment().isEmpty()) return true;
        var enchantment = server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT)
                .getOptional(RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of(reward.enchantment())));
        // Preserve valid extra names/lore/enchantments from other server mods, while requiring the promised book.
        return enchantment.isPresent() && EnchantmentHelper.getEnchantments(actual).getLevel(enchantment.get()) >= reward.level();
    }
    public static Optional<ItemStack> create(MinecraftServer server, GiftContent.Reward reward) {
        var item = Registries.ITEM.getOptionalValue(Identifier.of(reward.item())).filter(i -> i != Items.AIR);
        if (item.isEmpty()) return Optional.empty();
        if (reward.enchantment().isEmpty()) return Optional.of(new ItemStack(item.get(), reward.count()));
        var enchantment = server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT)
                .getOptional(RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of(reward.enchantment())));
        if (enchantment.isEmpty() || reward.level() > enchantment.get().value().getMaxLevel()) return Optional.empty();
        return Optional.of(EnchantmentHelper.getEnchantedBookWith(new EnchantmentLevelEntry(enchantment.get(), reward.level())));
    }
    public record Resolution(List<GiftContent.Reward> rewards, boolean fallbackApplied) {}
    public static List<GiftContent.Reward> resolve(MinecraftServer server, GiftContent content) { return initial(server,content).rewards(); }
    public static Resolution initial(MinecraftServer server, GiftContent content) {
        var result = new ArrayList<GiftContent.Reward>(); boolean missing = false;
        for (var reward : content.rewards()) {
            if (create(server, reward).isPresent()) result.add(reward); else missing = true;
        }
        if (missing) for (var fallback : content.fallback()) if (create(server, fallback).isPresent()) result.add(fallback);
        return new Resolution(List.copyOf(result),missing);
    }
}
