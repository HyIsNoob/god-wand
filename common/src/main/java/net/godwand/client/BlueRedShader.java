package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.godwand.GodWandMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * The Blue/Red/Purple core shader (assets/minecraft/shaders/core/godwand_bluered.*): animated procedural effects per pixel (BlueRedDraw.fx*) plus the
 * VFX atlas without vanilla's alpha cut-off. Built straight from the resource manager, so no loader registration is needed; if it cannot be built the
 * vanilla shader stands in and the procedural quads simply look wrong rather than crash.
 */
final class BlueRedShader {
    private BlueRedShader() {}
    private static ShaderInstance shader;
    private static boolean failed;

    static void bind(float seconds) {
        if (shader == null && !failed) {
            try {
                shader = new ShaderInstance(Minecraft.getInstance().getResourceManager(), "godwand_bluered", DefaultVertexFormat.POSITION_TEX_COLOR);
            } catch (Exception error) {
                failed = true;
                GodWandMod.LOGGER.warn("God Wand: Blue/Red VFX shader unavailable, using the vanilla one", error);
            }
        }
        if (shader == null) { RenderSystem.setShader(GameRenderer::getPositionTexColorShader); return; }
        shader.safeGetUniform("VfxTime").set(seconds);
        RenderSystem.setShader(() -> shader);
    }

    /** Builds the shader and draws one empty quad with it once the world is open (many drivers compile a program on its first draw), so the
     *  first Blue or Red does not stall on it. */
    static void prewarm() {
        bind(0);
        if (shader == null) return;
        var buffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < 4; i++) buffer.addVertex(0, 0, 0).setUv(0, 0).setColor(0, 0, 0, 0);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.depthMask(false);
        try {
            var mesh = buffer.build();
            if (mesh != null) com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        } finally {
            RenderSystem.depthMask(true); RenderSystem.disableBlend();
        }
    }
}
