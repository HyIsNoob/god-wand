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
    public static final int ASSEMBLY_END = 88;
    public static final int SPIN_END = 158;
    public static final int IMPACT_TICK = 167;
    public static final int SEQUENCE_END = 176;

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
        private SupernovaCraterCursor craterCursor;

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
            this.meteorCore = center.toCenterPos().add(0, drillHoverHeight(boulderRadius), 0);
        }
    }

    private static final Queue<RatioCollapseTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();

    public static void cast(ServerPlayerEntity player) {
        cast(player, 1);
    }

    public static void cast(ServerPlayerEntity player, int chargeLevel) {
        ServerWorld world = player.getServerWorld();

        double reach = switch (chargeLevel) {
            case 4 -> 85.0;
            case 3 -> 65.0;
            case 2 -> 45.0;
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
            case 4 -> 24.0;
            case 3 -> 19.0;
            case 2 -> 13.5;
            default -> 9.0;
        };

        double boulderRadius = switch (chargeLevel) {
            case 4 -> 6.5;
            case 3 -> 5.0;
            case 2 -> 3.6;
            default -> 2.5;
        };

        int maxBlocks = switch (chargeLevel) {
            case 4 -> 420;
            case 3 -> 300;
            case 2 -> 200;
            default -> 159;
        };

        int craterRadius = switch (chargeLevel) {
            case 4 -> 23;
            case 3 -> 18;
            case 2 -> 13;
            default -> 8;
        };

        // Tăng độ sâu hố khoan (Drill Borehole Depth) sâu hơn gấp đôi, khoan cắm sâu vào lòng đất
        int craterDepth = switch (chargeLevel) {
            case 4 -> 54;
            case 3 -> 41;
            case 2 -> 28;
            default -> 19;
        };

        float damageMult = switch (chargeLevel) {
            case 4 -> 3.8f;
            case 3 -> 2.8f;
            case 2 -> 1.8f;
            default -> 1.2f;
        };

        launch(world, player, targetPos, radius, boulderRadius, maxBlocks, craterRadius, craterDepth, damageMult);
    }

    public static void launch(ServerWorld world, ServerPlayerEntity player, BlockPos center, double radius) {
        launch(world, player, center, radius, 2.5, 159, 8, 16, 1.2f);
    }

    public static void launch(ServerWorld world, ServerPlayerEntity player, BlockPos center,
                              double radius, double boulderRadius, int maxBlocks,
                              int craterRadius, int craterDepth, float damageMult) {
        RatioCollapseTask task = new RatioCollapseTask(world, player, center, radius, boulderRadius, maxBlocks, craterRadius, craterDepth, damageMult);

        // 1. Camera Shake rung chấn màn hình khi vừa xuất chiêu (Trauma Shake on Cast)
        CinematicFxPayload castShake = new CinematicFxPayload(
                CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                player.getX(), player.getY(), player.getZ(),
                Math.min(1.0f, 0.65f * damageMult), 14
        );
        ServerPlayNetworking.send(player, castShake);
        for (ServerPlayerEntity p : PlayerLookup.around(world, player.getPos(), 48.0)) {
            if (p != player) {
                ServerPlayNetworking.send(p, castShake);
            }
        }

        // 2. Kích hoạt Quả Cầu Năng Lượng Ma Pháp trên không (Arcane Energy Core tương tự Chiêu 2)
        ImpactFxPayload gravityCorePayload = new ImpactFxPayload(
                task.meteorCore.x, task.meteorCore.y, task.meteorCore.z,
                ImpactFxPayload.TYPE_GRAVITY_CORE, 159, (float) (boulderRadius / 2.5)
        );
        for (ServerPlayerEntity p : PlayerLookup.around(world, task.meteorCore, 160.0)) {
            ServerPlayNetworking.send(p, gravityCorePayload);
        }


        world.spawnParticles(ParticleTypes.SONIC_BOOM, task.meteorCore.x, task.meteorCore.y, task.meteorCore.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, task.meteorCore.x, task.meteorCore.y, task.meteorCore.z, 20, 0.4, 0.4, 0.4, 0.1);

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

            SpatialDisplacementManager.displaceBlockPermanently(
                    world,
                    pos,
                    new Vector3f(0, 0, 0),
                    rot,
                    new Vector3f(1.0f, 1.0f, 1.0f),
                    160,
                    0,
                    true,
                    DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
                    player.getUuid(),
                    null,
                    0.0
            );

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
            e.damage(world.getDamageSources().magic(), (e instanceof net.minecraft.entity.mob.MobEntity ? 2.0f : 1.0f) * (4.0f * task.damageMult));
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
            // KHỐI CẦU NĂNG LƯỢNG ĐỎ RỰC (Pure Crimson Ruby Energy Core Ticks 1..158)
            // Thu nhỏ tinh gọn, đậm đặc, kèm hiệu ứng hút vật chất & năng lượng vào tâm
            // =========================================================================
            if (task.ageTicks <= SPIN_END) {
                double coreR = Math.max(0.38, task.boulderRadius * 0.26); // Khối cầu nhỏ lại theo yêu cầu!
                for (int f = 0; f < 3; f++) {
                    double ang = world.random.nextDouble() * Math.PI * 2.0;
                    double elev = (world.random.nextDouble() - 0.5) * Math.PI;
                    double fx = task.meteorCore.x + Math.cos(elev) * Math.cos(ang) * coreR;
                    double fy = task.meteorCore.y + Math.sin(elev) * coreR;
                    double fz = task.meteorCore.z + Math.cos(elev) * Math.sin(ang) * coreR;
                    world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.05f, 0.12f), 1.6f),
                            fx, fy, fz, 1, 0, 0.02, 0, 0.04);
                    if (f % 2 == 0) {
                        world.spawnParticles(new DustParticleEffect(new Vector3f(0.90f, 0.0f, 0.18f), 1.3f),
                                fx, fy, fz, 1, 0, 0.01, 0, 0.02);
                        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, fx, fy, fz, 1, 0.01, 0.01, 0.01, 0.02);
                    }
                }

                // HIỆU ỨNG HÚT VẬT CHẤT & NĂNG LƯỢNG VÀO BÊN TRONG LÕI
                for (int s = 0; s < 4; s++) {
                    double spawnDist = 2.8 + world.random.nextDouble() * 4.5;
                    double sTheta = world.random.nextDouble() * Math.PI * 2.0;
                    double sPhi = (world.random.nextDouble() - 0.5) * Math.PI;
                    double sx = task.meteorCore.x + Math.cos(sPhi) * Math.cos(sTheta) * spawnDist;
                    double sy = task.meteorCore.y + Math.sin(sPhi) * spawnDist;
                    double sz = task.meteorCore.z + Math.cos(sPhi) * Math.sin(sTheta) * spawnDist;

                    Vec3d toCenter = task.meteorCore.subtract(sx, sy, sz).normalize().multiply(0.40);
                    world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.10f, 0.22f), 1.2f),
                            sx, sy, sz, 0, toCenter.x, toCenter.y, toCenter.z, 0.40);
                    if (s % 2 == 0) {
                        world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                                sx, sy, sz, 1, toCenter.x * 0.35, toCenter.y * 0.35, toCenter.z * 0.35, 0.06);
                    }
                }
            }

            // =========================================================================
            // PHA 1: BAY LƠ LỬNG LÊN NGANG QUẢ CẦU NĂNG LƯỢNG (Ticks 1..38)
            // =========================================================================
            if (task.ageTicks <= 38) {
                float t = task.ageTicks / 38.0f;
                float smoothT = 0.5f - 0.5f * (float) Math.cos(t * Math.PI); // S-curve mượt

                // Tốc độ xoay bắt đầu tăng tốc mượt từ 0.0 lên 0.12 rad/tick tại Tick 38
                float spinSpeed1 = 0.12f * (t * t);
                task.orbitAngle += spinSpeed1;

                if (task.ageTicks % 2 == 0) {
                    for (BlockPos pos : task.displacedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;
                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        int idx = task.blockIndexMap.getOrDefault(pos, 0);
                        double initAngle = task.initialOrbitAngle.getOrDefault(pos, 0.0);
                        double initRad = task.initialOrbitRadius.getOrDefault(pos, 5.0);
                        double hoverY = task.hoverHeightOffset.getOrDefault(pos, 0.0);

                        // Điểm đích Pha 1: Bay lên xoay theo quỹ đạo vòng ngoài (curAngle) ngang tầm meteorCore
                        double curAngle = initAngle + task.orbitAngle;
                        Vec3d targetHoverWorld = new Vec3d(
                                task.meteorCore.x + Math.cos(curAngle) * initRad,
                                task.meteorCore.y + hoverY,
                                task.meteorCore.z + Math.sin(curAngle) * initRad
                        );
                        Vec3d curDisp = targetHoverWorld.subtract(Vec3d.ofCenter(pos)).multiply(smoothT);

                        Quaternionf baseRot = task.baseRotation.getOrDefault(pos, new Quaternionf());
                        Quaternionf rot = new Quaternionf(baseRot).rotateY(-task.orbitAngle);

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(2);
                        display.setTransformation(SpatialMath.createPivotTransform(curDisp, rot, 1.0f));

                        // Sợi dây năng lượng siêu nhỏ điều khiển khối đá bay lên (Mana Control Filaments)
                        if (idx % Math.max(1, totalBlocks / 14) == 0) {
                            Vec3d blockWorld = Vec3d.ofCenter(pos).add(curDisp);
                            Vec3d thread = blockWorld.subtract(task.meteorCore);
                            double dLen = thread.length();
                            if (dLen > 0.8) {
                                int steps = Math.min(8, Math.max(3, (int) (dLen * 1.2)));
                                for (int st = 1; st < steps; st++) {
                                    double fr = (double) st / steps;
                                    world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.22f, 0.38f), 0.35f),
                                            task.meteorCore.x + thread.x * fr,
                                            task.meteorCore.y + thread.y * fr,
                                            task.meteorCore.z + thread.z * fr,
                                            1, 0, 0, 0, 0.0);
                                }
                                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                                        blockWorld.x, blockWorld.y, blockWorld.z, 1, 0.01, 0.01, 0.01, 0.01);
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // PHA 2: XOAY TỐC ĐỘ CAO & CÁC KHỐI ĐÁ TẬP HỢP TRỰC TIẾP THÀNH MŨI KHOAN (Ticks 39..88)
            // =========================================================================
            else if (task.ageTicks <= ASSEMBLY_END) {
                float p2 = (task.ageTicks - 38) / 50.0f; // 0.0 -> 1.0
                float smoothP2 = p2 * p2 * (3.0f - 2.0f * p2);

                // Tốc độ xoay quỹ đạo nối tiếp mượt mà từ 0.12 lên 0.40 rad/tick
                float currentSpinSpeed = 0.12f + p2 * 0.28f;
                task.orbitAngle += currentSpinSpeed;


                if (task.ageTicks % 2 == 0) {
                    for (BlockPos pos : task.displacedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;
                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        int idx = task.blockIndexMap.getOrDefault(pos, 0);
                        double initAngle = task.initialOrbitAngle.getOrDefault(pos, 0.0);
                        double initRad = task.initialOrbitRadius.getOrDefault(pos, 5.0);
                        double hoverY = task.hoverHeightOffset.getOrDefault(pos, 0.0);

                        // Quỹ đạo xoáy ốc thu hẹp dần từ ngoài vào tâm mũi khoan
                        double curAngle = initAngle + task.orbitAngle;
                        double curOrbitRad = MathHelper.lerp(smoothP2, initRad, task.boulderRadius * 1.4);
                        Vec3d swirlingOrbitOffset = new Vec3d(
                                Math.cos(curAngle) * curOrbitRad,
                                hoverY * (1.0 - smoothP2 * 0.5),
                                Math.sin(curAngle) * curOrbitRad
                        );

                        // Vị trí mục tiêu trực tiếp trên MŨI KHOAN XOẮN ỐC
                        Vec3d drillTargetOff = computeSpiralDrillOffset(idx, totalBlocks, task.boulderRadius, task.orbitAngle);

                        // Khối đá từ quỹ đạo ngoài bay thẳng vào ghép khớp đúng rãnh mũi khoan
                        Vec3d localOff = swirlingOrbitOffset.lerp(drillTargetOff, smoothP2);
                        Vec3d finalDisp = task.meteorCore.add(localOff).subtract(Vec3d.ofCenter(pos));

                        Quaternionf baseRot = task.baseRotation.getOrDefault(pos, new Quaternionf());
                        Quaternionf targetDrillRot = new Quaternionf()
                                .rotateY(-task.orbitAngle)
                                .rotateX((float) Math.toRadians(35.0));
                        Quaternionf startRot = new Quaternionf(baseRot).rotateY(-task.orbitAngle);
                        Quaternionf drillRot = new Quaternionf(startRot).slerp(targetDrillRot, smoothP2);

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(2);
                        display.setTransformation(SpatialMath.createPivotTransform(finalDisp, drillRot, 1.0f));

                        // Sợi dây năng lượng siêu nhỏ dệt và kéo khối đá khớp vào rãnh mũi khoan
                        if (idx % Math.max(1, totalBlocks / 14) == 0) {
                            Vec3d blockWorld = task.meteorCore.add(localOff);
                            Vec3d thread = blockWorld.subtract(task.meteorCore);
                            double dLen = thread.length();
                            if (dLen > 0.8) {
                                int steps = Math.min(8, Math.max(3, (int) (dLen * 1.2)));
                                for (int st = 1; st < steps; st++) {
                                    double fr = (double) st / steps;
                                    world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.22f, 0.38f), 0.35f),
                                            task.meteorCore.x + thread.x * fr,
                                            task.meteorCore.y + thread.y * fr,
                                            task.meteorCore.z + thread.z * fr,
                                            1, 0, 0, 0, 0.0);
                                }
                                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                                        blockWorld.x, blockWorld.y, blockWorld.z, 1, 0.01, 0.01, 0.01, 0.01);
                            }
                        }
                    }
                }

                // Hút sinh vật xung quanh và tạo vệt gió xoáy quanh mũi khoan đang hình thành
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
            // PHA 3: MŨI KHOAN HOÀN TẤT -> CHỈ XOAY TỐC ĐỘ CAO (KHÔNG SCALE) (Ticks 89..158)
            // =========================================================================
            else if (task.ageTicks <= SPIN_END) {
                float drillProg = (task.ageTicks - 88) / 70.0f; // 0.0 -> 1.0

                // Xoay nhanh dần đều từ 0.40 lên 0.85 rad/tick, kích thước hoàn toàn cố định
                float drillSpinSpeed = 0.40f + drillProg * 0.45f;
                task.orbitAngle += drillSpinSpeed;
                if (drillSpinSpeed >= 0.50f) sendDrillWind(task, task.meteorCore, drillSpinSpeed);

                // Cập nhật mỗi tick với duration 1 để khử triệt để hiện tượng hụt dây cung (chord-cutting),
                // giữ nguyên vẹn 100% bán kính mũi khoan, không co giãn to nhỏ!
                for (BlockPos pos : task.displacedPositions) {
                    DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                    if (record == null || record.displayEntityUuid == null) continue;
                    BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                    if (display == null || !display.isAlive()) continue;

                    int idx = task.blockIndexMap.getOrDefault(pos, 0);

                    // Tọa độ trên MŨI KHOAN XOẮN ỐC (Spiral Drill) - chỉ xoay theo orbitAngle
                    if (drillHeat(0.40f + Math.min(1f, (task.ageTicks - 88) / 70f) * 0.45f) > 0.2f + (idx % 11) * 0.06f) display.setBlockState(Blocks.MAGMA_BLOCK.getDefaultState());
                    
                    Vec3d drillOff = computeSpiralDrillOffset(idx, totalBlocks, task.boulderRadius, task.orbitAngle);
                    Vec3d finalDisp = task.meteorCore.add(drillOff).subtract(Vec3d.ofCenter(pos));

                    Quaternionf drillRot = new Quaternionf()
                            .rotateY(-task.orbitAngle)
                            .rotateX((float) Math.toRadians(35.0));

                    display.setStartInterpolation(0);
                    display.setInterpolationDuration(1);
                    display.setTransformation(SpatialMath.createPivotTransform(finalDisp, drillRot, 1.0f));
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

                    // Hạt bụi năng lượng Đỏ Rực (Pure Crimson Ruby) xoáy quanh thân mũi khoan
                    world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.02f, 0.05f), 2.2f),
                            px, py, pz, 1, 0.05, 0.05, 0.05, 0.0);
                    if (task.ageTicks >= 112 && k % 2 == 0) world.spawnParticles(ParticleTypes.FLAME, px, py, pz,
                            0, -Math.sin(ang) * 0.3, 0.1, Math.cos(ang) * 0.3, 0.6);
                    if (k % 2 == 0) {
                        world.spawnParticles(new DustParticleEffect(new Vector3f(0.85f, 0.0f, 0.12f), 1.6f),
                                px, py, pz, 1, -Math.sin(ang) * 0.25, 0.05, Math.cos(ang) * 0.25, 0.02);
                        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, px, py, pz, 1, 0.04, 0.04, 0.04, 0.03);
                    }
                }

            }

            // =========================================================================
            // PHA 4: MŨI KHOAN ĐÂM XUYÊN MẶT ĐẤT & PHÁ HỦY DẠNG KHOAN SÂU (Ticks 159..176)
            // =========================================================================
            else if (task.ageTicks <= SEQUENCE_END) {
                float boreProg = (task.ageTicks - 158) / 10.0f; // Lao cắm sâu xuống đất trong 10 ticks (159..169)
                float clampedBore = MathHelper.clamp(boreProg, 0.0f, 1.0f);
                task.orbitAngle += 0.95f; // Vẫn xoay cực nhanh trong lúc đâm xuyên xuống đất!

                // Tâm mũi khoan lao từ meteorCore xuyên thẳng sâu xuống lòng đất (-craterDepth * 0.60)
                Vec3d drillTargetCenter = task.center.toCenterPos().subtract(0, task.craterDepth * 0.60, 0);
                Vec3d currentDrillCenter = task.meteorCore.lerp(drillTargetCenter, clampedBore * clampedBore);
                sendDrillWind(task, currentDrillCenter, 0.95f);

                // Cập nhật Mũi Khoan vừa xoay vừa cắm xuyên xuống lòng đất mỗi tick!
                if (task.ageTicks <= 169) {
                    for (BlockPos pos : task.displacedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;
                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        int idx = task.blockIndexMap.getOrDefault(pos, 0);
                        if (drillHeat(0.40f + Math.min(1f, (task.ageTicks - 88) / 70f) * 0.45f) > 0.2f + (idx % 11) * 0.06f) display.setBlockState(Blocks.MAGMA_BLOCK.getDefaultState());
                    
                    Vec3d drillOff = computeSpiralDrillOffset(idx, totalBlocks, task.boulderRadius, task.orbitAngle);
                        Vec3d slamDisp = currentDrillCenter.add(drillOff).subtract(Vec3d.ofCenter(pos));

                        Quaternionf drillRot = new Quaternionf()
                                .rotateY(-task.orbitAngle)
                                .rotateX((float) Math.toRadians(35.0));

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(1);
                        display.setTransformation(SpatialMath.createPivotTransform(slamDisp, drillRot, 1.0f));
                    }

                    // Sát thương nghiền nát xuyên thấu khi mũi khoan cắm sâu xuống đất (Ticks 159..169)
                    Box drillPenetrateBox = Box.of(currentDrillCenter, task.boulderRadius * 2.2, task.boulderRadius * 3.6, task.boulderRadius * 2.2);
                    for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, drillPenetrateBox, ent -> ent != task.caster)) {
                        e.damage(world.getDamageSources().fallingBlock(task.caster), (e instanceof net.minecraft.entity.mob.MobEntity ? 2.0f : 1.0f) * (10.0f * task.damageMult));
                        e.addVelocity(0, -0.45, 0); // Ép chặt nạn nhân xuống đáy lỗ khoan
                        e.velocityModified = true;
                    }

                    // Vệt lửa đỏ rực kéo dài theo đuôi mũi khoan khi lao xuống
                    world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.1f, 0.05f), 2.5f),
                            currentDrillCenter.x, currentDrillCenter.y, currentDrillCenter.z,
                            25, task.boulderRadius * 0.6, 1.5, task.boulderRadius * 0.6, 0.1);
                }

                // TẠI TICK 167 (Khi mũi nhọn khoan xuyên qua mặt đất): KÍCH HOẠT ĐẠI ĐỊA CHẤN & HỐ KHOAN SÂU!
                if (task.ageTicks == IMPACT_TICK && !task.slammed) {
                    task.slammed = true;


                    // VFX sóng xung kích mặt đất & cột dung nham phụt lên từ miệng lỗ khoan
                    // BỎ hoàn toàn ParticleTypes.FLASH theo yêu cầu để tránh lóa trắng màn hình, dùng tia chớp điện Ruby sắc bén
                    Box slamBox = new Box(
                            task.center.getX() - task.radius * 1.45,
                            task.center.getY() - task.craterDepth - 4.0,
                            task.center.getZ() - task.radius * 1.45,
                            task.center.getX() + task.radius * 1.45,
                            task.center.getY() + task.radius * 0.8,
                            task.center.getZ() + task.radius * 1.45
                    );
                    for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, slamBox, ent -> ent != task.caster)) {
                        double dist = e.getPos().distanceTo(task.center.toCenterPos());
                        float dmg = (float) Math.max(35.0, ((1.0 - (dist / (task.radius * 1.45))) * 75.0 + 25.0) * task.damageMult);
                        e.damage(world.getDamageSources().fallingBlock(task.caster), (e instanceof net.minecraft.entity.mob.MobEntity ? 2.0f : 1.0f) * (dmg));

                        Vec3d diff = e.getPos().subtract(task.center.toCenterPos()).normalize().multiply(2.2 * task.damageMult).add(0, 0.85, 0);
                        e.addVelocity(diff.x, diff.y, diff.z);
                        e.velocityModified = true;
                    }

                    // Shader Ground Seismic Shockwave & Camera Shake
                    ImpactFxPayload slamPayload = new ImpactFxPayload(
                            task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5,
                            ImpactFxPayload.TYPE_SEISMIC_SLAM, 34, 2.2f * task.damageMult
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
                    task.craterCursor = new SupernovaCraterCursor(task.craterRadius);
                }

                // Tại Tick 172: Xóa các BlockDisplay của mũi khoan (đã chui sâu vào lòng đất) và bắn đá nóng chảy văng lên miệng hố
                if (task.ageTicks == 172) {
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
            // PHA 5: KẾT THÚC (Tick 177+)
            // =========================================================================
            else if (task.craterCursor == null || task.craterCursor.finished()) {
                it.remove();
            }
            // Let the impact packet leave before excavating, then bound work during the shockwave.
            if (task.ageTicks > IMPACT_TICK && task.craterCursor != null && !task.craterCursor.finished()) {
                carveCrater(task);
            }
        }
    }

    private static void carveCrater(RatioCollapseTask task) {
        ServerWorld world = task.world;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        boolean changedRecords = false;
        for (int column = 0; column < 96 && !task.craterCursor.finished(); column++) {
            int x = task.craterCursor.x(), z = task.craterCursor.z();
            task.craterCursor.advance();
            if (x * x + z * z > task.craterRadius * task.craterRadius) continue;
            pos.set(task.center.getX() + x, task.center.getY(), task.center.getZ() + z);
            if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            double distance = Math.hypot(x, z);
            double groove = Math.sin(Math.atan2(z, x) * 3 + distance * .85);
            int depth = drillCraterDepth(x, z, task.craterRadius, task.craterDepth);
            for (int y = 3; y >= -depth; y--) {
                pos.setY(task.center.getY() + y);
                if (!world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos)) continue;
                BlockState state = world.getBlockState(pos);
                DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                if (record != null) state = record.originalState;
                if (state.isAir() || state.getHardness(world, pos) < 0) continue;
                if (record != null) {
                    SpatialDisplacementManager.removeActiveRecord(world.getRegistryKey(), pos);
                    changedRecords = true;
                }
                BlockState replacement = Blocks.AIR.getDefaultState();
                if (y == -depth) {
                    double heat = SpatialMath.hash01(pos);
                    replacement = (groove > .15 ? Blocks.MAGMA_BLOCK : heat < .45 ? Blocks.BLACKSTONE
                            : heat < .86 ? Blocks.COBBLESTONE : Blocks.OBSIDIAN).getDefaultState();
                }
                // Bulk excavation must not cascade neighbor physics for every removed block.
                world.setBlockState(pos, replacement, Block.FORCE_STATE | Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
            }
        }
        if (changedRecords) net.godwand.spatial.DisplacementPersistentState.getServerState(world).markDirty();
    }

    public static int drillCraterDepth(int x, int z, int radius, int maxDepth) {
        double distance = Math.hypot(x, z);
        double edge = Math.max(0, 1 - distance / radius);
        return (int)Math.round(Math.pow(edge, 1.55) * maxDepth
                + edge * Math.sin(Math.atan2(z, x) * 3 + distance * .85) * 1.5);
    }

    public static double drillHoverHeight(double radius) {
        return 18.0 + radius * 2.2 + Math.max(0, radius - 5.0) * 2.0;
    }

    public static float drillWindVisibility(float speed) {
        float t = MathHelper.clamp((speed - 0.50f) / 0.10f, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static float drillHeat(float speed) {
        return MathHelper.clamp((speed - 0.50f) / 0.35f, 0, 1);
    }

    private static void sendDrillWind(RatioCollapseTask task, Vec3d center, float speed) {
        var payload = new net.godwand.network.DrillWindPayload(task.center.asLong(), center.x, center.y, center.z,
                (float) task.boulderRadius, task.orbitAngle, speed);
        for (ServerPlayerEntity player : PlayerLookup.around(task.world, center, 128.0)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static int getActiveTaskCount() {
        return ACTIVE_TASKS.size();
    }

    public static void clearAllTasks() {
        ACTIVE_TASKS.clear();
    }
}
