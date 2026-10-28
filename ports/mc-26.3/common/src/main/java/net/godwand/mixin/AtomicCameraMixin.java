package net.godwand.mixin;
import net.godwand.client.*;
import net.minecraft.client.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=Camera.class,remap=false)
public abstract class AtomicCameraMixin {
 @Shadow private boolean detached;
 @Shadow public abstract Vec3 position();
 @Shadow public abstract float yRot();
 @Shadow public abstract float xRot();
 @Shadow public abstract Entity entity();
 @Shadow protected abstract void setPosition(Vec3 position);
 @Shadow protected abstract void setRotation(float yaw,float pitch);
 // Apply before FOV and frustum extraction so terrain follows the cinematic camera.
 @Inject(method="update",at=@At(value="INVOKE",target="Lnet/minecraft/client/Camera;alignWithEntity(F)V",shift=At.Shift.AFTER))
 private void godwand$atomicCamera(DeltaTracker timer,CallbackInfo ci) {
  float delta=timer.getGameTimeDeltaPartialTick(false);
  var pose=AtomicCinematic.camera(delta,position(),yRot(),xRot());
  if(pose==null||entity()==null)return;
  detached=!Minecraft.getInstance().options.getCameraType().isFirstPerson()||pose.position().distanceToSqr(entity().getEyePosition(delta))>1;
  setPosition(pose.position());setRotation(pose.yaw(),pose.pitch());
 }
 @Inject(method="calculateFov",at=@At("RETURN"),cancellable=true)
 private void godwand$dynamicFov(float delta,CallbackInfoReturnable<Float> ci) {
  ci.setReturnValue((float)AtomicCinematic.fov(ci.getReturnValue()*GodWandCinematicEngine.getFovMultiplier(delta),delta));
 }
}
