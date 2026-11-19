package net.godwand.client;

import net.godwand.skill.BlueRedRules;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import static net.godwand.skill.BlueRedRules.*;
import static org.junit.jupiter.api.Assertions.*;

/** Owner's direction (2026-11-16): blink, close shot of the orbs swirling in the world, quick fade to the 2D void where they are pushed together and
 *  Purple swallows them, back to the world with the camera racing round Purple, the blast throws the camera far out. */
class PurpleCinematicTest {
    static final Vec3 CENTER = new Vec3(0, 70, 22), EYE = new Vec3(0, 66, 0);

    @AfterEach void clean() { PurpleCinematic.reset(); }

    /** Where the orbs are at this age: rising from the Blue to the burst point (CENTER) during the swirl. */
    static Vec3 at(double age) { return CENTER.add(0, BlueRedRules.purpleRise(age), 0); }

    @Test void timelineIsInOrder() {
        assertTrue(PURPLE_BLINK < PURPLE_CONTACT && PURPLE_CONTACT < PURPLE_PLAY && PURPLE_PLAY < PURPLE_LIFT_END && PURPLE_LIFT_END < PURPLE_FACE && PURPLE_FACE < PURPLE_SWIRL_END
                && PURPLE_SWIRL_END < PURPLE_MERGE && PURPLE_MERGE < PURPLE_VOID_END
                && PURPLE_VOID_END < PURPLE_DETONATE && PURPLE_DETONATE < PURPLE_CINEMATIC && PURPLE_CINEMATIC < PURPLE_TERRAIN_END);
    }

    @Test void onlyParticipantsAreActive() {
        PurpleCinematic.start(7, 9, 9, CENTER, EYE);
        assertTrue(PurpleCinematic.active());
        PurpleCinematic.reset();
        PurpleCinematic.start(7, 9, 7, CENTER, EYE);
        assertTrue(PurpleCinematic.active(), "the Red shooter watches too");
        PurpleCinematic.reset();
        PurpleCinematic.start(7, 9, 11, CENTER, EYE);
        assertFalse(PurpleCinematic.active(), "a bystander keeps their camera");
        assertFalse(PurpleCinematic.inVoid(), "and never sees the void");
    }

    @Test void swirlInTheWorldThenTheVoidThenTheWorld() {
        PurpleCinematic.start(7, 7, 7, CENTER, EYE);
        for (int i = 0; i < PURPLE_SWIRL_END - 1; i++) PurpleCinematic.tick();
        assertFalse(PurpleCinematic.inVoid(), "the orbs first swirl in the world");
        for (int i = 0; i < 16; i++) PurpleCinematic.tick();
        assertTrue(PurpleCinematic.blackout(), "then the 2D void");
        while (PurpleCinematic.age() < PURPLE_MERGE + 2) PurpleCinematic.tick();
        assertTrue(PurpleCinematic.inVoid() && !PurpleCinematic.blackout(), "the void thins out over the world Purple");
        while (PurpleCinematic.age() < PURPLE_VOID_END) PurpleCinematic.tick();
        assertFalse(PurpleCinematic.inVoid());
        assertTrue(PurpleCinematic.active());
    }

    @Test void endsAtTheCinematicEnd() {
        PurpleCinematic.start(7, 7, 7, CENTER, EYE);
        for (int i = 0; i < PURPLE_CINEMATIC - 1; i++) PurpleCinematic.tick();
        assertTrue(PurpleCinematic.active());
        PurpleCinematic.tick();
        assertFalse(PurpleCinematic.active());
        assertFalse(PurpleCinematic.movementLocked());
    }

    @Test void blinkStartsFromTheOwnEyesThenCutsClose() {
        assertTrue(PurpleCinematic.pose(CENTER, EYE, 0).position().distanceTo(EYE) < 1e-6);
        double close = PurpleCinematic.pose(CENTER, EYE, PURPLE_BLINK + 2).position().distanceTo(at(PURPLE_BLINK + 2));
        assertTrue(close >= 2 && close <= 9, "a close shot of the swirling orbs: " + close);
    }

    @Test void racesRoundPurpleThenIsThrownFar() {
        double before = 0, turned = 0;
        Vec3 prev = null;
        for (double age = PurpleCinematic.FAR_END + 1; age < PURPLE_DETONATE; age += 1) {
            if (PurpleCinematic.strikeShot(age) >= 0) { prev = null; continue; }
            Vec3 p = PurpleCinematic.pose(CENTER, EYE, age).position().subtract(CENTER);
            if (prev != null) turned += Math.abs(Math.atan2(prev.x * p.z - prev.z * p.x, prev.x * p.x + prev.z * p.z));
            prev = p;
            before = p.length();
        }
        assertTrue(turned > .3, "a slow move round it: " + turned);
        double after = PurpleCinematic.pose(CENTER, EYE, PURPLE_DETONATE + 20).position().distanceTo(CENTER);
        assertTrue(after > before + 15, before + " -> " + after);
    }

    @Test void looksAtThePurpleSphere() {
        // (Early in the swirl the camera follows Red, so it is not checked there.)
        for (double age : new double[]{PURPLE_VOID_END + 10, PURPLE_DETONATE + 10}) {
            var pose = PurpleCinematic.pose(CENTER, EYE, age);
            Vec3 look = Vec3.directionFromRotation(pose.pitch(), pose.yaw());
            assertTrue(look.dot(at(age).subtract(pose.position()).normalize()) > .99, "age " + age);
        }
    }

    @Test void smoothBetweenTheCuts() {
        Vec3 prev = PurpleCinematic.pose(CENTER, EYE, 0).position();
        for (double age = .5; age <= PURPLE_CINEMATIC; age += .5) {
            Vec3 now = PurpleCinematic.pose(CENTER, EYE, age).position();
            boolean cut = Math.abs(age - PURPLE_BLINK) < .6 || Math.abs(age - PURPLE_SWIRL_END) < .6 || Math.abs(age - PURPLE_MERGE) < .6 || Math.abs(age - PurpleCinematic.HIGH_SHOT) < .6
                    || PurpleCinematic.nearStrikeCut(age) || PurpleCinematic.strikeShot(age) >= 0;
            double limit = age > PURPLE_DETONATE && age < PURPLE_DETONATE + 28 ? 5 : age < PURPLE_SWIRL_END ? 2 : 1.4;
            if (!cut) assertTrue(now.distanceTo(prev) < limit, "jump at age " + age + ": " + now.distanceTo(prev));
            prev = now;
        }
    }
}
