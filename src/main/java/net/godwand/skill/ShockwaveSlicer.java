package net.godwand.skill;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.ImpactFxPayload;
import net.godwand.spatial.DisplacedBlockRecord;
import net.godwand.spatial.SpatialDisplacementManager;
import net.godwand.spatial.SpatialMath;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ShockwaveSlicer {

    public static class SingularityTask {
        public final ServerWorld world;
        public final BlockPos center;
        public final double maxRadius;
        public final Vec3d orbCenter;

        public final List<BlockPos> displayedPositions = new ArrayList<>();
        public final List<BlockPos> interiorPositions = new ArrayList<>();
        public final Map<BlockPos, Vec3d> fullExpansionMap = new HashMap<>();
        public final Map<BlockPos, Quaternionf> baseRotationMap = new HashMap<>();
        public final Map<BlockPos, Float> blockScaleMap = new HashMap<>();

        // Lưu lại trạng thái thực tế của từng block trước khi thu về để không bị giật
        public final Map<BlockPos, Vec3d> lastOffsetMap = new HashMap<>();
        public final Map<BlockPos, Quaternionf> lastRotationMap = new HashMap<>();

        public int ageTicks = 0;
        public final int maxLifetimeTicks = 132;
        public boolean snappedBack = false;
        public boolean restored = false;

        public SingularityTask(ServerWorld world, BlockPos center, double maxRadius, Vec3d orbCenter) {
            this.world = world;
            this.center = center.toImmutable();
            this.maxRadius = maxRadius;
            this.orbCenter = orbCenter;
        }
    }

    private static final Queue<SingularityTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();

    private static boolean isExposedSurfaceBlock(ServerWorld world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockState neighbor = world.getBlockState(pos.offset(dir));
            if (neighbor.isAir() || !neighbor.isOpaqueFullCube(world, pos.offset(dir))) {
                return true;
            }
        }
        return false;
    }

    public static void queueCalamityDetonation(ServerWorld world, BlockPos center, double maxRadius) {
        Vec3d orbCenter = center.toCenterPos().add(0, 3.8 + maxRadius * 0.35, 0);
        SingularityTask task = new SingularityTask(world, center, maxRadius, orbCenter);

        int intR = (int) Math.ceil(maxRadius);
        double maxRadiusSq = maxRadius * maxRadius;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        List<BlockPos> surfaceCandidates = new ArrayList<>();
        List<BlockPos> deepCandidates = new ArrayList<>();

        // 1. Quét và phân loại: Ưu tiên 100% các block bề mặt (Surface Shell) để lúc ghép lại khớp kín miệng hố 1:1
        for (int x = -intR; x <= intR; x++) {
            for (int y = -intR; y <= intR; y++) {
                int targetY = center.getY() + y;
                if (targetY < world.getBottomY() || targetY >= world.getTopY()) continue;
                for (int z = -intR; z <= intR; z++) {
                    double dSq = x * x + y * y + z * z;
                    if (dSq > maxRadiusSq) continue;

                    mutable.set(center.getX() + x, targetY, center.getZ() + z);
                    BlockState state = world.getBlockState(mutable);
                    if (!SpatialDisplacementManager.canDisplace(world, mutable, state)) continue;

                    BlockPos imm = mutable.toImmutable();
                    if (isExposedSurfaceBlock(world, imm)) {
                        surfaceCandidates.add(imm);
                    } else {
                        deepCandidates.add(imm);
                    }
                }
            }
        }

        Random rng = new Random(center.asLong());
        Collections.shuffle(surfaceCandidates, rng);
        Collections.shuffle(deepCandidates, rng);

        List<BlockPos> candidatePositions = new ArrayList<>(surfaceCandidates.size() + deepCandidates.size());
        candidatePositions.addAll(surfaceCandidates);
        candidatePositions.addAll(deepCandidates);

        int maxDisplays = Math.min(600, candidatePositions.size());

        for (int i = 0; i < candidatePositions.size(); i++) {
            BlockPos pos = candidatePositions.get(i);
            boolean isDisplay = (i < maxDisplays);

            if (isDisplay) {
                float hashVal = (float) SpatialMath.hash01(pos);
                float hashVal2 = (float) SpatialMath.hash01(pos.up());

                // QUỸ ĐẠO XUYÊN TÂM ĐỒNG BỘ VỊ TRÍ GỐC (TRUE RADIAL + DOME ELEVATION):
                // Giữ nguyên góc phương vị ngang (azimuth) từ tâm hố tới đúng vị trí block đó,
                // giúp block ở phía nào bay ra đúng phía đó và khi thu về khớp 1:1 không bị bay chéo hỗn độn!
                double dx = (pos.getX() + 0.5) - (center.getX() + 0.5);
                double dz = (pos.getZ() + 0.5) - (center.getZ() + 0.5);
                double baseAzimuth = Math.atan2(dz, dx) + (hashVal - 0.5) * 0.22;

                // Trải đều góc nâng phi từ [-pi/3, +pi/2.05] để có đầy đủ block bao phủ cả TRÊN ĐỈNH, NGANG và DƯỚI quả cầu
                double phi = (hashVal2 * 1.45 - 0.55) * (Math.PI * 0.5);
                double expandRadius = maxRadius * (1.15 + hashVal * 0.95);

                Vec3d sphereDir = new Vec3d(
                        Math.cos(phi) * Math.cos(baseAzimuth),
                        Math.sin(phi),
                        Math.cos(phi) * Math.sin(baseAzimuth)
                ).normalize();

                Vec3d targetWorldPos = orbCenter.add(sphereDir.multiply(expandRadius));
                double minHoverY = center.getY() + 1.0 + hashVal2 * 2.5;
                if (targetWorldPos.y < minHoverY) {
                    targetWorldPos = new Vec3d(targetWorldPos.x, minHoverY, targetWorldPos.z);
                }

                Vec3d fullExpansionVec = targetWorldPos.subtract(Vec3d.ofCenter(pos));

                float scale = 0.92f + hashVal * 0.48f;
                Quaternionf rot = new Quaternionf()
                        .rotateY((float) (hashVal * Math.PI * 2.0))
                        .rotateX((float) Math.toRadians(25.0 + hashVal * 35.0));

                SpatialDisplacementManager.displaceBlock(
                        world,
                        pos,
                        new Vector3f(0, 0, 0),
                        rot,
                        new Vector3f(scale, scale, scale),
                        140,
                        0,
                        true,
                        DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
                        null,
                        center.toCenterPos(),
                        maxRadius
                );

                task.displayedPositions.add(pos);
                task.fullExpansionMap.put(pos, fullExpansionVec);
                task.baseRotationMap.put(pos, rot);
                task.blockScaleMap.put(pos, scale);
                task.lastOffsetMap.put(pos, Vec3d.ZERO);
                task.lastRotationMap.put(pos, rot);
            } else {
                SpatialDisplacementManager.displaceBlock(
                        world,
                        pos,
                        new Vector3f(0, 0, 0),
                        new Quaternionf(),
                        new Vector3f(1.0f, 1.0f, 1.0f),
                        140,
                        0,
                        false,
                        DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
                        null,
                        center.toCenterPos(),
                        maxRadius
                );
                task.interiorPositions.add(pos);
            }
        }

        // Audio & Visuals nổ khai cuộc
        world.spawnParticles(ParticleTypes.FLASH, orbCenter.x, orbCenter.y, orbCenter.z, 4, 0.2, 0.2, 0.2, 0.0);
        world.spawnParticles(ParticleTypes.SONIC_BOOM, orbCenter.x, orbCenter.y, orbCenter.z, 2, 0.1, 0.1, 0.1, 0.0);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 3, 1.0, 1.0, 1.0, 0.0);
        world.spawnParticles(new DustParticleEffect(new Vector3f(0.0f, 0.95f, 1.0f), 1.8f),
                orbCenter.x, orbCenter.y, orbCenter.z, 60, 1.5, 1.5, 1.5, 0.45);

        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 3.5f, 0.6f);
        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 3.0f, 0.8f);
        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.PLAYERS, 2.8f, 0.7f);

        Box blastBox = new Box(center).expand(maxRadius * 1.25);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, blastBox, ent -> true)) {
            Vec3d delta = e.getPos().subtract(center.toCenterPos());
            double dist = delta.length();
            if (dist < maxRadius * 1.25 && dist > 0.1) {
                double force = (1.0 - (dist / (maxRadius * 1.25))) * 2.2;
                Vec3d push = delta.normalize().multiply(force).add(0, 0.55, 0);
                e.addVelocity(push.x, push.y, push.z);
                e.velocityModified = true;
            }
        }

        ImpactFxPayload detPayload = new ImpactFxPayload(
                task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                ImpactFxPayload.TYPE_DETONATION, 112, (float) Math.max(1.0, maxRadius / 10.0)
        );
        CinematicFxPayload detCinematic = new CinematicFxPayload(
                CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                0.65f, 16
        );
        for (ServerPlayerEntity p : PlayerLookup.around(world, task.orbCenter, 96.0)) {
            ServerPlayNetworking.send(p, detPayload);
            ServerPlayNetworking.send(p, detCinematic);
        }

        ACTIVE_TASKS.add(task);
    }

    /**
     * Vẽ 3 Vòng Quỹ Đạo Particle (3-Ring Astrolabe Gimbal) bay vòng vòng cực lực quanh Quả Cầu Năng Lượng
     */
    private static void spawnThreeOrbitalParticleRings(ServerWorld world, Vec3d center, int ageTicks, double radius) {
        double baseSpeed = ageTicks * 0.28;
        int pointsPerRing = 6;

        for (int ring = 0; ring < 3; ring++) {
            double rotX = (ring == 0) ? Math.toRadians(38.0) : (ring == 1) ? Math.toRadians(-42.0) : Math.toRadians(15.0);
            double rotZ = (ring == 0) ? Math.toRadians(22.0) : (ring == 1) ? Math.toRadians(35.0) : Math.toRadians(-55.0);
            double dir = (ring == 1) ? -1.35 : (ring == 2) ? 1.15 : 1.0;

            Vector3f ringColor = switch (ring) {
                case 0 -> new Vector3f(0.0f, 0.96f, 1.0f);   // Điện quang Cyan
                case 1 -> new Vector3f(0.90f, 0.98f, 1.0f);  // Trắng sáng lõi
                default -> new Vector3f(0.68f, 0.22f, 1.0f); // Tím Arcane
            };

            for (int i = 0; i < pointsPerRing; i++) {
                double theta = baseSpeed * dir + (i * 2.0 * Math.PI / pointsPerRing);
                double lx = Math.cos(theta) * radius;
                double lz = Math.sin(theta) * radius;
                double ly = 0.0;

                // Xoay nghiêng 3D theo trục Z và X
                double x1 = lx * Math.cos(rotZ) - ly * Math.sin(rotZ);
                double y1 = lx * Math.sin(rotZ) + ly * Math.cos(rotZ);
                double z1 = lz;

                double fx = x1;
                double fy = y1 * Math.cos(rotX) - z1 * Math.sin(rotX);
                double fz = y1 * Math.sin(rotX) + z1 * Math.cos(rotX);

                world.spawnParticles(new DustParticleEffect(ringColor, 1.35f),
                        center.x + fx, center.y + fy, center.z + fz,
                        1, 0.02, 0.02, 0.02, 0.0);

                if (i % 2 == 0) {
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                            center.x + fx, center.y + fy, center.z + fz,
                            1, 0.03, 0.03, 0.03, 0.02);
                }
            }
        }
    }

    public static void tick(ServerWorld world) {
        Iterator<SingularityTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            SingularityTask task = it.next();
            if (task.world != world) continue;

            task.ageTicks++;

            // Duy trì 3 vòng Particle bay tốc độ cao quanh khối cầu năng lượng từ lúc nổ đến lúc thu về
            if (task.ageTicks <= 112) {
                double ringR = (task.ageTicks <= 105) ? 1.55 : Math.max(0.2, 1.55 * (1.0 - (task.ageTicks - 105) / 7.0));
                spawnThreeOrbitalParticleRings(world, task.orbCenter, task.ageTicks, ringR);
            }

            // =========================================================================
            // PHA 1: NỨT VỞ & BUNG NĂNG LƯỢNG (Ticks 1..22) - CUBIC EASE-OUT
            // =========================================================================
            if (task.ageTicks <= 22) {
                if (task.ageTicks % 2 == 0) {
                    float t = task.ageTicks / 22.0f;
                    double p = 1.0 - Math.pow(1.0 - t, 3.0);
                    updateBlockPositions(task, p, 2, false);
                }
            }

            // =========================================================================
            // PHA 2: LÀM CHẬM THỜI GIAN CỰC HẠN / BULLET-TIME DRIFT (Ticks 23..105)
            // Không bao giờ bị giật ở Tick 23 nhờ góc xoay nối tiếp liên tục từ 0.0!
            // =========================================================================
            else if (task.ageTicks <= 105) {
                if ((task.ageTicks - 22) % 4 == 0) {
                    double p = 1.0 + 0.06 * ((task.ageTicks - 22) / 83.0);
                    updateBlockPositions(task, p, 4, true);
                }
            }

            // =========================================================================
            // PHA 3: THU HỒI KHỚP 1:1 THEO LỚP TỪ DƯỚI LÊN (Ticks 106..132)
            // =========================================================================
            else {
                // Ticks 106..111: Nhích nhẹ lấy đà (+4%) từ ĐÚNG vị trí & góc xoay hiện tại (không reset góc xoay gây giật!)
                if (task.ageTicks == 106) {
                    world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 3.0f, 1.5f);

                    for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;
                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        Vec3d curOff = task.lastOffsetMap.getOrDefault(pos, task.fullExpansionMap.getOrDefault(pos, Vec3d.ZERO));
                        Quaternionf curRot = task.lastRotationMap.getOrDefault(pos, task.baseRotationMap.getOrDefault(pos, new Quaternionf()));
                        float scale = task.blockScaleMap.getOrDefault(pos, 1.0f);

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(5);
                        display.setTransformation(SpatialMath.createPivotTransform(curOff.multiply(1.04), curRot, scale));
                    }
                }

                // Ticks 112..130: Vacuum Snap-Back — Thu toàn bộ block về đúng ô gốc (0,0,0), góc xoay (0,0,0) và trả scale về đúng 1.0f!
                if (task.ageTicks == 112 && !task.snappedBack) {
                    task.snappedBack = true;

                    world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                            SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.BLOCKS, 3.5f, 1.6f);
                    world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                            SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 3.0f, 1.8f);

                    for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        // TRẢ SCALE VỀ ĐÚNG 1.0f ĐỂ KHỚP KHÍT TỪNG Ô GẠCH TRƯỚC KHI ĐẶT BLOCK THẬT!
                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(18);
                        display.setTransformation(SpatialMath.createPivotTransform(Vec3d.ZERO, new Quaternionf(), 1.0f));
                    }
                }

                // Tick 131: Dọn dẹp thực thể và khôi phục địa hình 1:1 ngay khi kết thúc nội suy 18 ticks (112 + 18 = 130)
                if (task.ageTicks >= 131 && !task.restored) {
                    task.restored = true;

                    List<DisplacedBlockRecord> toRestore = new ArrayList<>();
                    for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord rec = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (rec != null) toRestore.add(rec);
                    }
                    for (BlockPos pos : task.interiorPositions) {
                        DisplacedBlockRecord rec = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (rec != null) toRestore.add(rec);
                    }

                    if (!toRestore.isEmpty()) {
                        SpatialDisplacementManager.executeTwoPassRestoration(world, toRestore);
                    }

                    // Hiệu ứng chớp sáng & rung chấn nhẹ khi địa hình khớp khít 1:1 liền mạch
                    ImpactFxPayload snapFx = new ImpactFxPayload(task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                            ImpactFxPayload.TYPE_REASSEMBLY, 6, 1.0f);
                    CinematicFxPayload snapShake = new CinematicFxPayload(
                            CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                            task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                            0.38f, 7
                    );
                    CinematicFxPayload snapFov = new CinematicFxPayload(
                            CinematicFxPayload.EVENT_FOV_PUNCH,
                            task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                            1.06f, 5
                    );
                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.orbCenter, 64.0)) {
                        ServerPlayNetworking.send(p, snapFx);
                        ServerPlayNetworking.send(p, snapShake);
                        ServerPlayNetworking.send(p, snapFov);
                    }

                    world.spawnParticles(ParticleTypes.FLASH, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, 2, 0.2, 0.2, 0.2, 0.0);
                    for (int s = 0; s < 36; s++) {
                        double ang = (s / 36.0) * Math.PI * 2.0;
                        double dist = task.maxRadius * 0.75 + (world.random.nextDouble() - 0.5) * 3.0;
                        double px = task.center.getX() + 0.5 + Math.cos(ang) * dist;
                        double pz = task.center.getZ() + 0.5 + Math.sin(ang) * dist;
                        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, px, task.center.getY() + 1.1, pz, 1, 0, 0.1, 0, 0.04);
                    }

                    world.playSound(null, task.center, SoundEvents.BLOCK_VAULT_OPEN_SHUTTER, SoundCategory.BLOCKS, 2.8f, 1.3f);
                    world.playSound(null, task.center, SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.BLOCKS, 2.2f, 1.8f);
                    it.remove();
                }
            }
        }
    }

    private static void updateBlockPositions(SingularityTask task, double progress, int duration, boolean chaoticTumble) {
        int driftTicks = Math.max(0, task.ageTicks - 22);
        float smoothBlend = Math.min(1.0f, driftTicks / 20.0f);

        for (BlockPos pos : task.displayedPositions) {
            DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(task.world.getRegistryKey(), pos);
            if (record == null || record.displayEntityUuid == null) continue;

            BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
            if (display == null || !display.isAlive()) continue;

            Vec3d fullExp = task.fullExpansionMap.getOrDefault(pos, Vec3d.ZERO);
            Quaternionf rot = task.baseRotationMap.getOrDefault(pos, new Quaternionf());
            float scale = task.blockScaleMap.getOrDefault(pos, 1.0f);

            Vec3d curOffset;
            Quaternionf currentRot;

            if (chaoticTumble && driftTicks > 0) {
                // Bắt đầu từ 0.0 tại Tick 22 -> Tuyệt đối không bao giờ bị giật!
                float spinRate = 0.015f + (float) SpatialMath.hash01(pos) * 0.025f;
                float deltaAngle = driftTicks * spinRate;
                Vector3f tumbleAxis = new Vector3f(
                        (float) (SpatialMath.hash01(pos.north()) - 0.5),
                        (float) (SpatialMath.hash01(pos.up()) - 0.5),
                        (float) (SpatialMath.hash01(pos.east()) - 0.5)
                );
                if (tumbleAxis.lengthSquared() < 0.001f) tumbleAxis.set(0, 1, 0);
                tumbleAxis.normalize();

                currentRot = new Quaternionf(rot).rotateAxis(deltaAngle, tumbleAxis.x, tumbleAxis.y, tumbleAxis.z);

                double bob = Math.sin(driftTicks * 0.10 + SpatialMath.hash01(pos) * 6.28) * 0.22 * smoothBlend;
                curOffset = fullExp.multiply(progress).add(0, bob, 0);
            } else {
                curOffset = fullExp.multiply(progress);
                currentRot = rot;
            }

            task.lastOffsetMap.put(pos, curOffset);
            task.lastRotationMap.put(pos, currentRot);

            display.setStartInterpolation(0);
            display.setInterpolationDuration(duration);
            display.setTransformation(SpatialMath.createPivotTransform(curOffset, currentRot, scale));
        }
    }

    public static int getActiveTaskCount() {
        return ACTIVE_TASKS.size();
    }

    public static void clearAllTasks() {
        ACTIVE_TASKS.clear();
    }
}
