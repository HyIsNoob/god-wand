package net.godwand.skill;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BlockDominionRulesTest {

    @Test
    void modeCyclesOffManualAutoOff() {
        assertEquals(BlockDominionRules.Mode.MANUAL, BlockDominionRules.Mode.OFF.next());
        assertEquals(BlockDominionRules.Mode.AUTO, BlockDominionRules.Mode.MANUAL.next());
        assertEquals(BlockDominionRules.Mode.OFF, BlockDominionRules.Mode.AUTO.next());
        assertEquals(BlockDominionRules.Mode.OFF, BlockDominionRules.Mode.byOrdinal(-1));
        assertEquals(BlockDominionRules.Mode.OFF, BlockDominionRules.Mode.byOrdinal(99));
    }

    @Test
    void capacityClampsBetweenFourAndNinetySix() {
        assertEquals(4, BlockDominionRules.clampCapacity(0));
        assertEquals(4, BlockDominionRules.clampCapacity(-10));
        assertEquals(48, BlockDominionRules.clampCapacity(48));
        assertEquals(96, BlockDominionRules.clampCapacity(128));
    }

    @Test
    void blockFilterRejectsFluidsBlockEntitiesUnbreakableSpatialAndProtected() {
        assertTrue(BlockDominionRules.canPickUp(false, false, false, 1.5f, false, false));
        assertTrue(BlockDominionRules.canPickUp(false, false, false, 0.0f, false, false));
        assertFalse(BlockDominionRules.canPickUp(true, false, false, 1.0f, false, false));
        assertFalse(BlockDominionRules.canPickUp(false, true, false, 0.0f, false, false));
        assertFalse(BlockDominionRules.canPickUp(false, false, true, 2.5f, false, false));
        assertFalse(BlockDominionRules.canPickUp(false, false, false, -1.0f, false, false));
        assertFalse(BlockDominionRules.canPickUp(false, false, false, 1.5f, true, false));
        assertFalse(BlockDominionRules.canPickUp(false, false, false, 1.5f, false, true));
    }

    @Test
    void rapidFireAcceleratesDownToMinimumCadence() {
        assertEquals(6, BlockDominionRules.rapidFireInterval(0));
        assertEquals(6, BlockDominionRules.rapidFireInterval(1));
        assertTrue(BlockDominionRules.rapidFireInterval(4) < BlockDominionRules.rapidFireInterval(1));
        assertEquals(2, BlockDominionRules.rapidFireInterval(12));
        assertEquals(2, BlockDominionRules.rapidFireInterval(100));
    }

    @Test
    void volleyReleaseOrderStartsAtCenterAndCoversEveryBlockOnce() {
        assertArrayEquals(new int[0], BlockDominionRules.volleyReleaseOrder(0));
        assertArrayEquals(new int[]{0}, BlockDominionRules.volleyReleaseOrder(1));
        int[] order = BlockDominionRules.volleyReleaseOrder(32);
        assertEquals(32, order.length);
        assertEquals(16, order[0]);
        Set<Integer> seen = new HashSet<>();
        for (int idx : order) {
            assertTrue(idx >= 0 && idx < 32);
            assertTrue(seen.add(idx));
        }
    }

    @Test
    void spiralDropColumnsStayAtLeastTwoBlocksApartWithNoDuplicates() {
        int[][] offsets = BlockDominionRules.spiralDropOffsets(32);
        assertEquals(32, offsets.length);
        for (int i = 0; i < offsets.length; i++) {
            assertFalse(offsets[i][0] == 0 && offsets[i][1] == 0, "Never drops directly on player column");
            for (int j = i + 1; j < offsets.length; j++) {
                int dx = offsets[i][0] - offsets[j][0];
                int dz = offsets[i][1] - offsets[j][1];
                assertTrue(dx * dx + dz * dz >= 4, "Minimum 2-block spacing violated");
            }
        }
    }

    @Test
    void damageAbsorptionPriorityAndRules() {
        // Void / /kill always passes through
        assertEquals(BlockDominionRules.AbsorbResult.NONE,
                BlockDominionRules.decideAbsorb(BlockDominionRules.Mode.MANUAL, 10, true, true, false, false, false));
        // Cutscene / creative already invulnerable does not spend a block
        assertEquals(BlockDominionRules.AbsorbResult.NONE,
                BlockDominionRules.decideAbsorb(BlockDominionRules.Mode.MANUAL, 10, true, false, true, false, false));
        // Own Block Dominion projectile damage is negated for free
        assertEquals(BlockDominionRules.AbsorbResult.FREE_INVULN,
                BlockDominionRules.decideAbsorb(BlockDominionRules.Mode.MANUAL, 10, false, false, false, true, false));
        // Post-parry 0.5s i-frame window absorbs for free
        assertEquals(BlockDominionRules.AbsorbResult.FREE_INVULN,
                BlockDominionRules.decideAbsorb(BlockDominionRules.Mode.MANUAL, 10, false, false, false, false, true));
        // Manual mode consumes 1 reserve block if available
        assertEquals(BlockDominionRules.AbsorbResult.CONSUME_RESERVE,
                BlockDominionRules.decideAbsorb(BlockDominionRules.Mode.MANUAL, 1, false, false, false, false, false));
        assertEquals(BlockDominionRules.AbsorbResult.NONE,
                BlockDominionRules.decideAbsorb(BlockDominionRules.Mode.MANUAL, 0, false, false, false, false, false));
        // Auto mode pulls a nearby block if one exists
        assertEquals(BlockDominionRules.AbsorbResult.CONSUME_AUTO_BLOCK,
                BlockDominionRules.decideAbsorb(BlockDominionRules.Mode.AUTO, 0, true, false, false, false, false));
        assertEquals(BlockDominionRules.AbsorbResult.NONE,
                BlockDominionRules.decideAbsorb(BlockDominionRules.Mode.AUTO, 0, false, false, false, false, false));
        // Off mode never absorbs
        assertEquals(BlockDominionRules.AbsorbResult.NONE,
                BlockDominionRules.decideAbsorb(BlockDominionRules.Mode.OFF, 10, true, false, false, false, false));
    }

    @Test
    void recallWindowHonorsThirtySecondLimitAndExcludesDeath() {
        assertTrue(BlockDominionRules.canRecall(1000, 1000, false));
        assertTrue(BlockDominionRules.canRecall(1600, 1000, false));
        assertFalse(BlockDominionRules.canRecall(1601, 1000, false));
        assertFalse(BlockDominionRules.canRecall(1010, 1000, true));
        assertFalse(BlockDominionRules.canRecall(1000, -1, false));
    }

    @Test
    void formationsProduceFiniteOffsetsAndCompactStowScale() {
        Vec3 look = new Vec3(0, 0, 1);
        for (int count : new int[]{4, 14, 32}) {
            for (int i = 0; i < count; i++) {
                Vec3 orbit = BlockDominionRules.formationOffset(i, count, 12.5, look, false, 1f, false, 0f);
                Vec3 stow = BlockDominionRules.formationOffset(i, count, 12.5, look, true, 0f, false, 0f);
                Vec3 fan = BlockDominionRules.formationOffset(i, count, 12.5, look, false, 1f, true, 1f);
                assertTrue(Double.isFinite(orbit.x) && Double.isFinite(orbit.y) && Double.isFinite(orbit.z));
                assertTrue(Double.isFinite(stow.x) && Double.isFinite(stow.y) && Double.isFinite(stow.z));
                assertTrue(Double.isFinite(fan.x) && Double.isFinite(fan.y) && Double.isFinite(fan.z));
                assertTrue(stow.z < 0, "Stowed cluster stays behind shoulder");
            }
        }
        assertTrue(BlockDominionRules.blockScale(true, 0f, false, 0f) < BlockDominionRules.blockScale(false, 1f, false, 0f));
    }

    @Test
    void freeFlightAcceleratesBoostsAndSmoothlyDeceleratesToHover() {
        Vec3 look = new Vec3(0, 0.2, 1).normalize();
        Vec3 vel = Vec3.ZERO;
        // Cruise acceleration
        for (int t = 0; t < 20; t++) {
            vel = BlockDominionRules.stepFlightVelocity(vel, look, 1, 0, 0, false);
        }
        assertEquals(BlockDominionRules.FLIGHT_CRUISE_SPEED, vel.length(), 0.02);
        assertTrue(vel.y > 0.05 && vel.z > 0.5, "Follows 3D look vector");

        // Sprint Mach boost accelerates past cruise speed
        Vec3 boosted = vel;
        for (int t = 0; t < 20; t++) {
            boosted = BlockDominionRules.stepFlightVelocity(boosted, look, 1, 0, 0, true);
        }
        assertEquals(BlockDominionRules.FLIGHT_BOOST_SPEED, boosted.length(), 0.02);
        assertTrue(boosted.length() > vel.length() * 1.8);

        // Releasing keys smoothly decelerates to hover
        Vec3 braking = boosted;
        for (int t = 0; t < 40; t++) {
            braking = BlockDominionRules.stepFlightVelocity(braking, look, 0, 0, 0, false);
        }
        assertEquals(0.0, braking.length(), 1e-6);
    }

    @Test
    void airStepStairsCatchesFallHoldsStillAndHopsOnJump() {
        Vec3 falling = new Vec3(0.2, -0.8, 0.0);
        Vec3 stepped = BlockDominionRules.stepAirStairsVelocity(falling, 0.2, false);
        assertTrue(stepped.y > falling.y && stepped.y >= BlockDominionRules.STAIR_DESCEND_VY - 0.3);

        Vec3 standing = BlockDominionRules.stepAirStairsVelocity(new Vec3(0, -0.5, 0), 0.0, false);
        assertTrue(standing.y > -0.25, "Catches fall when hovering on step");

        Vec3 hopped = BlockDominionRules.stepAirStairsVelocity(new Vec3(0.1, -0.1, 0.1), 0.14, true);
        assertEquals(BlockDominionRules.STAIR_HOP_VY, hopped.y, 1e-6);
    }
}
