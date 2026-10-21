package net.godwand.client;

import net.godwand.entity.ArcaneOrbEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class ArcaneOrbEntityRenderer extends EntityRenderer<ArcaneOrbEntity> {
    public ArcaneOrbEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(ArcaneOrbEntity entity) {
        return null;
    }

    @Override
    public void render(ArcaneOrbEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }
}
