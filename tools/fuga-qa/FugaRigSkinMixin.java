package net.godwand.qa.mixin;
import net.godwand.qa.FugaRigProbe;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AbstractClientPlayer.class)
public abstract class FugaRigSkinMixin {
 @Inject(method="getSkin",at=@At("HEAD"),cancellable=true)
 private void qa$skin(CallbackInfoReturnable<PlayerSkin> ci){if(FugaRigProbe.skin!=null)ci.setReturnValue(FugaRigProbe.skin);}
}
