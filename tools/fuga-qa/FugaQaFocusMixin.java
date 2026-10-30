package net.godwand.qa.mixin;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Only the disposable QA mod treats an occluded window as focused, never the release mod. */
@Mixin(Minecraft.class)
public abstract class FugaQaFocusMixin {
    @Inject(method="isWindowActive",at=@At("HEAD"),cancellable=true)
    private void fugaQa$background(CallbackInfoReturnable<Boolean> ci) {
        if(Boolean.getBoolean("godwand.fuga.qa"))ci.setReturnValue(true);
    }
}
