package net.godwand.skill;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SilentGateSequenceTest {
    private static final Vec3 ANCHOR = new Vec3(100.5, 64, -20.25);

    @Test
    void skyCirclesAreDeterministicAndSpread() {
        float yaw = 37f;
        Vec3 center = SilentGateSequence.gateCenter(ANCHOR, yaw);
        Vec3[] a = new Vec3[48];
        for (int i = 0; i < 48; i++) {
            a[i] = SilentGateSequence.skyCircle(ANCHOR, yaw, 1234L, i, 48);
            assertEquals(a[i], SilentGateSequence.skyCircle(ANCHOR, yaw, 1234L, i, 48));
            assertTrue(a[i].y >= ANCHOR.y + 90 && a[i].y <= ANCHOR.y + 210, "y " + a[i].y);
            assertTrue(Math.hypot(a[i].x - center.x, a[i].z - center.z) <= 64 * SilentGateSequence.SCALE);
        }
        for (int i = 0; i < 48; i++)
            for (int j = i + 1; j < 48; j++)
                assertTrue(a[i].distanceTo(a[j]) >= 6 * SilentGateSequence.SCALE, i + "," + j);
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
        assertEquals(1, SilentGateSequence.gateDescent(139));
        assertEquals(0, SilentGateSequence.gateDescent(200));
        boolean dipped = false;
        for (double t = 185.5; t < 200; t += .5) dipped |= SilentGateSequence.gateDescent(t) < 0;
        assertTrue(dipped);
        for (double t = 190; t <= 200; t += .5)
            assertTrue(Math.abs(SilentGateSequence.gateDescent(t)) <= 0.08);
    }

    @Test
    void gateOpensAfterSilence() {
        assertEquals(0, SilentGateSequence.gateOpen(205));
        assertEquals(1, SilentGateSequence.gateOpen(250));
        double prev = 0;
        for (double t = 200; t <= 260; t += .25) {
            double v = SilentGateSequence.gateOpen(t);
            assertTrue(v >= prev);
            prev = v;
        }
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
