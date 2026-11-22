package net.godwand.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.godwand.entity.DominionBlockProjectileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Renders DominionBlockProjectileEntity as a spinning full block with a crisp kinetic slipstream trail. */
public class DominionBlockEntityRenderer extends EntityRenderer<DominionBlockProjectileEntity> {
    public DominionBlockEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(DominionBlockProjectileEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png");
    }

    @Override
    public void render(DominionBlockProjectileEntity entity, float entityYaw, float tickDelta, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light) {
        float time = entity.tickCount + tickDelta;
        float scale = entity.isVolley() ? 0.85f : 0.78f;

        matrices.pushPose();
        matrices.mulPose(Axis.YP.rotationDegrees(time * 38f));
        matrices.mulPose(Axis.XP.rotationDegrees(time * 27f));
        matrices.scale(scale, scale, scale);
        matrices.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                entity.getBlockState(), matrices, vertexConsumers, light, OverlayTexture.NO_OVERLAY);
        matrices.popPose();

        // Kinetic slipstream ribbon along the flight vector
        Vec3 vel = entity.getDeltaMovement();
        if (vel.lengthSqr() > 0.01) {
            double horiz = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            float yaw = (float) (Mth.atan2(vel.x, vel.z) * (180.0 / Math.PI));
            float pitch = (float) (Mth.atan2(vel.y, horiz) * (180.0 / Math.PI));
            matrices.pushPose();
            matrices.mulPose(Axis.YP.rotationDegrees(yaw));
            matrices.mulPose(Axis.XN.rotationDegrees(pitch));
            matrices.mulPose(Axis.ZP.rotationDegrees(time * 42f));

            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            Matrix4f mat = matrices.last().pose();
            float length = entity.isVolley() ? 3.8f : 2.8f;
            int rCol = entity.isVolley() ? 255 : 235;
            int gCol = entity.isVolley() ? 214 : 225;
            int bCol = entity.isVolley() ? 110 : 185;
            for (int arm = 0; arm < 2; arm++) {
                BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
                float basePhase = arm * (float) Math.PI;
                for (int s = 0; s <= 12; s++) {
                    float p = s / 12f;
                    float z = -0.35f - p * length;
                    float rad = 0.22f + p * 0.22f;
                    float theta = basePhase + p * 5.0f;
                    float cx = rad * (float) Math.cos(theta);
                    float cy = rad * (float) Math.sin(theta);
                    float w = 0.03f * (1f - p * 0.5f);
                    float nx = -(float) Math.sin(theta) * w;
                    float ny = (float) Math.cos(theta) * w;
                    int alpha = Math.clamp((int) ((1f - p) * 165f), 0, 255);
                    buffer.addVertex(mat, cx - nx, cy - ny, z).setColor(rCol, gCol, bCol, alpha);
                    buffer.addVertex(mat, cx + nx, cy + ny, z).setColor(rCol, gCol, bCol, alpha);
                }
                BufferUploader.drawWithShader(buffer.buildOrThrow());
            }

            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            matrices.popPose();
        }

        super.render(entity, entityYaw, tickDelta, matrices, vertexConsumers, light);
    }
}
