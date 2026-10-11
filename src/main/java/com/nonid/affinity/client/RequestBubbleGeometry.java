package com.nonid.affinity.client;

/** Pure geometry shared by rasterized adaptive tail and shape hit testing. */
public final class RequestBubbleGeometry {
    private RequestBubbleGeometry() {}
    public static final int WIDTH=520, HEIGHT=286, BUTTON_WIDTH=160, BUTTON_HEIGHT=62, BUTTON_TOP=170;
    public static final float HEIGHT_RATIO=286f*380/(520*208);
    public static float localX(float x) { return x*380/WIDTH; }
    public static float localY(float y) { return y*208/HEIGHT; }
    public static int buttonLeft(int choice) { return choice==1?90:270; }
    public static float physicalWidth(int framebufferWidth) { return Math.clamp(framebufferWidth*.305f,360f,520f); }
    public static boolean ellipse(double x,double y) {
        double dx=(x-189.5)/184.5,dy=(y-105.5)/88.5;return dx*dx+dy*dy<=1;
    }
    private static double cross(double ax,double ay,double bx,double by,double px,double py){return (bx-ax)*(py-ay)-(by-ay)*(px-ax);}
    public static double[] tail(double tx,double ty){
        if(Math.abs(tx-32)<1&&Math.abs(ty-205)<1)return new double[]{66,158,32,205,111,174};
        if(Math.abs(tx-347)<1&&Math.abs(ty-205)<1)return new double[]{313,158,347,205,268,174};
        double a=Math.atan2((ty-105.5)/88.5,(tx-189.5)/184.5);
        return new double[]{189.5+165*Math.cos(a-.22),105.5+79*Math.sin(a-.22),tx,ty,
                189.5+165*Math.cos(a+.22),105.5+79*Math.sin(a+.22)};
    }
    public static boolean inside(double x,double y,double[] t){
        if(ellipse(x,y))return true;
        double a=cross(t[0],t[1],t[2],t[3],x,y),b=cross(t[2],t[3],t[4],t[5],x,y),c=cross(t[4],t[5],t[0],t[1],x,y);
        return (a>=0&&b>=0&&c>=0)||(a<=0&&b<=0&&c<=0);
    }
    public static int button(double x,double y){
        double mx=x*WIDTH/380, my=y*HEIGHT/208;
        if(my<BUTTON_TOP||my>=BUTTON_TOP+BUTTON_HEIGHT)return 0;
        for(int choice=1;choice<=2;choice++)
            if(mx>=buttonLeft(choice)&&mx<buttonLeft(choice)+BUTTON_WIDTH)return choice;
        return 0;
    }
}