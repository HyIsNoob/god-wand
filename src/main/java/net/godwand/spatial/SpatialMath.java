package net.godwand.spatial;

import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SpatialMath {
    private static final Vector3f PIVOT = new Vector3f(0.5f, 0.5f, 0.5f);

    private SpatialMath() {}

    /**
     * 1. Bù trừ tâm xoay (0.5, 0.5, 0.5) để BlockDisplayEntity xoay quanh đúng trọng tâm khối
     * thay vì bị lắc lệch quanh góc tọa độ (0, 0, 0).
     */
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
     * 2. Tính vector đẩy Aura (Skill 1 - SpatialPathWalker):
     * Kết hợp lực đẩy cầu xung quanh người chơi + rẻ quạt mở đường + nâng lơ lửng + xoáy ma pháp.
     */
    public static Vec3d computeAuraDisplacement(BlockPos pos, Vec3d playerPos, Vec3d lookDirHoriz, double auraRadius) {
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        Vec3d rel = blockCenter.subtract(playerPos);

        double forwardProj = rel.x * lookDirHoriz.x + rel.z * lookDirHoriz.z;
        double clampedProj = Math.max(0.0, forwardProj);
        Vec3d spinePoint = new Vec3d(
            playerPos.x + lookDirHoriz.x * clampedProj,
            blockCenter.y,
            playerPos.z + lookDirHoriz.z * clampedProj
        );

        Vec3d radial = blockCenter.subtract(spinePoint);
        double dist = Math.sqrt(radial.x * radial.x + radial.z * radial.z);

        Vec3d pushDir;
        if (dist < 0.05) {
            // Nếu block nằm chính giữa trục đi, tách đều sang trái hoặc phải theo tọa độ
            boolean goRight = ((pos.getX() + pos.getZ()) & 1) == 0;
            pushDir = goRight
                ? new Vec3d(-lookDirHoriz.z, 0, lookDirHoriz.x)
                : new Vec3d(lookDirHoriz.z, 0, -lookDirHoriz.x);
        } else {
            pushDir = new Vec3d(radial.x / dist, 0, radial.z / dist);
        }

        double pushDistance = Math.max(1.8, (auraRadius - dist) + 2.2);
        double hashPhase = hash01(pos) * Math.PI * 2.0;
        double liftY = 0.65 + Math.sin(hashPhase) * 0.55 + (auraRadius - Math.min(dist, auraRadius)) * 0.35;

        // Thành phần xoáy tiếp tuyến
        Vec3d tangent = new Vec3d(-pushDir.z, 0, pushDir.x).multiply(0.45 * Math.cos(hashPhase));

        return new Vec3d(
            pushDir.x * pushDistance + tangent.x,
            liftY,
            pushDir.z * pushDistance + tangent.z
        );
    }

    /**
     * 3. Tính lực né động (Interactive Secondary Evasion):
     * Khi mảnh vỡ đang bay trên không mà người chơi lại gần (< 2.8m), mảnh vỡ tự giật lùi né tiếp.
     */
    public static Vec3d computeInteractiveEvasion(Vec3d currentDebrisWorldPos, Vec3d playerChestPos, double evasionRadius) {
        Vec3d diff = currentDebrisWorldPos.subtract(playerChestPos);
        double dist = diff.length();
        if (dist >= evasionRadius || dist < 0.001) {
            return Vec3d.ZERO;
        }
        double factor = (evasionRadius - dist) / evasionRadius;
        double strength = factor * factor * 3.2;
        Vec3d dir = diff.normalize();
        return new Vec3d(dir.x * strength, Math.max(0.25, dir.y * strength + 0.4), dir.z * strength);
    }

    /**
     * 4. Tính quỹ đạo Vòm Thiên Thạch Lơ Lửng (Skill 2 - Singularity Orb):
     * Bắt buộc bốc toàn bộ đất đá dưới đáy hố lên cao hơn miệng hố từ 3.5m đến 9.5m.
     */
    public static Vec3d computeCanopyVortexOffset(BlockPos pos, Vec3d blastCenter, double blastRadius, int ageTicks) {
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        double dx = blockCenter.x - blastCenter.x;
        double dz = blockCenter.z - blastCenter.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        double baseAngle = Math.atan2(dz, dx);
        double angularSpeed = 0.035 * (1.0 + hash01(pos) * 0.5);
        double currentAngle = baseAngle + ageTicks * angularSpeed;

        double targetRingRadius = Math.max(blastRadius * 0.65, horizDist + 2.5 + hash01(pos) * 3.0);
        double targetWorldX = blastCenter.x + Math.cos(currentAngle) * targetRingRadius;
        double targetWorldZ = blastCenter.z + Math.sin(currentAngle) * targetRingRadius;

        // Nâng toàn bộ mảnh vỡ lên bầu trời phía trên miệng hố
        double depthBelowCenter = Math.max(0.0, blastCenter.y - blockCenter.y);
        double liftAboveCrater = depthBelowCenter + 3.8 + hash01(pos) * 5.8 + Math.sin(ageTicks * 0.1 + hash01(pos) * 6.0) * 0.35;

        return new Vec3d(
            targetWorldX - blockCenter.x,
            liftAboveCrater,
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
