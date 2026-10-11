package com.nonid.affinity.client;
import com.nonid.affinity.item.NonAffinityItems;
import com.nonid.affinity.net.ReunionChargePayload;
import com.nonid.affinity.net.ReunionFullPayload;
import net.minecraft.util.Identifier;
import com.nonid.affinity.reunion.ReunionData;
import java.util.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.HeldItemContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Arm;

/** Changes only the transient render copy of the matching, actually held item. */
@Environment(EnvType.CLIENT)
public final class ReunionChargeClient {
 private record Visual(UUID receipt,Identifier dimension,String bond,long fuel,int before,int after,boolean offHand) {}
 private static final class Entry {
  final Visual payload;final long started=System.nanoTime();ItemStack anchored;
  Entry(Visual payload){this.payload=payload;}
 }
 private static final Map<Hand,Entry> ACTIVE=new EnumMap<>(Hand.class);
 private static final Set<UUID> SEEN=new LinkedHashSet<>();
 private ReunionChargeClient(){}
 public static void register(){
  ClientPlayNetworking.registerGlobalReceiver(ReunionChargePayload.ID,(p,c)->c.client().execute(()->accept(new Visual(p.receipt(),p.dimension(),p.bond(),p.fuel(),p.before(),p.after(),p.offHand()))));
  ClientPlayNetworking.registerGlobalReceiver(ReunionFullPayload.ID,(p,c)->c.client().execute(()->accept(new Visual(p.receipt(),p.dimension(),p.bond(),p.fuel(),3,3,p.offHand()))));
  ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{ACTIVE.clear();SEEN.clear();});
  ClientTickEvents.END_CLIENT_TICK.register(c->{
   if(c.world==null||c.player==null||!c.player.isAlive()){ACTIVE.clear();return;}
   long now=System.nanoTime();ACTIVE.entrySet().removeIf(entry->{
    var e=entry.getValue();var stack=c.player.getStackInHand(entry.getKey());
    return !e.payload.dimension().equals(c.world.getRegistryKey().getValue())||ReunionChargeTimeline.frame((now-e.started)/1_000_000)<0
      ||(e.anchored!=null&&stack!=e.anchored)||!e.payload.bond().equals(ReunionData.item(stack).getString("bond",""));
   });
  });
 }
 private static void accept(Visual p){
  var c=MinecraftClient.getInstance();
  if(c.player==null||c.world==null||p.bond().isEmpty()||!(ReunionChargeTimeline.valid(p.before(),p.after())||p.before()==3&&p.after()==3)
    ||!p.dimension().equals(c.world.getRegistryKey().getValue())||!SEEN.add(p.receipt()))return;
  if(SEEN.size()>1024)SEEN.remove(SEEN.iterator().next());
  Hand hand=p.offHand()?Hand.OFF_HAND:Hand.MAIN_HAND;
  var held=c.player.getStackInHand(hand);var data=ReunionData.item(held);
  if(!p.bond().equals(data.getString("bond","")))return;
  if(p.before()==3&&(ReunionData.fuel(data)!=p.fuel()||data.getInt("energy",-1)!=3))return;
  var entry=new Entry(p);
  if(ReunionData.fuel(data)==p.fuel()&&data.getInt("energy",-1)==p.after())entry.anchored=held;
  ACTIVE.put(hand,entry);
 }
 public static ItemStack renderCopy(ItemStack stack,ItemDisplayContext display,HeldItemContext holder){
  var c=MinecraftClient.getInstance();
  if(c.player==null||c.world==null||holder==null||holder.getEntity()!=c.player||!stack.isOf(NonAffinityItems.REUNION_WISH))return stack;
  if(display!=ItemDisplayContext.FIRST_PERSON_LEFT_HAND&&display!=ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
    &&display!=ItemDisplayContext.THIRD_PERSON_LEFT_HAND&&display!=ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)return stack;
  boolean left=display==ItemDisplayContext.FIRST_PERSON_LEFT_HAND||display==ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
  Hand hand=left==(c.player.getMainArm()==Arm.LEFT)?Hand.MAIN_HAND:Hand.OFF_HAND;
  var held=c.player.getStackInHand(hand);var e=ACTIVE.get(hand);if(e==null)return stack;
  if(e.anchored!=null&&held!=e.anchored){ACTIVE.remove(hand);return stack;}
  var data=ReunionData.item(stack);var actual=ReunionData.item(held);
  var p=e.payload;int frame=ReunionChargeTimeline.frame((System.nanoTime()-e.started)/1_000_000);
  if(frame<0||!p.dimension().equals(c.world.getRegistryKey().getValue())||!p.bond().equals(data.getString("bond",""))
    ||!p.bond().equals(actual.getString("bond",""))||ReunionData.fuel(actual)!=p.fuel()||actual.getInt("energy",-1)!=p.after()
    ||ReunionData.fuel(data)!=p.fuel()||data.getInt("energy",-1)!=p.after())return stack;
  e.anchored=held;
  ItemStack copy=stack.copy();copy.set(DataComponentTypes.ITEM_MODEL,ReunionData.id(String.format(Locale.ROOT,"charge/%d-%d-%03d",p.before(),p.after(),frame)));
  return copy;
 }
}
