package net.godwand.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The player's volume for the mod's own sounds (godwand:*), on top of the game's sliders (owner, 2026-11-20). */
@Mixin(SoundEngine.class)
public abstract class GodWandVolumeMixin {
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void godwand$modVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        float volume = net.godwand.config.GodWandConfig.CLIENT.soundVolume;
        if (volume < .999f && "godwand".equals(sound.getLocation().getNamespace())) cir.setReturnValue(cir.getReturnValue() * volume);
    }
}
