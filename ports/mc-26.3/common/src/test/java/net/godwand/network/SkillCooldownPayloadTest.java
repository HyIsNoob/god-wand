package net.godwand.network;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SkillCooldownPayloadTest {
 @Test void roundTripPreservesIndividualTimerAndRecall() {
  var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
  try {var p=new SkillCooldownPayload(1,200,80,true);SkillCooldownPayload.CODEC.encode(buf,p);assertEquals(p,SkillCooldownPayload.CODEC.decode(buf));assertEquals(0,buf.readableBytes());}finally{buf.release();}
 }
 @Test void rejectsInvalidBounds() {
  assertThrows(IllegalArgumentException.class,()->new SkillCooldownPayload(6,20,20,false));
  assertThrows(IllegalArgumentException.class,()->new SkillCooldownPayload(0,20,21,false));
  assertThrows(IllegalArgumentException.class,()->new SkillCooldownPayload(0,20,10,true));
 }
}
