package net.godwand.mixin;

import net.godwand.client.PurpleCinematic;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ESC skips Purple's cutscene (owner, 2026-11-20), like the other cutscenes; the blast itself goes on in the world. */
@Mixin(KeyboardHandler.class)
public abstract class PurpleSkipMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void godwand$skipPurple(long window, int key, int scan, int action, int modifiers, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (window == client.getWindow().getWindow() && key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS && client.screen == null && PurpleCinematic.active()) {
            PurpleCinematic.skip();
            ci.cancel();
        }
    }
}
