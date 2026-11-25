package net.godwand.skill;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Pure rules, timers and Milestone 1 formation math for Block Dominion (mode 9). */
public final class BlockDominionRules {
    public enum Mode {
        OFF, MANUAL, AUTO;

        public Mode next() {
            return this == OFF ? MANUAL : OFF;
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

    public static final int DEFAULT_CAPACITY = 48;
    public static final int MIN_CAPACITY = 4;
    public static final int MAX_CAPACITY = 96;

    public static final int HOLD_PICKUP_INTERVAL = 4;
    public static final int RAPID_FIRE_START_INTERVAL = 6;
    public static final int RAPID_FIRE_MIN_INTERVAL = 2;
    public static final int VOLLEY_MIN_CHARGE_TICKS = 4;
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
    public static final int FLAG_FLYING = 8;
    public static final int FLAG_BOOSTING = 16;
    public static final int FLAG_AIR_STAIRS = 32;
    public static final int FLAG_RAPID_FIRING = 64;
    public static final int FLAG_UMBRELLA = 128;
    public static final int FLAG_GLIDE_MODE = 256;
    public static final int FLAG_STAIR_ASCEND = 512;
    public static final int FLAG_LIQUID_STRIDE = 1024;
    public static final int FLAG_PERCH = 2048;
    public static final int FLAG_METEOR_DIVE = 4096;
    public static final int FLAG_METEOR_TIER2 = 8192;
    public static final int FLAG_METEOR_TIER3 = 16384;

    public static final int EVENT_SYNC = 0;
    public static final int EVENT_PICKUP = 1;
    public static final int EVENT_PARRY = 2;
    public static final int EVENT_FIRE = 3;
    public static final int EVENT_VOLLEY = 4;
    public static final int EVENT_UNFOLD = 5;
    public static final int EVENT_STOW = 6;
    public static final int EVENT_FLIGHT_START = 7;
    public static final int EVENT_FLIGHT_END = 8;
    public static final int EVENT_FLIGHT_BOOST = 9;
    public static final int EVENT_AIR_STEP = 10;
    public static final int EVENT_SURGE = 11;
    public static final int EVENT_ORBITAL_BARRAGE = 12;
    public static final int EVENT_HERO_LAND = 13;
    public static final int EVENT_CAGE_START = 14;
    public static final int EVENT_CAGE_END = 15;
    public static final int EVENT_METEOR_STRIKE = 16;
    public static final int EVENT_CAGE_IMPALE = 17;
    public static final int MAX_EVENT = EVENT_CAGE_IMPALE;

    public static final int METEOR_DIVE_TRIGGER_TICKS = 14;
    public static final double METEOR_DIVE_MIN_LOOK_Y = -0.74;
    public static final double METEOR_DIVE_MIN_VY = -0.90;
    public static final double METEOR_STRIKE_MIN_RADIUS = 6.5;
    public static final double METEOR_STRIKE_MAX_RADIUS = 18.5;

    public static final int SURGE_RADIUS = 8;
    public static final int SURGE_MAX_BLOCKS = 24;
    public static final int SURGE_COOLDOWN_TICKS = 30;
    public static final int CAGE_BLOCK_COST = 8;
    public static final int CAGE_MIN_BLOCKS = CAGE_BLOCK_COST;
    public static final int CAGE_BLOCK_COUNT = CAGE_BLOCK_COST;
    public static final int CAGE_MAX_ACTIVE = 3;
    public static final int CAGE_SPEAR_FORM_TICK = 16;
    public static final int CAGE_IMPALE_TICK = 38;
    public static final int CAGE_DURATION_TICKS = 66;
    public static final int CAGE_MONUMENT_TICKS = 120;
    public static final int CAGE_SPEAR_COUNT = 6;
    public static final int CAGE_COOLDOWN_TICKS = 6;
    public static final double CAGE_RANGE = 22.0;
    public static final int PERCH_IDLE_TICKS = 34;

    public static final int MIN_FLIGHT_BLOCKS = 6;
    public static final int MIN_STAIR_BLOCKS = 4;
    public static final double FLIGHT_CRUISE_SPEED = 0.94;
    public static final double FLIGHT_BOOST_SPEED = 2.35;
    public static final double FLIGHT_ACCEL = 0.135;
    public static final double FLIGHT_BOOST_ACCEL = 0.255;
    public static final double FLIGHT_DRAG = 0.82;
    public static final double GLIDE_MIN_SPEED = 0.48;
    public static final double GLIDE_MAX_SPEED = 2.58;
    public static final double GLIDE_DIVE_MAX_SPEED = 2.85;
    public static final double GLIDE_BOOST_ACCEL = 0.23;
    public static final double STAIR_DESCEND_VY = -0.085;
    public static final double STAIR_ASCEND_VY = 0.165;
    public static final double STAIR_HOP_VY = 0.44;

    private BlockDominionRules() {}

    public static int clampCapacity(int configured) {
        return Mth.clamp(configured, MIN_CAPACITY, MAX_CAPACITY);
    }

    /** Block filter: excludes air, fluids, block entities, non-full-cube blocks (grass/flowers/fire), unbreakable & protected blocks. */
    public static boolean canPickUp(boolean isAir, boolean isFluid, boolean hasBlockEntity,
                                    float destroySpeed, boolean isDisplacedBySpatial, boolean isProtected) {
        return canPickUp(isAir, isFluid, hasBlockEntity, destroySpeed, isDisplacedBySpatial, isProtected, true);
    }

    public static boolean canPickUp(boolean isAir, boolean isFluid, boolean hasBlockEntity,
                                    float destroySpeed, boolean isDisplacedBySpatial, boolean isProtected,
                                    boolean isFullBlock) {
        if (isAir || isFluid || hasBlockEntity || isDisplacedBySpatial || isProtected || !isFullBlock) return false;
        return destroySpeed >= 0.0f;
    }

    /**
     * Number of blocks fired by Myriad Volley / Orbital Barrage based on charge duration:
     * - Below min charge (< 4 ticks): 0 (cancelled)
     * - Full charge (>= 20 ticks): 100% of reserve
     * - Partial charge (4..19 ticks): scales smoothly from ~20% to ~85% so partial charges save remaining blocks!
     */
    public static int volleyShotCount(int chargedTicks, int reserveCount) {
        if (reserveCount <= 0 || chargedTicks < VOLLEY_MIN_CHARGE_TICKS) return 0;
        if (chargedTicks >= VOLLEY_CHARGE_TICKS || reserveCount == 1) return reserveCount;
        float frac = (chargedTicks - VOLLEY_MIN_CHARGE_TICKS)
                / (float) Math.max(1, VOLLEY_CHARGE_TICKS - VOLLEY_MIN_CHARGE_TICKS);
        float ratio = 0.20f + 0.65f * Mth.clamp(frac, 0f, 1f);
        return Mth.clamp(Math.round(reserveCount * ratio), 1, reserveCount - 1);
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
        float full = volleyCharging ? (0.58f + 0.14f * Mth.clamp(volleyProgress, 0f, 1f)) : 0.50f;
        float tiny = 0.22f;
        if (stowed) return tiny;
        if (unfoldProgress < 1.0f) {
            float t = Mth.clamp(unfoldProgress, 0f, 1f);
            float ease = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
            return Mth.lerp(ease, tiny, full);
        }
        return full;
    }

    /**
     * Flight thrust multiplier scaled by orbiting block count for tactical depth:
     * 6 blocks (~0.62x) -> 24 blocks (~0.81x) -> full 48 blocks (1.00x peak) -> 96 blocks (1.10x overcharge).
     */
    public static double wingSpeedScale(int blockCount) {
        int c = Math.max(MIN_FLIGHT_BLOCKS, blockCount);
        if (c <= DEFAULT_CAPACITY) {
            double t = (c - MIN_FLIGHT_BLOCKS) / (double) (DEFAULT_CAPACITY - MIN_FLIGHT_BLOCKS);
            return 0.62 + 0.38 * Mth.clamp(t, 0.0, 1.0);
        }
        double over = (c - DEFAULT_CAPACITY) / (double) (MAX_CAPACITY - DEFAULT_CAPACITY);
        return 1.0 + 0.10 * Mth.clamp(over, 0.0, 1.0);
    }

    /**
     * Pure per-tick 3D free-flight velocity integrator used identically on client and server.
     * Backward-compatible overload assuming full 48-block reserve.
     */
    public static Vec3 stepFlightVelocity(Vec3 currentVel, Vec3 lookDir,
                                          int forwardInput, int strafeInput, int verticalInput, boolean boost) {
        return stepFlightVelocity(currentVel, lookDir, forwardInput, strafeInput, verticalInput, boost, DEFAULT_CAPACITY);
    }

    /**
     * Pure per-tick 3D free-flight velocity integrator scaled by {@code blockCount}.
     * Supports smooth acceleration up to cruise speed, high-speed aerodynamic sprint boost,
     * and smooth exponential deceleration to a regal midair hover when keys are released.
     */
    public static Vec3 stepFlightVelocity(Vec3 currentVel, Vec3 lookDir,
                                          int forwardInput, int strafeInput, int verticalInput, boolean boost,
                                          int blockCount) {
        return stepFlightVelocity(currentVel, lookDir, null, forwardInput, strafeInput, verticalInput, boost, blockCount);
    }

    /**
     * Pure per-tick 3D free-flight velocity integrator scaled by {@code blockCount}, using an explicit
     * gimbal-lock-free horizontal {@code yawForward} so pressing A/D while diving straight down never snaps sideways/backwards.
     */
    public static Vec3 stepFlightVelocity(Vec3 currentVel, Vec3 lookDir, Vec3 yawForward,
                                          int forwardInput, int strafeInput, int verticalInput, boolean boost,
                                          int blockCount) {
        Vec3 cur = currentVel == null ? Vec3.ZERO : currentVel;
        Vec3 look = lookDir == null || lookDir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : lookDir.normalize();
        Vec3 horizFwd = (yawForward != null && yawForward.horizontalDistanceSqr() > 1e-5)
                ? new Vec3(yawForward.x, 0, yawForward.z).normalize()
                : new Vec3(look.x, 0, look.z);
        if (horizFwd.lengthSqr() < 1e-5) horizFwd = new Vec3(0, 0, 1);
        else horizFwd = horizFwd.normalize();
        Vec3 right = new Vec3(-horizFwd.z, 0, horizFwd.x);

        boolean steepDive = look.y < -0.68 && cur.y < -0.55;
        Vec3 wish = Vec3.ZERO;
        if (forwardInput != 0) wish = wish.add(look.scale(Mth.clamp(forwardInput, -1, 1)));
        if (strafeInput != 0) {
            double strafeGain = steepDive ? 0.32 : 1.0;
            wish = wish.add(right.scale(Mth.clamp(strafeInput, -1, 1) * strafeGain));
            if (steepDive && forwardInput == 0) {
                // Keep diving smoothly downward while trimming left/right with A/D instead of stalling sideways!
                wish = wish.add(look.scale(0.95));
            }
        }
        if (verticalInput != 0) wish = wish.add(0, Mth.clamp(verticalInput, -1, 1), 0);

        if (wish.lengthSqr() > 1e-5) {
            double scale = wingSpeedScale(blockCount);
            Vec3 wishDir = wish.normalize();
            double maxSpeed = (boost ? FLIGHT_BOOST_SPEED : FLIGHT_CRUISE_SPEED) * scale;
            double accel = (boost ? FLIGHT_BOOST_ACCEL : FLIGHT_ACCEL) * (0.80 + 0.20 * scale);
            Vec3 targetVel = wishDir.scale(maxSpeed);
            Vec3 diff = targetVel.subtract(cur);
            double diffLen = diff.length();
            if (diffLen <= accel) {
                return targetVel;
            }
            // Blend steering smoothly so sharp turns feel crisp while acceleration stays weighty
            Vec3 next = cur.add(diff.scale(accel / diffLen));
            double nextLen = next.length();
            if (nextLen > maxSpeed) {
                next = next.scale(maxSpeed / nextLen);
            }
            return next;
        }

        // No movement keys held -> smooth aerodynamic braking into midair hover
        Vec3 damped = cur.scale(FLIGHT_DRAG);
        return damped.lengthSqr() < 0.000064 ? Vec3.ZERO : damped;
    }

    /**
     * Elytra-style aerodynamic gliding physics with firework-like rocket boost (Mode 2 of Block Wings).
     * Backward-compatible overload assuming full 48-block reserve.
     */
    public static Vec3 stepGlideVelocity(Vec3 currentVel, Vec3 lookDir, int strafeInput, boolean boost) {
        return stepGlideVelocity(currentVel, lookDir, null, strafeInput, boost, DEFAULT_CAPACITY);
    }

    /**
     * Elytra-style aerodynamic gliding physics scaled by {@code blockCount}:
     * carrying more orbiting blocks visibly boosts both Jet top speed and dive velocity.
     */
    public static Vec3 stepGlideVelocity(Vec3 currentVel, Vec3 lookDir, int strafeInput, boolean boost, int blockCount) {
        return stepGlideVelocity(currentVel, lookDir, null, strafeInput, boost, blockCount);
    }

    /**
     * Gimbal-lock-free Elytra-style gliding physics using explicit horizontal {@code yawForward} so A/D banking
     * during vertical Meteor Dives steers gently along true player-right instead of snapping diagonally backward.
     */
    public static Vec3 stepGlideVelocity(Vec3 currentVel, Vec3 lookDir, Vec3 yawForward,
                                         int strafeInput, boolean boost, int blockCount) {
        Vec3 cur = currentVel == null ? Vec3.ZERO : currentVel;
        Vec3 look = lookDir == null || lookDir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : lookDir.normalize();
        Vec3 horizFwd = (yawForward != null && yawForward.horizontalDistanceSqr() > 1e-5)
                ? new Vec3(yawForward.x, 0, yawForward.z).normalize()
                : new Vec3(look.x, 0, look.z);
        double horizLen = horizFwd.length();
        Vec3 right = horizLen > 1e-4
                ? new Vec3(-horizFwd.z / horizLen, 0, horizFwd.x / horizLen)
                : new Vec3(-1, 0, 0);

        double scale = wingSpeedScale(blockCount);
        double minSpeed = GLIDE_MIN_SPEED * (0.85 + 0.15 * scale);
        double curSpeed = cur.length();
        double baseSpeed = Math.max(minSpeed, curSpeed);

        // Energy exchange: steep diving accelerates like a falling comet; climbing trades speed for altitude
        double pitchEnergy = look.y < -0.55 ? (-look.y * 0.125) : (-look.y * 0.088);
        double speed = baseSpeed + pitchEnergy;
        if (boost) {
            speed += GLIDE_BOOST_ACCEL * (0.80 + 0.20 * scale);
        } else {
            // Gentle aerodynamic drag above cruise glide speed
            speed *= 0.985;
        }
        double maxSpeed = (look.y < -0.55 ? GLIDE_DIVE_MAX_SPEED : GLIDE_MAX_SPEED) * scale;
        speed = Mth.clamp(speed, minSpeed, maxSpeed);

        // Blend velocity direction smoothly toward 3D look vector + damped horizontal banking from A/D during steep dives
        Vec3 desiredDir = look;
        if (strafeInput != 0) {
            double steepness = Mth.clamp((-look.y - 0.45) / 0.50, 0.0, 1.0);
            double bankAmt = Mth.lerp(steepness, 0.40, 0.16);
            desiredDir = desiredDir.add(right.scale(Mth.clamp(strafeInput, -1, 1) * bankAmt)).normalize();
        }
        Vec3 curDir = curSpeed > 0.05 ? cur.scale(1.0 / curSpeed) : desiredDir;
        double steerRate = boost ? 0.34 : 0.22;
        Vec3 newDir = curDir.lerp(desiredDir, steerRate);
        newDir = newDir.lengthSqr() > 1e-5 ? newDir.normalize() : desiredDir;

        Vec3 next = newDir.scale(speed);
        // Gentle natural glide sink when unboosted and flying level
        if (!boost && look.y > -0.15 && look.y < 0.25) {
            next = next.add(0, -0.022, 0);
        }
        return next;
    }

    /** True only when the airborne player is deliberately plunging steeply downward (>= ~48 deg) at high speed. */
    public static boolean isSteepMeteorDive(Vec3 lookDir, Vec3 vel, boolean glideMode) {
        if (lookDir == null || vel == null) return false;
        if (vel.y > METEOR_DIVE_MIN_VY) return false;
        if (lookDir.y <= METEOR_DIVE_MIN_LOOK_Y) return true;
        return glideMode && vel.y <= -1.25 && lookDir.y <= -0.64;
    }

    /**
     * Normalized 0..1 Meteor Dive charge from deliberate sustained steep plunge ticks, altitude dropped, and block reserve.
     * Prevents accidental low-altitude landings from triggering Meteor Strike!
     */
    public static float meteorDiveCharge(int diveTicks, double dropBlocks, int blockCount) {
        if (diveTicks < METEOR_DIVE_TRIGGER_TICKS || dropBlocks < 14.0) return 0f;
        double tickFrac = Mth.clamp((diveTicks - METEOR_DIVE_TRIGGER_TICKS) / 26.0, 0.0, 1.0);
        double dropFrac = Mth.clamp((dropBlocks - 14.0) / 46.0, 0.0, 1.0);
        double blockFrac = Mth.clamp((blockCount - MIN_FLIGHT_BLOCKS) / (double) (DEFAULT_CAPACITY - MIN_FLIGHT_BLOCKS), 0.0, 1.0);
        double raw = (tickFrac * 0.50 + dropFrac * 0.50) * (0.75 + 0.25 * blockFrac);
        return (float) Mth.clamp(raw, 0.0, 1.0);
    }

    /**
     * Discrete Meteor Strike Dive escalation tier:
     * 0 = inactive (normal landing), 1 = Meteor I (>=14 ticks & >=14m drop),
     * 2 = Meteor II (>=26 ticks & >=32m drop), 3 = Tier III CATACLYSM (>=38 ticks & >=56m drop, full charge!).
     */
    public static int meteorDiveTier(int diveTicks, double dropBlocks, int blockCount) {
        if (diveTicks < METEOR_DIVE_TRIGGER_TICKS || dropBlocks < 14.0) return 0;
        float charge = meteorDiveCharge(diveTicks, dropBlocks, blockCount);
        if (diveTicks >= 36 && dropBlocks >= 54.0 && ((charge >= 0.80f && blockCount >= 24) || charge >= 0.92f)) return 3;
        if (diveTicks >= 24 && dropBlocks >= 30.0 && charge >= 0.38f && blockCount >= 14) return 2;
        return 1;
    }

    /**
     * Number of orbiting reserve blocks consumed on Meteor Strike detonation:
     * Tier 1 consumes ~25% (min 6), Tier 2 consumes ~55% (min 14), Tier 3 CATACLYSM sacrifices ALL blocks (100%)!
     */
    public static int meteorBlockCost(int tier, int currentReserve) {
        if (currentReserve <= 0 || tier <= 0) return 0;
        if (tier >= 3) return currentReserve;
        if (tier == 2) return Math.min(currentReserve, Math.max(14, Math.round(currentReserve * 0.55f)));
        return Math.min(currentReserve, Math.max(6, Math.round(currentReserve * 0.25f)));
    }

    /**
     * Pure per-tick vertical velocity controller for Air-Step Block Stairs (ascending AND descending).
     * Backward-compatible 3-arg overload delegates to the full ascending/descending controller.
     */
    public static Vec3 stepAirStairsVelocity(Vec3 currentVel, double horizSpeed, boolean jumpPressed) {
        if (jumpPressed) {
            Vec3 cur = currentVel == null ? Vec3.ZERO : currentVel;
            return new Vec3(cur.x, STAIR_HOP_VY, cur.z);
        }
        return stepAirStairsVelocity(currentVel, horizSpeed, false, false);
    }

    /**
     * Smooth step-by-step walking staircase controller:
     * - {@code ascending=true}: walks gracefully upward step-by-step when moving (or lifts gently when holding Space)
     * - {@code ascending=false}: walks slowly & regally downward step-by-step when moving, holds dead level when still.
     */
    public static Vec3 stepAirStairsVelocity(Vec3 currentVel, double horizSpeed, boolean ascending, boolean holdingJump) {
        Vec3 cur = currentVel == null ? Vec3.ZERO : currentVel;
        if (ascending) {
            double targetVy = horizSpeed > 0.025 ? STAIR_ASCEND_VY : (holdingJump ? 0.13 : 0.0);
            return new Vec3(cur.x, Mth.lerp(0.60, cur.y, targetVy), cur.z);
        }
        double targetVy = horizSpeed > 0.025 ? STAIR_DESCEND_VY : 0.0;
        double vy = cur.y < targetVy ? Mth.lerp(0.68, cur.y, targetVy) : Math.min(cur.y, 0.26);
        return new Vec3(cur.x, vy, cur.z);
    }

    /**
     * Liquid Stride vertical velocity controller: keeps the player buoyed cleanly atop water or lava surfaces
     * so stepping stones feel solid under their soles.
     */
    public static Vec3 stepLiquidStrideVelocity(Vec3 currentVel, double feetY, double surfaceY, boolean jumpPressed) {
        Vec3 cur = currentVel == null ? Vec3.ZERO : currentVel;
        if (jumpPressed) {
            return new Vec3(cur.x, 0.42, cur.z);
        }
        double delta = surfaceY - feetY;
        double vy = delta > 0.02 ? Mth.clamp(delta * 0.48, 0.04, 0.34) : Math.max(0.0, cur.y * 0.4);
        return new Vec3(cur.x, vy, cur.z);
    }

    /**
     * Kinetic Deflect return velocity: redirects an intercepted projectile back toward its shooter (or straight back
     * along its incoming path when no shooter is available) with amplified kinetic speed.
     */
    public static Vec3 deflectVelocity(Vec3 incomingVel, Vec3 impactPos, Vec3 shooterPos, double minSpeed) {
        double speed = Math.max(minSpeed, (incomingVel == null ? 0.0 : incomingVel.length()) * 1.35);
        Vec3 dir;
        if (shooterPos != null && impactPos != null && shooterPos.distanceToSqr(impactPos) > 0.25) {
            dir = shooterPos.subtract(impactPos).normalize();
        } else if (incomingVel != null && incomingVel.lengthSqr() > 1e-5) {
            dir = incomingVel.normalize().scale(-1.0);
        } else {
            dir = new Vec3(0, 0.15, 1).normalize();
        }
        return dir.scale(speed);
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
        // Tier 3 (21+): colossal multi-ring sovereign armada — inner orbiting belt + twin diagonal rings + grand looming halo
        int innerCount = Math.min(12, count / 4);
        if (index < innerCount) {
            double angle = time * 0.058 + (Math.PI * 2.0 * index / innerCount);
            double r = 2.05;
            double lx = Math.cos(angle) * r;
            double lz = Math.sin(angle) * r;
            double ly = Math.sin(angle * 2.0 + time * 0.05) * 0.30 - 0.18;
            return right.scale(lx).add(forward.scale(lz - 0.25)).add(up.scale(ly));
        }
        int midCount = count >= 34 ? Math.min(14, (count - innerCount) / 2) : 0;
        if (index < innerCount + midCount) {
            int mi = index - innerCount;
            boolean tiltA = (mi & 1) == 0;
            int ri = mi / 2;
            int rc = Math.max(1, tiltA ? (midCount + 1) / 2 : midCount / 2);
            double angle = time * (tiltA ? 0.036 : -0.044) + (Math.PI * 2.0 * ri / rc);
            double r = tiltA ? 3.25 : 2.70;
            double tilt = tiltA ? 0.56 : -0.56;
            double lx = Math.cos(angle) * r;
            double lz = Math.sin(angle) * r;
            double ly = lz * Math.sin(tilt) + 0.55;
            double hz = lz * Math.cos(tilt);
            return right.scale(lx).add(forward.scale(hz - 0.85)).add(up.scale(ly));
        }
        int haloIdx = index - innerCount - midCount;
        int haloCount = Math.max(1, count - innerCount - midCount);
        int ring = haloIdx % 2;
        double arcFrac = (double) haloIdx / haloCount;
        double angle = arcFrac * Math.PI * 2.0 + time * (ring == 0 ? 0.024 : -0.032);
        double r = ring == 0 ? 3.65 : 2.55;
        double lx = Math.cos(angle) * r;
        double ly = Math.sin(angle) * r * 0.86 + 0.95;
        double lz = -1.65 - (ring == 0 ? 0.55 : 0.0) + Math.cos(angle * 3.0 + time * 0.04) * 0.16;
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
