package net.godwand.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    /**
     * Vô hiệu hóa hiệu ứng phát sáng outline cho toàn bộ khối BlockDisplay
     */
    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void godwand$preventDisplacedIsGlowing(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.getTags().contains("godwand_displaced")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method="isInvulnerableTo",at=@At("HEAD"),cancellable=true)
    private void godwand$atomicProtected(net.minecraft.world.damagesource.DamageSource source,CallbackInfoReturnable<Boolean> cir) {
        if((Object)this instanceof net.minecraft.server.level.ServerPlayer player && net.godwand.skill.AtomicManager.invulnerable(player))cir.setReturnValue(true);
    }

    @Inject(method = "hasGlowingTag", at = @At("HEAD"), cancellable = true)
    private void godwand$preventDisplacedHasGlowingTag(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.getTags().contains("godwand_displaced")) {
            cir.setReturnValue(false);
        }
    }
}
