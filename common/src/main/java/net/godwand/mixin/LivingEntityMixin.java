package net.godwand.mixin;

import net.godwand.skill.BlockDominionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Intercepts real incoming damage for Block Dominion defense (costs 1 block only when real damage lands). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void godwand$blockDominionDefend(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player && BlockDominionManager.tryAbsorbDamage(player, source, amount)) {
            cir.setReturnValue(false);
        }
    }
}
