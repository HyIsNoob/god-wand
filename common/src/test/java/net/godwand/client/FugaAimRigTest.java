package net.godwand.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
@org.junit.jupiter.api.Disabled("Rejected custom aim rig is not registered; aim uses vanilla BOW_AND_ARROW arms with a torso turn")
class FugaAimRigTest {
 @Test void horizontalArcherDrawsBesideNeckWithElbowBehindShoulder() {
  var j=FugaAimRig.joints(220,0);var h=j.elbow().lerp(j.tip(),7.0/8);
  assertEquals(2,j.shoulder().y,.001);assertEquals(2,j.elbow().y,.001);assertEquals(2,h.y,.001);
  assertTrue(j.elbow().z>j.shoulder().z+3,"elbow must open backward");
  assertTrue(h.z>2,"drawing hand must be beside the rear shoulder, not in front of the chest");
  assertTrue(j.elbow().subtract(j.shoulder()).normalize().dot(j.tip().subtract(j.elbow()).normalize())<0);
  assertEquals(0,FugaAimRig.weight(120));assertEquals(1,FugaAimRig.weight(200));assertEquals(1,FugaAimRig.weight(20000));
 }
 @Test void bothHandsStayOnTheAimAxisAcrossPitchAndHeldIdle() {
  for(float pitch:new float[]{0,.35f,-.35f})for(int age:new int[]{200,240,500,2000}) {
   var front=FugaPose.hand(age,pitch,false,false);var back=FugaAimRig.hand(age,pitch);
   var axis=front.subtract(back).normalize();
   assertEquals(0,axis.x,.001);assertEquals(-Math.sin(pitch),axis.y,.001);assertEquals(Math.cos(pitch),axis.z,.001);
  }
 }
}
