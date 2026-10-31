package net.godwand.skill;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DrillSweepTest {
    @Test
    void largerDrillsFormHigherAndWindRevealsGradually() {
        double previous = 0;
        for (double radius : new double[]{2.5, 3.6, 5.0, 6.5}) {
            double height = RatioCollapseManager.drillHoverHeight(radius);
            assertTrue(height > previous);
            assertTrue(height > 17 + radius * 1.4);
            previous = height;
        }
        assertEquals(0, RatioCollapseManager.drillWindVisibility(.50f));
        assertEquals(.5f, RatioCollapseManager.drillWindVisibility(.55f), .0001f);
        assertEquals(1, RatioCollapseManager.drillWindVisibility(.60f), .0001f);
    }

    @Test
    void craterVisitsEveryColumnOnceWithinTheVisibleShockwave() {
        for (int radius : new int[]{8, 13, 18, 23}) {
            var cursor = new SupernovaCraterCursor(radius);
            var columns = new java.util.HashSet<String>();
            int ticks = 0;
            while (!cursor.finished()) {
                for (int i = 0; i < 96 && !cursor.finished(); i++) {
                    assertTrue(columns.add(cursor.x() + ":" + cursor.z()));
                    assertTrue(Math.abs(cursor.x()) <= radius && Math.abs(cursor.z()) <= radius);
                    cursor.advance();
                }
                ticks++;
            }
            assertEquals((2 * radius + 1) * (2 * radius + 1), columns.size());
            assertTrue(ticks < 34);
            assertEquals(54, RatioCollapseManager.drillCraterDepth(0, 0, radius, 54));
            assertEquals(0, RatioCollapseManager.drillCraterDepth(radius, 0, radius, 54));
        }
        assertEquals(29, RatioCollapseManager.drillHoverHeight(5), .001);
        assertEquals(35.3, RatioCollapseManager.drillHoverHeight(6.5), .001);
    }

    @Test
    void drillHeatOnlyBuildsAtHighSpinSpeed() {
        assertEquals(0, RatioCollapseManager.drillHeat(0.4f));
        assertEquals(0, RatioCollapseManager.drillHeat(0.50f));
        assertTrue(RatioCollapseManager.drillHeat(0.55f) > 0);
        assertTrue(RatioCollapseManager.drillHeat(0.72f) > RatioCollapseManager.drillHeat(0.55f));
        assertEquals(1, RatioCollapseManager.drillHeat(0.95f));
    }

    @Test
    void fastDrillSweepsWholeSegmentAndLeavesTaperedTip() {
        Vec3 start = Vec3.ZERO;
        Vec3 end = new Vec3(0, 0, 5.4);
        assertTrue(StoneCannonManager.insideDrillSweep(new Vec3(0, 0, 2.7), start, end, 1, 3));
        assertTrue(StoneCannonManager.insideDrillSweep(new Vec3(0.9, 0, 1), start, end, 1, 3));
        assertFalse(StoneCannonManager.insideDrillSweep(new Vec3(0.9, 0, 5), start, end, 1, 3));
        assertFalse(StoneCannonManager.insideDrillSweep(new Vec3(0, 0, 6), start, end, 1, 3));
        assertFalse(StoneCannonManager.insideDrillSweep(new Vec3(0, 0, -4), start, end, 1, 3));
    }

    @Test
    void impactExcavationCannotExtendBeyondItsLocalEndpoint() {
        Vec3 hit = new Vec3(20, 65, 30);
        Vec3 tip = hit.add(0, 0, 9);
        assertTrue(StoneCannonManager.insideDrillSweep(hit.add(0, 0, 4), hit, tip, 2, 6));
        assertFalse(StoneCannonManager.insideDrillSweep(hit.add(0, 0, 20), hit, tip, 2, 6));
    }

    @Test
    void verticalAndDiagonalShotsUseTheirOwnAxis() {
        for (Vec3 direction : new Vec3[]{new Vec3(0, 1, 0), new Vec3(1, 1, -1).normalize()}) {
            Vec3 start = new Vec3(-20, 80, 3);
            Vec3 end = start.add(direction.scale(5));
            assertTrue(StoneCannonManager.insideDrillSweep(start.add(direction.scale(2)), start, end, 1, 3));
            assertFalse(StoneCannonManager.insideDrillSweep(end.add(direction), start, end, 1, 3));
        }
        assertFalse(StoneCannonManager.insideDrillSweep(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 1, 3));
    }
}
