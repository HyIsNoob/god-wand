package net.godwand;
import net.godwand.network.FugaFxPayload;
import net.godwand.skill.FugaSequence;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
public final class FugaPortCheck {
 public static void verify() {
  if(FugaSequence.ready(119,0) || !FugaSequence.ready(120,0) || FugaSequence.terrainActive(120))throw new AssertionError("Fuga lifecycle");
  for(int phase=0;phase<=4;phase++) {
   var value=new FugaFxPayload(9,123,1.5,64,-2.5,100,65,100,phase,phase==0?123:250);
   var buf=new FriendlyByteBuf(Unpooled.buffer());
   try {value.write(buf);if(!value.equals(FugaFxPayload.read(buf)) || buf.readableBytes()!=0)throw new AssertionError("Fuga packet roundtrip");} finally {buf.release();}
  }
 }
 public static void main(String[] args) {verify();}
}
