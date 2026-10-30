package net.godwand;
import net.godwand.network.FugaFxPayload;
import net.godwand.skill.FugaSequence;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
public final class FugaPortCheck {
 public static void verify() {
  if(FugaSequence.ready(199,0) || !FugaSequence.ready(200,0) || FugaSequence.terrainActive(FugaSequence.RETRACT_TICK))throw new AssertionError("Fuga lifecycle");
  for(int phase=0;phase<=4;phase++) {
   var value=new FugaFxPayload(9,123,1.5,64,-2.5,100,65,100,phase,phase==0?123:350);
   var buf=new FriendlyByteBuf(Unpooled.buffer());
   try {value.write(buf);if(!value.equals(FugaFxPayload.read(buf)) || buf.readableBytes()!=0)throw new AssertionError("Fuga packet roundtrip");} finally {buf.release();}
  }
 }
 public static void main(String[] args) {verify();}
}
