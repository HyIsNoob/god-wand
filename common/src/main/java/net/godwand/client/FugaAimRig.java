package net.godwand.client;
import net.minecraft.world.phys.Vec3;
import java.util.function.IntPredicate;

/** Two rigid blocks for the drawing arm only; the ritual and left arm stay vanilla. */
public final class FugaAimRig {
 private FugaAimRig() {}
 public static IntPredicate availableFor=id->false;
 public record Joints(Vec3 shoulder,Vec3 elbow,Vec3 tip) {}
 public static float weight(double age){return age<144?0:FugaCinematic.smooth(age,144,184);}
 public static Joints joints(double age,float pitch) {
  float turn=FugaPose.arms(0,age,pitch,0).bodyYaw();
  var shoulder=new Vec3(-5*Math.cos(turn),2,5*Math.sin(turn));
  // Draw beside the neck, with both hands on the same shooting axis. The elbow opens behind the shoulder.
  var front=FugaPose.hand(age,pitch,false,false);
  var level=FugaPose.hand(age,0,false,false);
  Vec3 frontModel=new Vec3(-front.x*16,(1.5-front.y)*16,-front.z*16);
  double drawLength=3+level.z*16;
  Vec3 direction=new Vec3(0,Math.sin(pitch),-Math.cos(pitch));
  Vec3 hand=frontModel.subtract(direction.scale(drawLength));
  Vec3 rear=hand.subtract(direction.scale(5));
  var elbow=new Vec3(-5.5,rear.y,rear.z-1.5*Math.cos(pitch));
  var tip=elbow.add(hand.subtract(elbow).scale(8.0/7));
  return new Joints(shoulder,elbow,tip);
 }
 public static Vec3 hand(double age,float pitch){
  var j=joints(age,pitch);var p=j.elbow().lerp(j.tip(),7.0/8);
  return new Vec3(-p.x/16,1.5-p.y/16,-p.z/16);
 }
}
