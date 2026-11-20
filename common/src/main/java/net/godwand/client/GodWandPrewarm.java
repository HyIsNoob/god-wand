package net.godwand.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * First-cast hitches (owner, 2026-11-20: the first Blue/Red/Purple, Fuga or Atomic of a session stalled): everything a skill would build
 * on its first frame is built just after the world opens instead, one piece a tick so joining does not stall either. Textures are decoded
 * and uploaded, shaders compiled and drawn once (many drivers compile only on the first draw), the post chains run once as a
 * pass-through, and the Blue/Red/Purple drawing code is run once into a sink so its classes are loaded and warm.
 */
public final class GodWandPrewarm {
    private GodWandPrewarm() {}

    private static Object world;
    private static int step = -1;
    private static boolean drawnOnce;

    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null) { world = null; return; }
        if (client.level != world) { world = client.level; step = 0; }
        if (step < 0) return;
        try {
            switch (step++) {
                case 0 -> {
                    for (String path : new String[]{"textures/vfx/blue_red.png", "textures/vfx/impact_frame_0.png", "textures/vfx/impact_frame_1.png",
                            "textures/vfx/impact_frame_2.png", "textures/vfx/impact_frame_3.png"})
                        client.getTextureManager().getTexture(ResourceLocation.fromNamespaceAndPath("godwand", path));
                    GateAtlas.prepare();
                }
                case 1 -> BlueRedShader.prewarm();
                case 2 -> GatePostFx.process(0, 0, .5f, .5f, 0, 0, 0);
                case 3 -> AtomicPostFx.prewarm();
                case 4, 5, 6 -> dryRun(step - 5);
                default -> step = -1;
            }
        } catch (Exception error) {
            // Warming is only ever a nicety: whatever fails here simply happens on the first cast instead.
            net.godwand.GodWandMod.LOGGER.warn("God Wand warm-up step skipped", error);
        }
    }

    /** Swallows vertices: the drawing code runs for real, nothing is drawn. */
    private static final VertexConsumer SINK = new VertexConsumer() {
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    };

    /** Once a session, part {@code part} (0..2) of Blue/Red/Purple's drawing, both passes, at a spread of ages. */
    private static void dryRun(int part) {
        if (drawnOnce && part == 0) return;
        if (part == 2) drawnOnce = true;
        Vec3 eye = new Vec3(0, 100, 0), at = new Vec3(0, 100, 12), look = new Vec3(0, 0, 1);
        for (int layer : new int[]{0, 1}) {
            GateDraw.Ctx c = new GateDraw.Ctx();
            c.v = SINK; c.m = new Matrix4f(); c.eye = eye; c.layer = layer; c.nearMin = .05; c.nearMax = .4;
            switch (part) {
                case 0 -> {
                    BlueRedDraw.blue(c, at, false, 6, -1);
                    BlueRedDraw.blue(c, at, true, 40, 5);
                    BlueRedDraw.redCharge(c, at, .5, 20, .6, false, look);
                    BlueRedDraw.redCharge(c, at, .6, 60, 1, true, look, 50);
                    BlueRedDraw.redFlight(c, eye, look, at, 6, 1, 7, true, 1);
                    BlueRedDraw.burst(c, at, 3, false, look);
                    BlueRedDraw.impact(c, at, 1, eye, look);
                }
                case 1 -> {
                    for (double age : new double[]{8, 50, 100, 150}) BlueRedDraw.purple(c, at, age, eye, look);
                    BlueRedDraw.purpleVoid(c, at, net.godwand.skill.BlueRedRules.PURPLE_SWIRL_END + 40, eye, look);
                }
                default -> {
                    for (double age : new double[]{200, 290, 302, 330, 400}) BlueRedDraw.purple(c, at, age, eye, look);
                }
            }
        }
    }
}
