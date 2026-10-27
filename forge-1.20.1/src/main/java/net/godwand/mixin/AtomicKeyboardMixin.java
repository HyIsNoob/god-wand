package net.godwand.mixin;

import net.godwand.client.AtomicCinematic;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class AtomicKeyboardMixin {
    @Inject(method="keyPress",at=@At("HEAD"),cancellable=true)
    private void godwand$skipAtomic(long window,int key,int scan,int action,int modifiers,CallbackInfo ci) {
        var client=Minecraft.getInstance();
        if(window==client.getWindow().getWindow()&&key==GLFW.GLFW_KEY_ESCAPE&&action==GLFW.GLFW_PRESS
                &&client.screen==null&&AtomicCinematic.active()) {
            AtomicCinematic.skip();
            ci.cancel();
        }
    }
}
