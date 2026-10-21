package net.godwand.client;

import net.godwand.entity.ArcaneOrbEntity;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public class ArcaneOrbEntityRenderer extends EntityRenderer<ArcaneOrbEntity> {
    private final BlockRenderManager blockRenderManager;

    public ArcaneOrbEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();
    }

    @Override
    public Identifier getTexture(ArcaneOrbEntity entity) {
        return Identifier.ofVanilla("textures/atlas/blocks.png");
    }

    @Override
    public void render(ArcaneOrbEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        float time = entity.age + tickDelta;
        float baseScale = 0.45f + entity.getChargeLevel() * 0.18f;

        // 1. Lõi năng lượng xoay tốc độ cao
        matrices.push();
        matrices.translate(0.0f, 0.25f, 0.0f);
        matrices.scale(baseScale, baseScale, baseScale);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 18.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(time * 14.0f));
        matrices.translate(-0.5f, -0.5f, -0.5f);
        blockRenderManager.renderBlockAsEntity(
                Blocks.SEA_LANTERN.getDefaultState(),
                matrices, vertexConsumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV
        );
        matrices.pop();

        // 2. Lớp vỏ ma pháp xoay ngược chiều
        matrices.push();
        matrices.translate(0.0f, 0.25f, 0.0f);
        float outerScale = baseScale * 1.45f;
        matrices.scale(outerScale, outerScale, outerScale);
        matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(time * 12.0f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(time * 16.0f));
        matrices.translate(-0.5f, -0.5f, -0.5f);
        blockRenderManager.renderBlockAsEntity(
                Blocks.CYAN_STAINED_GLASS.getDefaultState(),
                matrices, vertexConsumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV
        );
        matrices.pop();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }
}
