package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.godwand.GodWandMod;
import net.godwand.client.SupernovaAccretionRenderer.SkyFrame;
import net.godwand.mixin.PostEffectProcessorAccessor;
import net.godwand.network.ImpactFxPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class GodWandPostFxManager {
    private record Frame(Matrix4f positionMatrix, Matrix4f projectionMatrix, float tickDelta) {}
    private static Frame pendingFrame;
    private static PostChain postProcessor;
    private static PostChain novaProcessor;
    private static int lastWidth = -1;
    private static int lastHeight = -1;

    private static boolean active = false;
    private static int currentTick = 0;
    private static int waitingForBurstTicks;
    private static int maxTicks = 0;
    private static int fxType = 0;
    private static float baseIntensity = 1.0f;
    private static Vec3 blastCenter = Vec3.ZERO;
    private static float recallCoreStart;

    public static float singularityRadius(float progress) {
        float grow = Mth.clamp(progress / 0.10f, 0, 1);
        float collapse = Mth.clamp((progress - 0.80f) / 0.20f, 0, 1);
        return Mth.lerp(grow * (2 - grow), 0.18f, 0.68f) * (1 - collapse * collapse);
    }

    public static float singularityRecallRadius(float start, float progress) {
        float collapse = Mth.clamp(progress / 0.60f, 0, 1);
        return start * (1 - collapse * collapse);
    }

    public static float supernovaRadius(float progress) {
        float burst = Mth.clamp(progress / 0.10f, 0, 1);
        float radius = Mth.lerp(1.0f - (1.0f - burst) * (1.0f - burst), 14.0f, 68.0f);
        float swell = Mth.clamp((progress - 0.10f) / 0.30f, 0, 1);
        return Mth.lerp(swell * (2.0f - swell), radius, 98.0f);
    }

    public static float blackHoleRadius(float progress) {
        return net.godwand.skill.SupernovaSequence.coreRadius(progress * 340);
    }

    public static float supernovaAftermath(float progress) {
        float t = Mth.clamp((progress - 0.22f) / 0.18f, 0, 1);
        return t * t * (3.0f - 2.0f * t);
    }

    public static void triggerImpact(double x, double y, double z, int type, int durationTicks, float intensity) {
        if (type == ImpactFxPayload.TYPE_SUPERNOVA_IMPACT) {
            CustomImpactFrameOverlay.trigger();
            return;
        }
        if (type == ImpactFxPayload.TYPE_REASSEMBLY) {
            float progress = 1 - (float) currentTick / Math.max(1, maxTicks);
            boolean sameCore = active && blastCenter.distanceToSqr(x, y, z) < 0.01;
            recallCoreStart = !sameCore ? 0 : fxType == ImpactFxPayload.TYPE_DETONATION ? singularityRadius(progress)
                    : fxType == ImpactFxPayload.TYPE_REASSEMBLY ? singularityRecallRadius(recallCoreStart, progress) : 0;
        }
        blastCenter = new Vec3(x, y, z);
        fxType = type;
        maxTicks = Math.max(1, durationTicks);
        currentTick = maxTicks;
        waitingForBurstTicks = 0;
        baseIntensity = Math.max(0.1f, intensity);
        active = true;
    }

    public static void onClientTick() {
        if (active) {
            currentTick--;
            if (currentTick <= 0) {
                if (fxType == ImpactFxPayload.TYPE_BLACK_HOLE && ++waitingForBurstTicks <= 40) {
                    currentTick = 0;
                } else active = false;
            }
        }
    }

    public static void reset() {
        pendingFrame = null;
        reloadShaders();
        active = false;
        waitingForBurstTicks = 0;
    }

    public static void reloadShaders() {
        if (postProcessor != null) {
            postProcessor.close();
            postProcessor = null;
        }
        if (novaProcessor != null) {
            novaProcessor.close();
            novaProcessor = null;
        }
        lastWidth = -1;
        lastHeight = -1;
    }

    public static int bloomDimension(int fullSize) {
        int divisor = net.godwand.config.GodWandConfig.CLIENT.bloomDownsample;
        return Math.max(1, (fullSize + divisor - 1) / divisor);
    }

    private static void resizeNovaTargets(int width, int height) {
        for (String name : new String[]{"nova_emit", "nova_blur_x", "nova_blur_y"}) {
            novaProcessor.getTempTarget(name).resize(bloomDimension(width), bloomDimension(height), Minecraft.ON_OSX);
        }
        for (PostPass pass : ((PostEffectProcessorAccessor) novaProcessor).getPasses()) {
            pass.setOrthoMatrix(new Matrix4f().setOrtho(0, pass.outTarget.width, 0, pass.outTarget.height, 0.1f, 1000));
        }
    }

    public static void onWorldRender(Matrix4f positionMatrix, Matrix4f projectionMatrix, float tickDelta) {
        if (!net.godwand.config.GodWandConfig.CLIENT.postEffects || (!active && SupernovaAccretionRenderer.skyFrame(0) == null)) {
            pendingFrame = null;
            return;
        }
        pendingFrame = new Frame(new Matrix4f(positionMatrix), new Matrix4f(projectionMatrix), tickDelta);
    }

    public static void renderAfterWorld() {
        Frame frame = pendingFrame;
        pendingFrame = null;
        if (frame != null) renderFrame(frame);
    }

    private static void setUniform(PostChain chain, String name, float value) {
        if (chain == null) return;
        for (net.minecraft.client.renderer.PostPass pass : chain.passes) {
            var uniform = pass.getEffect().safeGetUniform(name);
            if (uniform != null) {
                uniform.set(value);
            }
        }
    }

    private static void renderFrame(Frame context) {
        var sky = SupernovaAccretionRenderer.skyFrame(0);
        if (!active && sky == null) return;
        float tickDelta = context.tickDelta();
        sky = SupernovaAccretionRenderer.skyFrame(tickDelta);
        boolean skyOnly = sky != null && !(active && (fxType == ImpactFxPayload.TYPE_SUPERNOVA || fxType == ImpactFxPayload.TYPE_BLACK_HOLE));
        Vec3 renderCenter = skyOnly ? sky.center() : blastCenter;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getMainRenderTarget() == null) return;

        int fbWidth = client.getWindow().getWidth();
        int fbHeight = client.getWindow().getHeight();
        if (fbWidth <= 0 || fbHeight <= 0) return;

        boolean cosmic = skyOnly || (active && (fxType == ImpactFxPayload.TYPE_SUPERNOVA || fxType == ImpactFxPayload.TYPE_BLACK_HOLE));
        PostChain processor = cosmic ? novaProcessor : postProcessor;
        if (processor == null) {
            try {
                processor = new PostChain(client.getTextureManager(), client.getResourceManager(),
                        client.getMainRenderTarget(), new ResourceLocation(cosmic ? "shaders/post/godwand_nova.json" : "shaders/post/godwand_impact.json"));
                processor.resize(fbWidth, fbHeight);
                if (cosmic) {
                    novaProcessor = processor;
                    resizeNovaTargets(fbWidth, fbHeight);
                } else postProcessor = processor;
            } catch (Exception e) {
                if (processor != null) processor.close();
                if (cosmic) novaProcessor = null;
                else postProcessor = null;
                GodWandMod.LOGGER.error("Failed to initialize God Wand PostEffectProcessor", e);
                active = false;
                return;
            }
        }
        if (fbWidth != lastWidth || fbHeight != lastHeight) {
            if (postProcessor != null) postProcessor.resize(fbWidth, fbHeight);
            if (novaProcessor != null) {
                novaProcessor.resize(fbWidth, fbHeight);
                resizeNovaTargets(fbWidth, fbHeight);
            }
            lastWidth = fbWidth;
            lastHeight = fbHeight;
        }

        float progress = Mth.clamp(1.0f - ((float) currentTick - tickDelta) / Math.max(1, maxTicks), 0.0f, 1.0f);

        float impactIntensity = active && !skyOnly ? baseIntensity * (1.0f - progress * 0.75f) : 0;
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

        if (active && !skyOnly) switch (fxType) {
            case ImpactFxPayload.TYPE_DETONATION -> {
                // Calamity Detonation:
                // Ticks 0..11 (12 ticks = 0.6s): Chu kỳ 36 Anime Impact Frame lâu hơn, mỗi frame 3 ticks rõ nét
                if (tickIdx < 12) {
                    impactFrame = 1.0f - ((float) tickIdx / 12.0f);
                    int[] detModes = {3, 3, 3, 2, 2, 2, 0, 0, 0, 33, 33, 33};
                    impactMode = detModes[tickIdx];
                }

                // Ticks 0..22: Sóng năng lượng bung tỏa kiểu Orbital Railgun lúc bắn
                if (tickIdx < 22) {
                    float shockProg = (float) tickIdx / 22.0f;
                    shockwaveRadius = (float) Math.pow(shockProg, 0.65) * 1.55f;
                    float distCurve = (float) Math.sin(shockProg * Math.PI);
                    distortionPower = 0.052f * baseIntensity * distCurve;
                    chromaticAmount = 0.028f * baseIntensity * (1.0f - shockProg);
                    zoomBlur = 0.070f * baseIntensity * (1.0f - shockProg);
                    retinaBurn = Math.max(0.0f, 0.75f * baseIntensity * (1.0f - shockProg * 1.3f));
                }
                // BỎ HOÀN TOÀN sóng shockwave co rút lúc thu về theo yêu cầu!
                else {
                    shockwaveRadius = 0.0f;
                    distortionPower = 0.0f;
                    zoomBlur = 0.0f;
                }

                // Stable shell; only the plasma moves until recall.
                coreRadius = singularityRadius(progress);
                lensingPower = 0.12f;

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
                // Spatial Path Collapse: Hoàn toàn không dùng filter impact frame hay méo hình
                impactFrame = 0.0f;
                shockwaveRadius = progress * 1.1f;
                distortionPower = 0.0f;
                chromaticAmount = 0.0f;
                zoomBlur = 0.0f;
                coreRadius = 0.0f; // Tuyệt đối không vẽ hố đen
                lensingPower = 0.0f;
            }
            case ImpactFxPayload.TYPE_SEISMIC_SLAM -> {
                // Chiêu 3: Ground Seismic Slam (Mũi khoan ma đạn cắm xuống đất)
                // Anime Impact Frame 12 ticks sắc nét chuẩn phong cách Chiêu 2 (mỗi frame 3 ticks rõ nét)
                if (tickIdx < 12) {
                    impactFrame = 1.0f - ((float) tickIdx / 12.0f);
                    int[] slamModes = {3, 3, 3, 2, 2, 2, 0, 0, 0, 33, 33, 33};
                    impactMode = slamModes[tickIdx];
                } else {
                    impactFrame = 0.0f;
                }
                shockwaveRadius = (float) Math.pow(progress, 0.45) * 1.6f;
                distortionPower = 0.0f; // Bỏ méo màn hình
                chromaticAmount = 0.025f * baseIntensity * (1.0f - progress);
                zoomBlur = 0.0f; // Bỏ nhòe màn hình
                retinaBurn = 0.0f; // Bỏ hoàn toàn flash trắng xóa màn hình theo yêu cầu!
                coreRadius = 0.0f; // Tuyệt đối không có hố đen
                lensingPower = 0.0f;
            }
            case ImpactFxPayload.TYPE_SUPERNOVA -> {
                impactFrame = 0.0f;
                shockwaveRadius = 0.0f;
                distortionPower = 0.0f;
                float aftermath = supernovaAftermath(progress * maxTicks / 240f);
                chromaticAmount = 0.006f * baseIntensity * aftermath * (1.0f - progress);
                zoomBlur = 0.0f;
                retinaBurn = 0.0f;
                coreRadius = supernovaRadius(progress * maxTicks / 240f);
                lensingPower = 0.0f;
                impactIntensity = baseIntensity * (float) Math.pow(1.0f - progress, 1.45);
            }
            case ImpactFxPayload.TYPE_BLACK_HOLE -> {
                impactIntensity = 1.0f;
                // Suốt từ progress 0.0 đến 1.0 (Ticks 1..340): Hố Đen to dần ĐƠN ĐIỆU từ 0.15m lên 5.2m cực đại
                coreRadius = blackHoleRadius(progress);
                lensingPower = Mth.lerp(progress, 0.4f, 3.6f) * Mth.clamp(coreRadius / 2, 0, 1);

                // Hiệu ứng shockwave tản ra trước khi đâm xuống tầm 0.5 giây (Ticks 330..340)
                if (progress >= 330.0f / 340.0f) {
                    float waveProgress = (progress - (330.0f / 340.0f)) / (10.0f / 340.0f);
                    shockwaveRadius = (float) Math.pow(waveProgress, 0.65) * 1.55f;
                    float waveFade = (float) Math.sin(waveProgress * Math.PI);
                    distortionPower = 0.045f * waveFade;
                    chromaticAmount = 0.020f * waveFade;
                    zoomBlur = 0.032f * waveFade;
                } else {
                    shockwaveRadius = 0.0f;
                    distortionPower = 0.0f;
                    zoomBlur = 0.0f;
                }
            }
            case ImpactFxPayload.TYPE_GRAVITY_CORE -> {
                // Quả cầu năng lượng Chiêu 3: Thu nhỏ gọn gàng, tinh thể Ruby đỏ rực rỡ (Dense Crimson Core)
                // Tuyệt đối không dùng hố đen, không thấu kính méo màn hình, không scale nhấp nhô
                float grow = Math.min(1.0f, progress / 0.12f);
                coreRadius = 0.52f * grow * Math.min(1.1f, baseIntensity);
                lensingPower = 0.0f; // Không thấu kính méo hố đen
                distortionPower = 0.0f; // Không bẻ cong méo hình
                zoomBlur = 0.0f;
                impactIntensity = 1.6f + (float) (Math.sin(gTime * 12.0) * 0.25); // Nhấp nháy ngọn lửa sống động
            }
            case ImpactFxPayload.TYPE_REASSEMBLY -> {
                // Chiêu 2 Reassembly Climax: Cực kỳ impact khi hàng trăm khối đá khóa sập vào đúng vị trí 1:1
                // 1. Chớp 3 frames Manga Inked Impact Frame sắc lẹm (Mode 33: Prism Inked Line-art / Mode 0: Ink Wash)
                if (tickIdx < 4) {
                    impactFrame = 0.22f * (1.0f - ((float) tickIdx / 4.0f));
                    impactMode = (tickIdx % 2 == 0) ? 33.0f : 0.0f;
                } else {
                    impactFrame = 0.0f;
                }
                // 2. Chớp trắng lóa mắt Retinal Flash nung chảy mối nối không gian
                float flash = (1.0f - progress) * baseIntensity;
                retinaBurn = flash * 0.18f;
                chromaticAmount = flash * 0.006f;
                zoomBlur = 0.0f; // Tuyệt đối không nhòe
                distortionPower = 0.0f; // Bỏ shockwave méo hình lúc thu về
                shockwaveRadius = 0.0f;
                coreRadius = singularityRecallRadius(recallCoreStart, progress);
                lensingPower = 0.0f;
            }
        }

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 camPos = camera != null ? camera.getPosition() : Vec3.ZERO;
        Vector4f clip = new Vector4f((float) (renderCenter.x - camPos.x),
                (float) (renderCenter.y - camPos.y), (float) (renderCenter.z - camPos.z), 1);
        clip.mul(context.positionMatrix()).mul(context.projectionMatrix());
        float centerX = clip.w > 0.001f ? clip.x / clip.w * 0.5f + 0.5f : 0.5f;
        float centerY = clip.w > 0.001f ? clip.y / clip.w * 0.5f + 0.5f : 0.5f;

        float aspect = (float) fbWidth / (float) fbHeight;

        Matrix4f invProj = new Matrix4f(context.projectionMatrix()).invert();
        Matrix4f invView = new Matrix4f(context.positionMatrix()).invert();

        int passIndex = 0;
        for (PostPass pass : processor.passes) {
            EffectInstance prog = pass.getEffect();
            prog.safeGetUniform("EmissionOnly").set(cosmic && passIndex == 1 ? 1.0f : 0.0f);
            passIndex++;
            prog.safeGetUniform("InvProjMat").set(invProj);
            prog.safeGetUniform("InvViewMat").set(invView);
            prog.safeGetUniform("CameraPos").set((float) camPos.x, (float) camPos.y, (float) camPos.z);
            prog.safeGetUniform("BlastCenter").set((float) renderCenter.x, (float) renderCenter.y, (float) renderCenter.z);
            Vec3 skyCenter = sky != null ? sky.center() : renderCenter;
            prog.safeGetUniform("SkyRiftCenter").set((float) skyCenter.x, (float) skyCenter.y, (float) skyCenter.z);
            prog.safeGetUniform("CoreRadius").set(coreRadius);
            prog.safeGetUniform("LensingPower").set(lensingPower);
            prog.safeGetUniform("FxType").set(active ? (float) fxType : -1.0f);
        }

        setUniform(processor, "BloomStrength", net.godwand.config.GodWandConfig.CLIENT.bloomStrength);
        setUniform(processor, "BloomBurst", fxType == ImpactFxPayload.TYPE_SUPERNOVA && clip.w > 0.001f
                ? Math.max(0, 1 - progress * maxTicks / 28) : 0);
        setUniform(processor, "ImpactIntensity", impactIntensity);
        setUniform(processor, "ImpactFrame", net.godwand.config.GodWandConfig.CLIENT.impactFrames ? impactFrame : 0);
        setUniform(processor, "ImpactMode", impactMode);
        setUniform(processor, "ShockwaveRadius", shockwaveRadius);
        setUniform(processor, "DistortionPower", distortionPower);
        setUniform(processor, "ChromaticAmount", chromaticAmount);
        setUniform(processor, "ZoomBlur", zoomBlur);
        setUniform(processor, "RetinaBurn", retinaBurn);
        setUniform(processor, "CenterX", centerX);
        setUniform(processor, "CenterY", centerY);
        setUniform(processor, "Aspect", aspect);
        setUniform(processor, "GTime", gTime);
        setUniform(processor, "CoreRadius", coreRadius);
        setUniform(processor, "LensingPower", lensingPower);
        setUniform(processor, "FxType", skyOnly ? 9f : active ? (float) fxType : -1f);
        setUniform(processor, "CollapseProgress", fxType == ImpactFxPayload.TYPE_BLACK_HOLE ? Mth.clamp((progress * 340 - 250) / 90, 0, 1) : 0);
        setUniform(processor, "SkyRiftAge", sky != null ? sky.time() : -1f);
        setUniform(processor, "SkyRiftScale", sky != null ? sky.scale() : 0);
        setUniform(processor, "SkyRiftCollapse", sky != null ? sky.collapse() : 0);
        setUniform(processor, "SkyBlackout", sky != null && net.godwand.config.GodWandConfig.CLIENT.skyBlackout ? net.godwand.skill.SupernovaSequence.skyBlackout(
                sky.time() - net.godwand.skill.SupernovaSequence.CHARGE_TICKS) : 0);
        setUniform(processor, "RiftPulseRadius", sky != null ? sky.pulseRadius() : 0);
        setUniform(processor, "RiftPulseStrength", sky != null ? sky.pulseStrength() : 0);
        setUniform(processor, "NovaProgress", !skyOnly && fxType == ImpactFxPayload.TYPE_SUPERNOVA ? progress * maxTicks / 240f : 0);
        setUniform(processor, "NovaAftermath", !skyOnly && fxType == ImpactFxPayload.TYPE_SUPERNOVA ? supernovaAftermath(progress * maxTicks / 240f) : 0.0f);

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        try {
            processor.process(tickDelta);
        } finally {
            client.getMainRenderTarget().bindWrite(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
        }
    }
}
