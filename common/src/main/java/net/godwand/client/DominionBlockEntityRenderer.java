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
import net.godwand.skill.BlockDominionPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Cinematic 3D renderer for DominionBlockProjectileEntity:
 * - Main tumbling 3D block + trailing 3D motion-smear block echoes along velocity
 * - True 3D Fresnel aerodynamic Mach cone wrapping the speeding meteor (colored by its Material Resonance!)
 * - Gossamer vapor contrail ribbon (zero flat floating rings or stiff black sticks!).
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
        var blockRenderer = Minecraft.getInstance().getBlockRenderer();
        var state = entity.getBlockState();
        BlockDominionPalette pal = BlockDominionPalette.resolveStates(java.util.List.of(state));

        // 1. Main spinning 3D block
        matrices.pushPose();
        matrices.mulPose(Axis.YP.rotationDegrees(time * 38f));
        matrices.mulPose(Axis.XP.rotationDegrees(time * 27f));
        matrices.scale(scale, scale, scale);
        matrices.translate(-0.5, -0.5, -0.5);
        blockRenderer.renderSingleBlock(state, matrices, vertexConsumers, light, OverlayTexture.NO_OVERLAY);
        matrices.popPose();

        Vec3 vel = entity.getDeltaMovement();
        if (vel.lengthSqr() > 0.01) {
            Vec3 dir = vel.normalize();

            // 2. 3D Block Motion-Smear Echoes trailing behind the speeding block (pure 3D physical weight!)
            for (int echo = 1; echo <= 2; echo++) {
                double lag = echo * (entity.isVolley() ? 0.68 : 0.52);
                float echoScale = scale * (1.0f - echo * 0.24f);
                matrices.pushPose();
                matrices.translate(-dir.x * lag, -dir.y * lag, -dir.z * lag);
                matrices.mulPose(Axis.YP.rotationDegrees((time - echo * 1.4f) * 38f));
                matrices.mulPose(Axis.XP.rotationDegrees((time - echo * 1.4f) * 27f));
                matrices.scale(echoScale, echoScale, echoScale);
                matrices.translate(-0.5, -0.5, -0.5);
                blockRenderer.renderSingleBlock(state, matrices, vertexConsumers, light, OverlayTexture.NO_OVERLAY);
                matrices.popPose();
            }

            // 3. True 3D Volumetric Fresnel Mach Cone + Gossamer Vapor Wake (Material-Resonant!)
            // Under an Iris/Oculus shaderpack, queue to BlockDominionRenderer so it renders cleanly after the level pass!
            if (AtomicPostFx.shaderPackActive()) {
                BlockDominionRenderer.queueProjectileWake(entity.getPosition(tickDelta), dir, entity.isVolley(), entity.getId(), pal);
            } else {
                double length = entity.isVolley() ? 4.4 : 3.0;
                Matrix4f mat = matrices.last().pose();
                Vec3[] wakePts = new Vec3[6];
                for (int i = 0; i < 6; i++) {
                    double u = i / 5.0; // 0 = tail, 1 = block
                    wakePts[i] = dir.scale(-length * (1.0 - u) + 0.15 * u);
                }

                RenderSystem.enableBlend();
                RenderSystem.disableCull();
                RenderSystem.depthMask(false);
                DominionShader.bind(time / 20.0f);
                try {
                    // Pass 1 (BODY): soft dark-resonant dust slipstream
                    RenderSystem.defaultBlendFunc();
                    var bodyBuf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                    DominionShader.emitTrail(bodyBuf, mat, wakePts, entity.isVolley() ? 0.42 : 0.28,
                            DominionShader.FX_BRUSH, entity.getId() & 63, pal.darkR, pal.darkG, pal.darkB, 92);
                    var bodyMesh = bodyBuf.build();
                    if (bodyMesh != null) BufferUploader.drawWithShader(bodyMesh);

                    // Pass 2 (GLOW): 3D Fresnel aerodynamic Mach cone + soft resonant vapor trail
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                    var glowBuf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                    DominionShader.emitFresnelCone(glowBuf, mat, dir.scale(0.58), dir,
                            entity.isVolley() ? 2.8 : 2.0, entity.isVolley() ? 0.92 : 0.68,
                            16, 6, entity.getId() & 31, pal.primaryR, pal.primaryG, pal.primaryB, 175);
                    DominionShader.emitTrail(glowBuf, mat, wakePts, entity.isVolley() ? 0.28 : 0.18,
                            DominionShader.FX_PLASMA, (entity.getId() + 17) & 63, pal.hotR, pal.hotG, pal.hotB, 165);
                    var glowMesh = glowBuf.build();
                    if (glowMesh != null) BufferUploader.drawWithShader(glowMesh);
                } finally {
                    RenderSystem.depthMask(true);
                    RenderSystem.enableCull();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.disableBlend();
                }
            }
        }

        super.render(entity, entityYaw, tickDelta, matrices, vertexConsumers, light);
    }
}
