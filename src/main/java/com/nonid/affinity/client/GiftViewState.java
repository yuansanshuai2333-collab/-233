package com.nonid.affinity.client;
import com.nonid.affinity.net.*;
import java.util.*;
/** Exact request/session correlation, separate from rendering and gameplay. */
public final class GiftViewState {
 private UUID session=UUID.randomUUID();private long sequence,pending;private String dimension="";
 private List<GiftViewPayload.Row> rows=List.of();private final Set<UUID> settled=new LinkedHashSet<>();
 public UUID session(){return session;} public List<GiftViewPayload.Row> rows(){return rows;}
 public void reset(String dimension){session=UUID.randomUUID();sequence=0;pending=0;this.dimension=dimension;rows=List.of();settled.clear();}
 public GiftQueryPayload query(){if(sequence==Long.MAX_VALUE)reset(dimension);pending=++sequence;return new GiftQueryPayload(session,pending);}
 public boolean accept(GiftViewPayload p){if(!session.equals(p.session())||pending==0||p.sequence()!=pending||!dimension.equals(p.dimension()))return false;pending=0;rows=p.rows().stream().filter(r->!settled.contains(r.gift())).toList();return true;}
 public void expire(){rows=List.of();}
 public boolean settled(GiftResultPayload p){if(!session.equals(p.session())||!p.status().equals("claimed"))return false;if(settled.size()>=128)settled.remove(settled.iterator().next());settled.add(p.gift());rows=rows.stream().filter(r->!p.gift().equals(r.gift())).toList();return true;}
}
