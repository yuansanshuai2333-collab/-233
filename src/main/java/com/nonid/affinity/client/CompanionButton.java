package com.nonid.affinity.client;

import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

final class CompanionButton extends ClickableWidget {
    private final Runnable action;private final String kind;private final Supplier<String> stage;private long pressed;
    CompanionButton(int x,int y,int w,int h,Text label,String kind,Supplier<String> stage,Runnable action){super(x,y,w,h,label);this.kind=kind;this.stage=stage;this.action=action;}
    @Override public void onClick(Click click,boolean doubled){pressed=System.currentTimeMillis()+120;action.run();}
    @Override public boolean keyPressed(KeyInput key){if(active&&visible&&(key.key()==GLFW.GLFW_KEY_ENTER||key.key()==GLFW.GLFW_KEY_SPACE)){pressed=System.currentTimeMillis()+120;action.run();return true;}return super.keyPressed(key);}
    @Override protected void appendClickableNarrations(NarrationMessageBuilder b){appendDefaultNarrations(b);}
    @Override protected void renderWidget(DrawContext c,int mx,int my,float delta){
        String state=!active?"disabled":System.currentTimeMillis()<pressed?"pressed":isHovered()?"hover":isFocused()?"focus":"default";
        String tier=stage.get();if(tier==null||tier.equals("new"))tier="first";
        boolean fixed=kind.equals("close")||kind.equals("scan")||kind.equals("direct_interaction");
        if(fixed)CompanionSkin.image(c,"specialty/buttons/"+kind+"/"+tier+"-"+state,getX()-4,getY()-4,width+8,height+8,width+8,height+8);
        else CompanionSkin.nine(c,"history/header-"+state,getX(),getY(),width,height,192,18);
        if(!kind.equals("close")){var tr=MinecraftClient.getInstance().textRenderer;int pad=fixed?18:5;String label=tr.trimToWidth(getMessage().getString(),Math.max(1,width-pad-(fixed?16:5)));c.drawText(tr,label,getX()+pad,getY()+(height-8)/2,0xFFFFE9F4,false);}
    }
}
