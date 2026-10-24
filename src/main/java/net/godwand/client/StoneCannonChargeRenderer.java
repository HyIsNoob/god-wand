package net.godwand.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.godwand.item.GodWandItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * 3D Client Charging Drill & Aerodynamic Wind Ribbon Vortex Renderer for Skill 5 (Stone Cannon).
 * - Renders a visible pointed rotating stone drill in front of the player's chest (1.85m forward) during charging.
 * - Drill expands, densifies, and gains high-speed rifling rotation across Charge Levels I to IV.
 * - Replaces vanilla particle puffs with a gold energy helix hugging the stone rifling.
 */
public class StoneCannonChargeRenderer {

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return;

        float tickDelta = context.tickCounter().getTickDelta(true);
        Vec3d camPos = context.camera().getPos();

        for (PlayerEntity player : client.world.getPlayers()) {
            if (!player.isUsingItem()) continue;

            ItemStack activeStack = player.getActiveItem();
            if (!(activeStack.getItem() instanceof GodWandItem)) continue;
            if (GodWandItem.getSkillMode(activeStack) != GodWandItem.MODE_STONE_CANNON) continue;

            int useTicks = player.getItemUseTime();
            float animTime = useTicks + tickDelta;

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
            float spinAngle = StoneCannonModel.chargeDrillRotation(animTime);


            // Kích thước mũi khoan to dần và đặc hơn khi tụ lực (0.22m -> 0.62m)
            float chargeRatio = Math.min(1.0f, animTime / 80.0f);
            float baseScale = 0.22f + chargeRatio * 0.40f;

            StoneCannonModel.renderChargeWind(matrices.peek().getPositionMatrix(), baseScale, animTime, chargeRatio);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spinAngle));
            StoneCannonModel.render(matrices.peek().getPositionMatrix(), baseScale, animTime, 0.4f + chargeRatio * 0.6f);

            matrices.pop();
        }
    }
}
