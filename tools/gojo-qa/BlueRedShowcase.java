package net.godwand.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.world.phys.Vec3;

/** Development only (QA scene "look"): draws one Blue or Red at a fixed point, so both can be shot from the same camera at the same distance.
 *  Lives in net.godwand.client for package access to the draw code; the probe sets {@link #at}, {@link #what} and {@link #age}. */
public final class BlueRedShowcase {
    /** 0 nothing, 1 Blue, 2 Blue ready, 3 Red (merge size), 4 Red (flight size), 5 Red charging (hand size, tight reach), 6 Red released and
     *  flying across the view from the left (starts at age 20). */
    public static int what;
    public static Vec3 at = Vec3.ZERO, back = new Vec3(-1, .3, 0);
    public static double age;

    private BlueRedShowcase() {}

    public static void render(WorldRenderContext context) {
        if (what == 0) return;
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
        double now = age + partial;
        var camera = context.camera();
        Vec3 eye = camera.getPosition();
        RenderSystem.enableBlend(); RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
        BlueRedAtlas.bind(); BlueRedShader.bind((float) ((now / 20.0) % 3600));
        try {
            for (int layer : new int[]{0, 1}) {
                if (layer == 0) RenderSystem.defaultBlendFunc();
                else RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                GateDraw.Ctx c = new GateDraw.Ctx();
                c.v = buffer; c.m = context.matrixStack().last().pose(); c.eye = eye; c.layer = layer; c.nearMin = .05; c.nearMax = .4;
                switch (what) {
                    case 1 -> BlueRedDraw.blue(c, at, false, now, -1);
                    case 2 -> BlueRedDraw.blue(c, at, true, now + 200, now + 40);
                    case 3 -> BlueRedDraw.redOrb(c, at, 1.4, now, back);
                    case 4 -> BlueRedDraw.redOrb(c, at, .5, now, back);
                    case 5 -> BlueRedDraw.redOrb(c, at, BlueRedVisual.redChargeSize(now, 50), now, new Vec3(0, 1, 0).add(back.scale(-.8)), .45);
                    case 6 -> {
                        double f = now - 20;
                        Vec3 dir = new Vec3(-back.x, 0, -back.z).normalize(), origin = at.subtract(dir.scale(12));
                        if (f >= 0) BlueRedDraw.redFlight(c, origin, dir, origin.add(dir.scale(Math.min(24, f * net.godwand.skill.BlueRedRules.RED_SPEED))), f, 1, 7, true);
                    }
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
