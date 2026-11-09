package net.godwand.skill;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SilentGateSequenceTest {
    private static final Vec3 ANCHOR = new Vec3(100.5, 64, -20.25);

    @Test
    void skyCirclesAreDeterministicAndFormADenseCanopy() {
        float yaw = 37f;
        int n = 220, total = SilentGateSequence.skyTotal(n);
        assertEquals(n + 24, total);
        Vec3 center = SilentGateSequence.gateCenter(ANCHOR, yaw);
        Vec3[] a = new Vec3[total];
        double minDist = 1e9, maxReach = 0;
        int[] tier = new int[3];
        for (int i = 0; i < total; i++) {
            a[i] = SilentGateSequence.skyCircle(ANCHOR, yaw, 1234L, i, n);
            assertEquals(a[i], SilentGateSequence.skyCircle(ANCHOR, yaw, 1234L, i, n));
            assertTrue(a[i].y >= ANCHOR.y + 40 - 8 && a[i].y <= ANCHOR.y + 260, "y " + (a[i].y - ANCHOR.y));
            maxReach = Math.max(maxReach, Math.hypot(a[i].x - ANCHOR.x, a[i].z - ANCHOR.z));
            if (i < n && i % 4 != 3) tier[i % 4]++;
        }
        for (int i = 0; i < total; i++) for (int j = i + 1; j < total; j++) minDist = Math.min(minDist, a[i].distanceTo(a[j]));
        assertTrue(maxReach > 190 && maxReach <= 290, "the canopy reaches over the field: " + maxReach);
        assertTrue(minDist >= MIN_SPACING, "no two circles on top of each other: " + minDist);
        for (int t : tier) assertEquals(n / 4, t, 1, "three tiers of equal strength");
        // Three height layers: a low tier (55..90), a middle one (115..160) and a high one (175..215) above the anchor.
        for (int i = 0; i < n; i++) {
            double h = a[i].y - ANCHOR.y;
            if (i % 4 == 0) assertTrue(h >= 55 && h <= 90, "low tier " + h);
            if (i % 4 == 1) assertTrue(h >= 115 && h <= 160, "middle tier " + h);
            if (i % 4 == 2) assertTrue(h >= 175 && h <= 215, "high tier " + h);
        }
        // Dense: in the target field (30..170 ahead, 100 to the sides) a ground point has a circle within 30 blocks (horizontally) almost everywhere.
        double[] d = {-Math.sin(Math.toRadians(yaw)), Math.cos(Math.toRadians(yaw))};
        var rnd = new java.util.Random(11);
        int covered = 0, samples = 600;
        for (int s = 0; s < samples; s++) {
            double along = 30 + 140 * rnd.nextDouble(), lateral = (rnd.nextDouble() * 2 - 1) * 100;
            Vec3 p = new Vec3(ANCHOR.x + d[0] * along + d[1] * lateral, ANCHOR.y, ANCHOR.z + d[1] * along - d[0] * lateral);
            double best = 1e9;
            for (int i = 0; i < total; i++) best = Math.min(best, Math.hypot(a[i].x - p.x, a[i].z - p.z));
            if (best <= 30) covered++;
        }
        assertTrue(covered > samples * .95, "canopy covers the field: " + covered + "/" + samples);
        // The ring of 24 round the gate body.
        for (int i = n; i < total; i++) {
            double dd = Math.hypot(a[i].x - center.x, a[i].z - center.z);
            assertEquals(90, dd, .01);
            double side = Math.abs((a[i].x - center.x) * Math.cos(Math.toRadians(yaw)) + (a[i].z - center.z) * Math.sin(Math.toRadians(yaw)));
            assertTrue(side >= 62, "ring circle " + i + " stays out of the gate's silhouette: " + side);
            assertTrue(a[i].y > ANCHOR.y + SilentGateSequence.gateBase(ANCHOR.y) && a[i].y < ANCHOR.y + SilentGateSequence.gateBase(ANCHOR.y) + 192, "ring height " + i);
        }
    }
    private static final double MIN_SPACING = 1.5;


    @Test
    void laneCirclesHangOverTheDiveAndEveryCircleBloomsInsideTheBloomWindow() {
        int n = 160;
        for (int i = 3; i < n; i += 4) {
            double u = 2 * SilentGateSequence.laneU(i, n);
            Vec3 c = SilentGateSequence.skyCircle(ANCHOR, 0f, 5L, i, n), lane = SilentGateSequence.flight(ANCHOR, 0f, u);
            assertTrue(c.y - lane.y >= 35 && c.y - lane.y <= 75, "lane circle " + i + " hangs " + (c.y - lane.y) + " over the camera");
            assertTrue(Math.hypot(c.x - lane.x, c.z - lane.z) <= 14.01, "within 14 sideways");
            double born = SilentGateSequence.circleBorn(i, n, 5L);
            assertTrue(born >= SilentGateSequence.FLIGHT_START - 4 && born <= SilentGateSequence.FLIGHT_START - 4 + 14, "lane circles bloom while the dive runs: " + born);
        }
        // Small circles bloom only in 420..470 (a bloom takes 8 ticks), never before or after.
        for (int count : new int[]{0, 48, 160, 256}) for (int i = 0; i < SilentGateSequence.skyTotal(count); i++) {
            double born = SilentGateSequence.circleBorn(i, count, 5L);
            assertTrue(born >= SilentGateSequence.BLOOM_START && born + 8 <= SilentGateSequence.BLOOM_END, "circle " + i + " of " + count + " born at " + born);
        }
        // The dive lane: 5 over the ground, 88 down to 62 blocks ahead of the caster.
        assertEquals(88, SilentGateSequence.flight(ANCHOR, 0f, 0).z - ANCHOR.z, 1e-9);
        assertEquals(5, SilentGateSequence.flight(ANCHOR, 0f, 0).y - ANCHOR.y, 1e-9);
        assertEquals(62, SilentGateSequence.flight(ANCHOR, 0f, 1).z - ANCHOR.z, 1e-9);
        // The canopy blooms across the sky (shots 16): most circles are born before the dive starts.
        int early = 0;
        for (int i = 0; i < SilentGateSequence.skyTotal(n); i++) if (SilentGateSequence.circleBorn(i, n, 5L) < SilentGateSequence.FLIGHT_START) early++;
        assertTrue(early > 70, "canopy blooms: " + early);
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
        assertEquals(1, SilentGateSequence.gateDescent(261));
        assertEquals(1, SilentGateSequence.gateDescent(262));
        assertEquals(0, SilentGateSequence.gateDescent(312));
        boolean dipped = false;
        for (double t = 300.5; t < 312; t += .5) dipped |= SilentGateSequence.gateDescent(t) < 0;
        assertTrue(dipped);
        for (double t = 300; t <= 312; t += .5)
            assertTrue(Math.abs(SilentGateSequence.gateDescent(t)) <= 0.08);
        assertEquals(.5, SilentGateSequence.gateDescent(281), .01, "half way down at the middle of the descent");
        assertEquals(0, SilentGateSequence.gateDescent(300), 1e-9, "touches its stop at 300");
    }

    @Test
    void buildIsThreeHundredAndSixtyTicksOfDirection() {
        assertEquals(480, SilentGateSequence.BUILD_TICKS);
        assertEquals(160, SilentGateSequence.ENDING_TICKS);
        assertTrue(SilentGateSequence.STUCK_END - SilentGateSequence.OPEN_START >= 20, "stuck at least 1 s");
        assertTrue(SilentGateSequence.OPEN_END - SilentGateSequence.STUCK_END >= 45, "separation 365..412");
        assertTrue(SilentGateSequence.OPEN_END - SilentGateSequence.OPEN_FAST_END >= 15, "a long creep into the stop");
        assertTrue(SilentGateSequence.SETTLE_END - SilentGateSequence.DESCENT_END >= 10 && SilentGateSequence.SHOCK - SilentGateSequence.SETTLE_END == 6, "settle, six ticks of silence, then the shockwave");
        assertEquals(340, SilentGateSequence.OPEN_START); assertEquals(365, SilentGateSequence.STUCK_END); assertEquals(412, SilentGateSequence.OPEN_END);
        assertEquals(318, SilentGateSequence.SHOCK); assertEquals(262, SilentGateSequence.DESCENT_START);
        assertEquals(160, SilentGateSequence.CIRCLE_START); assertEquals(232, SilentGateSequence.SLIT_START); assertEquals(420, SilentGateSequence.BLOOM_START);
    }

    @Test
    void gateOpensHeavily() {
        int s = SilentGateSequence.OPEN_START, st = SilentGateSequence.STUCK_END, f = SilentGateSequence.OPEN_FAST_END, o = SilentGateSequence.OPEN_END;
        assertEquals(0, SilentGateSequence.gateOpen(s));
        for (double t = s; t <= st; t += .25) assertTrue(SilentGateSequence.gateOpen(t) < .03, "stuck at " + t);
        assertEquals(.02, SilentGateSequence.gateOpen(st), 1e-9);
        assertTrue(SilentGateSequence.gateOpen(o) >= .98);
        assertEquals(1, SilentGateSequence.gateOpen(o + 20), 0);
        double prev = 0;
        for (double t = s - 20; t <= o + 20; t += .25) {
            double v = SilentGateSequence.gateOpen(t);
            assertTrue(v >= prev, "monotonic at " + t);
            prev = v;
        }
        // Speed: growing through the push (a huge mass getting going), then dying away to a stop with no jump.
        double last = 0;
        for (double t = st + 2; t <= f - 3; t += 2) {
            double v = SilentGateSequence.gateOpen(t + 1) - SilentGateSequence.gateOpen(t);
            assertTrue(v >= last - 1e-9, "the push keeps gaining speed at " + t);
            last = v;
        }
        double vBefore = SilentGateSequence.gateOpen(f - 1) - SilentGateSequence.gateOpen(f - 2), vAfter = SilentGateSequence.gateOpen(f + 2) - SilentGateSequence.gateOpen(f + 1);
        assertEquals(vBefore, vAfter, vBefore * .15, "no jump in speed at the end of the push");
        assertTrue(SilentGateSequence.gateOpen(f) < .88 && SilentGateSequence.gateOpen(f) > .7, "not yet fully open when the push ends: " + SilentGateSequence.gateOpen(f));
        assertTrue(SilentGateSequence.gateOpen(o - 1) - SilentGateSequence.gateOpen(o - 2) < (SilentGateSequence.gateOpen(f + 2) - SilentGateSequence.gateOpen(f + 1)) * .1, "creeps in at the end");
    }

    @Test
    void endingClosesHeavilyAndTheGateClimbsIntoTheCircle() {
        assertEquals(1, SilentGateSequence.closingOpen(0), 0);
        assertEquals(0, SilentGateSequence.closingOpen(SilentGateSequence.END_CLOSE), 0);
        double prev = 1, first = 0;
        for (double e = 0; e <= 40; e += .5) {
            double v = SilentGateSequence.closingOpen(e);
            assertTrue(v <= prev + 1e-12, "closing only closes at " + e);
            prev = v;
            if (e == 3) first = 1 - v;
        }
        assertTrue(first < .02, "it starts slowly: " + first);
        assertEquals(0, SilentGateSequence.gateRise(74), 0);
        assertEquals(2.2, SilentGateSequence.gateRise(125), 1e-9);
        assertTrue(SilentGateSequence.gateRise(80) < .1, "slow start");
        assertTrue(SilentGateSequence.END_RISE_FROM >= SilentGateSequence.END_CLOSE, "the gate climbs only when the doors are shut");
        assertTrue(SilentGateSequence.END_UNWRITE_FROM > SilentGateSequence.END_RISE_FROM && SilentGateSequence.END_UNWRITE_FROM + 50 <= SilentGateSequence.ENDING_TICKS);
    }

    @Test
    void gateShudderIsFeltOnlyWhileTheDoorsPush() {
        assertEquals(0, SilentGateSequence.gateShake(339), 0);
        assertEquals(0, SilentGateSequence.gateShake(412), 0);
        for (double t = 343; t < 404; t += 3) assertTrue(SilentGateSequence.gateShake(t) > .05 && SilentGateSequence.gateShake(t) < .5, "shake at " + t);
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
        assertEquals(372, SilentGateSequence.endTick(200));
    }

    @Test
    void featuredTargetIsWithheldUntilTheOneHeavyBeamLands() {
        for (int barrage : new int[]{200, 400}) {
            int kill = SilentGateSequence.killBarrageAge(barrage);
            assertEquals(SilentGateSequence.shotTick(100, barrage), kill + SilentGateSequence.ACTIVATE_TICKS);
            assertTrue(SilentGateSequence.withheld(0, kill) && SilentGateSequence.withheld(kill, kill));
            assertFalse(SilentGateSequence.withheld(kill + 1, kill));
            assertTrue(kill - SilentGateSequence.HEAVY_GLIDE >= 0, "the circle has time to glide over the target");
        }
        assertEquals(100 - SilentGateSequence.ACTIVATE_TICKS, SilentGateSequence.killBarrageAge(200));
        // At the default barrage the kill shot (authored tick 84) starts exactly when the circle starts to glide.
        assertEquals(SilentGateSequence.shotTick(84, 200) - SilentGateSequence.ACTIVATE_TICKS, SilentGateSequence.killBarrageAge(200) - SilentGateSequence.HEAVY_GLIDE);
        assertEquals(6, SilentGateSequence.HEAVY_DAMAGE_MULT, "the heavy beam carries the old volley's six beams of damage");
    }

    @Test
    void beamsRainNearlyVerticallyFromTheNearestCircleAfterAShortLocalSlide() {
        int n = 220, total = SilentGateSequence.skyTotal(n);
        var rnd = new java.util.Random(3);
        int slid = 0, straight = 0, far = 0;
        for (int t = 0; t < 600; t++) {
            // Targets over the whole field: ahead 20..180, 120 to either side, on the ground.
            Vec3 target = new Vec3(ANCHOR.x + rnd.nextDouble() * 240 - 120, ANCHOR.y + rnd.nextDouble() * 6, ANCHOR.z + 20 + rnd.nextDouble() * 160);
            int c = SilentGateSequence.nearestCircle(ANCHOR, 0f, 9L, n, target, i -> true);
            assertTrue(c >= 0 && c < total);
            Vec3 home = SilentGateSequence.skyCircle(ANCHOR, 0f, 9L, c, n);
            Vec3 move = SilentGateSequence.slide(home, target, false);
            assertTrue(move.length() <= SilentGateSequence.MAX_SLIDE + 1e-9, "a local slide only: " + move.length());
            assertEquals(0, move.y, 0);
            int g = SilentGateSequence.glideTicks(home, target);
            Vec3 at = home.add(move);
            if (move.lengthSqr() == 0) {
                g = g + 0; assertEquals(0, g);
                if (SilentGateSequence.tilt(home, target) <= SilentGateSequence.MAX_TILT_DEGREES + 1e-9) straight++; else far++;
            } else {
                slid++;
                assertTrue(g >= 6 && g <= 11, "slide " + g + " ticks");
                assertTrue(SilentGateSequence.tilt(at, target) <= SilentGateSequence.MAX_TILT_DEGREES + 1e-6, "after the slide the beam leans " + SilentGateSequence.tilt(at, target) + " degrees");
            }
            double d = Math.hypot(home.x - target.x, home.z - target.z);
            for (int i = 0; i < total; i++) { Vec3 p = SilentGateSequence.skyCircle(ANCHOR, 0f, 9L, i, n); if (p.y > target.y) assertTrue(Math.hypot(p.x - target.x, p.z - target.z) >= d - 1e-9); }
        }
        assertTrue(far < 600 * .08, "the dense canopy leaves almost no target needing a long diagonal: " + far);
        assertTrue(slid > 50 && straight > 50, "mixed: " + slid + " slid, " + straight + " straight, " + far + " out of reach");
        // The kill beam slides until it stands directly above the target (at most 20 blocks).
        Vec3 home = new Vec3(0, 150, 0), hit = new Vec3(12, 0, 5);
        Vec3 to = home.add(SilentGateSequence.slide(home, hit, true));
        assertEquals(0, SilentGateSequence.tilt(to, hit), 1e-9);
        assertEquals(20, SilentGateSequence.slide(home, new Vec3(80, 0, 0), true).length(), 1e-9, "never a long flight");
        // A circle that is busy is skipped.
        Vec3 target = new Vec3(ANCHOR.x + 10, ANCHOR.y, ANCHOR.z + 50);
        int first = SilentGateSequence.nearestCircle(ANCHOR, 0f, 9L, n, target, i -> true);
        assertNotEquals(first, SilentGateSequence.nearestCircle(ANCHOR, 0f, 9L, n, target, i -> i != first));
        // Glide curve: nothing before, eased over the glide, held while the beam lives, eased back.
        assertEquals(0, SilentGateSequence.glideK(0, 12), 0);
        assertEquals(.5, SilentGateSequence.glideK(6, 12), 1e-9);
        assertEquals(1, SilentGateSequence.glideK(12 + 3, 12), 0);
        assertEquals(0, SilentGateSequence.glideK(12 + SilentGateSequence.BEAM_TICKS + SilentGateSequence.GLIDE_BACK, 12), 1e-9);
        assertEquals(0, SilentGateSequence.glideK(5, 0), 0, "no glide, no move");
        assertEquals(0, SilentGateSequence.glideTicks(new Vec3(0, 100, 0), new Vec3(5, 0, 0)), "within 8 degrees: no slide");
        assertEquals(9, SilentGateSequence.glideTicks(new Vec3(0, 100, 0), new Vec3(25, 0, 0)), "11 blocks of slide take 9 ticks");
        assertEquals(0, SilentGateSequence.glideTicks(new Vec3(0, 100, 0), new Vec3(60, 0, 0)), "too far for a slide: fires as it stands");
    }
}
