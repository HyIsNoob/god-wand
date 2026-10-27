package net.godwand;
import net.godwand.skill.AtomicSequence;
import net.godwand.config.GodWandConfig;
import net.godwand.config.WandSkin;
import net.godwand.network.AtomicFxPayload;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
public final class AtomicPortCheck {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args) {
        net.godwand.skill.AtomicVertexOrderCheck.verify();
  check(AtomicSequence.CHARGE_TICKS==380 && AtomicSequence.ATOMIC_WORD==396 && AtomicSequence.WAIT_TICK==406 && AtomicSequence.DETONATION==414 && AtomicSequence.END==834,"accepted timing");
  check(AtomicSequence.castAge(10000,0,-1)==406,"hold freezes sequence");
  long released=AtomicSequence.releaseTime(500,0);
  check(AtomicSequence.castAge(released+8,0,released)==414,"release reaches detonation");
  check(AtomicSequence.beamRadius(414)==0 && AtomicSequence.beamRadius(534)==98 && AtomicSequence.beamRadius(734)==0,"blast expansion and contraction");
  check(AtomicSequence.movementLocked(825) && !AtomicSequence.movementLocked(826),"camera return unlocks movement");
  check(AtomicSequence.risingMote(.5,8).horizontalDistance()>=3 && AtomicSequence.risingMote(.5,8).horizontalDistance()<=24,"mote bounds");
  int pressureHits=0; for(int tick=440;tick<600;tick++) for(int wave=0;wave<4;wave++) {
   if(AtomicSequence.pressureCrosses(tick,wave,115)) pressureHits++;
   check(!AtomicSequence.pressureCrosses(tick,wave,90) && !AtomicSequence.pressureCrosses(tick,wave,140),"pressure damage stays outside beam and within wave reach");
  }
  check(pressureHits>0,"pressure front crosses outside-beam target");
  var config=new GodWandConfig.Server(); config.atomicTerrainRadius=999;config.atomicDamage=Float.NaN;config.atomicShockwaveDamage=-1;config.validate();
  check(config.atomicTerrainRadius==98 && config.atomicDamage==9999 && config.atomicShockwaveDamage==0,"config validation");
  var client=new GodWandConfig.Client();check(client.getActiveSkin(2)==WandSkin.AQUA_HEARTIA && client.getActiveSkin(4)==WandSkin.AQUA_HEARTIA && client.getActiveSkin(5)==WandSkin.SHADOW_BLADE,"AUTO mapping");
  var value=new AtomicFxPayload(9,123,1.5,64,-2.5,AtomicFxPayload.RELEASE,550);var buf=new FriendlyByteBuf(Unpooled.buffer());
  try {value.write(buf);check(value.equals(AtomicFxPayload.read(buf)) && buf.readableBytes()==0,"legacy packet roundtrip");} finally {buf.release();}
  System.out.println("Atomic Fabric 1.20.1 port checks passed");
 }
}
