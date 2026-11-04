package net.godwand.mixin;
import net.godwand.client.*;
import net.minecraft.client.model.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Use Minecraft's actual left-hand BOW_AND_ARROW branch, irrespective of the wand's use hand. */
@Mixin(PlayerModel.class)
public abstract class FugaVanillaBowMixin {
 @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
 private void godwand$nativeBow(LivingEntity entity,float walk,float stride,float time,float yaw,float pitch,CallbackInfo ci){
  var f=FugaRenderer.frameFor(entity.getId(),Mth.clamp(time-entity.tickCount,0,1));
  if(f==null||f.phase()!=0||f.age()<144)return;
  var m=(PlayerModel<?>)(Object)this;
  var old=m.leftArmPose;
  try {
   m.leftArmPose=HumanoidModel.ArmPose.BOW_AND_ARROW;
   m.rightArm.zRot=0;m.leftArm.zRot=0;
   ((FugaBowInvoker)m).godwand$vanillaLeftBow(entity);
   AnimationUtils.bobArms(m.rightArm,m.leftArm,time);
  } finally {m.leftArmPose=old;}
  float t=Mth.clamp((float)(f.age()-144)/46,0,1);t=t*t*(3-2*t);
  var ritual=FugaPose.arms(0,143,m.head.xRot,m.head.yRot);
  m.rightArm.xRot=Mth.lerp(t,ritual.rightPitch(),m.rightArm.xRot);
  m.rightArm.yRot=Mth.lerp(t,ritual.rightYaw(),m.rightArm.yRot);
  m.rightArm.zRot=Mth.lerp(t,ritual.rightRoll(),m.rightArm.zRot);
  m.leftArm.xRot=Mth.lerp(t,ritual.leftPitch(),m.leftArm.xRot);
  m.leftArm.yRot=Mth.lerp(t,ritual.leftYaw(),m.leftArm.yRot);
  m.leftArm.zRot=Mth.lerp(t,ritual.leftRoll(),m.leftArm.zRot);
  m.rightSleeve.copyFrom(m.rightArm);m.leftSleeve.copyFrom(m.leftArm);
 }
}
