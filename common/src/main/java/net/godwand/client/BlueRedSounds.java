package net.godwand.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Blue's and Red's own sounds (owner, 2026-11-20). Blue: an appear, a hum that follows it while it lives (louder once ready), a ready swell,
 * a vanish (tools/audio/blue.py). Red and the Purple shot (owner's tracks, 2026-11-20): the formation and the hold loop start together as the
 * cast begins, the loop repeating for as long as it is held; firing cuts both and plays the shot, in stereo for the caster and in mono, placed,
 * for everyone else (Minecraft only places mono sounds). Purple's own track starts on the contact's impact frame.
 */
final class BlueRedSounds {
    private BlueRedSounds() {}

    static final SoundEvent BLUE_APPEAR = event("blue_appear"), BLUE_HOLD = event("blue_hold"), BLUE_READY = event("blue_ready"),
            BLUE_VANISH = event("blue_vanish"), RED_FORMATION = event("red_formation"), RED_HOLD = event("red_hold"), RED_FIRE = event("red_fire"),
            RED_FIRE_STEREO = event("red_fire_stereo"), SHOT_FORMATION = event("purple_formation"), SHOT_LOOP = event("purple_loop"),
            SHOT_FIRE = event("purple_fire"), SHOT_FIRE_STEREO = event("purple_fire_stereo"), PURPLE_MAX = event("purple_max");

    private static SoundEvent event(String name) { return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("godwand", name)); }

    private static final Map<Integer, Follow> HUMS = new HashMap<>();
    private static final Map<Integer, List<Follow>> CASTING = new HashMap<>();

    static void play(Vec3 at, SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.PLAYERS, volume, pitch, RandomSource.create(), at.x, at.y, at.z));
    }

    /** Blue's hum, following {@code where} while {@code alive}; {@code gain} is read each tick. */
    static void blueHum(int owner, Supplier<Vec3> where, BooleanSupplier alive, DoubleSupplier gain) {
        Follow old = HUMS.get(owner);
        if (old != null && !old.isStopped()) return;
        Follow hum = new Follow(BLUE_HOLD, where, alive, gain, true, true);
        HUMS.put(owner, hum);
        Minecraft.getInstance().getSoundManager().play(hum);
    }

    /** A cast begins: its formation once and its hold loop together, both following the caster's eyes. */
    static void cast(int owner, SoundEvent formation, SoundEvent loop) {
        end(owner);
        Supplier<Vec3> eye = () -> { var l = Minecraft.getInstance().level; var e = l == null ? null : l.getEntity(owner); return e == null ? null : e.getEyePosition(); };
        List<Follow> sounds = List.of(new Follow(formation, eye, () -> true, () -> 1, false, false), new Follow(loop, eye, () -> true, () -> 1, true, false));
        CASTING.put(owner, sounds);
        sounds.forEach(Minecraft.getInstance().getSoundManager()::play);
    }

    /** Fired or called off: every sound of the cast stops at once. */
    static void end(int owner) {
        List<Follow> sounds = CASTING.remove(owner);
        if (sounds != null) sounds.forEach(Minecraft.getInstance().getSoundManager()::stop);
    }

    /** Fired: the cast is cut and the shot plays, in stereo for the caster and placed at {@code at} (mono) for everyone else. */
    static void fire(int owner, Vec3 at, SoundEvent mono, SoundEvent stereo, float volume) {
        end(owner);
        var me = Minecraft.getInstance().player;
        if (me != null && me.getId() == owner) Minecraft.getInstance().getSoundManager().play(new Near(stereo));
        else play(at, mono, volume, 1f);
    }

    /** Purple's track: unplaced for those in its cutscene (the camera wanders far), placed and carrying far for everyone else. */
    static void purple(Vec3 at, boolean inCutscene) {
        if (inCutscene) Minecraft.getInstance().getSoundManager().play(new Near(PURPLE_MAX));
        else play(at, PURPLE_MAX, 8f, 1f);
    }

    static void reset() {
        var sounds = Minecraft.getInstance().getSoundManager();
        HUMS.values().forEach(sounds::stop);
        CASTING.values().forEach(l -> l.forEach(sounds::stop));
        HUMS.clear(); CASTING.clear();
    }

    /** Played as if at the listener's own ears: stereo files keep their stereo. */
    private static final class Near extends AbstractSoundInstance {
        Near(SoundEvent sound) {
            super(sound, SoundSource.PLAYERS, RandomSource.create());
            relative = true; attenuation = Attenuation.NONE;
        }
    }

    /** A loop that follows a moving source, eases in, and fades out quickly once its source is gone. */
    private static final class Follow extends AbstractTickableSoundInstance {
        private final Supplier<Vec3> where;
        private final BooleanSupplier alive;
        private final DoubleSupplier gain;
        private boolean fading;

        Follow(SoundEvent sound, Supplier<Vec3> where, BooleanSupplier alive, DoubleSupplier gain, boolean loop, boolean easeIn) {
            super(sound, SoundSource.PLAYERS, RandomSource.create());
            this.where = where; this.alive = alive; this.gain = gain;
            looping = loop; delay = 0; volume = easeIn ? .001f : (float) gain.getAsDouble();
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
