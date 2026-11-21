package net.godwand.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Puts a cast's pose back after every other mod has animated the player (see CastPoseLock). A high priority applies this mixin late, so its tail
 *  callback runs after theirs. */
@Mixin(value=PlayerModel.class,priority=2000)
public abstract class CastPoseLastMixin {
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void godwand$castPoseLast(LivingEntity entity,float walk,float stride,float time,float yaw,float pitch,CallbackInfo ci) {
        var m=(PlayerModel<?>)(Object)this;
        if(!net.godwand.client.CastPoseLock.restore(m.rightArm,m.leftArm,m.body,m.head,m.rightLeg,m.leftLeg))return;
        m.hat.copyFrom(m.head);
        m.leftSleeve.copyFrom(m.leftArm);m.rightSleeve.copyFrom(m.rightArm);m.jacket.copyFrom(m.body);
        m.leftPants.copyFrom(m.leftLeg);m.rightPants.copyFrom(m.rightLeg);
    }
}
