package net.godwand.mixin;

import net.godwand.client.AtomicCinematic;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ToastManager.class,remap=false)
public abstract class AtomicToastMixin {
    @Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true)
    private void godwand$atomicDeferToasts(CallbackInfo ci) { if(AtomicCinematic.active())ci.cancel(); }
}
