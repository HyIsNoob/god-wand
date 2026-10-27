package net.godwand.mixin;

import net.godwand.client.AtomicCinematic;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class AtomicGuiMixin {
    @Inject(method={"renderCrosshair","renderHotbar","displayScoreboardSidebar","renderEffects"},at=@At("HEAD"),cancellable=true)
    private void godwand$atomicHideCrosshair(CallbackInfo ci) { if(AtomicCinematic.active())ci.cancel(); }
}
