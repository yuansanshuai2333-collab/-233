package com.nonid.affinity.client;

import com.nonid.affinity.item.AffinityDetectorItem;
import com.nonid.affinity.net.*;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** A paged private directory. Historical values are labelled; opening it is not a gameplay action. */
public final class CompanionDirectoryScreen extends Screen {
    private final List<CompanionRecord> allRows=new ArrayList<>();
    private String searchText=""; private net.minecraft.client.gui.widget.TextFieldWidget search;private CompanionButton familyTab,historyTab,deadTab;
    private UUID page=UUID.randomUUID(),session;private long sequence,pendingSequence,optionSequence;
    private int lastSent=-10;private boolean queued;
    private String dimension,status="loading",selected="",pendingSelected="";private boolean pending,closed,more,detailMode,optionPending;
    private int index,requestedIndex,age,ticks,optionAge,scroll,left,top,panelW,panelH,bodyTop,bodyBottom,listW;
    private Boolean directEnabled;private List<CompanionRecord> rows=List.of();private CompanionRecord selection;
    private CompanionInfoScreen detail;private CompanionButton scan,direct,previous,next,back,retry;private final List<Row> rowButtons=new ArrayList<>();
    public CompanionDirectoryScreen(){super(t("title"));}
    static Text t(String key,Object...args){return Text.translatable("companion_directory.noncreatureaffinity."+key,args);}
    @Override public boolean shouldPause(){return false;}
    private boolean wide(){return width>=480;}
    private String stage(){return "first";}
    @Override protected void init(){
        panelW=Math.min(640,Math.max(280,width-16));panelH=Math.max(180,height-16);left=(width-panelW)/2;top=(height-panelH)/2;
        bodyTop=top+62;bodyBottom=top+panelH-36;listW=wide()?168:panelW-24;rowButtons.clear();
        addDrawableChild(new CompanionButton(left+panelW-28,top+6,20,20,Text.literal("×"),"plain",this::stage,this::close));
        scan=addDrawableChild(new CompanionButton(left+12,top+panelH-27,96,22,Text.empty(),"plain",this::stage,()->AffinityDetectorState.toggle(client.player.getUuid())));
        previous=addDrawableChild(new CompanionButton(left+12,bodyBottom-20,44,18,t("previous"),"plain",this::stage,()->{index=Math.max(0,index-1);filterRows();}));
        next=addDrawableChild(new CompanionButton(left+12+listW-44,bodyBottom-20,44,18,t("next"),"plain",this::stage,()->{index++;filterRows();}));
        back=addDrawableChild(new CompanionButton(left+12,top+6,70,18,t("back"),"plain",this::stage,()->{detailMode=false;layoutRows();}));
        retry=addDrawableChild(new CompanionButton(left+panelW-90,top+panelH-27,78,22,t("refresh"),"plain",this::stage,this::refresh));
        for(var row:rows)rowButtons.add(addDrawableChild(new Row(row)));
        search=addDrawableChild(new net.minecraft.client.gui.widget.TextFieldWidget(textRenderer,left+12,top+34,listW,18,t("search")));
        search.setDrawsBackground(false);search.setEditableColor(CompanionUi.TEXT);search.setPlaceholder(t("search"));search.setText(searchText);search.setChangedListener(value->{searchText=value;filterRows();});
        deadTab=addDrawableChild(new CompanionButton(wide()?left+12+listW+8:left+panelW-158,wide()?top+34:top+6,70,18,t("deceased"),"plain",this::stage,()->client.setScreen(new CompanionRecordsScreen())));
        familyTab=addDrawableChild(new CompanionButton(left+panelW-158,top+34,70,18,t("family"),"plain",this::stage,()->client.setScreen(new CompanionRecordsScreen(selected,"family"))));
        historyTab=addDrawableChild(new CompanionButton(left+panelW-82,top+34,70,18,t("event_history"),"plain",this::stage,()->client.setScreen(new CompanionRecordsScreen(selected,"history"))));
        layoutRows();placeDetail();
        if(dimension==null){dimension=client.world.getRegistryKey().getValue().toString();request(0,"");}
    }
    private void refresh(){clearOption();page=UUID.randomUUID();session=null;sequence=0;pending=false;rows=List.of();selected="";selection=null;detail=null;detailMode=false;index=0;scroll=0;allRows.clear();status="loading";request(0,"");clearAndInit();}
    private void request(int wanted,String id){
        if(pending||closed||wanted<0)return;
        if(!ClientPlayNetworking.canSend(CompanionBrowse.Request.ID)){status="unsupported";return;}
        pending=true;age=0;pendingSequence=++sequence;requestedIndex=wanted;pendingSelected=id;
        if(id.isEmpty()&&wanted==0){status="loading";rows=List.of();clearAndInit();}
        queued=true;flushRequest();layoutRows();
    }
    private void flushRequest(){if(queued&&ticks-lastSent>=10){queued=false;lastSent=ticks;age=0;ClientPlayNetworking.send(new CompanionBrowse.Request(page,pendingSequence,requestedIndex,pendingSelected));}}
    public void receive(CompanionBrowse.Reply reply){
        if(closed||!pending||reply.sequence()!=pendingSequence||!page.equals(reply.page())||age>=60||!eligible())return;
        pending=false;
        if(session!=null&&!session.equals(reply.session())){status="expired";rows=List.of();selection=null;detail=null;clearAndInit();return;}
        if(session==null)session=reply.session();
        if(!pendingSelected.isEmpty()){
            if(reply.status().equals("detail")&&reply.rows().size()==1&&selected.equals(reply.rows().getFirst().id())){
                var priorTarget=selection==null?null:selection.info().query().target();selection=reply.rows().getFirst();status="ready";
                if(detail==null||!java.util.Objects.equals(priorTarget,selection.info().query().target()))detail=new CompanionInfoScreen(selection);else detail.supply(selection);
                placeDetail();if(directEnabled==null&&!optionPending)requestOption(false,false);syncOption();
            }else{selection=null;detail=null;status=reply.status();}
        }else{
            allRows.addAll(reply.rows());status=reply.status();
            if(reply.more()&&reply.status().equals("ready")){request(requestedIndex+1,"");return;}
            index=0;filterRows();
        }
        layoutRows();
    }
    private UUID detailTarget(){return selection==null?new UUID(0,0):selection.info().query().target();}
    private boolean eligible(){return client.player!=null&&client.world!=null&&client.player.isAlive()&&AffinityDetectorItem.isHeldBy(client.player)&&dimension.equals(client.world.getRegistryKey().getValue().toString());}
    private String optionStatus="loading";
    private void clearOption(){directEnabled=null;optionPending=false;optionStatus="loading";optionSequence++;syncOption();}
    private void syncOption(){if(detail!=null)detail.permission(directEnabled,optionPending,optionStatus,()->{if(directEnabled!=null&&!optionPending)requestOption(true,!directEnabled);});}
    private void requestOption(boolean write,boolean value){
        if(closed||selection==null||session==null||selected.isEmpty())return;
        if(!ClientPlayNetworking.canSend(DirectInteractionOption.ID)){optionStatus="unsupported";syncOption();return;}
        optionPending=true;optionAge=0;syncOption();
        ClientPlayNetworking.send(new DirectInteractionOption(page,++optionSequence,session,selected,write,value,"request"));
    }
    public void receiveOption(DirectInteractionOption reply){
        if(closed||!eligible()||!optionPending||!page.equals(reply.page())||!Objects.equals(session,reply.session())||!selected.equals(reply.target())||reply.sequence()!=optionSequence||optionAge>=60)return;
        directEnabled=reply.status().equals("ready")?reply.enabled():null;optionPending=false;
        optionStatus=reply.status().equals("ready")?"loading":reply.status().equals("busy")?"loading":"unavailable";syncOption();
    }
    @Override public void tick(){
        if(!eligible()){close();return;}ticks++;flushRequest();
        if(pending&&!queued&&++age>=60){pending=false;status="timeout";selection=null;detail=null;rows=List.of();clearAndInit();}
        if(optionPending&&++optionAge>=60){optionPending=false;directEnabled=null;optionStatus="timeout";syncOption();}
        if(!pending&&!selected.isEmpty()&&ticks%20==0&&status.equals("ready"))request(index,selected);
        if(!optionPending&&directEnabled==null&&ticks%60==0)requestOption(false,false);
        layoutRows();
    }
    private void filterRows(){
        String needle=searchText.toLowerCase(Locale.ROOT);
        var found=allRows.stream().filter(r->needle.isBlank()||name(r).getString().toLowerCase(Locale.ROOT).contains(needle)||Text.translatable("entity."+r.info().type().replace(':','.')).getString().toLowerCase(Locale.ROOT).contains(needle)).toList();
        index=Math.clamp(index,0,Math.max(0,(found.size()-1)/CompanionBrowse.PAGE_SIZE));int start=index*CompanionBrowse.PAGE_SIZE;
        rows=found.subList(Math.min(start,found.size()),Math.min(found.size(),start+CompanionBrowse.PAGE_SIZE));more=start+rows.size()<found.size();scroll=0;
        for(var b:rowButtons)remove(b);rowButtons.clear();for(var row:rows)rowButtons.add(addDrawableChild(new Row(row)));layoutRows();
    }
    private int listBottom(){return bodyBottom-26;}
    private void layoutRows(){
        boolean showing=wide()||!detailMode;scroll=Math.clamp(scroll,0,Math.max(0,rows.size()*52-(listBottom()-bodyTop)));
        for(int i=0;i<rowButtons.size();i++){var b=rowButtons.get(i);b.setX(left+12);b.setY(bodyTop+i*52-scroll);b.setWidth(listW);b.visible=showing&&b.getY()>=bodyTop&&b.getY()+50<=listBottom();b.active=!pending;}
        if(previous!=null){previous.visible=next.visible=showing;previous.active=!pending&&index>0;next.active=!pending&&more;back.visible=!wide()&&detailMode;retry.active=!pending;}
        if(search!=null)search.setVisible(wide()||!detailMode);
        if(deadTab!=null)deadTab.visible=wide()||!detailMode;
        if(familyTab!=null){familyTab.active=historyTab.active=!selected.isEmpty()&&selection!=null;familyTab.visible=historyTab.visible=wide()||detailMode;}
        if(scan!=null)scan.setMessage(CompanionInfoScreen.t(AffinityDetectorState.isEnabled(client.player.getUuid())?"scan_on":"scan_off"));
    }
    private void choose(CompanionRecord row){if(detail!=null)detail.removed();clearOption();selected=row.id();selection=null;detail=null;detailMode=true;request(index,selected);layoutRows();}
    private int detailX(){return wide()?left+188:left+8;}
    private int detailWidth(){return wide()?panelW-196:panelW-16;}
    private void placeDetail(){if(detail!=null)detail.place(detailX(),bodyTop+24,detailWidth(),Math.max(100,bodyBottom-bodyTop-24),width,height);syncOption();}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(detail!=null&&detail.isDraggingModel())return true;
        if(detail!=null&&(wide()?x>=detailX():detailMode))return detail.mouseScrolled(x,y,horizontal,vertical);
        scroll-=Math.round((float)vertical*36);layoutRows();return true;
    }
    @Override public boolean mouseClicked(Click click,boolean doubled){if(detail!=null&&detail.isDraggingModel())return true;if(detail!=null&&(wide()||detailMode)&&click.x()>=detailX()&&click.y()>=bodyTop+24&&click.y()<bodyBottom&&detail.mouseClicked(click,doubled))return true;return super.mouseClicked(click,doubled);}
    @Override public boolean mouseReleased(Click click){if(detail!=null)detail.mouseReleased(click);return super.mouseReleased(click);}
    @Override public boolean mouseDragged(Click click,double dx,double dy){if(detail!=null&&(wide()||detailMode)&&detail.mouseDragged(click,dx,dy))return true;return super.mouseDragged(click,dx,dy);}
    @Override public boolean keyPressed(KeyInput key){
        if(key.key()==GLFW.GLFW_KEY_ESCAPE){close();return true;}
        if(detail!=null&&detail.isDraggingModel())return true;
        if(detail!=null&&(wide()||detailMode)&&(key.key()==GLFW.GLFW_KEY_TAB||detail.getFocused()!=null)&&(key.key()==GLFW.GLFW_KEY_TAB||key.key()==GLFW.GLFW_KEY_ENTER||key.key()==GLFW.GLFW_KEY_SPACE)&&detail.keyPressed(key))return true;
        if(detail!=null&&detailMode&&(key.key()==GLFW.GLFW_KEY_PAGE_DOWN||key.key()==GLFW.GLFW_KEY_PAGE_UP||key.key()==GLFW.GLFW_KEY_HOME||key.key()==GLFW.GLFW_KEY_END))return detail.keyPressed(key);
        if(key.key()==GLFW.GLFW_KEY_PAGE_DOWN||key.key()==GLFW.GLFW_KEY_PAGE_UP){scroll+=(key.key()==GLFW.GLFW_KEY_PAGE_DOWN?1:-1)*(listBottom()-bodyTop);layoutRows();return true;}
        return super.keyPressed(key);
    }
    @Override public void close(){removed();client.setScreen(null);}
    @Override public void removed(){closed=true;pending=false;rows=List.of();selection=null;if(detail!=null)detail.removed();detail=null;}
    @Override public void renderBackground(DrawContext c,int mx,int my,float delta){}
    private void line(DrawContext c,Text text,int x,int y,int w,int color){c.drawText(textRenderer,textRenderer.trimToWidth(text.getString(),Math.max(1,w)),x,y,color,false);}
    private Text name(CompanionRecord r){return r.info().name().isEmpty()?Text.translatable("entity."+r.info().type().replace(':','.')):Text.literal(r.info().name());}
    private Text time(CompanionRecord r){return r.observedAt()<=0?t("time_unknown"):Text.literal(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(r.observedAt())));}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        CompanionUi.frame(c,left,top,panelW,panelH,true);
        if(wide()||!detailMode)line(c,title,left+12,top+12,panelW-140,0xFFFFE9F4);
        if(wide()||!detailMode){
            if(!status.equals("ready"))line(c,t("status."+status),left+16,bodyTop+10,listW-8,0xFFBFA6B4);
            else if(rows.isEmpty()){line(c,t("empty"),left+16,bodyTop+10,listW-8,0xFFFFE9F4);int y=bodyTop+27;for(var line:textRenderer.wrapLines(t("legacy"),listW-8)){c.drawText(textRenderer,line,left+16,y,0xFFBFA6B4,false);y+=11;}}
            line(c,t("page",index+1),left+60,bodyBottom-15,listW-96,0xFFBFA6B4);
        }
        if(wide()||detailMode){
            if(selection!=null&&detail!=null){
                line(c,t(selection.availability().equals("live")?"live":selection.availability().equals("dead")?"dead":"historical"),detailX()+5,bodyTop,detailWidth()-10,0xFFFFE9F4);
                if(!selection.availability().equals("live"))line(c,t("updated",time(selection)),detailX()+5,bodyTop+12,detailWidth()-10,0xFFBFA6B4);
                detail.render(c,mx,my,delta);
            }else line(c,t(selected.isEmpty()?"select":"status."+(pending?"loading":status)),detailX()+8,bodyTop+15,detailWidth()-16,0xFFBFA6B4);
        }
        if(search.visible){c.fill(search.getX(),search.getY(),search.getX()+search.getWidth(),search.getY()+search.getHeight(),0x4E211624);CompanionUi.border(c,search.getX(),search.getY(),search.getWidth(),search.getHeight(),0xFF80556F);}
        super.render(c,mx,my,delta);
        for(var row:rowButtons)if(row.visible&&row.isHovered()&&textRenderer.getWidth(name(row.row))>listW-38){CompanionUi.tooltip(c,name(row.row),mx,my,width,height);break;}

    }
    private final class Row extends ClickableWidget {
        final CompanionRecord row;
        Row(CompanionRecord row){super(0,0,listW,50,name(row));this.row=row;}
        @Override public void onClick(Click click,boolean doubled){choose(row);}
        @Override public boolean keyPressed(KeyInput key){if(active&&(key.key()==GLFW.GLFW_KEY_ENTER||key.key()==GLFW.GLFW_KEY_SPACE)){choose(row);return true;}return super.keyPressed(key);}
        @Override protected void appendClickableNarrations(NarrationMessageBuilder b){appendDefaultNarrations(b);}
        @Override protected void renderWidget(DrawContext c,int mx,int my,float delta){
            String state=!active?"disabled":isHovered()?"hover":isFocused()||selected.equals(row.id())?"focus":"default";
            CompanionUi.border(c,getX(),getY(),width,height,isHovered()||isFocused()?0xFFEFA3C7:0xFF80556F);

            CompanionSkin.image(c,"model/placeholder",getX()+6,getY()+8,24,24,32,32);
            CompanionUi.text(c,name(row),getX()+36,getY()+7,width-42,CompanionUi.TEXT);
            line(c,Text.translatable("stage.noncreatureaffinity."+row.info().stage()),getX()+36,getY()+20,width-42,0xFFFF8BC8);
            line(c,t(row.availability().equals("live")?"live":"historical"),getX()+6,getY()+36,width-12,0xFFBFA6B4);
        }
    }
}



