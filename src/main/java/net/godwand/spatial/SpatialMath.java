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
     * CHIÊU 1: MIRROR-DIMENSION COHERENT ARCHWAY (Tách cổng vòm gọn & đồng bộ)
     * Các khối tách sang 2 bên theo sóng mượt đồng bộ, tạo hành lang nhỏ gọn bán kính ~2.2m.
     */
    public static Vec3d computeDynamicAuraOffset(BlockPos pos, Vec3d playerPos, Vec3d lookDirHoriz, double auraRadius, int ageTicks) {
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        Vec3d fromPlayer = blockCenter.subtract(playerPos);

        // Hình chiếu dọc theo trục đường đi
        double forwardProj = fromPlayer.x * lookDirHoriz.x + fromPlayer.z * lookDirHoriz.z;
        Vec3d spinePoint = new Vec3d(
            playerPos.x + lookDirHoriz.x * MathHelper.clamp(forwardProj, -1.5, 7.0),
            blockCenter.y,
            playerPos.z + lookDirHoriz.z * MathHelper.clamp(forwardProj, -1.5, 7.0)
        );

        Vec3d radial = blockCenter.subtract(spinePoint);
        double perpDist = Math.sqrt(radial.x * radial.x + radial.z * radial.z);

        // Vector vuông góc sang phải của hướng nhìn
        Vec3d rightVec = new Vec3d(-lookDirHoriz.z, 0, lookDirHoriz.x);
        double sideSign = (radial.x * rightVec.x + radial.z * rightVec.z) >= 0 ? 1.0 : -1.0;

        Vec3d pushDir;
        if (perpDist < 0.15) {
            pushDir = rightVec.multiply(sideSign);
        } else {
            pushDir = new Vec3d(radial.x / perpDist, 0, radial.z / perpDist);
        }

        // Sóng liên kết mượt theo không gian (không dùng nhiễu loạn ngẫu nhiên)
        double spatialWave = Math.sin(pos.getX() * 0.45 + pos.getZ() * 0.45 + ageTicks * 0.12);

        // Đẩy gọn gàng ra vỏ hành lang 2.5m - 3.0m
        double targetCorridorRadius = auraRadius + 0.45 + spatialWave * 0.18;
        double pushOutDist = Math.max(1.15, targetCorridorRadius - perpDist);

        // Độ nâng vòm theo chiều cao tương đối so với chân người chơi
        double relY = Math.max(0.0, blockCenter.y - playerPos.y);
        double archLift = 0.35 + (relY * 0.42) + Math.max(0.0, (auraRadius - perpDist) * 0.35) + spatialWave * 0.15;

        return new Vec3d(
            pushDir.x * pushOutDist,
            archLift,
            pushDir.z * pushOutDist
        );
    }

    /**
     * Tính góc xoay uốn vòm đồng bộ cho Chiêu 1 (Càng lên cao càng uốn mở ra ngoài như cánh cổng)
     */
    public static Quaternionf computeArchwayRotation(BlockPos pos, Vec3d playerPos, Vec3d lookDirHoriz, int ageTicks) {
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        Vec3d rel = blockCenter.subtract(playerPos);
        Vec3d rightVec = new Vec3d(-lookDirHoriz.z, 0, lookDirHoriz.x);
        float side = (rel.x * rightVec.x + rel.z * rightVec.z) >= 0 ? 1.0f : -1.0f;

        float relY = (float) MathHelper.clamp(blockCenter.y - playerPos.y, 0.0, 3.0);
        float peelAngle = (float) Math.toRadians((18.0 + relY * 14.0) * side);
        float wavePitch = (float) Math.sin(ageTicks * 0.1 + (pos.getX() + pos.getZ()) * 0.35) * 0.12f;

        return new Quaternionf()
                .rotateY((float) Math.atan2(lookDirHoriz.x, lookDirHoriz.z))
                .rotateZ(peelAngle)
                .rotateX(wavePitch);
    }

    public static Vec3d computeAuraDisplacement(BlockPos pos, Vec3d playerPos, Vec3d lookDirHoriz, double auraRadius) {
        return computeDynamicAuraOffset(pos, playerPos, lookDirHoriz, auraRadius, 0);
    }

    public static double distanceToAuraSpine(Vec3d blockCenter, Vec3d chestPos, Vec3d lookDirHoriz, double forwardLead) {
        Vec3d rel = blockCenter.subtract(chestPos);
        double proj = rel.x * lookDirHoriz.x + rel.z * lookDirHoriz.z;
        double clampedProj = MathHelper.clamp(proj, 0.0, forwardLead);
        Vec3d closestPoint = new Vec3d(
            chestPos.x + lookDirHoriz.x * clampedProj,
            chestPos.y,
            chestPos.z + lookDirHoriz.z * clampedProj
        );
        return blockCenter.distanceTo(closestPoint);
    }

    public static Vec3d computeInteractiveEvasion(Vec3d actualDebrisWorldPos, Vec3d playerChestPos, double evasionRadius) {
        Vec3d diff = actualDebrisWorldPos.subtract(playerChestPos);
        double dist = diff.length();
        if (dist >= evasionRadius || dist < 0.001) {
            return Vec3d.ZERO;
        }
        double factor = (evasionRadius - dist) / evasionRadius;
        double strength = factor * factor * 2.8;
        Vec3d dir = diff.normalize();
        return new Vec3d(
            dir.x * strength,
            Math.max(0.35, dir.y * strength + 0.45 * factor),
            dir.z * strength
        );
    }

    /**
     * CHIÊU 2: 3 VÀNH ĐAI THIÊN THẠCH NGHIÊNG CHÉO (3-Ring Tilted Gimbal Astrolabe)
     * 3 vành đai nghiêng trên cả 2 trục X và Z tạo thành khối cầu Armillary Sphere 3D,
     * giữ khoảng cách thưa thoáng giữa các viên đá và để lộ tâm vụ nổ.
     */
    public static Vec3d computeCanopyVortexOffset(BlockPos pos, Vec3d blastCenter, double blastRadius, int ageTicks) {
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        double dx = blockCenter.x - blastCenter.x;
        double dz = blockCenter.z - blastCenter.z;
        double hash = hash01(pos);

        int ringIndex = (int) (Math.abs(pos.asLong()) % 3);
        double baseAngle = Math.atan2(dz, dx) + hash * 0.5;
        double angularSpeed = (ringIndex == 1 ? -0.045 : 0.040) * (0.85 + hash * 0.35);
        double theta = baseAngle + ageTicks * angularSpeed;

        double ringRadius = switch (ringIndex) {
            case 0 -> blastRadius * (0.85 + hash * 0.40);
            case 1 -> blastRadius * (0.68 + hash * 0.32);
            default -> blastRadius * (1.02 + hash * 0.42);
        };

        double lx = Math.cos(theta) * ringRadius;
        double lz = Math.sin(theta) * ringRadius;
        double ly = (hash - 0.5) * 1.8 + Math.sin(theta * 2.0 + ageTicks * 0.1) * 0.35;

        double rotX = 0.0;
        double rotZ = 0.0;
        if (ringIndex == 0) {
            rotZ = Math.toRadians(28.0);
        } else if (ringIndex == 1) {
            rotX = Math.toRadians(28.0);
        } else {
            rotX = Math.toRadians(-22.0);
            rotZ = Math.toRadians(-22.0);
        }

        // Xoay quanh trục Z
        double x1 = lx * Math.cos(rotZ) - ly * Math.sin(rotZ);
        double y1 = lx * Math.sin(rotZ) + ly * Math.cos(rotZ);
        double z1 = lz;

        // Xoay quanh trục X
        double finalX = x1;
        double finalY = y1 * Math.cos(rotX) - z1 * Math.sin(rotX);
        double finalZ = y1 * Math.sin(rotX) + z1 * Math.cos(rotX);

        double centerElevation = blastCenter.y + 3.2 + blastRadius * 0.35;

        double targetWorldX = blastCenter.x + finalX;
        double targetWorldY = Math.max(blastCenter.y + 1.2, centerElevation + finalY);
        double targetWorldZ = blastCenter.z + finalZ;

        return new Vec3d(
            targetWorldX - blockCenter.x,
            targetWorldY - blockCenter.y,
            targetWorldZ - blockCenter.z
        );
    }

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
