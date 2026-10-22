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

                // 3D Singularity Lõi năng lượng duy trì:
                // Nhịp thở scale dao động rất nhẹ (+- 0.035m), êm ái tự nhiên không giật mạnh
                float pulse = (float) (Math.sin(gTime * 8.0) * 0.035);
                float activeCoreR = Math.max(0.40f, 0.65f + pulse);
                float activeLensing = 0.55f + pulse * 0.2f;

                if (progress < 0.80f) {
                    coreRadius = activeCoreR;
                    lensingPower = activeLensing;
                } else {
                    // Ticks 106..131: Hút ngược chân không sụp đổ lõi
                    float snapProg = MathHelper.clamp((progress - 0.80f) / 0.20f, 0.0f, 1.0f);
                    coreRadius = MathHelper.lerp(snapProg, activeCoreR, 0.0f);
                    lensingPower = MathHelper.lerp(snapProg, activeLensing, 0.0f);
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
                // Cataclysmic Cosmic Supernova (Burst & Lingering Mushroom Aftermath - 140 ticks / 7.0s duration):
                // Các bức vẽ tay Anime Impact Frame (Snapshot 1..4) được render sắc nét qua CustomImpactFrameOverlay!
                impactFrame = 0.0f;

                // Không lạm dụng Distortion bẻ méo màn hình gây khó chịu cho người chơi
                shockwaveRadius = 0.0f;
                distortionPower = 0.0f;
                chromaticAmount = 0.035f * baseIntensity * (1.0f - progress);

                // Bỏ hoàn toàn retinaBurn và flash chớp nháy giật cục, để CustomImpactFrameOverlay xử lý fadeout trắng điện ảnh
                zoomBlur = 0.0f;
                retinaBurn = 0.0f;

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
                // Suốt từ progress 0.0 đến 1.0 (Ticks 1..340): Hố Đen to dần ĐƠN ĐIỆU từ 0.15m lên 5.2m cực đại
                coreRadius = MathHelper.lerp((float) Math.pow(progress, 0.95), 0.15f, 5.2f);
                lensingPower = MathHelper.lerp(progress, 0.4f, 3.6f);
                shockwaveRadius = 0.0f;
                distortionPower = 0.0f;
                zoomBlur = -0.025f * progress;
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
                    impactFrame = 1.0f - ((float) tickIdx / 4.0f);
                    impactMode = (tickIdx % 2 == 0) ? 33.0f : 0.0f;
                } else {
                    impactFrame = 0.0f;
                }
                // 2. Chớp trắng lóa mắt Retinal Flash nung chảy mối nối không gian
                float flash = (1.0f - progress) * baseIntensity;
                retinaBurn = flash * 0.92f;
                chromaticAmount = flash * 0.035f;
                zoomBlur = 0.0f; // Tuyệt đối không nhòe
                distortionPower = 0.0f; // Bỏ shockwave méo hình lúc thu về
                shockwaveRadius = 0.0f;
                coreRadius = 0.0f;
                lensingPower = 0.0f;
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
