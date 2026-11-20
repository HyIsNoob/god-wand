package net.godwand.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Blue's and Red's own sounds (owner, 2026-11-20). Blue: an appear, a hum that follows it while it lives (louder once ready), a ready swell,
 * a vanish (tools/audio/blue.py). Red: the charge plays once over the charge time and is cut short if fired early; once full a hold loop
 * runs for as long as it is held; then the shot. Red's three still point at vanilla sounds in sounds.json until the owner's tracks replace them.
 */
final class BlueRedSounds {
    private BlueRedSounds() {}

    static final SoundEvent BLUE_APPEAR = event("blue_appear"), BLUE_HOLD = event("blue_hold"), BLUE_READY = event("blue_ready"),
            BLUE_VANISH = event("blue_vanish"), RED_CHARGE = event("red_charge"), RED_HOLD = event("red_hold"), RED_FIRE = event("red_fire");

    private static SoundEvent event(String name) { return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("godwand", name)); }

    private static final Map<Integer, Follow> HUMS = new HashMap<>(), RED_LOOPS = new HashMap<>();
    private static final Map<Integer, SoundInstance> CHARGING = new HashMap<>();

    static void play(Vec3 at, SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.PLAYERS, volume, pitch, RandomSource.create(), at.x, at.y, at.z));
    }

    /** Blue's hum, following {@code where} while {@code alive}; {@code gain} is read each tick. */
    static void blueHum(int owner, Supplier<Vec3> where, BooleanSupplier alive, DoubleSupplier gain) {
        Follow old = HUMS.get(owner);
        if (old != null && !old.isStopped()) return;
        Follow hum = new Follow(BLUE_HOLD, where, alive, gain);
        HUMS.put(owner, hum);
        Minecraft.getInstance().getSoundManager().play(hum);
    }

    static void redCharge(int owner, Vec3 at) {
        redEnd(owner);
        SoundInstance s = new SimpleSoundInstance(RED_CHARGE, SoundSource.PLAYERS, 1f, 1f, RandomSource.create(), at.x, at.y, at.z);
        CHARGING.put(owner, s);
        Minecraft.getInstance().getSoundManager().play(s);
    }

    /** Red is full and still held: the hold loop, for as long as {@code alive}. */
    static void redFull(int owner, Supplier<Vec3> where, BooleanSupplier alive) {
        Follow loop = new Follow(RED_HOLD, where, alive, () -> .9);
        RED_LOOPS.put(owner, loop);
        Minecraft.getInstance().getSoundManager().play(loop);
    }

    /** Fired or called off: the charge is cut at once (the loop fades by itself). */
    static void redEnd(int owner) {
        SoundInstance s = CHARGING.remove(owner);
        if (s != null) Minecraft.getInstance().getSoundManager().stop(s);
        RED_LOOPS.remove(owner);
    }

    static void reset() {
        var sounds = Minecraft.getInstance().getSoundManager();
        CHARGING.values().forEach(sounds::stop);
        HUMS.values().forEach(sounds::stop);
        RED_LOOPS.values().forEach(sounds::stop);
        CHARGING.clear(); HUMS.clear(); RED_LOOPS.clear();
    }

    /** A loop that follows a moving source, eases in, and fades out quickly once its source is gone. */
    private static final class Follow extends AbstractTickableSoundInstance {
        private final Supplier<Vec3> where;
        private final BooleanSupplier alive;
        private final DoubleSupplier gain;
        private boolean fading;

        Follow(SoundEvent sound, Supplier<Vec3> where, BooleanSupplier alive, DoubleSupplier gain) {
            super(sound, SoundSource.PLAYERS, RandomSource.create());
            this.where = where; this.alive = alive; this.gain = gain;
            looping = true; delay = 0; volume = .001f;
            Vec3 at = where.get();
            if (at != null) { x = at.x; y = at.y; z = at.z; }
        }

        @Override public void tick() {
            Vec3 at = where.get();
            if (!fading && (at == null || !alive.getAsBoolean())) fading = true;
            if (at != null) { x = at.x; y = at.y; z = at.z; }
            float target = (float) gain.getAsDouble();
            if (fading) { volume -= .12f; if (volume <= 0) { volume = 0; stop(); } }
            else volume = Math.min(target, volume + target / 10);
        }
    }
}
