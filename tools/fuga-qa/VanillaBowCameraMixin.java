package net.godwand.qa.mixin;
import net.godwand.qa.VanillaBowProbe;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Camera.class)
public abstract class VanillaBowCameraMixin {
 @Shadow private boolean detached;@Shadow protected abstract void setPosition(Vec3 p);@Shadow protected abstract void setRotation(float yaw,float pitch);
 @Inject(method="setup",at=@At("RETURN"))
 private void qa$camera(net.minecraft.world.level.BlockGetter level,net.minecraft.world.entity.Entity entity,boolean third,boolean mirror,float partial,CallbackInfo ci){
  if(!VanillaBowProbe.active)return;Vec3 p=VanillaBowProbe.cameraPosition(),d=VanillaBowProbe.focus().subtract(p);detached=true;setPosition(p);setRotation((float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
 }
}
