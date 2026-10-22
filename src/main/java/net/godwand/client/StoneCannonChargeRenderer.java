package net.godwand.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.godwand.item.GodWandItem;
import net.godwand.skill.StoneCannonManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * 3D Client Charging Drill & Aerodynamic Wind Ribbon Vortex Renderer for Skill 5 (Stone Cannon).
 * - Renders a visible 3D rotating deepslate drill in front of the player's chest (1.85m forward) during charging.
 * - Drill expands, densifies, and gains high-speed rifling rotation across Charge Levels I to IV.
 * - Replaces vanilla particle puffs with 4 continuous 3D glowing additive wind ribbons converging into the drill tip.
 */
public class StoneCannonChargeRenderer {

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return;

        float tickDelta = context.tickCounter().getTickDelta(true);
        Vec3d camPos = context.camera().getPos();
        BlockRenderManager brm = client.getBlockRenderManager();
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();

        for (PlayerEntity player : client.world.getPlayers()) {
            if (!player.isUsingItem()) continue;

            ItemStack activeStack = player.getActiveItem();
            if (!(activeStack.getItem() instanceof GodWandItem)) continue;
            if (GodWandItem.getSkillMode(activeStack) != GodWandItem.MODE_STONE_CANNON) continue;

            int useTicks = player.getItemUseTime();
            float animTime = useTicks + tickDelta;
            int level = StoneCannonManager.calculateChargeLevel(useTicks);

            Vec3d look = player.getRotationVec(tickDelta);
            Vec3d eyePos = player.getCameraPosVec(tickDelta);

            // Tọa độ mũi khoan: 1.85m ngay trước ngực/tầm mắt người chơi
            Vec3d drillCenter = eyePos.add(look.multiply(1.85)).add(0, -0.15, 0);
            Vec3d renderPos = drillCenter.subtract(camPos);

            MatrixStack matrices = context.matrixStack();
            matrices.push();
            matrices.translate(renderPos.x, renderPos.y, renderPos.z);

            // Căn chỉnh trục mũi khoan theo hướng nhìn của người chơi
            float yaw = player.getYaw(tickDelta);
            float pitch = player.getPitch(tickDelta);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));

            // Tốc độ xoáy nòng siêu thanh tăng dần theo từng cấp độ nạp
            float spinSpeed = 38.0f + level * 24.0f;
            float spinAngle = animTime * spinSpeed;
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spinAngle));

            // Kích thước mũi khoan to dần và đặc hơn khi tụ lực (0.22m -> 0.62m)
            float chargeRatio = Math.min(1.0f, useTicks / 80.0f);
            float baseScale = 0.22f + chargeRatio * 0.40f;

            // 1. PHÂN ĐOẠN 1: Mũi khoan nhọn phía trước (Tapered Nose Tip)
            matrices.push();
            matrices.translate(0.0f, 0.0f, 0.45f * baseScale);
            matrices.scale(baseScale * 0.75f, baseScale * 0.75f, baseScale * 1.35f);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45.0f));
            matrices.translate(-0.5f, -0.5f, -0.5f);
            BlockState tipState = (level >= 3) ? Blocks.DIAMOND_BLOCK.getDefaultState() : Blocks.DEEPSLATE_TILES.getDefaultState();
            brm.renderBlockAsEntity(tipState, matrices, immediate, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
            matrices.pop();

            // 2. PHÂN ĐOẠN 2: Thân trụ khoan đá nén (Dense Core Shaft)
            matrices.push();
            matrices.translate(0.0f, 0.0f, 0.0f);
            matrices.scale(baseScale * 0.95f, baseScale * 0.95f, baseScale * 1.15f);
            matrices.translate(-0.5f, -0.5f, -0.5f);
            brm.renderBlockAsEntity(Blocks.POLISHED_DEEPSLATE.getDefaultState(), matrices, immediate, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
            matrices.pop();

            // 3. PHÂN ĐOẠN 3: Vành đế trợ lực khí động học (Aerodynamic Base Ring)
            matrices.push();
            matrices.translate(0.0f, 0.0f, -0.42f * baseScale);
            matrices.scale(baseScale * 1.15f, baseScale * 1.15f, baseScale * 0.65f);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(22.5f));
            matrices.translate(-0.5f, -0.5f, -0.5f);
            brm.renderBlockAsEntity(Blocks.SMOOTH_BASALT.getDefaultState(), matrices, immediate, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
            matrices.pop();

            // Flush toàn bộ các khối 3D của mũi khoan
            immediate.draw();

            // 4. HIỆU ỨNG LUỒNG GIÓ KHÍ ĐỘNG HỌC DẠNG 3D WIND RIBBONS (Thay thế hạt vanilla)
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE); // Additive glowing light
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);

            Matrix4f mat = matrices.peek().getPositionMatrix();
            int ribbonCount = 4;
            int steps = 28;

            int red = (level == 4) ? 255 : 205;
            int green = (level == 4) ? 245 : 240;
            int blue = 255;

            for (int k = 0; k < ribbonCount; k++) {
                Tessellator tessellator = Tessellator.getInstance();
                BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

                float ribbonPhase = k * ((float) Math.PI * 0.5f);

                for (int s = 0; s <= steps; s++) {
                    float t = (float) s / (float) steps; // 0.0 (vành ngoài xa) -> 1.0 (hút vào mũi)
                    float z = (1.0f - t) * (1.6f + level * 0.25f) + 0.35f * baseScale;
                    float r = 0.06f + (float) Math.pow(1.0f - t, 1.30) * (0.45f + level * 0.18f);
                    float theta = ribbonPhase + t * ((float) Math.PI * 3.5f) + (animTime * 0.18f);

                    float cx = r * (float) Math.cos(theta);
                    float cy = r * (float) Math.sin(theta);

                    // Bề rộng của dải ribbon khí động học
                    float w = 0.025f + (1.0f - t) * 0.055f;
                    float nx = -(float) Math.sin(theta) * w * 0.5f;
                    float ny = (float) Math.cos(theta) * w * 0.5f;

                    // Độ trong suốt mượt mà hình chuông
                    float alpha = (float) Math.sin(t * Math.PI) * (0.65f + level * 0.08f);
                    int a = Math.clamp((int) (alpha * 255.0f), 0, 255);

                    buffer.vertex(mat, cx - nx, cy - ny, z).color(red, green, blue, a);
                    buffer.vertex(mat, cx + nx, cy + ny, z).color(red, green, blue, a);
                }

                BufferRenderer.drawWithGlobalProgram(buffer.end());
            }

            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();

            matrices.pop();
        }
    }
}
