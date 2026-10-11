package com.nonid.affinity.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Regression for the actual GUI_TEXTURED_PREMULTIPLIED_ALPHA path; null is a synthetic map key only. */
class ScopedItemFadePremultAuditTest {
 @SuppressWarnings("unchecked") int fade(int color,float alpha)throws Exception{
  var f=ScopedItemFade.class.getDeclaredField("CURRENT");f.setAccessible(true);var current=(ThreadLocal<Float>)f.get(null);float previous=current.get();
  try{current.set(alpha);ScopedItemFade.remember(null);return ScopedItemFade.color(null,color);}finally{current.set(previous);}
 }
 @Test void quarterOpacityScalesPremultipliedWhite()throws Exception{assertEquals(0x40404040,fade(0xffffffff,.25f));}
 @Test void zeroOpacityHasNoSourceRgbContribution()throws Exception{assertEquals(0x00000000,fade(0xffffffff,0));}
 @Test void existingPremultipliedTintAndAlphaAreBothPreserved()throws Exception{assertEquals(0x20100804,fade(0x80402010,.25f));}
}
