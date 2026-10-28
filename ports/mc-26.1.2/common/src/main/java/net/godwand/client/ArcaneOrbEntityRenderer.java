package net.godwand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.godwand.entity.ArcaneOrbEntity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class ArcaneOrbEntityRenderer extends EntityRenderer<ArcaneOrbEntity, ArcaneOrbEntityRenderer.State> {

    public ArcaneOrbEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static final class State extends net.minecraft.client.renderer.entity.state.EntityRenderState {
        int chargeLevel;
        Vec3 velocity = Vec3.ZERO;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(ArcaneOrbEntity entity, State state, float delta) {
        super.extractRenderState(entity, state, delta);
        state.chargeLevel = entity.getChargeLevel();
        state.velocity = entity.getDeltaMovement();
    }

    @Override
    public void submit(State state, PoseStack matrices, net.minecraft.client.renderer.SubmitNodeCollector collector,
                       net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        float time = state.ageInTicks;
        int level = state.chargeLevel;
        float chargeRatio = level / 3.0f;
        // Kích thước viên đạn khi bay tương ứng với kích thước sau khi nạp (0.21m -> 0.31m)
        float baseScale = 0.16f + level * 0.05f;

        matrices.pushPose();
        matrices.translate(0.0f, 0.15f, 0.0f);

        Vec3 vel = state.velocity;
        if (vel.lengthSqr() > 0.001) {
            double horizontalDist = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            float velYaw = (float) (Mth.atan2(vel.x, vel.z) * (180.0 / Math.PI));
            float velPitch = (float) (Mth.atan2(vel.y, horizontalDist) * (180.0 / Math.PI));
            matrices.rotate(Axis.YP.rotationDegrees(velYaw));
            matrices.rotate(Axis.XN.rotationDegrees(velPitch));
        }

        // Xoáy nòng quanh trục tiến khi đang bay
        matrices.rotate(Axis.ZP.rotationDegrees(time * 65.0f));

        ArcaneOrbModel.render(matrices.last().pose(), baseScale, time, chargeRatio);

        matrices.popPose();

        super.submit(state, matrices, collector, camera);
    }
}
