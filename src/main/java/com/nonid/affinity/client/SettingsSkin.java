package com.nonid.affinity.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;

/** Integer-coordinate, nearest-filtered pieces of the approved artwork. */
@Environment(EnvType.CLIENT)
final class SettingsSkin {
    private SettingsSkin() {}
    static void part(DrawContext c, String name, int x, int y, int w, int h,
                     int u, int v, int sw, int sh, int tw, int th) {
        if (w <= 0 || h <= 0 || sw <= 0 || sh <= 0) return;
        c.drawTexture(RenderPipelines.GUI_TEXTURED, Identifier.of("noncreatureaffinity", "textures/gui/settings/" + name + ".png"),
                x, y, (float)u, (float)v, w, h, sw, sh, tw, th);
    }
    static void image(DrawContext c, String name, int x, int y, int w, int h, int tw, int th) {
        part(c, name, x, y, w, h, 0, 0, tw, th, tw, th);
    }
    static void horizontal(DrawContext c, String n, int x, int y, int w, int h, int tw, int th, int se, int de) {
        part(c,n,x,y,de,h,0,0,se,th,tw,th);
        part(c,n,x+de,y,w-2*de,h,se,0,tw-2*se,th,tw,th);
        part(c,n,x+w-de,y,de,h,tw-se,0,se,th,tw,th);
    }
    static void nine(DrawContext c, String name, int x, int y, int w, int h, int tw, int th, int sourceEdge, int edge) {
        edge = Math.min(edge, Math.min(w, h) / 2);
        int[] sx = {0, sourceEdge, tw - sourceEdge}, sy = {0, sourceEdge, th - sourceEdge};
        int[] sw = {sourceEdge, tw - 2 * sourceEdge, sourceEdge}, sh = {sourceEdge, th - 2 * sourceEdge, sourceEdge};
        int[] dx = {x, x + edge, x + w - edge}, dy = {y, y + edge, y + h - edge};
        int[] dw = {edge, w - 2 * edge, edge}, dh = {edge, h - 2 * edge, edge};
        for (int a = 0; a < 3; a++) for (int b = 0; b < 3; b++) part(c, name, dx[a], dy[b], dw[a], dh[b], sx[a], sy[b], sw[a], sh[b], tw, th);
    }
    static void frame(DrawContext c, int x, int y, int w, int h) {
        // Distribute extra length over plain seams. Preserve every unique border motif once.
        double scale = Math.min(1.0, Math.min(w / 320.0, h / 240.0));
        int edge=(int)(20*scale);
        frameStrip(c,x,y,w,edge,0,320,0,20,true,scale,new int[]{33,80,120,167,202,240,286});
        frameStrip(c,x,y+h-edge,w,edge,0,320,220,20,true,scale,new int[]{33,80,120,167,202,240,286});
        frameStrip(c,x,y+edge,edge,h-2*edge,20,220,0,20,false,scale,new int[]{50,90,130,170,210});
        frameStrip(c,x+w-edge,y+edge,edge,h-2*edge,20,220,300,20,false,scale,new int[]{50,90,130,170,210});
        // Crop from the confirmed composite, preserving its exact corner composition and proportions.
        part(c, "frame/composite", x - 10, y - 10, 34, 30, 88, 28, 102, 90, 1440, 1080);
        part(c, "frame/composite", x + w - 27, y - 16, 39, 40, 1315, 4, 117, 120, 1440, 1080);
        int ch = Math.min(83, h / 4), cw = ch * 216 / 249;
        part(c, "frame/composite", x + w - cw - 3, y + h - ch - 3, cw, ch, 1180, 767, 216, 249, 1440, 1080);
    }
    private static void frameStrip(DrawContext c,int x,int y,int w,int h,int first,int end,int other,int thickness,
                                   boolean horizontal,double scale,int[] seams) {
        int total=horizontal?w:h, used=0, source=first;
        for(int i=0;i<=seams.length;i++) {
            int cut=i==seams.length?end:seams[i];
            int destination=i==seams.length?total:(int)Math.round((cut-first)*scale
                    +(total-(end-first)*scale)*i/seams.length);
            int length=destination-used;
            if(horizontal) part(c,"frame/border",x+used,y,length,h,source,other,cut-source,thickness,320,240);
            else part(c,"frame/border",x,y+used,w,length,other,source,thickness,cut-source,320,240);
            used=destination; source=cut;
            if(i<seams.length) {
                int after=(int)Math.round((cut-first)*scale+(total-(end-first)*scale)*(i+1)/seams.length);
                if(horizontal) part(c,"frame/border",x+used,y,after-used,h,cut,other,1,thickness,320,240);
                else part(c,"frame/border",x,y+used,w,after-used,other,cut,thickness,1,320,240);
                used=after;
            }
        }
    }
    static void scrollTrack(DrawContext c, int x, int y, int h) {
        String n = "widgets/scrollbar/track";
        part(c,n,x,y,14,8,0,0,28,16,28,128);
        part(c,n,x,y+h-8,14,8,0,112,28,16,28,128);
        // Repeat only a plain track segment. Draw the unique middle hanging decoration once.
        for(int yy=y+8; yy<y+h-8; yy+=8) part(c,n,x,yy,14,Math.min(8,y+h-8-yy),0,24,28,Math.min(16,2*(y+h-8-yy)),28,128);
        part(c,n,x,y+h*2/3,14,14,0,76,28,28,28,128);
    }
    static void scrollThumb(DrawContext c, int x, int y, int h) {
        String n="widgets/scrollbar/thumb";
        part(c,n,x,y,7,3,0,0,14,6,14,30);
        part(c,n,x,y+3,7,h-6,0,6,14,2,14,30);
        part(c,n,x,y+h-3,7,3,0,24,14,6,14,30);
        part(c,n,x,y+h/2-4,7,8,0,8,14,16,14,30);
    }
}

