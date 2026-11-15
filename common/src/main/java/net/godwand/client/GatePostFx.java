package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.godwand.GodWandMod;
import net.godwand.config.GodWandConfig;
import net.godwand.mixin.PostEffectProcessorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Bloom for the Silent Gate: what is bright in the frame (circles, beams, the doorway, the lit porcelain) is blurred and laid back over it, so light spills past its
 * own geometry, with shafts streaming away from the light source on screen. Reuses the Atomic blur and composite programs; the bright pass is vanilla
 * color_convolve (scale and offset: a threshold). Without it (post effects off, a shaderpack, a failed chain) the scene is simply drawn as before.
 */
final class GatePostFx {
    private GatePostFx() {}
    private static PostChain chain;
    private static int width, height, downsample;
    private static boolean failed;
    private static final String[] SMALL = {"gate_emit", "gate_blur_x", "gate_blur_y"};

    /** Frees the chain; the next cast builds it again (also how a resource reload is picked up). */
    static void close() {
        if (chain != null) chain.close();
        chain = null; failed = false; width = height = downsample = 0;
    }

    /** Runs the chain on the finished world frame. {@code strength} 0 is a pass-through (still run, so shaders are compiled behind the black opening shot);
     *  {@code burst} 0..1 adds the shafts away from ({@code cx}, {@code cy}), screen coordinates 0..1. */
    static void process(float strength, float burst, float cx, float cy, float delta) { process(strength, burst, cx, cy, delta, 0, 0); }

    /** True while the chain will run this frame: what it does for the picture (the inked impact frames) need not be faked another way. */
    static boolean ready() {
        return !failed && GodWandConfig.CLIENT.postEffects && !AtomicPostFx.shaderPackActive() && !Boolean.getBoolean("godwand.gate.nobloom");
    }

    /** As above; {@code ink} 1 or 2 turns the frame into an anime impact frame (two tones: white on black, or black on white), leaving the scope bars
     *  ({@code bar}: the share of the height each covers) black. */
    static void process(float strength, float burst, float cx, float cy, float delta, float ink, float bar) {
        var client = Minecraft.getInstance();
        if (!ready()) return;
        int w = client.getWindow().getWidth(), h = client.getWindow().getHeight();
        if (w <= 0 || h <= 0) return;
        try {
            if (chain == null) chain = new PostChain(client.getTextureManager(), client.getResourceManager(), client.getMainRenderTarget(),
                    ResourceLocation.withDefaultNamespace("shaders/post/godwand_gate.json"));
            if (w != width || h != height || downsample != GodWandConfig.CLIENT.bloomDownsample) {
                chain.resize(w, h);
                for (String name : SMALL) chain.getTempTarget(name).resize(GodWandPostFxManager.bloomDimension(w), GodWandPostFxManager.bloomDimension(h), false);
                for (var pass : ((PostEffectProcessorAccessor) chain).getPasses())
                    pass.setOrthoMatrix(new Matrix4f().setOrtho(0, pass.outTarget.width, 0, pass.outTarget.height, .1f, 1000));
                width = w; height = h; downsample = GodWandConfig.CLIENT.bloomDownsample;
            }
            chain.setUniform("BloomStrength", strength);
            chain.setUniform("BloomBurst", burst);
            chain.setUniform("CenterX", cx);
            chain.setUniform("CenterY", cy);
            chain.setUniform("Ink", ink);
            chain.setUniform("Bar", bar);
            RenderSystem.disableBlend(); RenderSystem.disableDepthTest(); RenderSystem.resetTextureMatrix();
            chain.process(delta);
        } catch (Exception error) {
            failed = true;
            GodWandMod.LOGGER.error("Silent Gate bloom disabled; the gate is drawn without it", error);
            if (chain != null) { chain.close(); chain = null; }
        } finally {
            client.getMainRenderTarget().bindWrite(true);
            RenderSystem.enableDepthTest(); RenderSystem.depthMask(true);
        }
    }
}
