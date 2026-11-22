package net.godwand.skill;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Pure rules, timers and Milestone 1 formation math for Block Dominion (mode 9). */
public final class BlockDominionRules {
    public enum Mode {
        OFF, MANUAL, AUTO;

        public Mode next() {
            return switch (this) {
                case OFF -> MANUAL;
                case MANUAL -> AUTO;
                case AUTO -> OFF;
            };
        }

        public static Mode byOrdinal(int ordinal) {
            Mode[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : OFF;
        }
    }

    public enum AbsorbResult {
        NONE,
        FREE_INVULN,
        CONSUME_RESERVE,
        CONSUME_AUTO_BLOCK
    }

    public static final int DEFAULT_CAPACITY = 32;
    public static final int MIN_CAPACITY = 4;
    public static final int MAX_CAPACITY = 64;

    public static final int HOLD_PICKUP_INTERVAL = 4;
    public static final int RAPID_FIRE_START_INTERVAL = 6;
    public static final int RAPID_FIRE_MIN_INTERVAL = 2;
    public static final int VOLLEY_MIN_CHARGE_TICKS = 10;
    public static final int VOLLEY_CHARGE_TICKS = 20;
    public static final int VOLLEY_SILENCE_TICKS = 4;
    public static final int PARRY_INVULN_TICKS = 10;
    public static final int RECALL_WINDOW_TICKS = 600;
    public static final int UNFOLD_TICKS = 8;
    public static final int AUTO_SCAN_RADIUS = 10;
    public static final int DROP_MIN_SPACING = 2;

    public static final int FLAG_STOWED = 1;
    public static final int FLAG_VOLLEY_CHARGING = 2;
    public static final int FLAG_HIDDEN = 4;

    public static final int EVENT_SYNC = 0;
    public static final int EVENT_PICKUP = 1;
    public static final int EVENT_PARRY = 2;
    public static final int EVENT_FIRE = 3;
    public static final int EVENT_VOLLEY = 4;
    public static final int EVENT_UNFOLD = 5;
    public static final int EVENT_STOW = 6;

    private BlockDominionRules() {}

    public static int clampCapacity(int configured) {
        return Mth.clamp(configured, MIN_CAPACITY, MAX_CAPACITY);
    }

    /** Block filter: excludes air, fluids, block entities, unbreakable blocks, Spatial-displaced blocks and protected blocks. */
    public static boolean canPickUp(boolean isAir, boolean isFluid, boolean hasBlockEntity,
                                    float destroySpeed, boolean isDisplacedBySpatial, boolean isProtected) {
        if (isAir || isFluid || hasBlockEntity || isDisplacedBySpatial || isProtected) return false;
        return destroySpeed >= 0.0f;
    }

    /** Rapid-fire cadence accelerates from 6 ticks/shot down to 2 ticks/shot over a burst. */
    public static int rapidFireInterval(int shotsFiredInBurst) {
        int s = Math.max(0, shotsFiredInBurst);
        if (s <= 1) return RAPID_FIRE_START_INTERVAL;
        if (s <= 3) return 5;
        if (s <= 5) return 4;
        if (s <= 8) return 3;
        return RAPID_FIRE_MIN_INTERVAL;
    }

    /** Center-out release order for Myriad Volley: [center, center-1, center+1, ...]. */
    public static int[] volleyReleaseOrder(int count) {
        if (count <= 0) return new int[0];
        int[] order = new int[count];
        int center = count / 2;
        int out = 0;
        order[out++] = center;
        for (int step = 1; out < count; step++) {
            int left = center - step;
            if (left >= 0) order[out++] = left;
            int right = center + step;
            if (right < count && out < count) order[out++] = right;
        }
        return order;
    }

    /**
     * Outward spiral column offsets (dx, dz) around the player, on a 2-block grid so no two
     * dropped blocks share a column or stack into a tower (minimum spacing >= 2 blocks).
     */
    public static int[][] spiralDropOffsets(int count) {
        if (count <= 0) return new int[0][0];
        int[][] offsets = new int[count][2];
        int gx = 0, gz = 0;
        int dx = 1, dz = 0;
        int legLen = 1, legProgress = 0, turns = 0;
        int produced = 0;
        while (produced < count) {
            gx += dx;
            gz += dz;
            offsets[produced][0] = gx * DROP_MIN_SPACING;
            offsets[produced][1] = gz * DROP_MIN_SPACING;
            produced++;
            legProgress++;
            if (legProgress == legLen) {
                legProgress = 0;
                int nextDx = -dz;
                int nextDz = dx;
                dx = nextDx;
                dz = nextDz;
                turns++;
                if (turns % 2 == 0) legLen++;
            }
        }
        return offsets;
    }

    /** Determines how incoming damage should be handled by Block Dominion defense. */
    public static AbsorbResult decideAbsorb(Mode mode, int storedCount, boolean hasAutoCandidate,
                                            boolean bypassesInvulnerability, boolean alreadyInvulnerable,
                                            boolean ownDominionDamage, boolean inPostParryInvuln) {
        if (bypassesInvulnerability || alreadyInvulnerable) return AbsorbResult.NONE;
        if (ownDominionDamage) return AbsorbResult.FREE_INVULN;
        if (mode == Mode.OFF) return AbsorbResult.NONE;
        if (inPostParryInvuln) return AbsorbResult.FREE_INVULN;
        if (mode == Mode.MANUAL) {
            return storedCount > 0 ? AbsorbResult.CONSUME_RESERVE : AbsorbResult.NONE;
        }
        return hasAutoCandidate ? AbsorbResult.CONSUME_AUTO_BLOCK : AbsorbResult.NONE;
    }

    /** True if deactivated blocks are still within the 30-second recall window (and deactivation was not death). */
    public static boolean canRecall(long nowTick, long deactivatedAtTick, boolean died) {
        if (died || deactivatedAtTick < 0) return false;
        long elapsed = nowTick - deactivatedAtTick;
        return elapsed >= 0 && elapsed <= RECALL_WINDOW_TICKS;
    }

    /**
     * Target offset (relative to player chest at y + 1.25) for block {@code index} of {@code count}.
     * Supports idle tiers (single ring -> twin tilted rings -> full god halo), stowed shoulder cluster,
     * smooth unfold interpolation, and Myriad Volley fan spread.
     */
    public static Vec3 formationOffset(int index, int count, double time, Vec3 lookDir,
                                       boolean stowed, float unfoldProgress,
                                       boolean volleyCharging, float volleyProgress) {
        if (count <= 0) return Vec3.ZERO;
        Vec3 forward = new Vec3(lookDir.x, 0, lookDir.z);
        if (forward.lengthSqr() < 1e-4) forward = new Vec3(0, 0, 1);
        else forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 up = new Vec3(0, 1, 0);

        Vec3 orbit = idleOrbitOffset(index, count, time, forward, right, up);
        Vec3 stow = stowedClusterOffset(index, count, time, forward, right, up);

        Vec3 base;
        if (stowed) {
            base = stow;
        } else if (unfoldProgress < 1.0f) {
            float t = Mth.clamp(unfoldProgress, 0f, 1f);
            float ease = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
            base = stow.lerp(orbit, ease);
        } else {
            base = orbit;
        }

        if (volleyCharging && !stowed) {
            Vec3 fan = volleyFanOffset(index, count, time, lookDir, right, volleyProgress);
            float t = Mth.clamp(volleyProgress, 0f, 1f);
            float ease = 1.0f - (1.0f - t) * (1.0f - t);
            return base.lerp(fan, ease);
        }
        return base;
    }

    public static float blockScale(boolean stowed, float unfoldProgress, boolean volleyCharging, float volleyProgress) {
        float full = volleyCharging ? (0.68f + 0.14f * Mth.clamp(volleyProgress, 0f, 1f)) : 0.62f;
        float tiny = 0.24f;
        if (stowed) return tiny;
        if (unfoldProgress < 1.0f) {
            float t = Mth.clamp(unfoldProgress, 0f, 1f);
            float ease = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
            return Mth.lerp(ease, tiny, full);
        }
        return full;
    }

    private static Vec3 idleOrbitOffset(int index, int count, double time, Vec3 forward, Vec3 right, Vec3 up) {
        if (count <= 8) {
            // Tier 1: single majestic tilted ring orbiting around/behind player
            double angle = time * 0.045 + (Math.PI * 2.0 * index / count);
            double r = 2.05;
            double x = Math.cos(angle) * r;
            double z = Math.sin(angle) * r;
            double y = Math.sin(angle + 0.5) * 0.35 + Math.sin(time * 0.08 + index) * 0.08;
            return right.scale(x).add(forward.scale(z * 0.85 - 0.35)).add(up.scale(y));
        }
        if (count <= 20) {
            // Tier 2: two counter-rotating intersecting tilted rings
            boolean outer = (index & 1) == 0;
            int ringIdx = index / 2;
            int ringCount = outer ? (count + 1) / 2 : count / 2;
            double speed = outer ? 0.042 : -0.052;
            double angle = time * speed + (Math.PI * 2.0 * ringIdx / Math.max(1, ringCount));
            double r = outer ? 2.45 : 1.95;
            double tilt = outer ? 0.44 : -0.44;
            double lx = Math.cos(angle) * r;
            double lz = Math.sin(angle) * r;
            double ly = lz * Math.sin(tilt) + Math.sin(time * 0.07 + index) * 0.07;
            double hz = lz * Math.cos(tilt);
            return right.scale(lx).add(forward.scale(hz - 0.35)).add(up.scale(ly + 0.15));
        }
        // Tier 3 (21+): grand sovereign halo behind the back + orbiting inner ring
        int innerCount = Math.min(10, count / 3);
        if (index < innerCount) {
            double angle = time * 0.055 + (Math.PI * 2.0 * index / innerCount);
            double r = 1.95;
            double lx = Math.cos(angle) * r;
            double lz = Math.sin(angle) * r;
            double ly = Math.sin(angle * 2.0 + time * 0.05) * 0.25 - 0.15;
            return right.scale(lx).add(forward.scale(lz - 0.2)).add(up.scale(ly));
        }
        int haloIdx = index - innerCount;
        int haloCount = count - innerCount;
        boolean outerArc = haloIdx % 2 == 0;
        double arcFrac = (double) haloIdx / Math.max(1, haloCount);
        double angle = arcFrac * Math.PI * 2.0 + time * (outerArc ? 0.028 : -0.036);
        double r = outerArc ? 2.85 : 2.15;
        double lx = Math.cos(angle) * r;
        double ly = Math.sin(angle) * r * 0.88 + 0.55;
        double lz = -1.45 - (outerArc ? 0.35 : 0.0) + Math.cos(angle * 3.0 + time * 0.04) * 0.12;
        return right.scale(lx).add(forward.scale(lz)).add(up.scale(ly));
    }

    private static Vec3 stowedClusterOffset(int index, int count, double time, Vec3 forward, Vec3 right, Vec3 up) {
        // Compact rotating miniature polyhedron behind right shoulder, well clear of first-person camera
        Vec3 anchor = right.scale(0.82).add(forward.scale(-0.78)).add(up.scale(0.48 + Math.sin(time * 0.07) * 0.05));
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        double y = 1.0 - (count <= 1 ? 0.5 : (index / (double) (count - 1))) * 2.0;
        double radiusAtY = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        double theta = golden * index + time * 0.06;
        double clusterR = 0.22 + Math.min(0.18, count * 0.005);
        Vec3 local = right.scale(Math.cos(theta) * radiusAtY * clusterR)
                .add(up.scale(y * clusterR))
                .add(forward.scale(Math.sin(theta) * radiusAtY * clusterR));
        return anchor.add(local);
    }

    private static Vec3 volleyFanOffset(int index, int count, double time, Vec3 aimDir, Vec3 horizRight, float progress) {
        Vec3 aim = aimDir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : aimDir.normalize();
        Vec3 side = aim.cross(Math.abs(aim.y) < 0.95 ? new Vec3(0, 1, 0) : horizRight).normalize();
        Vec3 camUp = side.cross(aim).normalize();
        // Spread into 1 or 2 sweeping fan arcs above and beside the aim line (keeping crosshair clear)
        int rows = count > 16 ? 2 : 1;
        int row = rows == 2 ? (index % 2) : 0;
        int col = rows == 2 ? (index / 2) : index;
        int colCount = rows == 2 ? (row == 0 ? (count + 1) / 2 : count / 2) : count;
        double u = colCount <= 1 ? 0.0 : (col / (double) (colCount - 1)) * 2.0 - 1.0; // -1..+1
        double fanSpan = Math.PI * 0.78;
        double theta = u * (fanSpan * 0.5);
        double r = (row == 0 ? 2.9 : 3.85) * (0.75 + 0.25 * Mth.clamp(progress, 0f, 1f));
        double pullBack = progress > 0.85f ? -0.45 * ((progress - 0.85f) / 0.15f) : 0.0;
        double tremor = progress > 0.7f ? Math.sin(time * 1.4 + index * 2.1) * 0.04 : 0.0;
        return side.scale(Math.sin(theta) * r + tremor)
                .add(camUp.scale(Math.cos(theta) * r * 0.78 + 0.35))
                .add(aim.scale(0.65 - row * 0.45 + pullBack));
    }
}
