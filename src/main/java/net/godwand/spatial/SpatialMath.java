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
     * Biến một BlockDisplayEntity (đặt tại startPos) thành một Tia Năng Lượng 3D siêu mỏng
     * nối thẳng từ startPos tới endPos với độ dày thickness (mặc định 0.035f).
     */
    public static AffineTransformation createThinBeamTransform(Vec3d startPos, Vec3d endPos, float thickness) {
        Vec3d diff = endPos.subtract(startPos);
        float length = (float) diff.length();
        if (length < 0.01f) {
            return new AffineTransformation(new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(0, 0, 0), null);
        }

        Vector3f dir = new Vector3f((float) diff.x / length, (float) diff.y / length, (float) diff.z / length);
        Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), dir);

        // Căn giữa tiết diện tia (thickness x thickness) quanh trục phóng Z
        Vector3f localOffset = new Vector3f(-thickness * 0.5f, -thickness * 0.5f, 0.0f);
        rot.transform(localOffset);

        return new AffineTransformation(
            localOffset,
            rot,
            new Vector3f(thickness, thickness, length),
            null
        );
    }

    /**
     * CHIÊU 1: CỔNG VÒM 2 TẦNG & SÓNG ĐỊA CHẤN (Inner Cathedral Arch + Outer Ground Ripple)
     * Tầng trong uốn cao vút qua đầu, tầng ngoài nhấp nhô nhẹ trên mặt đất tạo sóng chấn động.
     */
    public static Vec3d computeDynamicAuraOffset(BlockPos pos, Vec3d playerPos, Vec3d lookDirHoriz, double auraRadius, int ageTicks) {
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        Vec3d fromPlayer = blockCenter.subtract(playerPos);

        double forwardProj = fromPlayer.x * lookDirHoriz.x + fromPlayer.z * lookDirHoriz.z;
        Vec3d spinePoint = new Vec3d(
            playerPos.x + lookDirHoriz.x * MathHelper.clamp(forwardProj, -1.5, 7.0),
            blockCenter.y,
            playerPos.z + lookDirHoriz.z * MathHelper.clamp(forwardProj, -1.5, 7.0)
        );

        Vec3d radial = blockCenter.subtract(spinePoint);
        double perpDist = Math.sqrt(radial.x * radial.x + radial.z * radial.z);

        Vec3d rightVec = new Vec3d(-lookDirHoriz.z, 0, lookDirHoriz.x);
        double sideSign = (radial.x * rightVec.x + radial.z * rightVec.z) >= 0 ? 1.0 : -1.0;

        Vec3d pushDir = perpDist < 0.15
                ? rightVec.multiply(sideSign)
                : new Vec3d(radial.x / perpDist, 0, radial.z / perpDist);

        double spatialWave = Math.sin(pos.getX() * 0.5 + pos.getZ() * 0.5 - ageTicks * 0.18);

        // Tầng ngoài (perpDist > 2.0m): Hiệu ứng sóng chấn động nhấp nhô nhẹ trên mặt đất
        if (perpDist > 2.0) {
            double rippleFactor = Math.max(0.0, 1.0 - ((perpDist - 2.0) / 1.4));
            double rippleLift = (0.28 + Math.max(0.0, spatialWave) * 0.42) * rippleFactor;
            return new Vec3d(
                pushDir.x * (0.35 * rippleFactor),
                rippleLift,
                pushDir.z * (0.35 * rippleFactor)
            );
        }

        // Tầng trong (perpDist <= 2.0m): Uốn vòm cao vút qua đầu kiểu Doctor Strange
        double targetCorridorRadius = auraRadius + 0.65 + spatialWave * 0.15;
        double pushOutDist = Math.max(1.35, targetCorridorRadius - perpDist);

        double relY = Math.max(0.0, blockCenter.y - playerPos.y);
        double archFactor = Math.max(0.0, 1.0 - (perpDist / 2.1));
        double archLift = 0.85 + (relY * 0.55) + (archFactor * 1.65) + spatialWave * 0.18;

        return new Vec3d(
            pushDir.x * pushOutDist,
            archLift,
            pushDir.z * pushOutDist
        );
    }

    /**
     * Tính góc xoay uốn vòm đồng bộ cho Chiêu 1 (Tầng ngoài nghiêng nhẹ 14 độ, tầng trong lật 38 -> 72 độ)
     */
    public static Quaternionf computeArchwayRotation(BlockPos pos, Vec3d playerPos, Vec3d lookDirHoriz, int ageTicks) {
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        Vec3d rel = blockCenter.subtract(playerPos);
        Vec3d rightVec = new Vec3d(-lookDirHoriz.z, 0, lookDirHoriz.x);
        double perpDist = Math.abs(rel.x * rightVec.x + rel.z * rightVec.z);
        float side = (rel.x * rightVec.x + rel.z * rightVec.z) >= 0 ? 1.0f : -1.0f;

        if (perpDist > 2.0) {
            // Tầng sóng địa chấn ngoài: nghiêng nhẹ 14 độ
            float rippleAngle = (float) Math.toRadians(14.0 * side);
            return new Quaternionf()
                    .rotateY((float) Math.atan2(lookDirHoriz.x, lookDirHoriz.z))
                    .rotateZ(rippleAngle);
        }

        // Tầng vòm chính: xoay lật mạnh 38 -> 72 độ tạo mái vòm cơ khí
        float relY = (float) MathHelper.clamp(blockCenter.y - playerPos.y, 0.0, 3.0);
        float peelAngle = (float) Math.toRadians((38.0 + relY * 14.0) * side);
        float wavePitch = (float) Math.sin(ageTicks * 0.14 + (pos.getX() + pos.getZ()) * 0.4) * 0.16f;

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
        double dx = pos.getX() + 0.5 - blastCenter.x;
        double dz = pos.getZ() + 0.5 - blastCenter.z;
        double hash = hash01(pos);

        int ringIndex = (int) (Math.abs(pos.asLong()) % 3);
        double baseAngle = Math.atan2(dz, dx) + hash * 0.5;
        double angularSpeed = (ringIndex == 1 ? -0.045 : 0.040) * (0.85 + hash * 0.35);
        double theta = baseAngle + ageTicks * angularSpeed;

        return computeCanopyVortexInternal(pos, blastCenter, blastRadius, ringIndex, theta, hash, 1.0);
    }

    public static Vec3d computeCanopyVortexOffset(BlockPos pos, Vec3d blastCenter, double blastRadius, float customAngle, double radiusScale) {
        double dx = pos.getX() + 0.5 - blastCenter.x;
        double dz = pos.getZ() + 0.5 - blastCenter.z;
        double hash = hash01(pos);

        int ringIndex = (int) (Math.abs(pos.asLong()) % 3);
        double baseAngle = Math.atan2(dz, dx) + hash * 0.5;
        double dir = (ringIndex == 1 ? -1.0 : 1.0);
        double theta = baseAngle + customAngle * dir * (0.85 + hash * 0.35);

        return computeCanopyVortexInternal(pos, blastCenter, blastRadius, ringIndex, theta, hash, radiusScale);
    }

    private static Vec3d computeCanopyVortexInternal(BlockPos pos, Vec3d blastCenter, double blastRadius,
                                                    int ringIndex, double theta, double hash, double radiusScale) {
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        double baseR = switch (ringIndex) {
            case 0 -> blastRadius * (0.85 + hash * 0.40);
            case 1 -> blastRadius * (0.68 + hash * 0.32);
            default -> blastRadius * (1.02 + hash * 0.42);
        };
        double ringRadius = baseR * radiusScale;

        double lx = Math.cos(theta) * ringRadius;
        double lz = Math.sin(theta) * ringRadius;
        double ly = ((hash - 0.5) * 1.8 + Math.sin(theta * 2.0) * 0.35) * radiusScale;

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
