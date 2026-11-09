package net.godwand.network;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SilentGatePayloadTest {
 private static SilentGatePayload make(long startedAt,long phaseAt,int phase,double x,int[] shots,double[] hits) {
  return new SilentGatePayload(7,startedAt,phaseAt,phase,x,64,-3,45.5f,99L,48,200,shots,hits);
 }
 @Test void roundTripsThroughCodec() {
  var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
  try { var p=make(100,130,2,1.5,new int[]{0,55,3},new double[]{1,2,3,4,5,6,7,8,9});
   SilentGatePayload.CODEC.encode(buf,p); var d=SilentGatePayload.CODEC.decode(buf); assertEquals(0,buf.readableBytes());
   assertEquals(p.casterId(),d.casterId()); assertEquals(p.startedAt(),d.startedAt()); assertEquals(p.phaseAt(),d.phaseAt());
   assertEquals(p.phase(),d.phase()); assertEquals(p.x(),d.x()); assertEquals(p.y(),d.y()); assertEquals(p.z(),d.z());
   assertEquals(p.yaw(),d.yaw()); assertEquals(p.seed(),d.seed()); assertEquals(p.skyCircles(),d.skyCircles());
   assertEquals(p.barrageTicks(),d.barrageTicks()); assertArrayEquals(p.shots(),d.shots()); assertArrayEquals(p.hits(),d.hits());
  } finally {buf.release();}
 }
 @Test void acceptsTheNewCircleCounts() {
  var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
  try { var p=new SilentGatePayload(7,100,130,2,1.5,64,-3,45.5f,99L,256,200,new int[]{0,255,287},new double[]{1,2,3,4,5,6,7,8,9});
   SilentGatePayload.CODEC.encode(buf,p); assertEquals(256,SilentGatePayload.CODEC.decode(buf).skyCircles());
  } finally {buf.release();}
  assertThrows(IllegalArgumentException.class,()->new SilentGatePayload(7,100,130,2,1.5,64,-3,45.5f,99L,257,200,new int[0],new double[0]));
  assertThrows(IllegalArgumentException.class,()->new SilentGatePayload(7,100,130,2,1.5,64,-3,45.5f,99L,160,200,new int[]{192},new double[]{1,2,3}));
  assertDoesNotThrow(()->new SilentGatePayload(7,100,130,2,1.5,64,-3,45.5f,99L,160,200,new int[]{191},new double[]{1,2,3}));
 }
 @Test void rejectsMalformedLifecycle() {
  var none=new int[0]; var noHits=new double[0];
  assertDoesNotThrow(()->make(10,10,4,0,none,noHits));
  assertThrows(IllegalArgumentException.class,()->make(10,10,5,0,none,noHits));
  assertThrows(IllegalArgumentException.class,()->make(10,9,0,0,none,noHits));
  assertThrows(IllegalArgumentException.class,()->make(10,10,0,Double.NaN,none,noHits));
  assertThrows(IllegalArgumentException.class,()->make(10,10,0,0,new int[]{1},new double[]{1,2}));
  assertThrows(IllegalArgumentException.class,()->make(10,10,0,0,new int[]{80},new double[]{1,2,3}));
  assertThrows(IllegalArgumentException.class,()->make(10,10,0,0,new int[]{-1},new double[]{1,2,3}));
  assertThrows(IllegalArgumentException.class,()->make(10,10,0,0,new int[]{1},new double[]{1,Double.POSITIVE_INFINITY,3}));
  assertThrows(IllegalArgumentException.class,()->make(10,10,0,0,new int[105],new double[315]));
  assertDoesNotThrow(()->make(10,10,0,0,new int[104],new double[312]));
 }
 @Test void featuredTargetRoundTripsAndIsValidated() {
  var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
  try { for(int id:new int[]{-1,0,4711}) {
    var p=new SilentGatePayload(7,100,130,2,1.5,64,-3,45.5f,99L,48,200,new int[0],new double[0],id);
    SilentGatePayload.CODEC.encode(buf,p); var d=SilentGatePayload.CODEC.decode(buf);
    assertEquals(id,d.featured()); assertEquals(0,buf.readableBytes()); }
   assertEquals(-1,make(10,10,0,0,new int[0],new double[0]).featured(),"old constructor means none");
  } finally {buf.release();}
  assertThrows(IllegalArgumentException.class,()->new SilentGatePayload(7,100,130,2,1.5,64,-3,45.5f,99L,48,200,new int[0],new double[0],-2));
 }
}
