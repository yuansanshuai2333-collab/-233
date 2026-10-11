package com.nonid.affinity.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nonid.affinity.reunion.*;
import com.nonid.affinity.item.NonAffinityItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
/** Observe the committed ordinary recharge; recipe previews and unsuccessful output takes stay silent. */
@Mixin(CraftingResultSlot.class)
public abstract class ReunionCraftingFeedbackMixin {
    @WrapMethod(method="onTakeItem")
    private void affinity$chargeFeedback(PlayerEntity player,ItemStack result,Operation<Void> original){
        var self=(CraftingResultSlot)(Object)this;var grid=((ReunionCraftingInputAccessor)this).noncreatureaffinity$reunionInput();
        int sourceSlot=-1,before=-1,count=0;ItemStack source=ItemStack.EMPTY,sourceIdentity=ItemStack.EMPTY;
        if(player instanceof ServerPlayerEntity p&&result.isOf(NonAffinityItems.REUNION_WISH)
                &&self.inventory instanceof CraftingResultInventory output&&output.getLastRecipe()!=null
                &&output.getLastRecipe().value() instanceof ReunionRecipe&&ReunionRecipe.INSTANCE.matches(grid.createRecipeInput(),p.getEntityWorld())){
            for(int i=0;i<grid.size();i++)if(grid.getStack(i).isOf(NonAffinityItems.REUNION_WISH)){sourceSlot=i;sourceIdentity=grid.getStack(i);source=sourceIdentity.copy();count=source.getCount();var n=ReunionData.item(source);before=ReunionData.energy(n,ReunionData.record(p.getEntityWorld().getServer(),n.getString("mob","")));break;}
        }
        original.call(player,result);
        if(sourceSlot<0||!(player instanceof ServerPlayerEntity p))return;
        var left=grid.getStack(sourceSlot);if(left.isOf(NonAffinityItems.REUNION_WISH)&&left.getCount()>=count)return;
        var old=ReunionData.item(source);var next=ReunionData.item(result);var record=ReunionData.record(p.getEntityWorld().getServer(),old.getString("mob",""));
        if(!old.getString("mob","").equals(next.getString("mob",""))||!old.getString("bond","").equals(next.getString("bond",""))||!ReunionData.authentic(next,record))return;
        int after=ReunionData.energy(next,record);long delta=ReunionData.fuel(next)-ReunionData.fuel(old);
        if(delta>0&&delta<=3&&delta==after-before)ReunionChargeFeedback.crafted(p,count==1?sourceIdentity:ItemStack.EMPTY,result,before,after);
    }
}
