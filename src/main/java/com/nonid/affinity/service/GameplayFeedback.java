package com.nonid.affinity.service;
import com.nonid.affinity.net.GameplayNoticePayload;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
/** Routes explicit NaR gameplay send sites only; never intercepts global chat. */
public final class GameplayFeedback {
 private static final ThreadLocal<Integer> COMMAND=ThreadLocal.withInitial(()->0);
 private static final Map<UUID,LinkedHashMap<Object,GameplayNoticePayload>> PENDING=new HashMap<>();
 public static final class CommandScope implements AutoCloseable {
  private final int previous=COMMAND.get();private boolean closed;
  private CommandScope(){COMMAND.set(previous+1);}
  public void close(){if(!closed){closed=true;if(previous==0)COMMAND.remove();else COMMAND.set(previous);}}
 }
 public static CommandScope command(){return new CommandScope();}
 public static boolean commandActive(){return COMMAND.get()>0;}
 public static boolean supported(ServerPlayerEntity p){return !commandActive()&&ServerPlayNetworking.canSend(p,GameplayNoticePayload.ID);}
 public static void register(){
  ServerTickEvents.END_SERVER_TICK.register(s->{var snapshot=new HashMap<>(PENDING);PENDING.clear();snapshot.forEach((id,b)->{var p=s.getPlayerManager().getPlayer(id);if(p!=null)for(var notice:b.values())if(p.getEntityWorld().getRegistryKey().getValue().equals(notice.dimension())&&ServerPlayNetworking.canSend(p,GameplayNoticePayload.ID))ServerPlayNetworking.send(p,notice);});});
  ServerPlayConnectionEvents.DISCONNECT.register((h,s)->PENDING.remove(h.player.getUuid()));
  ServerLifecycleEvents.SERVER_STOPPED.register(s->{PENDING.clear();COMMAND.remove();});
 }
 public static void send(ServerPlayerEntity p,Text text,boolean overlay){
  if(!supported(p)){p.sendMessage(text,overlay);return;}
  var style=GameplayNoticeStyle.of(text);enqueue(p,text.copy(),style.kind(),style.priority(),style.title(),text.copy(),0,0,0);
 }
 public static void relationship(ServerPlayerEntity p,Entity mob,Text text,boolean overlay){
  if(!supported(p)){p.sendMessage(text,overlay);return;}
  var style=GameplayNoticeStyle.of(text);enqueue(p,"relationship:"+mob.getUuid(),1,style.title().equals("birth")?2:1,style.title(),text.copy(),0,0,0);
 }
 public static boolean decrease(ServerPlayerEntity p,Entity mob,int before,int after,int maximum,String reason){
  if(!supported(p))return false;
  Text detail=Text.translatable("message.noncreatureaffinity.feedback.decrease.detail",mob.getName(),before-after,Text.translatable("reason.noncreatureaffinity."+reason));
  enqueue(p,"decrease:"+mob.getUuid()+":"+before+":"+after+":"+reason,0,1,"decrease",detail,before,after,Math.max(0,maximum));return true;
 }
 private static void enqueue(ServerPlayerEntity p,Object event,int kind,int priority,String title,Text detail,int before,int after,int maximum){
  if(detail.getString().length()>2048)detail=Text.literal(detail.getString().substring(0,2048));
  var b=PENDING.computeIfAbsent(p.getUuid(),id->new LinkedHashMap<>());
  if(b.containsKey(event)&&b.get(event).priority()>priority)return;
  if(!b.containsKey(event)&&b.size()>=32){Object victim=null;int lowest=3;for(var e:b.entrySet())if(e.getValue().priority()<lowest){lowest=e.getValue().priority();victim=e.getKey();}if(lowest>priority)return;b.remove(victim);}
  b.put(event,new GameplayNoticePayload(UUID.randomUUID(),p.getEntityWorld().getRegistryKey().getValue(),kind,priority,Text.translatable("message.noncreatureaffinity.feedback."+title),detail,before,after,maximum));
 }
 private GameplayFeedback(){}
}
