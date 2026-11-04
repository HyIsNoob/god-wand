package net.godwand.qa.mixin;
import net.godwand.client.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AtomicPostFx.class)
public abstract class FugaRigNoPostMixin {
 @Inject(method="renderAfterWorld",at=@At("HEAD"),cancellable=true,remap=false)
 private static void qa$noFugaPost(CallbackInfo ci){if(FugaRenderer.localFrame(0)!=null && (net.godwand.qa.FugaRigProbe.POSE_ONLY || net.godwand.qa.FugaRigProbe.scenario!=2 && net.godwand.qa.FugaRigProbe.scenario!=3))ci.cancel();}
}
