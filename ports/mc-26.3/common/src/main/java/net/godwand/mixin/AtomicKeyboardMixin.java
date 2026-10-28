package net.godwand.mixin;
import net.godwand.client.AtomicCinematic;
import net.minecraft.client.*;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=KeyboardHandler.class,remap=false)
public abstract class AtomicKeyboardMixin {
 @Inject(method="keyPress",at=@At("HEAD"),cancellable=true)
 private void godwand$skipAtomic(long window,int action,KeyEvent event,CallbackInfo ci) {
  var client=Minecraft.getInstance();
  if(window==client.getWindow().handle()&&event.key()==InputConstants.KEY_ESCAPE&&action==InputConstants.PRESS&&client.gui.screen()==null&&AtomicCinematic.active()) {
   AtomicCinematic.skip();ci.cancel();
  }
 }
}
