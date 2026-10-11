package com.nonid.affinity.client;
import java.util.*;
/** Bounded FIFO within priority, with finite queue age and bounded receipt memory. */
public final class GameplayNoticeQueue<T> {
 private record Pending<T>(UUID id,T value,int priority,long queued){}
 private final List<Pending<T>> pending=new ArrayList<>();
 private final LinkedHashMap<UUID,Long> seen=new LinkedHashMap<>();
 public boolean offer(UUID id,T value,int priority,long now){
  expire(now);if(seen.containsKey(id))return false;
  seen.put(id,now);while(seen.size()>1024)seen.remove(seen.keySet().iterator().next());
  if(pending.size()>=8){int victim=0;for(int i=1;i<pending.size();i++)if(pending.get(i).priority<pending.get(victim).priority)victim=i;if(pending.get(victim).priority>priority)return false;pending.remove(victim);}
  pending.add(new Pending<>(id,value,priority,now));return true;
 }
 public T poll(long now){expire(now);if(pending.isEmpty())return null;int best=0;for(int i=1;i<pending.size();i++)if(pending.get(i).priority>pending.get(best).priority)best=i;return pending.remove(best).value;}
 public int size(){return pending.size();}
 public void clear(){pending.clear();seen.clear();}
 private void expire(long now){pending.removeIf(p->now-p.queued>=20000);seen.entrySet().removeIf(e->now-e.getValue()>=30000);}
}
