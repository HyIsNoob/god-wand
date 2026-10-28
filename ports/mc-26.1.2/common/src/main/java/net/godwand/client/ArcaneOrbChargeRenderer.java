package net.godwand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.godwand.item.GodWandItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class ArcaneOrbChargeRenderer {

    public static void render(PoseStack matrices, Camera cameraObj, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;

        Vec3 camPos = cameraObj.position();

        for (Player player : client.level.players()) {
            if (!player.isUsingItem()) continue;

            ItemStack activeStack = player.getUseItem();
            if (!(activeStack.getItem() instanceof GodWandItem)) continue;
            if (GodWandItem.getSkillMode(activeStack) != GodWandItem.MODE_ARCANE_ORB) continue;

            int useTicks = player.getTicksUsingItem();
            float animTime = useTicks + tickDelta;

            Vec3 look = player.getViewVector(tickDelta);
            Vec3 eyePos = player.getEyePosition(tickDelta);

            // Toạ độ quả cầu: Đẩy ra xa tầm nhìn (2.15m trước mắt, hạ 0.16m theo hướng đầu trượng)
            Vec3 orbCenter = eyePos.add(look.scale(2.15)).add(0, -0.16, 0);
            Vec3 renderPos = orbCenter.subtract(camPos);

            float chargeRatio = Math.min(1.0f, animTime / 45.0f);

            // Hạt năng lượng từ bên ngoài hút xoáy vào tâm quả cầu trên client
            if (client.level != null) {
                int particleCount = 2 + (int) (chargeRatio * 3);
                for (int i = 0; i < particleCount; i++) {
                    double theta = Math.random() * Math.PI * 2.0;
                    double phi = (Math.random() - 0.5) * Math.PI;
                    double dist = 0.50 + Math.random() * 0.65;
                    double px = orbCenter.x + dist * Math.cos(phi) * Math.cos(theta);
                    double py = orbCenter.y + dist * Math.sin(phi);
                    double pz = orbCenter.z + dist * Math.cos(phi) * Math.sin(theta);

                    Vec3 dir = orbCenter.subtract(new Vec3(px, py, pz)).normalize();
                    double speed = 0.10 + chargeRatio * 0.18;

                    client.level.addParticle(
                            new DustParticleOptions(0x00F0FF, 0.70f),
                            px, py, pz,
                            dir.x * speed, dir.y * speed, dir.z * speed
                    );
                    if (Math.random() < 0.45) {
                        client.level.addParticle(
                                ParticleTypes.REVERSE_PORTAL,
                                px, py, pz,
                                dir.x * speed, dir.y * speed, dir.z * speed
                        );
                    }
                }
            }

            matrices.pushPose();
            matrices.translate(renderPos.x, renderPos.y, renderPos.z);

            // Căn chỉnh trục theo hướng nhìn của người chơi
            float yaw = player.getViewYRot(tickDelta);
            float pitch = player.getViewXRot(tickDelta);
            matrices.mulPose(Axis.YP.rotationDegrees(-yaw));
            matrices.mulPose(Axis.XP.rotationDegrees(pitch));

            // Toàn bộ quả cầu xoay vòng vòng quanh trục tiến, tăng tốc cực nhanh khi nạp nhiều
            float spinRamp = chargeRatio * chargeRatio;
            float spinAngle = animTime * (25.0f + spinRamp * 160.0f);
            matrices.mulPose(Axis.ZP.rotationDegrees(spinAngle));

            // Kích thước nhỏ gọn tinh tế (0.11m -> 0.25m) vừa vặn như viên đạn năng lượng
            float pulse = (float) Math.sin(animTime * 0.55f) * (0.008f + chargeRatio * 0.015f);
            float baseScale = 0.11f + chargeRatio * 0.14f + pulse;

            ArcaneOrbModel.render(matrices.last().pose(), baseScale, animTime, chargeRatio);

            matrices.popPose();
        }
    }
}

