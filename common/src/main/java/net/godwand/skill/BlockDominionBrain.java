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

    /**
     * Full sculpted 3D pose for a single wing block: chest-relative offset, non-uniform 3D scale (sx, sy, sz),
     * exact right-handed world-space orthonormal basis (axisX, axisY, axisZ) so feathers align with zero Euler
     * distortion at any yaw, and anatomical tier:
     * 0 = spine/scapula core, 1 = curved upper wing arch/bone spar, 2 = long splayed primary flight feathers,
     * 3 = overlapping secondary coverts & lower plumes.
     */
    public record WingPose(Vec3 offset, Vec3 scale, Vec3 axisX, Vec3 axisY, Vec3 axisZ, int tier) {}

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
     * Articulated multi-scale anatomical Block Wings behind the player's back.
     * Delegates to {@link #wingPose} so position, non-uniform feather scale, and 3D rotation stay in perfect sync.
     */
    public static Vec3 wingsOffset(int index, int count, double time, Vec3 lookDir, boolean boosted) {
        return wingPose(index, count, time, lookDir, boosted).offset();
    }

    /**
     * Computes full sculpted 3D wing anatomy for block {@code index} of {@code count}:
     * - Tier 0: Armored spine / scapula mount between shoulder blades
     * - Tier 1: Continuous curved upper wing arch / bone spar tapering smoothly to the wrist & tip
     * - Tier 2: Long slender splayed primary flight feathers (non-uniform stretched stone blades up to 1.65m!)
     * - Tier 3: Layered secondary coverts filling the inner wing membrane for a dense, majestic silhouette.
     */
    public static WingPose wingPose(int index, int count, double time, Vec3 lookDir, boolean boosted) {
        Vec3 forward = new Vec3(lookDir.x, 0, lookDir.z);
        if (forward.lengthSqr() < 1e-4) forward = new Vec3(0, 0, 1);
        else forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 up = new Vec3(0, 1, 0);

        int spineCount = count >= 12 ? 2 : 0;
        if (index < spineCount) {
            double yOff = index == 0 ? 0.24 : -0.22;
            double back = boosted ? -0.52 : -0.44;
            Vec3 pos = forward.scale(back).add(up.scale(yOff + Math.sin(time * 0.14 + index) * 0.025));
            Vec3 sc = index == 0 ? new Vec3(0.42, 0.64, 0.24) : new Vec3(0.32, 0.46, 0.20);
            Vec3 yA = up;
            Vec3 zA = forward;
            Vec3 xA = yA.cross(zA).normalize();
            return new WingPose(pos, sc, xA, yA, zA, 0);
        }

        int wingBlock = index - spineCount;
        int side = (wingBlock & 1) == 0 ? -1 : 1; // Left (-1) / Right (+1)
        int perWing = Math.max(1, (count - spineCount + (side < 0 ? 1 : 0)) / 2);
        int localIdx = wingBlock / 2;

        // Allocate each wing cleanly into 3 anatomical tiers: Arch Spar (~26%), Long Primaries (~46%), Coverts (~28%)
        int archCount = Math.max(2, (int) Math.round(perWing * 0.26));
        int primaryCount = Math.max(2, (int) Math.round(perWing * 0.46));
        if (archCount + primaryCount > perWing) {
            primaryCount = Math.max(1, perWing - archCount);
        }
        int covertCount = Math.max(0, perWing - archCount - primaryCount);

        if (localIdx < archCount) {
            // Tier 1: Continuous curved upper wing arch (long axisX runs right along the curve tangent!)
            double u = archCount <= 1 ? 0.4 : (localIdx / (double) (archCount - 1));
            Vec3 pos = wingArchPoint(side < 0, u, time, lookDir, boosted);
            Vec3 next = wingArchPoint(side < 0, Math.min(1.0, u + 0.08), time, lookDir, boosted);
            Vec3 prev = wingArchPoint(side < 0, Math.max(0.0, u - 0.08), time, lookDir, boosted);
            Vec3 xA = next.subtract(prev);
            xA = xA.lengthSqr() > 1e-6 ? xA.normalize() : right.scale(side);
            // Make sure yA points generally upward perpendicular to arch tangent
            Vec3 zA = xA.cross(up);
            if (zA.lengthSqr() < 1e-5) zA = forward;
            else zA = zA.normalize();
            if (zA.dot(forward) < 0) zA = zA.scale(-1.0);
            Vec3 yA = zA.cross(xA).normalize();
            if (yA.y < 0) {
                yA = yA.scale(-1.0);
                zA = xA.cross(yA).normalize();
            }

            double sx = Mth.lerp(u, 0.62, 0.34);
            double sy = Mth.lerp(u, 0.28, 0.16);
            double sz = Mth.lerp(u, 0.28, 0.18);
            return new WingPose(pos, new Vec3(sx, sy, sz), xA, yA, zA, 1);
        }

        if (localIdx < archCount + primaryCount) {
            // Tier 2: Long slender splayed primary flight feathers — axisY points EXACTLY from arch root -> feather tip!
            int fi = localIdx - archCount;
            double u = primaryCount <= 1 ? 0.6 : (fi / (double) (primaryCount - 1));
            double archU = 0.14 + u * 0.86;
            Vec3 root = wingArchPoint(side < 0, archU, time, lookDir, boosted);
            Vec3 tip = wingFeatherTip(side < 0, u, time, lookDir, boosted);
            Vec3 center = root.lerp(tip, 0.50);

            double featherLen = Math.max(0.75, root.distanceTo(tip));
            double sx = Mth.lerp(u, 0.26, 0.17);
            double sy = featherLen * (boosted ? 1.06 : 1.02);
            double sz = 0.11;

            Vec3 yA = tip.subtract(root).normalize();
            // Thin feather face aligns with wing plane (normal ~ forward, slightly angled for graceful overlapping depth)
            Vec3 planeRef = forward.add(right.scale(side * (boosted ? 0.28 : 0.14))).normalize();
            Vec3 xA = yA.cross(planeRef);
            if (xA.lengthSqr() < 1e-5) xA = right;
            else xA = xA.normalize();
            Vec3 zA = xA.cross(yA).normalize();
            return new WingPose(center, new Vec3(sx, sy, sz), xA, yA, zA, 2);
        }

        // Tier 3: Layered secondary coverts tucked along the inner wing hollow aligned cleanly with the feather fan
        int ci = localIdx - archCount - primaryCount;
        double u = covertCount <= 1 ? 0.5 : (ci / (double) (covertCount - 1));
        double archU = 0.08 + u * 0.72;
        Vec3 root = wingArchPoint(side < 0, archU, time, lookDir, boosted);
        Vec3 tip = wingFeatherTip(side < 0, u * 0.82, time, lookDir, boosted);
        Vec3 center = root.lerp(tip, 0.34).add(forward.scale(boosted ? -0.14 : -0.10));
        double sx = Mth.lerp(u, 0.30, 0.22);
        double sy = Mth.lerp(u, 0.54, 0.80);
        double sz = 0.13;
        Vec3 yA = tip.subtract(root).normalize();
        Vec3 xA = yA.cross(forward);
        if (xA.lengthSqr() < 1e-5) xA = right;
        else xA = xA.normalize();
        Vec3 zA = xA.cross(yA).normalize();
        return new WingPose(center, new Vec3(sx, sy, sz), xA, yA, zA, 3);
    }

    /** Point along the sculpted curved leading-edge wing arch ({@code u} = 0 at shoulder -> 1 at outer wingtip). */
    public static Vec3 wingArchPoint(boolean left, double u, double time, Vec3 lookDir, boolean boosted) {
        Vec3 forward = new Vec3(lookDir.x, 0, lookDir.z);
        if (forward.lengthSqr() < 1e-4) forward = new Vec3(0, 0, 1);
        else forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 up = new Vec3(0, 1, 0);
        int side = left ? -1 : 1;

        if (boosted) {
            // Swept-back supersonic chevron arch (kept clearly visible in 3rd-person back view!)
            double span = 0.42 + u * 2.55;
            double archY = 0.24 + Math.sin(u * Math.PI * 0.76) * 1.02 + u * 0.22;
            double sweepBack = -0.44 - u * 1.12;
            double vibe = Math.sin(time * 1.35 + u * 6.0) * 0.022 * u;
            return right.scale(side * span).add(forward.scale(sweepBack)).add(up.scale(archY + vibe));
        }

        // Majestic eagle/seraphic arch: rises sharply to high wrist at u~0.42, then sweeps gracefully outward
        double flapPhase = time * 0.22 - u * 1.15;
        double flapY = Math.sin(flapPhase) * (0.12 + u * 0.48);
        double flapBack = -Math.cos(flapPhase) * (0.08 + u * 0.18);
        double span = 0.44 + u * 2.78;
        double archY = 0.24 + Math.sin(Math.pow(u, 0.78) * Math.PI * 0.86) * 1.48 + u * 0.18;
        double sweepBack = -0.44 - u * 0.60 + flapBack;
        return right.scale(side * span).add(forward.scale(sweepBack)).add(up.scale(archY + flapY));
    }

    /** Trailing feather-tip position corresponding to primary feather {@code u} (0=inner -> 1=outermost pinion). */
    public static Vec3 wingFeatherTip(boolean left, double u, double time, Vec3 lookDir, boolean boosted) {
        Vec3 forward = new Vec3(lookDir.x, 0, lookDir.z);
        if (forward.lengthSqr() < 1e-4) forward = new Vec3(0, 0, 1);
        else forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 up = new Vec3(0, 1, 0);
        int side = left ? -1 : 1;

        double archU = 0.16 + u * 0.84;
        Vec3 root = wingArchPoint(left, archU, time, lookDir, boosted);
        double len = (boosted ? 0.92 : 0.96) + Math.sin(u * Math.PI * 0.88) * (boosted ? 0.56 : 0.72);
        // Splay angle sweeps smoothly from downward-outward (inner feathers) to wide horizontal-outward (wingtip pinions!)
        double splayRad = Mth.lerp(u, Math.toRadians(-72.0), Math.toRadians(boosted ? -8.0 : -14.0));
        double outX = Math.cos(splayRad) * len;
        double outY = Math.sin(splayRad) * len;
        double trailBack = boosted ? (-0.52 - u * 0.45) : (-0.24 - u * 0.28);
        double waveTip = boosted ? 0.0 : Math.sin(time * 0.22 - archU * 1.25 - 0.55) * (0.14 + u * 0.22);
        return root.add(right.scale(side * outX))
                .add(up.scale(outY + waveTip))
                .add(forward.scale(trailBack));
    }

    /** Returns the world-relative offset (from chest) of the outer left or right wingtip for ribbon/particle emission. */
    public static Vec3 wingTipOffset(boolean left, double time, Vec3 lookDir, boolean boosted) {
        return wingFeatherTip(left, 1.0, time, lookDir, boosted);
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
