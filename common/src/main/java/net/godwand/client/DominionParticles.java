package net.godwand.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Custom pooled 3D GPU particle engine for Block Dominion.
 * Rendered in a single batched draw call through {@link DominionShader#FX_SPARK_PARTICLE}
 * (celestial 4-point star sparks, molten golden embers, and crystalline diamond shards).
 */
public final class DominionParticles {
    public static final int STYLE_STAR = 0;
    public static final int STYLE_EMBER = 1;
    public static final int STYLE_SHARD = 2;

    private static final int MAX_PARTICLES = 512;

    private static final class Particle {
        boolean alive;
        double x, y, z;
        double prevX, prevY, prevZ;
        double vx, vy, vz;
        double drag;
        double gravity;
        float size;
        int age;
        int maxAge;
        int r, g, b;
        int seed;
    }

    private static final Particle[] POOL = new Particle[MAX_PARTICLES];
    private static int cursor = 0;
    private static final Random RNG = new Random(17031L);

    static {
        for (int i = 0; i < MAX_PARTICLES; i++) POOL[i] = new Particle();
    }

    private DominionParticles() {}

    public static void clear() {
        for (Particle p : POOL) p.alive = false;
        cursor = 0;
    }

    public static void spawn(Vec3 pos, Vec3 vel, float size, int maxAge, double drag, double gravity,
                             int r, int g, int b, int style) {
        Particle p = POOL[cursor];
        cursor = (cursor + 1) % MAX_PARTICLES;
        p.alive = true;
        p.x = p.prevX = pos.x;
        p.y = p.prevY = pos.y;
        p.z = p.prevZ = pos.z;
        p.vx = vel.x;
        p.vy = vel.y;
        p.vz = vel.z;
        p.size = size;
        p.age = 0;
        p.maxAge = Math.max(2, maxAge);
        p.drag = drag;
        p.gravity = gravity;
        p.r = r;
        p.g = g;
        p.b = b;
        // seed mod 3 selects shader particle variant (0=star, 1=ember, 2=shard)
        p.seed = (RNG.nextInt(30) * 3) + (style % 3);
    }

    /** Radial burst of golden crystal dust + sparks (used for air-step pads, parry impacts, wing-flap takeoff). */
    public static void spawnRingBurst(Vec3 center, Vec3 normal, double radius, int count,
                                      double radialSpeed, int r, int g, int b, int style) {
        Vec3 n = normal.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : normal.normalize();
        Vec3 side = n.cross(Math.abs(n.y) < 0.94 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 up = n.cross(side).normalize();
        for (int i = 0; i < count; i++) {
            double ang = (Math.PI * 2.0 * i / count) + (RNG.nextDouble() - 0.5) * 0.35;
            Vec3 dir = side.scale(Math.cos(ang)).add(up.scale(Math.sin(ang)));
            Vec3 pos = center.add(dir.scale(radius * (0.65 + RNG.nextDouble() * 0.45)));
            Vec3 vel = dir.scale(radialSpeed * (0.7 + RNG.nextDouble() * 0.6))
                    .add(n.scale((RNG.nextDouble() - 0.3) * radialSpeed * 0.45));
            spawn(pos, vel, 0.14f + RNG.nextFloat() * 0.16f, 10 + RNG.nextInt(10),
                    0.86, 0.002, r, g, b, (i & 1) == 0 ? style : STYLE_STAR);
        }
    }

    /** Ambient rising ember or sparkle around orbiting blocks / gravity fields / wings. */
    public static void spawnAmbient(Vec3 origin, double spread, double upSpeed, int r, int g, int b, int style) {
        Vec3 pos = origin.add(
                (RNG.nextDouble() - 0.5) * spread * 2.0,
                (RNG.nextDouble() - 0.5) * spread * 1.4,
                (RNG.nextDouble() - 0.5) * spread * 2.0
        );
        Vec3 vel = new Vec3(
                (RNG.nextDouble() - 0.5) * 0.03,
                upSpeed * (0.6 + RNG.nextDouble() * 0.7),
                (RNG.nextDouble() - 0.5) * 0.03
        );
        spawn(pos, vel, 0.11f + RNG.nextFloat() * 0.13f, 14 + RNG.nextInt(14), 0.94, 0.0, r, g, b, style);
    }

    public static void tick() {
        for (Particle p : POOL) {
            if (!p.alive) continue;
            p.prevX = p.x;
            p.prevY = p.y;
            p.prevZ = p.z;
            p.x += p.vx;
            p.y += p.vy;
            p.z += p.vz;
            p.vx *= p.drag;
            p.vy = p.vy * p.drag - p.gravity;
            p.vz *= p.drag;
            p.age++;
            if (p.age >= p.maxAge) p.alive = false;
        }
    }

    /** Emits all live particles as camera-facing quads into an active {@link DominionShader} batch. */
    public static boolean emitAll(VertexConsumer v, Matrix4f m, Camera camera, float tickDelta) {
        Vec3 camPos = camera.getPosition();
        Vector3f left = camera.getLeftVector();
        Vector3f upVec = camera.getUpVector();
        Vec3 right = new Vec3(-left.x, -left.y, -left.z);
        Vec3 up = new Vec3(upVec.x, upVec.y, upVec.z);

        boolean any = false;
        for (Particle p : POOL) {
            if (!p.alive) continue;
            float life = (p.age + tickDelta) / (float) p.maxAge;
            if (life >= 1.0f) continue;
            float fade = life < 0.15f ? (life / 0.15f) : (1.0f - (life - 0.15f) / 0.85f);
            int alpha = Mth.clamp((int) (fade * fade * 235f), 0, 255);
            if (alpha <= 3) continue;

            double rx = Mth.lerp(tickDelta, p.prevX, p.x) - camPos.x;
            double ry = Mth.lerp(tickDelta, p.prevY, p.y) - camPos.y;
            double rz = Mth.lerp(tickDelta, p.prevZ, p.z) - camPos.z;
            if (rx * rx + ry * ry + rz * rz > 96.0 * 96.0) continue;

            double sz = p.size * (0.65 + 0.55 * (1.0 - life));
            DominionShader.emitQuad(v, m, new Vec3(rx, ry, rz), right, up, sz, sz,
                    DominionShader.FX_SPARK_PARTICLE, p.seed, p.r, p.g, p.b, alpha);
            any = true;
        }
        return any;
    }
}
