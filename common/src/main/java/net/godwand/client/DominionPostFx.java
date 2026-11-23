package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.godwand.GodWandMod;
import net.godwand.config.GodWandConfig;
import net.godwand.mixin.PostEffectProcessorAccessor;
import net.godwand.skill.BlockDominionPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * True screen-space cinematic post-process chain for Block Dominion (shaders/post/godwand_dominion.json):
 * - Real multi-pass Kawase/Gaussian bloom for luminous cores without fake billboard glow discs
 * - Spatial refraction shockwaves with RGB chromatic dispersion (bends the real 3D scene!)
 * - Aerodynamic radial velocity motion blur during Mach / Jet wing flight
 * - True scene-space Sakuga Impact Frames using Sobel contours on the real 3D world tinted by Material Resonance.
 */
public final class DominionPostFx {
    private static final String[] SMALL = {"dom_emit", "dom_blur_x", "dom_blur_y"};

    private static PostChain chain;
    private static int width, height, downsample;
    private static boolean failed;

    // Live animated state (ticked once per client tick, interpolated smoothly per render frame)
    private static float speedBlur, prevSpeedBlur;
    private static float bloomStrength, prevBloomStrength;
    private static float accentR = 1.0f, accentG = 0.82f, accentB = 0.35f;

    private static int shockTicks, shockMaxTicks;
    private static float shockPeakStrength;
    private static float shockCenterX = 0.5f, shockCenterY = 0.5f;

    private static int impactTicks, impactMaxTicks;

    private DominionPostFx() {}

    public static boolean ready() {
        return !failed && GodWandConfig.CLIENT.postEffects && !AtomicPostFx.shaderPackActive();
    }

    public static void close() {
        if (chain != null) chain.close();
        chain = null;
        failed = false;
        width = height = downsample = 0;
    }

    public static void reset() {
        speedBlur = prevSpeedBlur = 0f;
        bloomStrength = prevBloomStrength = 0f;
        shockTicks = shockMaxTicks = 0;
        impactTicks = impactMaxTicks = 0;
    }

    public static void setAmbientState(float targetSpeedBlur, float targetBloom, BlockDominionPalette palette) {
        prevSpeedBlur = speedBlur;
        prevBloomStrength = bloomStrength;
        speedBlur = Mth.lerp(0.28f, speedBlur, Mth.clamp(targetSpeedBlur, 0f, 1f));
        bloomStrength = Mth.lerp(0.22f, bloomStrength, Mth.clamp(targetBloom, 0f, 0.85f));
        if (palette != null) {
            accentR = Mth.lerp(0.18f, accentR, palette.primaryR / 255.0f);
            accentG = Mth.lerp(0.18f, accentG, palette.primaryG / 255.0f);
            accentB = Mth.lerp(0.18f, accentB, palette.primaryB / 255.0f);
        }
    }

    public static void triggerShockwave(float strength, int durationTicks, BlockDominionPalette palette) {
        shockPeakStrength = Math.max(shockPeakStrength * 0.5f, Mth.clamp(strength, 0.1f, 1.0f));
        shockMaxTicks = Math.max(4, durationTicks);
        shockTicks = shockMaxTicks;
        shockCenterX = 0.5f;
        shockCenterY = 0.5f;
        if (palette != null) {
            accentR = palette.primaryR / 255.0f;
            accentG = palette.primaryG / 255.0f;
            accentB = palette.primaryB / 255.0f;
        }
    }

    public static void triggerImpact(int ticks, BlockDominionPalette palette) {
        if (!GodWandConfig.CLIENT.impactFrames) return;
        impactMaxTicks = Math.max(2, ticks);
        impactTicks = Math.max(impactTicks, impactMaxTicks);
        if (palette != null) {
            accentR = palette.primaryR / 255.0f;
            accentG = palette.primaryG / 255.0f;
            accentB = palette.primaryB / 255.0f;
        }
    }

    public static void tick() {
        if (shockTicks > 0) shockTicks--;
        if (impactTicks > 0) impactTicks--;
    }

    public static boolean isActive() {
        return impactTicks > 0 || shockTicks > 0 || speedBlur > 0.008f || bloomStrength > 0.02f;
    }

    public static void prewarm() {
        if (!ready()) return;
        processInternal(0f, 0f, 0f, 0f, 0f, 0f);
    }

    public static void renderAfterWorld() {
        if (!ready() || !isActive()) return;
        if (AtomicCinematic.active() || FugaCinematic.active()
                || SilentGateCinematic.active() || PurpleCinematic.active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        float delta = mc.getTimer().getGameTimeDeltaPartialTick(true);

        float sb = Mth.lerp(delta, prevSpeedBlur, speedBlur);
        float bloom = Mth.lerp(delta, prevBloomStrength, bloomStrength);

        float shockR = 0f;
        float shockStr = 0f;
        if (shockTicks > 0 && shockMaxTicks > 0) {
            float p = 1.0f - Mth.clamp((shockTicks - delta) / (float) shockMaxTicks, 0f, 1f);
            float ease = 1.0f - (1.0f - p) * (1.0f - p);
            shockR = 0.04f + ease * 1.15f;
            shockStr = shockPeakStrength * (1.0f - p) * (1.0f - p * 0.45f);
            bloom = Math.max(bloom, 0.42f * (1.0f - p));
        }

        float impactMode = 0f;
        if (impactTicks > 0 && impactMaxTicks > 0) {
            // Classic anime sakuga 2-tone flip: frame 0 dark ink -> frame 1 bright flash -> frame 2+ dark ink
            int elapsed = impactMaxTicks - impactTicks;
            impactMode = (elapsed == 1) ? 2.0f : 1.0f;
            bloom = Math.max(bloom, 0.55f);
        }

        float vfxTime = (mc.level.getGameTime() + delta) / 20.0f;
        processInternal(bloom, sb, shockR, shockStr, impactMode, vfxTime);
    }

    private static void processInternal(float bloom, float sb, float shockR, float shockStr,
                                        float impactMode, float vfxTime) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        int w = client.getWindow().getWidth();
        int h = client.getWindow().getHeight();
        if (w <= 0 || h <= 0) return;

        try {
            if (chain == null) {
                chain = new PostChain(client.getTextureManager(), client.getResourceManager(),
                        client.getMainRenderTarget(),
                        ResourceLocation.withDefaultNamespace("shaders/post/godwand_dominion.json"));
            }
            if (w != width || h != height || downsample != GodWandConfig.CLIENT.bloomDownsample) {
                chain.resize(w, h);
                for (String name : SMALL) {
                    chain.getTempTarget(name).resize(
                            GodWandPostFxManager.bloomDimension(w),
                            GodWandPostFxManager.bloomDimension(h), false);
                }
                for (var pass : ((PostEffectProcessorAccessor) chain).getPasses()) {
                    pass.setOrthoMatrix(new Matrix4f().setOrtho(
                            0, pass.outTarget.width, 0, pass.outTarget.height, 0.1f, 1000f));
                }
                width = w;
                height = h;
                downsample = GodWandConfig.CLIENT.bloomDownsample;
            }
            chain.setUniform("BloomStrength", bloom);
            chain.setUniform("CenterX", shockCenterX);
            chain.setUniform("CenterY", shockCenterY);
            chain.setUniform("SpeedBlur", sb);
            chain.setUniform("ShockRadius", shockR);
            chain.setUniform("ShockStrength", shockStr);
            chain.setUniform("ImpactMode", impactMode);
            chain.setUniform("AccentR", accentR);
            chain.setUniform("AccentG", accentG);
            chain.setUniform("AccentB", accentB);
            chain.setUniform("VfxTime", vfxTime);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            chain.process(0f);
        } catch (Exception error) {
            failed = true;
            GodWandMod.LOGGER.error("Block Dominion post effect disabled; continuing with world visuals", error);
            if (chain != null) {
                chain.close();
                chain = null;
            }
        } finally {
            client.getMainRenderTarget().bindWrite(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
        }
    }
}
