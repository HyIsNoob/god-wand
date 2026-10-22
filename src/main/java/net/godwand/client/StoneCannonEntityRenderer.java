package net.godwand.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.godwand.entity.StoneCannonProjectileEntity;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * 3D Aerodynamic Rifling Model & Supersonic Wind Ribbon Trail Renderer for StoneCannonProjectileEntity.
 * Renders the 3-part rifling projectile with continuous rotation and 3 intertwined 3D aerodynamic
 * wind ribbons trailing behind the projectile, replacing vanilla particle puffs.
 */
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
        int level = entity.getChargeLevel();
        float spin = time * (50.0f + level * 20.0f); // Tốc độ xoáy nòng siêu thanh

        float baseScale = 0.38f + level * 0.14f;

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
        var tipBlock = (level >= 3) ? Blocks.DIAMOND_BLOCK.getDefaultState() : Blocks.DEEPSLATE_TILES.getDefaultState();
        blockRenderManager.renderBlockAsEntity(
                tipBlock,
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

        // 4. DẢI RIBBON KHÍ ĐỘNG HỌC CUỘN XOẮN PHÍA SAU ĐUÔI ĐẠN (3D Supersonic Ribbon Slipstream)
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Matrix4f mat = matrices.peek().getPositionMatrix();
        int ribbonArms = 3;
        int trailSteps = 24;
        float trailLength = 5.5f + level * 2.2f;

        int rCol = (level == 4) ? 255 : 210;
        int gCol = (level == 4) ? 245 : 245;
        int bCol = 255;

        for (int arm = 0; arm < ribbonArms; arm++) {
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

            float armPhase = arm * ((float) Math.PI * 2.0f / (float) ribbonArms);

            for (int s = 0; s <= trailSteps; s++) {
                float progress = (float) s / (float) trailSteps; // 0.0 (ngay sau đế) -> 1.0 (đuôi xa)
                float z = -0.45f * baseScale - progress * trailLength;
                float r = (0.12f + progress * 0.35f) * (0.85f + level * 0.15f);
                float theta = armPhase + progress * ((float) Math.PI * 4.2f) - (time * 0.25f);

                float cx = r * (float) Math.cos(theta);
                float cy = r * (float) Math.sin(theta);

                float w = (0.04f + progress * 0.06f);
                float nx = -(float) Math.sin(theta) * w * 0.5f;
                float ny = (float) Math.cos(theta) * w * 0.5f;

                float alpha = (1.0f - progress) * (0.75f + level * 0.08f);
                int a = Math.clamp((int) (alpha * 255.0f), 0, 255);

                buffer.vertex(mat, cx - nx, cy - ny, z).color(rCol, gCol, bCol, a);
                buffer.vertex(mat, cx + nx, cy + ny, z).color(rCol, gCol, bCol, a);
            }

            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();

        matrices.pop();

        super.render(entity, entityYaw, tickDelta, matrices, vertexConsumers, light);
    }
}
