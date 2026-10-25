package net.godwand.client;

import net.minecraft.util.Mth;

import net.godwand.item.GodWandItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** One wind voice, gated by actual high charge and stopped on release, switch or disconnect. */
public final class StoneCannonWindSound extends AbstractTickableSoundInstance {
    private static StoneCannonWindSound instance;
    private float target;
    private StoneCannonWindSound(float initial) {
        super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, RandomSource.create());
        relative = true; looping = true; delay = 0;
        attenuation = SoundInstance.Attenuation.NONE;
        volume = target = initial;
        pitch = 0.8f;
    }
    public static float windVolume(int ticks) {
        float ramp = Mth.clamp((ticks - 50) / 30f, 0, 1);
        return 0.18f * ramp * ramp;
    }
    public static void update(Minecraft client) {
        if (client.player == null || client.level == null || !client.player.isUsingItem()) { reset(); return; }
        var stack = client.player.getUseItem();
        if (!(stack.getItem() instanceof GodWandItem) || GodWandItem.getSkillMode(stack) != GodWandItem.MODE_STONE_CANNON) { reset(); return; }
        float volume = windVolume(client.player.getTicksUsingItem());
        if (volume < 0.003f) { reset(); return; }
        if (instance == null || instance.isStopped()) {
            instance = new StoneCannonWindSound(volume);
            client.getSoundManager().play(instance);
        }
        instance.target = volume;
    }
    public static void reset() {
        if (instance != null) {
            instance.stop();
            var client = Minecraft.getInstance();
            if (client != null) client.getSoundManager().stop(instance);
            instance = null;
        }
    }
    public void tick() {
        volume += (target - volume) * 0.18f;
        pitch += (0.8f + target * 2 - pitch) * 0.12f;
    }
}
