package net.godwand.mixin;

import net.godwand.item.GodWandItem;
import net.godwand.skill.AtomicSequence;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class AtomicPoseMixin {
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart body;
    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart hat;
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void godwand$raiseWand(LivingEntity entity,float walk,float stride,float time,float yaw,float pitch,CallbackInfo ci) {
        var stack=entity.getUseItem();
        float partial=Math.clamp(time-entity.tickCount,0,1);
        var frame=net.godwand.client.AtomicRenderer.frameFor(entity.getId(),partial);
        boolean charging=stack.getItem() instanceof GodWandItem&&GodWandItem.getSkillMode(stack)==GodWandItem.MODE_ATOMIC;
        if(!charging&&frame==null)return;
        double age=frame==null?entity.getTicksUsingItem()+partial:frame.age();
        float raise=AtomicSequence.armRaise(age), spread=AtomicSequence.armSpread(age), start=AtomicSequence.activation(age);
        float windup=AtomicSequence.armWindup(age), facing=AtomicSequence.facing(age);
        boolean right=(entity.getUsedItemHand()==net.minecraft.world.InteractionHand.MAIN_HAND)
                ==(entity.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT);
        ModelPart grip=right?rightArm:leftArm, support=right?leftArm:rightArm;
        float weight=1-AtomicSequence.phase(age,774,802), style=AtomicSequence.phase(age,320,360)*weight;
        float oldGrip=grip.xRot, oldSupport=support.xRot;
        grip.xRot=AtomicSequence.armPitch(age);
        grip.yRot=(right?1:-1)*(-.64f*raise*(1-windup)*(1-spread)+.55f*windup)*weight;
        grip.zRot=(right?1:-1)*(.02f*raise*(1-spread)*(1-windup)-.72f*windup+1.15f*spread);
        float supportUp=net.minecraft.util.Mth.lerp(raise,-.12f-1.3f*start,-2.16f);
        supportUp+=(-1.05f-supportUp)*windup;
        support.xRot=supportUp*(1-spread)-.12f*spread;
        support.zRot=(right?-1:1)*(.02f*raise*(1-spread)*(1-windup)-.88f*windup+1.32f*spread)*weight;
        support.yRot=(right?1:-1)*(.64f*raise*(1-spread)*(1-windup)-.525f*windup)*weight;
        grip.xRot=net.minecraft.util.Mth.lerp(weight,oldGrip,grip.xRot);
        support.xRot=net.minecraft.util.Mth.lerp(weight,oldSupport,support.xRot);
        grip.zRot*=weight;
        body.yRot=body.yRot*(1-facing)-.12f*style*(1-facing)+.2f*windup;
        body.zRot=-.035f*style*(1-facing);
        float idle=frame==null?0:AtomicSequence.holdIdle(frame.waitTicks())*(1-AtomicSequence.phase(age,AtomicSequence.WAIT_TICK,AtomicSequence.WAIT_TICK+4));
        grip.xRot+=idle; support.xRot-=idle*.8f; body.zRot+=idle*.2f;
        var look=AtomicSequence.headLook(age,entity.getId()+(frame==null?0:frame.startedAt()*.01));
        float glance=AtomicSequence.phase(age,0,14)*weight;
        head.xRot=net.minecraft.util.Mth.lerp(glance,head.xRot,(float)look.x)*(1-facing);
        head.yRot=net.minecraft.util.Mth.lerp(glance,head.yRot,(float)look.y)*(1-facing);
        head.zRot=net.minecraft.util.Mth.lerp(glance,head.zRot,(float)look.z)*(1-facing);
        hat.copyFrom(head);

    }
}
