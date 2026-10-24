package net.godwand.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.godwand.client.GodWandCinematicEngine;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            shift = At.Shift.AFTER))
    private void godwand$afterWorldComposite(CallbackInfo ci) {
        net.godwand.client.GodWandPostFxManager.renderAfterWorld();
    }

    /**
     * Bơm Trauma Screen Shake trực tiếp vào ma trận góc nhìn Camera
     */
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"))
    private void godwand$applyScreenShake(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
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
