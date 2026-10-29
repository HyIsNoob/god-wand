package net.godwand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.godwand.item.GodWandItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class ArcaneOrbChargeRenderer {

    public static void render(PoseStack matrices, Camera cameraObj, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;

        Vec3 camPos = cameraObj.getPosition();

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

            matrices.pushPose();
            matrices.translate(renderPos.x, renderPos.y, renderPos.z);

            matrices.mulPose(cameraObj.rotation());

            // Kích thước nhỏ gọn tinh tế (0.11m -> 0.25m) vừa vặn như viên đạn năng lượng
            float pulse = (float) Math.sin(animTime * 0.18f) * (0.004f + chargeRatio * 0.006f);
            float baseScale = 0.11f + chargeRatio * 0.14f + pulse;

            ArcaneOrbModel.renderCharge(matrices.last().pose(), baseScale, animTime, chargeRatio);

            matrices.popPose();
        }
    }
}
