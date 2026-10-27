package net.godwand.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.godwand.client.AtomicRenderer;
import net.godwand.skill.AtomicSequence;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class AtomicFacingMixin {
    @Inject(method="setupRotations",at=@At("TAIL"))
    private void godwand$atomicFaceCamera(LivingEntity entity,PoseStack matrices,float time,float yaw,float partial,float scale,CallbackInfo ci) {
        var frame=AtomicRenderer.frameFor(entity.getId(),partial);
        if(frame==null)return;
        // Rotate only the rendered model; mouse view and server rotation stay under player control.
        matrices.mulPose(Axis.YP.rotationDegrees(Mth.wrapDegrees(yaw-frame.yaw())*AtomicSequence.facing(frame.age())));
    }
}
