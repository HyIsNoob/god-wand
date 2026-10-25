package net.godwand.mixin;

import net.godwand.client.GodWandCinematicEngine;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundSystemMixin {

    /**
     * Vacuum Silence (Khoảng lặng chân không):
     * Ép âm lượng mọi âm thanh môi trường/mob về 0 ngay trước khoảnh khắc đại nổ.
     * Ngoại trừ PLAYERS (tiếng người chơi/vũ khí) và MASTER.
     */
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void godwand$applyVacuumSilence(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        float gain = GodWandCinematicEngine.getEnvironmentGain();
        if (gain < 0.999f) {
            SoundSource category = sound.getSource();
            if (category != SoundSource.PLAYERS && category != SoundSource.MASTER) {
                cir.setReturnValue(cir.getReturnValue() * gain);
            }
        }
    }
}
