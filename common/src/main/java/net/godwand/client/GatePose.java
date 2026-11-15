package net.godwand.client;

import net.godwand.client.SilentGateRenderer.Frame;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Caster performance of Silent Gate: arm and head angles (vanilla joints only), and the floating wand that follows the right hand.
 * Everything here is a pure function of the cast clock so tests can pin the angles at the beat ticks.
 * Joint signs: right arm {@code yaw < 0} and {@code roll > 0} swing it toward the body / outward, the left arm is mirrored.
 */
public final class GatePose {
    private GatePose() {}

    /** Arm joints in radians plus a head pitch (positive looks down). */
    public record Joints(float rp, float ry, float rr, float lp, float ly, float lr, float head) {
        Joints mix(Joints o, double t) {
            float k = (float) t;
            return new Joints(Mth.lerp(k, rp, o.rp), Mth.lerp(k, ry, o.ry), Mth.lerp(k, rr, o.rr),
                    Mth.lerp(k, lp, o.lp), Mth.lerp(k, ly, o.ly), Mth.lerp(k, lr, o.lr), Mth.lerp(k, head, o.head));
        }
    }
    /** Keyframe poses. R = right arm part, L = left arm part, both stored in one record for brevity (the other half is ignored). */
    static final Joints REST = new Joints(-.10f, 0, .05f, -.10f, 0, -.05f, 0),
            HOVER = new Joints(-.30f, -.12f, .14f, -.30f, .12f, -.14f, 0),
            /** Right hand at face height in front of the shoulder: the wand is spun around the hand here. */
            FACE = new Joints(-1.95f, -.30f, .10f, 0, 0, 0, 0),
            /** Right hand in front of the hip, wand horizontal. */
            HIP = new Joints(-.80f, -.45f, .10f, 0, 0, 0, 0),
            /** Left index finger at the lips ("shh"): the arm cannot bend, so it reaches across and up toward the mouth. */
            LIPS = new Joints(0, 0, 0, -1.78f, .60f, -.04f, 0),
            /** Right arm straight to the sky (reference image 10). */
            POINT = new Joints(-2.95f, -.04f, .64f, 0, 0, 0, 0),
            /** The command: arm thrown forward and down. */
            THROW = new Joints(-.95f, -.05f, .06f, 0, 0, 0, 0), AIM = new Joints(-1.15f, -.05f, .06f, 0, 0, 0, 0);
    /** The wand drawn at this scale of its model units (model is 4.5 units long, ~1.5 blocks here). */
    public static final double WAND_SCALE = .34;
    /** Head end and pommel end of the wand along its axis, in blocks. */
    static final double TIP = 1.75 * WAND_SCALE, POMMEL = 2.75 * WAND_SCALE;
    static final Vec3 UP = new Vec3(0, 1, 0);

    static double sm(double x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
    static double sm(double t, double a, double b) { return sm((t - a) / (b - a)); }
    /** Heavy ease: starts slowly, lands with a small overshoot (about 3% of the move) and settles back. */
    static double heavy(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * x * (x * (6 * x - 15) + 10) + .16 * x * Math.sin(Math.PI * x);
    }
    static double heavy(double t, double a, double b) { return heavy((t - a) / (b - a)); }
    static double smoother(double t, double a, double b) { double x = Mth.clamp((t - a) / (b - a), 0, 1); return x * x * x * (x * (6 * x - 15) + 10); }
    private record Key(double t, Joints pose) {}
    private static Joints track(double t, Key... keys) { return track(t, true, keys); }
    /** {@code heavy} false: every move eases in and out with no overshoot. */
    private static Joints track(double t, boolean heavy, Key... keys) {
        if (t <= keys[0].t) return keys[0].pose;
        for (int i = 1; i < keys.length; i++)
            if (t < keys[i].t) return keys[i - 1].pose.mix(keys[i].pose, heavy ? heavy(t, keys[i - 1].t, keys[i].t) : smoother(t, keys[i - 1].t, keys[i].t));
        return keys[keys.length - 1].pose;
    }

    /** Cast ticks (BUILD = 480) to the pose timeline authored for 300 (script v3): the wand floats before the eyes until 62 and beside the caster until 80,
     *  spins round the hand 95..120, the finger goes to the lips by 132 (shh), and the arm rises to the sky 385..455, slowly (the caster is never in a hurry: owner, 2026-11-11). */
    private static final double[] NEW_T = {0, 290, 330, 340, 400, 455}, OLD_T = {0, 12, 46, 70, 250, 296};
    static double poseClock(double t) {
        for (int i = 1; i < NEW_T.length; i++) if (t < NEW_T[i]) return OLD_T[i - 1] + (t - NEW_T[i - 1]) * (OLD_T[i] - OLD_T[i - 1]) / (NEW_T[i] - NEW_T[i - 1]);
        return OLD_T[OLD_T.length - 1] + (t - NEW_T[NEW_T.length - 1]);
    }
    /** Inverse of {@link #poseClock}: the cast tick at which the 300-tick timeline reaches {@code old}. */
    static double fromOld(double old) {
        for (int i = 1; i < OLD_T.length; i++) if (old < OLD_T[i]) return NEW_T[i - 1] + (old - OLD_T[i - 1]) * (NEW_T[i] - NEW_T[i - 1]) / (OLD_T[i] - OLD_T[i - 1]);
        return NEW_T[NEW_T.length - 1] + (old - OLD_T[OLD_T.length - 1]);
    }

    /** First click to the command: hover, spin, shh, release, point at the sky. Right arm from {@code r}, left arm and head from {@code l}. */
    private static Joints build(double age) {
        // Script v7: the right hand is raised to the wand afloat before the caster (cast ticks 0..25) and held there while the gate comes down; back on the caster
        // the finger goes to the lips (256..286), then the wand is taken and spun (290..330) and brought to the hip.
        Joints r = track(age, false, new Key(0, REST), new Key(1, HOVER), new Key(12, HOVER), new Key(30, FACE), new Key(46, FACE), new Key(68, HIP),
                new Key(258, HIP), new Key(296, POINT));
        // The finger goes to the lips first (cast tick 20..60); the wand is taken and spun only after the camera has drawn back (owner, 2026-11-12).
        Joints l = track(age, false, new Key(0, REST), new Key(10.59, REST), new Key(11.83, LIPS),
                new Key(250, LIPS), new Key(276, REST));
        float head = (float) (.20 * smoother(age, 10.59, 11.83) * (1 - sm(age, 250, 276)) - .14 * smoother(age, 262, 296));
        return new Joints(r.rp, r.ry, r.rr, l.lp, l.ly, l.lr, head);
    }
    /**
     * Joints at the given cast clock. {@code age}: ticks since the first click, {@code command}: ticks since the second (-1 before it),
     * {@code endStart}: command age at which the ending begins.
     */
    public static Joints joints(double age, double command, double endStart) {
        age = poseClock(age);
        Joints j;
        if (command < 0) j = build(age);
        else {
            Joints r = track(command, new Key(0, POINT), new Key(8, THROW), new Key(18, AIM), new Key(Math.max(12, endStart), AIM),
                    new Key(Math.max(12, endStart) + 28, REST));
            j = new Joints(r.rp, r.ry, r.rr, REST.lp, REST.ly, REST.lr, (float) (.05 * (1 - sm(command, endStart, endStart + 28))));
        }
        // Breathing: a slow sway on both shoulders so the held poses never freeze.
        float breath = (float) Math.sin(age * .09) * .012f;
        return new Joints(j.rp + breath, j.ry, j.rr + breath * .6f, j.lp + breath, j.ly, j.lr - breath * .6f, j.head + breath * .5f);
    }
    static double endStart(Frame f) { return net.godwand.skill.SilentGateSequence.ACTIVATE_TICKS + f.barrageTicks(); }
    public static Joints joints(Frame f) { return joints(f.age(), f.commandAge(), endStart(f)); }

    /** Applies the pose to a humanoid model; false when this entity is not casting (nothing was touched). */
    public static boolean apply(int casterId, float partial, ModelPart rightArm, ModelPart leftArm, ModelPart body, ModelPart head) {
        Frame f = SilentGateRenderer.frame(casterId, partial);
        if (f == null || f.age() < 0) return false;
        Joints j = joints(f);
        rightArm.xRot = j.rp; rightArm.yRot = j.ry; rightArm.zRot = j.rr;
        leftArm.xRot = j.lp; leftArm.yRot = j.ly; leftArm.zRot = j.lr;
        head.xRot += j.head;
        body.yRot = 0;
        return true;
    }

    // ---- geometry: where the hands and the wand are --------------------------------------------------------------
    /** A hand: world position of the palm and the unit direction shoulder -> palm. */
    public record Hand(Vec3 pos, Vec3 axis) {}

    /** Palm of an arm in body space (right, up, forward), arm cube centre line, {@code len} model pixels from the shoulder. */
    static Hand handLocal(float pitch, float yaw, float roll, boolean right, double len) {
        var q = new org.joml.Quaternionf().rotationZYX(roll, yaw, pitch);
        var p = q.transform(new org.joml.Vector3f(right ? -1 : 1, (float) len, 0));
        var d = q.transform(new org.joml.Vector3f(0, 1, 0));
        return new Hand(new Vec3(-(p.x + (right ? -5 : 5)) / 16.0, 1.5 - (p.y + 2) / 16.0, -p.z / 16.0), new Vec3(-d.x, -d.y, -d.z));
    }
    static Vec3 toWorld(Vec3 feet, double bodyYawDeg, Vec3 local) {
        Vec3 f = Vec3.directionFromRotation(0, (float) bodyYawDeg), r = f.cross(UP);
        return feet.add(r.scale(local.x)).add(0, local.y, 0).add(f.scale(local.z));
    }
    static Vec3 dirToWorld(double bodyYawDeg, Vec3 local) {
        Vec3 f = Vec3.directionFromRotation(0, (float) bodyYawDeg), r = f.cross(UP);
        return r.scale(local.x).add(0, local.y, 0).add(f.scale(local.z));
    }
    static final double PALM = 10;

    public static Hand rightHand(Joints j, Vec3 feet, double bodyYawDeg) {
        Hand h = handLocal(j.rp, j.ry, j.rr, true, PALM);
        return new Hand(toWorld(feet, bodyYawDeg, h.pos), dirToWorld(bodyYawDeg, h.axis));
    }
    public static Hand leftHand(Joints j, Vec3 feet, double bodyYawDeg) {
        Hand h = handLocal(j.lp, j.ly, j.lr, false, PALM);
        return new Hand(toWorld(feet, bodyYawDeg, h.pos), dirToWorld(bodyYawDeg, h.axis));
    }

    /** Wand placement: centre of the grip, unit axis toward the head end, and the unit vector the flat side of the head faces. */
    public record Wand(Vec3 pos, Vec3 axis, Vec3 face) {
        public Vec3 tip() { return pos.add(axis.scale(TIP)); }
        public Vec3 pommel() { return pos.subtract(axis.scale(POMMEL)); }
    }
    static final double SPIN_FROM = 25, SPIN_TO = 46;
    /** Roll angle of the spin around the forearm axis: a small wind-up the other way, then one full turn that ends on the start angle. */
    public static double spinAngle(double age) {
        return -.40 * sm(age, 20, 25) + (2 * Math.PI + .40) * heavy(age, SPIN_FROM, SPIN_TO);
    }
    static Vec3 slerp(Vec3 a, Vec3 b, double t) {
        Vec3 v = a.scale(1 - t).add(b.scale(t));
        return v.lengthSqr() < 1e-6 ? a : v.normalize();
    }
    /** Unit vector toward the camera-facing side: the body's forward projected off the wand axis, rolled around it. */
    static Vec3 faceFor(Vec3 axis, Vec3 forward, Vec3 right, double roll) {
        Vec3 f = forward.subtract(axis.scale(forward.dot(axis)));
        if (f.lengthSqr() < 1e-4) f = right.subtract(axis.scale(right.dot(axis)));
        f = f.normalize();
        Vec3 g = axis.cross(f);
        return f.scale(Math.cos(roll)).add(g.scale(Math.sin(roll)));
    }

    /** Where the wand is at this cast clock, given the caster's feet and body yaw (degrees). */
    public static Wand wand(double age, double command, double endStart, Vec3 feet, double bodyYawDeg) {
        Joints j = joints(age, command, endStart);
        double raw = age;
        age = poseClock(age);
        Hand hand = rightHand(j, feet, bodyYawDeg);
        Vec3 f = Vec3.directionFromRotation(0, (float) bodyYawDeg), r = f.cross(UP);
        Vec3 chest = feet.add(f.scale(1.05)).add(r.scale(.05)).add(0, 1.30 + .045 * Math.sin(age * .12), 0);
        // First person (until 62): the wand floats 1.5 blocks before the eyes, face toward the caster; it then drifts to the chest hover while turning its face round.
        double leave = smoother(raw, 286, 302);
        Vec3 front = feet.add(f.scale(1.5)).add(0, 1.50 + .03 * Math.sin(raw * .05), 0);
        chest = front.lerp(chest, leave);
        Vec3 side = feet.add(r.scale(.85)).add(f.scale(.12)).add(0, 1.15 + .05 * Math.sin(age * .1), 0);
        Vec3 hoverAxis = new Vec3(Math.sin(age * .05) * .08, 1, Math.cos(age * .07) * .06).normalize();
        double roll = age * .05;
        Vec3 pos, axis, face;
        if (command >= 0) {
            // Floats beside the caster through the barrage; at the ending it drifts back to the lowered hand.
            double back = sm(command, endStart + 6, endStart + 40);
            Vec3 held = hand.pos.add(0, .02, 0);
            pos = side.lerp(held, back);
            axis = slerp(hoverAxis, f.scale(.85).add(0, -.5, 0).normalize(), back);
            return new Wand(pos, axis, faceFor(axis, f, r, roll * (1 - back)));
        }
        Vec3 a = hand.axis;
        Vec3 lateral = r.subtract(a.scale(r.dot(a))).normalize();
        Vec3 normal = a.cross(lateral).normalize();
        double theta = spinAngle(age);
        Vec3 spinAxis = lateral.scale(Math.cos(theta)).add(normal.scale(Math.sin(theta))).normalize();
        Vec3 spinPos = hand.pos.add(a.scale(.10));
        Vec3 gripAxis = r.scale(.99).add(0, .14, 0).normalize();
        double take = smoother(age, 12, 30), catchK = smoother(age, 46, 56);
        pos = chest.lerp(spinPos, take).lerp(hand.pos, catchK);
        axis = slerp(slerp(hoverAxis, spinAxis, take), gripAxis, catchK);
        double flat = sm(age, 16, 26);
        face = faceFor(axis, a.scale(flat).add(f.scale(1 - flat)), r, roll * (1 - flat) + Math.PI * (1 - leave) * (1 - flat));
        // Release into the float beside the caster while the left hand lowers.
        double rel = smoother(age, 248, 272);
        if (rel > 0) {
            pos = pos.lerp(side, rel);
            axis = slerp(axis, hoverAxis, rel);
            face = faceFor(axis, f, r, roll * rel);
        }
        return new Wand(pos, axis, face);
    }
    public static Wand wand(Frame f, Vec3 feet, double bodyYawDeg) { return wand(f.age(), f.commandAge(), endStart(f), feet, bodyYawDeg); }

    /** Mana strength of the cast: strong through the build, calm in the wait, present in the barrage, gone with the ending. */
    public static double manaGain(Frame f) {
        double a = f.age(), g = sm(a, 0, 22) * (1 - .45 * sm(a, 360, 420));
        if (f.commandAge() >= 0) {
            double end = endStart(f);
            g = Math.max(g, .7 * sm(f.commandAge(), 0, 8)) * (1 - sm(f.commandAge(), end, end + net.godwand.skill.SilentGateSequence.ENDING_TICKS - 5));
        }
        return Math.max(0, g);
    }
}
