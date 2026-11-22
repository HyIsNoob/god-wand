package net.godwand.mixin;

import net.godwand.client.BlockDominionInput;
import net.godwand.client.BlueRedInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** On Blue/Red and Block Dominion, left click triggers skill input instead of attacking or mining. */
@Mixin(Minecraft.class)
public abstract class BlueRedAttackMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void godwand$blueInsteadOfAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = (Minecraft) (Object) this;
        if (BlueRedInput.eligible(client.player)) {
            cir.setReturnValue(false);
        } else if (BlockDominionInput.eligible(client.player)) {
            BlockDominionInput.onStartAttack(client);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void godwand$blueInsteadOfMining(boolean leftClick, CallbackInfo ci) {
        BlueRedInput.attackHeld = leftClick; // controller mods (Controllable) feed the hold here, never through keyAttack
        Minecraft client = (Minecraft) (Object) this;
        if (BlueRedInput.eligible(client.player) || BlockDominionInput.eligible(client.player)) ci.cancel();
    }
}
