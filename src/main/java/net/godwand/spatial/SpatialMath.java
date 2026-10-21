package net.godwand.spatial;

import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
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
     * CHIÊU 1: TRƯỜNG LỰC VÒM MA PHÁP KHÍ ĐỘNG HỌC (Aerodynamic Living Aura)
     * Đẩy dạt các khối ra vỏ cầu 4.8m quanh người chơi, uốn cong thành cổng vòm phía trên đầu
     * và trôi nổi nhịp nhàng theo pha sóng Sine.
     */
    public static Vec3d computeDynamicAuraOffset(BlockPos pos, Vec3d playerPos, Vec3d lookDirHoriz, double auraRadius, int ageTicks) {
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        Vec3d fromPlayer = blockCenter.subtract(playerPos);

        double horizDist = Math.sqrt(fromPlayer.x * fromPlayer.x + fromPlayer.z * fromPlayer.z);
        double hash = hash01(pos);
        double phase = hash * Math.PI * 2.0 + ageTicks * 0.14;

        // Tính hình chiếu dọc theo trục nhìn để tách sang 2 bên trái/phải mượt mà
        double forwardProj = fromPlayer.x * lookDirHoriz.x + fromPlayer.z * lookDirHoriz.z;
        Vec3d spinePoint = new Vec3d(
            playerPos.x + lookDirHoriz.x * MathHelper.clamp(forwardProj, -1.5, 4.5),
            blockCenter.y,
            playerPos.z + lookDirHoriz.z * MathHelper.clamp(forwardProj, -1.5, 4.5)
        );

        Vec3d radial = blockCenter.subtract(spinePoint);
        double perpDist = Math.sqrt(radial.x * radial.x + radial.z * radial.z);

        Vec3d pushDir;
        if (perpDist < 0.08) {
            boolean goRight = ((pos.getX() + pos.getZ()) & 1) == 0;
            pushDir = goRight
                ? new Vec3d(-lookDirHoriz.z, 0, lookDirHoriz.x)
                : new Vec3d(lookDirHoriz.z, 0, -lookDirHoriz.x);
        } else {
            pushDir = new Vec3d(radial.x / perpDist, 0, radial.z / perpDist);
        }

        // Bán kính vòm mục tiêu (4.5m -> 5.3m)
        double targetShellRadius = auraRadius + 0.8 + Math.sin(phase) * 0.35 + hash * 0.65;
        double pushOutDist = Math.max(1.75, targetShellRadius - perpDist);

        // Chuyển động quỹ đạo tiếp tuyến nhẹ quanh người chơi
        Vec3d tangent = new Vec3d(-pushDir.z, 0, pushDir.x).multiply(0.55 * Math.sin(phase * 0.65));

        // Nâng cao tạo mái vòm (block càng gần trục giữa càng được nâng cao vượt qua đầu người chơi)
        double archFactor = Math.max(0.0, 1.0 - (perpDist / (auraRadius + 1.2)));
        double liftY = 0.95 + archFactor * 2.15 + Math.sin(phase) * 0.38 + hash * 0.65;

        return new Vec3d(
            pushDir.x * pushOutDist + tangent.x,
            liftY,
            pushDir.z * pushOutDist + tangent.z
        );
    }

    public static Vec3d computeAuraDisplacement(BlockPos pos, Vec3d playerPos, Vec3d lookDirHoriz, double auraRadius) {
        return computeDynamicAuraOffset(pos, playerPos, lookDirHoriz, auraRadius, 0);
    }

    /**
     * PHẢN XẠ NÉ NGƯỜI CHƠI TỨC THỜI (Interactive Living Debris Evasion):
     * Tính trên tọa độ thực tế đang lơ lửng của mảnh vỡ. Khi người chơi tiến sát (< 3.4m),
     * mảnh vỡ tự động dạt mạnh ra xa và nhấc bổng lên.
     */
    public static Vec3d computeInteractiveEvasion(Vec3d actualDebrisWorldPos, Vec3d playerChestPos, double evasionRadius) {
        Vec3d diff = actualDebrisWorldPos.subtract(playerChestPos);
        double dist = diff.length();
        if (dist >= evasionRadius || dist < 0.001) {
            return Vec3d.ZERO;
        }
        double factor = (evasionRadius - dist) / evasionRadius;
        double strength = factor * factor * 4.2;
        Vec3d dir = diff.normalize();
        return new Vec3d(
            dir.x * strength,
            Math.max(0.55, dir.y * strength + 0.75 * factor),
            dir.z * strength
        );
    }

    /**
     * CHIÊU 2: LỐC XOÁY PHỄU THIÊN THẠCH 3 TẦNG (Multi-Tier Tornado Funnel Vortex)
     * Phân bổ các mảnh vỡ từ ngay vành miệng hố (+1.8m) xoắn ốc lên bầu trời (+13.5m)
     * để người chơi nhìn thấy hàng trăm khối đá bay lơ lửng ngay trước tầm mắt.
     */
    public static Vec3d computeCanopyVortexOffset(BlockPos pos, Vec3d blastCenter, double blastRadius, int ageTicks) {
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        double dx = blockCenter.x - blastCenter.x;
        double dz = blockCenter.z - blastCenter.z;
        double hash = hash01(pos);
        double hash2 = hash01(pos.up(7));

        double baseAngle = Math.atan2(dz, dx);
        // Tầng thấp xoay nhanh, tầng cao xoay chậm tạo hiệu ứng lốc xoáy thực tế
        double angularSpeed = (0.032 + 0.038 * (1.0 - hash)) * (((pos.getY() & 1) == 0) ? 1.0 : -0.85);
        double currentAngle = baseAngle + ageTicks * angularSpeed;

        // Hình phễu: Càng lên cao bán kính vòng xoáy càng mở rộng (từ 0.45R ở đáy phễu lên 1.25R ở đỉnh phễu)
        double heightTier = hash; // 0.0 (thấp ngang tầm mắt) -> 1.0 (vòm cao trên bầu trời)
        double funnelRadius = blastRadius * (0.45 + 0.80 * heightTier) + Math.sin(ageTicks * 0.1 + hash * 6.28) * 0.75;

        double targetWorldX = blastCenter.x + Math.cos(currentAngle) * funnelRadius;
        double targetWorldZ = blastCenter.z + Math.sin(currentAngle) * funnelRadius;

        // Độ cao: Bắt đầu từ +1.8m phía trên tâm nổ kéo dài lên +(4.5 + 0.75 * R) mét
        double minHoverY = blastCenter.y + 1.8;
        double maxHoverY = blastCenter.y + 5.0 + blastRadius * 0.75;
        double targetWorldY = MathHelper.lerp(heightTier, minHoverY, maxHoverY)
                + Math.sin(ageTicks * 0.14 + hash2 * 6.28) * 0.55;

        return new Vec3d(
            targetWorldX - blockCenter.x,
            targetWorldY - blockCenter.y,
            targetWorldZ - blockCenter.z
        );
    }

    /**
     * ĐƯỜNG CONG THU HỒI XOẮN ỐC (Spiral Ease-In Reassembly):
     * Tạo điểm trung gian xoáy nhẹ trước khi khóa từ tính về (0,0,0).
     */
    public static Vec3d computeSpiralReturnOffset(Vec3d currentOffset, BlockPos pos) {
        double angle = 0.55 * (((pos.asLong() & 1) == 0) ? 1.0 : -1.0);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double rx = (currentOffset.x * cos - currentOffset.z * sin) * 0.22;
        double rz = (currentOffset.x * sin + currentOffset.z * cos) * 0.22;
        double ry = currentOffset.y * 0.18 + 0.25;
        return new Vec3d(rx, ry, rz);
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
