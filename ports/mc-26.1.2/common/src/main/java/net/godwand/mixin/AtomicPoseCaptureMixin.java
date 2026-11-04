package net.godwand.mixin;

import net.godwand.client.AtomicPoseFrames;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=HumanoidMobRenderer.class,remap=false)
public abstract class AtomicPoseCaptureMixin {
    @Inject(method="extractHumanoidRenderState",at=@At("TAIL"))
    private static void godwand$extractAtomicPose(LivingEntity entity,HumanoidRenderState state,float delta,
            ItemModelResolver resolver,CallbackInfo ci) {
        AtomicPoseFrames.capture(entity,state,delta);
    }
}
