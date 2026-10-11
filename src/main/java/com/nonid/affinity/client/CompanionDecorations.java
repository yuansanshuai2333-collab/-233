package com.nonid.affinity.client;

import com.google.gson.JsonParser;
import com.nonid.affinity.NonCreatureAffinity;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.gui.DrawContext;

final class CompanionDecorations {
    private record Part(String path,String anchor,int dx,int dy,int width,int height){}
    private static final Map<String,List<Part>> PARTS=load();
    private CompanionDecorations(){}
    private static Map<String,List<Part>> load(){
        var result=new HashMap<String,List<Part>>();
        for(String source:List.of("increment","counts")) {
        try(var input=CompanionDecorations.class.getResourceAsStream("/assets/noncreatureaffinity/textures/gui/companion/"+source+"/manifest.json")){
            if(input==null)continue;
            var manifest=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var element:manifest.getAsJsonArray("assets")){
                var asset=element.getAsJsonObject();var parts=new ArrayList<Part>();
                for(var p:asset.getAsJsonArray("parts")){var item=p.getAsJsonObject();var offset=item.getAsJsonArray("edge_offset");var size=item.getAsJsonArray("size");
                    parts.add(new Part(item.get("path").getAsString().replace("assets/","increment/").replace(".png",""),item.get("anchor").getAsString(),offset.get(0).getAsInt(),offset.get(1).getAsInt(),size.get(0).getAsInt(),size.get(1).getAsInt()));}
                result.put(asset.get("component").getAsString()+":"+asset.get("stage").getAsString(),List.copyOf(parts));
            }
        }catch(java.io.IOException|RuntimeException error){NonCreatureAffinity.LOGGER.warn("Companion decoration manifest unavailable",error);}
        }
        return Map.copyOf(result);
    }
    /** Tight original corner cutouts anchored to the actual detail frame, without preview-cell padding. */
    static void detailFrame(DrawContext c,String stage,int x,int y,int w,int h){
        if(stage==null||stage.equals("first")||stage.equals("new"))return;
        float scale=Math.min(.45f,w/600f);
        for(var part:PARTS.getOrDefault("main:"+stage,List.of())){
            int pw=Math.max(1,Math.round(part.width*scale)),ph=Math.max(1,Math.round(part.height*scale));
            int px=x+(part.anchor.contains("right")?w-pw:0);
            int py=y+(part.anchor.contains("bottom")?h-ph:0);
            CompanionSkin.image(c,part.path,px,py,pw,ph,part.width,part.height);
        }
    }
    static void draw(DrawContext c,String component,String stage,int x,int y,int w,int h,float scale){
        if(stage==null||stage.equals("first")||stage.equals("new"))return;
        for(var part:PARTS.getOrDefault(component+":"+stage,List.of())){
            int pw=Math.max(1,Math.round(part.width*scale)),ph=Math.max(1,Math.round(part.height*scale));
            int px=x+Math.round(part.dx*scale)+(part.anchor.contains("right")?w-pw:part.anchor.contains("center")?(w-pw)/2:0);
            int py=y+Math.round(part.dy*scale)+(part.anchor.contains("bottom")?h-ph:part.anchor.contains("middle")?(h-ph)/2:0);
            CompanionSkin.image(c,part.path,px,py,pw,ph,part.width,part.height);
        }
    }
}
