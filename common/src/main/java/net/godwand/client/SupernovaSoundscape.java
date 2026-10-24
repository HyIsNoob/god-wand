package net.godwand.client;

import net.godwand.network.CinematicFxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/** Supports continuous custom audio spanning from initial wand charge to sequence end,
 *  running full duration without premature cutoffs, while falling back to vanilla sound cues
 *  when using the default placeholder. */
public final class SupernovaSoundscape {
    private static final ResourceLocation AUDIO = ResourceLocation.fromNamespaceAndPath("godwand", "supernova_sequence");
    private static final String PLACEHOLDER_SHA = "225b65ec21cc27259fd742d6e1488a69c28f4a0365301d533eeaf433e3c60443";
    private static Charge chargeSound;
    private static SimpleSoundInstance sequence;
    private static ClientLevel world;
    private static boolean custom;
    private static boolean customChecked;
    private static boolean charging;
    private static boolean activated;
    private static long activationTime;

    static boolean isCustomAudio(byte[] bytes) {
        if (bytes.length < 4 || bytes[0] != 'O' || bytes[1] != 'g' || bytes[2] != 'g' || bytes[3] != 'S') return false;
        try {
            return !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(PLACEHOLDER_SHA);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean checkCustom(Minecraft client) {
        if (customChecked) return custom;
        var resource = client.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("godwand", "sounds/supernova_sequence.ogg"));
        if (resource.isPresent()) {
            try (var input = resource.get().open()) {
                custom = isCustomAudio(input.readAllBytes());
            } catch (java.io.IOException ignored) {
                custom = false;
            }
        }
        customChecked = true;
        return custom;
    }

    static float volume(double age, float charge) {
        return age < 0 && charge > 0 ? 0.08f + Math.clamp(charge, 0, 1) * 0.18f : 0;
    }

    public static void start(Vec3 center) {
        var client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        checkCustom(client);
        activated = true;
        charging = false;
        if (client.level != null) {
            world = client.level;
            activationTime = client.level.getGameTime();
        }
        if (custom) {
            if (chargeSound != null) {
                client.getSoundManager().stop(chargeSound);
                chargeSound = null;
            }
            if (sequence != null && client.getSoundManager().isActive(sequence)) {
                // Already started during charge phase and still playing, let it play through without interruption!
                return;
            }
            if (sequence != null) {
                client.getSoundManager().stop(sequence);
                sequence = null;
            }
            float distance = GodWandCinematicEngine.distanceFactor(CinematicFxPayload.EVENT_SUPERNOVA_COMPRESS,
                    client.player.position().distanceTo(center));
            sequence = new SimpleSoundInstance(AUDIO, SoundSource.PLAYERS,
                    distance, 1.0f, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, center.x, center.y, center.z, false);
            client.getSoundManager().play(sequence);
        } else {
            if (chargeSound != null) {
                client.getSoundManager().stop(chargeSound);
                chargeSound = null;
            }
        }
    }

    static void tick(double age, float charge, float distance) {
        var client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null) { reset(); return; }
        if (world == null) world = client.level;
        if (world != client.level) { reset(); return; }
        checkCustom(client);

        if (custom) {
            // Automatically clean up when sequence audio finishes playing in OpenAL
            if (sequence != null && !client.getSoundManager().isActive(sequence)) {
                sequence = null;
                activated = false;
                charging = false;
            }

            if (charge > 0) {
                // If starting a fresh charge: restart sequence audio cleanly even if cast previously
                if (!charging) {
                    if (sequence != null) {
                        client.getSoundManager().stop(sequence);
                        sequence = null;
                    }
                    activated = false;
                    charging = true;
                    sequence = new SimpleSoundInstance(AUDIO, SoundSource.PLAYERS,
                            1.0f, 1.0f, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
                    client.getSoundManager().play(sequence);
                }
                if (chargeSound != null) {
                    client.getSoundManager().stop(chargeSound);
                    chargeSound = null;
                }
            } else if (charging && !activated) {
                // Cancelled wand charge before release
                if (sequence != null) {
                    client.getSoundManager().stop(sequence);
                    sequence = null;
                }
                charging = false;
            }
        } else {
            // Placeholder: vanilla charge sound
            float target = volume(age, charge);
            if (target <= 0) {
                if (chargeSound != null) client.getSoundManager().stop(chargeSound);
                chargeSound = null;
                return;
            }
            if (chargeSound == null || chargeSound.isStopped()) {
                chargeSound = new Charge(target);
                client.getSoundManager().play(chargeSound);
            }
            chargeSound.target = target;
            chargeSound.tone = 0.75f + charge * 0.3f;
        }
    }

    public static void reset() {
        var client = Minecraft.getInstance();
        if (client != null) {
            if (chargeSound != null) client.getSoundManager().stop(chargeSound);
            if (sequence != null) client.getSoundManager().stop(sequence);
        }
        chargeSound = null;
        sequence = null;
        customChecked = false;
        charging = false;
        activated = false;
        world = null;
    }

    private static final class Charge extends AbstractTickableSoundInstance {
        float target, tone = 0.75f;
        Charge(float initial) {
            super(SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, RandomSource.create());
            relative = true; looping = true; delay = 0;
            attenuation = SoundInstance.Attenuation.NONE;
            volume = target = initial; pitch = tone;
        }
        public void tick() {
            volume += (target - volume) * 0.15f;
            pitch += (tone - pitch) * 0.1f;
        }
    }
}
