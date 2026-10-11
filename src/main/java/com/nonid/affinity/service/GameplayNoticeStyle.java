package com.nonid.affinity.service;
import net.minecraft.text.*;
/** Presentation only; every detail is the original authoritative result text. */
public final class GameplayNoticeStyle {
 public record Style(int kind,int priority,String title){}
 /** Only new generic notices use these portraits. Existing four-card artwork stays unchanged. */
 public static String portrait(Text title,Text detail){
  String key=title.getContent() instanceof TranslatableTextContent t?t.getKey():"";
  String prefix="message.noncreatureaffinity.feedback.";
  if(!key.startsWith(prefix))return null;
  return switch(key.substring(prefix.length())){
   case "accepted","updated" -> "approved";
   case "rejected","restricted","unavailable" -> "rejected";
   case "decrease" -> "decrease";
   case "relationship","birth" -> "celebrate";
   case "status" -> {
    String reason=detail.getContent() instanceof TranslatableTextContent t?t.getKey():"";
    yield reason.endsWith("reunion_preparing")||reason.endsWith("reunion_wait")?"waiting":null;
   }
   default -> null;
  };
 }
 public static Style of(Text text){
  String key=text.getContent() instanceof TranslatableTextContent t?t.getKey():"";
  if(key.endsWith("accepted"))return new Style(1,1,"accepted");
  if(key.contains("rejected_"))return new Style(3,2,"rejected");
  if(key.endsWith("offspring_born"))return new Style(1,1,"birth");
  if(key.endsWith("unified_relationship")||key.endsWith("befriended")||key.endsWith("tamed"))return new Style(1,1,"relationship");
  if(key.endsWith("companion_mode")||key.endsWith("highlight_done")||key.endsWith("reunion_bound")||key.endsWith("_started"))return new Style(1,0,"updated");
  if(key.endsWith("escape_hint")||key.endsWith("reunion_preparing")||key.endsWith("reunion_wait")||key.endsWith("reunion_bind_hint"))return new Style(2,1,"status");
  if(key.contains("timeout")||key.contains("stale")||key.contains("gone")||key.contains("empty")||key.contains("not_found"))return new Style(2,2,"unavailable");
  return new Style(3,2,"restricted");
 }
 private GameplayNoticeStyle(){}
}
