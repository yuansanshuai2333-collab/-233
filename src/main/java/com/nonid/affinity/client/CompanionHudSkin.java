package com.nonid.affinity.client;

import com.google.gson.*;
import com.nonid.affinity.NonCreatureAffinity;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.gui.DrawContext;

/** Exact approved v7 frame anchors; all numeric text remains live GUI text. */
final class CompanionHudSkin {
    private static final JsonObject MANIFEST=load();
    private static JsonObject load(){try(var in=CompanionHudSkin.class.getResourceAsStream("/assets/noncreatureaffinity/textures/gui/companion/hud-v7/manifest.json")){
        if(in==null)throw new java.io.IOException("Missing HUD v7 manifest");return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
    }catch(java.io.IOException|RuntimeException failure){NonCreatureAffinity.LOGGER.warn("HUD v7 anchors unavailable",failure);return new JsonObject();}}
    private static int round(double n){return (int)Math.rint(n);}
    static String stage(String value){return value==null||value.equals("new")?"first":value;}
    static void frame(DrawContext c,int x,int y,int w,int h,String stage,float layoutScale){
        c.fill(x,y,x+w,y+h,0xDC211624);c.fill(x,y,x+w,y+1,0xFFAD5A83);c.fill(x,y+h-1,x+w,y+h,0xFFAD5A83);c.fill(x,y,x+1,y+h,0xFFAD5A83);c.fill(x+w-1,y,x+w,y+h,0xFFAD5A83);
        var relations=MANIFEST.getAsJsonObject("relationships");if(relations==null||!relations.has(stage(stage)))return;
        for(var v:relations.getAsJsonArray(stage(stage))){var recipe=v.getAsJsonObject();var key=recipe.get("part").getAsString();JsonObject part=null;
            for(var p:MANIFEST.getAsJsonArray("frame_parts"))if(p.getAsJsonObject().get("id").getAsString().equals(key)){part=p.getAsJsonObject();break;}
            if(part==null)continue;double scale=recipe.get("scale").getAsDouble()*layoutScale;var size=part.getAsJsonArray("size");var anchor=part.getAsJsonArray("local_anchor");
            var target=recipe.getAsJsonArray("target");double tx=target.get(0).getAsString().equals("x")?x:x+w-1;
            double ty=switch(target.get(1).getAsString()){case "y+h*0.46"->y+h*.46;case "y+h*0.52"->y+h*.52;case "y+h-1"->y+h-1;default->y;};
            CompanionSkin.image(c,"hud-v7/frame/"+key,round(tx-anchor.get(0).getAsDouble()*scale),round(ty-anchor.get(1).getAsDouble()*scale),
                    Math.max(1,round(size.get(0).getAsDouble()*scale)),Math.max(1,round(size.get(1).getAsDouble()*scale)),size.get(0).getAsInt(),size.get(1).getAsInt());
        }
    }
    static void bar(DrawContext c,int x,int y,int w,double value,double maximum,boolean health,String stage,float layoutScale){
        c.fill(x,y,x+w,y+7,0xFFB36589);c.fill(x+1,y+1,x+w-1,y+6,0xFF1D1421);
        double ratio=Double.isFinite(value)&&Double.isFinite(maximum)&&value>=0&&maximum>0?Math.clamp(value/maximum,0,1):0;
        int filled=round((w-4)*ratio);if(filled>0)c.fill(x+2,y+2,x+2+filled,y+5,health?0xFF81B8A0:0xFFEA75A7);
        if(health||stage==null||stage(stage).equals("first")||!MANIFEST.has("bar_overlays"))return;
        for(var v:MANIFEST.getAsJsonArray("bar_overlays")){var overlay=v.getAsJsonObject();if(!overlay.get("stage").getAsString().equals(stage(stage)))continue;
            var crop=overlay.getAsJsonArray("crop_before_resize");int top=crop.get(1).getAsInt(),height=22-top;
            CompanionSkin.part(c,"hud-v7/affinity/"+stage(stage)+"-decoration",x,y-round(3*layoutScale),w,Math.max(1,round(height*w/200.0)),0,top,200,height,200,22);
        }
    }
    private CompanionHudSkin(){}
}
