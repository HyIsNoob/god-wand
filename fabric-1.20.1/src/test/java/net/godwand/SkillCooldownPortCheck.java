package net.godwand;
import net.godwand.skill.SkillCooldownState;
import net.godwand.network.SkillCooldownPayload;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
public final class SkillCooldownPortCheck {
 public static void verify() {
  var s=new SkillCooldownState();s.set(5,1200,1200,0);s.set(1,200,200,20);
  if(s.remaining(0,30)!=0||s.remaining(5,30)!=1170||s.remaining(1,30)!=190)throw new AssertionError("Cooldowns must remain independent");
  var b=new FriendlyByteBuf(Unpooled.buffer());
  try{var p=new SkillCooldownPayload(1,200,80,true);p.write(b);if(!p.equals(SkillCooldownPayload.read(b))||b.readableBytes()!=0)throw new AssertionError("Cooldown wire roundtrip");}finally{b.release();}
 }
}
