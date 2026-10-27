package net.godwand.mixin;

import net.godwand.client.AtomicCinematic;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class AtomicInputMixin extends Input {
    @Inject(method="tick",at=@At("TAIL"))
    private void godwand$atomicInput(boolean slow,float multiplier,CallbackInfo ci) {
        if(!AtomicCinematic.movementLocked())return;
        forwardImpulse=leftImpulse=0;
        up=down=left=right=jumping=shiftKeyDown=false;
    }
}
