package com.nonid.affinity.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Shared confirmed colours; independent decoration anchors are never stretched. */
final class CompanionUi {
    static final int TEXT=0xFFF8EDF2, MUTED=0xFFBCA7B8, PINK=0xFFEFA3C7;
    static void frame(DrawContext c,int x,int y,int w,int h,boolean background) {
        if(background)c.fill(x,y,x+w,y+h,0xDB211624);
        border(c,x,y,w,h,0xFF80556F);
    }
    static void border(DrawContext c,int x,int y,int w,int h,int colour) {
        c.fill(x,y,x+w,y+1,colour);c.fill(x,y+h-1,x+w,y+h,colour);c.fill(x,y,x+1,y+h,colour);c.fill(x+w-1,y,x+w,y+h,colour);
    }
    static String trim(Text value,int w) {
        var tr=MinecraftClient.getInstance().textRenderer;String s=value.getString();if(tr.getWidth(s)<=w)return s;
        return tr.trimToWidth(s,Math.max(0,w-tr.getWidth("…")))+"…";
    }
    static void text(DrawContext c,Text text,int x,int y,int w,int colour){if(w>0)c.drawText(MinecraftClient.getInstance().textRenderer,trim(text,w),x,y,colour,false);}
    static void tooltip(DrawContext c,Text text,int mx,int my,int width,int height){
        var tr=MinecraftClient.getInstance().textRenderer;int w=Math.min(220,Math.max(60,width-16));var lines=tr.wrapLines(text,w-12);int h=lines.size()*11+12;
        int x=Math.clamp(mx+8,4,Math.max(4,width-w-4)),y=Math.clamp(my+12,4,Math.max(4,height-h-4));
        c.fill(x,y,x+w,y+h,0xF5211624);border(c,x,y,w,h,0xFFAD5A83);int at=y+6;for(var line:lines){c.drawText(tr,line,x+6,at,TEXT,false);at+=11;}
    }
    private CompanionUi(){}
}
