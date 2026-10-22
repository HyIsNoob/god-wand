package net.godwand.skill;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.ImpactFxPayload;
import net.godwand.spatial.DisplacedBlockRecord;
import net.godwand.spatial.SpatialDisplacementManager;
import net.godwand.spatial.SpatialMath;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RatioCollapseManager {

    public static class RatioCollapseTask {
        public final ServerWorld world;
        public final ServerPlayerEntity caster;
        public final BlockPos center;
        public final Vec3d meteorCore;
        public final double radius;
        public final double boulderRadius;
        public final int maxBlocks;
        public final int craterRadius;
        public final int craterDepth;
        public final float damageMult;

        public int ageTicks = 0;
        public float orbitAngle = 0.0f;
        public boolean slammed = false;

        public final List<BlockPos> displacedPositions = new ArrayList<>();
        public final Map<BlockPos, Integer> blockIndexMap = new HashMap<>();
        public final Map<BlockPos, Double> initialOrbitAngle = new HashMap<>();
        public final Map<BlockPos, Double> initialOrbitRadius = new HashMap<>();
        public final Map<BlockPos, Double> hoverHeightOffset = new HashMap<>();
        public final Map<BlockPos, Vec3d> sphereClusterOffset = new HashMap<>();
        public final Map<BlockPos, Quaternionf> baseRotation = new HashMap<>();

        public RatioCollapseTask(ServerWorld world, ServerPlayerEntity caster, BlockPos center, double radius,
                                 double boulderRadius, int maxBlocks, int craterRadius, int craterDepth, float damageMult) {
            this.world = world;
            this.caster = caster;
            this.center = center.toImmutable();
            this.radius = radius;
            this.boulderRadius = boulderRadius;
            this.maxBlocks = maxBlocks;
            this.craterRadius = craterRadius;
            this.craterDepth = craterDepth;
            this.damageMult = damageMult;
            this.meteorCore = center.toCenterPos().add(0, 12.0 + boulderRadius * 1.4, 0);
        }
    }

    private static final Queue<RatioCollapseTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();

    public static void cast(ServerPlayerEntity player) {
        cast(player, 1);
    }

    public static void cast(ServerPlayerEntity player, int chargeLevel) {
        ServerWorld world = player.getServerWorld();

        double reach = switch (chargeLevel) {
            case 2 -> 45.0;
            case 3 -> 65.0;
            default -> 28.0;
        };

        HitResult hit = player.raycast(reach, 0.0f, false);
        BlockPos targetPos;
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bHit) {
            targetPos = bHit.getBlockPos();
        } else {
            Vec3d targetVec = player.getEyePos().add(player.getRotationVec(1.0f).multiply(reach * 0.7));
            targetPos = BlockPos.ofFloored(targetVec);
            while (targetPos.getY() > world.getBottomY() && world.getBlockState(targetPos).isAir()) {
                targetPos = targetPos.down();
            }
        }

        double radius = switch (chargeLevel) {
            case 2 -> 13.5;
            case 3 -> 19.0;
            default -> 9.0;
        };

        double boulderRadius = switch (chargeLevel) {
            case 2 -> 3.6;
            case 3 -> 5.0;
            default -> 2.5;
        };

        int maxBlocks = switch (chargeLevel) {
            case 2 -> 200;
            case 3 -> 300;
            default -> 135;
        };

        int craterRadius = switch (chargeLevel) {
            case 2 -> 14;
            case 3 -> 20;
            default -> 9;
        };

        // Tăng độ sâu hố khoan (Drill Borehole Depth) sâu hơn đáng kể
        int craterDepth = switch (chargeLevel) {
            case 2 -> 12;
            case 3 -> 18;
            default -> 8;
        };

        float damageMult = switch (chargeLevel) {
            case 2 -> 1.6f;
            case 3 -> 2.5f;
            default -> 1.0f;
        };

        launch(world, player, targetPos, radius, boulderRadius, maxBlocks, craterRadius, craterDepth, damageMult);
    }

    public static void launch(ServerWorld world, ServerPlayerEntity player, BlockPos center, double radius) {
        launch(world, player, center, radius, 2.5, 135, 9, 8, 1.0f);
    }

    public static void launch(ServerWorld world, ServerPlayerEntity player, BlockPos center,
                              double radius, double boulderRadius, int maxBlocks,
                              int craterRadius, int craterDepth, float damageMult) {
        RatioCollapseTask task = new RatioCollapseTask(world, player, center, radius, boulderRadius, maxBlocks, craterRadius, craterDepth, damageMult);

        // 1. Kích hoạt Quả Cầu Năng Lượng Trọng Lực trên không (Đổi màu từ Vàng Cam -> Đỏ Nhiệt Hạch ở pha Mũi Khoan)
        ImpactFxPayload gravityCorePayload = new ImpactFxPayload(
                task.meteorCore.x, task.meteorCore.y, task.meteorCore.z,
                ImpactFxPayload.TYPE_GRAVITY_CORE, 135, (float) (boulderRadius / 2.5)
        );
        for (ServerPlayerEntity p : PlayerLookup.around(world, task.meteorCore, 96.0)) {
            ServerPlayNetworking.send(p, gravityCorePayload);
        }

        // Âm thanh triệu hồi lõi trọng lực & nứt vỡ địa chấn
        world.playSound(null, center, SoundEvents.BLOCK_HEAVY_CORE_BREAK, SoundCategory.BLOCKS, 3.5f, 0.75f);
        world.playSound(null, task.meteorCore.x, task.meteorCore.y, task.meteorCore.z,
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 3.0f, 1.2f);
        world.playSound(null, center, SoundEvents.ITEM_MACE_SMASH_GROUND, SoundCategory.BLOCKS, 3.0f, 0.8f);

        world.spawnParticles(ParticleTypes.SONIC_BOOM, task.meteorCore.x, task.meteorCore.y, task.meteorCore.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.FLASH, task.meteorCore.x, task.meteorCore.y, task.meteorCore.z, 3, 0.2, 0.2, 0.2, 0.0);

        // 2. Bóc các khối đất đá từ mặt đất
        int intR = (int) Math.ceil(task.radius);
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        List<BlockPos> validPositions = new ArrayList<>();
        for (int x = -intR; x <= intR; x++) {
            for (int z = -intR; z <= intR; z++) {
                double dSq = x * x + z * z;
                if (dSq > task.radius * task.radius) continue;

                int startY = center.getY() + 3;
                mutable.set(center.getX() + x, startY, center.getZ() + z);
                while (mutable.getY() > center.getY() - 6 && world.getBlockState(mutable).isAir()) {
                    mutable.move(0, -1, 0);
                }

                BlockState state = world.getBlockState(mutable);
                if (!SpatialDisplacementManager.canDisplace(world, mutable, state)) continue;
                validPositions.add(mutable.toImmutable());
            }
        }

        Collections.shuffle(validPositions, new Random(center.asLong()));
        int count = Math.min(task.maxBlocks, validPositions.size());

        for (int i = 0; i < count; i++) {
            BlockPos pos = validPositions.get(i);
            float hash = (float) SpatialMath.hash01(pos);

            Quaternionf rot = new Quaternionf()
                    .rotateX((float) Math.toRadians(25.0 * Math.sin(hash * 6.28)))
                    .rotateY((float) Math.toRadians(45.0 * Math.cos(hash * 6.28)));

            SpatialDisplacementManager.displaceBlock(
                    world,
                    pos,
                    new Vector3f(0, 0, 0),
                    rot,
                    new Vector3f(1.0f, 1.0f, 1.0f),
                    125,
                    0,
                    true,
                    DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
                    player.getUuid(),
                    null,
                    0.0
            );

            DisplacedBlockRecord rec = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
            if (rec != null) {
                rec.permanentDestruction = true;
            }

            double dx = (pos.getX() + 0.5) - task.meteorCore.x;
            double dz = (pos.getZ() + 0.5) - task.meteorCore.z;
            double initAngle = Math.atan2(dz, dx);
            double initRad = Math.max(task.boulderRadius * 1.8, Math.sqrt(dx * dx + dz * dz));
            double yBand = (hash - 0.5) * (task.boulderRadius * 1.6);

            task.displacedPositions.add(pos);
            task.blockIndexMap.put(pos, i);
            task.initialOrbitAngle.put(pos, initAngle);
            task.initialOrbitRadius.put(pos, initRad);
            task.hoverHeightOffset.put(pos, yBand);
            task.baseRotation.put(pos, rot);
            task.sphereClusterOffset.put(pos, computeBoulderClusterOffset(i, count, task.boulderRadius));
        }

        Box vortexBox = new Box(center).expand(task.radius * 1.25);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, vortexBox, ent -> ent != player)) {
            e.addVelocity(0, 0.48, 0);
            e.velocityModified = true;
            e.damage(world.getDamageSources().magic(), 4.0f * task.damageMult);
        }

        ACTIVE_TASKS.add(task);
    }

    public static Vec3d computeBoulderClusterOffset(int index, int total, double radius) {
        if (total <= 1) return Vec3d.ZERO;
        double phi = Math.acos(1.0 - 2.0 * (index + 0.5) / total);
        double theta = Math.PI * (1.0 + Math.sqrt(5.0)) * index;
        double r = radius * (0.65 + 0.35 * Math.sqrt((index + 0.5) / (double) total));
        return new Vec3d(
                r * Math.sin(phi) * Math.cos(theta),
                r * Math.cos(phi),
                r * Math.sin(phi) * Math.sin(theta)
        );
    }

    /**
     * Tính tọa độ MŨI KHOAN XOẮN ỐC (Rudeus Stone Cannon Conical Spiral Drill)
     * - Mũi nhọn ở dưới (y âm), đáy khoan phình rộng ở trên (y dương)
     * - 3 đường gân xoắn ốc chạy dọc thân mũi khoan
     */
    public static Vec3d computeSpiralDrillOffset(int index, int total, double boulderRadius, float spinAngle) {
        if (total <= 1) return Vec3d.ZERO;
        // h chạy từ 0.0 (mũi nhọn dưới cùng) lên 1.0 (đáy mũi khoan phía trên)
        double h = (index + 0.5) / (double) total;
        double drillLength = boulderRadius * 3.2;

        // Tọa độ Y: mũi khoan nhọn chúc thẳng xuống đất (-0.65 * drillLength)
        double localY = (h - 0.62) * drillLength;

        // Bán kính hình nón: Nhọn hoắt ở mũi (0.12m) và phình to theo hàm mũ 0.72 lên đỉnh
        double maxConeR = boulderRadius * 1.18;
        double coneR = 0.12 + maxConeR * Math.pow(h, 0.72);

        // Phân thành 3 rãnh xoắn ốc (3-Flute Helical Drill)
        int flute = index % 3;
        double spiralTwist = h * Math.PI * 5.0; // Xoắn 2.5 vòng từ mũi lên đáy
        double angle = flute * (2.0 * Math.PI / 3.0) + spiralTwist + spinAngle;

        // Lấp đầy cả lõi bên trong mũi khoan và gân ngoài
        double radialLayer = (index % 5 == 0) ? 0.45 : 1.0;
        double r = coneR * radialLayer;

        return new Vec3d(
                Math.cos(angle) * r,
                localY,
                Math.sin(angle) * r
        );
    }

    public static void tick(ServerWorld world) {
        Iterator<RatioCollapseTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            RatioCollapseTask task = it.next();
            if (task.world != world) continue;

            task.ageTicks++;
            int totalBlocks = Math.max(1, task.displacedPositions.size());

            // =========================================================================
            // KHỐI CẦU NĂNG LƯỢNG ĐỎ BỐC CHÁY (Blazing Crimson Fireball Particles Ticks 1..134)
            // =========================================================================
            if (task.ageTicks <= 134) {
                double coreR = task.boulderRadius * 0.72;
                for (int f = 0; f < 3; f++) {
                    double ang = world.random.nextDouble() * Math.PI * 2.0;
                    double elev = (world.random.nextDouble() - 0.5) * Math.PI;
                    double fx = task.meteorCore.x + Math.cos(elev) * Math.cos(ang) * coreR;
                    double fy = task.meteorCore.y + Math.sin(elev) * coreR;
                    double fz = task.meteorCore.z + Math.cos(elev) * Math.sin(ang) * coreR;
                    world.spawnParticles(ParticleTypes.FLAME, fx, fy, fz, 1, 0, 0.05, 0, 0.02);
                    world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.12f, 0.02f), 1.6f),
                            fx, fy, fz, 1, 0, 0.02, 0, 0.05);
                }
                if (task.ageTicks % 4 == 0) {
                    world.spawnParticles(ParticleTypes.LAVA, task.meteorCore.x, task.meteorCore.y, task.meteorCore.z, 2, 0.25, 0.25, 0.25, 0.04);
                }
            }

            // =========================================================================
            // PHA 1: BAY LƠ LỬNG LÊN NGANG QUẢ CẦU NĂNG LƯỢNG (Ticks 1..38)
            // =========================================================================
            if (task.ageTicks <= 38) {
                float t = task.ageTicks / 38.0f;
                float smoothT = 0.5f - 0.5f * (float) Math.cos(t * Math.PI); // S-curve mượt

                if (task.ageTicks % 2 == 0) {
                    for (BlockPos pos : task.displacedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;
                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        double initAngle = task.initialOrbitAngle.getOrDefault(pos, 0.0);
                        double initRad = task.initialOrbitRadius.getOrDefault(pos, 5.0);
                        double hoverY = task.hoverHeightOffset.getOrDefault(pos, 0.0);

                        // Điểm đích Pha 1: Bay thẳng lên độ cao ngang với Quả Cầu Năng Lượng (meteorCore.y + hoverY)
                        Vec3d targetHoverWorld = new Vec3d(
                                task.meteorCore.x + Math.cos(initAngle) * initRad,
                                task.meteorCore.y + hoverY,
                                task.meteorCore.z + Math.sin(initAngle) * initRad
                        );
                        Vec3d curDisp = targetHoverWorld.subtract(Vec3d.ofCenter(pos)).multiply(smoothT);
                        Quaternionf rot = task.baseRotation.getOrDefault(pos, new Quaternionf());

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(2);
                        display.setTransformation(SpatialMath.createPivotTransform(curDisp, rot, 1.0f));
                    }

                    if (task.ageTicks % 6 == 0) {
                        world.playSound(null, task.center.up(5), SoundEvents.BLOCK_BASALT_BREAK, SoundCategory.BLOCKS, 1.8f, 0.75f);
                    }
                }
            }

            // =========================================================================
            // PHA 2: XOAY TỐC ĐỘ CAO THEO QUỸ ĐẠO QUANH QUẢ CẦU & GHÉP THÀNH KHỐI LỚN (Ticks 39..88)
            // =========================================================================
            else if (task.ageTicks <= 88) {
                float p2 = (task.ageTicks - 38) / 50.0f; // 0.0 -> 1.0
                float smoothP2 = p2 * p2 * (3.0f - 2.0f * p2);

                // Tốc độ xoay quỹ đạo tăng vọt khi các khối đá lao vào tâm cầu
                float currentSpinSpeed = 0.12f + p2 * 0.28f;
                task.orbitAngle += currentSpinSpeed;

                if (task.ageTicks % 2 == 0) {
                    for (BlockPos pos : task.displacedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;
                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        double initAngle = task.initialOrbitAngle.getOrDefault(pos, 0.0);
                        double initRad = task.initialOrbitRadius.getOrDefault(pos, 5.0);
                        double hoverY = task.hoverHeightOffset.getOrDefault(pos, 0.0);

                        // Quỹ đạo xoáy ốc thu hẹp dần quanh quả cầu
                        double curAngle = initAngle + task.orbitAngle;
                        double curOrbitRad = MathHelper.lerp(smoothP2, initRad, task.boulderRadius);
                        Vec3d swirlingOrbitOffset = new Vec3d(
                                Math.cos(curAngle) * curOrbitRad,
                                hoverY * (1.0 - smoothP2 * 0.5),
                                Math.sin(curAngle) * curOrbitRad
                        );

                        // Khối cầu lớn tự xoay quanh trục Y
                        Vec3d baseSphereOff = task.sphereClusterOffset.getOrDefault(pos, Vec3d.ZERO);
                        double cosA = Math.cos(task.orbitAngle);
                        double sinA = Math.sin(task.orbitAngle);
                        Vec3d rotatedSphereOff = new Vec3d(
                                baseSphereOff.x * cosA - baseSphereOff.z * sinA,
                                baseSphereOff.y,
                                baseSphereOff.x * sinA + baseSphereOff.z * cosA
                        );

                        // Nội suy từ vòng quỹ đạo ngoài ép chặt thành 1 khối cầu lớn đang xoay
                        Vec3d localOff = swirlingOrbitOffset.lerp(rotatedSphereOff, smoothP2);
                        Vec3d finalDisp = task.meteorCore.add(localOff).subtract(Vec3d.ofCenter(pos));

                        Quaternionf rot = new Quaternionf(task.baseRotation.getOrDefault(pos, new Quaternionf()))
                                .rotateY(task.orbitAngle);
                        float scaleVal = 1.0f + 0.28f * smoothP2;

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(2);
                        display.setTransformation(SpatialMath.createPivotTransform(finalDisp, rot, scaleVal));
                    }
                }

                // Hút sinh vật xung quanh và tạo vệt gió xoáy quanh quả cầu
                if (task.ageTicks % 4 == 0) {
                    world.playSound(null, task.meteorCore.x, task.meteorCore.y, task.meteorCore.z,
                            SoundEvents.ENTITY_BREEZE_IDLE_AIR, SoundCategory.BLOCKS, 2.2f, 0.8f + p2 * 0.8f);
                }
                if (task.ageTicks % 6 == 0) {
                    Box suctionBox = new Box(task.center).expand(task.radius * 1.2);
                    for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, suctionBox, ent -> ent != task.caster)) {
                        Vec3d pull = task.meteorCore.subtract(e.getPos()).normalize().multiply(0.24);
                        e.addVelocity(pull.x, pull.y, pull.z);
                        e.velocityModified = true;
                    }
                }
            }

            // =========================================================================
            // PHA 3: GIA TỐC CỰC HẠN -> HÓA ĐỎ NHIỆT HẠCH -> BIẾN HÌNH MŨI KHOAN (Ticks 89..134)
            // =========================================================================
            else if (task.ageTicks <= 134) {
                float drillProg = (task.ageTicks - 88) / 46.0f; // 0.0 -> 1.0
                float morphFactor = Math.min(1.0f, drillProg / 0.65f); // Ticks 89..118: Biến hình hoàn tất thành Mũi Khoan
                float smoothMorph = morphFactor * morphFactor * (3.0f - 2.0f * morphFactor);

                // Xoay nhanh gấp bội như mũi khoan siêu tốc!
                float drillSpinSpeed = 0.42f + drillProg * 0.55f;
                task.orbitAngle += drillSpinSpeed;

                if (task.ageTicks == 89) {
                    CinematicFxPayload focusPayload = new CinematicFxPayload(
                            CinematicFxPayload.EVENT_METEOR_FOCUS,
                            task.meteorCore.x, task.meteorCore.y, task.meteorCore.z,
                            0.86f, 46
                    );
                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.meteorCore, 96.0)) {
                        ServerPlayNetworking.send(p, focusPayload);
                    }
                }

                if (task.ageTicks % 2 == 0) {
                    for (BlockPos pos : task.displacedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;
                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        int idx = task.blockIndexMap.getOrDefault(pos, 0);

                        // Tọa độ trên khối cầu đang xoay
                        Vec3d baseSphereOff = task.sphereClusterOffset.getOrDefault(pos, Vec3d.ZERO);
                        double cosA = Math.cos(task.orbitAngle);
                        double sinA = Math.sin(task.orbitAngle);
                        Vec3d rotSphere = new Vec3d(
                                baseSphereOff.x * cosA - baseSphereOff.z * sinA,
                                baseSphereOff.y,
                                baseSphereOff.x * sinA + baseSphereOff.z * cosA
                        );

                        // Tọa độ trên MŨI KHOAN XOẮN ỐC (Spiral Drill) đang xoay cực đại
                        Vec3d drillOff = computeSpiralDrillOffset(idx, totalBlocks, task.boulderRadius, task.orbitAngle);

                        // Nội suy từ Khối Cầu -> Mũi Khoan Nhọn
                        Vec3d currentShapeOff = rotSphere.lerp(drillOff, smoothMorph);
                        Vec3d finalDisp = task.meteorCore.add(currentShapeOff).subtract(Vec3d.ofCenter(pos));

                        // Góc nghiêng của từng viên đá xoắn theo rãnh mũi khoan
                        Quaternionf drillRot = new Quaternionf()
                                .rotateY(-task.orbitAngle)
                                .rotateX((float) Math.toRadians(35.0 * smoothMorph))
                                .rotateZ((float) Math.toRadians(20.0 * smoothMorph));

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(2);
                        display.setTransformation(SpatialMath.createPivotTransform(finalDisp, drillRot, 1.25f));
                    }
                }

                // HIỆU ỨNG HÓA ĐỎ NHIỆT HẠCH (RED-HOT OVERHEAT PARTICLES) BAO QUANH MŨI KHOAN
                double drillLen = task.boulderRadius * 3.2;
                for (int k = 0; k < 12; k++) {
                    double h = Math.random();
                    double coneR = 0.15 + task.boulderRadius * 1.2 * Math.pow(h, 0.72);
                    double ang = task.orbitAngle + k * (Math.PI / 6.0) + h * 8.0;
                    double px = task.meteorCore.x + Math.cos(ang) * coneR;
                    double py = task.meteorCore.y + (h - 0.62) * drillLen;
                    double pz = task.meteorCore.z + Math.sin(ang) * coneR;

                    // Hạt bụi năng lượng Đỏ Rực / Cam Nhiệt Hạch xoáy quanh thân mũi khoan
                    world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.12f + (float) (1.0 - drillProg) * 0.35f, 0.05f), 2.1f),
                            px, py, pz, 1, 0.05, 0.05, 0.05, 0.0);
                    if (k % 3 == 0) {
                        world.spawnParticles(ParticleTypes.FLAME, px, py, pz, 1, -Math.sin(ang) * 0.25, 0.05, Math.cos(ang) * 0.25, 0.02);
                    }
                }
                world.spawnParticles(ParticleTypes.LAVA, task.meteorCore.x, task.meteorCore.y - drillLen * 0.5, task.meteorCore.z, 3, 0.4, 0.4, 0.4, 0.1);

                // Âm thanh khoan ma thuật & nạp nhiệt hạch dồn dập
                if (task.ageTicks % 4 == 0) {
                    float pitch = 0.9f + drillProg * 1.0f;
                    world.playSound(null, task.meteorCore.x, task.meteorCore.y, task.meteorCore.z,
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 3.0f, pitch);
                    world.playSound(null, task.meteorCore.x, task.meteorCore.y, task.meteorCore.z,
                            SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.BLOCKS, 2.5f, pitch);
                }
            }

            // =========================================================================
            // PHA 4: MŨI KHOAN ĐÂM XUYÊN MẶT ĐẤT & PHÁ HỦY DẠNG KHOAN SÂU (Ticks 135..152)
            // =========================================================================
            else if (task.ageTicks <= 152) {
                float boreProg = (task.ageTicks - 134) / 10.0f; // Lao cắm sâu xuống đất trong 10 ticks (135..145)
                float clampedBore = MathHelper.clamp(boreProg, 0.0f, 1.0f);
                task.orbitAngle += 0.95f; // Vẫn xoay cực nhanh trong lúc đâm xuyên xuống đất!

                // Tâm mũi khoan lao từ meteorCore xuyên thẳng xuống dưới mặt đất (-craterDepth * 0.45)
                Vec3d drillTargetCenter = task.center.toCenterPos().subtract(0, task.craterDepth * 0.35, 0);
                Vec3d currentDrillCenter = task.meteorCore.lerp(drillTargetCenter, clampedBore * clampedBore);

                if (task.ageTicks == 135) {
                    world.playSound(null, task.center, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.BLOCKS, 3.5f, 0.5f);
                    world.playSound(null, task.center, SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.BLOCKS, 3.5f, 1.2f);
                }

                // Cập nhật Mũi Khoan vừa xoay vừa cắm xuyên xuống lòng đất mỗi tick!
                if (task.ageTicks <= 145) {
                    for (BlockPos pos : task.displacedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;
                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        int idx = task.blockIndexMap.getOrDefault(pos, 0);
                        Vec3d drillOff = computeSpiralDrillOffset(idx, totalBlocks, task.boulderRadius, task.orbitAngle);
                        Vec3d slamDisp = currentDrillCenter.add(drillOff).subtract(Vec3d.ofCenter(pos));

                        Quaternionf drillRot = new Quaternionf()
                                .rotateY(-task.orbitAngle)
                                .rotateX((float) Math.toRadians(35.0));

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(1);
                        display.setTransformation(SpatialMath.createPivotTransform(slamDisp, drillRot, 1.25f));
                    }

                    // Vệt lửa đỏ rực kéo dài theo đuôi mũi khoan khi lao xuống
                    world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.1f, 0.05f), 2.5f),
                            currentDrillCenter.x, currentDrillCenter.y, currentDrillCenter.z,
                            25, task.boulderRadius * 0.6, 1.5, task.boulderRadius * 0.6, 0.1);
                }

                // TẠI TICK 143 (Khi mũi nhọn khoan xuyên qua mặt đất): KÍCH HOẠT ĐẠI ĐỊA CHẤN & HỐ KHOAN SÂU!
                if (task.ageTicks == 143 && !task.slammed) {
                    task.slammed = true;

                    // Âm thanh khoan thủng lòng đất
                    world.playSound(null, task.center, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 4.5f, 0.55f);
                    world.playSound(null, task.center, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.BLOCKS, 4.5f, 0.60f);
                    world.playSound(null, task.center, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 3.5f, 0.5f);
                    world.playSound(null, task.center, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.BLOCKS, 3.8f, 1.1f);

                    // VFX sóng xung kích mặt đất & cột dung nham phụt lên từ miệng lỗ khoan
                    world.spawnParticles(ParticleTypes.FLASH,
                            task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5, 8, 0.4, 0.4, 0.4, 0.0);
                    world.spawnParticles(ParticleTypes.SONIC_BOOM,
                            task.center.getX() + 0.5, task.center.getY() + 1.0, task.center.getZ() + 0.5, 4, 0.2, 0.5, 0.2, 0.0);
                    world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                            task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5, 5, 1.2, 1.2, 1.2, 0.0);
                    world.spawnParticles(ParticleTypes.LAVA,
                            task.center.getX() + 0.5, task.center.getY() + 1.0, task.center.getZ() + 0.5, 40, 2.0, 2.5, 2.0, 0.3);

                    for (int i = 0; i < 36; i++) {
                        double angle = (i / 36.0) * (2.0 * Math.PI);
                        double r = task.craterRadius * 0.9;
                        double px = task.center.getX() + 0.5 + Math.cos(angle) * r;
                        double pz = task.center.getZ() + 0.5 + Math.sin(angle) * r;
                        world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, task.center.getY() + 0.3, pz,
                                2, Math.cos(angle) * 0.22, 0.08, Math.sin(angle) * 0.22, 0.03);
                        world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.2f, 0.05f), 1.8f),
                                px, task.center.getY() + 0.4, pz, 3, 0.3, 0.3, 0.3, 0.05);
                    }

                    // Sát thương khoan xuyên giáp & hất văng
                    Box slamBox = new Box(task.center).expand(task.radius * 1.45);
                    for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, slamBox, ent -> ent != task.caster)) {
                        double dist = e.getPos().distanceTo(task.center.toCenterPos());
                        float dmg = (float) Math.max(20.0, ((1.0 - (dist / (task.radius * 1.45))) * 48.0 + 14.0) * task.damageMult);
                        e.damage(world.getDamageSources().fallingBlock(task.caster), dmg);

                        Vec3d diff = e.getPos().subtract(task.center.toCenterPos()).normalize().multiply(1.8 * task.damageMult).add(0, 0.75, 0);
                        e.addVelocity(diff.x, diff.y, diff.z);
                        e.velocityModified = true;
                    }

                    // Shader Ground Seismic Shockwave & Camera Shake
                    ImpactFxPayload slamPayload = new ImpactFxPayload(
                            task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5,
                            ImpactFxPayload.TYPE_SEISMIC_SLAM, 22, 2.2f * task.damageMult
                    );
                    CinematicFxPayload slamCinematic = new CinematicFxPayload(
                            CinematicFxPayload.EVENT_METEOR_SLAM,
                            task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5,
                            Math.min(1.0f, 1.0f * task.damageMult), 26
                    );
                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.center.toCenterPos(), 96.0)) {
                        ServerPlayNetworking.send(p, slamPayload);
                        ServerPlayNetworking.send(p, slamCinematic);
                    }

                    // TẠO HỐ KHOAN HÌNH NÓN XOẮN SÂU (SPIRAL DRILL BOREHOLE CRATER)
                    int craterR = task.craterRadius;
                    int maxD = task.craterDepth;
                    BlockPos.Mutable cMut = new BlockPos.Mutable();
                    Random rng = new Random(task.center.asLong());

                    for (int cx = -craterR; cx <= craterR; cx++) {
                        for (int cz = -craterR; cz <= craterR; cz++) {
                            double distSq = cx * cx + cz * cz;
                            if (distSq > craterR * craterR) continue;

                            double dist2D = Math.sqrt(distSq);
                            double angle2D = Math.atan2(cz, cx);
                            // Rãnh xoắn ốc khắc vào lòng hố khoan
                            double spiralGroove = Math.sin(angle2D * 3.0 + dist2D * 0.85) * 1.2;
                            double normDist = Math.min(1.0, dist2D / craterR);
                            // Hình phễu mũi khoan nhọn và sâu ở tâm (hàm mũ 1.45)
                            int depth = (int) Math.round(Math.pow(1.0 - normDist, 1.45) * maxD + (1.0 - normDist) * spiralGroove);

                            for (int cy = -depth; cy <= 2; cy++) {
                                cMut.set(task.center.getX() + cx, task.center.getY() + cy, task.center.getZ() + cz);
                                if (cMut.getY() < world.getBottomY() || cMut.getY() >= world.getTopY()) continue;

                                BlockState bState = world.getBlockState(cMut);
                                if (bState.isAir() || bState.getBlock() == Blocks.BEDROCK) continue;

                                if (cy == -depth) {
                                    // Vách rãnh khoan nóng chảy đỏ rực
                                    boolean isGrooveHot = Math.sin(angle2D * 3.0 + dist2D * 0.85) > 0.25;
                                    BlockState floorState = isGrooveHot ? Blocks.MAGMA_BLOCK.getDefaultState() :
                                            (rng.nextFloat() < 0.45f) ? Blocks.BLACKSTONE.getDefaultState() :
                                            (rng.nextFloat() < 0.75f) ? Blocks.COBBLESTONE.getDefaultState() :
                                                    Blocks.OBSIDIAN.getDefaultState();
                                    world.setBlockState(cMut, floorState, Block.NOTIFY_ALL);
                                } else if (cy > -depth) {
                                    world.setBlockState(cMut, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                                }
                            }
                        }
                    }
                }

                // Tại Tick 148: Xóa các BlockDisplay của mũi khoan (đã chui sâu vào lòng đất) và bắn đá nóng chảy văng lên miệng hố
                if (task.ageTicks == 148) {
                    Random rng = new Random(task.center.asLong());
                    int flingCount = 0;
                    int maxFling = Math.min(65, task.maxBlocks / 3);
                    for (BlockPos pos : task.displacedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.removeActiveRecord(world.getRegistryKey(), pos);
                        if (record != null && flingCount < maxFling && rng.nextFloat() < 0.55f) {
                            flingCount++;
                            double fAngle = rng.nextDouble() * Math.PI * 2.0;
                            double fSpeed = 0.50 + rng.nextDouble() * 0.85;
                            double spawnX = task.center.getX() + 0.5 + Math.cos(fAngle) * 3.0;
                            double spawnY = task.center.getY() + 1.5;
                            double spawnZ = task.center.getZ() + 0.5 + Math.sin(fAngle) * 3.0;

                            BlockState flungState = (rng.nextFloat() < 0.35f) ? Blocks.MAGMA_BLOCK.getDefaultState() : record.originalState;
                            FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world,
                                    BlockPos.ofFloored(spawnX, spawnY, spawnZ),
                                    flungState);
                            falling.setVelocity(Math.cos(fAngle) * fSpeed, 0.55 + rng.nextDouble() * 0.55, Math.sin(fAngle) * fSpeed);
                            falling.velocityModified = true;
                        }
                    }
                }
            }

            // =========================================================================
            // PHA 5: KẾT THÚC (Tick 153+)
            // =========================================================================
            else {
                it.remove();
            }
        }
    }

    public static int getActiveTaskCount() {
        return ACTIVE_TASKS.size();
    }

    public static void clearAllTasks() {
        ACTIVE_TASKS.clear();
    }
}
