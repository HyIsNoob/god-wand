package net.godwand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.godwand.skill.BlockDominionBrain;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Third-person & multiplayer 3D body and limb animation controller for Block Dominion:
 * - Glide / Jet / Mach flight: full horizontal aerodynamic Superman/Elytra body pitch along 3D velocity + turn bank roll,
 *   arms swept cleanly back, legs extended together with subtle wind flutter (zero walking swing!).
 * - Hover flight: regal midair levitation pose (slight forward poise, weightless vertical bob, asymmetric bent-knee hover stance).
 * - Volley / Orbital Meteor charge: dramatic summoning pose raising arms to command the floating stone armada.
 */
public final class BlockDominionPose {
    private static final class PoseState {
        float pitchDeg, prevPitchDeg;
        float rollDeg, prevRollDeg;
        float glideWeight, prevGlideWeight;
        float hoverWeight, prevHoverWeight;
        float landWeight, prevLandWeight;
        long landStartTick = -1000;
        float lastYaw = Float.NaN;
        long lastTick;
    }

    private static final Map<Integer, PoseState> STATES = new HashMap<>();

    private BlockDominionPose() {}

    public static void reset() {
        STATES.clear();
    }

    /** Triggers a crisp 3-point superhero touchdown crouch pose lasting ~24 ticks on Hero Land / Meteor Strike. */
    public static void triggerHeroLanding(int entityId, long nowTick) {
        PoseState st = STATES.computeIfAbsent(entityId, k -> new PoseState());
        st.landStartTick = nowTick;
        st.lastTick = nowTick;
    }

    /** Called once per client tick from BlockDominionRenderer for every visible player using Block Dominion. */
    public static void tickEntity(LivingEntity entity, boolean flying, boolean glideMode, boolean boosting,
                                  boolean volleyCharging, long nowTick) {
        tickEntity(entity, flying, glideMode, boosting, volleyCharging, false, nowTick);
    }

    public static void tickEntity(LivingEntity entity, boolean flying, boolean glideMode, boolean boosting,
                                  boolean volleyCharging, boolean meteorDive, long nowTick) {
        if (entity == null) return;
        PoseState st = STATES.computeIfAbsent(entity.getId(), k -> new PoseState());
        st.prevPitchDeg = st.pitchDeg;
        st.prevRollDeg = st.rollDeg;
        st.prevGlideWeight = st.glideWeight;
        st.prevHoverWeight = st.hoverWeight;
        st.prevLandWeight = st.landWeight;
        st.lastTick = nowTick;

        Vec3 vel = entity.getDeltaMovement();
        double speed = vel.length();
        double horizSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);

        float targetPitch = 0f;
        float targetRoll = 0f;
        float targetGlide = 0f;
        float targetHover = 0f;

        if (flying && !volleyCharging) {
            float yaw = entity.getYRot();
            float yawDelta = Float.isNaN(st.lastYaw) ? 0f : Mth.wrapDegrees(yaw - st.lastYaw);
            st.lastYaw = yaw;

            if (BlockDominionRenderer.formationOf(entity.getId()) == BlockDominionBrain.FormationKind.SOVEREIGN_PERCH) {
                // Standing regally upright atop the floating stone pedestal
                targetHover = 0.5f;
                targetPitch = 0f;
                targetRoll = 0f;
            } else if (meteorDive) {
                // Head-first Meteor Strike plunge along 3D dive vector (128..166 deg nose-down comet silhouette!)
                targetGlide = 1.0f;
                Vec3 dir = speed > 0.14 ? vel.normalize() : entity.getViewVector(1.0f);
                double dh = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
                float divePitchDeg = (float) Math.toDegrees(Math.atan2(-dir.y, Math.max(0.01, dh)));
                targetPitch = Mth.clamp(86.0f + divePitchDeg * 0.86f, 128.0f, 166.0f);
                targetRoll = Mth.clamp(yawDelta * 1.2f, -16.0f, 16.0f);
            } else if (glideMode || (boosting && speed > 0.65)) {
                // Full-range 3D aerodynamic flight along velocity/look:
                // 10 deg (straight-up rocket climb) <-> 82 deg (flat horizontal glide) <-> 160 deg (steep dive)!
                targetGlide = 1.0f;
                Vec3 dir = speed > 0.14 ? vel.normalize() : entity.getViewVector(1.0f);
                double dh = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
                float flightPitchDeg = (float) Math.toDegrees(Math.atan2(-dir.y, Math.max(0.02, dh)));
                targetPitch = Mth.clamp(82.0f + flightPitchDeg * 0.86f, 10.0f, 160.0f);
                // Natural fighter-jet bank roll into horizontal turns, suppressed during near-vertical climbs/dives
                float horizFactor = Mth.clamp((float) Math.sin(Math.toRadians(targetPitch)), 0.15f, 1.0f);
                targetRoll = Mth.clamp(yawDelta * 2.1f * horizFactor, -24.0f, 24.0f);
            } else {
                // Regal midair hover: leans gracefully with horizontal velocity, stays upright when hovering still
                targetHover = 1.0f;
                float speedLean = Mth.clamp((float) (horizSpeed / 0.72), 0f, 1f);
                targetPitch = 6.0f + speedLean * 28.0f;
                targetRoll = Mth.clamp(yawDelta * 0.95f, -12.0f, 12.0f);
            }
        } else {
            st.lastYaw = entity.getYRot();
        }

        st.pitchDeg = Mth.lerp(0.28f, st.pitchDeg, targetPitch);
        st.rollDeg = Mth.lerp(0.24f, st.rollDeg, targetRoll);
        st.glideWeight = Mth.lerp(0.26f, st.glideWeight, targetGlide);
        st.hoverWeight = Mth.lerp(0.26f, st.hoverWeight, targetHover);

        long landAge = nowTick - st.landStartTick;
        float targetLand = (!flying && landAge >= 0 && landAge < 22)
                ? (landAge < 12 ? 1.0f : Mth.clamp(1.0f - (landAge - 12) / 10.0f, 0f, 1f))
                : 0f;
        st.landWeight = Mth.lerp(targetLand > st.landWeight ? 0.55f : 0.28f, st.landWeight, targetLand);
    }

    public static void prune(long nowTick) {
        STATES.entrySet().removeIf(e -> nowTick - e.getValue().lastTick > 40);
    }

    /** Returns the smooth interpolated forward body pitch in degrees (10 rocket climb -> 82 horizontal Glide -> 162 Meteor Dive). */
    public static float bodyPitchDegrees(int entityId, float partialTick) {
        PoseState st = STATES.get(entityId);
        return st == null ? 0f : Mth.lerp(partialTick, st.prevPitchDeg, st.pitchDeg);
    }

    /** Returns the smooth interpolated turn bank roll in degrees for syncing horizontal wings with the player body. */
    public static float bodyRollDegrees(int entityId, float partialTick) {
        PoseState st = STATES.get(entityId);
        return st == null ? 0f : Mth.lerp(partialTick, st.prevRollDeg, st.rollDeg);
    }

    /** Returns smooth aerodynamic Glide/Climb/Dive blend (0 = upright Hover arch, 1 = sleek aerodynamic swept wings). */
    public static float glideWeight(int entityId, float partialTick) {
        PoseState st = STATES.get(entityId);
        return st == null ? 0f : Mth.clamp(Mth.lerp(partialTick, st.prevGlideWeight, st.glideWeight), 0f, 1f);
    }

    /**
     * Rotates the entire player body in LivingEntityRenderer.setupRotations (smoothly horizontal during Glide/Jet/Mach,
     * with subtle aerodynamic turn banking and weightless hover bob).
     */
    public static void applyBodyRotations(LivingEntity entity, PoseStack matrices, float partialTick) {
        if (entity == null || entity.isFallFlying() || entity.isAutoSpinAttack()) return;
        PoseState st = STATES.get(entity.getId());
        if (st == null) return;

        float glide = Mth.lerp(partialTick, st.prevGlideWeight, st.glideWeight);
        float hover = Mth.lerp(partialTick, st.prevHoverWeight, st.hoverWeight);
        float land = Mth.lerp(partialTick, st.prevLandWeight, st.landWeight);
        float total = Math.max(Math.max(glide, hover), land);
        if (total < 0.01f) return;

        if (land > 0.02f && glide < 0.15f && hover < 0.15f) {
            // Superhero 3-point impact crouch: drops torso sharply into a grounded knee-bend & leans forward!
            matrices.translate(0.0, -0.26 * land, 0.0);
            matrices.translate(0.0, 0.85, 0.0);
            matrices.mulPose(Axis.XP.rotationDegrees(-24.0f * land));
            matrices.translate(0.0, -0.85, 0.0);
            return;
        }

        float pitch = Mth.lerp(partialTick, st.prevPitchDeg, st.pitchDeg);
        float roll = Mth.lerp(partialTick, st.prevRollDeg, st.rollDeg);

        // Weightless subtle vertical hover float
        if (hover > 0.02f) {
            float t = (entity.tickCount + partialTick) * 0.14f;
            matrices.translate(0.0, Math.sin(t) * 0.065 * hover, 0.0);
        }

        // Pivot around torso center (y = 0.95) so horizontal body pitch aligns naturally with the Block Wings!
        matrices.translate(0.0, 0.95, 0.0);
        if (Math.abs(roll) > 0.05f) {
            matrices.mulPose(Axis.ZP.rotationDegrees(roll));
        }
        if (Math.abs(pitch) > 0.05f) {
            // In LivingEntityRenderer after YP(180 - bodyYaw), +X pitch leans the character's head FORWARD in their facing direction!
            matrices.mulPose(Axis.XP.rotationDegrees(-pitch));
        }
        matrices.translate(0.0, -0.95, 0.0);
    }

    /**
     * Overrides vanilla walking swing on HumanoidModel during Block Wings flight (Glide / Hover)
     * and Volley / Orbital Meteor summoning. Works for local and multiplayer players alike.
     */
    public static void applyLimbPose(LivingEntity entity, float partialTick,
                                     ModelPart head, ModelPart body,
                                     ModelPart rightArm, ModelPart leftArm,
                                     ModelPart rightLeg, ModelPart leftLeg,
                                     ModelPart hat) {
        if (entity == null) return;
        if (AtomicRenderer.frameFor(entity.getId(), partialTick) != null
                || FugaRenderer.frameFor(entity.getId(), partialTick) != null) {
            return;
        }

        BlockDominionBrain.FormationKind form = BlockDominionRenderer.formationOf(entity.getId());
        if (form == BlockDominionBrain.FormationKind.SOVEREIGN_PERCH) {
            // Noble upright sovereign stance perched atop floating hexagonal dais (crossed/poised arms, calm legs)
            float sway = (float) Math.sin((entity.tickCount + partialTick) * 0.08f) * 0.025f;
            leftArm.xRot = -0.82f + sway;
            leftArm.yRot = 0.55f;
            leftArm.zRot = -0.18f;
            rightArm.xRot = -0.74f - sway;
            rightArm.yRot = -0.38f;
            rightArm.zRot = 0.16f;
            rightLeg.xRot = 0.04f;
            rightLeg.zRot = 0.05f;
            leftLeg.xRot = -0.04f;
            leftLeg.zRot = -0.05f;
            hat.copyFrom(head);
            return;
        }
        if (form == BlockDominionBrain.FormationKind.VOLLEY_FAN
                || form == BlockDominionBrain.FormationKind.ORBITAL_METEOR) {
            boolean orbital = form == BlockDominionBrain.FormationKind.ORBITAL_METEOR;
            float t = (entity.tickCount + partialTick) * 0.25f;
            float tremor = (float) Math.sin(t * 2.2f) * 0.035f;
            // Dramatic sovereign summoning pose: arms raised commanding the floating stone armada
            rightArm.xRot = (orbital ? -2.28f : -1.55f) + tremor;
            rightArm.zRot = 0.34f;
            leftArm.xRot = (orbital ? -2.18f : -1.35f) - tremor;
            leftArm.zRot = -0.34f;
            if (orbital) {
                rightLeg.xRot = 0.14f;
                leftLeg.xRot = -0.30f;
            }
            hat.copyFrom(head);
            return;
        }

        PoseState st = STATES.get(entity.getId());
        if (st == null) return;

        float glide = Mth.lerp(partialTick, st.prevGlideWeight, st.glideWeight);
        float hover = Mth.lerp(partialTick, st.prevHoverWeight, st.hoverWeight);
        float land = Mth.lerp(partialTick, st.prevLandWeight, st.landWeight);
        if (glide < 0.02f && hover < 0.02f && land < 0.02f) return;

        float t = entity.tickCount + partialTick;

        if (land > 0.02f && glide < 0.25f && hover < 0.25f) {
            float w = Mth.clamp(land, 0f, 1f);
            // 3-point hero landing: right fist slammed down toward earth, left arm swept back, heroic bent-knee crouch!
            head.xRot = Mth.lerp(w, head.xRot, 0.22f);
            rightArm.xRot = Mth.lerp(w, rightArm.xRot, -1.05f);
            rightArm.zRot = Mth.lerp(w, rightArm.zRot, -0.18f);
            leftArm.xRot = Mth.lerp(w, leftArm.xRot, 0.85f);
            leftArm.zRot = Mth.lerp(w, leftArm.zRot, -0.45f);
            rightLeg.xRot = Mth.lerp(w, rightLeg.xRot, -0.78f);
            rightLeg.zRot = Mth.lerp(w, rightLeg.zRot, 0.14f);
            leftLeg.xRot = Mth.lerp(w, leftLeg.xRot, 0.62f);
            leftLeg.zRot = Mth.lerp(w, leftLeg.zRot, -0.14f);
            hat.copyFrom(head);
            return;
        }

        if (glide >= hover) {
            float w = Mth.clamp(glide, 0f, 1f);
            float flutter = (float) Math.sin(t * 0.45f) * 0.035f;
            float pitch = Mth.lerp(partialTick, st.prevPitchDeg, st.pitchDeg);
            boolean diving = form == BlockDominionBrain.FormationKind.METEOR_DIVE;
            // Smooth head pitch: looks up smoothly during steep climb, ahead during level flight, down during Meteor Dive
            float targetHead = diving ? -0.82f : Mth.lerp(Mth.clamp((pitch - 12f) / 68f, 0f, 1f), -0.42f, -0.78f);
            head.xRot = Mth.lerp(w, head.xRot, targetHead);
            // Aerodynamic swept-back arms (ultra-tight bullet tuck during Meteor Dive!)
            float armBack = diving ? 0.74f : 0.56f;
            float armSpread = diving ? 0.16f : 0.32f;
            rightArm.xRot = Mth.lerp(w, rightArm.xRot, armBack + flutter);
            rightArm.yRot = Mth.lerp(w, rightArm.yRot, -0.18f);
            rightArm.zRot = Mth.lerp(w, rightArm.zRot, armSpread);
            leftArm.xRot = Mth.lerp(w, leftArm.xRot, armBack - flutter);
            leftArm.yRot = Mth.lerp(w, leftArm.yRot, 0.18f);
            leftArm.zRot = Mth.lerp(w, leftArm.zRot, -armSpread);
            // Legs extended straight behind with zero walking swing!
            rightLeg.xRot = Mth.lerp(w, rightLeg.xRot, 0.16f + flutter * 0.6f);
            rightLeg.zRot = Mth.lerp(w, rightLeg.zRot, 0.05f);
            leftLeg.xRot = Mth.lerp(w, leftLeg.xRot, 0.22f - flutter * 0.6f);
            leftLeg.zRot = Mth.lerp(w, leftLeg.zRot, -0.05f);
            hat.copyFrom(head);
        } else {
            float w = Mth.clamp(hover, 0f, 1f);
            float sway = (float) Math.sin(t * 0.11f) * 0.05f;
            // Regal midair hover stance (asymmetric bent knee + poised wand arm, zero walking swing!)
            rightArm.xRot = Mth.lerp(w, rightArm.xRot, -0.42f + sway * 0.6f);
            rightArm.zRot = Mth.lerp(w, rightArm.zRot, 0.18f);
            leftArm.xRot = Mth.lerp(w, leftArm.xRot, -0.14f - sway * 0.6f);
            leftArm.zRot = Mth.lerp(w, leftArm.zRot, -0.24f);
            rightLeg.xRot = Mth.lerp(w, rightLeg.xRot, 0.18f + sway * 0.4f);
            rightLeg.zRot = Mth.lerp(w, rightLeg.zRot, 0.06f);
            leftLeg.xRot = Mth.lerp(w, leftLeg.xRot, -0.34f - sway * 0.4f);
            leftLeg.zRot = Mth.lerp(w, leftLeg.zRot, -0.06f);
            hat.copyFrom(head);
        }
    }
}
