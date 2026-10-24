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
        return ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png");
    }

    @Override
    public void render(ArcaneOrbEntity entity, float yaw, float tickDelta, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light) {
        float time = entity.tickCount + tickDelta;
        int level = entity.getChargeLevel();
        float chargeRatio = level / 3.0f;
        float baseScale = 0.32f + level * 0.14f;

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

        ArcaneOrbModel.render(matrices.last().pose(), baseScale, time, chargeRatio);

        matrices.popPose();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }
}
