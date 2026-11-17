package net.godwand.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.godwand.skill.BlueRedRules;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Development only (QA scene "look"): one Blue or Red at a time in front of a fixed camera, for videos of their motion. Blue is steered along
 *  a scripted aim path with the server's own inertia (BlueRedRules.follow) and keeps the same position history the client keeps for its smears.
 *  Lives in net.godwand.client for package access to the draw code; the probe calls {@link #step} every tick. */
public final class BlueRedShowcase {
    /** 0 nothing, 1 Blue idle, 2 Blue moving then flicked the other way, 3 Blue ready circling, 4 Red charging, 5 Red released and flying,
     *  6 Red held still (merge size). */
    public static int what;
    public static Vec3 at = Vec3.ZERO, right = new Vec3(1, 0, 0);
    public static double age;
    private static Vec3 pos = Vec3.ZERO, prev = Vec3.ZERO, vel = Vec3.ZERO;
    private static final List<Vec3> trail = new ArrayList<>();
    private static final List<Double> trailAt = new ArrayList<>();

    private BlueRedShowcase() {}

    /** Where the player aims Blue at tick k of subject s. */
    static Vec3 aim(int s, int k) {
        Vec3 up = new Vec3(0, 1, 0);
        if (s == 2) {
            if (k < 20) return at.add(right.scale(-5 + k * .5));
            if (k < 40) return at.add(right.scale(5)).add(up.scale((k - 20) * .15));
            return at.add(right.scale(-5));
        }
        if (s == 3) return at.add(right.scale(3.5 * Math.cos(k * .12))).add(up.scale(2 * Math.sin(k * .12)));
        return at;
    }

    public static void step(int s, int k) {
        what = s; age = k;
        if (s > 3) return;
        if (k == 0) { pos = prev = aim(s, 0); vel = Vec3.ZERO; trail.clear(); trailAt.clear(); return; }
        trail.add(pos); trailAt.add((double) k);
        while (trail.size() > 16) { trail.remove(0); trailAt.remove(0); }
        prev = pos;
        Vec3[] next = BlueRedRules.follow(pos, vel, aim(s, k));
        pos = next[0]; vel = next[1];
    }

    public static void render(WorldRenderContext context) {
        if (what == 0) return;
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
        double now = age + partial;
        Vec3 eye = context.camera().getPosition(), back = right.scale(-1).add(0, .3, 0).normalize();
        RenderSystem.enableBlend(); RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
        BlueRedAtlas.bind(); BlueRedShader.bind((float) ((now / 20.0) % 3600));
        try {
            for (int layer : new int[]{0, 1}) {
                if (layer == 0) RenderSystem.defaultBlendFunc();
                else RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                GateDraw.Ctx c = new GateDraw.Ctx();
                c.v = buffer; c.m = context.matrixStack().last().pose(); c.eye = eye; c.layer = layer; c.nearMin = .05; c.nearMax = .4;
                if (what <= 3) {
                    Vec3 drawn = BlueRedVisual.interp(prev, pos, partial);
                    Vec3[] t = trail.toArray(new Vec3[0]);
                    double[] ago = new double[t.length];
                    for (int i = 0; i < t.length; i++) ago[i] = now - trailAt.get(i) + 1;
                    BlueRedDraw.blue(c, drawn, what == 3, what == 3 ? now + 200 : now + 10, what == 3 ? now + 40 : -1, t, ago);
                } else switch (what) {
                    case 4 -> {
                        // Where the renderer holds the charge: in front of the eye, low and to the right.
                        var l = context.camera().getLookVector();
                        Vec3 view = new Vec3(l.x(), l.y(), l.z()), hand = eye.add(view.scale(1.9)).add(right.scale(.45)).add(0, -.3, 0);
                        BlueRedDraw.redOrb(c, hand, BlueRedVisual.redChargeSize(now, 50), now, view.scale(.3).add(0, 1, 0).add(right.scale(.8)), .45);
                    }
                    case 5 -> {
                        Vec3 dir = new Vec3(right.x, 0, right.z).normalize(), origin = at.subtract(dir.scale(4));
                        BlueRedDraw.redFlight(c, origin, dir, origin.add(dir.scale(Math.min(24, now * BlueRedRules.RED_SPEED))), now, 1, 7, true);
                    }
                    case 6 -> BlueRedDraw.redOrb(c, at, 1.2, now, back, 1);
                    default -> {}
                }
                var mesh = buffer.build();
                if (mesh != null) BufferUploader.drawWithShader(mesh);
            }
        } finally {
            RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
        }
    }
}
