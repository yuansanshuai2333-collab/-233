package com.nonid.affinity.client;

import com.nonid.affinity.NonCreatureAffinity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Passive compact head bubble. Actual render geometry is also the only clickable geometry. */
@Environment(EnvType.CLIENT)
public final class AffinityHud {
    private static final Identifier ELEMENT_ID=Identifier.of(NonCreatureAffinity.MOD_ID,"request_prompt");
    private static final StyleSpriteSource.Font FONT=new StyleSpriteSource.Font(Identifier.of(NonCreatureAffinity.MOD_ID,"request"));
    private record Hit(long token,int choice,long frame,int width,int height,float x,float y,float scale,
            boolean aimed,Vec3d entityPosition,Vec3d camera,org.joml.Quaternionf rotation) {}
    private record Placement(float x,float y,boolean mirror,boolean adaptive,float tipX,float tipY) {}
    private static Hit hit;
    private AffinityHud() {}
    public static void register(){HudElementRegistry.addLast(ELEMENT_ID,AffinityHud::render);}
    private static Text text(String key,Object...args){return Text.translatable("request_bubble.noncreatureaffinity."+key,args).styled(s->s.withFont(FONT));}
    private static void image(DrawContext c,String path,int x,int y,int w,int h,int u,int v,int sw,int sh,int tw,int th){
        c.drawTexture(RenderPipelines.GUI_TEXTURED,Identifier.of(NonCreatureAffinity.MOD_ID,"textures/gui/request/"+path+".png"),x,y,u,v,w,h,sw,sh,tw,th);
    }
    private static void image(DrawContext c,String path,int x,int y,int w,int h,int tw,int th){image(c,path,x,y,w,h,0,0,tw,th,tw,th);}
    private static void centered(DrawContext c,Text text,int center,int y,int maximum,float fontScale,int color){
        var r=MinecraftClient.getInstance().textRenderer;Text shown=text.copy().styled(s->s.withFont(FONT));
        int limit=Math.max(1,(int)(maximum/fontScale));
        if(r.getWidth(shown)>limit){String value=shown.getString();int end=value.length();do{end=value.offsetByCodePoints(end,-1);shown=Text.literal(value.substring(0,end)+"…").setStyle(shown.getStyle());}while(end>0&&r.getWidth(shown)>limit);}
        c.getMatrices().pushMatrix();c.getMatrices().translate(center,y);c.getMatrices().scale(fontScale,fontScale);
        c.drawText(r,shown,-r.getWidth(shown)/2,0,color,false);c.getMatrices().popMatrix();
    }
    private static boolean clearRay(MinecraftClient c,Vec3d point){
        return c.world.raycast(new RaycastContext(c.gameRenderer.getCamera().getCameraPos(),point,RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,c.player)).getType()==HitResult.Type.MISS;
    }
    static Vec3d headAnchor(MinecraftClient c,LivingEntity mob,Vec3d pos,Vec3d right){
        double h=mob.getHeight();Vec3d top=pos.add(0,h+.12,0);
        // A single blocked point above a low ceiling does not make the visible creature disappear.
        Vec3d[] points={pos.add(0,Math.max(.15,h-.12),0),pos.add(0,h*.70,0),pos.add(0,h*.4,0),
                pos.add(right.multiply(mob.getWidth()*.35)).add(0,h*.85,0),pos.subtract(right.multiply(mob.getWidth()*.35)).add(0,h*.85,0)};
        Vec3d visible=null;for(Vec3d point:points)if(clearRay(c,point)){visible=point;break;}
        if(visible==null)return null; // Fully behind a wall: no marker, no fallback and no input.
        return clearRay(c,top)?top:visible;
    }
    static boolean panelClear(MinecraftClient c,Vec3d anchor,Vec3d right,Vec3d up,float ax,float ay,
            float x,float y,float scale,double sx,double sy){
        if(Math.abs(sx)<.001||Math.abs(sy)<.001)return false;
        for(int[] q:new int[][]{{8,106},{34,61},{97,29},{190,20},{282,29},{346,61},{371,106},{346,150},{282,180},{190,191},{97,180},{34,150}}){
            Vec3d point=anchor.add(right.multiply((x+q[0]*scale-ax)/sx)).add(up.multiply((ay-y-q[1]*scale*RequestBubbleGeometry.HEIGHT_RATIO)/sy));
            if(!clearRay(c,point))return false;
        }
        return true;
    }
    private static Placement placement(MinecraftClient c,Vec3d anchor,Vec3d right,Vec3d up,Vec3d projected,float scale,int width,int height){
        float ax=(float)((projected.x+1)*width/2),ay=(float)((1-projected.y)*height/2);
        var pr=c.gameRenderer.project(anchor.add(right));var pu=c.gameRenderer.project(anchor.add(up));
        double sx=(pr.x-projected.x)*width/2,sy=(pu.y-projected.y)*height/2;
        float[][] candidates={{-32,-205},{-347,-205},{70,-160},{-450,-160},{190,-150},{-570,-150},{222,-150},{-602,-150},{254,-150},{-634,-150},{286,-150},{-666,-150},{318,-150},{-698,-150},{350,-150},{-730,-150},{382,-150},{-762,-150},{414,-150},{-794,-150},{446,-150},{-826,-150},{470,-150},{-850,-150},{70,-80},{-450,-80}};
        for(int i=0;i<candidates.length;i++){
            int selected=i;
            var requested=ClientRequestState.requestEntity();
            if(i<2&&requested!=null&&GiftHudClient.hasGift(requested.getUuid()))selected=1-i;
            float x=Math.clamp(ax+candidates[selected][0]*scale,4,width-380*scale-4);
            float y=Math.clamp(ay+candidates[selected][1]*scale*RequestBubbleGeometry.HEIGHT_RATIO,4,height-208*scale*RequestBubbleGeometry.HEIGHT_RATIO-4);
            if(GiftHudClient.overlaps(x,y,380*scale,208*scale*RequestBubbleGeometry.HEIGHT_RATIO))continue;
            if(!panelClear(c,anchor,right,up,ax,ay,x,y,scale,sx,sy))continue;
            float tx=(ax-x)/scale,ty=(ay-y)/(scale*RequestBubbleGeometry.HEIGHT_RATIO);boolean mirror=selected==1;
            boolean adaptive=Math.abs(tx-(mirror?347:32))>1||Math.abs(ty-205)>1;
            return new Placement(x,y,mirror,adaptive,tx,ty);
        }
        return null;
    }
    /** Same approved ellipse and a unioned tail, only when an obstacle/edge requires another direction. */
    private static void adaptiveFrame(DrawContext c,float tx,float ty){
        double[] tail=RequestBubbleGeometry.tail(tx,ty);
        int minX=Math.min(3,(int)Math.floor(tx)-2),maxX=Math.max(376,(int)Math.ceil(tx)+2);
        int minY=Math.min(15,(int)Math.floor(ty)-2),maxY=Math.max(196,(int)Math.ceil(ty)+2);
        for(int y=minY;y<=maxY;y++){
            int start=minX,previous=0;
            for(int x=minX;x<=maxX+1;x++){
                int color=0;
                if(x<=maxX&&RequestBubbleGeometry.inside(x+.5,y+.5,tail)){
                    boolean edge=!RequestBubbleGeometry.inside(x-1.5,y+.5,tail)||!RequestBubbleGeometry.inside(x+2.5,y+.5,tail)
                            ||!RequestBubbleGeometry.inside(x+.5,y-1.5,tail)||!RequestBubbleGeometry.inside(x+.5,y+2.5,tail);
                    color=edge?0xFFAD5A83:0xE6211624;
                }
                if(color!=previous){if(previous!=0)c.fill(start,y,x,y+1,previous);start=x;previous=color;}
            }
        }
    }
    private static void render(DrawContext c,RenderTickCounter tick){
        hit=null;var client=MinecraftClient.getInstance();
        if(client.player==null||client.world==null||client.currentScreen!=null||client.options.hudHidden
                ||!NonCreatureAffinity.getConfig().hudEnabled||!ClientRequestState.isActive())return;
        var mob=ClientRequestState.requestEntity();if(mob==null)return;
        int width=client.getWindow().getScaledWidth(),height=client.getWindow().getScaledHeight();
        var camera=client.gameRenderer.getCamera();
        Vec3d right=new Vec3d(camera.getRotation().transform(new org.joml.Vector3f(1,0,0)));
        Vec3d up=new Vec3d(camera.getRotation().transform(new org.joml.Vector3f(0,1,0)));
        Vec3d anchor=headAnchor(client,mob,mob.getLerpedPos(tick.getTickProgress(false)),right);if(anchor==null)return;
        if(anchor.subtract(camera.getCameraPos()).dotProduct(new Vec3d(camera.getHorizontalPlane()))<=0)return;
        Vec3d projected=client.gameRenderer.project(anchor);
        if(!Double.isFinite(projected.x)||!Double.isFinite(projected.y)||projected.z < -1||projected.z>1||Math.abs(projected.x)>1||Math.abs(projected.y)>1)return;
        float pixels=RequestBubbleGeometry.physicalWidth(client.getWindow().getFramebufferWidth());
        float guiPixels=(float)client.getWindow().getFramebufferWidth()/width;
        float scale=Math.min(pixels/(380*guiPixels),Math.min((width-8)/380f,(height-8)/(208f*RequestBubbleGeometry.HEIGHT_RATIO)));
        var p=placement(client,anchor,right,up,projected,scale,width,height);if(p==null)return;
        double localX=(width/2.0-p.x())/scale,localY=(height/2.0-p.y())/(scale*RequestBubbleGeometry.HEIGHT_RATIO);
        int choice=RequestBubbleGeometry.button(localX,localY);
        boolean aimed=RequestBubbleGeometry.inside(localX,localY,RequestBubbleGeometry.tail(p.tipX(),p.tipY()));
        c.getMatrices().pushMatrix();c.getMatrices().translate(p.x(),p.y());c.getMatrices().scale(scale,scale*RequestBubbleGeometry.HEIGHT_RATIO);
        if(p.adaptive())adaptiveFrame(c,p.tipX(),p.tipY());
        if(!p.adaptive()){
            if(p.mirror())c.drawTexture(RenderPipelines.GUI_TEXTURED,Identifier.of(NonCreatureAffinity.MOD_ID,
                    "textures/gui/gift/request-tail-right.png"),0,0,0,0,380,208,380,208);
            else image(c,"frame/oval-base",0,0,380,208,380,208);
        }
        if(ClientRequestState.desireStage()>0)image(c,"desire/stage-"+ClientRequestState.desireStage(),0,0,380,208,380,208);
        float physicalScale=scale*guiPixels;
        float regular=14f/(client.textRenderer.fontHeight*physicalScale),title=16f/(client.textRenderer.fontHeight*physicalScale);
        centered(c,mob.getName(),190,43,228,title,0xFFFFE9F4);
        centered(c,text("affinity",ClientRequestState.affinity()),190,61,270,regular,0xFFF6D6E6);
        var energy=ClientRequestState.repression();String percentage=energy.known()?(int)Math.round(energy.ratio()*100)+"%":"—";
        centered(c,text("repression",percentage),190,86,270,regular,0xFFCCB6C5);
        c.fill(79,108,302,116,0xFFB6658B);c.fill(80,109,301,115,0xFF211A26);
        if(energy.known()){
            int fill=(int)Math.round(221*energy.ratio());if(fill>0)c.fill(80,109,80+fill,115,0xFFEA75A7);
            image(c,"progress/count-"+energy.hearts(),70,98,242,26,70,94,242,26,380,208);
        }
        boolean sent=ClientRequestState.submitted();
        for(int b=1;b<=2;b++){
            // Use the manifest coordinate system exactly. No extra invisible hit padding.
            c.getMatrices().pushMatrix();
            c.getMatrices().scale(380f/RequestBubbleGeometry.WIDTH,208f/RequestBubbleGeometry.HEIGHT);
            int x=RequestBubbleGeometry.buttonLeft(b), y=RequestBubbleGeometry.BUTTON_TOP;
            image(c,"buttons/"+(sent?"disabled":choice==b?"hover":"default"),x,y,
                    RequestBubbleGeometry.BUTTON_WIDTH,RequestBubbleGeometry.BUTTON_HEIGHT,104,30);
            float fontScale=29f/client.textRenderer.fontHeight;
            centered(c,text(b==1?"accept":"reject"),x+80,y+(62-29)/2,144,fontScale,0xFFFFE9F4);
            c.getMatrices().popMatrix();
        }
        c.getMatrices().popMatrix();
        if(choice>0){int cx=width/2,cy=height/2;c.fill(cx-2,cy,cx+3,cy+1,0xFFFFF2F8);c.fill(cx,cy-2,cx+1,cy+3,0xFFFFF2F8);}
        hit=new Hit(ClientRequestState.token(),choice,System.nanoTime(),width,height,p.x(),p.y(),scale,
                aimed,mob.getEntityPos(),camera.getCameraPos(),new org.joml.Quaternionf(camera.getRotation()));
    }
    private static boolean current(Hit h){
        var c=MinecraftClient.getInstance();return h!=null&&c.currentScreen==null&&!c.options.hudHidden&&NonCreatureAffinity.getConfig().hudEnabled
                &&ClientRequestState.isActive()&&h.token()==ClientRequestState.token()&&System.nanoTime()-h.frame()<250_000_000L
                &&c.getWindow().getScaledWidth()==h.width()&&c.getWindow().getScaledHeight()==h.height()
                &&c.gameRenderer.getCamera().getCameraPos().equals(h.camera())&&c.gameRenderer.getCamera().getRotation().equals(h.rotation());
    }
    static boolean overlaps(float x,float y,float w,float h){
        var p=hit;return current(p)&&x<p.x()+380*p.scale()&&x+w>p.x()
                &&y<p.y()+208*p.scale()*RequestBubbleGeometry.HEIGHT_RATIO&&y+h>p.y();
    }
    /** Hide only the overlapping presentation; never disable scanning or change its state. */
    public static boolean hidesAffinityHud(){return current(hit)&&hit.aimed();}
    public static boolean tryAnswer(){
        var c=MinecraftClient.getInstance();var h=hit;
        if(!current(h)||h.choice()==0||!c.mouse.isCursorLocked())return false;
        var mob=ClientRequestState.requestEntity();
        if(mob==null||!mob.getEntityPos().equals(h.entityPosition()))return false;
        Vec3d right=new Vec3d(c.gameRenderer.getCamera().getRotation().transform(new org.joml.Vector3f(1,0,0)));
                Vec3d anchor=headAnchor(c,mob,mob.getEntityPos(),right);if(anchor==null)return false;
        Vec3d up=new Vec3d(c.gameRenderer.getCamera().getRotation().transform(new org.joml.Vector3f(0,1,0)));
        var p=placement(c,anchor,right,up,c.gameRenderer.project(anchor),h.scale(),h.width(),h.height());
        if(p==null||Math.abs(p.x()-h.x())>1||Math.abs(p.y()-h.y())>1)return false;
        ClientRequestState.answer(h.token(),h.choice()==1);return true;
    }
}
