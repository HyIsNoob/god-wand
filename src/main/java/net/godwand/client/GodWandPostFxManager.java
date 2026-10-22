package net.godwand.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.godwand.GodWandMod;
import net.godwand.mixin.PostEffectProcessorAccessor;
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
                if (tickIdx < 5) {
                    impactFrame = 1.0f - ((float) tickIdx / 5.0f);
                    int[] detModes = {3, 2, 0, 33, 3};
                    impactMode = detModes[tickIdx];
                }

                // Ticks 0..15: Sóng xung kích bùng nổ, quang sai và lóa võng mạc
                if (tickIdx < 16) {
                    float shockProg = (float) tickIdx / 16.0f;
                    shockwaveRadius = (float) Math.pow(shockProg, 0.65) * 1.35f;
                    float distCurve = (float) Math.sin(shockProg * Math.PI);
                    distortionPower = 0.048f * baseIntensity * distCurve;
                    chromaticAmount = 0.024f * baseIntensity * (1.0f - shockProg);
                    zoomBlur = 0.065f * baseIntensity * (1.0f - shockProg);
                    retinaBurn = Math.max(0.0f, 0.65f * baseIntensity * (1.0f - shockProg * 1.4f));
                }

                // 3D Singularity Gravitational Lensing & Lõi năng lượng duy trì cùng 45 Tether Beams
                if (progress < 0.94f) {
                    coreRadius = 1.6f;
                    lensingPower = 1.6f;
                } else {
                    // Ticks 106..112: Hút ngược chân không sụp đổ lõi
                    float snapProg = (progress - 0.94f) / 0.06f;
                    coreRadius = MathHelper.lerp(snapProg, 1.6f, 0.0f);
                    lensingPower = MathHelper.lerp(snapProg, 1.6f, 0.0f);
                    zoomBlur = -0.05f * snapProg;
                }
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
                shockwaveRadius = progress * 1.1f;
                distortionPower = 0.022f * baseIntensity * (1.0f - progress);
                chromaticAmount = 0.012f * baseIntensity * (1.0f - progress);
                zoomBlur = 0.0f;
                coreRadius = 0.0f; // Tuyệt đối không vẽ hố đen
                lensingPower = 0.0f;
            }
            case ImpactFxPayload.TYPE_SEISMIC_SLAM -> {
                // Chiêu 3: Ground Seismic Slam (Thiên thạch giáng sập)
                // 2-3 frame Anime Impact Frame sắc lạnh trắng đen
                if (tickIdx < 4) {
                    impactFrame = 1.0f - ((float) tickIdx / 4.0f);
                    int[] slamModes = {0, 1, 33, 0};
                    impactMode = slamModes[tickIdx];
                }
                // Sóng xung kích mặt đất lan rộng nhanh
                shockwaveRadius = (float) Math.pow(progress, 0.45) * 1.6f;
                distortionPower = 0.065f * baseIntensity * (1.0f - progress);
                chromaticAmount = 0.025f * baseIntensity * (1.0f - progress);
                zoomBlur = 0.045f * baseIntensity * (1.0f - progress);
                retinaBurn = Math.max(0.0f, 0.45f * baseIntensity * (1.0f - progress * 1.5f));
                coreRadius = 0.0f; // Tuyệt đối không có hố đen
                lensingPower = 0.0f;
            }
            case ImpactFxPayload.TYPE_SUPERNOVA -> {
                // Cataclysmic Cosmic Supernova (Burst & Lingering Mushroom Aftermath - 140 ticks / 7.0s duration):
                // Ticks 0..11 (progress < 0.08f): Chuỗi 36 Anime Impact Frame liên tục
                if (progress < 0.08f) {
                    impactFrame = 1.0f - (progress / 0.08f);
                    int[] novaModes = {35, 34, 33, 2, 3, 0, 1, 33};
                    impactMode = novaModes[Math.min(tickIdx, novaModes.length - 1)];
                } else {
                    impactFrame = 0.0f;
                }

                // Relativistic shockwave expands across the entire sky
                shockwaveRadius = (float) Math.pow(progress, 0.42) * 2.8f;
                float distCurve = (float) Math.sin(progress * Math.PI);
                distortionPower = 0.085f * baseIntensity * distCurve * (1.0f - progress * 0.7f);
                chromaticAmount = 0.055f * baseIntensity * (1.0f - progress);

                // Zoom Blur & Retina Burn: cực đại lúc nổ rồi tan dần mượt mà
                zoomBlur = 0.12f * baseIntensity * (float) Math.sin(progress * Math.PI) * (1.0f - progress * 0.5f);
                retinaBurn = Math.max(0.0f, 1.0f * baseIntensity * (1.0f - progress * 2.0f));

                // Quả cầu lửa & đám mây nấm vũ trụ:
                // Ticks 0..35 (progress 0..0.25): Bung nở cực nhanh từ 5.2m lên 38.0m
                // Ticks 36..140 (progress 0.25..1.0): Duy trì kích thước khổng lồ 38m trong khi chuyển thành khói bụi & tan dần
                float expandT = Math.min(1.0f, progress / 0.25f);
                coreRadius = MathHelper.lerp((float) Math.sin(expandT * Math.PI * 0.5), 5.2f, 38.0f);
                lensingPower = Math.max(0.0f, (1.0f - progress) * 2.5f);

                // AFTERMATH: Không biến mất đột ngột! Cường độ mờ dần từ từ trong 140 ticks (7 giây)
                impactIntensity = baseIntensity * (1.0f - (float) Math.pow(progress, 2.2));
            }
            case ImpactFxPayload.TYPE_BLACK_HOLE -> {
                impactIntensity = 1.0f;
                // Ticks 0..340 (progress 0.0 -> 0.944): Hố Đen to dần từ 1 chấm (0.15f) lên cực đại (5.2f)
                // ĐƠN ĐIỆU LIÊN TỤC - TUYỆT ĐỐI KHÔNG CO RÚT HOẶC DAO ĐỘNG!
                if (progress < 0.944f) {
                    float grow = progress / 0.944f;
                    // Đường cong nở mượt: lúc đầu nhỏ nở chậm, càng về sau càng nở to uy nghi
                    coreRadius = MathHelper.lerp((float) Math.pow(grow, 0.92), 0.15f, 5.2f);
                    lensingPower = MathHelper.lerp(grow, 0.4f, 3.2f);
                    shockwaveRadius = 0.0f;
                    distortionPower = 0.0f;
                    zoomBlur = -0.015f * grow;
                }
                // Ticks 341..360 (progress 0.944 -> 1.0): Chân không im lặng (Vacuum Silence) & Tích tụ năng lượng cực hạn trước nổ
                else {
                    coreRadius = 5.2f + 0.05f * (float) Math.sin(gTime * 40.0);
                    lensingPower = 5.5f;
                    zoomBlur = -0.075f;
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
                centerX = MathHelper.clamp(projX, -2.0f, 3.0f);
                centerY = MathHelper.clamp(projY, -2.0f, 3.0f);
            } else {
                // Khi vụ nổ ở sau lưng người chơi: trượt tâm ra xa khỏi màn hình thay vì nhảy về giữa tâm ngắm
                float signX = (local.x >= 0.0f) ? 1.0f : -1.0f;
                float signY = (local.y >= 0.0f) ? -1.0f : 1.0f;
                centerX = 0.5f + signX * 5.0f;
                centerY = 0.5f + signY * 5.0f;
            }
        }

        float aspect = (float) fbWidth / (float) fbHeight;

        Camera camera = client.gameRenderer.getCamera();
        Vec3d camPos = (camera != null) ? camera.getPos() : Vec3d.ZERO;
        Matrix4f invProj = new Matrix4f(context.projectionMatrix()).invert();
        Matrix4f invView = new Matrix4f(context.positionMatrix()).invert();

        for (PostEffectPass pass : ((PostEffectProcessorAccessor) postProcessor).getPasses()) {
            JsonEffectShaderProgram prog = pass.getProgram();
            prog.getUniformByNameOrDummy("InvProjMat").set(invProj);
            prog.getUniformByNameOrDummy("InvViewMat").set(invView);
            prog.getUniformByNameOrDummy("CameraPos").set((float) camPos.x, (float) camPos.y, (float) camPos.z);
            prog.getUniformByNameOrDummy("BlastCenter").set((float) blastCenter.x, (float) blastCenter.y, (float) blastCenter.z);
            prog.getUniformByNameOrDummy("CoreRadius").set(coreRadius);
            prog.getUniformByNameOrDummy("LensingPower").set(lensingPower);
            prog.getUniformByNameOrDummy("FxType").set((float) fxType);
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
        postProcessor.setUniforms("FxType", (float) fxType);

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        postProcessor.render(tickDelta);
    }
}
