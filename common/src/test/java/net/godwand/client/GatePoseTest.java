package net.godwand.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GatePoseTest {
    private static final double END = 12 + 200;
    private static GatePose.Joints at(double age) { return GatePose.joints(age, -1, END); }

    @Test void beatAnglesAtTheKeyTicks() {
        var hover = at(5);
        assertEquals(-.30, hover.rp(), .03); assertEquals(-.30, hover.lp(), .03);
        var spin = at(35);
        assertEquals(GatePose.FACE.rp(), spin.rp(), .03); assertTrue(spin.lp() > -.2);
        var shh = at(100);
        assertEquals(GatePose.LIPS.lp(), shh.lp(), .03); assertEquals(GatePose.HIP.rp(), shh.rp(), .03); assertEquals(.20, shh.head(), .03);
        assertEquals(GatePose.LIPS.lp(), at(240).lp(), .03);
        var lowered = at(345);
        assertEquals(GatePose.REST.lp(), lowered.lp(), .03); assertTrue(lowered.rp() < GatePose.HIP.rp());
        var wait = at(380);
        assertEquals(GatePose.POINT.rp(), wait.rp(), .03);
        assertTrue(Math.abs(wait.rp()) > 2.8);
        assertEquals(GatePose.AIM.rp(), GatePose.joints(380, 20, END).rp(), .03);
        assertEquals(GatePose.REST.rp(), GatePose.joints(460, END + 40, END).rp(), .03);
    }

    @Test void commandSnapsFromSkyToForwardInUnderTwelveTicks() {
        assertTrue(GatePose.joints(380, 0, END).rp() < -2.8);
        assertTrue(GatePose.joints(380, 8, END).rp() > -1.2);
        assertEquals(GatePose.AIM.rp(), GatePose.joints(380, 20, END).rp(), .03);
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
        var hand = GatePose.rightHand(GatePose.joints(35, -1, END), feet, 0);
        var wand = GatePose.wand(35, -1, END, feet, 0);
        assertTrue(wand.pos().distanceTo(hand.pos()) < .25, "spinning around the hand");
        assertEquals(1, wand.axis().length(), 1e-6); assertEquals(0, wand.axis().dot(wand.face()), 1e-6);
        assertTrue(Math.abs(wand.axis().dot(hand.axis())) < .35, "spin plane is roughly perpendicular to the forearm: " + wand.axis().dot(hand.axis()));
        var grip = GatePose.wand(100, -1, END, feet, 0);
        assertTrue(Math.abs(grip.axis().y) < .2, "horizontal in the hand");
        assertTrue(grip.pos().distanceTo(GatePose.rightHand(GatePose.joints(100, -1, END), feet, 0).pos()) < .05);
        var floating = GatePose.wand(380, -1, END, feet, 0);
        assertTrue(floating.axis().y > .9, "upright beside the caster");
        assertTrue(floating.pos().subtract(feet).horizontalDistance() > .6);
        var chest = GatePose.wand(5, -1, END, feet, 0);
        assertTrue(chest.axis().y > .9 && chest.pos().z > .4 && chest.pos().y > 71, "floating before the chest");
    }

    @Test void leftHandReachesTheMouthArea() {
        var lips = GatePose.leftHand(GatePose.joints(120, -1, END), Vec3.ZERO, 0);
        assertTrue(lips.pos().y > 1.3 && lips.pos().y < 1.75, "at face height: " + lips.pos());
        assertTrue(Math.abs(lips.pos().x) < .3 && lips.pos().z > .25, "in front of the face: " + lips.pos());
    }

    @Test void manaGainShape() {
        assertEquals(0, GatePose.manaGain(frame(0, -1)), 1e-9);
        double strong = GatePose.manaGain(frame(40, -1)), calm = GatePose.manaGain(frame(310, -1)), gone = GatePose.manaGain(frame(500, 272));
        assertTrue(strong > .95 && calm < strong && calm > .4 && gone < 1e-6, strong + " " + calm + " " + gone);
    }
    private static SilentGateRenderer.Frame frame(double age, double cmd) {
        return new SilentGateRenderer.Frame(1, 0, cmd < 0 ? 0 : 2, age, cmd, Vec3.ZERO, 0, 0, 0, 200, -1);
    }
}
