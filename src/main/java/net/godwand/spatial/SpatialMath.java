package net.godwand.spatial;

import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SpatialMath {
    private static final Vector3f PIVOT = new Vector3f(0.5f, 0.5f, 0.5f);

    private SpatialMath() {}

    public static AffineTransformation createPivotTransform(Vec3d offset, Quaternionf rotation, float scale) {
        Vector3f center = new Vector3f(0.5f, 0.5f, 0.5f);
        Vector3f scaledRotatedCenter = new Vector3f(center).mul(scale).rotate(rotation);
        Vector3f translation = new Vector3f(
            (float) offset.x + center.x - scaledRotatedCenter.x,
            (float) offset.y + center.y - scaledRotatedCenter.y,
            (float) offset.z + center.z - scaledRotatedCenter.z
        );
        return new AffineTransformation(
            translation,
            rotation,
            new Vector3f(scale, scale, scale),
            null
        );
    }

    public static AffineTransformation createPivotTransform(Vector3f offset, Quaternionf rotation, float scale) {
        return createPivotTransform(new Vec3d(offset.x, offset.y, offset.z), rotation, scale);
    }

    public static Vector3f compensatePivot(Vector3f displacement, Quaternionf rotation) {
        Vector3f rotatedPivot = new Vector3f(PIVOT);
        rotation.transform(rotatedPivot);

        Vector3f result = new Vector3f(displacement);
        result.add(PIVOT);
        result.sub(rotatedPivot);
        return result;
    }

    /**
     * TÍNH TRƯỜNG LỰC VÒM AURA ĐỘNG (Skill 1 - Dynamic Spherical Repulsion):
     * Đẩy mọi block trong vùng quanh người chơi dạt ra bề mặt vỏ cầu bán kính shellRadius (4.8m),
     * tạo thành một vòm đá lơ lửng sống động luôn né người chơi theo thời gian thực.
     */
    public static Vec3d computeDynamicAuraOffset(BlockPos pos, Vec3d playerPos, Vec3d lookDirHoriz, double auraRadius, int ageTicks) {
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        Vec3d fromPlayer = blockCenter.subtract(playerPos);

        double horizDist = Math.sqrt(fromPlayer.x * fromPlayer.x + fromPlayer.z * fromPlayer.z);
        double hash = hash01(pos);
        double phase = hash * Math.PI * 2.0 + ageTicks * 0.12;

        // Hướng đẩy ngang từ tâm người chơi ra ngoài vòm Aura
        Vec3d radialHoriz;
        if (horizDist < 0.08) {
            boolean goRight = ((pos.getX() + pos.getZ()) & 1) == 0;
            radialHoriz = goRight
                ? new Vec3d(-lookDirHoriz.z, 0, lookDirHoriz.x)
                : new Vec3d(lookDirHoriz.z, 0, -lookDirHoriz.x);
        } else {
            radialHoriz = new Vec3d(fromPlayer.x / horizDist, 0, fromPlayer.z / horizDist);
        }

        // Đẩy block ra sát vành đai vỏ Aura (bán kính mục tiêu 4.6m - 5.4m quanh người chơi)
        double targetShellRadius = auraRadius + 1.0 + Math.sin(phase) * 0.35 + hash * 0.6;
        double pushOutDist = Math.max(1.6, targetShellRadius - horizDist);

        // Xoáy nhẹ tiếp tuyến quanh người chơi tạo cảm giác trường năng lượng sống
        Vec3d tangent = new Vec3d(-radialHoriz.z, 0, radialHoriz.x).multiply(0.45 * Math.sin(phase * 0.7));

        // Độ nâng vòm (càng gần người chơi càng được nâng cao lên tạo mái vòm)
        double proximityFactor = Math.max(0.0, 1.0 - (horizDist / (auraRadius + 1.5)));
        double liftY = 0.85 + proximityFactor * 1.85 + Math.sin(phase) * 0.35 + hash * 0.75;

        double targetWorldX = blockCenter.x + radialHoriz.x * pushOutDist + tangent.x;
        double targetWorldY = blockCenter.y + liftY;
        double targetWorldZ = blockCenter.z + radialHoriz.z * pushOutDist + tangent.z;

        return new Vec3d(
            targetWorldX - blockCenter.x,
            targetWorldY - blockCenter.y,
            targetWorldZ - blockCenter.z
        );
    }

    public static Vec3d computeAuraDisplacement(BlockPos pos, Vec3d playerPos, Vec3d lookDirHoriz, double auraRadius) {
        return computeDynamicAuraOffset(pos, playerPos, lookDirHoriz, auraRadius, 0);
    }

    /**
     * LỰC NÉ TỨC THỜI KHI NGƯỜI CHƠI LẠI GẦN MẢNH VỠ ĐANG BAY (Interactive Evasion):
     */
    public static Vec3d computeInteractiveEvasion(Vec3d currentDebrisWorldPos, Vec3d playerChestPos, double evasionRadius) {
        Vec3d diff = currentDebrisWorldPos.subtract(playerChestPos);
        double dist = diff.length();
        if (dist >= evasionRadius || dist < 0.001) {
            return Vec3d.ZERO;
        }
        double factor = (evasionRadius - dist) / evasionRadius;
        double strength = factor * factor * 3.8;
        Vec3d dir = diff.normalize();
        return new Vec3d(dir.x * strength, Math.max(0.45, dir.y * strength + 0.65), dir.z * strength);
    }

    /**
     * QUỸ ĐẠO LỐC XOÁY THIÊN THẠCH TRÊN BẦU TRỜI (Skill 2 - Canopy Vortex):
     * Kéo toàn bộ các block bị bóc tách bay vọt lên không trung (từ +6.0m đến +16.5m phía trên tâm nổ)
     * và xoay thành 3 tầng vành đai thiên thạch lơ lửng ngay trên miệng hố.
     */
    public static Vec3d computeCanopyVortexOffset(BlockPos pos, Vec3d blastCenter, double blastRadius, int ageTicks) {
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        double dx = blockCenter.x - blastCenter.x;
        double dz = blockCenter.z - blastCenter.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        double hash = hash01(pos);

        double baseAngle = Math.atan2(dz, dx);
        double angularSpeed = 0.045 * (0.8 + hash * 0.7);
        double currentAngle = baseAngle + ageTicks * angularSpeed;

        // Gom các mảnh vỡ vào vành đai lốc xoáy ngay phía trên miệng hố (bán kính từ 0.35R đến 1.05R)
        double ringBand = (0.35 + 0.70 * hash) * blastRadius;
        double targetRingRadius = Math.max(2.2, ringBand + Math.sin(ageTicks * 0.08 + hash * 6.28) * 0.6);

        double targetWorldX = blastCenter.x + Math.cos(currentAngle) * targetRingRadius;
        double targetWorldZ = blastCenter.z + Math.sin(currentAngle) * targetRingRadius;

        // Bắt buộc nâng cao hơn đỉnh miệng hố (blastCenter.y + blastRadius * 0.6) để 100% block bay trên trời
        double skyApexBase = blastCenter.y + Math.max(5.5, blastRadius * 0.65);
        double targetWorldY = skyApexBase + (hash * (4.5 + blastRadius * 0.45)) + Math.sin(ageTicks * 0.12 + hash * 6.28) * 0.45;

        return new Vec3d(
            targetWorldX - blockCenter.x,
            targetWorldY - blockCenter.y,
            targetWorldZ - blockCenter.z
        );
    }

    public static double hash01(BlockPos pos) {
        long h = pos.asLong() * 0x9E3779B97F4A7C15L;
        h ^= (h >>> 30);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27);
        return ((h & 0xFFFFFFL) / (double) 0x1000000L);
    }

    public static double hashCoordinateToPhase(long posAsLong) {
        long h = posAsLong * 0x5deece66dL + 0xbL;
        return ((h >> 16) & 0xFFFF) / 65535.0 * 2.0 * Math.PI;
    }
}
