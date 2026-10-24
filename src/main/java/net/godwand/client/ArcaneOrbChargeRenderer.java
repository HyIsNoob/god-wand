package net.godwand.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.godwand.item.GodWandItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

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

            // Muzzle position 1.35m in front of eyes, slightly lowered by 0.12m matching wand aim
            Vec3d orbCenter = eyePos.add(look.multiply(1.35)).add(0, -0.12, 0);
            Vec3d renderPos = orbCenter.subtract(camPos);

            MatrixStack matrices = context.matrixStack();
            matrices.push();
            matrices.translate(renderPos.x, renderPos.y, renderPos.z);

            // Align rotation with player aim
            float yaw = player.getYaw(tickDelta);
            float pitch = player.getPitch(tickDelta);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));

            // Charge ratio reaches max 1.0 at 45 ticks (Level 3 Calamity)
            float chargeRatio = Math.min(1.0f, animTime / 45.0f);
            float pulse = (float) Math.sin(animTime * 0.45f) * (0.015f + chargeRatio * 0.025f);
            float baseScale = 0.20f + chargeRatio * 0.35f + pulse;

            ArcaneOrbModel.render(matrices.peek().getPositionMatrix(), baseScale, animTime, chargeRatio);

            matrices.pop();
        }
    }
}
