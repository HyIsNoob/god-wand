package net.godwand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.godwand.item.GodWandItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 3D Client Charging Drill & Aerodynamic Wind Ribbon Vortex Renderer for Skill 5 (Stone Cannon).
 * - Renders a visible pointed rotating stone drill in front of the player's chest (1.85m forward) during charging.
 * - Drill expands, densifies, and gains high-speed rifling rotation across Charge Levels I to IV.
 * - Replaces vanilla particle puffs with a gold energy helix hugging the stone rifling.
 */
public class StoneCannonChargeRenderer {

    public static void render(PoseStack matrices, Camera cameraObj, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;

        Vec3 camPos = cameraObj.position();

        for (Player player : client.level.players()) {
            if (!player.isUsingItem()) continue;

            ItemStack activeStack = player.getUseItem();
            if (!(activeStack.getItem() instanceof GodWandItem)) continue;
            if (GodWandItem.getSkillMode(activeStack) != GodWandItem.MODE_STONE_CANNON) continue;

            int useTicks = player.getTicksUsingItem();
            float animTime = useTicks + tickDelta;

            Vec3 look = player.getViewVector(tickDelta);
            Vec3 eyePos = player.getEyePosition(tickDelta);

            // Tọa độ mũi khoan: 1.85m ngay trước ngực/tầm mắt người chơi
            Vec3 drillCenter = eyePos.add(look.scale(1.85)).add(0, -0.15, 0);
            Vec3 renderPos = drillCenter.subtract(camPos);

                        matrices.pushPose();
            matrices.translate(renderPos.x, renderPos.y, renderPos.z);

            // Căn chỉnh trục mũi khoan theo hướng nhìn của người chơi
            float yaw = player.getViewYRot(tickDelta);
            float pitch = player.getViewXRot(tickDelta);
            matrices.mulPose(Axis.YP.rotationDegrees(-yaw));
            matrices.mulPose(Axis.XP.rotationDegrees(pitch));

            // Tốc độ xoáy nòng siêu thanh tăng dần theo từng cấp độ nạp
            float spinAngle = StoneCannonModel.chargeDrillRotation(animTime);


            // Kích thước mũi khoan to dần và đặc hơn khi tụ lực (0.22m -> 0.62m)
            float chargeRatio = Math.min(1.0f, animTime / 80.0f);
            float baseScale = 0.22f + chargeRatio * 0.40f;

            StoneCannonModel.renderChargeWind(matrices.last().pose(), baseScale, animTime, chargeRatio);
            matrices.mulPose(Axis.ZP.rotationDegrees(spinAngle));
            StoneCannonModel.render(matrices.last().pose(), baseScale, animTime, 0.4f + chargeRatio * 0.6f);

            matrices.popPose();
        }
    }
}

