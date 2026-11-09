package net.godwand.skill;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SilentGateSequenceTest {
    private static final Vec3 ANCHOR = new Vec3(100.5, 64, -20.25);

    @Test
    void skyCirclesAreDeterministicAndSpread() {
        float yaw = 37f;
        int n = 160, total = SilentGateSequence.skyTotal(n);
        assertEquals(n + 24, total);
        Vec3 center = SilentGateSequence.gateCenter(ANCHOR, yaw);
        Vec3[] a = new Vec3[total];
        double minDist = 1e9, maxReach = 0;
        for (int i = 0; i < total; i++) {
            a[i] = SilentGateSequence.skyCircle(ANCHOR, yaw, 1234L, i, n);
            assertEquals(a[i], SilentGateSequence.skyCircle(ANCHOR, yaw, 1234L, i, n));
            assertTrue(a[i].y >= ANCHOR.y + 40 - 8 && a[i].y <= ANCHOR.y + 260, "y " + (a[i].y - ANCHOR.y));
            maxReach = Math.max(maxReach, Math.hypot(a[i].x - ANCHOR.x, a[i].z - ANCHOR.z));
        }
        for (int i = 0; i < total; i++) for (int j = i + 1; j < total; j++) minDist = Math.min(minDist, a[i].distanceTo(a[j]));
        assertTrue(maxReach > 190 && maxReach <= 290, "spread over a radius of about 220 round the caster: " + maxReach);
        assertTrue(minDist >= MIN_SPACING, "no two circles on top of each other: " + minDist);
        // Denser round the gate than round the caster.
        int nearGate = 0, nearCaster = 0;
        for (int i = 0; i < n; i++) {
            if (Math.hypot(a[i].x - center.x, a[i].z - center.z) < 120) nearGate++;
            if (Math.hypot(a[i].x - ANCHOR.x, a[i].z - ANCHOR.z) < 120) nearCaster++;
        }
        assertTrue(nearGate > n * .45 && nearGate > nearCaster, "denser near the gate: " + nearGate + " vs " + nearCaster);
        // The ring of 24 round the gate body.
        for (int i = n; i < total; i++) {
            double d = Math.hypot(a[i].x - center.x, a[i].z - center.z);
            assertEquals(78, d, .01);
            assertTrue(a[i].y > ANCHOR.y + SilentGateSequence.gateBase(ANCHOR.y) && a[i].y < ANCHOR.y + SilentGateSequence.gateBase(ANCHOR.y) + 192, "ring height " + i);
        }
    }
    private static final double MIN_SPACING = 1.5;

    @Test
    void laneCirclesBloomJustAheadOfTheFlyingCamera() {
        int n = 160;
        for (int i = 3; i < n; i += 4) {
            Vec3 c = SilentGateSequence.skyCircle(ANCHOR, 0f, 5L, i, n), lane = SilentGateSequence.flight(ANCHOR, 0f, SilentGateSequence.laneU(i, n));
            double d = c.distanceTo(lane);
            assertTrue(d >= 1.5 && d <= 9, "lane circle " + i + " is " + d + " from the camera path");
            double born = SilentGateSequence.circleBorn(i, n, 5L);
            // The camera is at that lane point when flightU(t) = laneU: the circle starts to bloom a few ticks before.
            double passes = SilentGateSequence.FLIGHT_START + 40 * Math.sqrt(SilentGateSequence.laneU(i, n));
            assertEquals(passes - 4, born, 1e-9);
            assertEquals(SilentGateSequence.laneU(i, n), SilentGateSequence.flightU(passes), 1e-9);
        }
        for (int i = 0; i < SilentGateSequence.skyTotal(n); i++) {
            double born = SilentGateSequence.circleBorn(i, n, 5L);
            assertTrue(born >= SilentGateSequence.FLIGHT_START - 4 && born < SilentGateSequence.FLIGHT_END + 20, "born " + born);
        }
        assertEquals(4, SilentGateSequence.doorCenter(ANCHOR, 0f).distanceTo(SilentGateSequence.flight(ANCHOR, 0f, 0)), 1e-9, "the lane starts in the doorway");
    }

    @Test
    void groundRingAlternatesGoldAndGreen() {
        for (int i = 0; i < 8; i++) {
            assertEquals(i % 2 == 0, SilentGateSequence.groundCircleGold(i));
            Vec3 p = SilentGateSequence.groundCircle(ANCHOR, 123f, i);
            assertEquals(6, Math.hypot(p.x - ANCHOR.x, p.z - ANCHOR.z), 0.01);
            assertEquals(ANCHOR.y + 3, p.y, 0.01);
            // Behind the caster: opposite the cast direction (yaw 123 forward = (-sin, cos)).
            double fx = -Math.sin(Math.toRadians(123)), fz = Math.cos(Math.toRadians(123));
            assertTrue((p.x - ANCHOR.x) * fx + (p.z - ANCHOR.z) * fz < 0.01, "behind " + i);
        }
    }

    @Test
    void gateDescendsWithOvershootAndSettles() {
        assertEquals(1, SilentGateSequence.gateDescent(149));
        assertEquals(1, SilentGateSequence.gateDescent(150));
        assertEquals(0, SilentGateSequence.gateDescent(203));
        boolean dipped = false;
        for (double t = 188.5; t < 203; t += .5) dipped |= SilentGateSequence.gateDescent(t) < 0;
        assertTrue(dipped);
        for (double t = 190; t <= 203; t += .5)
            assertTrue(Math.abs(SilentGateSequence.gateDescent(t)) <= 0.08);
        assertEquals(.5, SilentGateSequence.gateDescent(169), .01, "half way down at the middle of S6");
    }

    @Test
    void buildIsThreeHundredAndSixtyTicksOfDirection() {
        assertEquals(360, SilentGateSequence.BUILD_TICKS);
        assertEquals(215, SilentGateSequence.OPEN_START);
        assertEquals(285, SilentGateSequence.OPEN_END);
    }

    @Test
    void gateOpensHeavily() {
        assertEquals(0, SilentGateSequence.gateOpen(215));
        for (double t = 215; t <= 228; t += .25) assertTrue(SilentGateSequence.gateOpen(t) < .03, "stuck at " + t);
        assertEquals(.02, SilentGateSequence.gateOpen(228), 1e-9);
        assertTrue(SilentGateSequence.gateOpen(285) >= .98);
        assertEquals(1, SilentGateSequence.gateOpen(300), 0);
        double prev = 0;
        for (double t = 200; t <= 300; t += .25) {
            double v = SilentGateSequence.gateOpen(t);
            assertTrue(v >= prev, "monotonic at " + t);
            prev = v;
        }
        // Speed: growing through the push (a huge mass getting going), then dying away to a stop with no jump at 270.
        double last = 0;
        for (double t = 230; t <= 268; t += 2) {
            double v = SilentGateSequence.gateOpen(t + 1) - SilentGateSequence.gateOpen(t);
            assertTrue(v >= last - 1e-9, "the push keeps gaining speed at " + t);
            last = v;
        }
        double v268 = SilentGateSequence.gateOpen(269) - SilentGateSequence.gateOpen(268), v271 = SilentGateSequence.gateOpen(271) - SilentGateSequence.gateOpen(270);
        assertEquals(v268, v271, v268 * .15, "no jump in speed at 270");
        assertTrue(SilentGateSequence.gateOpen(270) < .85 && SilentGateSequence.gateOpen(270) > .75, "not yet fully open at 270: " + SilentGateSequence.gateOpen(270));
        assertTrue(SilentGateSequence.gateOpen(284) - SilentGateSequence.gateOpen(283) < (SilentGateSequence.gateOpen(272) - SilentGateSequence.gateOpen(271)) * .1, "creeps in at the end");
    }

    @Test
    void gateShudderIsFeltOnlyWhileTheDoorsPush() {
        assertEquals(0, SilentGateSequence.gateShake(214), 0);
        assertEquals(0, SilentGateSequence.gateShake(285), 0);
        for (double t = 216; t < 275; t += 3) assertTrue(SilentGateSequence.gateShake(t) > .05 && SilentGateSequence.gateShake(t) < .5, "shake at " + t);
    }

    private static int shots(int circle, int barrageTicks) {
        int n = 0;
        boolean last = false;
        for (int age = 0; age < barrageTicks; age++) {
            boolean f = SilentGateSequence.fires(circle, age, barrageTicks, 99L);
            assertFalse(f && last, "consecutive ticks");
            if (f) n++;
            last = f;
        }
        return n;
    }

    @Test
    void everyCircleFiresSeveralTimesAcrossTheBarrage() {
        for (int c = 0; c < 48; c++) {
            int n200 = shots(c, 200), n600 = shots(c, 600);
            assertTrue(n200 >= 4 && n200 <= 8, "circle " + c + " fired " + n200);
            assertTrue(Math.abs(n600 - 3 * n200) <= 3, "circle " + c + " 600: " + n600);
        }
    }

    @Test
    void shotTicksScaleWithBarrage() {
        assertEquals(140, SilentGateSequence.shotTick(140, 200));
        assertEquals(268, SilentGateSequence.shotTick(140, 400));
        assertEquals(272, SilentGateSequence.endTick(200));
    }

    @Test
    void featuredTargetIsWithheldThenSixBeamsLandWithinThreeTicks() {
        for (int barrage : new int[]{200, 400}) {
            int kill = SilentGateSequence.killBarrageAge(barrage);
            assertEquals(SilentGateSequence.shotTick(75, barrage), kill + SilentGateSequence.ACTIVATE_TICKS);
            assertTrue(SilentGateSequence.withheld(0, kill) && SilentGateSequence.withheld(kill + 2, kill));
            assertFalse(SilentGateSequence.withheld(kill + 3, kill));
            var all = new java.util.HashSet<Integer>();
            for (int a = kill - 2; a < kill + 6; a++) {
                int[] v = SilentGateSequence.volley(a, kill, 12345L, 48);
                assertEquals(a >= kill && a < kill + 3 ? 2 : 0, v.length, "age " + a);
                for (int i : v) all.add(i);
            }
            assertEquals(6, all.size(), "six distinct sky circles");
            assertTrue(all.stream().allMatch(i -> i >= 0 && i < 48));
        }
        assertEquals(75 - SilentGateSequence.ACTIVATE_TICKS, SilentGateSequence.killBarrageAge(200));
    }
}
