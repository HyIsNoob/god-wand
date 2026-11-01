package net.godwand.skill;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SkillCooldownRespawnTest {
 @Test void replacementPlayerWithSameEntityIdDoesNotInheritOldTickDeadlines() throws Exception {
  net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
  var factory=Class.forName("org.objenesis.ObjenesisStd");
  var objects=factory.getConstructor().newInstance();
  var create=factory.getMethod("newInstance",Class.class);
  var old=(ServerPlayer)create.invoke(objects,ServerPlayer.class);var replacement=(ServerPlayer)create.invoke(objects,ServerPlayer.class);
  old.setId(424242);replacement.setId(424242);
  assertEquals(old,replacement);assertNotSame(old,replacement);
  var state=SkillCooldowns.class.getDeclaredMethod("state",ServerPlayer.class);state.setAccessible(true);
  var previous=(SkillCooldownState)state.invoke(null,old);
  previous.set(5,1200,1200,50000);previous.set(1,200,100,50000);
  var next=(SkillCooldownState)state.invoke(null,replacement);
  assertEquals(0,next.remaining(5,0));assertEquals(0,next.remaining(1,0));
  assertSame(next,state.invoke(null,replacement));
 }
}
