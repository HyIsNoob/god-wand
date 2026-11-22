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
 * Renders DominionBlockProjectileEntity as a spinning full block with custom GLSL shader
 * golden plasma slipstream ribbons and Mach shock rings.
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
        float scale = entity.isVolley() ? 0.86f : 0.78f;

        matrices.pushPose();
        matrices.mulPose(Axis.YP.rotationDegrees(time * 38f));
        matrices.mulPose(Axis.XP.rotationDegrees(time * 27f));
        matrices.scale(scale, scale, scale);
        matrices.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                entity.getBlockState(), matrices, vertexConsumers, light, OverlayTexture.NO_OVERLAY);
        matrices.popPose();

        // Custom GLSL plasma slipstream & shock collar along flight vector
        Vec3 vel = entity.getDeltaMovement();
        if (vel.lengthSqr() > 0.01) {
            Vec3 dir = vel.normalize();
            double length = entity.isVolley() ? 4.4 : 3.2;
            int rCol = 255;
            int gCol = entity.isVolley() ? 222 : 210;
            int bCol = entity.isVolley() ? 122 : 138;

            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            DominionShader.bind(time / 20.0f);
            try {
                var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                Matrix4f mat = matrices.last().pose();
                Vec3 tail = dir.scale(-length);
                Vec3 head = dir.scale(0.35);
                DominionShader.emitBeam(buffer, mat, tail, head, entity.isVolley() ? 0.34 : 0.24,
                        entity.getId() & 31, rCol, gCol, bCol, 225);
                DominionShader.emitDisc(buffer, mat, dir.scale(0.15), dir, entity.isVolley() ? 1.15 : 0.85,
                        DominionShader.FX_MACH_SHOCK, (entity.getId() + 7) & 31, 255, 242, 185, 195);
                var mesh = buffer.build();
                if (mesh != null) BufferUploader.drawWithShader(mesh);
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
