package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.godwand.GodWandMod;
import net.godwand.network.ImpactFxPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.JsonEffectShaderProgram;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class GodWandPostFxManager {
    private static PostEffectProcessor postProcessor;
    private static int lastWidth = -1;
    private static int lastHeight = -1;

    private static boolean active = false;
    private static int currentTick = 0;
    private static int maxTicks = 0;
    private static int fxType = 0;
    private static float baseIntensity = 1.0f;
    private static Vec3d blastCenter = Vec3d.ZERO;

    public static void triggerImpact(double x, double y, double z, int type, int durationTicks, float intensity) {
        blastCenter = new Vec3d(x, y, z);
        fxType = type;
        maxTicks = Math.max(1, durationTicks);
        currentTick = maxTicks;
        baseIntensity = Math.max(0.1f, intensity);
        active = true;
    }

    public static void onClientTick() {
        if (active) {
            currentTick--;
            if (currentTick <= 0) {
                active = false;
            }
        }
    }

    public static void reset() {
        if (postProcessor != null) {
            postProcessor.close();
            postProcessor = null;
        }
        active = false;
        lastWidth = -1;
        lastHeight = -1;
    }

    public static void onWorldRender(WorldRenderContext context) {
        if (!active) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getFramebuffer() == null) return;

        int fbWidth = client.getWindow().getFramebufferWidth();
        int fbHeight = client.getWindow().getFramebufferHeight();
        if (fbWidth <= 0 || fbHeight <= 0) return;

        if (postProcessor == null) {
            try {
                postProcessor = new PostEffectProcessor(
                        client.getTextureManager(),
                        client.getResourceManager(),
                        client.getFramebuffer(),
                        Identifier.ofVanilla("shaders/post/godwand_impact.json")
                );
                postProcessor.setupDimensions(fbWidth, fbHeight);
                lastWidth = fbWidth;
                lastHeight = fbHeight;
            } catch (Exception e) {
                GodWandMod.LOGGER.error("Failed to initialize God Wand PostEffectProcessor", e);
                active = false;
                return;
            }
        }

        if (fbWidth != lastWidth || fbHeight != lastHeight) {
            postProcessor.setupDimensions(fbWidth, fbHeight);
            lastWidth = fbWidth;
            lastHeight = fbHeight;
        }

        float tickDelta = context.tickCounter().getTickDelta(false);
        float progress = MathHelper.clamp(1.0f - ((float) currentTick - tickDelta) / (float) maxTicks, 0.0f, 1.0f);

        float impactIntensity = baseIntensity * (1.0f - progress * 0.75f);
        float impactFrame = 0.0f;
        float impactMode = 0.0f;
        float shockwaveRadius = 0.0f;
        float distortionPower = 0.0f;
        float chromaticAmount = 0.0f;
        float zoomBlur = 0.0f;
        float retinaBurn = 0.0f;
        float coreRadius = 0.0f;
        float lensingPower = 0.0f;
        float gTime = (float) ((System.currentTimeMillis() % 10000000L) / 1000.0);

        int tickIdx = Math.max(0, maxTicks - currentTick);

        switch (fxType) {
            case ImpactFxPayload.TYPE_DETONATION -> {
                // Calamity Detonation:
                // Ticks 0..4: 36 Anime Impact Frames cycle: Six-Eyes Cyan -> Crimson Invert -> Ink-Wash -> Prism Line-Art
                if (progress < 0.26f) {
                    impactFrame = (1.0f - (progress / 0.26f));
                    int[] detModes = {3, 2, 0, 33, 3};
                    impactMode = detModes[Math.min(tickIdx, detModes.length - 1)];
                }

                // Shockwave expands rapidly outwards across screen
                shockwaveRadius = (float) Math.pow(progress, 0.65) * 1.35f;

                // Distortion peaks right after impact frame, then dissipates
                float distCurve = (float) Math.sin(progress * Math.PI);
                distortionPower = 0.048f * baseIntensity * distCurve;

                // Chromatic Aberration decays exponentially
                chromaticAmount = 0.024f * baseIntensity * (1.0f - progress);

                // Directional Zoom Blur & Retinal Flash Afterglow
                zoomBlur = 0.065f * baseIntensity * (1.0f - progress);
                retinaBurn = Math.max(0.0f, 0.65f * baseIntensity * (1.0f - progress * 1.4f));

                // 3D Singularity Gravitational Lensing
                coreRadius = (1.0f - progress) * 1.8f;
                lensingPower = (1.0f - progress) * 1.8f;
            }
            case ImpactFxPayload.TYPE_IMPLOSION -> {
                // Epicenter Calamity Implosion:
                // Phase 1 (progress 0..0.3): Inward suction (negative distortion power pulls pixels towards center)
                if (progress < 0.30f) {
                    float suckCurve = progress / 0.30f;
                    distortionPower = -0.042f * baseIntensity * suckCurve;
                    shockwaveRadius = 0.7f * (1.0f - suckCurve);
                    zoomBlur = -0.052f * baseIntensity * suckCurve; // Hút hướng tâm
                    impactMode = 4.0f; // Shadow Blue
                    coreRadius = suckCurve * 1.5f;
                    lensingPower = 1.6f * suckCurve;
                } else {
                    // Phase 2 (progress 0.30..0.55): Slam Impact Frame flash
                    float slamProg = (progress - 0.30f) / 0.70f;
                    if (slamProg < 0.30f) {
                        impactFrame = 1.0f - (slamProg / 0.30f);
                        impactMode = (tickIdx % 2 == 0) ? 3.0f : 2.0f;
                    }
                    shockwaveRadius = slamProg * 1.45f;
                    distortionPower = 0.052f * baseIntensity * (1.0f - slamProg);
                    zoomBlur = 0.048f * baseIntensity * (1.0f - slamProg);
                    retinaBurn = Math.max(0.0f, 0.45f * baseIntensity * (1.0f - slamProg * 1.5f));
                    coreRadius = Math.max(0.0f, (1.0f - slamProg) * 1.5f);
                    lensingPower = Math.max(0.0f, 2.2f * (1.0f - slamProg));
                }
                chromaticAmount = 0.030f * baseIntensity * (1.0f - progress);
            }
            case ImpactFxPayload.TYPE_COLLAPSE -> {
                // Spatial Path Collapse: Snappy, sharp tactical burst
                if (progress < 0.35f) {
                    impactFrame = 0.85f * (1.0f - (progress / 0.35f));
                    impactMode = (tickIdx % 2 == 0) ? 3.0f : 0.0f;
                }
                shockwaveRadius = progress * 1.2f;
                distortionPower = 0.028f * baseIntensity * (1.0f - progress);
                chromaticAmount = 0.016f * baseIntensity * (1.0f - progress);
                zoomBlur = 0.025f * baseIntensity * (1.0f - progress);
                coreRadius = (1.0f - progress) * 0.9f;
                lensingPower = (1.0f - progress) * 1.2f;
            }
            case ImpactFxPayload.TYPE_SUPERNOVA -> {
                // Cataclysmic Cosmic Supernova (Burst phase):
                // Ticks 0..7: Chuỗi chuyển đổi 36 Anime Impact Frame liên tục:
                // Isometric 30° Grid (35) -> Spectral Bands (34) -> Prism 7-Color Line-Art (33) -> Crimson (2) -> Six-Eyes Cyan (3) -> Stark Ink (0)
                if (progress < 0.32f) {
                    impactFrame = 1.0f - (progress / 0.32f);
                    int[] novaModes = {35, 34, 33, 2, 3, 0, 1, 33};
                    impactMode = novaModes[Math.min(tickIdx, novaModes.length - 1)];
                }
                // Relativistic shockwave expands across the entire sky
                shockwaveRadius = (float) Math.pow(progress, 0.52) * 1.65f;
                float distCurve = (float) Math.sin(progress * Math.PI);
                distortionPower = 0.075f * baseIntensity * distCurve;
                chromaticAmount = 0.045f * baseIntensity * (1.0f - progress);

                // Cực đại Zoom Blur và Lưu ảnh võng mạc (Retina Burn)
                zoomBlur = 0.098f * baseIntensity * (float) Math.sin(progress * Math.PI);
                retinaBurn = Math.max(0.0f, 1.0f * baseIntensity * (1.0f - progress * 1.15f));

                // Expanding Fireball Singularity
                coreRadius = MathHelper.lerp(progress, 1.2f, 8.5f);
                lensingPower = (1.0f - progress) * 2.8f;
            }
            case ImpactFxPayload.TYPE_BLACK_HOLE -> {
                // 3D Raymarched Black Hole Accretion & Collapse Progression (186 ticks)
                impactIntensity = 1.0f;
                // Progression 0.0 to 0.75 (Ticks 0..140): Growth & Steady Accretion
                if (progress < 0.75f) {
                    float grow = Math.min(1.0f, progress / 0.20f);
                    coreRadius = 2.4f * (float) Math.sin(grow * Math.PI * 0.5);
                    lensingPower = 1.4f * grow;
                    shockwaveRadius = 0.0f;
                    distortionPower = 0.0f;
                }
                // Progression 0.75 to 0.94 (Ticks 140..175): Gravitational Collapse / Inward Compression
                else if (progress < 0.94f) {
                    float collapseProg = (progress - 0.75f) / 0.19f;
                    coreRadius = MathHelper.lerp(collapseProg, 2.4f, 0.45f);
                    lensingPower = MathHelper.lerp(collapseProg, 1.4f, 4.2f);
                    zoomBlur = -0.045f * collapseProg; // Inward gravitational suction
                    distortionPower = -0.035f * collapseProg;
                }
                // Progression 0.94 to 1.0 (Ticks 176..186): Vacuum Silence & Horizon Jitter
                else {
                    coreRadius = 0.40f + 0.04f * (float) Math.sin(gTime * 50.0);
                    lensingPower = 4.8f;
                    zoomBlur = -0.06f;
                    distortionPower = -0.05f;
                }
            }
        }

        // Project 3D explosion coordinates to 2D Screen UV
        float centerX = 0.5f;
        float centerY = 0.5f;
        if (blastCenter.lengthSquared() > 0.001) {
            Camera camera = client.gameRenderer.getCamera();
            Vec3d camPos = camera.getPos();
            Vec3d rel = blastCenter.subtract(camPos);

            Quaternionf camRot = camera.getRotation();
            Vector3f local = new Vector3f((float) rel.x, (float) rel.y, (float) rel.z);
            new Quaternionf(camRot).conjugate().transform(local);

            // In camera coordinates: -Z is forward
            if (local.z < -0.1f) {
                float fov = (float) Math.toRadians(client.options.getFov().getValue());
                float tanFov = (float) Math.tan(fov / 2.0);
                float aspect = (float) fbWidth / (float) fbHeight;
                float projX = 0.5f + (local.x / (-local.z * tanFov * aspect)) * 0.5f;
                float projY = 0.5f - (local.y / (-local.z * tanFov)) * 0.5f;
                if (projX >= -0.2f && projX <= 1.2f && projY >= -0.2f && projY <= 1.2f) {
                    centerX = MathHelper.clamp(projX, 0.02f, 0.98f);
                    centerY = MathHelper.clamp(projY, 0.02f, 0.98f);
                }
            }
        }

        float aspect = (float) fbWidth / (float) fbHeight;

        Camera camera = client.gameRenderer.getCamera();
        Vec3d camPos = (camera != null) ? camera.getPos() : Vec3d.ZERO;
        Matrix4f invProj = new Matrix4f(context.projectionMatrix()).invert();
        Matrix4f invView = new Matrix4f(context.positionMatrix()).invert();

        for (PostEffectPass pass : postProcessor.passes) {
            JsonEffectShaderProgram prog = pass.getProgram();
            prog.getUniformByNameOrDummy("InvProjMat").set(invProj);
            prog.getUniformByNameOrDummy("InvViewMat").set(invView);
            prog.getUniformByNameOrDummy("CameraPos").set((float) camPos.x, (float) camPos.y, (float) camPos.z);
            prog.getUniformByNameOrDummy("BlastCenter").set((float) blastCenter.x, (float) blastCenter.y, (float) blastCenter.z);
            prog.getUniformByNameOrDummy("CoreRadius").set(coreRadius);
            prog.getUniformByNameOrDummy("LensingPower").set(lensingPower);
        }

        postProcessor.setUniforms("ImpactIntensity", impactIntensity);
        postProcessor.setUniforms("ImpactFrame", impactFrame);
        postProcessor.setUniforms("ImpactMode", impactMode);
        postProcessor.setUniforms("ShockwaveRadius", shockwaveRadius);
        postProcessor.setUniforms("DistortionPower", distortionPower);
        postProcessor.setUniforms("ChromaticAmount", chromaticAmount);
        postProcessor.setUniforms("ZoomBlur", zoomBlur);
        postProcessor.setUniforms("RetinaBurn", retinaBurn);
        postProcessor.setUniforms("CenterX", centerX);
        postProcessor.setUniforms("CenterY", centerY);
        postProcessor.setUniforms("Aspect", aspect);
        postProcessor.setUniforms("GTime", gTime);
        postProcessor.setUniforms("CoreRadius", coreRadius);
        postProcessor.setUniforms("LensingPower", lensingPower);

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        postProcessor.render(tickDelta);
    }
}
