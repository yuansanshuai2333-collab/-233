package com.nonid.affinity.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;

/** Only runtime-approved base assets. Unknown slice geometry is never inferred. */
final class CompanionSkin {
    private CompanionSkin() {}
    static void part(DrawContext c, String name, int x, int y, int w, int h, int u, int v, int sw, int sh, int tw, int th) {
        if (w <= 0 || h <= 0) return;
        c.drawTexture(RenderPipelines.GUI_TEXTURED, Identifier.of("noncreatureaffinity", "textures/gui/companion/" + name + ".png"),
                x, y, (float)u, (float)v, w, h, sw, sh, tw, th);
    }
    static void image(DrawContext c, String n, int x, int y, int w, int h, int tw, int th) { part(c,n,x,y,w,h,0,0,tw,th,tw,th); }
    static void nine(DrawContext c, String n, int x, int y, int w, int h, int tw, int th) {
        int e = Math.min(4, Math.min(w,h)/2);
        int[] sx={0,4,tw-4}, sy={0,4,th-4}, sw={4,tw-8,4}, sh={4,th-8,4};
        int[] dx={x,x+e,x+w-e}, dy={y,y+e,y+h-e}, dw={e,w-2*e,e}, dh={e,h-2*e,e};
        for(int a=0;a<3;a++) for(int b=0;b<3;b++) part(c,n,dx[a],dy[b],dw[a],dh[b],sx[a],sy[b],sw[a],sh[b],tw,th);
    }
}
