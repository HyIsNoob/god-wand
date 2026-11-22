package net.godwand.skill;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BlockDominionBrainTest {

    @Test
    void detectsIncomingProjectilesAndIgnoresRecedingOrWideMisses() {
        Vec3 player = new Vec3(0, 1.15, 0);
        assertTrue(BlockDominionBrain.isIncomingProjectile(player, new Vec3(10, 1.5, 0), new Vec3(-1.5, 0, 0)));
        assertFalse(BlockDominionBrain.isIncomingProjectile(player, new Vec3(10, 1.5, 0), new Vec3(1.5, 0, 0)));
        assertFalse(BlockDominionBrain.isIncomingProjectile(player, new Vec3(10, 1.5, 6), new Vec3(-1.5, 0, 0)));
        assertFalse(BlockDominionBrain.isIncomingProjectile(player, new Vec3(22, 1.5, 0), new Vec3(-2.0, 0, 0)));
        assertFalse(BlockDominionBrain.isIncomingProjectile(player, new Vec3(5, 1.5, 0), Vec3.ZERO));
    }

    @Test
    void plansIdleStowedVolleySingleMultiAndDomeFormations() {
        // No threats -> idle or stowed
        assertEquals(BlockDominionBrain.FormationKind.IDLE,
                BlockDominionBrain.plan(List.of(), 12, false, false).kind());
        assertEquals(BlockDominionBrain.FormationKind.STOWED,
                BlockDominionBrain.plan(List.of(), 12, true, false).kind());

        // Volley overrides threats while actively charging
        var oneThreat = List.of(new BlockDominionBrain.ThreatSample(new Vec3(1, 0, 0), 10, false));
        assertEquals(BlockDominionBrain.FormationKind.VOLLEY_FAN,
                BlockDominionBrain.plan(oneThreat, 12, false, true).kind());

        // Single threat -> SHIELD_SINGLE (even when stowed, background defense raises shield!)
        var single = BlockDominionBrain.plan(oneThreat, 12, true, false);
        assertEquals(BlockDominionBrain.FormationKind.SHIELD_SINGLE, single.kind());
        assertTrue(single.primaryDir().x > 0.99);

        // Two close threats (< 55 deg) cluster into one single shield
        var clustered = List.of(
                new BlockDominionBrain.ThreatSample(new Vec3(1, 0, 0), 12, false),
                new BlockDominionBrain.ThreatSample(new Vec3(1, 0, 0.3), 10, false));
        assertEquals(BlockDominionBrain.FormationKind.SHIELD_SINGLE,
                BlockDominionBrain.plan(clustered, 12, false, false).kind());

        // Two separated threats -> SHIELD_MULTI
        var dual = List.of(
                new BlockDominionBrain.ThreatSample(new Vec3(1, 0, 0), 12, false),
                new BlockDominionBrain.ThreatSample(new Vec3(-1, 0, 0), 9, false));
        var multiPlan = BlockDominionBrain.plan(dual, 12, false, false);
        assertEquals(BlockDominionBrain.FormationKind.SHIELD_MULTI, multiPlan.kind());
        assertTrue(multiPlan.primaryDir().x > 0.99 && multiPlan.secondaryDir().x < -0.99);

        // Three separated threats or imminent explosion with >= 10 blocks -> SHIELD_DOME
        var triple = List.of(
                new BlockDominionBrain.ThreatSample(new Vec3(1, 0, 0), 12, false),
                new BlockDominionBrain.ThreatSample(new Vec3(-1, 0, 0), 10, false),
                new BlockDominionBrain.ThreatSample(new Vec3(0, 0, 1), 8, false));
        assertEquals(BlockDominionBrain.FormationKind.SHIELD_DOME,
                BlockDominionBrain.plan(triple, 12, false, false).kind());

        var explosive = List.of(new BlockDominionBrain.ThreatSample(new Vec3(0, 0, 1), 22, true));
        assertEquals(BlockDominionBrain.FormationKind.SHIELD_DOME,
                BlockDominionBrain.plan(explosive, 14, false, false).kind());
        // With few blocks (< 10), even an explosion concentrates into a directional shield
        assertEquals(BlockDominionBrain.FormationKind.SHIELD_SINGLE,
                BlockDominionBrain.plan(explosive, 6, false, false).kind());
    }

    @Test
    void shieldPlateFacesThreatAndDomeEnclosesAllSides() {
        Vec3 threat = new Vec3(0, 0, 1);
        for (int i = 0; i < 16; i++) {
            Vec3 off = BlockDominionBrain.shieldPlateOffset(i, 16, threat, 1.72, 10.0);
            assertTrue(off.z > 0.5, "Shield blocks must sit in front of the threat direction");
        }

        boolean posX = false, negX = false, posZ = false, negZ = false, overhead = false;
        for (int i = 0; i < 24; i++) {
            Vec3 off = BlockDominionBrain.shieldDomeOffset(i, 24, 5.0);
            if (off.x > 1.0) posX = true;
            if (off.x < -1.0) negX = true;
            if (off.z > 1.0) posZ = true;
            if (off.z < -1.0) negZ = true;
            if (off.y > 1.5) overhead = true;
        }
        assertTrue(posX && negX && posZ && negZ && overhead, "Dome must cover all horizontal quadrants and overhead");
    }

    @Test
    void nearestSlotAssignmentMatchesClosestBlocksWithoutDuplicates() {
        List<Vec3> current = List.of(new Vec3(10, 0, 0), new Vec3(0, 0, 0), new Vec3(-10, 0, 0));
        List<Vec3> target = List.of(new Vec3(-9, 0, 0), new Vec3(9, 0, 0), new Vec3(0.5, 0, 0));
        int[] assigned = BlockDominionBrain.assignNearestSlots(current, target);
        assertArrayEquals(new int[]{2, 0, 1}, assigned);

        Set<Integer> seen = new HashSet<>();
        for (int idx : assigned) assertTrue(seen.add(idx));
    }

    @Test
    void significantDirectionChangeThresholdFiltersJitter() {
        Vec3 forward = new Vec3(0, 0, 1);
        Vec3 tinyJitter = new Vec3(0.05, 0, 1).normalize();
        Vec3 bigTurn = new Vec3(0.4, 0, 1).normalize();
        assertFalse(BlockDominionBrain.directionChangedSignificantly(forward, tinyJitter));
        assertTrue(BlockDominionBrain.directionChangedSignificantly(forward, bigTurn));
    }
}
