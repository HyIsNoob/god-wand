package net.godwand.skill;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SkillCooldownStateTest {
 @Test void coolingOneSkillLeavesOtherSkillsReadyAndSwitchingDoesNotResetItsClock() {
  var timers=new SkillCooldownState();timers.set(5,1200,1200,100);
  assertEquals(1200,timers.remaining(5,100));for(int mode=0;mode<5;mode++)assertEquals(0,timers.remaining(mode,100));
  timers.set(1,200,200,150);assertEquals(1100,timers.remaining(5,200));assertEquals(150,timers.remaining(1,200));
  assertEquals(0,timers.remaining(1,350));assertEquals(1000,timers.remaining(5,300));assertEquals(0,timers.remaining(5,1300));
 }
 @Test void serverRemainingTimePreservesDisplayFractionAndZeroDisablesCooldown() {
  var timers=new SkillCooldownState();timers.set(2,320,160,50);assertEquals(.5f,timers.fraction(2,50));assertEquals(0,timers.remaining(2,210));
  timers.set(2,0,0,100);assertEquals(0,timers.fraction(2,100));
  var otherPlayer=new SkillCooldownState();assertEquals(0,otherPlayer.remaining(5,100));
 }
 @Test void rejectsInvalidSkillAndTimerBounds() {
  var timers=new SkillCooldownState();assertThrows(IllegalArgumentException.class,()->timers.set(6,20,20,0));
  assertThrows(IllegalArgumentException.class,()->timers.set(0,20,21,0));assertThrows(IllegalArgumentException.class,()->timers.set(0,-1,0,0));
 }
}
