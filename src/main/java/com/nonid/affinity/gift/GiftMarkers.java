package com.nonid.affinity.gift;
import java.util.*;
/** Private markers already filtered by the server, grouped without mutating tickets. */
public final class GiftMarkers {
    public record Group(GiftEnvelope.Location location, int count, UUID nextGift, String nextName) {}
    private GiftMarkers() {}
    public static List<Group> aggregate(List<GiftService.Marker> markers) {
        var grouped=new LinkedHashMap<GiftEnvelope.Location,List<GiftService.Marker>>();
        for(var marker:markers) grouped.computeIfAbsent(marker.location(),key->new ArrayList<>()).add(marker);
        var result=new ArrayList<Group>();
        grouped.forEach((location,rows)-> { var next=rows.getFirst(); result.add(new Group(location,rows.size(),next.gift(),next.name())); });
        return List.copyOf(result);
    }
}
