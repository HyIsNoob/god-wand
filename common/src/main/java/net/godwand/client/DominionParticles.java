package net.godwand.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Two-pass custom pooled 3D GPU particle engine for Block Dominion (Purple volumetric standard):
 * - BODY pass (alpha blend): billowing dark-gold/obsidian smoke puffs & jagged dark stone shards
 * - GLOW pass (additive): luminous 4-point celestial star sparks & molten golden embers.
 */
public final class DominionParticles {
    public static final int STYLE_STAR = 0;
    public static final int STYLE_EMBER = 1;
    public static final int STYLE_SMOKE = 2;
    public static final int STYLE_SHARD = 3;

    private static final int MAX_PARTICLES = 768;

    private static final class Particle {
        boolean alive;
        boolean bodyPass;
        double x, y, z;
        double prevX, prevY, prevZ;
        double vx, vy, vz;
        double drag;
        double gravity;
        float size;
        float growRate;
        float rot;
        float rotSpeed;
        int age;
        int maxAge;
        int r, g, b;
        int alpha;
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
        boolean body = (style == STYLE_SMOKE || style == STYLE_SHARD);
        spawnEx(pos, vel, size, body && style == STYLE_SMOKE ? 0.9f : 0.0f,
                maxAge, drag, gravity, r, g, b, body ? 165 : 230, style, body);
    }

    public static void spawnEx(Vec3 pos, Vec3 vel, float size, float growRate, int maxAge, double drag, double gravity,
                               int r, int g, int b, int alpha, int style, boolean bodyPass) {
        Particle p = POOL[cursor];
        cursor = (cursor + 1) % MAX_PARTICLES;
        p.alive = true;
        p.bodyPass = bodyPass;
        p.x = p.prevX = pos.x;
        p.y = p.prevY = pos.y;
        p.z = p.prevZ = pos.z;
        p.vx = vel.x;
        p.vy = vel.y;
        p.vz = vel.z;
        p.size = size;
        p.growRate = growRate;
        p.rot = RNG.nextFloat() * 6.283f;
        p.rotSpeed = (RNG.nextFloat() - 0.5f) * 0.14f;
        p.age = 0;
        p.maxAge = Math.max(3, maxAge);
        p.drag = drag;
        p.gravity = gravity;
        p.r = r;
        p.g = g;
        p.b = b;
        p.alpha = alpha;
        // seed mod 4 selects shader particle variant (0 star, 1 ember, 2 smoke wisp, 3 stone shard)
        p.seed = (RNG.nextInt(32) * 4) + (style & 3);
    }

    /** Spawns billowing dark-obsidian / warm-gold volumetric smoke puffs (Purple body-pass style). */
    public static void spawnSmoke(Vec3 pos, Vec3 vel, float size, int maxAge, int r, int g, int b, int alpha) {
        spawnEx(pos, vel, size, 1.15f, maxAge, 0.92, -0.002, r, g, b, alpha, STYLE_SMOKE, true);
    }

    /** Radial burst of smoke puffs + sparks/shards (air-step pads, parry, wing takeoff, volley release). */
    public static void spawnRingBurst(Vec3 center, Vec3 normal, double radius, int count,
                                      double radialSpeed, int r, int g, int b, int style) {
        Vec3[] uw = DominionShader.basis(normal);
        Vec3 n = normal.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : normal.normalize();
        for (int i = 0; i < count; i++) {
            double ang = (Math.PI * 2.0 * i / count) + (RNG.nextDouble() - 0.5) * 0.4;
            Vec3 dir = uw[0].scale(Math.cos(ang)).add(uw[1].scale(Math.sin(ang)));
            Vec3 pos = center.add(dir.scale(radius * (0.55 + RNG.nextDouble() * 0.5)));
            Vec3 vel = dir.scale(radialSpeed * (0.65 + RNG.nextDouble() * 0.65))
                    .add(n.scale((RNG.nextDouble() - 0.25) * radialSpeed * 0.45));
            if (i % 3 == 0) {
                // Soft dark-amber/obsidian smoke puff in BODY pass
                boolean ink = (i % 6 == 0);
                spawnSmoke(pos, vel.scale(0.6), 0.36f + RNG.nextFloat() * 0.28f, 14 + RNG.nextInt(10),
                        ink ? 28 : 148, ink ? 22 : 108, ink ? 20 : 38, ink ? 135 : 120);
            }
            spawn(pos, vel, 0.14f + RNG.nextFloat() * 0.16f, 11 + RNG.nextInt(10),
                    0.87, 0.002, r, g, b, (i & 1) == 0 ? style : STYLE_STAR);
        }
    }

    /** Ambient rising ember, sparkle, or drifting dark-gold smoke wisp. */
    public static void spawnAmbient(Vec3 origin, double spread, double upSpeed, int r, int g, int b, int style) {
        Vec3 pos = origin.add(
                (RNG.nextDouble() - 0.5) * spread * 2.0,
                (RNG.nextDouble() - 0.5) * spread * 1.4,
                (RNG.nextDouble() - 0.5) * spread * 2.0
        );
        Vec3 vel = new Vec3(
                (RNG.nextDouble() - 0.5) * 0.035,
                upSpeed * (0.6 + RNG.nextDouble() * 0.7),
                (RNG.nextDouble() - 0.5) * 0.035
        );
        if (style == STYLE_SMOKE) {
            spawnSmoke(pos, vel, 0.32f + RNG.nextFloat() * 0.25f, 18 + RNG.nextInt(12), r, g, b, 115);
        } else {
            spawn(pos, vel, 0.11f + RNG.nextFloat() * 0.14f, 14 + RNG.nextInt(14), 0.94, 0.0, r, g, b, style);
        }
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
            p.rot += p.rotSpeed;
            p.age++;
            if (p.age >= p.maxAge) p.alive = false;
        }
    }

    /** Emits live particles for either BODY pass ({@code bodyPass=true}) or GLOW pass ({@code bodyPass=false}). */
    public static boolean emitPass(VertexConsumer v, Matrix4f m, Camera camera, float tickDelta, boolean bodyPass) {
        Vec3 camPos = camera.getPosition();
        Vector3f left = camera.getLeftVector();
        Vector3f upVec = camera.getUpVector();
        Vec3 right = new Vec3(-left.x, -left.y, -left.z);
        Vec3 up = new Vec3(upVec.x, upVec.y, upVec.z);

        boolean any = false;
        for (Particle p : POOL) {
            if (!p.alive || p.bodyPass != bodyPass) continue;
            float life = (p.age + tickDelta) / (float) p.maxAge;
            if (life >= 1.0f) continue;
            float fadeIn = Mth.clamp(life / 0.16f, 0f, 1f);
            float fadeOut = 1.0f - Mth.clamp((life - 0.16f) / 0.84f, 0f, 1f);
            float env = fadeIn * fadeOut * fadeOut;
            int alpha = Mth.clamp((int) (env * p.alpha), 0, 255);
            if (alpha <= 3) continue;

            double rx = Mth.lerp(tickDelta, p.prevX, p.x) - camPos.x;
            double ry = Mth.lerp(tickDelta, p.prevY, p.y) - camPos.y;
            double rz = Mth.lerp(tickDelta, p.prevZ, p.z) - camPos.z;
            if (rx * rx + ry * ry + rz * rz > 96.0 * 96.0) continue;

            double sz = p.size * (p.growRate > 0f ? (0.72 + p.growRate * life) : (0.65 + 0.55 * (1.0 - life)));
            DominionShader.emitBillboard(v, m, new Vec3(rx, ry, rz), right, up, sz, p.rot,
                    DominionShader.FX_SPARK, p.seed, p.r, p.g, p.b, alpha);
            any = true;
        }
        return any;
    }
}
