package net.godwand.mixin;

import net.minecraft.world.entity.Display;
import net.godwand.spatial.SpatialDisplacementManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Display.class)
public abstract class DisplayLifecycleMixin {
    @Inject(method="tick",at=@At("HEAD"))
    private void godwand$reattachDisplacedDisplay(CallbackInfo ci) {
        if((Object)this instanceof Display.BlockDisplay display)
            SpatialDisplacementManager.tickManagedDisplay(display);
    }
}
