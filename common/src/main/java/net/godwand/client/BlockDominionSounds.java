package net.godwand.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Client soundscape for Block Dominion (CC0 SFX assets under godwand:dominion_*).
 * Automatically follows GodWandConfig.CLIENT.soundVolume via GodWandVolumeMixin.
 */
public final class BlockDominionSounds {
    public static final SoundEvent ORBIT_LOOP = event("dominion_orbit_loop");
    public static final SoundEvent AUTO_LOOP = event("dominion_auto_loop");
    public static final SoundEvent PICKUP = event("dominion_pickup");
    public static final SoundEvent SURGE = event("dominion_surge");
    public static final SoundEvent FIRE = event("dominion_fire");
    public static final SoundEvent PARRY = event("dominion_parry");
    public static final SoundEvent VOLLEY_CHARGE = event("dominion_volley_charge");
    public static final SoundEvent VOLLEY_RELEASE = event("dominion_volley_release");
    public static final SoundEvent IMPACT = event("dominion_impact");
    public static final SoundEvent UNFOLD = event("dominion_unfold");
    public static final SoundEvent SHIELD_LOCK = event("dominion_shield_lock");
    public static final SoundEvent CAGE_FORGE = event("dominion_cage_forge");
    public static final SoundEvent CAGE_IMPALE = event("dominion_cage_impale");
    public static final SoundEvent WING_FLAP = event("dominion_wing_flap");
    public static final SoundEvent FLIGHT_BOOST = event("dominion_flight_boost");
    public static final SoundEvent FLIGHT_LOOP = SoundEvents.ELYTRA_FLYING;
    public static final SoundEvent METEOR_IGNITE = event("dominion_meteor_ignite");
    public static final SoundEvent METEOR_CATACLYSM = event("dominion_meteor_cataclysm");
    public static final SoundEvent STEP = event("dominion_step");

    private static final Map<Integer, FollowLoop> LOOPS = new HashMap<>();
    private static final Map<Integer, FollowLoop> VOLLEY_CHARGES = new HashMap<>();

    private BlockDominionSounds() {}

    private static SoundEvent event(String name) {
        return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("godwand", name));
    }

    public static void play(Vec3 at, SoundEvent sound, float volume, float pitch) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getSoundManager() == null) return;
        client.getSoundManager().play(new SimpleSoundInstance(
                sound, SoundSource.PLAYERS, volume, pitch, RandomSource.create(), at.x, at.y, at.z));
    }

    public static void ensureLoop(int playerId, SoundEvent sound, Supplier<Vec3> where,
                                  BooleanSupplier alive, DoubleSupplier gain, DoubleSupplier pitch) {
        FollowLoop current = LOOPS.get(playerId);
        if (current != null && !current.isStopped() && current.soundEvent == sound) return;
        if (current != null) current.cut();
        FollowLoop next = new FollowLoop(sound, where, alive, gain, pitch, true);
        LOOPS.put(playerId, next);
        Minecraft.getInstance().getSoundManager().play(next);
    }

    public static void stopLoop(int playerId) {
        FollowLoop current = LOOPS.remove(playerId);
        if (current != null) current.cut();
    }

    public static void startVolleyCharge(int playerId, Supplier<Vec3> where, BooleanSupplier stillCharging) {
        stopVolleyCharge(playerId);
        FollowLoop loop = new FollowLoop(VOLLEY_CHARGE, where, stillCharging, () -> 0.72, () -> 1.0, false);
        VOLLEY_CHARGES.put(playerId, loop);
        Minecraft.getInstance().getSoundManager().play(loop);
    }

    public static void stopVolleyCharge(int playerId) {
        FollowLoop current = VOLLEY_CHARGES.remove(playerId);
        if (current != null) current.cut();
    }

    public static void reset() {
        LOOPS.values().forEach(FollowLoop::cut);
        VOLLEY_CHARGES.values().forEach(FollowLoop::cut);
        LOOPS.clear();
        VOLLEY_CHARGES.clear();
    }

    private static final class FollowLoop extends AbstractTickableSoundInstance {
        final SoundEvent soundEvent;
        private final Supplier<Vec3> where;
        private final BooleanSupplier alive;
        private final DoubleSupplier targetGain;
        private final DoubleSupplier targetPitch;
        private boolean fading;

        FollowLoop(SoundEvent sound, Supplier<Vec3> where, BooleanSupplier alive,
                   DoubleSupplier gain, DoubleSupplier pitch, boolean loop) {
            super(sound, SoundSource.PLAYERS, RandomSource.create());
            this.soundEvent = sound;
            this.where = where;
            this.alive = alive;
            this.targetGain = gain;
            this.targetPitch = pitch;
            this.looping = loop;
            this.delay = 0;
            this.volume = 0.01f;
            this.pitch = (float) Mth.clamp(pitch.getAsDouble(), 0.5, 2.0);
            Vec3 at = where.get();
            if (at != null) {
                this.x = at.x;
                this.y = at.y;
                this.z = at.z;
            }
        }

        void cut() {
            this.volume = 0f;
            stop();
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getSoundManager() != null) {
                client.getSoundManager().stop(this);
            }
        }

        @Override
        public void tick() {
            Vec3 at = where.get();
            if (!fading && (at == null || !alive.getAsBoolean())) {
                fading = true;
            }
            if (at != null) {
                this.x = at.x;
                this.y = at.y;
                this.z = at.z;
            }
            float wantVol = (float) Mth.clamp(targetGain.getAsDouble(), 0.0, 1.5);
            float wantPitch = (float) Mth.clamp(targetPitch.getAsDouble(), 0.5, 2.0);
            this.pitch = Mth.lerp(0.18f, this.pitch, wantPitch);
            if (fading) {
                this.volume = Math.max(0f, this.volume - 0.06f);
                if (this.volume <= 0.001f) stop();
            } else {
                this.volume = Mth.lerp(0.15f, this.volume, wantVol);
            }
        }
    }
}
