package com.nonid.affinity.reunion;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Transient, explicit physical-item lineage. Register moves before aggregating feedback. */
final class ReunionChargeTextBatch<T> {
    private final IdentityHashMap<T,T> origins=new IdentityHashMap<>();
    private final Set<T> all=Collections.newSetFromMap(new IdentityHashMap<>());
    private final IdentityHashMap<T,Value> text=new IdentityHashMap<>();
    private static final class Value { int gained,after;long order=-1; }
    void inherit(T source,T result){if(source!=result)origins.put(result,identity(source));}
    private T identity(T item){return origins.getOrDefault(item,item);}
    void track(T item){all.add(identity(item));}
    void add(T item,int gained,int after,long order){
        track(item);var value=text.computeIfAbsent(identity(item),key->new Value());
        value.gained+=gained;
        if(order>value.order){value.order=order;value.after=after;}
    }
    int itemCount(){return text.size();}
    int totalItemCount(){return all.size();}
    int gained(){return text.values().stream().mapToInt(v->v.gained).sum();}
    int singleAfter(){if(text.size()!=1)throw new IllegalStateException("Not a single text item");return text.values().iterator().next().after;}
}
