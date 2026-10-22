package net.godwand.client;

import net.godwand.entity.StoneCannonProjectileEntity;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public class StoneCannonEntityRenderer extends EntityRenderer<StoneCannonProjectileEntity> {
    private final BlockRenderManager blockRenderManager;

    public StoneCannonEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();
    }

    @Override
    public Identifier getTexture(StoneCannonProjectileEntity entity) {
        return Identifier.ofVanilla("textures/atlas/blocks.png");
    }

    @Override
    public void render(StoneCannonProjectileEntity entity, float entityYaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        Vec3d vel = entity.getVelocity();
        double horizontalDist = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (vel.lengthSquared() < 0.001) {
            super.render(entity, entityYaw, tickDelta, matrices, vertexConsumers, light);
            return;
        }

        float yaw = (float) (MathHelper.atan2(vel.x, vel.z) * (180.0 / Math.PI));
        float pitch = (float) (MathHelper.atan2(vel.y, horizontalDist) * (180.0 / Math.PI));
        float time = entity.age + tickDelta;
        float spin = time * (45.0f + entity.getChargeLevel() * 15.0f); // Tốc độ xoáy nòng siêu thanh

        float baseScale = 0.38f + entity.getChargeLevel() * 0.14f;

        matrices.push();
        // Căn chỉnh trục mũi khoan theo vector vận tốc
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(pitch));

        // Xoáy dọc theo trục tiến (Rifling spin around Z axis)
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spin));

        // 1. Phân đoạn 1: Mũi khoan nhọn phía trước (Tapered Nose Tip)
        matrices.push();
        matrices.translate(0.0f, 0.0f, 0.45f * baseScale);
        matrices.scale(baseScale * 0.75f, baseScale * 0.75f, baseScale * 1.35f);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45.0f));
        matrices.translate(-0.5f, -0.5f, -0.5f);
        blockRenderManager.renderBlockAsEntity(
                Blocks.DEEPSLATE_TILES.getDefaultState(),
                matrices, vertexConsumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV
        );
        matrices.pop();

        // 2. Phân đoạn 2: Thân trụ khoan đá nén cực đặc (Dense Compressed Core)
        matrices.push();
        matrices.translate(0.0f, 0.0f, 0.0f);
        matrices.scale(baseScale * 0.95f, baseScale * 0.95f, baseScale * 1.15f);
        matrices.translate(-0.5f, -0.5f, -0.5f);
        blockRenderManager.renderBlockAsEntity(
                Blocks.POLISHED_DEEPSLATE.getDefaultState(),
                matrices, vertexConsumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV
        );
        matrices.pop();

        // 3. Phân đoạn 3: Vành đế trợ lực khí động học phía sau (Aerodynamic Base Fin Ring)
        matrices.push();
        matrices.translate(0.0f, 0.0f, -0.42f * baseScale);
        matrices.scale(baseScale * 1.15f, baseScale * 1.15f, baseScale * 0.65f);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(22.5f));
        matrices.translate(-0.5f, -0.5f, -0.5f);
        blockRenderManager.renderBlockAsEntity(
                Blocks.SMOOTH_BASALT.getDefaultState(),
                matrices, vertexConsumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV
        );
        matrices.pop();

        matrices.pop();

        super.render(entity, entityYaw, tickDelta, matrices, vertexConsumers, light);
    }
}
