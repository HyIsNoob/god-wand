package net.godwand.client;

import net.godwand.skill.BlueRedRules;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class PurpleCinematicTest {
    static final Vec3 CENTER = new Vec3(0, 70, 22);

    @AfterEach void clean() { PurpleCinematic.reset(); }

    @Test void onlyParticipantsAreActive() {
        PurpleCinematic.start(7, 9, 9, CENTER, new Vec3(0, 66, 0));
        assertTrue(PurpleCinematic.active());
        PurpleCinematic.reset();
        PurpleCinematic.start(7, 9, 7, CENTER, new Vec3(0, 66, 0));
        assertTrue(PurpleCinematic.active(), "the Red shooter watches too");
        PurpleCinematic.reset();
        PurpleCinematic.start(7, 9, 11, CENTER, new Vec3(0, 66, 0));
        assertFalse(PurpleCinematic.active(), "a bystander keeps their camera");
    }

    @Test void endsAt130() {
        PurpleCinematic.start(7, 7, 7, CENTER, new Vec3(0, 66, 0));
        for (int i = 0; i < BlueRedRules.PURPLE_CINEMATIC - 1; i++) PurpleCinematic.tick();
        assertTrue(PurpleCinematic.active());
        PurpleCinematic.tick();
        assertFalse(PurpleCinematic.active());
        assertFalse(PurpleCinematic.movementLocked());
    }

    @Test void farParticipantPulledWithin40() {
        for (double age : new double[]{0, 40, 80, 100, 129}) {
            Vec3 cam = PurpleCinematic.pose(CENTER, new Vec3(0, 66, -300), age).position();
            assertTrue(cam.distanceTo(CENTER) <= 40 + 22, "age " + age + ": " + cam.distanceTo(CENTER));
            assertTrue(cam.distanceTo(CENTER) >= 8);
        }
    }

    @Test void looksAtThePurpleSphere() {
        var pose = PurpleCinematic.pose(CENTER, new Vec3(0, 66, 0), 30);
        Vec3 look = Vec3.directionFromRotation(pose.pitch(), pose.yaw());
        Vec3 toCenter = CENTER.subtract(pose.position()).normalize();
        assertTrue(look.dot(toCenter) > .99, "camera aims at the sphere");
    }

    @Test void poseIsContinuous() {
        Vec3 eye = new Vec3(3, 66, 0);
        Vec3 prev = PurpleCinematic.pose(CENTER, eye, 0).position();
        for (double age = .5; age <= BlueRedRules.PURPLE_CINEMATIC; age += .5) {
            Vec3 now = PurpleCinematic.pose(CENTER, eye, age).position();
            double limit = age > BlueRedRules.PURPLE_DETONATE && age < BlueRedRules.PURPLE_DETONATE + 16 ? 1.5 : .5;
            assertTrue(now.distanceTo(prev) < limit, "jump at age " + age + ": " + now.distanceTo(prev));
            prev = now;
        }
    }
}
