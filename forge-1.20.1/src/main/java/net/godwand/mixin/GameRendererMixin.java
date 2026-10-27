package net.godwand.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.godwand.client.GodWandCinematicEngine;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
            shift = At.Shift.AFTER))
    private void godwand$afterWorldComposite(float tickDelta, long startTime, PoseStack matrices, CallbackInfo ci) {
        if (!net.godwand.client.GodWandPostFxManager.hasPendingFrame()) {
            net.godwand.client.GodWandPostFxManager.onWorldRender(matrices.last().pose(), com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix(), tickDelta);
        }
        net.godwand.client.GodWandPostFxManager.renderAfterWorld();
    }

    @Inject(method="renderItemInHand",at=@At("HEAD"),cancellable=true)
    private void godwand$atomicHideHand(PoseStack matrices,Camera camera,float delta,CallbackInfo ci) { if(net.godwand.client.AtomicCinematic.active())ci.cancel(); }

    /**
     * Bơm Trauma Screen Shake trực tiếp vào ma trận góc nhìn Camera
     */
    @Inject(method = "bobHurt", at = @At("HEAD"))
    private void godwand$applyScreenShake(PoseStack matrices, float tickDelta, CallbackInfo ci) {
        GodWandCinematicEngine.applyScreenShake(matrices, tickDelta);
        net.godwand.client.AtomicCinematic.applyShake(matrices,tickDelta);
    }

    /**
     * Bơm Dynamic FOV Zoom (Co khi nén lực, bung khi nổ)
     */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void godwand$applyDynamicFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        double current = cir.getReturnValue();
        double multiplier = GodWandCinematicEngine.getFovMultiplier(tickDelta);
        cir.setReturnValue(net.godwand.client.AtomicCinematic.fov(current*multiplier,tickDelta));
    }
}
