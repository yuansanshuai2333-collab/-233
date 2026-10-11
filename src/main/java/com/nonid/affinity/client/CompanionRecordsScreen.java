package com.nonid.affinity.client;

import com.nonid.affinity.NonCreatureAffinity;
import com.nonid.affinity.item.AffinityDetectorItem;
import com.nonid.affinity.net.*;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import net.minecraft.nbt.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Viewer-private server facts, explicit confirmation and paged history. No invented family or events. */
public final class CompanionRecordsScreen extends Screen {
    private static final UUID ZERO=new UUID(0,0);
    private record Row(CompanionRecord record,boolean bound,boolean wasFriend,NbtCompound death){}
    private record Job(String operation,String root,long cursor,UUID nonce,int revision){}
    private final String requestedRoot,initialTab;
    private String dimension,root="",tab="dead",status="loading",indexStatus="loading";
    private final List<Row> rows=new ArrayList<>();
    private final List<NbtCompound> facts=new ArrayList<>(),links=new ArrayList<>();
    private final Set<String> folded=new HashSet<>(),failedModels=new HashSet<>();
    private final Deque<Job> jobs=new ArrayDeque<>();private Job pending;
    private Row selected;private UUID hideNonce;private boolean confirm,closed,more,indexMore;
    private int ticks,lastSent=-10,age,revision,left,top,pw,ph,bodyTop,bodyBottom,listW,scroll,indexScroll;
    private long next,pendingSequence;private double zoom=1,panX,panY;private boolean dragging;private double dragX,dragY;
    private CompanionButton nextButton,hideButton,scanButton,recenterButton,modalCancel,modalConfirm;
    private boolean confirming;
    private final Map<net.minecraft.client.gui.widget.ClickableWidget,Boolean> modalActive=new IdentityHashMap<>();
    private net.minecraft.client.gui.widget.TextFieldWidget search;
    private final List<Hit> hits=new ArrayList<>();
    private record Hit(int x,int y,int w,int h,Text text,Runnable action){}
    public CompanionRecordsScreen(){this("","dead");}
    public CompanionRecordsScreen(String root,String tab){super(t("title"));requestedRoot=root;initialTab=tab;}
    static Text t(String key,Object...args){return Text.translatable("companion_archive.noncreatureaffinity."+key,args);}
    @Override public boolean shouldPause(){return false;}
    @Override protected void init(){
        pw=Math.min(660,Math.max(240,width-16));ph=Math.max(160,height-16);left=(width-pw)/2;top=(height-ph)/2;
        bodyTop=top+(width>=480?60:86);bodyBottom=top+ph-34;listW=width>=480?150:0;
        addDrawableChild(new CompanionButton(left+pw-28,top+7,20,20,Text.literal("×"),"plain",()->"first",this::close));
        addDrawableChild(new CompanionButton(left+12,top+8,96,18,t("directory"),"plain",()->"first",()->client.setScreen(new CompanionDirectoryScreen())));
        String priorSearch=search==null?"":search.getText();
        search=addDrawableChild(new net.minecraft.client.gui.widget.TextFieldWidget(textRenderer,left+12,top+(listW==0?60:34),Math.max(100,listW==0?pw-24:listW),18,t("search")));
        search.setDrawsBackground(false);search.setEditableColor(CompanionUi.TEXT);search.setPlaceholder(t("search"));search.setText(priorSearch);search.setChangedListener(value->indexScroll=0);
        int tx=left+12+listW+(listW>0?12:0);
        for(String value:List.of("dead","history","family")){
            final String dest=value;addDrawableChild(new CompanionButton(tx,top+34,70,18,t(value),"plain",()->"first",()->switchTab(dest)));tx+=76;
        }
        if(listW==0)search.setVisible(tab.equals("dead"));
        nextButton=addDrawableChild(new CompanionButton(left+pw-90,top+ph-27,78,20,t("more"),"plain",()->"first",this::loadMore));
        hideButton=addDrawableChild(new CompanionButton(left+pw-142,bodyBottom-26,130,20,t("hide"),"plain",()->"first",this::prepareHide));
        scanButton=addDrawableChild(new CompanionButton(left+12,top+ph-27,96,20,CompanionInfoScreen.t("scan_on"),"plain",()->"first",()->AffinityDetectorState.toggle(client.player.getUuid())));
        recenterButton=addDrawableChild(new CompanionButton(left+120,top+ph-27,96,20,t("recenter"),"plain",()->"first",()->{zoom=1;panX=panY=0;}));
        int mw=Math.min(280,pw-24),mx=left+(pw-mw)/2,my=top+(ph-100)/2;
        modalCancel=addSelectableChild(new CompanionButton(mx+12,my+74,94,18,t("cancel"),"plain",()->"first",this::cancelHide));
        modalConfirm=addSelectableChild(new CompanionButton(mx+mw-106,my+74,94,18,t("confirm"),"plain",()->"first",this::confirmHide));
        modalCancel.visible=modalConfirm.visible=confirm;modalCancel.active=modalConfirm.active=false;
        if(confirm)modal(true);
        if(dimension==null){dimension=client.world.getRegistryKey().getValue().toString();CompanionArchiveClient.open(this::receive);enqueue("records","",0,ZERO);}
    }
    private boolean eligible(){return client.player!=null&&client.world!=null&&client.player.isAlive()&&!client.player.isSpectator()&&AffinityDetectorItem.isHeldBy(client.player)&&dimension.equals(client.world.getRegistryKey().getValue().toString());}
    private void enqueue(String op,String id,long cursor,UUID nonce){jobs.addLast(new Job(op,id,cursor,nonce,revision));}
    @Override public void tick(){
        if(!eligible()){close();return;}ticks++;
        if(pending!=null&&++age>=60){status="timeout";pending=null;jobs.clear();if(confirming){confirming=false;confirm=false;hideNonce=null;modal(false);}}
        if(pending==null&&!jobs.isEmpty()&&ticks-lastSent>=6){pending=jobs.removeFirst();age=0;lastSent=ticks;
            if((pendingSequence=CompanionArchiveClient.requestSequence(pending.operation(),pending.root(),pending.cursor(),64,pending.nonce()))==0){status="unsupported";pending=null;jobs.clear();}}
        nextButton.visible=more&&(tab.equals("history")||tab.equals("family"));nextButton.active=pending==null&&jobs.isEmpty()&&!confirm;
        hideButton.visible=tab.equals("dead")&&selected!=null&&selected.record().availability().equals("dead");hideButton.active=pending==null&&!confirm;
        scanButton.setMessage(CompanionInfoScreen.t(AffinityDetectorState.isEnabled(client.player.getUuid())?"scan_on":"scan_off"));
        recenterButton.visible=tab.equals("family")&&!facts.isEmpty();
        modalCancel.visible=modalConfirm.visible=confirm;modalCancel.active=confirm&&!confirming;modalConfirm.active=confirm&&!confirming&&hideNonce!=null;
        modalConfirm.setMessage(t(hideNonce==null?"loading":"confirm"));
    }
    private void receive(CompanionArchivePayload.Reply reply){
        if(closed||pending==null||reply.sequence()!=pendingSequence||!pending.operation().equals(reply.operation()))return;
        Job done=pending;pending=null;
        if(reply.status().equals("busy")){jobs.addFirst(done);return;}
        if(done.operation().equals("records")){
            if(!reply.status().equals("ready")){indexStatus=status=reply.status();return;}
            for(var element:reply.body().getListOrEmpty("rows"))if(element instanceof NbtCompound n){
                var record=CompanionRecord.fromNbt(n.getCompoundOrEmpty("snapshot"));
                if(!n.getBoolean("hidden",false))rows.add(new Row(record,n.getBoolean("bound",false),n.getBoolean("was_friend",false),n.getCompoundOrEmpty("death")));
            }
            indexMore=reply.body().getBoolean("more",false);
            if(indexMore){enqueue("records","",reply.body().getLong("next",0),ZERO);return;}
            indexStatus="ready";
            if(!requestedRoot.isEmpty()){selected=rows.stream().filter(v->v.record().id().equals(requestedRoot)).findFirst().orElse(null);root=requestedRoot;switchTab(initialTab);}
            else status="ready";
            return;
        }
        if(done.revision()!=revision||!done.root().equals(root))return;
        status=reply.status();var body=reply.body();
        if(done.operation().equals("hide_prepare")){if(confirm&&status.equals("confirm_required")){try{hideNonce=UUID.fromString(body.getString("nonce",""));}catch(IllegalArgumentException bad){status="invalid";confirm=false;modal(false);}}return;}
        if(done.operation().equals("hide_confirm")){confirm=false;confirming=false;modal(false);hideNonce=null;if(status.equals("hidden")){rows.removeIf(v->v.record().id().equals(root));selected=null;root="";}return;}
        if(done.operation().equals("hide_cancel"))return;
        if(done.operation().equals("links")){for(var e:body.getListOrEmpty("rows"))if(e instanceof NbtCompound n)links.add(n.copy());if(body.getBoolean("more",false))enqueue("links",root,body.getLong("next",0),ZERO);return;}
        if(status.equals("ready")||status.equals("no_record")){
            for(var e:body.getListOrEmpty("rows"))if(e instanceof NbtCompound n)facts.add(n.copy());
            more=body.getBoolean("more",false);next=body.getLong("next",0);
            if(tab.equals("family")){if(more)loadMore();else enqueue("links",root,0,ZERO);}
        }
    }
    private void choose(Row value){selected=value;root=value.record().id();indexScroll=0;scroll=0;if(tab.equals("dead")){if(listW==0)search.setVisible(false);return;}switchTab(tab);}
    private void switchTab(String value){
        if(confirm)cancelHide();revision++;jobs.removeIf(j->!j.operation().equals("records"));tab=value;status="loading";facts.clear();links.clear();folded.clear();scroll=0;next=0;more=false;zoom=1;panX=panY=0;
        if(search!=null)search.setVisible(listW>0||tab.equals("dead"));
        if(tab.equals("dead")){if(listW==0)selected=null;status=indexStatus;return;}
        if(root.isEmpty()){status="select";return;}
        enqueue(tab,root,0,ZERO);
    }
    private void loadMore(){if(more){more=false;enqueue(tab,root,next,ZERO);}}
    private void prepareHide(){if(selected==null||confirm)return;confirm=true;confirming=false;hideNonce=null;modal(true);enqueue("hide_prepare",root,0,ZERO);}
    private void confirmHide(){if(!confirm||confirming||hideNonce==null)return;confirming=true;enqueue("hide_confirm",root,0,hideNonce);}
    private void cancelHide(){if(confirming)return;confirm=false;hideNonce=null;modal(false);enqueue("hide_cancel",root,0,ZERO);}
    private void modal(boolean open){
        if(open){modalActive.clear();for(var child:children())if(child instanceof net.minecraft.client.gui.widget.ClickableWidget widget&&widget!=modalCancel&&widget!=modalConfirm){modalActive.put(widget,widget.active);widget.active=false;}modalCancel.visible=modalConfirm.visible=true;modalCancel.active=true;setFocused(modalCancel);}
        else{modalActive.forEach((widget,active)->widget.active=active);modalActive.clear();modalCancel.visible=modalConfirm.visible=false;modalCancel.active=modalConfirm.active=false;setFocused(null);}
    }
    @Override public void close(){removed();client.setScreen(null);}
    @Override public void removed(){if(closed)return;closed=true;confirm=false;hideNonce=null;jobs.clear();pending=null;CompanionArchiveClient.close();}
    @Override public void renderBackground(DrawContext c,int mx,int my,float delta){}
    private Text name(CompanionRecord row){return row.info().name().isBlank()?Text.translatable("entity."+row.info().type().replace(':','.')):Text.literal(row.info().name());}
    private Text time(long millis){return millis<=0?t("unknown_time"):Text.literal(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis)));}
    private List<Row> visibleRows(){String query=search==null?"":search.getText().toLowerCase(Locale.ROOT);return rows.stream().filter(v->tab.equals("dead")?v.record().availability().equals("dead")&&v.wasFriend():true).filter(v->query.isBlank()||name(v.record()).getString().toLowerCase(Locale.ROOT).contains(query)||Text.translatable("entity."+v.record().info().type().replace(':','.')).getString().toLowerCase(Locale.ROOT).contains(query)).toList();}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        hits.clear();CompanionUi.frame(c,left,top,pw,ph,true);CompanionUi.text(c,selected==null?t("title"):t("named_title",name(selected.record()),t(tab)),left+120,top+13,pw-160,CompanionUi.TEXT);
        boolean list=listW>0||tab.equals("dead")&&selected==null;int rx=left+12+(listW>0?listW+12:0),rw=pw-24-(listW>0?listW+12:0);
        if(list){int lw=listW>0?listW:pw-24;var filtered=visibleRows();int visible=Math.max(1,(bodyBottom-bodyTop-6)/48);indexScroll=Math.clamp(indexScroll,0,Math.max(0,filtered.size()-visible));
            for(int i=indexScroll;i<Math.min(filtered.size(),indexScroll+visible);i++){Row row=filtered.get(i);int y=bodyTop+(i-indexScroll)*48;CompanionUi.border(c,left+12,y,lw,44,row==selected?0xFFEFA3C7:0xFF80556F);
                if(row.record().availability().equals("dead"))CompanionSkin.image(c,"history-icons/"+(row.bound()?"waiting_reunion":"unbound_reunion"),left+16,y+6,30,30,64,64);else CompanionSkin.image(c,"model/placeholder",left+18,y+8,26,26,32,32);
                CompanionUi.text(c,name(row.record()),left+50,y+7,lw-44,CompanionUi.TEXT);CompanionUi.text(c,t(row.record().availability().equals("dead")?(row.bound()?"waiting":"unbound"):"recorded"),left+50,y+23,lw-44,CompanionUi.MUTED);
                hits.add(new Hit(left+12,y,lw,44,name(row.record()),()->choose(row)));}
            if(filtered.isEmpty())CompanionUi.text(c,t("status."+indexStatus),left+16,bodyTop+12,lw-8,CompanionUi.MUTED);
        }
        if(tab.equals("dead")){if(selected!=null)drawDead(c,rx,rw);}
        else if(tab.equals("history"))drawHistory(c,rx,rw);
        else drawFamily(c,rx,rw);
        if(!tab.equals("family"))CompanionUi.text(c,t("record_note"),left+120,top+ph-20,pw-230,CompanionUi.MUTED);
        if(search.visible){c.fill(search.getX(),search.getY(),search.getX()+search.getWidth(),search.getY()+search.getHeight(),0x4E211624);CompanionUi.border(c,search.getX(),search.getY(),search.getWidth(),search.getHeight(),0xFF80556F);}
        super.render(c,mx,my,delta);
        if(confirm)drawConfirm(c,mx,my);
        else for(var hit:hits)if(mx>=hit.x&&mx<hit.x+hit.w&&my>=hit.y&&my<hit.y+hit.h&&textRenderer.getWidth(hit.text)>hit.w-44){CompanionUi.tooltip(c,hit.text,mx,my,width,height);break;}
    }
    private void drawDead(DrawContext c,int x,int w){
        var record=selected.record();var lines=new ArrayList<net.minecraft.text.OrderedText>();
        for(Text line:List.of(t("life_stage",Text.translatable("stage.noncreatureaffinity."+record.info().stage())),t("death_place",selected.death().getString("dimension",""),selected.death().getDouble("x",0),selected.death().getDouble("y",0),selected.death().getDouble("z",0)),t("death_cause",selected.death().getString("cause","").isBlank()?t("unknown"):Text.literal(selected.death().getString("cause",""))),t("death_time",time(selected.death().getLong("time",0)))))lines.addAll(textRenderer.wrapLines(line,w));
        int bottom=bodyBottom-32;scroll=Math.clamp(scroll,0,Math.max(0,68+lines.size()*14-(bottom-bodyTop)));
        c.enableScissor(x,bodyTop,x+w,bottom);
        try{int y=bodyTop+8-scroll;CompanionSkin.image(c,"history-icons/"+(selected.bound()?"waiting_reunion":"unbound_reunion"),x,y,40,40,64,64);CompanionUi.text(c,name(record),x+48,y+4,w-48,CompanionUi.TEXT);
            CompanionUi.text(c,t(selected.bound()?"waiting":"unbound"),x+48,y+20,w-48,CompanionUi.PINK);y+=56;
            for(var line:lines){c.drawText(textRenderer,line,x,y,CompanionUi.TEXT,false);y+=14;}
        }finally{c.disableScissor();}
    }
    private String eventIcon(NbtCompound n){String kind=n.getString("kind","").toLowerCase(Locale.ROOT);if(List.of("stage_changed","completed","accepted").contains(kind)){int stage=n.getInt("event_stage",-1);return kind+"/"+(stage>=0&&stage<=4?List.of("first","stranger","familiar","close","friend").get(stage):"neutral");}return kind;}
    private void drawHistory(DrawContext c,int x,int w){
        if(facts.isEmpty()){CompanionUi.text(c,t("status."+status),x,bodyTop+12,w,CompanionUi.MUTED);return;}
        int rowH=w<260?62:48,view=bodyBottom-bodyTop-8;scroll=Math.clamp(scroll,0,Math.max(0,facts.size()*rowH-view));c.enableScissor(x,bodyTop,x+w,bodyBottom);
        try{for(int i=0;i<facts.size();i++){int y=bodyTop+i*rowH-scroll;if(y+rowH<bodyTop||y>bodyBottom)continue;var event=facts.get(i);c.fill(x,y,x+w,y+rowH-5,0x4E211624);CompanionUi.border(c,x,y,w,rowH-5,0xFF80556F);CompanionSkin.image(c,"history-icons/"+eventIcon(event),x+5,y+5,32,32,64,64);
            Text title=t("event."+event.getString("kind","").toLowerCase(Locale.ROOT));Text clock=time(event.getLong("time",0));int tw=textRenderer.getWidth(clock),textW=w-50-(w<260?0:tw+12);
            CompanionUi.text(c,title,x+43,y+7,textW,CompanionUi.PINK);Text description=event.getString("kind","").equals("STAGE_CHANGED")?t("stage_transition",stageName(event.getInt("before",-1)),stageName(event.getInt("after",-1))):t("description."+event.getString("kind","").toLowerCase(Locale.ROOT));
            CompanionUi.text(c,description,x+43,y+23,textW,CompanionUi.TEXT);CompanionUi.text(c,clock,x+w-tw-8,w<260?y+43:y+17,tw,CompanionUi.MUTED);
            hits.add(new Hit(x+40,Math.max(y,bodyTop),textW,Math.min(rowH,bodyBottom-Math.max(y,bodyTop)),title.copy().append("\n").append(description).append("\n").append(clock),()->{}));}}
        finally{c.disableScissor();}
    }
    private Text stageName(int stage){return stage>=0&&stage<=4?Text.translatable("stage.noncreatureaffinity."+List.of("new","stranger","familiar","close","friend").get(stage)):t("unknown");}
    private void drawFamily(DrawContext c,int x,int w){
        if(facts.isEmpty()){CompanionUi.text(c,t("status."+status),x,bodyTop+12,w,CompanionUi.MUTED);return;}
        Set<String> hidden=new HashSet<>();Deque<String> queue=new ArrayDeque<>();for(String fold:folded){int gen=facts.stream().filter(n->n.getString("id","").equals(fold)).findFirst().map(n->n.getInt("generations",0)).orElse(0);for(var link:links)if(link.getString(gen<0?"child":"parent","").equals(fold))queue.add((gen<0?"up:":"down:")+link.getString(gen<0?"parent":"child",""));}
        while(!queue.isEmpty()){String entry=queue.removeFirst();boolean up=entry.startsWith("up:");String id=entry.substring(up?3:5);if(id.equals(root)||!hidden.add(id))continue;for(var link:links)if(link.getString(up?"child":"parent","").equals(id))queue.add((up?"up:":"down:")+link.getString(up?"parent":"child",""));}
        int centre=x+w/2,nodeW=Math.max(90,(int)(130*zoom)),nodeH=42,columnStep=nodeW+Math.max(8,(int)(10*zoom)),rowStep=nodeH+Math.max(12,(int)(28*zoom));Map<String,int[]> positions=new HashMap<>();Map<Integer,Integer> counts=new HashMap<>();
        for(var node:facts)if(!hidden.contains(node.getString("id",""))){int gen=node.getInt("generations",0),order=counts.merge(gen,1,Integer::sum)-1;positions.put(node.getString("id",""),new int[]{(int)(centre+panX+order*columnStep-nodeW/2.0),(int)(bodyTop+70+panY+gen*rowStep)});}
        c.enableScissor(x,bodyTop,x+w,bodyBottom);
        try{for(var link:links){var a=positions.get(link.getString("parent",""));var b=positions.get(link.getString("child",""));if(a!=null&&b!=null){int ax=a[0]+nodeW/2,ay=a[1]+nodeH,bx=b[0]+nodeW/2,by=b[1];c.fill(ax,Math.min(ay,by),ax+1,Math.max(ay,by)+1,0xFF80556F);c.fill(Math.min(ax,bx),by,Math.max(ax,bx)+1,by+1,0xFF80556F);}}
            for(var node:facts){String id=node.getString("id","");var pos=positions.get(id);if(pos==null)continue;int nx=pos[0],ny=pos[1],nw=nodeW;if(ny+42<bodyTop||ny>bodyBottom||nx+nw<x||nx>x+w)continue;
                c.fill(nx,ny,nx+nw,ny+42,0x4E211624);CompanionUi.border(c,nx,ny,nw,42,id.equals(root)?0xFFEFA3C7:0xFF80556F);
                Text label=familyName(node);
                thumbnail(c,id,nx+4,ny+6);CompanionUi.text(c,label,nx+34,ny+7,nw-40,CompanionUi.TEXT);int gen=node.getInt("generations",0);CompanionUi.text(c,id.equals(root)?t("self"):Text.translatable("message.noncreatureaffinity.family."+com.nonid.affinity.service.FamilyLabels.relative(gen,node.getInt("gender",0))),nx+6,ny+24,nw-12,CompanionUi.PINK);
                hits.add(new Hit(Math.max(x,nx),Math.max(bodyTop,ny),Math.min(nx+nw,x+w)-Math.max(x,nx),Math.min(ny+42,bodyBottom)-Math.max(bodyTop,ny),label,()->{if(!folded.add(id))folded.remove(id);}));}}
        finally{c.disableScissor();}
    }
    private Text familyName(NbtCompound node){
        String name=node.getString("name",""),id=node.getString("id","");
        if(!id.startsWith("player:")&&node.getString("name_kind","UNKNOWN").equals("DEFAULT_TYPE")||name.isBlank())return Text.translatable("entity."+node.getString("type","").replace(':','.'));
        return Text.literal(name);
    }
    private void thumbnail(DrawContext c,String id,int x,int y){
        if(client.world==null||failedModels.contains(id))return;
        var actual=client.world.getEntities().iterator();
        while(actual.hasNext()){
            var entity=actual.next();if(!id.equals((entity instanceof net.minecraft.entity.player.PlayerEntity?"player:":"entity:")+entity.getUuid()))continue;
            try{
                if(entity instanceof net.minecraft.client.network.AbstractClientPlayerEntity player)net.minecraft.client.gui.PlayerSkinDrawer.draw(c,player.getSkin(),x,y,24);
                else if(entity instanceof net.minecraft.entity.LivingEntity living)CompanionModelPreview.draw(c,living,x,y,24,26,180,failure->modelFailure(id,failure));
            }catch(RuntimeException failure){modelFailure(id,failure);}return;
        }
        // Unloaded players retain their recorded name; never create a player or fetch a skin for this graph.
    }
    private void modelFailure(String id,RuntimeException failure){if(failedModels.add(id))NonCreatureAffinity.LOGGER.warn("Family model unavailable for {}",id,failure);}
    private void drawConfirm(DrawContext c,int mx,int my){c.fill(left,top,left+pw,top+ph,0xAA211624);int w=Math.min(280,pw-24),x=left+(pw-w)/2,y=top+(ph-100)/2;c.fill(x,y,x+w,y+100,0xF5211624);CompanionUi.border(c,x,y,w,100,0xFFAD5A83);CompanionUi.text(c,t("hide_title"),x+12,y+12,w-24,CompanionUi.TEXT);int at=y+30;for(var line:textRenderer.wrapLines(t("hide_note"),w-24)){c.drawText(textRenderer,line,x+12,at,CompanionUi.MUTED,false);at+=11;}
        modalCancel.render(c,mx,my,0);modalConfirm.render(c,mx,my,0);hits.clear();
    }
    @Override public boolean mouseClicked(Click click,boolean doubled){if(confirm){modalCancel.mouseClicked(click,doubled);modalConfirm.mouseClicked(click,doubled);return true;}for(var hit:hits)if(click.x()>=hit.x&&click.x()<hit.x+hit.w&&click.y()>=hit.y&&click.y()<hit.y+hit.h){hit.action.run();return true;}if(confirm)return true;
        if(tab.equals("family")&&click.x()>=left+12+listW&&click.x()<left+pw-12&&click.y()>=bodyTop&&click.y()<bodyBottom){dragging=true;dragX=click.x();dragY=click.y();return true;}boolean handled=super.mouseClicked(click,doubled);if(confirm&&!confirming)setFocused(modalCancel);return handled;}
    @Override public boolean mouseDragged(Click click,double dx,double dy){if(dragging){panX+=dx;panY+=dy;return true;}return super.mouseDragged(click,dx,dy);}
    @Override public boolean mouseReleased(Click click){dragging=false;return super.mouseReleased(click);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(confirm)return true;if(listW>0&&x<left+listW+12||tab.equals("dead")&&selected==null)indexScroll-=(int)Math.signum(vertical);else if(tab.equals("family"))zoom=Math.clamp(zoom*Math.pow(1.1,vertical),.6,1.8);else scroll-=(int)(vertical*36);return true;}
    @Override public boolean keyPressed(KeyInput key){if(confirm){if(key.key()==GLFW.GLFW_KEY_ESCAPE)cancelHide();else super.keyPressed(key);return true;}return super.keyPressed(key);}
}
