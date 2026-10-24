package net.godwand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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

            // Muzzle position 1.35m in front of eyes, slightly lowered by 0.12m matching wand aim
            Vec3 orbCenter = eyePos.add(look.scale(1.35)).add(0, -0.12, 0);
            Vec3 renderPos = orbCenter.subtract(camPos);

            matrices.pushPose();
            matrices.translate(renderPos.x, renderPos.y, renderPos.z);

            // Align rotation with player aim
            float yaw = player.getViewYRot(tickDelta);
            float pitch = player.getViewXRot(tickDelta);
            matrices.mulPose(Axis.YP.rotationDegrees(-yaw));
            matrices.mulPose(Axis.XP.rotationDegrees(pitch));

            // Charge ratio reaches max 1.0 at 45 ticks (Level 3 Calamity)
            float chargeRatio = Math.min(1.0f, animTime / 45.0f);
            float pulse = (float) Math.sin(animTime * 0.45f) * (0.015f + chargeRatio * 0.025f);
            float baseScale = 0.20f + chargeRatio * 0.35f + pulse;

            ArcaneOrbModel.render(matrices.last().pose(), baseScale, animTime, chargeRatio);

            matrices.popPose();
        }
    }
}
