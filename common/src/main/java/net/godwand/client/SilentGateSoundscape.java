package net.godwand.client;

import net.godwand.client.SilentGateRenderer.Frame;
import net.godwand.client.SilentGateRenderer.Shot;
import net.godwand.skill.SilentGateSequence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Silent Gate's whole soundtrack (owner, 2026-11-15: "make the full audio"): a timed track for the build (click 1 to the wait) and one for the ending, a loop
 *  for the wait however long it lasts and one under the rain of beams, and one-shots for what the server decides (the command, each strike, the kill beam).
 *  The sounds are mixed from the owner's CC0 library by tools/audio/silent_gate.py; every level is a constant below, to be tuned by ear. */
final class SilentGateSoundscape {
    private SilentGateSoundscape() {}
    /** Loop level under the barrage (storm of air, high choir, ground rumble). */
    static final float RAIN = .75f;
    /** Timed track levels: the build, the owner's track from click 1 through the wait to the command, which the server gives by itself after
     *  gateWaitTicks (139 = 6.95 s; owner, 2026-11-16: no loop, so the wait is as long as the track); and the ending, softer (it was loud and boomy). */
    static final float BUILD = 1f, END = .55f;
    /** One-shot levels: the command, a strike close by, a strike far off, the kill beam. */
    static final float COMMAND = .9f, STRIKE = .6f, STRIKE_FAR = .35f, KILL = 1f;
    /** A strike this far from the listener is at its faintest; the whole cast fades out for a viewer this far from its caster. */
    static final double STRIKE_REACH = 240, CAST_REACH = 220;

    private static final class Cast { Loop rain, build, end; boolean built, ended; double lastCommand = -1; }
    private static final Map<Integer, Cast> CASTS = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    /** 0..1 loudness of a strike {@code distance} blocks from the listener: full close by, a quarter-power tail far off, never quite silent inside the field. */
    static double near(double distance) { double k = Mth.clamp(1 - distance / STRIKE_REACH, 0, 1); return k * k; }
    /** A strike is voiced when it is close, and otherwise only every other tick: some 220 circles fire seven beams a tick, which no mixer should be asked to play. */
    static boolean voiced(double near, long tick) { return near > .3 || tick % 2 == 0; }

    static void tick(Minecraft client, List<Frame> frames) {
        if (frames.isEmpty() && CASTS.isEmpty()) return;
        CASTS.entrySet().removeIf(e -> {
            for (Frame f : frames) if (f.casterId() == e.getKey()) return false;
            stop(e.getValue());
            return true;
        });
        Vec3 ear = client.gameRenderer.getMainCamera().getPosition();
        for (Frame f : frames) {
            if (f.anchor() == null) continue;
            Cast c = CASTS.computeIfAbsent(f.casterId(), id -> new Cast());
            // How loud the cast is goes by where the listener stands, not where the director's camera is: from the shots high in the sky it fell silent.
            Vec3 body = client.player != null ? client.player.position() : ear;
            float gain = (float) Mth.clamp(1.25 - f.anchor().distanceTo(body) / CAST_REACH, 0, 1);
            double command = f.commandAge(), rain = command - SilentGateSequence.ACTIVATE_TICKS;
            boolean raining = command >= 0 && rain < f.barrageTicks();
            // The timed tracks start on their beat, or as near it as this client could (a slow first frame can hold the first ticks back: the owner heard no build
            // track at all with a stricter limit); a viewer who arrives well after the start hears the loops only.
            if (!c.built && command < 0 && f.age() < 40) { c.built = true; c.build = track(client, "gate_build", BUILD * gain); }
            double end = SilentGateSequence.ACTIVATE_TICKS + f.barrageTicks();
            if (!c.ended && command >= end && command < end + 40) { c.ended = true; c.end = track(client, "gate_end", END * gain); }
            // A command before the track's end cuts it (owner, 2026-11-16): the command's own sound takes over.
            if (c.build != null) { if (command >= 0) c.build.cut(); else c.build.target = BUILD * gain; }
            if (c.end != null) c.end.target = END * gain;
            c.rain = keep(client, c.rain, raining, "gate_rain", RAIN * gain);
            if (c.lastCommand < 0 && command >= 0 && command < 4 && gain > 0) at(client, "gate_command", f.anchor(), COMMAND * gain, 1);
            c.lastCommand = command;
            if (command >= 0 && gain > 0) strikes(client, f, ear);
        }
    }

    /** The beams that land on this tick: the nearest is heard (a crack, and the light breaking like glass when it is close); the kill beam always. */
    private static void strikes(Minecraft client, Frame f, Vec3 ear) {
        double now = f.startedAt() + f.age(), bestD = Double.MAX_VALUE;
        Shot best = null;
        for (Shot s : SilentGateRenderer.shots(f.casterId())) {
            double life = SilentGateRenderer.beamLife(s, now) - GateBeams.FALL_TICKS;
            if (life < 0 || life >= 1) continue;
            if (s.heavy()) { at(client, "gate_kill", s.hit(), KILL, 1); continue; }
            double d = s.hit().distanceTo(ear);
            if (d < bestD) { bestD = d; best = s; }
        }
        if (best == null) return;
        double near = near(bestD);
        if (!voiced(near, (long) now)) return;
        if (near > .08) at(client, "gate_strike", best.hit(), (float) (STRIKE * (.2 + .8 * near)), .85f + .3f * RANDOM.nextFloat());
        else at(client, "gate_strike_far", best.hit(), STRIKE_FAR, .85f + .3f * RANDOM.nextFloat());
    }

    /** A timed track, at full level from its first sample (its opening beat must be heard); it fades like a loop when the cast is cancelled. */
    private static Loop track(Minecraft client, String sound, float volume) {
        var t = new Loop(id(sound), false, volume);
        t.target = volume;
        client.getSoundManager().play(t);
        return t;
    }

    /** Starts the loop when {@code on} and it is not playing, follows {@code volume} while it plays, and lets it fade out by itself when no longer {@code on}. */
    private static Loop keep(Minecraft client, Loop loop, boolean on, String sound, float volume) {
        if (!on || volume <= 0) { if (loop != null) loop.target = 0; return null; }
        if (loop == null || loop.isStopped()) { loop = new Loop(id(sound), true, .001f); client.getSoundManager().play(loop); }
        loop.target = volume;
        return loop;
    }

    /** A sound placed in the world for its direction only: its level is ours (the engine's own falloff would silence anything past 16 blocks). */
    private static void at(Minecraft client, String sound, Vec3 pos, float volume, float pitch) {
        client.getSoundManager().play(new SimpleSoundInstance(id(sound), SoundSource.PLAYERS, volume, pitch, RandomSource.create(), false, 0,
                SoundInstance.Attenuation.NONE, pos.x, pos.y, pos.z, false));
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("godwand", path); }

    private static void stop(Cast c) { for (Loop l : new Loop[]{c.rain, c.build, c.end}) if (l != null) l.target = 0; }

    /** Every loop is told to fade; called when the world goes away, and every tick while there is none (so it must stay cheap when nothing plays). */
    static void reset() {
        if (CASTS.isEmpty()) return;
        CASTS.values().forEach(SilentGateSoundscape::stop);
        CASTS.clear();
    }

    /** A loop that swells to its target level over a second and dies away over a second once the target is 0. */
    private static final class Loop extends AbstractTickableSoundInstance {
        float target, fade = .03f;
        /** Gone in four ticks instead of a second, without a click. */
        void cut() { target = 0; fade = .25f; }
        Loop(ResourceLocation sound, boolean loop, float start) {
            super(SoundEvent.createVariableRangeEvent(sound), SoundSource.PLAYERS, RandomSource.create());
            looping = loop; delay = 0; relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
            volume = Math.max(.001f, start);
        }
        public void tick() {
            if (target <= 0) { volume -= fade; if (volume <= 0) { volume = 0; stop(); } }
            else volume += Mth.clamp(target - volume, -.03f, .03f);
        }
    }
}
