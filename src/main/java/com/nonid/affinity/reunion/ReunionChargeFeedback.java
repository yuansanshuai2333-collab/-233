package com.nonid.affinity.reunion;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import com.nonid.affinity.net.ReunionChargePayload;
import com.nonid.affinity.net.ReunionFullPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
/** Coalesces successful changes only. The action bar never inserts one chat line per item. */
public final class ReunionChargeFeedback {
    private static final Map<UUID,Batch> PENDING=new HashMap<>();
    private static final Map<UUID,Integer> LAST=new HashMap<>();
    private static final class Batch {
        final IdentityHashMap<ItemStack,Change> items=new IdentityHashMap<>();
        final IdentityHashMap<ItemStack,Change> crafts=new IdentityHashMap<>();
        final IdentityHashMap<ItemStack,Change> full=new IdentityHashMap<>();
        final ReunionChargeTextBatch<ItemStack> text=new ReunionChargeTextBatch<>();long order;
    }
    private record Change(String bond,long fuel,int before,int after,int gained,long order) {}
    public static void register(){
        ServerTickEvents.END_SERVER_TICK.register(ReunionChargeFeedback::flush);
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{PENDING.remove(h.player.getUuid());LAST.remove(h.player.getUuid());});
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{PENDING.clear();LAST.clear();});
    }
    /** Material charging keeps its aggregate text, even when the result is actually held. */
    public static void crafted(ServerPlayerEntity player,ItemStack consumed,ItemStack item,int before,int after){
        if(before<0||after>3||after<=before)return;
        record(player,item,before,after,false);
        if(consumed!=ItemStack.EMPTY)PENDING.get(player.getUuid()).text.inherit(consumed,item);
    }
    /** Only the consumed normal-interaction completion path may request animation. */
    public static void completedInteraction(ServerPlayerEntity player,ItemStack item,int before,int after){
        record(player,item,before,after,true);
    }
    /** Invoked only after full receipt, owner and completion eligibility validation. */
    public static void fullInteraction(ServerPlayerEntity player,ItemStack item){
        // Full inventory items have no text fallback and need no pending display state.
        if(player.getMainHandStack()!=item&&player.getOffHandStack()!=item)return;
        var data=ReunionData.item(item);
        var batch=PENDING.computeIfAbsent(player.getUuid(),id->new Batch());
        batch.full.put(item,new Change(data.getString("bond",""),ReunionData.fuel(data),3,3,0,0));
    }
    private static void record(ServerPlayerEntity player,ItemStack item,int before,int after,boolean completion){
        if(before<0||after>3||after<=before)return;
        var data=ReunionData.item(item);String bond=data.getString("bond","");
        var batch=PENDING.computeIfAbsent(player.getUuid(),id->new Batch());
        var items=completion?batch.items:batch.crafts;
        var old=items.get(item);
        int start=old!=null&&old.bond.equals(bond)&&old.after==before?old.before:before;
        items.put(item,new Change(bond,ReunionData.fuel(data),start,after,(old==null?0:old.gained)+after-before,++batch.order));
    }
    private static void flush(MinecraftServer server){
        int now=server.getTicks();var iterator=PENDING.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();var p=server.getPlayerManager().getPlayer(entry.getKey());
            if(p==null){iterator.remove();LAST.remove(entry.getKey());continue;}
            if(now-LAST.getOrDefault(entry.getKey(),now-10)<10)continue;
            var b=entry.getValue();iterator.remove();LAST.put(entry.getKey(),now);
            boolean supported=ServerPlayNetworking.canSend(p,ReunionChargePayload.ID);
            var text=b.text;
            b.items.keySet().forEach(text::track);b.crafts.keySet().forEach(text::track);
            // Keep sources separate: a craft must never expand an interaction animation's delta.
            for(var source:List.of(b.crafts,b.items))for(var item:source.entrySet()) {
                var change=item.getValue();var stack=item.getKey();
                boolean main=p.getMainHandStack()==stack,off=p.getOffHandStack()==stack;
                if(source==b.items&&supported&&(main||off)&&matches(stack,change)) {
                    ServerPlayNetworking.send(p,new ReunionChargePayload(UUID.randomUUID(),p.getEntityWorld().getRegistryKey().getValue(),change.bond,change.fuel,change.before,change.after,!main));
                } else {
                    text.add(stack,change.gained,change.after,change.order);
                }
            }
            if(ServerPlayNetworking.canSend(p,ReunionFullPayload.ID))for(var item:b.full.entrySet()){
                var stack=item.getKey();var change=item.getValue();
                boolean main=p.getMainHandStack()==stack,off=p.getOffHandStack()==stack;
                // A real increase already coalesced in this window takes visual precedence.
                if(!b.items.containsKey(stack)&&(main||off)&&matches(stack,change))
                    ServerPlayNetworking.send(p,new ReunionFullPayload(UUID.randomUUID(),p.getEntityWorld().getRegistryKey().getValue(),change.bond,change.fuel,!main));
            }
            int textItems=text.itemCount(),textGained=text.gained();
            // Crafts, inventory-only completions and legacy clients retain aggregate text.
            if(textItems>0)p.sendMessage(textItems==1&&text.totalItemCount()==1?Text.translatable("message.noncreatureaffinity.reunion_charged",text.singleAfter()):Text.translatable("message.noncreatureaffinity.reunion_charged_many",textItems,textGained),true);
        }
    }
    private static boolean matches(ItemStack stack,Change change){
        var n=ReunionData.item(stack);return !change.bond.isEmpty()&&change.bond.equals(n.getString("bond",""))
                &&change.fuel==ReunionData.fuel(n)&&change.after==n.getInt("energy",-1);
    }
    private ReunionChargeFeedback(){}
}
