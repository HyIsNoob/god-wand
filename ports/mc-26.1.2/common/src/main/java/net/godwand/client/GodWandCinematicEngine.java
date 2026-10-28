package net.godwand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.godwand.network.CinematicFxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

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
    private static float environmentGain = 1;

    /**
     * Kích hoạt sự kiện điện ảnh từ gói mạng CinematicFxPayload
     */
    public static void onCinematicEvent(CinematicFxPayload payload) {
        float distFactor = 1.0f;
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null) {
            // Tính toán suy giảm chấn động theo khoảng cách 3D từ tâm nổ tới người chơi
            double distSq = client.player.distanceToSqr(payload.x(), payload.y(), payload.z());
            if (payload.x() != 0 || payload.y() != 0 || payload.z() != 0) {
                double dist = Math.sqrt(distSq);
                distFactor = distanceFactor(payload.eventType(), dist);
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
                    setFovFocus(payload.intensity(), payload.durationTicks(), 0.12f);
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
                setFovFocus(0.92f, payload.durationTicks(), 0.06f);
            }
            case CinematicFxPayload.EVENT_SUPERNOVA_BURST -> {
                vacuumSilenceTicks = 0;
                environmentGain = 1;
                // One short impact; sustained camera oscillation obscures the expanding sphere.
                addTrauma(0.55f * distFactor, 8);
                setFovPunch(1.0f + 0.12f * distFactor, 8);
            }
        }
    }

    public static float distanceFactor(int eventType, double distance) {
        boolean supernova = eventType == CinematicFxPayload.EVENT_SUPERNOVA_COMPRESS
                || eventType == CinematicFxPayload.EVENT_SUPERNOVA_BURST;
        double near = supernova ? 64 : 8;
        double far = supernova ? 250 : 64;
        return (float) Mth.clamp(1 - (distance - near) / (far - near), 0, 1);
    }

    public static void setFovFocus(float targetMultiplier, int holdTicks, float recoverySpeed) {
        targetFovMultiplier = targetMultiplier;
        fovHoldTicks = holdTicks;
        fovRecoverySpeed = recoverySpeed;
    }

    public static void addTrauma(float amount, int durationTicks) {
        trauma = Mth.clamp(trauma + amount, 0.0f, 1.0f);
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

        var client = Minecraft.getInstance();
        double novaAge = SupernovaAccretionRenderer.age(0);
        float charge = 0;
        if (client != null && client.player != null && client.player.isUsingItem()) {
            var stack = client.player.getUseItem();
            if (stack.getItem() instanceof net.godwand.item.GodWandItem
                    && net.godwand.item.GodWandItem.getSkillMode(stack) == net.godwand.item.GodWandItem.MODE_SUPERNOVA)
                charge = Math.clamp(client.player.getTicksUsingItem() / 140f, 0, 1);
        }
        float distance = novaAge >= 0 && client != null && client.player != null
                ? distanceFactor(CinematicFxPayload.EVENT_SUPERNOVA_COMPRESS, client.player.position().distanceTo(SupernovaAccretionRenderer.center())) : 1;
        float targetEnvironment = 1;
        if (novaAge >= 0) {
            float pressure = net.godwand.skill.SupernovaSequence.pressure(novaAge);
            setFovFocus(1 + (net.godwand.skill.SupernovaSequence.cameraFov(novaAge) - 1) * (distance > 0 ? 0.45f + distance * 0.55f : 0), 2, 0.08f);
            if (novaAge < net.godwand.skill.SupernovaSequence.QUIET_START)
                trauma = Math.max(trauma, (0.06f + pressure * 0.20f) * distance);
            else { trauma *= 0.5f; vacuumSilenceTicks = 2; }
            targetEnvironment = 1 - (1 - net.godwand.skill.SupernovaSequence.environmentGain(novaAge)) * distance;
        } else if (charge > 0) {
            setFovFocus(1 - charge * charge * 0.06f, 2, 0.08f);
            trauma = Math.max(trauma, 0.04f * charge);
        }
        if (vacuumSilenceTicks > 0) targetEnvironment = Math.min(targetEnvironment, 0.04f);
        environmentGain = Mth.lerp(0.22f, environmentGain, targetEnvironment);
        SupernovaSoundscape.tick(novaAge, charge, distance);

        // 2. Cập nhật Dynamic FOV
        prevFovMultiplier = currentFovMultiplier;
        if (fovHoldTicks > 0) {
            fovHoldTicks--;
            // Nội suy mượt từ FOV hiện tại về targetFovMultiplier trong lúc nén lực
            currentFovMultiplier = Mth.lerp(0.25f, currentFovMultiplier, targetFovMultiplier);
        } else {
            // Hồi phục dần về trạng thái bình thường (1.0f)
            currentFovMultiplier = Mth.lerp(fovRecoverySpeed, currentFovMultiplier, 1.0f);
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
    public static void applyScreenShake(PoseStack matrices, float tickDelta) {
        if (trauma <= 0.001f) return;

        // Mô hình phi tuyến tính Trauma² giúp rung chấn mạnh mẽ, gãy gọn ở mức cao
        float intensity = trauma * trauma * net.godwand.config.GodWandConfig.CLIENT.cameraShake;
        float t = shakeTime + tickDelta * 0.85f;

        // Sóng điều hòa đa tần số tạo cảm giác rung lắc bất quy tắc tự nhiên
        float pitch = (float) (Math.sin(t * 1.6) * Math.cos(t * 0.7) * 4.2) * intensity;
        float yaw   = (float) (Math.cos(t * 1.3) * Math.sin(t * 0.9) * 3.8) * intensity;
        float roll  = (float) (Math.sin(t * 2.1) * 2.8) * intensity;

        float transX = (float) (Math.sin(t * 1.8) * 0.065) * intensity;
        float transY = (float) (Math.cos(t * 2.4) * 0.085) * intensity;
        float transZ = (float) (Math.sin(t * 1.1) * 0.045) * intensity;

        matrices.translate(transX, transY, transZ);
        matrices.rotate(Axis.ZP.rotationDegrees(roll));
        matrices.rotate(Axis.XP.rotationDegrees(pitch));
        matrices.rotate(Axis.YP.rotationDegrees(yaw));
    }

    /**
     * Lấy hệ số co/bung FOV nội suy mượt mà (Gọi từ GameRendererMixin.getFov)
     */
    public static double getFovMultiplier(float tickDelta) {
        float interpolated = Mth.lerp(tickDelta, prevFovMultiplier, currentFovMultiplier);
        return net.godwand.config.GodWandConfig.CLIENT.cameraFov ? (double) interpolated : 1.0;
    }

    /**
     * Kiểm tra trạng thái câm lặng chân không (Gọi từ SoundEngineMixin.getVolume)
     */
    public static float getEnvironmentGain() { return Math.min(environmentGain, AtomicCinematic.environmentGain()); }

    public static boolean isVacuumSilenceActive() {
        return vacuumSilenceTicks > 0;
    }

    public static void reset() {
        SupernovaSoundscape.reset();
        environmentGain = 1;
        trauma = 0.0f;
        currentFovMultiplier = 1.0f;
        prevFovMultiplier = 1.0f;
        targetFovMultiplier = 1.0f;
        fovHoldTicks = 0;
        vacuumSilenceTicks = 0;
    }
}
