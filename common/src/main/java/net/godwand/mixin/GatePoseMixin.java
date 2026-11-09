package net.godwand.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Silent Gate caster performance. A separate mixin so the Atomic/Fuga pose mixin and its adapters stay untouched. */
@Mixin(HumanoidModel.class)
public abstract class GatePoseMixin {
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart body;
    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart hat;
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void godwand$gatePose(LivingEntity entity,float walk,float stride,float time,float yaw,float pitch,CallbackInfo ci) {
        if(net.godwand.client.GatePose.apply(entity.getId(),net.minecraft.util.Mth.clamp(time-entity.tickCount,0,1),rightArm,leftArm,body,head))hat.copyFrom(head);
    }
}
