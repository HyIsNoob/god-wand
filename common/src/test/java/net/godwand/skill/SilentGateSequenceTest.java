package net.godwand.skill;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SilentGateSequenceTest {
    private static final Vec3 ANCHOR = new Vec3(100.5, 64, -20.25);

    /** Caster-space {along, lateral} of a point for the given anchor and yaw. */
    private static double[] local(Vec3 p, float yaw) {
        double dx = p.x - ANCHOR.x, dz = p.z - ANCHOR.z, fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
        return new double[]{dx * fx + dz * fz, dx * fz - dz * fx};
    }

    @Test
    void skyCirclesCoverTheWholeFieldEvenlyAndLeaveTheGateClear() {
        float yaw = 37f;
        double area = 2 * SilentGateSequence.FIELD_SIDE * (SilentGateSequence.FIELD_BACK + SilentGateSequence.FIELD_AHEAD);
        for (int n : new int[]{48, 160, 220, 256}) {
            Vec3[] a = new Vec3[n];
            double[][] l = new double[n][];
            int[] tiers = new int[SilentGateSequence.TIER_HEIGHTS.length];
            for (int i = 0; i < n; i++) {
                a[i] = SilentGateSequence.skyCircle(ANCHOR, yaw, 1234L, i, n);
                assertEquals(a[i], SilentGateSequence.skyCircle(ANCHOR, yaw, 1234L, i, n), "deterministic");
                l[i] = local(a[i], yaw);
                // Five level tiers, high over the ground: every circle within 4 blocks of its tier's height.
                assertEquals(SilentGateSequence.TIER_HEIGHTS[SilentGateSequence.tier(i)], a[i].y - ANCHOR.y, 4.01, "tier of " + i);
                assertTrue(a[i].y - ANCHOR.y > 100, "high over the ground");
                tiers[SilentGateSequence.tier(i)]++;
                assertTrue(SilentGateSequence.clearOfGate(l[i][0], l[i][1]), "circle " + i + " stays out of the gate's airspace and avenue: " + l[i][0] + ", " + l[i][1]);
                assertTrue(Math.abs(l[i][1]) <= SilentGateSequence.FIELD_SIDE + 1e-6 && l[i][0] >= -SilentGateSequence.FIELD_BACK - 1e-6 && l[i][0] <= SilentGateSequence.FIELD_AHEAD + 1e-6);
            }
            for (int t : tiers) assertTrue(t >= n / 8, "tiers of similar strength: " + java.util.Arrays.toString(tiers));
            // Spread apart: no two circles close together, and two on the same level never overlap (a circle is 38.4 blocks across).
            double spacing = Math.sqrt(area / n), min = 1e9, minTier = 1e9;
            for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
                double d = Math.hypot(l[i][0] - l[j][0], l[i][1] - l[j][1]);
                min = Math.min(min, d);
                if (SilentGateSequence.tier(i) == SilentGateSequence.tier(j)) minTier = Math.min(minTier, d);
            }
            assertTrue(min > .55 * spacing, n + " circles: nearest pair " + min + " of spacing " + spacing);
            assertTrue(minTier > 38.4, n + " circles: nearest pair on one level " + minTier);
            // Covering: every ground point of the field outside the gate's avenue has a circle within a spacing and a half; nothing is left bare.
            var rnd = new java.util.Random(11);
            for (int k = 0; k < 2000; k++) {
                double along = -SilentGateSequence.FIELD_BACK + (SilentGateSequence.FIELD_BACK + SilentGateSequence.FIELD_AHEAD) * rnd.nextDouble(), lateral = (rnd.nextDouble() * 2 - 1) * SilentGateSequence.FIELD_SIDE;
                // The gate's avenue and airspace included: they are narrow, so the ground under them is as well covered as the rest.
                double best = 1e9;
                for (int i = 0; i < n; i++) best = Math.min(best, Math.hypot(l[i][0] - along, l[i][1] - lateral));
                assertTrue(best <= 1.5 * spacing, n + " circles: a bare patch at " + along + ", " + lateral + ": " + best);
            }
            // Not gathered round the gate: within 160 blocks of it there are no more circles than its share of the ground.
            int near = 0;
            for (int i = 0; i < n; i++) if (Math.hypot(l[i][0] - SilentGateSequence.GATE_AHEAD, l[i][1]) < 160) near++;
            assertTrue(near <= n * Math.PI * 160 * 160 / area, "near the gate: " + near + " of " + n);
        }
        // The gate hangs far off and high: its foot 170 over the ground, the great circle 320 above that.
        Vec3 foot = SilentGateSequence.gateFoot(ANCHOR, yaw, 0);
        assertEquals(SilentGateSequence.GATE_AHEAD, Math.hypot(foot.x - ANCHOR.x, foot.z - ANCHOR.z), 1e-9);
        assertEquals(170, foot.y - ANCHOR.y, 1e-9);
        assertEquals(SilentGateSequence.CIRCLE_ALTITUDE, SilentGateSequence.GATE_BASE + 80 * SilentGateSequence.SCALE, 1e-9);
        assertEquals(SilentGateSequence.doorCenter(ANCHOR, yaw), SilentGateSequence.gateCenter(ANCHOR, yaw));
    }

    @Test
    void circlesAreWrittenInAWaveFromTheGateInsideTheBloomWindow() {
        int n = 256;
        for (int i = 0; i < n; i++) {
            double born = SilentGateSequence.circleBorn(i);
            assertTrue(born - SilentGateSequence.SEED_TICKS >= SilentGateSequence.BLOOM_START && born + SilentGateSequence.FORM_TICKS <= SilentGateSequence.BLOOM_END,
                    "circle " + i + ": seed and writing inside 420..470, born " + born);
        }
        // The wave leaves the gate: of two circles the one farther from the gate is written later.
        for (int i = 0; i < n; i += 3) for (int j = 1; j < n; j += 7) {
            double[] a = local(SilentGateSequence.skyCircle(ANCHOR, 0f, 5L, i, n), 0f), b = local(SilentGateSequence.skyCircle(ANCHOR, 0f, 5L, j, n), 0f);
            double da = Math.hypot(a[0] - SilentGateSequence.GATE_AHEAD, a[1]), db = Math.hypot(b[0] - SilentGateSequence.GATE_AHEAD, b[1]);
            if (da < db - 1) assertTrue(SilentGateSequence.circleBorn(i) < SilentGateSequence.circleBorn(j), i + " before " + j);
        }
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
        int from = SilentGateSequence.DESCENT_START, to = SilentGateSequence.DESCENT_END, settled = SilentGateSequence.SETTLE_END;
        assertTrue(to - from >= 50, "a heavy thing comes down slowly: " + (to - from) + " ticks");
        assertEquals(1, SilentGateSequence.gateDescent(from - 1));
        assertEquals(1, SilentGateSequence.gateDescent(from));
        assertEquals(0, SilentGateSequence.gateDescent(settled));
        boolean dipped = false;
        for (double t = to + .5; t < settled; t += .5) dipped |= SilentGateSequence.gateDescent(t) < 0;
        assertTrue(dipped);
        for (double t = to; t <= settled; t += .5)
            assertTrue(Math.abs(SilentGateSequence.gateDescent(t)) <= 0.08);
        assertEquals(.5, SilentGateSequence.gateDescent((from + to) / 2.0), .01, "half way down at the middle of the descent");
        assertEquals(0, SilentGateSequence.gateDescent(to), 1e-9, "touches its stop");
        // The gate comes down steadily (the shake is the camera's): always on its way down, never a jerk.
        for (double t = from + 1; t < to - 1; t += .25) assertTrue(SilentGateSequence.gateDescent(t) > SilentGateSequence.gateDescent(t + .25), "always on its way down at " + t);
        assertEquals(0, SilentGateSequence.descentStrain(from), 0); assertEquals(0, SilentGateSequence.descentStrain(to), 0);
        assertTrue(SilentGateSequence.descentStrain(from + (to - from) * 4.0 / SilentGateSequence.LURCHES + .1) > .8, "it shudders hardest half way down, as a notch gives");
    }

    @Test
    void buildIsThreeHundredAndSixtyTicksOfDirection() {
        assertEquals(480, SilentGateSequence.BUILD_TICKS);
        assertEquals(265, SilentGateSequence.ENDING_TICKS);
        assertTrue(SilentGateSequence.STUCK_END - SilentGateSequence.OPEN_START >= 20, "stuck at least 1 s");
        assertTrue(SilentGateSequence.OPEN_END - SilentGateSequence.STUCK_END >= 45, "separation 365..412");
        assertTrue(SilentGateSequence.OPEN_END - SilentGateSequence.OPEN_FAST_END >= 15, "a long creep into the stop");
        assertTrue(SilentGateSequence.SETTLE_END - SilentGateSequence.DESCENT_END >= 10 && SilentGateSequence.SHOCK - SilentGateSequence.SETTLE_END == 6, "settle, six ticks of silence, then the shockwave");
        assertEquals(340, SilentGateSequence.OPEN_START); assertEquals(365, SilentGateSequence.STUCK_END); assertEquals(412, SilentGateSequence.OPEN_END);
        assertEquals(236, SilentGateSequence.SHOCK); assertEquals(158, SilentGateSequence.DESCENT_START);
        assertEquals(70, SilentGateSequence.CIRCLE_START); assertEquals(142, SilentGateSequence.SLIT_START); assertEquals(420, SilentGateSequence.BLOOM_START);
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
        assertEquals(1.2, SilentGateSequence.gateRise(SilentGateSequence.END_RISE_TO), 1e-9);
        assertTrue(SilentGateSequence.END_RISE_TO - SilentGateSequence.END_RISE_FROM >= 130, "the gate climbs back slowly (owner, 2026-11-15)");
        assertTrue(SilentGateSequence.gateRise(SilentGateSequence.END_RISE_FROM) < .1, "slow start");
        assertTrue(SilentGateSequence.END_CLOSE >= 70, "the doors close slowly (owner, 2026-11-15)");
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
            assertTrue(n200 >= 4 && n200 <= 9, "circle " + c + " fired " + n200);
            assertTrue(Math.abs(n600 - 3 * n200) <= 3, "circle " + c + " 600: " + n600);
        }
    }

    @Test
    void shotTicksScaleWithBarrage() {
        assertEquals(140, SilentGateSequence.shotTick(140, 200));
        assertEquals(268, SilentGateSequence.shotTick(140, 400));
        assertEquals(477, SilentGateSequence.endTick(200));
    }

    @Test
    void featuredTargetIsWithheldUntilTheOneHeavyBeamLands() {
        for (int barrage : new int[]{200, 400}) {
            int kill = SilentGateSequence.killBarrageAge(barrage);
            assertEquals(SilentGateSequence.shotTick(136, barrage), kill + SilentGateSequence.ACTIVATE_TICKS);
            assertTrue(SilentGateSequence.withheld(0, kill) && SilentGateSequence.withheld(kill, kill));
            assertFalse(SilentGateSequence.withheld(kill + 1, kill));
            assertTrue(kill - SilentGateSequence.HEAVY_CHARGE >= 0, "the circle has time to gather its light");
        }
        assertEquals(136 - SilentGateSequence.ACTIVATE_TICKS, SilentGateSequence.killBarrageAge(200));
        // At the default barrage the kill shot (authored tick 84) starts exactly when the circle starts to charge.
        assertEquals(SilentGateSequence.shotTick(120, 200) - SilentGateSequence.ACTIVATE_TICKS, SilentGateSequence.killBarrageAge(200) - SilentGateSequence.HEAVY_CHARGE);
        assertEquals(6, SilentGateSequence.HEAVY_DAMAGE_MULT, "the heavy beam carries the old volley's six beams of damage");
    }

    @Test
    void beamsComeFromTheNearestFreeCircle() {
        int n = 220;
        var rnd = new java.util.Random(3);
        for (int t = 0; t < 300; t++) {
            // Targets over the whole field, on the ground.
            Vec3 target = new Vec3(ANCHOR.x + rnd.nextDouble() * 400 - 200, ANCHOR.y + rnd.nextDouble() * 6, ANCHOR.z - 150 + rnd.nextDouble() * 450);
            int c = SilentGateSequence.nearestCircle(ANCHOR, 0f, 9L, n, target, i -> true);
            assertTrue(c >= 0 && c < n);
            Vec3 home = SilentGateSequence.skyCircle(ANCHOR, 0f, 9L, c, n);
            double d = Math.hypot(home.x - target.x, home.z - target.z);
            for (int i = 0; i < n; i++) { Vec3 p = SilentGateSequence.skyCircle(ANCHOR, 0f, 9L, i, n); assertTrue(Math.hypot(p.x - target.x, p.z - target.z) >= d - 1e-9); }
        }
        // A circle that is busy is skipped.
        Vec3 target = new Vec3(ANCHOR.x + 10, ANCHOR.y, ANCHOR.z + 50);
        int first = SilentGateSequence.nearestCircle(ANCHOR, 0f, 9L, n, target, i -> true);
        assertNotEquals(first, SilentGateSequence.nearestCircle(ANCHOR, 0f, 9L, n, target, i -> i != first));
        // Beams rain down steeply. With every circle free, any target in the field (the gate's avenue and the ground under the gate too) is hit from close above:
        // within 26 degrees of vertical. A circle much farther off than the nearest never fires: the target waits for one overhead instead.
        var any = new java.util.Random(8);
        for (int t = 0; t < 2000; t++) {
            Vec3 hit = new Vec3(ANCHOR.x + (any.nextDouble() * 2 - 1) * 240, ANCHOR.y + 1, ANCHOR.z - 200 + any.nextDouble() * 560);
            int c = SilentGateSequence.beamCircle(ANCHOR, 0f, 9L, n, hit, i -> true);
            assertEquals(SilentGateSequence.nearestCircle(ANCHOR, 0f, 9L, n, hit, i -> true), c);
            assertTrue(SilentGateSequence.lean(SilentGateSequence.skyCircle(ANCHOR, 0f, 9L, c, n), hit) < 26, "leans " + SilentGateSequence.lean(SilentGateSequence.skyCircle(ANCHOR, 0f, 9L, c, n), hit));
            // With the nearest busy, the next one fires only if it is not a long way farther off.
            int next = SilentGateSequence.nearestCircle(ANCHOR, 0f, 9L, n, hit, i -> i != c), fired = SilentGateSequence.beamCircle(ANCHOR, 0f, 9L, n, hit, i -> i != c);
            Vec3 a = SilentGateSequence.skyCircle(ANCHOR, 0f, 9L, c, n), b = SilentGateSequence.skyCircle(ANCHOR, 0f, 9L, next, n);
            boolean close = Math.hypot(b.x - hit.x, b.z - hit.z) <= Math.hypot(a.x - hit.x, a.z - hit.z) + SilentGateSequence.MAX_DETOUR;
            assertEquals(close ? next : -1, fired);
        }
        assertEquals(0, SilentGateSequence.lean(new Vec3(0, 100, 0), new Vec3(0, 0, 0)), 1e-9);
        assertEquals(45, SilentGateSequence.lean(new Vec3(0, 100, 0), new Vec3(100, 0, 0)), 1e-9);
    }

    @Test void theCrackStaysOpenUntilTheGateHasGoneIn() {
        assertEquals(0, SilentGateSequence.endSlit(0), 0);
        assertEquals(0, SilentGateSequence.endSlit(SilentGateSequence.ENDING_TICKS), 1e-9);
        // Open from the start of the climb until the gate has gone through it (rise 1), shut soon after.
        for (double e = SilentGateSequence.END_RISE_FROM; e < SilentGateSequence.ENDING_TICKS; e += 2) {
            if (SilentGateSequence.gateRise(e) < 1) assertTrue(SilentGateSequence.endSlit(e) > .99, "open at " + e);
            if (SilentGateSequence.gateRise(e) > 1.18) assertEquals(0, SilentGateSequence.endSlit(e), 0, "shut at " + e);
        }
    }
}
