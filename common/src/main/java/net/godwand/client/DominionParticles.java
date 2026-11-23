package net.godwand.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Two-pass atmospheric GPU micro-particle engine for Block Dominion (zero giant PNG star stickers!):
 * - BODY pass (alpha blend): gossamer low-opacity dust puffs & dark 3D-lit tumbling rock splinters
 * - GLOW pass (additive): pinpoint luminous dust motes & velocity-aligned wind/spark needles.
 */
public final class DominionParticles {
    public static final int STYLE_STAR = 0;    // Pinpoint luminous dust mote (never a giant 4-point sticker!)
    public static final int STYLE_EMBER = 1;   // Velocity-aligned aerodynamic spark/wind needle
    public static final int STYLE_SMOKE = 2;   // Gossamer volumetric dust & vapor puff
    public static final int STYLE_SHARD = 3;   // Dark tumbling 3D-lit stone splinter

    private static final int MAX_PARTICLES = 768;

    private static final class Particle {
        boolean alive;
        boolean bodyPass;
        boolean velocityAligned;
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
        boolean aligned = (style == STYLE_EMBER);
        // Keep motes & chips finely scaled so they read as cinematic micro-debris, never chunky sprites
        float fineSize = style == STYLE_STAR ? size * 0.45f
                : style == STYLE_SHARD ? size * 0.62f
                : size;
        int baseAlpha = style == STYLE_SMOKE ? 82 : (style == STYLE_SHARD ? 175 : 185);
        spawnEx(pos, vel, fineSize, style == STYLE_SMOKE ? 1.05f : 0.0f,
                maxAge, drag, gravity, r, g, b, baseAlpha, style, body, aligned);
    }

    public static void spawnEx(Vec3 pos, Vec3 vel, float size, float growRate, int maxAge, double drag, double gravity,
                               int r, int g, int b, int alpha, int style, boolean bodyPass, boolean velocityAligned) {
        Particle p = POOL[cursor];
        cursor = (cursor + 1) % MAX_PARTICLES;
        p.alive = true;
        p.bodyPass = bodyPass;
        p.velocityAligned = velocityAligned;
        p.x = p.prevX = pos.x;
        p.y = p.prevY = pos.y;
        p.z = p.prevZ = pos.z;
        p.vx = vel.x;
        p.vy = vel.y;
        p.vz = vel.z;
        p.size = size;
        p.growRate = growRate;
        p.rot = RNG.nextFloat() * 6.283f;
        p.rotSpeed = (RNG.nextFloat() - 0.5f) * 0.16f;
        p.age = 0;
        p.maxAge = Math.max(3, maxAge);
        p.drag = drag;
        p.gravity = gravity;
        p.r = r;
        p.g = g;
        p.b = b;
        p.alpha = alpha;
        p.seed = (RNG.nextInt(32) * 4) + (style & 3);
    }

    /** Spawns gossamer, low-opacity volumetric dust/mist puffs in world space (BODY pass). */
    public static void spawnSmoke(Vec3 pos, Vec3 vel, float size, int maxAge, int r, int g, int b, int alpha) {
        int softAlpha = Math.min(alpha, 92);
        spawnEx(pos, vel, size, 1.25f, maxAge, 0.91, -0.002, r, g, b, softAlpha, STYLE_SMOKE, true, false);
    }

    /** Spawns a razor-thin velocity-aligned wind/spark needle (GLOW pass). */
    public static void spawnStreak(Vec3 pos, Vec3 vel, float halfLen, int maxAge, int r, int g, int b, int alpha) {
        spawnEx(pos, vel, halfLen, 0.0f, maxAge, 0.92, 0.0, r, g, b, alpha, STYLE_EMBER, false, true);
    }

    /** Radial burst of soft dust + tumbling rock splinters + fine velocity needles. */
    public static void spawnRingBurst(Vec3 center, Vec3 normal, double radius, int count,
                                      double radialSpeed, int r, int g, int b, int style) {
        Vec3[] uw = DominionShader.basis(normal);
        Vec3 n = normal.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : normal.normalize();
        for (int i = 0; i < count; i++) {
            double ang = (Math.PI * 2.0 * i / count) + (RNG.nextDouble() - 0.5) * 0.45;
            Vec3 dir = uw[0].scale(Math.cos(ang)).add(uw[1].scale(Math.sin(ang)));
            Vec3 pos = center.add(dir.scale(radius * (0.45 + RNG.nextDouble() * 0.55)));
            Vec3 vel = dir.scale(radialSpeed * (0.65 + RNG.nextDouble() * 0.75))
                    .add(n.scale((RNG.nextDouble() - 0.25) * radialSpeed * 0.45));
            if (i % 3 == 0) {
                spawnSmoke(pos, vel.scale(0.5), 0.34f + RNG.nextFloat() * 0.24f, 15 + RNG.nextInt(10),
                        (r + 48) / 2, (g + 44) / 2, (b + 48) / 2, 74);
            }
            if (i % 2 == 0) {
                spawnStreak(pos, vel.scale(1.25), 0.24f + RNG.nextFloat() * 0.20f, 8 + RNG.nextInt(6), r, g, b, 175);
            } else {
                spawn(pos, vel, 0.11f + RNG.nextFloat() * 0.09f, 11 + RNG.nextInt(9),
                        0.88, 0.008, r, g, b, style == STYLE_SHARD ? STYLE_SHARD : STYLE_STAR);
            }
        }
    }

    /** Ambient rising dust mote or gentle atmospheric mist wisp. */
    public static void spawnAmbient(Vec3 origin, double spread, double upSpeed, int r, int g, int b, int style) {
        Vec3 pos = origin.add(
                (RNG.nextDouble() - 0.5) * spread * 2.0,
                (RNG.nextDouble() - 0.5) * spread * 1.4,
                (RNG.nextDouble() - 0.5) * spread * 2.0
        );
        Vec3 vel = new Vec3(
                (RNG.nextDouble() - 0.5) * 0.032,
                upSpeed * (0.6 + RNG.nextDouble() * 0.7),
                (RNG.nextDouble() - 0.5) * 0.032
        );
        if (style == STYLE_SMOKE) {
            spawnSmoke(pos, vel, 0.30f + RNG.nextFloat() * 0.22f, 18 + RNG.nextInt(12), r, g, b, 68);
        } else {
            spawn(pos, vel, 0.09f + RNG.nextFloat() * 0.08f, 14 + RNG.nextInt(12), 0.94, 0.0, r, g, b, style);
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

            Vec3 rel = new Vec3(rx, ry, rz);
            if (p.velocityAligned) {
                Vec3 vel = new Vec3(p.vx, p.vy, p.vz);
                double len = p.size * (0.6 + 0.8 * (1.0 - life));
                DominionShader.emitVelocityStreak(v, m, rel, vel, len, 0.042, p.seed >> 2, p.r, p.g, p.b, alpha);
            } else {
                double sz = p.size * (p.growRate > 0f ? (0.72 + p.growRate * life) : (0.65 + 0.45 * (1.0 - life)));
                DominionShader.emitBillboard(v, m, rel, right, up, sz, p.rot,
                        DominionShader.FX_SPARK, p.seed, p.r, p.g, p.b, alpha);
            }
            any = true;
        }
        return any;
    }
}
