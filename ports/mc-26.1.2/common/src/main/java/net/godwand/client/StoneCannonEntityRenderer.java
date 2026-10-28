package net.godwand.client;

import net.godwand.client.compat.GodWandGeometry;


import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;

import net.godwand.client.compat.PrimitiveTopology;
import com.mojang.math.Axis;
import net.godwand.entity.StoneCannonProjectileEntity;
import net.minecraft.client.renderer.GameRenderer;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 3D Aerodynamic Rifling Model & Supersonic Wind Ribbon Trail Renderer for StoneCannonProjectileEntity.
 * Renders the pointed fluted stone projectile with continuous rotation and 3 intertwined 3D aerodynamic
 * wind ribbons trailing behind the projectile, replacing vanilla particle puffs.
 */
public class StoneCannonEntityRenderer extends EntityRenderer<StoneCannonProjectileEntity, StoneCannonEntityRenderer.State> {

    public StoneCannonEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static final class State extends net.minecraft.client.renderer.entity.state.EntityRenderState {
        int chargeLevel;
        Vec3 velocity = Vec3.ZERO;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(StoneCannonProjectileEntity entity, State state, float delta) {
        super.extractRenderState(entity, state, delta);
        state.chargeLevel = entity.getChargeLevel();
        state.velocity = entity.getDeltaMovement();
    }

    @Override
    public void submit(State state, PoseStack matrices, net.minecraft.client.renderer.SubmitNodeCollector collector,
                       net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        Vec3 vel = state.velocity;
        double horizontalDist = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (vel.lengthSqr() < 0.001) {
            super.submit(state, matrices, collector, camera);
            return;
        }

        float yaw = (float) (Mth.atan2(vel.x, vel.z) * (180.0 / Math.PI));
        float pitch = (float) (Mth.atan2(vel.y, horizontalDist) * (180.0 / Math.PI));
        float time = state.ageInTicks;
        int level = state.chargeLevel;
        float spin = time * (50.0f + level * 20.0f); // Tốc độ xoáy nòng siêu thanh

        float baseScale = 0.38f + level * 0.14f;

        matrices.pushPose();
        // Căn chỉnh trục mũi khoan theo vector vận tốc
        matrices.mulPose(Axis.YP.rotationDegrees(yaw));
        matrices.mulPose(Axis.XN.rotationDegrees(pitch));

        // Xoáy dọc theo trục tiến (Rifling spin around Z axis)
        matrices.mulPose(Axis.ZP.rotationDegrees(spin));

        StoneCannonModel.render(matrices.last().pose(), baseScale, time, 0.45f);

        GodWandGeometry.enableBlend();
        GodWandGeometry.additiveBlend();
        GodWandGeometry.disableCull();
        GodWandGeometry.depthMask(false);
        GodWandGeometry.colored();

        Matrix4f mat = matrices.last().pose();
        int ribbonArms = 3;
        int trailSteps = 24;
        float trailLength = 5.5f + level * 2.2f;

        int rCol = 245;
        int gCol = 250;
        int bCol = 255;

        for (int arm = 0; arm < ribbonArms; arm++) {

            BufferBuilder buffer = GodWandGeometry.begin(PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

            float armPhase = arm * ((float) Math.PI * 2.0f / (float) ribbonArms);

            for (int s = 0; s <= trailSteps; s++) {
                float progress = (float) s / (float) trailSteps; // 0.0 (ngay sau đế) -> 1.0 (đuôi xa)
                float z = -0.45f * baseScale - progress * trailLength;
                float r = (0.12f + progress * 0.35f) * (0.85f + level * 0.15f);
                float theta = armPhase + progress * ((float) Math.PI * 4.2f) - (time * 0.25f);

                float cx = r * (float) Math.cos(theta);
                float cy = r * (float) Math.sin(theta);

                float w = (0.018f + progress * 0.025f);
                float nx = -(float) Math.sin(theta) * w * 0.5f;
                float ny = (float) Math.cos(theta) * w * 0.5f;

                float alpha = (1.0f - progress) * (0.75f + level * 0.08f);
                int a = Math.clamp((int) (alpha * 255.0f), 0, 255);

                buffer.addVertex(mat, cx - nx, cy - ny, z).setColor(rCol, gCol, bCol, a);
                buffer.addVertex(mat, cx + nx, cy + ny, z).setColor(rCol, gCol, bCol, a);
            }

            GodWandGeometry.draw(buffer.buildOrThrow());
        }

        GodWandGeometry.depthMask(true);
        GodWandGeometry.enableCull();
        GodWandGeometry.defaultBlendFunc();
        GodWandGeometry.disableBlend();

        matrices.popPose();

        super.submit(state, matrices, collector, camera);
    }
}


