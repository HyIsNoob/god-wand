package net.godwand.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.godwand.client.AtomicPoseFrames;
import net.godwand.skill.AtomicSequence;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=LivingEntityRenderer.class,remap=false)
public abstract class AtomicFacingMixin {
 @Inject(method="setupRotations",at=@At("TAIL"))
 private void godwand$face(LivingEntityRenderState state,PoseStack pose,float yaw,float scale,CallbackInfo ci) {
  if(!(state instanceof HumanoidRenderState humanoid))return;
  var extracted=AtomicPoseFrames.get(humanoid);
  if(extracted==null||extracted.frame()==null)return;
  var frame=extracted.frame();pose.mulPose(Axis.YP.rotationDegrees(Mth.wrapDegrees(yaw-frame.yaw())*AtomicSequence.facing(frame.age())));
 }
}

