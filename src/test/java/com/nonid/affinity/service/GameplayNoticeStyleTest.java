package com.nonid.affinity.service;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GameplayNoticeStyleTest {
 @Test void retainsOldCardsAndUsesApprovedRejection(){
  assertNull(GameplayNoticeStyle.portrait(Text.translatable("message.noncreatureaffinity.notice.restricted.title"),Text.empty()));
  assertEquals("rejected",GameplayNoticeStyle.portrait(Text.translatable("message.noncreatureaffinity.feedback.rejected"),Text.empty()));
 }
 @Test void waitingRequiresActualWaitReason(){
  var title=Text.translatable("message.noncreatureaffinity.feedback.status");
  assertEquals("waiting",GameplayNoticeStyle.portrait(title,Text.translatable("message.noncreatureaffinity.reunion_preparing")));
  assertNull(GameplayNoticeStyle.portrait(title,Text.translatable("message.noncreatureaffinity.escape_hint")));
 }
}
