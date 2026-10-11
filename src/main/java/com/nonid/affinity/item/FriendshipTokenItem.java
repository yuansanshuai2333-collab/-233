package com.nonid.affinity.item;

import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.component.type.ConsumableComponents;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.Item;

/** Food properties and future token-specific behavior live here, not in registration code. */
public final class FriendshipTokenItem extends Item {
    /** Five hunger points equal two and a half drumsticks. */
    public static final int NUTRITION = 5;
    public static final float SATURATION_MODIFIER = 0.0F;

    public static final FoodComponent FOOD = new FoodComponent.Builder()
            .nutrition(NUTRITION)
            .saturationModifier(SATURATION_MODIFIER)
            .build();

    /** Extend this definition for future consumption effects; vanilla handles stack consumption. */
    public static final ConsumableComponent CONSUMABLE = ConsumableComponents.FOOD;

    public FriendshipTokenItem(Settings settings) {
        super(settings.food(FOOD, CONSUMABLE));
    }
}