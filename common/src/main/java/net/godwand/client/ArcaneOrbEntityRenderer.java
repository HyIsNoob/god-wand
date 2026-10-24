package net.godwand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.godwand.entity.ArcaneOrbEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

public class ArcaneOrbEntityRenderer extends EntityRenderer<ArcaneOrbEntity> {
    private final BlockRenderDispatcher blockRenderManager;

    public ArcaneOrbEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderManager = Minecraft.getInstance().getBlockRenderer();
    }

    @Override
    public ResourceLocation getTextureLocation(ArcaneOrbEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png");
    }

    @Override
    public void render(ArcaneOrbEntity entity, float yaw, float tickDelta, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light) {
        float time = entity.tickCount + tickDelta;
        float baseScale = 0.45f + entity.getChargeLevel() * 0.18f;

        // 1. Lõi năng lượng xoay tốc độ cao
        matrices.pushPose();
        matrices.translate(0.0f, 0.25f, 0.0f);
        matrices.scale(baseScale, baseScale, baseScale);
        matrices.mulPose(Axis.YP.rotationDegrees(time * 18.0f));
        matrices.mulPose(Axis.XP.rotationDegrees(time * 14.0f));
        matrices.translate(-0.5f, -0.5f, -0.5f);
        blockRenderManager.renderSingleBlock(
                Blocks.SEA_LANTERN.defaultBlockState(),
                matrices, vertexConsumers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
        );
        matrices.popPose();

        // 2. Lớp vỏ ma pháp xoay ngược chiều
        matrices.pushPose();
        matrices.translate(0.0f, 0.25f, 0.0f);
        float outerScale = baseScale * 1.45f;
        matrices.scale(outerScale, outerScale, outerScale);
        matrices.mulPose(Axis.YN.rotationDegrees(time * 12.0f));
        matrices.mulPose(Axis.ZP.rotationDegrees(time * 16.0f));
        matrices.translate(-0.5f, -0.5f, -0.5f);
        blockRenderManager.renderSingleBlock(
                Blocks.CYAN_STAINED_GLASS.defaultBlockState(),
                matrices, vertexConsumers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
        );
        matrices.popPose();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }
}
