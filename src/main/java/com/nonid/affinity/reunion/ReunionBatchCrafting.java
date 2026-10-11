package com.nonid.affinity.reunion;

import com.nonid.affinity.item.NonAffinityItems;
import com.nonid.affinity.mixin.ReunionCraftingInputAccessor;
import java.util.ArrayList;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/** Shift-taking a valid recharge output consumes only the material units needed to fill this wish. */
public final class ReunionBatchCrafting {
    @Nullable public static ItemStack quickMove(ScreenHandler handler,PlayerEntity user,int slot){
        if(slot!=0||!(user instanceof ServerPlayerEntity player))return null;
        var output=handler.getSlot(0);
        if(!(output instanceof ReunionCraftingInputAccessor access)||!(output.inventory instanceof CraftingResultInventory result)
                ||result.getLastRecipe()==null||!(result.getLastRecipe().value() instanceof ReunionRecipe))return null;
        var grid=access.noncreatureaffinity$reunionInput();int wishSlot=-1,tokens=0,bottles=0;
        for(int i=0;i<grid.size();i++){
            var stack=grid.getStack(i);
            if(stack.isOf(NonAffinityItems.REUNION_WISH)){if(wishSlot>=0)return ItemStack.EMPTY;wishSlot=i;}
            if(stack.isOf(NonAffinityItems.FRIENDSHIP_TOKEN))tokens+=Math.min(3,stack.getCount());
            if(ReunionRecipe.bottle(stack))bottles+=Math.min(3,stack.getCount());
        }
        if(wishSlot<0)return null; // First creation remains entirely vanilla.
        if(!ReunionCraftingGuard.allow(output,player)||!ReunionRecipe.INSTANCE.matches(grid.createRecipeInput(),player.getEntityWorld()))return ItemStack.EMPTY;
        var original=grid.getStack(wishSlot);var data=ReunionData.item(original);
        var record=ReunionData.record(player.getEntityWorld().getServer(),data.getString("mob",""));
        int energy=ReunionData.energy(data,record),amount=ReunionRechargeRules.materials(energy,tokens,bottles);
        long fuel=ReunionData.fuel(data);
        if(amount<=0||fuel>Long.MAX_VALUE-amount)return ItemStack.EMPTY;
        int destination=player.getInventory().getEmptySlot();if(destination<0)return ItemStack.EMPTY;
        var crafted=original.copyWithCount(1);data.putLong("fuel",fuel+amount);data.putInt("energy",energy+amount);data.putBoolean("dim",false);ReunionData.item(crafted,data);
        var before=new ArrayList<ItemStack>();var after=new ArrayList<ItemStack>();var remainders=new ArrayList<ItemStack>();
        int tokenNeed=amount,bottleNeed=amount;
        for(int i=0;i<grid.size();i++){
            var source=grid.getStack(i);before.add(source.copy());var next=source.copy();
            if(i==wishSlot)next.decrement(1);
            else if(next.isOf(NonAffinityItems.FRIENDSHIP_TOKEN)){int take=Math.min(tokenNeed,next.getCount());next.decrement(take);tokenNeed-=take;}
            else if(ReunionRecipe.bottle(next)){int take=Math.min(bottleNeed,next.getCount());next.decrement(take);bottleNeed-=take;
                if(take>0){var glass=new ItemStack(Items.GLASS_BOTTLE,take);if(next.isEmpty())next=glass;else remainders.add(glass);}}
            after.add(next);
        }
        if(tokenNeed!=0||bottleNeed!=0)return ItemStack.EMPTY;
        // All checks precede mutation. Inserting the result reserves capacity before glass is returned.
        player.getInventory().setStack(destination,crafted);
        result.unlockLastRecipe(player,before);
        for(int i=0;i<after.size();i++)grid.setStack(i,after.get(i));
        for(var glass:remainders)player.getInventory().offerOrDrop(glass);
        output.setStack(ItemStack.EMPTY);grid.markDirty();player.getInventory().markDirty();handler.onContentChanged(grid);
        crafted.onCraftByPlayer(player,1);handler.sendContentUpdates();ReunionChargeFeedback.crafted(player,original.getCount()==1?original:ItemStack.EMPTY,crafted,energy,energy+amount);return crafted.copy();
    }
    private ReunionBatchCrafting(){}
}

