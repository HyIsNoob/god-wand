package net.godwand.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.godwand.client.BlockDominionPose;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies smooth 3D aerodynamic body pitch & bank roll during Block Wings Glide/Hover flight. */
@Mixin(LivingEntityRenderer.class)
public abstract class BlockDominionFlightRenderMixin {
    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void godwand$dominionFlightRotations(LivingEntity entity, PoseStack matrices, float time,
                                                 float yaw, float partial, float scale, CallbackInfo ci) {
        BlockDominionPose.applyBodyRotations(entity, matrices, partial);
    }
}
