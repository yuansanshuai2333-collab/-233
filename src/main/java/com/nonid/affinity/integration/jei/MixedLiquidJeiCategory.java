package com.nonid.affinity.integration.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.List;
import static com.nonid.affinity.integration.jei.NaturalRelationshipsJeiPlugin.*;

/** Native source stacks and native mixed outputs. No binding or recipe-transfer shortcut. */
public final class MixedLiquidJeiCategory implements IRecipeCategory<MixedLiquidJeiCategory.Guide> {
    public record Guide(List<ItemStack> inputs, ItemStack output) {}
    public static final IRecipeType<Guide> TYPE=IRecipeType.create(id("mixed_liquid_guide"),Guide.class);
    private final IDrawable icon;
    public MixedLiquidJeiCategory(IGuiHelper helper) { icon=helper.createDrawableItemStack(new ItemStack(net.minecraft.item.Items.POTION)); }
    @Override public IRecipeType<Guide> getRecipeType(){return TYPE;}
    @Override public Text getTitle(){return text("mixed_title");}
    @Override public int getWidth(){return 174;}
    @Override public int getHeight(){return 72+MinecraftClient.getInstance().textRenderer.wrapLines(text("mixed_rule"),168).size()*10;}
    @Override public IDrawable getIcon(){return icon;}
    @Override public Identifier getIdentifier(Guide guide){return id("jei/mixed_liquid_"+guide.inputs().size());}
    @Override public void setRecipe(IRecipeLayoutBuilder b,Guide g,IFocusGroup focuses){
        b.setShapeless(148,5);
        for(int i=0;i<g.inputs().size();i++) b.addInputSlot(4+i%3*18,8+i/3*18).add(g.inputs().get(i)).setStandardSlotBackground();
        b.addOutputSlot(114,26).add(g.output()).setOutputSlotBackground();
    }
    @Override public void draw(Guide g,IRecipeSlotsView slots,DrawContext c,double mx,double my){
        var font=MinecraftClient.getInstance().textRenderer;c.drawText(font,Text.literal("→"),82,32,0xFF303030,false);
        int y=68;for(var line:font.wrapLines(text("mixed_rule"),168)){c.drawText(font,line,3,y,0xFF404040,false);y+=10;}
    }
}
