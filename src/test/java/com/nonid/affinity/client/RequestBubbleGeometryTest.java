package com.nonid.affinity.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RequestBubbleGeometryTest {
 @Test void footprintIsPhysicalNotGuiScale(){assertEquals(520,RequestBubbleGeometry.physicalWidth(1714));assertEquals(360,RequestBubbleGeometry.physicalWidth(960));assertEquals(520,RequestBubbleGeometry.physicalWidth(3840));}
 @Test void transparentCornersDoNotHideHud(){var t=RequestBubbleGeometry.tail(32,205);assertFalse(RequestBubbleGeometry.inside(0,0,t));assertFalse(RequestBubbleGeometry.inside(370,200,t));assertTrue(RequestBubbleGeometry.inside(190,105,t));assertTrue(RequestBubbleGeometry.inside(32,205,t));}
 @Test void buttonsAreSeparateAndNeedVerticalMatch(){assertEquals(1,RequestBubbleGeometry.button(100,150));assertEquals(2,RequestBubbleGeometry.button(240,150));assertEquals(0,RequestBubbleGeometry.button(190,150));assertEquals(0,RequestBubbleGeometry.button(100,120));}
 @Test void buttonsHaveNoInvisiblePaddingAcrossPhysicalSizesAndGuiScales(){
  for(int framebuffer:new int[]{640,960,1280,1920,3840})for(int gui:new int[]{1,2,3,4,6})for(int choice=1;choice<=2;choice++){
   double scale=RequestBubbleGeometry.physicalWidth(framebuffer)/380/gui;
   double left=RequestBubbleGeometry.buttonLeft(choice)*380.0/520,top=170*208.0/286;
   double right=left+160*380.0/520,bottom=top+62*208.0/286;
   for(double[] p:new double[][]{{left+.01,top+.01},{right-.01,bottom-.01}})
    assertEquals(choice,RequestBubbleGeometry.button(p[0]*scale/scale,p[1]*scale/scale));
   for(double[] p:new double[][]{{left-.01,top+1},{right+.01,top+1},{left+1,top-.01},{left+1,bottom+.01}})
    assertEquals(0,RequestBubbleGeometry.button(p[0]*scale/scale,p[1]*scale/scale));
  }
 }
 @Test void movedTailStillIncludesTip(){assertTrue(RequestBubbleGeometry.inside(-80,180,RequestBubbleGeometry.tail(-80,180)));assertTrue(RequestBubbleGeometry.inside(430,190,RequestBubbleGeometry.tail(430,190)));}
}
