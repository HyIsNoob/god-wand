package net.godwand.client;

import net.godwand.item.GodWandItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.random.Random;

/** One wind voice, gated by actual high charge and stopped on release, switch or disconnect. */
public final class StoneCannonWindSound extends MovingSoundInstance {
    private static StoneCannonWindSound instance;
    private float target;
    private StoneCannonWindSound(float initial) {
        super(SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, Random.create());
        relative = true; repeat = true; repeatDelay = 0;
        attenuationType = SoundInstance.AttenuationType.NONE;
        volume = target = initial;
        pitch = 0.8f;
    }
    public static float windVolume(int ticks) {
        float ramp = Math.clamp((ticks - 50) / 30f, 0, 1);
        return 0.18f * ramp * ramp;
    }
    public static void update(MinecraftClient client) {
        if (client.player == null || client.world == null || !client.player.isUsingItem()) { reset(); return; }
        var stack = client.player.getActiveItem();
        if (!(stack.getItem() instanceof GodWandItem) || GodWandItem.getSkillMode(stack) != GodWandItem.MODE_STONE_CANNON) { reset(); return; }
        float volume = windVolume(client.player.getItemUseTime());
        if (volume < 0.003f) { reset(); return; }
        if (instance == null || instance.isDone()) {
            instance = new StoneCannonWindSound(volume);
            client.getSoundManager().play(instance);
        }
        instance.target = volume;
    }
    public static void reset() {
        if (instance != null) {
            instance.setDone();
            var client = MinecraftClient.getInstance();
            if (client != null) client.getSoundManager().stop(instance);
            instance = null;
        }
    }
    public void tick() {
        volume += (target - volume) * 0.18f;
        pitch += (0.8f + target * 2 - pitch) * 0.12f;
    }
}
