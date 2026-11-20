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

/** Blue/Red caster arm in third person. Its own mixin so the other pose mixins and their version adapters stay untouched. */
@Mixin(HumanoidModel.class)
public abstract class BlueRedPoseMixin {
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart head;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void godwand$blueRedPose(LivingEntity entity, float walk, float stride, float time, float yaw, float pitch, CallbackInfo ci) {
        net.godwand.client.BlueRedHand.thirdPerson(entity, net.minecraft.util.Mth.clamp(time - entity.tickCount, 0, 1), rightArm, leftArm, head);
    }
}
