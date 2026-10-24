package net.godwand.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.godwand.item.GodWandItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public class ArcaneOrbChargeRenderer {

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return;

        float tickDelta = context.tickCounter().getTickDelta(true);
        Vec3d camPos = context.camera().getPos();

        for (PlayerEntity player : client.world.getPlayers()) {
            if (!player.isUsingItem()) continue;

            ItemStack activeStack = player.getActiveItem();
            if (!(activeStack.getItem() instanceof GodWandItem)) continue;
            if (GodWandItem.getSkillMode(activeStack) != GodWandItem.MODE_ARCANE_ORB) continue;

            int useTicks = player.getItemUseTime();
            float animTime = useTicks + tickDelta;

            Vec3d look = player.getRotationVec(tickDelta);
            Vec3d eyePos = player.getCameraPosVec(tickDelta);

            // Toạ độ quả cầu: Đẩy ra xa tầm nhìn (2.15m trước mắt, hạ 0.16m theo hướng đầu trượng)
            Vec3d orbCenter = eyePos.add(look.multiply(2.15)).add(0, -0.16, 0);
            Vec3d renderPos = orbCenter.subtract(camPos);

            float chargeRatio = Math.min(1.0f, animTime / 45.0f);

            // Hạt năng lượng từ bên ngoài hút xoáy vào tâm quả cầu trên client
            if (client.world != null) {
                int particleCount = 2 + (int) (chargeRatio * 3);
                for (int i = 0; i < particleCount; i++) {
                    double theta = Math.random() * Math.PI * 2.0;
                    double phi = (Math.random() - 0.5) * Math.PI;
                    double dist = 0.50 + Math.random() * 0.65;
                    double px = orbCenter.x + dist * Math.cos(phi) * Math.cos(theta);
                    double py = orbCenter.y + dist * Math.sin(phi);
                    double pz = orbCenter.z + dist * Math.cos(phi) * Math.sin(theta);

                    Vec3d dir = orbCenter.subtract(new Vec3d(px, py, pz)).normalize();
                    double speed = 0.10 + chargeRatio * 0.18;

                    client.world.addParticle(
                            new DustParticleEffect(new Vector3f(0.0f, 0.94f, 1.0f), 0.70f),
                            px, py, pz,
                            dir.x * speed, dir.y * speed, dir.z * speed
                    );
                    if (Math.random() < 0.45) {
                        client.world.addParticle(
                                ParticleTypes.REVERSE_PORTAL,
                                px, py, pz,
                                dir.x * speed, dir.y * speed, dir.z * speed
                        );
                    }
                }
            }

            MatrixStack matrices = context.matrixStack();
            matrices.push();
            matrices.translate(renderPos.x, renderPos.y, renderPos.z);

            // Căn chỉnh trục theo hướng nhìn của người chơi
            float yaw = player.getYaw(tickDelta);
            float pitch = player.getPitch(tickDelta);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));

            // Toàn bộ quả cầu xoay vòng vòng quanh trục tiến, tăng tốc cực nhanh khi nạp nhiều
            float spinRamp = chargeRatio * chargeRatio;
            float spinAngle = animTime * (25.0f + spinRamp * 160.0f);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spinAngle));

            // Kích thước nhỏ gọn tinh tế (0.11m -> 0.25m) vừa vặn như viên đạn năng lượng
            float pulse = (float) Math.sin(animTime * 0.55f) * (0.008f + chargeRatio * 0.015f);
            float baseScale = 0.11f + chargeRatio * 0.14f + pulse;

            ArcaneOrbModel.render(matrices.peek().getPositionMatrix(), baseScale, animTime, chargeRatio);

            matrices.pop();
        }
    }
}
