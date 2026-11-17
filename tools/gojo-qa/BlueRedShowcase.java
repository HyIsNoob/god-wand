package net.godwand.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.world.phys.Vec3;

/** Development only (QA scene "look"): draws one Blue or Red at a fixed point, so both can be shot from the same camera at the same distance.
 *  Lives in net.godwand.client for package access to the draw code; the probe sets {@link #at}, {@link #what} and {@link #age}. */
public final class BlueRedShowcase {
    /** 0 nothing, 1 Blue, 2 Blue ready, 3 Red (merge size), 4 Red (flight size). */
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
