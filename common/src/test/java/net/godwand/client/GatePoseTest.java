package net.godwand.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GatePoseTest {
    private static final double END = 12 + 200;
    /** Pose at an age of the 300-tick authoring timeline (BUILD is 480: the clock is stretched, see GatePose.poseClock). */
    private static GatePose.Joints at(double old) { return GatePose.joints(GatePose.fromOld(old), -1, END); }

    @Test void beatAnglesAtTheKeyTicks() {
        var hover = at(2);
        assertEquals(-.30, hover.rp(), .03); assertEquals(GatePose.REST.lp(), hover.lp(), .03, "only the right hand is raised");
        var spin = at(35);
        assertEquals(GatePose.FACE.rp(), spin.rp(), .03); assertEquals(GatePose.LIPS.lp(), spin.lp(), .03, "the finger is at the lips before the wand is spun");
        assertEquals(GatePose.REST.lp(), at(10).lp(), .03, "and not before the camera is back on the caster");
        var shh = at(100);
        assertEquals(GatePose.LIPS.lp(), shh.lp(), .03); assertEquals(GatePose.HIP.rp(), shh.rp(), .03); assertEquals(.20, shh.head(), .03);
        assertEquals(GatePose.LIPS.lp(), at(240).lp(), .03);
        var lowered = at(345);
        assertEquals(GatePose.REST.lp(), lowered.lp(), .03); assertTrue(lowered.rp() < GatePose.HIP.rp());
        var wait = at(300);
        assertEquals(GatePose.POINT.rp(), wait.rp(), .03);
        assertTrue(Math.abs(wait.rp()) > 2.8);
        assertEquals(GatePose.AIM.rp(), GatePose.joints(480, 20, END).rp(), .03);
        assertEquals(GatePose.REST.rp(), GatePose.joints(560, END + 40, END).rp(), .03);
    }

    @Test void commandSnapsFromSkyToForwardInUnderTwelveTicks() {
        assertTrue(GatePose.joints(480, 0, END).rp() < -2.8);
        assertTrue(GatePose.joints(480, 8, END).rp() > -1.2);
        assertEquals(GatePose.AIM.rp(), GatePose.joints(480, 20, END).rp(), .03);
    }

    @Test void heavyEaseOvershootsALittleAndSettles() {
        double peak = 0; for (double x = 0; x <= 1; x += .01) peak = Math.max(peak, GatePose.heavy(x));
        assertTrue(peak > 1.01 && peak < 1.06, "overshoot " + peak);
        assertEquals(0, GatePose.heavy(0), 1e-9); assertEquals(1, GatePose.heavy(1), 1e-9);
        assertTrue(GatePose.heavy(.1) < .02, "slow start");
    }

    @Test void spinIsOneFullTurnThatEndsOnTheStartAngle() {
        assertEquals(0, GatePose.spinAngle(10), 1e-9);
        assertTrue(GatePose.spinAngle(23) < 0);
        assertEquals(2 * Math.PI, GatePose.spinAngle(GatePose.SPIN_TO), 1e-9);
        double prev = GatePose.spinAngle(26);
        for (double t = 27; t <= 40; t += 1) { double a = GatePose.spinAngle(t); assertTrue(a > prev); prev = a; }
        assertEquals(2 * Math.PI, GatePose.spinAngle(60), 1e-9);
    }

    @Test void wandFollowsTheHandAndLeavesItAtTheRelease() {
        Vec3 feet = new Vec3(0, 70, 0);
        var hand = GatePose.rightHand(GatePose.joints(GatePose.fromOld(35), -1, END), feet, 0);
        var wand = GatePose.wand(GatePose.fromOld(35), -1, END, feet, 0);
        assertTrue(wand.pos().distanceTo(hand.pos()) < .25, "spinning around the hand");
        assertEquals(1, wand.axis().length(), 1e-6); assertEquals(0, wand.axis().dot(wand.face()), 1e-6);
        assertTrue(Math.abs(wand.axis().dot(hand.axis())) < .35, "spin plane is roughly perpendicular to the forearm: " + wand.axis().dot(hand.axis()));
        var grip = GatePose.wand(GatePose.fromOld(100), -1, END, feet, 0);
        assertTrue(Math.abs(grip.axis().y) < .2, "horizontal in the hand");
        assertTrue(grip.pos().distanceTo(GatePose.rightHand(GatePose.joints(GatePose.fromOld(100), -1, END), feet, 0).pos()) < .05);
        var floating = GatePose.wand(GatePose.fromOld(320), -1, END, feet, 0);
        assertTrue(floating.axis().y > .9, "upright beside the caster");
        assertTrue(floating.pos().subtract(feet).horizontalDistance() > .6);
        var chest = GatePose.wand(GatePose.fromOld(5), -1, END, feet, 0);
        assertTrue(chest.axis().y > .9 && chest.pos().z > .4 && chest.pos().y > 71, "floating before the chest");
    }

    @Test void leftHandReachesTheMouthArea() {
        var lips = GatePose.leftHand(GatePose.joints(GatePose.fromOld(120), -1, END), Vec3.ZERO, 0);
        assertTrue(lips.pos().y > 1.3 && lips.pos().y < 1.75, "at face height: " + lips.pos());
        assertTrue(Math.abs(lips.pos().x) < .3 && lips.pos().z > .25, "in front of the face: " + lips.pos());
    }

    @Test void scriptV3BeatsInCastTicks() {
        Vec3 feet = new Vec3(0, 70, 0);
        // Spin: after the "shh", while the camera draws back (305..330): a wind-up, then one full turn; the wand is back in the hand after the catch.
        assertEquals(0, GatePose.spinAngle(GatePose.poseClock(280)), 1e-9);
        assertTrue(GatePose.spinAngle(GatePose.poseClock(306)) < 0.1);
        assertEquals(2 * Math.PI, GatePose.spinAngle(GatePose.poseClock(330)), 1e-6);
        double prev = GatePose.spinAngle(GatePose.poseClock(310));
        for (double t = 311; t <= 322; t++) { double a = GatePose.spinAngle(GatePose.poseClock(t)); assertTrue(a > prev, "turning at " + t); prev = a; }
        // Shh: the finger reaches the lips by tick 286, before the spin, and stays there.
        assertEquals(GatePose.LIPS.lp(), GatePose.joints(288, -1, END).lp(), .03);
        assertTrue(GatePose.joints(250, -1, END).lp() > GatePose.LIPS.lp() + .5, "not yet at the lips at 250");
        assertEquals(GatePose.LIPS.lp(), GatePose.joints(380, -1, END).lp(), .03);
        // The arm points at the sky from ~440 on.
        assertTrue(GatePose.joints(450, -1, END).rp() < -2.6, "rising: " + GatePose.joints(450, -1, END).rp());
        assertEquals(GatePose.POINT.rp(), GatePose.joints(470, -1, END).rp(), .03);
        assertTrue(GatePose.joints(400, -1, END).rp() > -1);
        // First person: until 62 the wand hangs 1.5 blocks before the eyes at eye height; it then drifts beside the caster.
        for (double t : new double[]{0, 20, 45, 61}) {
            var w = GatePose.wand(t, -1, END, feet, 0);
            assertEquals(1.5, w.pos().z, .05, "tick " + t);
            assertEquals(71.5, w.pos().y, .1);
            assertTrue(Math.abs(w.pos().x) < .1);
        }
        assertTrue(GatePose.wand(320, -1, END, feet, 0).pos().z < 1.3, "away from the face by 320");
    }

    @Test void manaGainShape() {
        assertEquals(0, GatePose.manaGain(frame(0, -1)), 1e-9);
        double strong = GatePose.manaGain(frame(40, -1)), calm = GatePose.manaGain(frame(440, -1)), gone = GatePose.manaGain(frame(640, 502));
        assertTrue(strong > .95 && calm < strong && calm > .4 && gone < 1e-6, strong + " " + calm + " " + gone);
    }
    private static SilentGateRenderer.Frame frame(double age, double cmd) {
        return new SilentGateRenderer.Frame(1, 0, cmd < 0 ? 0 : 2, age, cmd, Vec3.ZERO, 0, 0, 0, 200, -1);
    }
}
