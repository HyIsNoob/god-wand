package net.godwand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.godwand.entity.ArcaneOrbEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class ArcaneOrbEntityRenderer extends EntityRenderer<ArcaneOrbEntity> {

    public ArcaneOrbEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ArcaneOrbEntity entity) {
        return new ResourceLocation("textures/atlas/blocks.png");
    }

    @Override
    public void render(ArcaneOrbEntity entity, float yaw, float tickDelta, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light) {
        float time = entity.tickCount + tickDelta;
        int level = entity.getChargeLevel();
        float chargeRatio = level / 3.0f;
        // Kích thước viên đạn khi bay tương ứng với kích thước sau khi nạp (0.21m -> 0.31m)
        float baseScale = 0.16f + level * 0.05f;

        matrices.pushPose();
        matrices.translate(0.0f, 0.15f, 0.0f);

        Vec3 vel = entity.getDeltaMovement();
        if (vel.lengthSqr() > 0.001) {
            double horizontalDist = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            float velYaw = (float) (Mth.atan2(vel.x, vel.z) * (180.0 / Math.PI));
            float velPitch = (float) (Mth.atan2(vel.y, horizontalDist) * (180.0 / Math.PI));
            matrices.mulPose(Axis.YP.rotationDegrees(velYaw));
            matrices.mulPose(Axis.XN.rotationDegrees(velPitch));
        }

        // Xoáy nòng quanh trục tiến khi đang bay
        matrices.mulPose(Axis.ZP.rotationDegrees(time * 65.0f));

        ArcaneOrbModel.render(matrices.last().pose(), baseScale, time, chargeRatio);

        matrices.popPose();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }
}
