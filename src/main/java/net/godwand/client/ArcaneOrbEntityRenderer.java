package net.godwand.client;

import net.godwand.entity.ArcaneOrbEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public class ArcaneOrbEntityRenderer extends EntityRenderer<ArcaneOrbEntity> {

    public ArcaneOrbEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(ArcaneOrbEntity entity) {
        return Identifier.ofVanilla("textures/atlas/blocks.png");
    }

    @Override
    public void render(ArcaneOrbEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        float time = entity.age + tickDelta;
        int level = entity.getChargeLevel();
        float chargeRatio = level / 3.0f;
        float baseScale = 0.32f + level * 0.14f;

        matrices.push();
        matrices.translate(0.0f, 0.15f, 0.0f);

        Vec3d vel = entity.getVelocity();
        if (vel.lengthSquared() > 0.001) {
            double horizontalDist = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            float velYaw = (float) (MathHelper.atan2(vel.x, vel.z) * (180.0 / Math.PI));
            float velPitch = (float) (MathHelper.atan2(vel.y, horizontalDist) * (180.0 / Math.PI));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(velYaw));
            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(velPitch));
        }

        ArcaneOrbModel.render(matrices.peek().getPositionMatrix(), baseScale, time, chargeRatio);

        matrices.pop();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }
}
