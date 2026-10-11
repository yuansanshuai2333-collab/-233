package com.nonid.affinity.client;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.net.*;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Read-only partner details plus an explicit server-owned personal preference. */
public final class CompanionInfoScreen extends Screen {
    private final UUID target;
    private final String dimension;
    private final CompanionInfoLease lease;
    private CompanionInfoData data;
    private String status;
    private int ticks, left, top, panelWidth, panelHeight, bodyTop, bodyBottom, scroll, contentHeight;
    private boolean expanded = true, modelFailed, closed;
    private String knownStage;
    private Button history, scan, direct;
    private long optionSequence;
    private int optionAge;
    private Boolean directEnabled;
    private boolean optionPending; private Runnable optionAction; private String optionStatus="loading";
    private ScrollBar scrollbar;
    private boolean embedded, historical;
    private boolean draggingModel,modelHit;
    private float modelYaw;
    private int modelLeft,modelTop,modelRight,modelBottom;
    private int viewportX,viewportY,viewportW,viewportH;
    private final List<Hint> hints = new ArrayList<>();
    private record Hint(int x,int y,int w,int h,Text text) {}
    public CompanionInfoScreen(UUID target, String dimension) {
        super(t("title")); this.target=target; this.dimension=dimension;
        lease=new CompanionInfoLease(target,dimension); status=target==null?"unselected":"loading";
    }
    CompanionInfoScreen(CompanionRecord record) {
        this(record.info().query().target(),record.info().query().dimension());embedded=true;supply(record);
    }
    void supply(CompanionRecord record) {data=record.info();knownStage=data.stage();historical=!record.availability().equals("live");if(historical){draggingModel=false;modelHit=false;}status=historical?record.availability():"ready";}
    void place(int x,int y,int w,int h,int screenW,int screenH) {if(client!=null&&viewportX==x&&viewportY==y&&viewportW==w&&viewportH==h&&width==screenW&&height==screenH){layout();return;}viewportX=x;viewportY=y;viewportW=w;viewportH=h;init(screenW,screenH);}
    static Text t(String key,Object... args) { return Text.translatable("companion_info.noncreatureaffinity."+key,args); }
    @Override public boolean shouldPause() { return false; }
    @Override protected void init() {
        panelWidth=Math.min(640,Math.max(240,width-16)); panelHeight=Math.max(180,height-16);
        left=(width-panelWidth)/2; top=(height-panelHeight)/2; bodyTop=top+32; bodyBottom=top+panelHeight-34;
        if(embedded){panelWidth=viewportW;panelHeight=viewportH;left=viewportX+(viewportW-panelWidth)/2;top=viewportY;bodyTop=top+32;bodyBottom=top+panelHeight-8;}
        if(!embedded){
        addDrawableChild(new Button(left+panelWidth-28,top+7,20,20,t("close"),"close",this::close));
        scan=addDrawableChild(new Button(left+45,top+panelHeight-27,96,22,Text.empty(),"scan",()->{
            if(client.player!=null) AffinityDetectorState.toggle(client.player.getUuid());
        }));
        }
        if(embedded) direct=addDrawableChild(new Button(0,0,96,22,t("direct_loading"),"direct_interaction",()->{
            if(directEnabled!=null&&!optionPending&&optionAction!=null)optionAction.run();
        }));
        history=addDrawableChild(new Button(0,0,192,18,t("history"),"history",()->{expanded=!expanded; layout();}));
        scrollbar=addDrawableChild(new ScrollBar());
        layout();
        if(!embedded && ticks==0) request();

    }
    void permission(Boolean enabled,boolean pending,String status,Runnable action){
        directEnabled=enabled;optionPending=pending;optionStatus=status;optionAction=action;if(textRenderer!=null)layout();
    }
    public void receiveOption(DirectInteractionOption reply) {} // Standalone legacy detail cannot change permissions.
    private boolean narrow() { return panelWidth<330; }
    private int infoX() { return left+(narrow()?12:124); }
    private int infoW() { return panelWidth-(narrow()?36:148); }
    private int infoOffset() { return 0; }
    private int modelBlock(){return narrow()?174:0;}
    private int optionTop() { return bodyTop+modelBlock()+278-scroll; }
    private int optionLabelHeight() { return (textRenderer.wrapLines(t("direct_target_title"),Math.max(1,infoW())).size()+textRenderer.wrapLines(t("direct_object",data==null?t("unknown"):entityName()),Math.max(1,infoW())).size())*11; }
    private int permissionHeight(){return embedded?optionLabelHeight()+36:0;}
    private void layout() {
        contentHeight=modelBlock()+302+(expanded?recordsHeight():0)+permissionHeight();
        scroll=Math.clamp(scroll,0,maxScroll());
        if(history!=null) {
            history.setX(infoX()); history.setY(bodyTop+modelBlock()+278+permissionHeight()-scroll); history.setWidth(Math.min(192,infoW()));
            history.visible=history.getY()>=bodyTop && history.getY()+18<=bodyBottom;
            history.setMessage(t(expanded?"history_open":"history_closed"));
        }
        if(scrollbar!=null) { scrollbar.setX(left+panelWidth-18); scrollbar.setY(bodyTop); scrollbar.setHeight(Math.max(24,bodyBottom-bodyTop)); scrollbar.active=maxScroll()>0; }
        if(direct!=null) {
            direct.setX(infoX());
            direct.setY(optionTop()+optionLabelHeight()+6);
            direct.active=directEnabled!=null&&!optionPending&&optionAction!=null;direct.setMessage(t(optionPending?"direct_loading":directEnabled!=null?(directEnabled?"direct_on":"direct_off"):"direct_"+optionStatus));
            direct.visible=direct.getY()>=bodyTop && direct.getY()+22<=bodyBottom;
        }
        if(getFocused() instanceof ClickableWidget widget && !widget.visible) setFocused(null);
    }
    private int maxScroll() { return Math.max(0,contentHeight-(bodyBottom-bodyTop)); }
    private void request() {
        if(target==null || closed || client==null || client.world==null || client.player==null) return;
        if(!dimension.equals(client.world.getRegistryKey().getValue().toString())) { invalidate("invalid"); return; }
        if(!ClientPlayNetworking.canSend(CompanionInfoQuery.ID)) { invalidate("unsupported"); return; }
        ClientPlayNetworking.send(new CompanionInfoQuery(lease.page(),lease.issue(),target,dimension));
    }
    @Override public void tick() {
        if(embedded)return;
        if(client.world==null || client.player==null) { close(); return; }
        if(!dimension.equals(client.world.getRegistryKey().getValue().toString())) { invalidate("invalid"); return; }
        lease.tick(); ticks++;


        if(target!=null && lease.expired() && !status.equals("unsupported")) invalidate("timeout");
        if(ticks%20==0) request();
    }
    private void invalidate(String reason) { data=null; status=reason; }
    public void receive(CompanionInfoData incoming) {
        if(closed || client.world==null || !dimension.equals(client.world.getRegistryKey().getValue().toString())) return;
        var q=incoming.query();
        if(!lease.accept(q.page(),q.target(),q.dimension(),q.sequence())) return;
        status=incoming.status(); data=incoming.hasEntity()?incoming:null;if(data!=null)knownStage=data.stage();
    }
    @Override public void removed() { draggingModel=false;modelHit=false;closed=true; lease.close(); data=null; }
    @Override public void close() { removed(); if(client!=null) client.setScreen(null); }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(draggingModel)return true;
        if(vertical!=0) { scroll=Math.clamp(scroll-(int)(vertical*24),0,maxScroll());layout();return true; } return false;
    }
    boolean isDraggingModel(){return draggingModel;}
    @Override public boolean mouseClicked(Click click,boolean doubled){
        if(draggingModel)return true;
        if(click.button()==GLFW.GLFW_MOUSE_BUTTON_LEFT&&modelHit&&click.x()>=modelLeft&&click.x()<modelRight&&click.y()>=modelTop&&click.y()<modelBottom){draggingModel=true;return true;}
        return super.mouseClicked(click,doubled);
    }
    @Override public boolean mouseDragged(Click click,double dx,double dy){
        if(draggingModel){modelYaw=net.minecraft.util.math.MathHelper.wrapDegrees(modelYaw+(float)dx*1.5f);return true;}
        return super.mouseDragged(click,dx,dy);
    }
    @Override public boolean mouseReleased(Click click){if(draggingModel){draggingModel=false;return true;}return super.mouseReleased(click);}
    @Override public boolean keyPressed(KeyInput key) {
        int k=key.key();
        if(k==GLFW.GLFW_KEY_PAGE_DOWN || k==GLFW.GLFW_KEY_PAGE_UP || k==GLFW.GLFW_KEY_HOME || k==GLFW.GLFW_KEY_END) {
            scroll=k==GLFW.GLFW_KEY_HOME?0:k==GLFW.GLFW_KEY_END?maxScroll():Math.clamp(scroll+(k==GLFW.GLFW_KEY_PAGE_DOWN?1:-1)*(bodyBottom-bodyTop),0,maxScroll());
            layout();return true;
        }
        return super.keyPressed(key);
    }
    @Override public void renderBackground(DrawContext c,int x,int y,float delta) {}
    @Override public void render(DrawContext c,int mx,int my,float delta) {
        hints.clear();modelHit=false;
        CompanionUi.frame(c,left,top,panelWidth,panelHeight,!embedded);
        CompanionDecorations.detailFrame(c,artStage(),left,top,panelWidth,panelHeight);
        line(c,(embedded||narrow())&&data!=null?title.copy().append(" · ").append(entityName()):title,left+12,top+12,panelWidth-48,CompanionUi.TEXT,false);
        c.enableScissor(left+8,bodyTop,left+panelWidth-20,bodyBottom);
        try {
        int modelX=left+12+(narrow()?Math.max(0,(infoW()-104)/2):0), y=bodyTop-scroll;
        int modelY=narrow()?y+90:bodyTop;
        model(c,modelX,modelY);
        name(c,data==null?state(CompanionInfoPresentation.TextField.STATUS,status):entityName(),narrow()?infoX():modelX,modelY+127,narrow()?infoW():104);
        if(data!=null) line(c,data.family().isEmpty()?entityType():entityType().copy().append(" · ").append(family(data.family())),narrow()?infoX():modelX,modelY+151,narrow()?infoW():104,0xFFBFA6B4,true);
        int x=infoX(), w=infoW();y+=infoOffset();
        if(artStage()!=null)CompanionSkin.image(c,"specialty/hearts/"+artStage(),x,y-3,24,24,128,128);
        line(c,t("stage",data==null?t("unknown"):Text.translatable("stage.noncreatureaffinity."+data.stage())),x+28,y,w-28,0xFFFF8BC8,true);
        line(c,state(CompanionInfoPresentation.TextField.STATUS,status),x+28,y+14,w-28,0xFFBFA6B4,true);
        bar(c,x,y+30,w,true);
        bar(c,x,y+59,w,false);
        y+=8+modelBlock(); // Reserve the dedicated affinity overlay envelope below the bar.
        panel(c,"ownership/panel",x,y+90,w,46,192,52);
        attribute(c,"armor","defense",data==null?t("unknown"):number(data.armor()),x+6,y+97,w-12,null);
        attribute(c,"attack","attack",data==null?t("unknown"):number(data.attack()),x+6,y+109,w-12,t("attribute_note"));
        attribute(c,"regeneration","regeneration",t("unknown"),x+6,y+121,w-12,t("unknown_note"));
        panel(c,"ownership/panel",x,y+140,w,76,192,52);
        field(c,"owner",state(CompanionInfoPresentation.TextField.OWNER,data==null?null:data.owner()),x+6,y+147,w-12,null);
        field(c,"control",state(CompanionInfoPresentation.TextField.CONTROL,data==null?null:data.control()),x+6,y+159,w-12,null);
        field(c,"mode",state(CompanionInfoPresentation.TextField.MODE,data==null?null:data.mode()),x+6,y+171,w-12,t("mode_note"));
        field(c,"dimension",data==null?t("unknown"):Text.literal(dimension),x+6,y+183,w-12,null);
        field(c,"distance",data==null||historical||data.distance()<0?t("unknown"):t("blocks",format(data.distance())),x+6,y+195,w-12,null);
        panel(c,"status/panel",x,y+220,w,44,192,30);
        String icon=data==null?(status.equals("loading")?"loading":"unavailable"):data.restriction().equals("interaction")?"interaction":data.restriction().equals("locked")||data.restriction().equals("other_owner")?"locked":"";
        if(!icon.isEmpty()) CompanionSkin.image(c,"status/"+icon,x+6,y+227,12,12,12,12);
        line(c,state(CompanionInfoPresentation.TextField.RESTRICTION,data==null?null:data.restriction()),x+22,y+229,w-28,0xFFECCDDD,true);
        line(c,t("readonly"),x+6,y+245,w-12,0xFFBFA6B4,true);
        if(expanded) {
            panel(c,"history/body",x,y+291+permissionHeight(),w,recordsHeight(),192,77);
            String[] keys={"completed","deep","agreed","rejected","tokens"};
            int[] values=data==null?new int[]{-1,-1,-1,-1,-1}:new int[]{data.completed(),data.deep(),data.agreed(),data.rejected(),data.tokens()};
            for(int i=0;i<5;i++) record(c,keys[i],values[i],x+6,y+298+permissionHeight()+i*recordStride(),w-12);
        }
        if(embedded){
            int labelY=optionTop();
            for(var label:List.of(t("direct_target_title"),t("direct_object",data==null?t("unknown"):entityName())))
                for(var line:textRenderer.wrapLines(label,Math.max(1,w))){c.drawText(textRenderer,line,x,labelY,0xFFFFE9F4,false);labelY+=11;}
            hint(x,optionTop(),w,optionLabelHeight(),t("direct_target_note"));
        }
        } finally { c.disableScissor(); }
        if(!embedded){
        CompanionSkin.image(c,"cat",left+8,top+panelHeight-32,32,32,1254,1254);
        scan.setMessage(t(client.player!=null && AffinityDetectorState.isEnabled(client.player.getUuid())?"scan_on":"scan_off"));
        }
        super.render(c,mx,my,delta);
        if(!embedded)line(c,t("foundation"),left+147,top+panelHeight-20,panelWidth-160,0xFFBFA6B4,false);
        if(direct!=null && direct.visible && direct.isHovered()) hints.add(0,new Hint(direct.getX(),direct.getY(),direct.getWidth(),direct.getHeight(),t("direct_target_note")));
        for(Hint hint:hints) if(mx>=hint.x && mx<hint.x+hint.w && my>=hint.y && my<hint.y+hint.h) { tooltip(c,hint.text,mx,my); break; }
    }
    private Text entityType() { return Text.translatable("entity."+data.type().replace(':','.')); }
    private Text entityName() { return data.name().isEmpty()?entityType():Text.literal(data.name()); }
    private static Text family(String key) { return Text.translatable("message.noncreatureaffinity.family."+key); }
    private void model(DrawContext c,int x,int y) {
        CompanionUi.border(c,x,y,104,124,0xFF80556F);

        LivingEntity entity=null;
        if(data!=null && !historical && !modelFailed && client.world!=null) for(var candidate:client.world.getEntities())
            if(candidate.getUuid().equals(target) && candidate instanceof LivingEntity living && living.isAlive()) { entity=living;break; }
        if(entity!=null && y+4<bodyBottom && y+106>bodyTop) {
            try {
                CompanionModelPreview.draw(c,entity,x+4,y+4,96,102,modelYaw,this::modelFailure);
                modelLeft=x+8;modelRight=x+96;modelTop=Math.max(bodyTop,y+8);modelBottom=Math.min(bodyBottom,y+102);modelHit=modelBottom>modelTop;
                line(c,t("model_rotate"),x+5,y+111,94,0xFFBFA6B4,false);
                return;
            } catch(RuntimeException failure) { modelFailure(failure); }
        }
        CompanionSkin.image(c,"model/placeholder",x+36,y+42,32,32,32,32);
        hint(x,y,104,124,t("model_note"));
    }
    private void modelFailure(RuntimeException failure) {
        draggingModel=false;modelHit=false;if(!modelFailed){modelFailed=true;NonCreatureAffinity.LOGGER.warn("Companion info model unavailable for {}",target,failure);}
    }
    private void name(DrawContext c,Text text,int x,int y,int w) {
        var lines=textRenderer.wrapLines(text,w);
        for(int i=0;i<Math.min(2,lines.size());i++) {
            if(i==1 && lines.size()>2) {
                StringBuilder value=new StringBuilder();lines.get(i).accept((index,style,cp)->{value.appendCodePoint(cp);return true;});
                c.drawText(textRenderer,textRenderer.trimToWidth(value.toString(),w-textRenderer.getWidth("…"))+"…",x,y+11,0xFFFFFFFF,false);
            }
            else c.drawText(textRenderer,lines.get(i),x,y+i*11,0xFFFFFFFF,false);
        }
        hint(x,y,w,22,text);
    }
    private String artStage(){String stage=data==null?(status.equals("loading")?knownStage:null):data.stage();return stage==null?null:stage.equals("new")?"first":stage;}
    private void panel(DrawContext c,String n,int x,int y,int w,int h,int tw,int th) {
        c.fill(x,y,x+w,y+h,0x4E211624);CompanionUi.border(c,x,y,w,h,0xFF80556F);

    }
    private void attribute(DrawContext c,String label,String icon,Text value,int x,int y,int w,Text note){
        CompanionSkin.image(c,"increment/attributes/"+icon+"-"+(artStage()==null?"first":artStage()),x,y-2,12,12,64,64);
        field(c,label,value,x+16,y,w-16,note);
    }
    private int recordStride(){return infoW()-12<120?38:26;}
    private int recordsHeight(){return recordStride()*5+12;}
    private void record(DrawContext c,String key,int value,int x,int y,int w){
        int hearts=key.equals("agreed")?com.nonid.affinity.service.CompanionCountStages.agreement(value):0;
        CompanionUi.border(c,x,y,w,recordStride()-4,0xFF80556F);
        if(hearts>0)CompanionDecorations.draw(c,"agreement",Integer.toString(hearts),x,y,w,recordStride()-4,.12f);
        int balloon=key.equals("tokens")?com.nonid.affinity.service.CompanionCountStages.gifts(value):0;
        int right=x+w-6;
        boolean stacked=recordStride()>26;
        if(balloon>0){int bh=balloon<=3?17:21;CompanionSkin.image(c,"counts/balloon-"+balloon,right-26,y,26,bh,balloon<=3?1536:1402,balloon<=3?1024:1122);if(!stacked)right-=30;}
        String number=value<0?Text.translatable("companion_info.noncreatureaffinity.unknown").getString():Integer.toString(value);int numberWidth=textRenderer.getWidth(number);
        c.drawText(textRenderer,number,right-numberWidth,y+(stacked?22:6),0xFFFFE9F4,false);
        line(c,t(key),x+6,y+6,Math.max(0,stacked?w-42:right-numberWidth-x-16),0xFFFFE9F4,false);
        hint(x,y,w,recordStride()-4,t(key).copy().append(": "+number+"\n").append(t("record_note."+key)));
    }
    private void field(DrawContext c,String key,Text value,int x,int y,int w,Text note) {
        Text text=t(key).copy().append(": ").append(value);
        line(c,text,x,y,w,0xFFFFE9F4,true);
        if(note!=null) hint(x,y,w,11,text.copy().append("\n").append(note));
    }
    private void line(DrawContext c,Text text,int x,int y,int w,int color,boolean clipped) {
        if(w<=0)return;
        String value=text.getString(); boolean cut=textRenderer.getWidth(value)>w;
        c.drawText(textRenderer,cut?textRenderer.trimToWidth(value,Math.max(1,w-textRenderer.getWidth("…")))+"…":value,x,y,color,false);
        if(cut) { if(clipped)hint(x,y,w,11,text);else hints.add(new Hint(x,y,w,11,text)); }
    }
    private void hint(int x,int y,int w,int h,Text text) {
        int start=Math.max(y,bodyTop),end=Math.min(y+h,bodyBottom);
        if(end>start) hints.add(0,new Hint(x,start,w,end-start,text));
    }
    private void bar(DrawContext c,int x,int y,int w,boolean health) {
        Text value=data==null?Text.translatable("companion_info.noncreatureaffinity.unknown"):health?number(data.health()).copy().append(" / ").append(number(data.maxHealth())):
                Text.literal(data.affinity()+" / "+data.maximum());
        field(c,health?"health":"affinity",value,x,y,w,health?null:t("affinity_note",data==null?"—":data.friendThreshold()));
        int bw=Math.min(184,w), bx=x,by=y+(health?12:16);
        // Fixed unmeasured track: crop width, do not invent a stretch grid.
        CompanionSkin.part(c,"bars/track",bx,by,bw,12,0,0,bw,12,184,12);
        double ratio=data==null?0:health?data.health()/Math.max(1.0,data.maxHealth()):(double)data.affinity()/Math.max(1,data.maximum());
        int fill=(int)Math.round(Math.clamp(ratio,0,1)*Math.max(0,bw-6));
        CompanionSkin.image(c,"bars/"+(health?"health-fill":"affinity-fill"),bx+3,by+3,fill,6,1,6);
        CompanionSkin.nine(c,"bars/border",bx,by,bw,12,184,12);
        if(!health&&artStage()!=null) {
            float scale=bw/184f;
            CompanionSkin.image(c,"specialty/affinity/"+artStage()+"-decoration",bx-Math.round(8*scale),by-4,
                    Math.round(200*scale),Math.round(22*scale),200,22);
        }
    }
    private static String format(double value) { return String.format(Locale.ROOT,"%.1f",value); }
    private static Text state(CompanionInfoPresentation.TextField field,String value) {
        return Text.translatable(CompanionInfoPresentation.key(field,value));
    }
    private static Text number(double value) {
        String key=CompanionInfoPresentation.decimal(value);
        return key.startsWith("companion_info.noncreatureaffinity.")?Text.translatable(key):Text.literal(key);
    }
    private void tooltip(DrawContext c,Text text,int mx,int my) {
        int tw=Math.min(220,Math.max(40,width-24));var lines=textRenderer.wrapLines(text,tw-12);
        int th=lines.size()*10+12,tx=Math.clamp(mx+8,4,Math.max(4,width-tw-4)),ty=Math.clamp(my+12,4,Math.max(4,height-th-4));
        CompanionSkin.nine(c,"tooltip/panel",tx,ty,tw,th,144,46);
        int yy=ty+6; for(var line:lines){c.drawText(textRenderer,line,tx+6,yy,0xFFFFE9F4,false);yy+=10;}
    }
    private final class Button extends ClickableWidget {
        private final String kind;private final Runnable action;private long pressed;
        Button(int x,int y,int w,int h,Text text,String kind,Runnable action){super(x,y,w,h,text);this.kind=kind;this.action=action;}
        @Override public void onClick(Click click,boolean doubled){pressed=System.currentTimeMillis()+120;action.run();}
        @Override public boolean keyPressed(KeyInput key){if(active && visible && (key.key()==GLFW.GLFW_KEY_ENTER||key.key()==GLFW.GLFW_KEY_SPACE)){pressed=System.currentTimeMillis()+120;action.run();return true;}return super.keyPressed(key);}
        @Override protected void appendClickableNarrations(NarrationMessageBuilder b){appendDefaultNarrations(b);}
        @Override protected void renderWidget(DrawContext c,int mx,int my,float delta){
            String state=!active?"disabled":System.currentTimeMillis()<pressed?"pressed":isHovered()?"hover":isFocused()?"focus":"default";
            String component=kind.equals("history")?"records":kind;
            String stage=artStage()==null?"first":artStage();
            // Approved fixed-size composition; the four-pixel gutter is visual only.
            if(!kind.equals("history")||width==192)
                CompanionSkin.image(c,"specialty/buttons/"+component+"/"+stage+"-"+state,getX()-4,getY()-4,width+8,height+8,width+8,height+8);
            else CompanionSkin.nine(c,"history/header-"+state,getX(),getY(),width,height,192,18);
            if(!kind.equals("close"))line(c,getMessage(),getX()+18,getY()+(height-8)/2,width-34,0xFFFFE9F4,false);
            if(isHovered())hints.add(new Hint(getX(),getY(),width,height,getMessage()));
        }
    }
    private final class ScrollBar extends ClickableWidget {
        private long pressed;
        ScrollBar(){super(0,0,8,24,t("scroll"));}
        private void move(double y){scroll=(int)Math.round(Math.clamp((y-getY()-12)/Math.max(1,height-24),0,1)*maxScroll());layout();}
        @Override public void onClick(Click click,boolean doubled){pressed=System.currentTimeMillis()+120;move(click.y());}
        @Override protected void onDrag(Click click,double dx,double dy){pressed=System.currentTimeMillis()+120;move(click.y());}
        @Override public boolean keyPressed(KeyInput key){if(key.key()==GLFW.GLFW_KEY_DOWN||key.key()==GLFW.GLFW_KEY_UP){scroll=Math.clamp(scroll+(key.key()==GLFW.GLFW_KEY_DOWN?12:-12),0,maxScroll());layout();return true;}return super.keyPressed(key);}
        @Override protected void appendClickableNarrations(NarrationMessageBuilder b){appendDefaultNarrations(b);}
        @Override protected void renderWidget(DrawContext c,int mx,int my,float delta){
            for(int yy=0;yy<height;yy+=96)CompanionSkin.part(c,"scroll/track",getX(),getY()+yy,8,Math.min(96,height-yy),0,0,8,Math.min(96,height-yy),8,96);
            int yy=getY()+(int)((height-24)*(maxScroll()==0?0:(double)scroll/maxScroll()));
            String state=!active?"disabled":System.currentTimeMillis()<pressed?"pressed":isHovered()?"hover":isFocused()?"focus":"default";
            CompanionSkin.image(c,"specialty/buttons/scroll/"+(artStage()==null?"first":artStage())+"-"+state,getX()-4,yy-4,16,32,16,32);
        }
    }
}






