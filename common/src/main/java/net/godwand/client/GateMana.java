package net.godwand.client;

import net.godwand.client.GateDraw.Ctx;
import net.godwand.client.SilentGateRenderer.Frame;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Mana of Silent Gate: eight glittering silk ribbons that start at the wand and the right hand and trail behind them with inertia
 * (spring chain, damping, curl-noise drift), swirl round the caster and are kept out of a cylinder around the body.
 * The simulation is visual only: it steps once per client tick and the draw interpolates between the last two steps.
 */
final class GateMana {
    private GateMana() {}
    static final int STRANDS = 8, POINTS = 24, GLINTS = 24, FLOW = 2100, BANDS = 26, TRAIL = 44, SHED = 900, QUAD_BUDGET = 4200;
    /** Body cylinder the points stay out of, and ribbon width (root, tail is a point). */
    static final double AVOID = .8, CYL_R = .45, CYL_H = 1.9, WIDTH = .15;
    private static final double DAMP = .78, FOLLOW = .14, CURL = .003, WIND = .08, UNDULATE = .004, MAX_STRETCH = 1.7;
    private static final int SUB = 2, SAMPLES = (POINTS - 1) * SUB + 1;
    static final int ROOT_TIP_END = 4, ROOT_HAND_END = 6;

    /** Per-caster simulation state. */
    static final class Sim {
        final long startedAt;
        final Vec3[] cur = new Vec3[STRANDS * POINTS], last = new Vec3[STRANDS * POINTS];
        Vec3 feet, lastFeet; double yaw, lastYaw; double age, lastAge; boolean ready;
        Sim(long startedAt) { this.startedAt = startedAt; }
        double partial(double frameAge) { return age <= lastAge ? 1 : Math.max(0, Math.min(1, (frameAge - lastAge) / (age - lastAge))); }
        Vec3 feet(double p) { return lastFeet == null ? feet : lastFeet.lerp(feet, p); }
        double yaw(double p) { return lastYaw + net.minecraft.util.Mth.wrapDegrees(yaw - lastYaw) * p; }
    }
    private static final Map<Integer, Sim> SIMS = new HashMap<>();
    static Sim sim(int casterId) { return SIMS.get(casterId); }
    static void forget(java.util.Set<Integer> keep) { SIMS.keySet().retainAll(keep); }

    /** One step per client tick for every visible cast. */
    static void tick(java.util.List<Frame> frames, net.minecraft.client.multiplayer.ClientLevel level) {
        java.util.Set<Integer> keep = new java.util.HashSet<>();
        for (Frame f : frames) {
            keep.add(f.casterId());
            Sim s = SIMS.get(f.casterId());
            if (s == null || s.startedAt != f.startedAt()) SIMS.put(f.casterId(), s = new Sim(f.startedAt()));
            Vec3 feet = f.anchor(); double yaw = f.yaw();
            if (level != null && level.getEntity(f.casterId()) instanceof net.minecraft.world.entity.LivingEntity e) {
                feet = e.position(); yaw = e.yBodyRot;
            }
            step(s, f, feet, yaw);
        }
        forget(keep);
    }

    /** Advances the state to this frame's clock (also used by the tests with synthetic frames). */
    static void step(Sim s, Frame f, Vec3 feet, double yawDeg) {
        s.lastFeet = s.feet == null ? feet : s.feet; s.lastYaw = s.feet == null ? yawDeg : s.yaw;
        s.feet = feet; s.yaw = yawDeg; s.lastAge = s.ready ? s.age : f.age() - 1; s.age = f.age();
        Vec3[] roots = roots(f.age(), f.commandAge(), GatePose.endStart(f), feet, yawDeg);
        if (!s.ready) {
            for (int k = 0; k < STRANDS; k++) for (int i = 0; i < POINTS; i++) {
                Vec3 d = new Vec3(hash(k, 1) - .5, hash(k, 2) * .6 - .1, hash(k, 3) - .5).normalize();
                s.cur[k * POINTS + i] = s.last[k * POINTS + i] = roots[k].add(d.scale(seg(k) * i));
            }
            s.ready = true;
            for (int i = 0; i < 40; i++) advance(s, roots, feet, yawDeg, f.age(), GatePose.manaGain(f));
        }
        advance(s, roots, feet, yawDeg, f.age(), GatePose.manaGain(f));
    }

    static Vec3[] roots(double age, double command, double endStart, Vec3 feet, double yawDeg) {
        var wand = GatePose.wand(age, command, endStart, feet, yawDeg);
        var hand = GatePose.rightHand(GatePose.joints(age, command, endStart), feet, yawDeg);
        Vec3[] r = new Vec3[STRANDS];
        for (int k = 0; k < STRANDS; k++) r[k] = k < ROOT_TIP_END ? wand.tip() : k < ROOT_HAND_END ? hand.pos() : wand.pommel();
        return r;
    }

    static double hash(int k, int salt) { double h = Math.sin(k * 127.1 + salt * 311.7) * 43758.5453; return h - Math.floor(h); }
    static double seg(int k) { return .16 + .07 * hash(k, 9); }

    /** Divergence-free drift: the curl of a vector potential made of three travelling sines. */
    static Vec3 curl(Vec3 p, double t) {
        double ax = 1.1 * p.y + .7 * p.z + .031 * t, ay = 1.3 * p.z + .9 * p.x + .026 * t + 1.7, az = .9 * p.x + 1.2 * p.y + .029 * t + 3.1;
        return new Vec3(1.2 * Math.cos(az) - 1.3 * Math.cos(ay), .7 * Math.cos(ax) - .9 * Math.cos(az), .9 * Math.cos(ay) - 1.1 * Math.cos(ax));
    }

    private static void advance(Sim s, Vec3[] roots, Vec3 feet, double yawDeg, double time, double gain) {
        double flow = .5 + .5 * gain;
        Vec3 fwd = Vec3.directionFromRotation(0, (float) yawDeg), side = fwd.cross(new Vec3(0, 1, 0));
        for (int k = 0; k < STRANDS; k++) {
            int b = k * POINTS;
            s.last[b] = s.cur[b]; s.cur[b] = roots[k];
            Vec3[] next = new Vec3[POINTS];
            next[0] = roots[k];
            // Steady wind from the front: every strand streams backward, fanned a little to the sides and up.
            Vec3 wind = fwd.scale(-1).add(side.scale((k - (STRANDS - 1) / 2.0) * .09)).add(0, .10 + .12 * hash(k, 4), 0).normalize();
            for (int i = 1; i < POINTS; i++) {
                Vec3 p = s.cur[b + i], v = p.subtract(s.last[b + i]).scale(DAMP);
                double w = i / (double) (POINTS - 1), free = .35 + .65 * w;
                Vec3 gap = p.subtract(next[i - 1]);
                double len = gap.length();
                Vec3 want = len < 1e-6 ? p : next[i - 1].add(gap.scale(seg(k) / len));
                v = v.add(want.subtract(p).scale(FOLLOW));
                v = v.add(wind.scale(WIND * free * flow));
                // Gentle undulation along the length, sideways and up, travelling toward the tail like a wave in silk.
                double wave = Math.sin(time * .09 - i * .45 + k * 1.9);
                v = v.add(side.scale(wave * UNDULATE * w * flow)).add(0, Math.cos(time * .07 - i * .38 + k) * UNDULATE * .7 * w * flow, 0);
                // Soft avoidance of the body: pushed out and slid sideways (alternating per strand) so the stream flows around the shoulders.
                Vec3 off = p.subtract(feet);
                double r = Math.hypot(off.x, off.z), dy = off.y;
                if (r > 1e-4 && r < AVOID && dy > -.2 && dy < CYL_H + .2) {
                    double push = (AVOID - r) * .05, ox = off.x / r, oz = off.z / r, d = k % 2 == 0 ? 1 : -1;
                    v = v.add(ox * push - oz * d * push * .5, 0, oz * push + ox * d * push * .5);
                }
                v = v.add(curl(p.scale(.6).add(k * 3.1, 0, 0), time).scale(CURL * free * flow));
                Vec3 q = p.add(v);
                Vec3 g2 = q.subtract(next[i - 1]);
                double l2 = g2.length(), cap = seg(k) * MAX_STRETCH;
                if (l2 > cap) q = next[i - 1].add(g2.scale(cap / l2));
                next[i] = keepOut(q, feet);
            }
            for (int i = 1; i < POINTS; i++) { s.last[b + i] = s.cur[b + i]; s.cur[b + i] = next[i]; }
        }
    }

    /** Pushes a point out of the body cylinder (the last step of every update, so it always holds). */
    static Vec3 keepOut(Vec3 p, Vec3 feet) {
        double dy = p.y - feet.y;
        if (dy < -.1 || dy > CYL_H + .1) return p;
        double dx = p.x - feet.x, dz = p.z - feet.z, r = Math.hypot(dx, dz);
        if (r >= CYL_R) return p;
        if (r < 1e-6) { dx = 1; dz = 0; r = 1; }
        double k = (CYL_R + 1e-4) / r;
        return new Vec3(feet.x + dx * k, p.y, feet.z + dz * k);
    }

    // ---- drawing -----------------------------------------------------------------------------------------------------
    private static final int C0 = 0x7FE8FF, C1 = 0x5CFFC8, C2 = 0xFFFFFF;
    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t),
                bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }
    /** Ribbon colour at u (0 at the wand, 1 at the tail): blue to jade, white at the tail. */
    static int color(double u) { return u < .6 ? mix(C0, C1, u / .6) : mix(C1, C2, (u - .6) / .4); }

    private static Vec3 catmull(Vec3 a, Vec3 b, Vec3 c, Vec3 d, double t) {
        double t2 = t * t, t3 = t2 * t;
        return b.scale(2).add(c.subtract(a).scale(t)).add(a.scale(2).subtract(b.scale(5)).add(c.scale(4)).subtract(d).scale(t2))
                .add(a.scale(-1).add(b.scale(3)).subtract(c.scale(3)).add(d).scale(t3)).scale(.5);
    }

    /** Points of strand k at partial p, root pinned to {@code root}. */
    static Vec3[] strand(Sim s, int k, double p, Vec3 root) {
        Vec3[] out = new Vec3[POINTS];
        for (int i = 0; i < POINTS; i++) out[i] = s.last[k * POINTS + i].lerp(s.cur[k * POINTS + i], p);
        out[0] = root;
        return out;
    }

    /** Emits the ribbons and glints of one caster; returns the quads added. All of it is light: additive, so the wisps are soft and glow where they cross. */
    static int emit(Ctx c, Sim s, Frame f) {
        if (!s.ready || c.fade <= 0) return 0;
        double gain = GatePose.manaGain(f);
        if (gain <= 0) return 0;
        double p = s.partial(f.age()), time = f.age();
        Vec3 feet = s.feet(p);
        Vec3[] roots = roots(f.age(), f.commandAge(), GatePose.endStart(f), feet, s.yaw(p));
        int before = c.quads;
        double nMin = c.nearMin, nMax = c.nearMax;
        c.nearMin = .8; c.nearMax = 4;
        try {
            // shortcut: the strand simulation above is still stepped (its feet and yaw place the wand) but no longer drawn: the ribbons whipped about when the wand
            // spun (owner, 2026-11-12). The mana is all particles now; delete the strands once nothing reads them.
            if (c.glow()) {
                // Thick while the spell is built, thinned once the barrage has begun (the frame is full of beams then).
                double load = f.commandAge() < 0 ? 1 : .4;
                Vec3 fwd = Vec3.directionFromRotation(0, (float) s.yaw(p));
                flow(c, feet, fwd, gain, time, (int) (FLOW * load));
                bands(c, feet, fwd, gain, time);
                wandTrail(c, f, feet, s.yaw(p), gain, time, load);
            }
        } finally { c.nearMin = nMin; c.nearMax = nMax; }
        return c.quads - before;
    }

    private static double halfWidth(int j, int k, double w0, double time) {
        double u = j / (double) (SAMPLES - 1);
        return .5 * w0 * Math.pow(1 - u, .85) * (1 + .22 * Math.sin(u * 11 + k * 1.7 - time * .12)) * Math.min(1, .75 + u * 12);
    }
    private static void ribbon(Ctx c, Vec3[] cen, int k, double w0, double gain, double time) {
        Vec3[] left = new Vec3[SAMPLES], right = new Vec3[SAMPLES];
        for (int j = 0; j < SAMPLES; j++) {
            Vec3 t = cen[Math.min(SAMPLES - 1, j + 1)].subtract(cen[Math.max(0, j - 1)]);
            Vec3 side = t.cross(cen[j].subtract(c.eye));
            side = (side.lengthSqr() < 1e-10 ? new Vec3(0, 1, 0) : side.normalize()).scale(halfWidth(j, k, w0, time));
            left[j] = cen[j].add(side); right[j] = cen[j].subtract(side);
        }
        for (int j = 0; j < SAMPLES - 1; j++) {
            double u0 = j / (double) (SAMPLES - 1), u1 = (j + 1) / (double) (SAMPLES - 1), um = (u0 + u1) / 2;
            double shimmer = .5 + .5 * Math.sin(j * .7 - time * .35 + k * 1.3);
            int col = mix(color(um), 0xFFFFFF, .22 * shimmer);
            double a0 = alpha(u0, gain), a1 = alpha(u1, gain);
            GateDraw.tex(c, left[j], left[j + 1], right[j + 1], right[j], GateAtlas.SILK, u0, u1, 0, 1, col, a0, a1, a1, a0);
        }
    }
    /** Opacity along the ribbon: strong at the source, fading to nothing at the tail. */
    static double alpha(double u, double gain) { return gain * .7 * Math.pow(1 - u, .9); }

    private static final int DUST_GREEN = 0x8CFFD0, DUST_TEAL = 0x9FE8FF;
    private static int dustColor(int i) { return i % 4 == 0 ? 0xFFFFFF : i % 2 == 0 ? DUST_GREEN : DUST_TEAL; }
    /** The mana is a wind (owner, 2026-11-12): born about the wand before the caster, it streams back past them and away behind, widening and lifting as it goes;
     *  a river of fine specks. Fixed to where the caster stands, so nothing the wand does can whip it. */
    private static void flow(Ctx c, Vec3 feet, Vec3 f, double gain, double time, int count) {
        Vec3 r = f.cross(new Vec3(0, 1, 0));
        for (int i = 0; i < count && GateDraw.room(c); i++) {
            double h = hash(i, 61), life = (hash(i, 62) + time * (.006 + .006 * h)) % 1, back = -1.6 + 12 * life;
            double ring = hash(i, 63) * 6.2831853 + .6 * Math.sin(time * .02 + i), rad = (.25 + 1.5 * hash(i, 64)) * (.5 + 1.3 * life);
            Vec3 at = feet.add(f.scale(-back)).add(r.scale(Math.cos(ring) * rad + .25 * Math.sin(life * 9 + i * 1.7 + time * .05)))
                    .add(0, 1.45 + Math.sin(ring) * rad * .8 + .9 * life * life, 0);
            double alpha = gain * Math.sin(Math.PI * Math.pow(life, .7)) * (.45 + .55 * Math.abs(Math.sin(time * .13 + i)));
            GateDraw.sprite(c, at, .022 + .04 * hash(i, 65), alpha, dustColor(i));
        }
    }
    /** And long bands of it, wide and thin as gauze, ride the same wind. */
    private static void bands(Ctx c, Vec3 feet, Vec3 f, double gain, double time) {
        Vec3 r = f.cross(new Vec3(0, 1, 0));
        for (int i = 0; i < BANDS; i++) {
            double life = (hash(i, 91) + time * (.004 + .003 * hash(i, 92))) % 1, back = -1 + 11 * life, len = 3 + 3.5 * hash(i, 93);
            double side = (hash(i, 94) - .5) * 3.2 * (.5 + life), y = .5 + 2.4 * hash(i, 95) + .8 * life;
            Vec3 a = feet.add(f.scale(-back)).add(r.scale(side)).add(0, y, 0);
            Vec3 b = a.add(f.scale(-len)).add(r.scale(.5 * Math.sin(time * .03 + i))).add(0, .35 * Math.sin(time * .04 + i * 2), 0);
            GateDraw.streamer(c, a, b, .4 + .5 * hash(i, 96), i % 3 == 0 ? 0xFFFFFF : i % 3 == 1 ? DUST_GREEN : DUST_TEAL, .42 * gain * Math.sin(Math.PI * life), (hash(i, 97) - .5) * 2);
        }
    }
    /** The wand writes in the air (owner, 2026-11-12): its head and its pommel each leave a ribbon of light along the path they have just travelled, and shed specks
     *  all the while, thickest at the head, thick at the pommel, thin along the shaft; the specks drift off and are taken by the wind going back. When the wand is spun
     *  the two ribbons are arcs chasing each other round the hand, in a wheel of sparks. Where the wand was is known exactly: its pose is a function of the cast clock. */
    private static void wandTrail(Ctx c, Frame f, Vec3 feet, double yaw, double gain, double time, double load) {
        double dt = .65;
        Vec3[] tip = new Vec3[TRAIL], pom = new Vec3[TRAIL];
        for (int j = 0; j < TRAIL; j++) {
            double back = j * dt;
            var w = GatePose.wand(Math.max(0, f.age() - back), f.commandAge() < 0 ? -1 : Math.max(0, f.commandAge() - back), GatePose.endStart(f), feet, yaw);
            tip[j] = w.tip(); pom[j] = w.pommel();
        }
        // The ribbons: the last 16 samples of each end, bright at the wand and gone at their tails; nothing while the wand hangs still.
        int n = 16;
        for (int end = 0; end < 2; end++) {
            Vec3[] p = end == 0 ? tip : pom;
            double moved = Math.min(1, p[0].distanceTo(p[n - 1]) * 1.5);
            for (int j = 0; j + 1 < n && moved > .02; j++) {
                Vec3 side = p[j + 1].subtract(p[j]).cross(p[j].subtract(c.eye));
                if (side.lengthSqr() < 1e-10) continue;
                side = side.normalize();
                double k0 = 1 - (double) j / n, k1 = 1 - (double) (j + 1) / n, a = gain * moved;
                for (int layer = 0; layer < 2; layer++) {
                    double w = layer == 0 ? .22 : .06, al = layer == 0 ? .4 : .95;
                    int col = layer == 0 ? (end == 0 ? DUST_TEAL : DUST_GREEN) : 0xFFFFFF;
                    GateDraw.gradient(c, p[j].subtract(side.scale(w * k0)), p[j], p[j + 1], p[j + 1].subtract(side.scale(w * k1)), col, 0, al * a * k0, al * a * k1, 0);
                    GateDraw.gradient(c, p[j], p[j].add(side.scale(w * k0)), p[j + 1].add(side.scale(w * k1)), p[j + 1], col, al * a * k0, 0, 0, al * a * k1);
                }
            }
        }
        // The specks: each was shed some ticks ago from where the wand then was, and has drifted since.
        Vec3 fwd = Vec3.directionFromRotation(0, (float) yaw);
        double period = (TRAIL - 1) * dt;
        int count = (int) (SHED * load);
        for (int i = 0; i < count && GateDraw.room(c); i++) {
            double phase = (hash(i, 81) + time / period) % 1, since = phase * period;
            int j = Math.min(TRAIL - 1, (int) (since / dt));
            // 45% from the head, 35% from the pommel, the rest from along the shaft.
            double pick = hash(i, 82), along = pick < .45 ? 0 : pick < .8 ? 1 : hash(i, 83);
            Vec3 from = tip[j].lerp(pom[j], along);
            double a = hash(i, 84) * 6.2831853, b = hash(i, 85) * 3.14159 - 1.5708, out = (.05 + .5 * hash(i, 86)) * Math.sqrt(phase);
            Vec3 at = from.add(Math.cos(a) * Math.cos(b) * out, Math.sin(b) * out + .25 * phase, Math.sin(a) * Math.cos(b) * out).add(fwd.scale(-(.6 * phase + 2.2 * phase * phase)));
            GateDraw.sprite(c, at, (along == 0 ? .03 : .022) + .045 * hash(i, 87) * (1 - .5 * phase), gain * (1 - phase) * (.5 + .5 * Math.abs(Math.sin(time * .2 + i))), dustColor(i),
                    i % 5 == 0 ? GateAtlas.STAR : GateAtlas.GLOW, time * .05 + i);
        }
    }

    private static void glints(Ctx c, Vec3[] cen, int k, double w0, double gain, double time) {
        for (int g = 0; g < GLINTS; g++) {
            double u = (hash(k * 7 + g, 11) * .9 + time * (.0035 + .002 * hash(k, g))) % 1 * .92;
            double pos = u * (SAMPLES - 1);
            int j = Math.min(SAMPLES - 2, (int) pos);
            // Scattered off the strand, wider toward the tail: a cloud of glitter round each wisp.
            double spread = (.04 + .4 * u) * (g % 4 == 0 ? 0 : 1);
            Vec3 at = cen[j].lerp(cen[j + 1], pos - j).add((hash(k * 3 + g, 41) - .5) * spread, (hash(k * 3 + g, 43) - .5) * spread, (hash(k * 3 + g, 47) - .5) * spread);
            double twinkle = Math.max(0, Math.sin(time * (.30 + .15 * hash(k + g, 13)) + hash(k, 20 + g) * 6.28));
            double alpha = gain * Math.pow(twinkle, 1.6) * (1 - .6 * u);
            if (alpha < .02) continue;
            GateDraw.sprite(c, at, (g % 4 == 0 ? .09 : .035) + .07 * hash(k * 5 + g, 17), alpha, mix(0xCFF8FF, 0xFFFFFF, hash(k, g + 30)), GateAtlas.STAR, time * .03 + g);
        }
    }
}
