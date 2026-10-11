package com.nonid.affinity.gift;
import java.util.*;
import java.util.function.Function;
import net.minecraft.util.math.BlockPos;
/** Complete finite sphere search, resumed under a per-tick probe budget. Unchecked/unloaded never means loss. */
public final class GiftPlacement {
    public enum Probe { SAFE, UNSAFE, UNLOADED }
    public enum Status { PLACED, SEARCHING, WAITING_LOAD, LOST }
    public record Result(Status status, GiftEnvelope.Location location, int checked, int cursor, boolean unloaded) {}
    private static final List<BlockPos> OFFSETS;
    static {
        var list=new ArrayList<BlockPos>();
        for(int x=-16;x<=16;x++)for(int y=-16;y<=16;y++)for(int z=-16;z<=16;z++)list.add(new BlockPos(x,y,z));
        list.sort(Comparator.comparingInt((BlockPos p)->p.getX()*p.getX()+p.getY()*p.getY()+p.getZ()*p.getZ()).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        OFFSETS=List.copyOf(list);
    }
    private GiftPlacement() {}
    public static int maxCursor(){return OFFSETS.size();}
    public static Result search(GiftEnvelope.Location death,Function<BlockPos,Probe> probe){return search(death,probe,0,false,4096);}
    public static Result search(GiftEnvelope.Location death,Function<BlockPos,Probe> probe,int cursor,boolean unloaded,int budget){
        if(cursor<0||cursor>OFFSETS.size()||budget<0||budget>4096)throw new IllegalArgumentException("Invalid gift placement search progress");
        var origin=BlockPos.ofFloored(death.x(),death.y(),death.z());int checked=0,index=cursor;
        while(index<OFFSETS.size()){
            if(checked>=budget)return new Result(Status.SEARCHING,null,checked,index,unloaded);
            var pos=origin.add(OFFSETS.get(index++));if(distance(death,pos)>256)continue;checked++;
            switch(probe.apply(pos)){
                case SAFE->{return new Result(Status.PLACED,new GiftEnvelope.Location(death.dimension(),pos.getX()+.5,pos.getY(),pos.getZ()+.5),checked,0,false);}
                case UNLOADED->unloaded=true;
                case UNSAFE->{}
            }
        }
        return new Result(unloaded?Status.WAITING_LOAD:Status.LOST,null,checked,0,false);
    }
    private static double distance(GiftEnvelope.Location death,BlockPos pos){double x=pos.getX()+.5-death.x(),y=pos.getY()-death.y(),z=pos.getZ()+.5-death.z();return x*x+y*y+z*z;}
}
