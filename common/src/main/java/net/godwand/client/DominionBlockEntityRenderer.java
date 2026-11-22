package net.godwand.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.godwand.entity.DominionBlockProjectileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Renders DominionBlockProjectileEntity as a spinning full block with Purple-grade
 * two-pass smoky dark-gold wake, liquid plasma comet ribbon, and crackling lightning.
 */
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
        float scale = entity.isVolley() ? 0.88f : 0.78f;

        matrices.pushPose();
        matrices.mulPose(Axis.YP.rotationDegrees(time * 38f));
        matrices.mulPose(Axis.XP.rotationDegrees(time * 27f));
        matrices.scale(scale, scale, scale);
        matrices.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                entity.getBlockState(), matrices, vertexConsumers, light, OverlayTexture.NO_OVERLAY);
        matrices.popPose();

        Vec3 vel = entity.getDeltaMovement();
        if (vel.lengthSqr() > 0.01) {
            Vec3 dir = vel.normalize();
            double length = entity.isVolley() ? 4.8 : 3.4;
            Matrix4f mat = matrices.last().pose();
            Vec3 tail = dir.scale(-length);
            Vec3 head = dir.scale(0.35);

            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            DominionShader.bind(time / 20.0f);
            try {
                // Pass 1 (BODY): dark obsidian-gold brush wake
                RenderSystem.defaultBlendFunc();
                var bodyBuf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                DominionShader.emitLiveBolt(bodyBuf, mat, tail, head, 6, 0.22, entity.isVolley() ? 0.36 : 0.26,
                        entity.getId() * 19L, time, 26, 18, 12, 165);
                var bodyMesh = bodyBuf.build();
                if (bodyMesh != null) BufferUploader.drawWithShader(bodyMesh);

                // Pass 2 (GLOW): blazing liquid plasma comet + ragged shock collar + lightning spine
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                var glowBuf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                DominionShader.emitBeam(glowBuf, mat, tail, head, entity.isVolley() ? 0.34 : 0.24,
                        entity.getId() & 31, 255, entity.isVolley() ? 224 : 210, entity.isVolley() ? 120 : 136, 215);
                DominionShader.emitLiveBolt(glowBuf, mat, tail.scale(0.85), head, 6, 0.18, 0.055,
                        entity.getId() * 19L + 7, time, 255, 242, 186, 210);
                DominionShader.emitOrganicWave(glowBuf, mat, dir.scale(0.1), dir, entity.isVolley() ? 0.95 : 0.72,
                        0.16, time, entity.getId() * 31L, DominionShader.FX_PLASMA, 255, 236, 156, 175);
                var glowMesh = glowBuf.build();
                if (glowMesh != null) BufferUploader.drawWithShader(glowMesh);
            } finally {
                RenderSystem.depthMask(true);
                RenderSystem.enableCull();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableBlend();
            }
        }

        super.render(entity, entityYaw, tickDelta, matrices, vertexConsumers, light);
    }
}
