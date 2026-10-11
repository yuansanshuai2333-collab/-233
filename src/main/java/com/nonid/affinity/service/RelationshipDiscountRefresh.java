package com.nonid.affinity.service;
import net.minecraft.entity.player.PlayerEntity;

/** Refresh this addon's price contribution without re-appending vanilla reputation discounts. */
public interface RelationshipDiscountRefresh {
    boolean noncreatureaffinity$refreshRelationshipDiscount(PlayerEntity player);
}
