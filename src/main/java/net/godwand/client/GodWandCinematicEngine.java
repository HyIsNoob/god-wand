package net.godwand.client;

import net.godwand.network.CinematicFxPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Bộ điều khiển Camera Điện Ảnh & Âm Thanh phía Client:
 * - Trauma Screen Shake (Mô hình Trauma² kết hợp hàm sóng đa tần số)
 * - Dynamic FOV Zoom (Nội suy co/bung góc nhìn theo từng pha kỹ năng)
 * - Vacuum Silence (Tắt toàn bộ âm thanh môi trường tạo khoảng lặng trước vụ nổ)
 */
public class GodWandCinematicEngine {

    // ==========================================
    // 1. TRAUMA SCREEN SHAKE
    // ==========================================
    private static float trauma = 0.0f;
    private static float traumaDecay = 0.045f;
    private static float shakeTime = 0.0f;

    // ==========================================
    // 2. DYNAMIC FOV ZOOM
    // ==========================================
    private static float targetFovMultiplier = 1.0f;
    private static float currentFovMultiplier = 1.0f;
    private static float prevFovMultiplier = 1.0f;
    private static int fovHoldTicks = 0;
    private static float fovRecoverySpeed = 0.08f;

    // ==========================================
    // 3. VACUUM SILENCE (AUDIO DUCKING)
    // ==========================================
    private static int vacuumSilenceTicks = 0;

    /**
     * Kích hoạt sự kiện điện ảnh từ gói mạng CinematicFxPayload
     */
    public static void onCinematicEvent(CinematicFxPayload payload) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;

        // Tính toán suy giảm chấn động theo khoảng cách 3D từ tâm nổ tới người chơi
        double distSq = client.player.squaredDistanceTo(payload.x(), payload.y(), payload.z());
        float distFactor = 1.0f;
        if (payload.x() != 0 || payload.y() != 0 || payload.z() != 0) {
            double dist = Math.sqrt(distSq);
            double maxDist = 64.0;
            if (dist > maxDist) {
                distFactor = 0.0f;
            } else if (dist > 8.0) {
                distFactor = (float) (1.0 - ((dist - 8.0) / (maxDist - 8.0)));
            }
        }

        switch (payload.eventType()) {
            case CinematicFxPayload.EVENT_TRAUMA_SHAKE -> {
                addTrauma(payload.intensity() * distFactor, payload.durationTicks());
            }
            case CinematicFxPayload.EVENT_FOV_PUNCH -> {
                setFovPunch(payload.intensity(), payload.durationTicks());
            }
            case CinematicFxPayload.EVENT_VACUUM_SILENCE -> {
                // Khoảng lặng chân không kích hoạt nếu người chơi ở trong bán kính ảnh hưởng
                if (distFactor > 0.1f) {
                    vacuumSilenceTicks = Math.max(vacuumSilenceTicks, payload.durationTicks());
                }
            }
            case CinematicFxPayload.EVENT_METEOR_FOCUS -> {
                // Chiêu 3: Co FOV lại để tạo cảm giác tập trung năng lượng cực hạn
                if (distFactor > 0.2f) {
                    targetFovMultiplier = payload.intensity(); // ~0.88f
                    fovHoldTicks = payload.durationTicks();
                    fovRecoverySpeed = 0.12f;
                }
            }
            case CinematicFxPayload.EVENT_METEOR_SLAM -> {
                // Chiêu 3: Bung cực đại FOV + rung chấn dữ dội khi thiên thạch cắm đất
                addTrauma(payload.intensity() * distFactor, payload.durationTicks());
                setFovPunch(1.0f + 0.25f * distFactor, 14);
            }
            case CinematicFxPayload.EVENT_SUPERNOVA_COMPRESS -> {
                // Chiêu 4: Sụp đổ hấp dẫn - Rung nhịp tim dồn dập
                addTrauma(0.45f * distFactor, payload.durationTicks());
                targetFovMultiplier = 0.92f;
                fovHoldTicks = payload.durationTicks();
                fovRecoverySpeed = 0.06f;
            }
            case CinematicFxPayload.EVENT_SUPERNOVA_BURST -> {
                // Chiêu 4: Đại nổ siêu tân tinh - Rung chấn cực đại + bung FOV toàn cảnh
                addTrauma(payload.intensity() * distFactor, payload.durationTicks());
                setFovPunch(1.0f + 0.32f * distFactor, 24);
            }
        }
    }

    public static void addTrauma(float amount, int durationTicks) {
        trauma = MathHelper.clamp(trauma + amount, 0.0f, 1.0f);
        if (durationTicks > 0) {
            traumaDecay = Math.max(0.015f, 1.0f / (float) durationTicks);
        }
    }

    public static void setFovPunch(float multiplier, int holdTicks) {
        targetFovMultiplier = multiplier;
        currentFovMultiplier = multiplier;
        fovHoldTicks = holdTicks;
        fovRecoverySpeed = 0.065f;
    }

    public static void setVacuumSilence(int ticks) {
        vacuumSilenceTicks = Math.max(0, ticks);
    }

    /**
     * Cập nhật logic mỗi Client Tick
     */
    public static void onClientTick() {
        // 1. Cập nhật Trauma suy giảm dần
        if (trauma > 0.0f) {
            trauma = Math.max(0.0f, trauma - traumaDecay);
            shakeTime += 0.85f;
        }

        // 2. Cập nhật Dynamic FOV
        prevFovMultiplier = currentFovMultiplier;
        if (fovHoldTicks > 0) {
            fovHoldTicks--;
        } else {
            // Hồi phục dần về trạng thái bình thường (1.0f)
            currentFovMultiplier = MathHelper.lerp(fovRecoverySpeed, currentFovMultiplier, 1.0f);
            if (Math.abs(currentFovMultiplier - 1.0f) < 0.002f) {
                currentFovMultiplier = 1.0f;
                targetFovMultiplier = 1.0f;
            }
        }

        // 3. Cập nhật Vacuum Silence
        if (vacuumSilenceTicks > 0) {
            vacuumSilenceTicks--;
        }
    }

    /**
     * Áp dụng Trauma Screen Shake vào ma trận Camera (Gọi từ GameRendererMixin.tiltViewWhenHurt)
     */
    public static void applyScreenShake(MatrixStack matrices, float tickDelta) {
        if (trauma <= 0.001f) return;

        // Mô hình phi tuyến tính Trauma² giúp rung chấn mạnh mẽ, gãy gọn ở mức cao
        float intensity = trauma * trauma;
        float t = shakeTime + tickDelta * 0.85f;

        // Sóng điều hòa đa tần số tạo cảm giác rung lắc bất quy tắc tự nhiên
        float pitch = (float) (Math.sin(t * 1.6) * Math.cos(t * 0.7) * 4.2) * intensity;
        float yaw   = (float) (Math.cos(t * 1.3) * Math.sin(t * 0.9) * 3.8) * intensity;
        float roll  = (float) (Math.sin(t * 2.1) * 2.8) * intensity;

        float transX = (float) (Math.sin(t * 1.8) * 0.065) * intensity;
        float transY = (float) (Math.cos(t * 2.4) * 0.085) * intensity;
        float transZ = (float) (Math.sin(t * 1.1) * 0.045) * intensity;

        matrices.translate(transX, transY, transZ);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
    }

    /**
     * Lấy hệ số co/bung FOV nội suy mượt mà (Gọi từ GameRendererMixin.getFov)
     */
    public static double getFovMultiplier(float tickDelta) {
        float interpolated = MathHelper.lerp(tickDelta, prevFovMultiplier, currentFovMultiplier);
        return (double) interpolated;
    }

    /**
     * Kiểm tra trạng thái câm lặng chân không (Gọi từ SoundEngineMixin.getVolume)
     */
    public static boolean isVacuumSilenceActive() {
        return vacuumSilenceTicks > 0;
    }

    public static void reset() {
        trauma = 0.0f;
        currentFovMultiplier = 1.0f;
        prevFovMultiplier = 1.0f;
        targetFovMultiplier = 1.0f;
        fovHoldTicks = 0;
        vacuumSilenceTicks = 0;
    }
}
