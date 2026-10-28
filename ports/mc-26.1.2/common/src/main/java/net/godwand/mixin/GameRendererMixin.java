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

@Mixin(value = GameRenderer.class, remap = false)
public abstract class GameRendererMixin {
    @org.spongepowered.asm.mixin.Unique
    private org.joml.Matrix4f godwand$worldProjection;

    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"), index = 0)
    private org.joml.Matrix4f godwand$captureProjection(org.joml.Matrix4f matrix) {
        godwand$worldProjection = new org.joml.Matrix4f(matrix);
        return matrix;
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V",
            shift = At.Shift.AFTER))
    private void godwand$afterWorldComposite(CallbackInfo ci) {
        var state = ((GameRenderer)(Object)this).getGameRenderState().levelRenderState;
        var camera = state.cameraRenderState;
        float partialTicks = net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        net.godwand.client.GodWandPostFxManager.onWorldRender(camera.viewRotationMatrix, godwand$worldProjection, partialTicks);
        net.godwand.client.AtomicPostFx.capture(camera.viewRotationMatrix, godwand$worldProjection, partialTicks);
        net.godwand.client.GodWandPostFxManager.renderAfterWorld();
        net.godwand.client.AtomicPostFx.renderAfterWorld();
    }

    /**
     * Bơm Trauma Screen Shake trực tiếp vào ma trận góc nhìn Camera
     */
    @Inject(method = "bobHurt", at = @At("HEAD"))
    private void godwand$applyScreenShake(net.minecraft.client.renderer.state.level.CameraRenderState camera, PoseStack matrices, CallbackInfo ci) {
        float partialTicks = net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        GodWandCinematicEngine.applyScreenShake(matrices, partialTicks);
        net.godwand.client.AtomicCinematic.applyShake(matrices, partialTicks);
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void godwand$atomicHideHand(net.minecraft.client.renderer.state.level.CameraRenderState camera, float partialTicks, org.joml.Matrix4fc matrix, CallbackInfo ci) {
        if (net.godwand.client.AtomicCinematic.active()) ci.cancel();
    }
}

