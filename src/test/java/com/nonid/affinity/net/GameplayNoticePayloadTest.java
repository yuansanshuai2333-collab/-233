package com.nonid.affinity.net;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GameplayNoticePayloadTest {
 // Minecraft's text codec needs the initialized game registry; actual wire round-trip is checked by the isolated TCP fixture.
 @Test void preservesActualTranslatedReasonAndDecreaseFacts(){
  var reason=Text.translatable("reason.noncreatureaffinity.hit");
  var detail=Text.translatable("message.noncreatureaffinity.feedback.decrease.detail",Text.literal("Milo"),5,reason);
  var p=new GameplayNoticePayload(UUID.randomUUID(),Identifier.ofVanilla("overworld"),0,1,Text.translatable("message.noncreatureaffinity.feedback.decrease"),detail,75,70,100);
  assertSame(detail,p.detail());assertEquals(75,p.before());assertEquals(70,p.after());
  var content=(net.minecraft.text.TranslatableTextContent)p.detail().getContent();assertEquals(reason,content.getArgs()[2]);
 }
 @Test void unknownKindsUnboundedTextAndInvalidBarCannotEnterQueue(){var id=UUID.randomUUID();var dim=Identifier.ofVanilla("overworld");assertThrows(IllegalArgumentException.class,()->new GameplayNoticePayload(id,dim,4,1,Text.empty(),Text.empty(),0,0,0));assertThrows(IllegalArgumentException.class,()->new GameplayNoticePayload(id,dim,0,3,Text.empty(),Text.empty(),0,0,0));assertThrows(IllegalArgumentException.class,()->new GameplayNoticePayload(id,dim,0,1,Text.empty(),Text.literal("x".repeat(2049)),0,0,0));}
}
