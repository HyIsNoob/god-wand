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

    @Test
    void plansWingsBoostAndAirStairsFormationsWithValidGeometry() {
        assertEquals(BlockDominionBrain.FormationKind.WINGS_HOVER,
                BlockDominionBrain.plan(List.of(), 16, false, false, true, false, false).kind());
        assertEquals(BlockDominionBrain.FormationKind.WINGS_BOOST,
                BlockDominionBrain.plan(List.of(), 16, false, false, true, true, false).kind());
        assertEquals(BlockDominionBrain.FormationKind.AIR_STAIRS,
                BlockDominionBrain.plan(List.of(), 12, false, false, false, false, true).kind());
        // Not enough blocks -> falls back to IDLE
        assertEquals(BlockDominionBrain.FormationKind.IDLE,
                BlockDominionBrain.plan(List.of(), 4, false, false, true, false, false).kind());

        Vec3 look = new Vec3(0, 0, 1);
        // Sculpted multi-scale wings span both left (-X) and right (+X) behind the back (-Z),
        // with elongated non-uniform primary feathers and deeper sweep-back on Mach boost
        boolean hasLeft = false, hasRight = false, hasElongatedFeather = false;
        double hoverMinZ = 0, boostMinZ = 0;
        for (int i = 0; i < 40; i++) {
            var pose = BlockDominionBrain.wingPose(i, 40, 10.0, look, false);
            var boostPose = BlockDominionBrain.wingPose(i, 40, 10.0, look, true);
            Vec3 w = pose.offset();
            Vec3 wb = boostPose.offset();
            assertTrue(w.z < -0.15, "Wings stay behind back");
            if (w.x < -1.8) hasLeft = true;
            if (w.x > 1.8) hasRight = true;
            if (pose.tier() == 2 && pose.scale().y > 1.0 && pose.scale().y > pose.scale().x * 1.8 && pose.scale().z < 0.20) {
                hasElongatedFeather = true;
            }
            hoverMinZ = Math.min(hoverMinZ, w.z);
            boostMinZ = Math.min(boostMinZ, wb.z);
        }
        assertTrue(hasLeft && hasRight, "Wings must form symmetrical left and right pinions");
        assertTrue(hasElongatedFeather, "Primary flight feathers must use elongated non-uniform sculpted scales");
        assertTrue(boostMinZ < hoverMinZ - 0.15, "Boost chevron sweeps further back than hover wings");

        // Air stairs place first steps directly beneath feet (y < -1.1 relative to chest)
        Vec3 step0 = BlockDominionBrain.airStairsOffset(0, 16, 5.0, look);
        Vec3 step1 = BlockDominionBrain.airStairsOffset(1, 16, 5.0, look);
        assertTrue(step0.y < -1.2 && step1.y < step0.y, "Stairs step downward in front of feet");
        assertTrue(step1.z > step0.z, "Next stair step lies ahead of player");

        // Railgun ring during rapid fire places 5 accelerator blocks ahead along aim vector
        assertEquals(BlockDominionBrain.FormationKind.RAILGUN_RING,
                BlockDominionBrain.plan(List.of(), 12, false, false, false, false, false, true).kind());
        for (int i = 0; i < 5; i++) {
            Vec3 r = BlockDominionBrain.railgunRingOffset(i, 12, 4.0, look);
            assertTrue(r.z > 1.5, "Railgun ring blocks sit ahead of player along aim line");
        }

        // Aerial orbital meteor crown when charging volley while in wing flight
        assertEquals(BlockDominionBrain.FormationKind.ORBITAL_METEOR,
                BlockDominionBrain.plan(List.of(), 24, false, true, true, false, false, false).kind());
        for (int i = 0; i < 24; i++) {
            Vec3 m = BlockDominionBrain.orbitalMeteorOffset(i, 24, 8.0, look, 1.0f);
            assertTrue(m.y > 2.0, "Orbital meteor crown hovers high overhead");
        }

        // QoL Rain Umbrella Canopy activates when raining overhead with >= 5 blocks, yields to wings/stairs
        assertEquals(BlockDominionBrain.FormationKind.RAIN_UMBRELLA,
                BlockDominionBrain.plan(List.of(), 12, false, false, false, false, false, false, true).kind());
        assertEquals(BlockDominionBrain.FormationKind.IDLE,
                BlockDominionBrain.plan(List.of(), 4, false, false, false, false, false, false, true).kind());
        assertEquals(BlockDominionBrain.FormationKind.WINGS_HOVER,
                BlockDominionBrain.plan(List.of(), 16, false, false, true, false, false, false, true).kind());
        for (int i = 0; i < 7; i++) {
            Vec3 u = BlockDominionBrain.rainUmbrellaOffset(i, 12, 6.0, look);
            assertTrue(u.y > 1.35, "Rain umbrella canopy blocks hover cleanly above player head");
        }

        // Liquid Stride & Sovereign Perch formations
        assertEquals(BlockDominionBrain.FormationKind.LIQUID_STRIDE,
                BlockDominionBrain.plan(List.of(), 12, false, false, false, false, false, false, false, true, false).kind());
        assertEquals(BlockDominionBrain.FormationKind.SOVEREIGN_PERCH,
                BlockDominionBrain.plan(List.of(), 16, false, false, true, false, false, false, false, false, true).kind());
        for (int i = 0; i < 7; i++) {
            var dais = BlockDominionBrain.sovereignPerchPose(i, 24, 10.0, look);
            assertTrue(dais.offset().y < -1.2, "Sovereign Perch dais slabs lock directly beneath boots");
        }
        Vec3 stride0 = BlockDominionBrain.liquidStrideOffset(0, 12, 5.0, look);
        assertTrue(stride0.y < -1.2, "Liquid Stride slabs skim level beneath boots");

        // Horizontal 3D Glide wings pitch flat across the horizontal back (wide X span, compact vertical Y thickness!)
        double uprightMinY = Double.MAX_VALUE, uprightMaxY = -Double.MAX_VALUE;
        double glideMinY = Double.MAX_VALUE, glideMaxY = -Double.MAX_VALUE;
        for (int i = 2; i < 40; i++) {
            Vec3 upW = BlockDominionBrain.wingPose(i, 40, 10.0, look, true, 0f, 0f).offset();
            Vec3 glW = BlockDominionBrain.wingPose(i, 40, 10.0, look, true, 80f, 0f).offset();
            uprightMinY = Math.min(uprightMinY, upW.y);
            uprightMaxY = Math.max(uprightMaxY, upW.y);
            glideMinY = Math.min(glideMinY, glW.y);
            glideMaxY = Math.max(glideMaxY, glW.y);
        }
        assertTrue((glideMaxY - glideMinY) < (uprightMaxY - uprightMinY) * 0.72,
                "Horizontal Glide wings flatten sleekly along the horizontal back");

        // Steep vertical climb (bodyPitchDeg=12, aeroFrac=1.0) stays cleanly behind back without flipping or clipping ahead
        for (int i = 2; i < 40; i++) {
            Vec3 climbW = BlockDominionBrain.wingPose(i, 40, 10.0, look, true, 12f, 0f, 1.0f).offset();
            assertTrue(climbW.z < 0.35, "Steep vertical climb wings stay cleanly behind torso (z=" + climbW.z + ")");
        }

        // Meteor Strike Dive rules, planner selection, and dive arrowhead geometry
        assertTrue(BlockDominionRules.isSteepMeteorDive(new Vec3(0, -0.85, 0.52).normalize(), new Vec3(0, -1.2, 0.4), true));
        assertFalse(BlockDominionRules.isSteepMeteorDive(new Vec3(0, 0.85, 0.52).normalize(), new Vec3(0, 1.1, 0.4), true));
        assertEquals(BlockDominionBrain.FormationKind.METEOR_DIVE,
                BlockDominionBrain.plan(List.of(), 24, false, false, true, true, false, false, false, false, false, true).kind());
        for (int i = 0; i < 24; i++) {
            var mp = BlockDominionBrain.meteorDivePose(i, 24, 15.0, look, 158f, 0f);
            assertTrue(Double.isFinite(mp.offset().x) && Double.isFinite(mp.offset().y) && Double.isFinite(mp.offset().z));
            assertEquals(1.0, mp.axisX().length(), 0.01);
            assertEquals(1.0, mp.axisY().length(), 0.01);
            assertEquals(1.0, mp.axisZ().length(), 0.01);
        }

        // Monolith Cage Impaling Stone Lances: 4 normalized downward-converging vectors to skewer & pin into ground
        Vec3[] spears = BlockDominionBrain.cageSpearDirections(4, 0.4);
        assertEquals(4, spears.length);
        for (Vec3 sp : spears) {
            assertEquals(1.0, sp.length(), 1e-4);
            assertTrue(sp.y < -0.45, "Spears angle cleanly downward into the earth (y=" + sp.y + ")");
        }
    }
}
