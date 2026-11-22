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
        VOLLEY_FAN,
        AIR_STAIRS,
        WINGS_HOVER,
        WINGS_BOOST;

        public boolean isShield() {
            return this == SHIELD_SINGLE || this == SHIELD_MULTI || this == SHIELD_DOME;
        }

        public boolean isWings() {
            return this == WINGS_HOVER || this == WINGS_BOOST;
        }

        public boolean isMobility() {
            return isWings() || this == AIR_STAIRS;
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
        public static final BrainPlan AIR_STAIRS_PLAN = new BrainPlan(FormationKind.AIR_STAIRS, new Vec3(0, 0, 1), Vec3.ZERO);
        public static final BrainPlan WINGS_HOVER_PLAN = new BrainPlan(FormationKind.WINGS_HOVER, new Vec3(0, 0, 1), Vec3.ZERO);
        public static final BrainPlan WINGS_BOOST_PLAN = new BrainPlan(FormationKind.WINGS_BOOST, new Vec3(0, 0, 1), Vec3.ZERO);
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
        return plan(threats, blockCount, stowed, volleyCharging, false, false, false);
    }

    /**
     * Full formation planner including mobility modes (Block Wings free flight, Mach boost chevron,
     * and Air-Step floating block stairs). Immediate threats still raise defensive shields.
     */
    public static BrainPlan plan(List<ThreatSample> threats, int blockCount, boolean stowed, boolean volleyCharging,
                                 boolean flying, boolean boosting, boolean airStairs) {
        if (volleyCharging && !stowed) return BrainPlan.VOLLEY_PLAN;

        boolean hasThreats = blockCount > 0 && threats != null && !threats.isEmpty();
        if (hasThreats) {
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
            // During supersonic wing boost, keep wings locked unless an explosive or incoming projectile demands a shield
            if (!boosting || sorted.get(0).urgency() >= 14.0) {
                if (clusters.size() >= 2 && blockCount >= 4) {
                    return new BrainPlan(FormationKind.SHIELD_MULTI, clusters.get(0), clusters.get(1));
                }
                return new BrainPlan(FormationKind.SHIELD_SINGLE, clusters.get(0), Vec3.ZERO);
            }
        }

        if (flying && blockCount >= BlockDominionRules.MIN_FLIGHT_BLOCKS) {
            return boosting ? BrainPlan.WINGS_BOOST_PLAN : BrainPlan.WINGS_HOVER_PLAN;
        }
        if (airStairs && !stowed && blockCount >= BlockDominionRules.MIN_STAIR_BLOCKS) {
            return BrainPlan.AIR_STAIRS_PLAN;
        }
        return stowed ? BrainPlan.STOWED_PLAN : BrainPlan.IDLE_PLAN;
    }

    /** Computes target offset relative to player chest (y + 1.25) for any Block Dominion formation. */
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
            case AIR_STAIRS -> airStairsOffset(index, count, time, lookDir);
            case WINGS_HOVER -> wingsOffset(index, count, time, lookDir, false);
            case WINGS_BOOST -> wingsOffset(index, count, time, lookDir, true);
        };
    }

    /**
     * Air-Step Block Stairs ("bước xuống tạo thang"): up to 6 reserve blocks materialize directly beneath
     * and ahead of the player's feet as a glowing floating stone staircase; remaining blocks orbit behind.
     */
    public static Vec3 airStairsOffset(int index, int count, double time, Vec3 lookDir) {
        Vec3 forward = new Vec3(lookDir.x, 0, lookDir.z);
        if (forward.lengthSqr() < 1e-4) forward = new Vec3(0, 0, 1);
        else forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 up = new Vec3(0, 1, 0);

        int stairSlots = Math.min(6, count);
        if (index < stairSlots) {
            // Chest is at playerY + 1.25; -1.52 places the top face of a ~0.55-scale block right under the soles
            double pulse = Math.sin(time * 0.25 + index * 1.1) * 0.025;
            return switch (index) {
                case 0 -> forward.scale(0.18).add(up.scale(-1.52 + pulse));
                case 1 -> forward.scale(1.02).add(up.scale(-1.86 + pulse));
                case 2 -> forward.scale(1.88).add(up.scale(-2.20 + pulse));
                case 3 -> forward.scale(-0.78).add(up.scale(-1.18 + pulse));
                case 4 -> forward.scale(0.55).add(right.scale(-0.78)).add(up.scale(-1.62 + pulse));
                default -> forward.scale(0.55).add(right.scale(0.78)).add(up.scale(-1.62 + pulse));
            };
        }
        int remIdx = index - stairSlots;
        int remCount = Math.max(1, count - stairSlots);
        return BlockDominionRules.formationOffset(remIdx, remCount, time, lookDir, false, 1f, false, 0f);
    }

    /**
     * Articulated multi-layered Block Wings behind the player's back.
     * In {@code WINGS_HOVER} (!boosted): grand outstretched seraphic stone wings with a traveling organic
     * flap wave from shoulder to wingtip. In {@code WINGS_BOOST} (boosted): swept-back aerodynamic
     * chevron delta-wings built for high-speed Mach flight.
     */
    public static Vec3 wingsOffset(int index, int count, double time, Vec3 lookDir, boolean boosted) {
        Vec3 forward = new Vec3(lookDir.x, 0, lookDir.z);
        if (forward.lengthSqr() < 1e-4) forward = new Vec3(0, 0, 1);
        else forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 up = new Vec3(0, 1, 0);

        // Central spine/scapula anchor blocks right behind the upper back when reserve >= 10
        int spineCount = count >= 10 ? 2 : 0;
        if (index < spineCount) {
            double yOff = index == 0 ? 0.24 : -0.22;
            double back = boosted ? -0.82 : -0.68;
            return forward.scale(back).add(up.scale(yOff + Math.sin(time * 0.16 + index) * 0.04));
        }

        int wingBlock = index - spineCount;
        int side = (wingBlock & 1) == 0 ? -1 : 1; // alternate Left (-1) and Right (+1) for perfect symmetry
        int perWingTotal = Math.max(1, (count - spineCount + (side < 0 ? 1 : 0)) / 2);
        int localIdx = wingBlock / 2;

        // Split each wing into upper primary pinions (~60%) and lower secondary feathers (~40%)
        int primaryCount = Math.max(3, (int) Math.ceil(perWingTotal * 0.62));
        boolean primary = localIdx < primaryCount;
        int featherIdx = primary ? localIdx : (localIdx - primaryCount);
        int featherCount = primary ? primaryCount : Math.max(1, perWingTotal - primaryCount);
        double u = featherCount <= 1 ? 0.5 : (featherIdx / (double) (featherCount - 1)); // 0 (root) -> 1 (tip)

        if (boosted) {
            // Sleek swept-back supersonic delta chevron wings (kept well in front of 3rd-person camera!)
            double span = primary ? (0.62 + u * 2.55) : (0.52 + u * 1.72);
            double sweepBack = primary ? (-0.42 - u * 1.18) : (-0.64 - u * 0.92);
            double lift = primary
                    ? (0.24 + Math.sin(u * Math.PI * 0.72) * 1.05 + u * 0.35)
                    : (-0.28 + u * 0.36);
            double vibe = Math.sin(time * 1.45 + index * 1.7) * 0.028 * u;
            return right.scale(side * span)
                    .add(forward.scale(sweepBack))
                    .add(up.scale(lift + vibe));
        }

        // Regal seraphic outstretched wings with dense feathered arch and organic traveling flap wave
        double flapPhase = time * 0.22 - u * 1.15;
        double flapY = Math.sin(flapPhase) * (0.14 + u * 0.52);
        double flapBack = -Math.cos(flapPhase) * (0.10 + u * 0.20);

        double span = primary ? (0.58 + u * 2.68) : (0.52 + u * 1.82);
        double archY = primary
                ? (0.22 + Math.sin(u * Math.PI * 0.80) * 1.38 + u * 0.26)
                : (-0.26 + Math.sin(u * Math.PI * 0.74) * 0.68 - u * 0.18);
        double sweepBack = primary
                ? (-0.48 - u * 0.62 + flapBack)
                : (-0.70 - u * 0.52 + flapBack * 0.8);

        return right.scale(side * span)
                .add(forward.scale(sweepBack))
                .add(up.scale(archY + flapY));
    }

    /** Returns the world-relative offset (from chest) of the outer left or right wingtip for ribbon/particle emission. */
    public static Vec3 wingTipOffset(boolean left, double time, Vec3 lookDir, boolean boosted) {
        Vec3 forward = new Vec3(lookDir.x, 0, lookDir.z);
        if (forward.lengthSqr() < 1e-4) forward = new Vec3(0, 0, 1);
        else forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 up = new Vec3(0, 1, 0);
        int side = left ? -1 : 1;
        double u = 1.0;
        if (boosted) {
            return right.scale(side * (0.62 + 2.55))
                    .add(forward.scale(-0.42 - 1.18))
                    .add(up.scale(0.24 + Math.sin(Math.PI * 0.72) * 1.05 + 0.35));
        }
        double flapPhase = time * 0.22 - u * 1.15;
        double flapY = Math.sin(flapPhase) * (0.14 + u * 0.52);
        double flapBack = -Math.cos(flapPhase) * (0.10 + u * 0.20);
        return right.scale(side * (0.58 + 2.68))
                .add(forward.scale(-0.48 - 0.62 + flapBack))
                .add(up.scale(0.22 + Math.sin(Math.PI * 0.80) * 1.38 + 0.26 + flapY));
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
