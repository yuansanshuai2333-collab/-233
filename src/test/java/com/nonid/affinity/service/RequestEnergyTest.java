package com.nonid.affinity.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RequestEnergyTest {
 @Test void actualCapDrivesBarInsteadOfRequestAffinityThreshold(){
  var half=new RequestEnergy(100,200,true);
  assertEquals(.5,half.ratio());assertEquals(4,half.hearts());
  assertEquals(2,new RequestEnergy(100,400,true).hearts());
 }
 @Test void unknownIsNotZero(){
  assertFalse(RequestEnergy.UNKNOWN.known());assertEquals(-1,RequestEnergy.UNKNOWN.ratio());
  assertFalse(new RequestEnergy(10,0,true).known());assertFalse(new RequestEnergy(10,200,false).known());
  assertTrue(new RequestEnergy(0,200,true).known());assertEquals(0,new RequestEnergy(0,200,true).ratio());
 }
 @Test void displayCapsWithoutChangingRawValue(){
  var excess=new RequestEnergy(250,200,true);
  assertEquals(250,excess.value());assertEquals(1,excess.ratio());assertEquals(8,excess.hearts());
 }
}
