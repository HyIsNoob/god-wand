package net.godwand.mixin;

import net.godwand.client.BlueRedInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** On Blue/Red, left click summons Blue (see BlueRedInput) instead of attacking or mining. */
@Mixin(Minecraft.class)
public abstract class BlueRedAttackMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void godwand$blueInsteadOfAttack(CallbackInfoReturnable<Boolean> cir) {
        if (BlueRedInput.eligible(((Minecraft) (Object) this).player)) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void godwand$blueInsteadOfMining(boolean leftClick, CallbackInfo ci) {
        if (BlueRedInput.eligible(((Minecraft) (Object) this).player)) ci.cancel();
    }
}
