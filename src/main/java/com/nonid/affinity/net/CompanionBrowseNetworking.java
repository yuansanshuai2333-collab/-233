package com.nonid.affinity.net;

import com.nonid.affinity.item.AffinityDetectorItem;
import com.nonid.affinity.service.CompanionDirectory;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.network.ServerPlayerEntity;

public final class CompanionBrowseNetworking {
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Integer> LAST=new HashMap<>();
    private static final Map<UUID,CompanionBrowse.Request> PENDING=new HashMap<>();
    private static final UUID ZERO=new UUID(0,0);
    private static final class Session {
        final UUID page,token=UUID.randomUUID();final String dimension;final List<String> ids;final Set<String> allowed;
        final int began;long sequence,optionSequence;int optionTick=-10;
        Session(ServerPlayerEntity player,UUID page,int now){this.page=page;this.began=now;this.dimension=player.getEntityWorld().getRegistryKey().getValue().toString();
            var data=CompanionDirectory.peek(player.getEntityWorld().getServer());ids=data==null?List.of():data.positiveIds(player.getUuid());allowed=Set.copyOf(ids);}
    }
    private CompanionBrowseNetworking() {}
    public static boolean eligible(ServerPlayerEntity p){return p.isAlive()&&!p.isSpectator()&&AffinityDetectorItem.isHeldBy(p);}
    public static void register(){
        PayloadTypeRegistry.playC2S().register(CompanionBrowse.Request.ID,CompanionBrowse.Request.CODEC);
        PayloadTypeRegistry.playS2C().register(CompanionBrowse.Reply.ID,CompanionBrowse.Reply.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CompanionBrowse.Request.ID,(q,c)->c.server().execute(()->{
            var p=c.player();if(!ServerPlayNetworking.canSend(p,CompanionBrowse.Reply.ID))return;
            int now=c.server().getTicks();var last=LAST.get(p.getUuid());if(last!=null&&now-last>=0&&now-last<10){PENDING.put(p.getUuid(),q);return;}
            PENDING.remove(p.getUuid());
            LAST.put(p.getUuid(),now);ServerPlayNetworking.send(p,read(p,q,now));
        }));
        ServerTickEvents.END_SERVER_TICK.register(server->SESSIONS.entrySet().removeIf(e->{var p=server.getPlayerManager().getPlayer(e.getKey());
            return p==null||!eligible(p)||!e.getValue().dimension.equals(p.getEntityWorld().getRegistryKey().getValue().toString())||server.getTicks()-e.getValue().began>=1200;}));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            var iterator=PENDING.entrySet().iterator();
            while(iterator.hasNext()){
                var next=iterator.next();var p=server.getPlayerManager().getPlayer(next.getKey());
                if(p==null){iterator.remove();continue;}
                int now=server.getTicks();if(now-LAST.getOrDefault(next.getKey(),now-10)<10)continue;
                var q=next.getValue();iterator.remove();LAST.put(p.getUuid(),now);
                if(ServerPlayNetworking.canSend(p,CompanionBrowse.Reply.ID))ServerPlayNetworking.send(p,read(p,q,now));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{SESSIONS.remove(h.player.getUuid());LAST.remove(h.player.getUuid());PENDING.remove(h.player.getUuid());});
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{SESSIONS.clear();LAST.clear();PENDING.clear();});
    }
    /** Side effect is a bounded per-connection pagination session only; never writes the directory or entities. */
    public static CompanionBrowse.Reply read(ServerPlayerEntity p,CompanionBrowse.Request q,int now){
        if(!eligible(p)){SESSIONS.remove(p.getUuid());return empty(q,ZERO,"detector_required");}
        if(q.sequence()<=0||q.index()<0)return empty(q,ZERO,"invalid");
        var session=SESSIONS.get(p.getUuid());
        if(session==null||!session.page.equals(q.page())){
            if(q.index()!=0||!q.selected().isEmpty())return empty(q,ZERO,"expired");
            session=new Session(p,q.page(),now);SESSIONS.put(p.getUuid(),session);
        }
        if(!session.dimension.equals(p.getEntityWorld().getRegistryKey().getValue().toString())||now-session.began>=1200)return empty(q,session.token,"expired");
        if(q.sequence()<=session.sequence)return empty(q,session.token,"invalid");session.sequence=q.sequence();
        if(!q.selected().isEmpty()){
            var row=session.allowed.contains(q.selected())?CompanionDirectory.read(p,q.selected()):null;
            return new CompanionBrowse.Reply(q.page(),q.sequence(),session.token,q.index(),false,row==null?"unavailable":"detail",row==null?List.of():List.of(row));
        }
        long start=(long)q.index()*CompanionBrowse.PAGE_SIZE;
        if(start>session.ids.size())return empty(q,session.token,"invalid");
        int end=(int)Math.min(session.ids.size(),start+CompanionBrowse.PAGE_SIZE);var rows=new ArrayList<CompanionRecord>();
        for(int i=(int)start;i<end;i++){var row=CompanionDirectory.read(p,session.ids.get(i));if(row!=null)rows.add(row);}
        return new CompanionBrowse.Reply(q.page(),q.sequence(),session.token,q.index(),end<session.ids.size(),"ready",List.copyOf(rows));
    }
    public static DirectInteractionOption option(ServerPlayerEntity p,DirectInteractionOption q,int now){
        var s=SESSIONS.get(p.getUuid());
        if(!eligible(p)||s==null||!s.page.equals(q.page())||!s.token.equals(q.session())
                ||!s.dimension.equals(p.getEntityWorld().getRegistryKey().getValue().toString())||now-s.began>=1200)
            return q.reply("expired",false);
        if(q.sequence()<=s.optionSequence||q.sequence()<=0)return q.reply("invalid",false);
        s.optionSequence=q.sequence();
        if(now-s.optionTick>=0&&now-s.optionTick<5)return q.reply("busy",false);
        s.optionTick=now;
        if(!com.nonid.affinity.service.DirectInteractionPreference.validIdentity(q.target())||!s.allowed.contains(q.target())||CompanionDirectory.read(p,q.target())==null)return q.reply("unavailable",false);
        if(q.write())com.nonid.affinity.service.DirectInteractionPreference.set(p,q.target(),q.enabled());
        return q.reply("ready",com.nonid.affinity.service.DirectInteractionPreference.enabled(p,q.target()));
    }
    private static CompanionBrowse.Reply empty(CompanionBrowse.Request q,UUID token,String status){return new CompanionBrowse.Reply(q.page(),q.sequence(),token,q.index(),false,status,List.of());}
}


