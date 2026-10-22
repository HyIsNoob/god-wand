package net.godwand.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.godwand.client.GodWandCinematicEngine;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {

    /**
     * Vacuum Silence (Khoảng lặng chân không):
     * Ép âm lượng mọi âm thanh môi trường/mob về 0 ngay trước khoảnh khắc đại nổ.
     * Ngoại trừ PLAYERS (tiếng người chơi/vũ khí) và MASTER.
     */
    @Inject(method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F", at = @At("HEAD"), cancellable = true)
    private void godwand$applyVacuumSilence(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (GodWandCinematicEngine.isVacuumSilenceActive()) {
            SoundCategory category = sound.getCategory();
            if (category != SoundCategory.PLAYERS && category != SoundCategory.MASTER) {
                cir.setReturnValue(0.0f);
            }
        }
    }
}
