package net.godwand.mixin;

import net.godwand.client.AtomicCinematic;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=Hud.class,remap=false)
public abstract class AtomicGuiMixin {
    @Inject(method={"extractCrosshair","extractHotbarAndDecorations","extractChat","extractScoreboardSidebar","extractTabList","extractEffects","extractOverlayMessage","extractTitle"},at=@At("HEAD"),cancellable=true)
    private void godwand$atomicHideCrosshair(CallbackInfo ci) { if(AtomicCinematic.active())ci.cancel(); }
}
