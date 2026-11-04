package net.godwand.mixin;

import com.mojang.blaze3d.vertex.*;
import net.godwand.fabric.client.FugaAimArms;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntityRenderer.class)
public abstract class FugaAimRenderMixin {
    @Redirect(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void godwand$fugaAim(EntityModel<?> model,PoseStack pose,VertexConsumer vertices,int light,int overlay,int color,
            LivingEntity entity,float yaw,float partial,PoseStack original,MultiBufferSource buffers,int packedLight) {
        FugaAimArms.render(model,entity,partial,pose,vertices,light,overlay,color);
    }
    @org.spongepowered.asm.mixin.injection.Inject(method="setupRotations",at=@At("TAIL"))
    private void godwand$aimFacing(LivingEntity entity,PoseStack pose,float time,float yaw,float partial,float scale,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if(net.godwand.client.FugaAimRig.availableFor.test(entity.getId()))
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(net.minecraft.util.Mth.wrapDegrees(yaw-entity.getViewYRot(partial))));
    }

}
