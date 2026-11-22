package net.godwand.skill;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure Milestone 2 "block brain": threat perception math, formation selection (idle, stowed,
 * single-direction shield, dual-direction shield, 360 dome, volley fan), slot layout and
 * nearest-slot matching so blocks glide into place without erratic crossing.
 */
public final class BlockDominionBrain {
    public static final double THREAT_SCAN_RADIUS = 16.0;
    public static final double PROJECTILE_HIT_CORRIDOR = 2.6;
    public static final double DIRECTION_CLUSTER_COS = Math.cos(Math.toRadians(55.0));

    public enum FormationKind {
        IDLE,
        STOWED,
        SHIELD_SINGLE,
        SHIELD_MULTI,
        SHIELD_DOME,
        VOLLEY_FAN;

        public boolean isShield() {
            return this == SHIELD_SINGLE || this == SHIELD_MULTI || this == SHIELD_DOME;
        }

        public static FormationKind byOrdinal(int ordinal) {
            FormationKind[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IDLE;
        }
    }

    public record ThreatSample(Vec3 direction, double urgency, boolean explosive) {
        public ThreatSample {
            direction = direction == null || direction.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : direction.normalize();
        }
    }

    public record BrainPlan(FormationKind kind, Vec3 primaryDir, Vec3 secondaryDir) {
        public static final BrainPlan IDLE_PLAN = new BrainPlan(FormationKind.IDLE, new Vec3(0, 0, 1), Vec3.ZERO);
        public static final BrainPlan STOWED_PLAN = new BrainPlan(FormationKind.STOWED, new Vec3(0, 0, 1), Vec3.ZERO);
        public static final BrainPlan VOLLEY_PLAN = new BrainPlan(FormationKind.VOLLEY_FAN, new Vec3(0, 0, 1), Vec3.ZERO);
    }

    private BlockDominionBrain() {}

    /** True if a moving projectile is heading toward {@code playerCenter} within scan radius and hit corridor. */
    public static boolean isIncomingProjectile(Vec3 playerCenter, Vec3 projPos, Vec3 projVel) {
        if (projVel == null || projVel.lengthSqr() < 1e-4) return false;
        Vec3 toPlayer = playerCenter.subtract(projPos);
        double distSq = toPlayer.lengthSqr();
        if (distSq > THREAT_SCAN_RADIUS * THREAT_SCAN_RADIUS) return false;
        if (distSq < 2.25) return true;
        Vec3 velDir = projVel.normalize();
        double forward = toPlayer.dot(velDir);
        if (forward <= 0) return false;
        Vec3 closest = projPos.add(velDir.scale(forward));
        return closest.distanceToSqr(playerCenter) <= PROJECTILE_HIT_CORRIDOR * PROJECTILE_HIT_CORRIDOR;
    }

    /** Selects the best formation from active threats, reserve size, stowed state and volley charge. */
    public static BrainPlan plan(List<ThreatSample> threats, int blockCount, boolean stowed, boolean volleyCharging) {
        if (volleyCharging && !stowed) return BrainPlan.VOLLEY_PLAN;
        if (blockCount <= 0 || threats == null || threats.isEmpty()) {
            return stowed ? BrainPlan.STOWED_PLAN : BrainPlan.IDLE_PLAN;
        }

        List<ThreatSample> sorted = new ArrayList<>(threats);
        sorted.sort(Comparator.comparingDouble(ThreatSample::urgency).reversed());

        boolean anyExplosive = false;
        List<Vec3> clusters = new ArrayList<>();
        for (ThreatSample sample : sorted) {
            if (sample.explosive()) anyExplosive = true;
            boolean merged = false;
            for (Vec3 existing : clusters) {
                if (existing.dot(sample.direction()) >= DIRECTION_CLUSTER_COS) {
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                clusters.add(sample.direction());
                if (clusters.size() >= 3) break;
            }
        }

        if ((anyExplosive && blockCount >= 10) || (clusters.size() >= 3 && blockCount >= 8)) {
            return new BrainPlan(FormationKind.SHIELD_DOME, clusters.get(0),
                    clusters.size() > 1 ? clusters.get(1) : Vec3.ZERO);
        }
        if (clusters.size() >= 2 && blockCount >= 4) {
            return new BrainPlan(FormationKind.SHIELD_MULTI, clusters.get(0), clusters.get(1));
        }
        return new BrainPlan(FormationKind.SHIELD_SINGLE, clusters.get(0), Vec3.ZERO);
    }

    /** Computes target offset relative to player chest (y + 1.25) for any Milestone 1 or 2 formation. */
    public static Vec3 slotOffset(FormationKind kind, int index, int count, double time, Vec3 lookDir,
                                  Vec3 primaryDir, Vec3 secondaryDir, float unfoldProgress, float volleyProgress) {
        if (count <= 0) return Vec3.ZERO;
        return switch (kind) {
            case IDLE -> BlockDominionRules.formationOffset(index, count, time, lookDir, false, unfoldProgress, false, 0f);
            case STOWED -> BlockDominionRules.formationOffset(index, count, time, lookDir, true, 0f, false, 0f);
            case VOLLEY_FAN -> BlockDominionRules.formationOffset(index, count, time, lookDir, false, 1f, true, volleyProgress);
            case SHIELD_SINGLE -> shieldPlateOffset(index, count, primaryDir, 1.72, time);
            case SHIELD_MULTI -> {
                int half = (count + 1) / 2;
                if (index < half) {
                    yield shieldPlateOffset(index, half, primaryDir, 1.72, time);
                } else {
                    yield shieldPlateOffset(index - half, count - half, secondaryDir, 1.72, time);
                }
            }
            case SHIELD_DOME -> shieldDomeOffset(index, count, time);
        };
    }

    /**
     * Hex-packed curved shield plate perpendicular to {@code threatDir}.
     * Center block guards dead-center, subsequent rings pack around it in a tight concave buckler/wall.
     */
    public static Vec3 shieldPlateOffset(int index, int count, Vec3 threatDir, double distance, double time) {
        Vec3 normal = threatDir == null || threatDir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : threatDir.normalize();
        Vec3 side = normal.cross(Math.abs(normal.y) < 0.92 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 up = side.cross(normal).normalize();

        double lx, ly, curveBack;
        if (index == 0) {
            lx = 0.0;
            ly = 0.0;
            curveBack = 0.0;
        } else if (index <= 6) {
            // Inner hex ring (6 blocks)
            double a = (index - 1) * (Math.PI / 3.0) + Math.sin(time * 0.04) * 0.04;
            double r = 0.72;
            lx = Math.cos(a) * r;
            ly = Math.sin(a) * r;
            curveBack = -0.14;
        } else if (index <= 18) {
            // Outer hex ring (12 blocks)
            double a = (index - 7) * (Math.PI / 6.0) - Math.sin(time * 0.04) * 0.03;
            double r = 1.40;
            lx = Math.cos(a) * r;
            ly = Math.sin(a) * r;
            curveBack = -0.42;
        } else {
            // Heavy reinforced perimeter ring / flank guard (19..64)
            int rem = Math.max(1, count - 19);
            double a = (index - 19) * (Math.PI * 2.0 / rem) + time * 0.025;
            double r = 1.98;
            lx = Math.cos(a) * r;
            ly = Math.sin(a) * r * 0.92;
            curveBack = -0.82;
        }

        double breathe = Math.sin(time * 0.14 + index * 0.7) * 0.025;
        return normal.scale(distance + curveBack + breathe)
                .add(side.scale(lx))
                .add(up.scale(ly));
    }

    /** 360-degree Fibonacci sphere dome surrounding the player on all sides and overhead. */
    public static Vec3 shieldDomeOffset(int index, int count, double time) {
        double radius = 2.10;
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        // Span y from -0.45 (waist/legs) to +0.96 (overhead canopy) so feet stay clear while body & top are sealed
        double frac = count <= 1 ? 0.5 : (index / (double) (count - 1));
        double y = Mth.lerp(frac, 0.96, -0.45);
        double ringR = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        double theta = golden * index + time * 0.022;
        return new Vec3(Math.cos(theta) * ringR * radius, y * radius + 0.15, Math.sin(theta) * ringR * radius);
    }

    /**
     * Greedy nearest-slot matching: returns {@code perm[slotIndex] = blockIndex} so each block glides
     * to the nearest target slot when formations transition.
     */
    public static int[] assignNearestSlots(List<Vec3> currentPositions, List<Vec3> targetPositions) {
        int n = Math.min(currentPositions.size(), targetPositions.size());
        int[] assigned = new int[n];
        boolean[] usedBlock = new boolean[n];
        for (int slot = 0; slot < n; slot++) {
            Vec3 target = targetPositions.get(slot);
            int bestBlock = -1;
            double bestDistSq = Double.MAX_VALUE;
            for (int b = 0; b < n; b++) {
                if (usedBlock[b]) continue;
                double d2 = currentPositions.get(b).distanceToSqr(target);
                if (d2 < bestDistSq) {
                    bestDistSq = d2;
                    bestBlock = b;
                }
            }
            assigned[slot] = bestBlock;
            if (bestBlock >= 0) usedBlock[bestBlock] = true;
        }
        return assigned;
    }

    /** True if two unit direction vectors diverged by more than ~12 degrees (triggers network resync). */
    public static boolean directionChangedSignificantly(Vec3 a, Vec3 b) {
        if (a == null || b == null) return a != b;
        boolean aZero = a.lengthSqr() < 1e-4;
        boolean bZero = b.lengthSqr() < 1e-4;
        if (aZero != bZero) return true;
        if (aZero) return false;
        return a.normalize().dot(b.normalize()) < 0.978; // cos(12 deg)
    }
}
