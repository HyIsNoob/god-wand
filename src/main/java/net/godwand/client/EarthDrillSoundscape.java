package net.godwand.client;

import net.godwand.skill.RatioCollapseManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** One fixed timeline for all four levels; custom audio is permitted to play its full duration (including aftermath),
 *  with 3D world positioning enabled when using mono audio. */
final class EarthDrillSoundscape {
    private static final Identifier AUDIO = Identifier.of("godwand", "earth_drill_sequence");
    private static final String PLACEHOLDER_SHA = "225b65ec21cc27259fd742d6e1488a69c28f4a0365301d533eeaf433e3c60443";
    private static final List<SoundInstance> voices = new ArrayList<>();
    private static ClientWorld world;
    private static long start;
    private static int lastAge;
    private static float gain;
    private static boolean custom;

    static boolean isCustomAudio(byte[] bytes) {
        if (bytes.length < 4 || bytes[0] != 'O' || bytes[1] != 'g' || bytes[2] != 'g' || bytes[3] != 'S') return false;
        try { return !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(PLACEHOLDER_SHA); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static float distanceGain(double distance) { return (float)Math.clamp((160 - distance) / 112, 0, 1); }

    static void start(Vec3d center) {
        reset();
        var client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) return;
        world = client.world; start = world.getTime(); lastAge = -1;
        gain = distanceGain(client.player.getPos().distanceTo(center));
        var resource = client.getResourceManager().getResource(Identifier.of("godwand", "sounds/earth_drill_sequence.ogg"));
        if (resource.isPresent()) {
            try (var input = resource.get().getInputStream()) { custom = isCustomAudio(input.readAllBytes()); }
            catch (java.io.IOException ignored) { custom = false; }
        }
        if (custom) playAt(AUDIO, .9f, 1, center);
        else cue(0);
        lastAge = 0;
    }

    static void tick(MinecraftClient client) {
        if (world == null) return;
        if (world != client.world || client.player == null) { reset(); return; }
        int age = (int)(world.getTime() - start);

        if (!custom) {
            if (age < 0 || age > RatioCollapseManager.SEQUENCE_END + 24) { reset(); return; }
            int first = Math.max(lastAge + 1, age - 2);
            // A clock correction must not stack old charging sounds, or lose the impact cue.
            if (lastAge < RatioCollapseManager.IMPACT_TICK && RatioCollapseManager.IMPACT_TICK < first)
                cue(RatioCollapseManager.IMPACT_TICK);
            for (int tick = first; tick <= Math.min(age, RatioCollapseManager.SEQUENCE_END); tick++) cue(tick);
        } else {
            // Allow custom audio to run its full aftermath duration without cutting off at 10s
            if (age < 0 || age > 1200) { reset(); return; }
        }
        lastAge = Math.max(lastAge, age);
    }

    private static void cue(int age) {
        if (age == 0) {
            play(SoundEvents.BLOCK_HEAVY_CORE_BREAK, .7f, .7f);
            play(SoundEvents.BLOCK_BEACON_ACTIVATE, .35f, .85f);
        }
        if (age > 0 && age <= RatioCollapseManager.ASSEMBLY_END && age % 12 == 0)
            play(SoundEvents.BLOCK_BASALT_BREAK, .38f, .65f + age * .004f);
        if (age >= 38 && age <= RatioCollapseManager.SPIN_END && age % 20 == 0)
            play(SoundEvents.BLOCK_GRINDSTONE_USE, .38f + age * .001f, .65f + age * .004f);
        if (age >= 96 && age <= RatioCollapseManager.SPIN_END && age % 18 == 0)
            play(SoundEvents.ENTITY_BREEZE_IDLE_AIR, .35f + (age - 96) * .003f, .8f + (age - 96) * .008f);
        if (age == RatioCollapseManager.SPIN_END + 1) play(SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), .8f, 1.15f);
        if (age == RatioCollapseManager.IMPACT_TICK) {
            play(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), .9f, .55f);
            play(SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, .7f, .6f);
        }
    }

    private static void play(SoundEvent event, float volume, float pitch) { play(event.getId(), volume, pitch); }
    private static void play(Identifier id, float volume, float pitch) {
        var sound = new PositionedSoundInstance(id, SoundCategory.PLAYERS, volume * gain, pitch,
                Random.create(), false, 0, SoundInstance.AttenuationType.NONE, 0, 0, 0, true);
        voices.add(sound);
        MinecraftClient.getInstance().getSoundManager().play(sound);
    }

    private static void playAt(Identifier id, float volume, float pitch, Vec3d pos) {
        // relative = false enables 3D positional audio and directional panning in OpenAL when the Ogg file is Mono
        var sound = new PositionedSoundInstance(id, SoundCategory.PLAYERS, volume * gain, pitch,
                Random.create(), false, 0, SoundInstance.AttenuationType.NONE, pos.x, pos.y, pos.z, false);
        voices.add(sound);
        MinecraftClient.getInstance().getSoundManager().play(sound);
    }

    static void reset() {
        var client = MinecraftClient.getInstance();
        if (client != null) for (var voice : voices) client.getSoundManager().stop(voice);
        voices.clear(); world = null; custom = false;
    }
}
