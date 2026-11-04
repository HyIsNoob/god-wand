package net.godwand.qa.mixin;
import net.godwand.client.*;
import net.godwand.qa.FugaRigProbe;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(FugaCinematic.class)
public abstract class FugaRigCameraMixin {
 @Inject(method="camera",at=@At("RETURN"),cancellable=true,remap=false)
 private static void qa$wide(float partial,Vec3 vanilla,float yaw,float pitch,CallbackInfoReturnable<AtomicCinematic.Pose> ci){
  var f=FugaRenderer.localFrame(partial);if(f==null||f.age()<200||FugaRigProbe.scenario!=1&&FugaRigProbe.scenario!=3)return;
  var player=Minecraft.getInstance().player;var p=player.getPosition(partial);
  var forward=Vec3.directionFromRotation(0,player.getViewYRot(partial));var right=forward.cross(new Vec3(0,1,0));
  var subject=p.add(0,1.30,0).add(forward.scale(.25));
  double angle=f.age()<240?.60:f.age()<280?Math.PI/2:f.age()<320?-Math.PI/2:f.age()<360?2.40:0;
  var position=p.add(forward.scale(Math.cos(angle)*1.95)).add(right.scale(Math.sin(angle)*1.95)).add(0,1.55,0);var d=subject.subtract(position);
  ci.setReturnValue(new AtomicCinematic.Pose(position,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)))));
 }
 @Inject(method="fov",at=@At("RETURN"),cancellable=true,remap=false)
 private static void qa$inspectionLens(double vanilla,float partial,CallbackInfoReturnable<Double> ci){
  var f=FugaRenderer.localFrame(partial);
  if(f!=null&&f.age()>=200&&(FugaRigProbe.scenario==1||FugaRigProbe.scenario==3))ci.setReturnValue(55.0);
 }

}
