package net.godwand.mixin;

import net.godwand.client.BlockDominionPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Overrides walking swing with aerodynamic Glide, Hover, and Volley/Meteor summoning poses for Block Dominion. */
@Mixin(HumanoidModel.class)
public abstract class BlockDominionPoseMixin {
    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart hat;
    @Shadow @Final public ModelPart body;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftLeg;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void godwand$dominionPose(LivingEntity entity, float walk, float stride, float time,
                                      float yaw, float pitch, CallbackInfo ci) {
        float partial = Mth.clamp(time - entity.tickCount, 0f, 1f);
        BlockDominionPose.applyLimbPose(entity, partial, head, body, rightArm, leftArm, rightLeg, leftLeg, hat);
    }
}
