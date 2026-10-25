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

    /**
     * Bơm Trauma Screen Shake trực tiếp vào ma trận góc nhìn Camera
     */
    @Inject(method = "bobHurt", at = @At("HEAD"))
    private void godwand$applyScreenShake(PoseStack matrices, float tickDelta, CallbackInfo ci) {
        GodWandCinematicEngine.applyScreenShake(matrices, tickDelta);
    }

    /**
     * Bơm Dynamic FOV Zoom (Co khi nén lực, bung khi nổ)
     */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void godwand$applyDynamicFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        double current = cir.getReturnValue();
        double multiplier = GodWandCinematicEngine.getFovMultiplier(tickDelta);
        if (Math.abs(multiplier - 1.0) > 0.0001) {
            cir.setReturnValue(current * multiplier);
        }
    }
}
