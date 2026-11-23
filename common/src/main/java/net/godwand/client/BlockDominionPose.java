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
        float lastYaw = Float.NaN;
        long lastTick;
    }

    private static final Map<Integer, PoseState> STATES = new HashMap<>();

    private BlockDominionPose() {}

    public static void reset() {
        STATES.clear();
    }

    /** Called once per client tick from BlockDominionRenderer for every visible player using Block Dominion. */
    public static void tickEntity(LivingEntity entity, boolean flying, boolean glideMode, boolean boosting,
                                  boolean volleyCharging, long nowTick) {
        if (entity == null) return;
        PoseState st = STATES.computeIfAbsent(entity.getId(), k -> new PoseState());
        st.prevPitchDeg = st.pitchDeg;
        st.prevRollDeg = st.rollDeg;
        st.prevGlideWeight = st.glideWeight;
        st.prevHoverWeight = st.hoverWeight;
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

            if (glideMode || (boosting && speed > 0.65)) {
                // Aerodynamic horizontal flight along 3D velocity vector (62..88 deg pitch forward like Elytra!)
                targetGlide = 1.0f;
                float velPitchDeg = speed > 0.12
                        ? (float) Math.toDegrees(Math.atan2(-vel.y, Math.max(0.18, horizSpeed)))
                        : 0f;
                targetPitch = Mth.clamp(74.0f + velPitchDeg * 0.62f, 44.0f, 108.0f);
                // Natural fighter-jet bank roll into turns
                targetRoll = Mth.clamp(yawDelta * 2.1f, -24.0f, 24.0f);
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
    }

    public static void prune(long nowTick) {
        STATES.entrySet().removeIf(e -> nowTick - e.getValue().lastTick > 40);
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
        float total = Math.max(glide, hover);
        if (total < 0.01f) return;

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
        if (glide < 0.02f && hover < 0.02f) return;

        float t = entity.tickCount + partialTick;

        if (glide >= hover) {
            float w = Mth.clamp(glide, 0f, 1f);
            float flutter = (float) Math.sin(t * 0.45f) * 0.035f;
            // Look ahead along flight path instead of staring down at terrain
            head.xRot = Mth.lerp(w, head.xRot, -0.78f);
            // Aerodynamic swept-back arms (jet/falcon silhouette)
            rightArm.xRot = Mth.lerp(w, rightArm.xRot, 0.56f + flutter);
            rightArm.yRot = Mth.lerp(w, rightArm.yRot, -0.18f);
            rightArm.zRot = Mth.lerp(w, rightArm.zRot, 0.32f);
            leftArm.xRot = Mth.lerp(w, leftArm.xRot, 0.56f - flutter);
            leftArm.yRot = Mth.lerp(w, leftArm.yRot, 0.18f);
            leftArm.zRot = Mth.lerp(w, leftArm.zRot, -0.32f);
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
