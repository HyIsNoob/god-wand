package net.godwand.client;

import net.godwand.network.CinematicFxPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Supports continuous custom audio spanning from initial wand charge to sequence end,
 *  running full duration without premature cutoffs, while falling back to vanilla sound cues
 *  when using the default placeholder. */
final class SupernovaSoundscape {
    private static final Identifier AUDIO = Identifier.of("godwand", "supernova_sequence");
    private static final String PLACEHOLDER_SHA = "225b65ec21cc27259fd742d6e1488a69c28f4a0365301d533eeaf433e3c60443";
    private static Charge chargeSound;
    private static PositionedSoundInstance sequence;
    private static ClientWorld world;
    private static boolean custom;
    private static boolean customChecked;
    private static boolean charging;
    private static boolean activated;
    private static int cancelGraceTicks;
    private static long activationTime;

    static boolean isCustomAudio(byte[] bytes) {
        if (bytes.length < 4 || bytes[0] != 'O' || bytes[1] != 'g' || bytes[2] != 'g' || bytes[3] != 'S') return false;
        try {
            return !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(PLACEHOLDER_SHA);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean checkCustom(MinecraftClient client) {
        if (customChecked) return custom;
        var resource = client.getResourceManager().getResource(Identifier.of("godwand", "sounds/supernova_sequence.ogg"));
        if (resource.isPresent()) {
            try (var input = resource.get().getInputStream()) {
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

    static void start(Vec3d center) {
        var client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        checkCustom(client);
        activated = true;
        charging = false;
        cancelGraceTicks = 0;
        if (client.world != null) {
            world = client.world;
            activationTime = client.world.getTime();
        }
        if (chargeSound != null) {
            client.getSoundManager().stop(chargeSound);
            chargeSound = null;
        }
        if (custom) {
            if (sequence != null && client.getSoundManager().isPlaying(sequence)) {
                // Already started during charge phase and still playing, let it play through without interruption!
                return;
            }
            if (sequence != null) {
                client.getSoundManager().stop(sequence);
                sequence = null;
            }
            sequence = new PositionedSoundInstance(AUDIO, SoundCategory.PLAYERS,
                    1.0f, 1.0f, Random.create(), false, 0, SoundInstance.AttenuationType.NONE, 0, 0, 0, true);
            client.getSoundManager().play(sequence);
        }
    }

    static void tick(double age, float charge, float distance) {
        var client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) { reset(); return; }
        if (world == null) world = client.world;
        if (world != client.world) { reset(); return; }
        checkCustom(client);

        if (custom) {
            // Automatically clean up when sequence audio finishes playing in OpenAL
            if (sequence != null && !client.getSoundManager().isPlaying(sequence)) {
                sequence = null;
                activated = false;
                charging = false;
                cancelGraceTicks = 0;
            }

            if (charge > 0) {
                cancelGraceTicks = 0;
                // If starting a fresh charge: restart sequence audio cleanly even if cast previously
                if (!charging && !activated) {
                    if (sequence != null) {
                        client.getSoundManager().stop(sequence);
                        sequence = null;
                    }
                    charging = true;
                    sequence = new PositionedSoundInstance(AUDIO, SoundCategory.PLAYERS,
                            1.0f, 1.0f, Random.create(), false, 0, SoundInstance.AttenuationType.NONE, 0, 0, 0, true);
                    client.getSoundManager().play(sequence);
                }
                if (chargeSound != null) {
                    client.getSoundManager().stop(chargeSound);
                    chargeSound = null;
                }
            } else if (charging && !activated) {
                // Wand charge ended; wait a grace period for the server cast packet before assuming cancellation
                cancelGraceTicks++;
                if (cancelGraceTicks > 15) {
                    if (sequence != null) {
                        client.getSoundManager().stop(sequence);
                        sequence = null;
                    }
                    charging = false;
                    cancelGraceTicks = 0;
                }
            }
        } else {
            // Placeholder: vanilla charge sound
            float target = volume(age, charge);
            if (target <= 0) {
                if (chargeSound != null) client.getSoundManager().stop(chargeSound);
                chargeSound = null;
                return;
            }
            if (chargeSound == null || chargeSound.isDone()) {
                chargeSound = new Charge(target);
                client.getSoundManager().play(chargeSound);
            }
            chargeSound.target = target;
            chargeSound.tone = 0.75f + charge * 0.3f;
        }
    }

    static void reset() {
        var client = MinecraftClient.getInstance();
        if (client != null) {
            if (chargeSound != null) client.getSoundManager().stop(chargeSound);
            if (sequence != null) client.getSoundManager().stop(sequence);
        }
        chargeSound = null;
        sequence = null;
        customChecked = false;
        charging = false;
        activated = false;
        cancelGraceTicks = 0;
        world = null;
    }

    private static final class Charge extends MovingSoundInstance {
        float target, tone = 0.75f;
        Charge(float initial) {
            super(SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, Random.create());
            relative = true; repeat = true; repeatDelay = 0;
            attenuationType = SoundInstance.AttenuationType.NONE;
            volume = target = initial; pitch = tone;
        }
        public void tick() {
            volume += (target - volume) * 0.15f;
            pitch += (tone - pitch) * 0.1f;
        }
    }
}
