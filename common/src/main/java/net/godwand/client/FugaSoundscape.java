package net.godwand.client;

import net.godwand.network.FugaFxPayload;
import net.godwand.skill.FugaSequence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Fuga audio in two authored parts plus a loop: part 1 (fuga_charge) from the start of the hold, cut on cancel;
 *  the hold loop (fuga_hold) once the bow is ready until the shot; part 2 (fuga_release) from the shot, always
 *  played to its end. While a part is still the shipped silent placeholder, vanilla cues stand in for it. */
public final class FugaSoundscape {
    static final String CHARGE = "fuga_charge", HOLD = "fuga_hold", RELEASE = "fuga_release";
    /** SHA-256 of the silent placeholder shipped as fuga_charge.ogg and fuga_release.ogg. */
    static final String PLACEHOLDER_SHA = "9dfd4844812c24144949e8743103d18ff27928717aab16c69fabbc7225934eeb";
    private static final class Cast {
        final long token;
        int phase = -1;
        SimpleSoundInstance charge, release;
        Hold hold;
        boolean readyCue, exploded;
        Vec3 impact;
        long impactAt;
        Cast(long token) { this.token = token; }
    }
    private static final Map<Integer, Cast> CASTS = new LinkedHashMap<>();
    private static final Map<String, Boolean> CUSTOM = new LinkedHashMap<>();
    private static ClientLevel world;
    private FugaSoundscape() {}

    private static ResourceLocation id(String namespace, String path) { return ResourceLocation.fromNamespaceAndPath(namespace, path); }

    static boolean isCustomAudio(byte[] bytes) {
        if (bytes.length < 4 || bytes[0] != 'O' || bytes[1] != 'g' || bytes[2] != 'g' || bytes[3] != 'S') return false;
        try {
            return !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(PLACEHOLDER_SHA);
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static boolean custom(Minecraft client, String name) {
        return CUSTOM.computeIfAbsent(name, n -> {
            var resource = client.getResourceManager().getResource(id("godwand", "sounds/" + n + ".ogg"));
            if (resource.isEmpty()) return false;
            try (var input = resource.get().open()) { return isCustomAudio(input.readAllBytes()); }
            catch (java.io.IOException e) { return false; }
        });
    }

    /** Called every client tick with the casts FugaRenderer still tracks. */
    static void tick(Minecraft client, ClientLevel level, Map<Integer, FugaFxPayload> active) {
        if (level != world) { reset(); world = level; }
        var sounds = client.getSoundManager();
        if (level == null || client.player == null || sounds == null || client.getResourceManager() == null) return;
        long now = level.getGameTime();
        for (var entry : active.entrySet()) {
            var p = entry.getValue();
            Cast cast = CASTS.get(entry.getKey());
            if (cast == null || cast.token != p.startedAt()) {
                if (cast != null) stop(cast, true);
                cast = new Cast(p.startedAt());
                CASTS.put(entry.getKey(), cast);
            }
            if (p.phase() == cast.phase) {
                if (p.phase() == FugaFxPayload.CHARGE && now - p.startedAt() >= FugaSequence.CHARGE_TICKS) {
                    if (cast.hold == null) { cast.hold = new Hold(id("godwand", HOLD), gain(client, p, false)); sounds.play(cast.hold); }
                    if (!cast.readyCue && !custom(client, CHARGE)) { cast.readyCue = true; cue(client, "entity.blaze.shoot", p, 1, .7f); }
                }
                if (p.phase() == FugaFxPayload.IMPACT && !cast.exploded && now - p.phaseAt() >= FugaSequence.ORB_TICKS) {
                    cast.exploded = true;
                    if (!custom(client, RELEASE)) cue(client, "entity.generic.explode", p, 6, .55f);
                }
                continue;
            }
            int previous = cast.phase;
            cast.phase = p.phase();
            if (p.phase() == FugaFxPayload.CHARGE) {
                if (custom(client, CHARGE)) cast.charge = play(client, CHARGE, gain(client, p, false));
                else cue(client, "entity.blaze.ambient", p, 1.5f, .65f);
            } else if (p.phase() == FugaFxPayload.FIRE) {
                fadeHold(cast);
                if (cast.charge != null) { sounds.stop(cast.charge); cast.charge = null; }
                if (custom(client, RELEASE)) cast.release = play(client, RELEASE, gain(client, p, true));
                else cue(client, "item.firecharge.use", p, 3, .6f);
            } else if (p.phase() == FugaFxPayload.IMPACT) {
                fadeHold(cast);
                if (previous == FugaFxPayload.FIRE && !custom(client, RELEASE)) cue(client, "entity.blaze.shoot", p, 3, .5f);
            }
        }
        // A cast FugaRenderer dropped: a charge that never fired was cancelled; a fired one keeps its audio running.
        CASTS.entrySet().removeIf(entry -> {
            if (active.containsKey(entry.getKey())) return false;
            Cast cast = entry.getValue();
            if (cast.phase <= FugaFxPayload.CHARGE) { fadeHold(cast); stop(cast, true); return true; }
            fadeHold(cast);
            return cast.release == null || !sounds.isActive(cast.release);
        });
    }

    /** Caster: full volume wherever the camera is. Observers: by distance to the cast and to where it is aimed. */
    private static float gain(Minecraft client, FugaFxPayload p, boolean target) {
        if (client.player.getId() == p.casterId()) return 1;
        var eye = client.player.position();
        double distance = eye.distanceTo(new Vec3(p.x(), p.y(), p.z()));
        if (target) distance = Math.min(distance, eye.distanceTo(new Vec3(p.tx(), p.ty(), p.tz())));
        return AtomicRenderer.distanceGain(distance);
    }
    private static SimpleSoundInstance play(Minecraft client, String name, float gain) {
        if (gain <= 0) return null;
        var sound = new SimpleSoundInstance(id("godwand", name), SoundSource.PLAYERS, gain, 1, RandomSource.create(),
                false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        client.getSoundManager().play(sound);
        return sound;
    }
    /** Vanilla stand-in for a placeholder part, at the cast's own position like the old server cue. */
    private static void cue(Minecraft client, String vanilla, FugaFxPayload p, float volume, float pitch) {
        client.getSoundManager().play(new SimpleSoundInstance(id("minecraft", vanilla), SoundSource.PLAYERS, volume, pitch,
                RandomSource.create(), false, 0, SoundInstance.Attenuation.LINEAR, p.x(), p.y(), p.z(), false));
    }
    private static void fadeHold(Cast cast) { if (cast.hold != null) { cast.hold.fading = true; cast.hold = null; } }
    private static void stop(Cast cast, boolean all) {
        var client = Minecraft.getInstance();
        var sounds = client == null ? null : client.getSoundManager();
        if (sounds == null) { cast.charge = null; cast.hold = null; return; }
        if (cast.charge != null) sounds.stop(cast.charge);
        if (cast.hold != null) sounds.stop(cast.hold);
        if (all && cast.release != null) sounds.stop(cast.release);
        cast.charge = null; cast.hold = null;
    }
    public static void reset() {
        for (var cast : CASTS.values()) stop(cast, true);
        CASTS.clear(); CUSTOM.clear(); world = null;
    }

    /** The ready-to-fire loop: eases in, and eases out quickly when the shot or a cancel ends it. */
    private static final class Hold extends AbstractTickableSoundInstance {
        final float target;
        boolean fading;
        Hold(ResourceLocation sound, float gain) {
            super(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(sound), SoundSource.PLAYERS, RandomSource.create());
            looping = true; delay = 0; relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
            target = .6f * gain; volume = .001f;
        }
        public void tick() {
            if (fading) { volume -= target / 4; if (volume <= 0) { volume = 0; stop(); } }
            else volume = Math.min(target, volume + target / 16);
        }
    }
}
