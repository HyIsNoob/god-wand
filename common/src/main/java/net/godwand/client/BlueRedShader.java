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
}
