package com.nonid.affinity.integration.jei;

import com.nonid.affinity.item.NonAffinityItems;
import com.nonid.affinity.reunion.ReunionRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.List;
import static com.nonid.affinity.integration.jei.NaturalRelationshipsJeiPlugin.*;

/** A separate JEI category deliberately has no recipe transfer handler. */
@Environment(EnvType.CLIENT)
public final class ReunionJeiCategory implements IRecipeCategory<ReunionJeiCategory.Guide> {
    public record Guide(String kind, int materials, boolean recharge, List<ItemStack> bottles) {}
    public static final IRecipeType<Guide> TYPE = IRecipeType.create(id("reunion_guide"), Guide.class);
    private final IDrawable icon;
    public ReunionJeiCategory(IGuiHelper helper) { icon = helper.createDrawableItemStack(new ItemStack(NonAffinityItems.REUNION_WISH)); }
    @Override public IRecipeType<Guide> getRecipeType() { return TYPE; }
    @Override public Text getTitle() { return text("category"); }
    @Override public int getWidth() { return 174; }
    @Override public int getHeight() {
        var font = MinecraftClient.getInstance().textRenderer;
        int maxLines = 0;
        for (String kind : List.of("create", "single", "double")) {
            int lines = 0;
            for (String key : List.of(kind + "_rule", kind.equals("create") ? "any_species" : "species", "generic_bottle", "separate_slots", "display_only"))
                lines += font.wrapLines(text(key), getWidth() - 6).size();
            maxLines = Math.max(maxLines, lines);
        }
        return 84 + maxLines * (font.fontHeight + 1);
    }
    @Override public IDrawable getIcon() { return icon; }
    @Override public Identifier getIdentifier(Guide guide) { return id("jei/reunion_" + guide.kind()); }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, Guide guide, IFocusGroup focuses) {
        builder.setShapeless(148, 18);
        // One item per crafting grid slot, reflecting the actual ingredient counting rules.
        for (int i = 0; i < guide.materials(); i++) {
            builder.addInputSlot(6 + i * 18, 20).add(new ItemStack(NonAffinityItems.FRIENDSHIP_TOKEN)).setStandardSlotBackground();
            builder.addInputSlot(6 + i * 18, 38).addItemStacks(guide.bottles()).setStandardSlotBackground()
                    .addRichTooltipCallback((slot, tooltip) -> {
                        tooltip.add(text(guide.recharge() ? "species" : "any_species"));
                        if (slot.getDisplayedItemStack().map(ReunionRecipe::species).orElse("").isEmpty())
                            tooltip.add(text("generic_bottle"));
                    });
        }
        var special = builder.addInputSlot(6, 56).setStandardSlotBackground();
        if (guide.recharge()) {
            special.add(new ItemStack(NonAffinityItems.REUNION_WISH))
                    .addRichTooltipCallback((slot, tooltip) -> tooltip.add(text("bound_icon")));
        } else special.add(new ItemStack(Items.DIAMOND));
        builder.addOutputSlot(114, 29).add(new ItemStack(NonAffinityItems.REUNION_WISH)).setOutputSlotBackground()
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(text(guide.recharge() ? "same_binding" : "unbound")));
        // A crafting remainder is displayed but must not be indexed as a newly crafted output.
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, 114, 56)
                .add(new ItemStack(Items.GLASS_BOTTLE, guide.materials()))
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(text("remainder")));
    }
    @Override public void draw(Guide guide, IRecipeSlotsView slots, DrawContext graphics, double mouseX, double mouseY) {
        var font = MinecraftClient.getInstance().textRenderer;
        graphics.drawText(font, text(guide.kind()), 3, 2, 0xFF303030, false);
        graphics.drawText(font, Text.literal("→"), 82, 34, 0xFF303030, false);
        int y = 80;
        for (String key : List.of(guide.kind() + "_rule", guide.recharge() ? "species" : "any_species", "generic_bottle", "separate_slots", "display_only")) {
            for (var line : font.wrapLines(text(key), getWidth() - 6)) {
                graphics.drawText(font, line, 3, y, 0xFF404040, false);
                y += font.fontHeight + 1;
            }
        }
    }
}

