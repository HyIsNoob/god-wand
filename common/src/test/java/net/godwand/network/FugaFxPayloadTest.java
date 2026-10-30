package net.godwand.network;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FugaFxPayloadTest {
 @Test void codecPreservesBothClocksAndTarget() {
  var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
  try { var packet=new FugaFxPayload(7,100,1,2,3,151,4,5,FugaFxPayload.FIRE,300);
   FugaFxPayload.CODEC.encode(buf,packet); assertEquals(packet,FugaFxPayload.CODEC.decode(buf)); assertEquals(0,buf.readableBytes());
  } finally {buf.release();}
 }
 @Test void rejectsInvalidCoordinatesPhasesAndReversedClocks() {
  assertThrows(IllegalArgumentException.class,()->new FugaFxPayload(1,10,Double.NaN,0,0,0,0,0,0,10));
  assertThrows(IllegalArgumentException.class,()->new FugaFxPayload(1,10,0,0,0,0,0,0,5,10));
  assertThrows(IllegalArgumentException.class,()->new FugaFxPayload(1,10,0,0,0,0,0,0,1,9));
  assertThrows(IllegalArgumentException.class,()->new FugaFxPayload(1,10,0,0,0,0,0,0,1,129));
 }
}
