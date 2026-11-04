package net.godwand.qa.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(net.minecraft.client.renderer.entity.layers.ItemInHandLayer.class)
public abstract class VanillaBowHideItemMixin {
 @Inject(method="renderArmWithItem",at=@At("HEAD"),cancellable=true)
 private void qa$hide(CallbackInfo ci){if(net.godwand.qa.VanillaBowProbe.active)ci.cancel();}
}
