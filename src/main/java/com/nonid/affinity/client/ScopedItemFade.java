package com.nonid.affinity.client;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.ItemGuiElementRenderState;
import net.minecraft.item.ItemStack;
/** Alpha is attached only to the exact GUI item state produced inside our draw call. */
public final class ScopedItemFade {
 private static final ThreadLocal<Float> CURRENT=ThreadLocal.withInitial(()->1f);
 private static final Map<ItemGuiElementRenderState,Float> ALPHAS=new WeakHashMap<>();
 private ScopedItemFade(){}
 public static void draw(DrawContext c,ItemStack stack,int x,int y,float alpha){
  float previous=CURRENT.get();CURRENT.set(Math.clamp(alpha,0,1));
  try{c.drawItem(stack,x,y);}finally{CURRENT.set(previous);}
 }
 public static void remember(ItemGuiElementRenderState state){float a=CURRENT.get();if(a<1)ALPHAS.put(state,a);}
 public static int color(ItemGuiElementRenderState state,int original){
  Float a=ALPHAS.remove(state);if(a==null)return original;
  // GuiRenderer's item quad uses premultiplied-alpha blending: opacity must scale all four channels.
  return (Math.round((original>>>24)*a)<<24)|(Math.round(((original>>>16)&255)*a)<<16)
    |(Math.round(((original>>>8)&255)*a)<<8)|Math.round((original&255)*a);
 }
}
