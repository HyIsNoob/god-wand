package net.godwand.skill;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.ImpactFxPayload;
import net.godwand.spatial.DisplacedBlockRecord;
import net.godwand.spatial.SpatialDisplacementManager;
import net.godwand.spatial.SpatialMath;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

        public int ageTicks = 0;
        public final int maxLifetimeTicks = 130;
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

    public static void queueCalamityDetonation(ServerWorld world, BlockPos center, double maxRadius) {
        Vec3d orbCenter = center.toCenterPos().add(0, 3.8 + maxRadius * 0.35, 0);
        SingularityTask task = new SingularityTask(world, center, maxRadius, orbCenter);

        // 1. Quả cầu năng lượng trung tâm được kết xuất trực tiếp bằng 3D Raymarched Shader (TYPE_DETONATION)
        // 2. Bóc tách các khối đất đá trong hình cầu maxRadius
        int intR = (int) Math.ceil(maxRadius);
        double maxRadiusSq = maxRadius * maxRadius;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        List<BlockPos> candidatePositions = new ArrayList<>();
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

                    candidatePositions.add(mutable.toImmutable());
                }
            }
        }

        // Trộn ngẫu nhiên trước khi chia tầng bán kính để 500 khối phân bổ đều 360 độ quanh tâm
        Collections.shuffle(candidatePositions, new Random(center.asLong()));
        candidatePositions.sort((a, b) -> {
            int bandA = (int) (Math.sqrt(a.getSquaredDistance(task.center)) / 2.0);
            int bandB = (int) (Math.sqrt(b.getSquaredDistance(task.center)) / 2.0);
            return Integer.compare(bandB, bandA);
        });

        int maxDisplays = Math.min(500, candidatePositions.size());

        for (int i = 0; i < candidatePositions.size(); i++) {
            BlockPos pos = candidatePositions.get(i);
            boolean isDisplay = (i < maxDisplays);

            if (isDisplay) {
                // Phân bổ Cầu Ngẫu Nhiên 3D (Spherical Fibonacci + Chaos Noise) quanh orbCenter
                // Góc nâng phi trải đều [-pi/2, +pi/2] để 50% khối bắn vọt lên TRÊN quả cầu (cao hơn 3m - 14m)
                double goldenRatio = (1.0 + Math.sqrt(5.0)) / 2.0;
                double phi = Math.asin(1.0 - 2.0 * (i + 0.5) / maxDisplays);
                double theta = 2.0 * Math.PI * i / goldenRatio;
                double chaosJitter = SpatialMath.hash01(pos) * 0.35 - 0.175;
                phi = MathHelper.clamp(phi + chaosJitter, -Math.PI * 0.48, Math.PI * 0.48);

                double expandRadius = maxRadius * (1.35 + SpatialMath.hash01(pos) * 1.0);
                Vec3d sphereDir = new Vec3d(
                        Math.cos(phi) * Math.cos(theta),
                        Math.sin(phi),
                        Math.cos(phi) * Math.sin(theta)
                ).normalize();

                Vec3d targetWorldPos = orbCenter.add(sphereDir.multiply(expandRadius));
                // Đảm bảo các mảnh vỡ nửa dưới cầu không bị chôn sâu dưới lòng đất mà lơ lửng quanh miệng hố
                double minHoverY = center.getY() + 1.2 + SpatialMath.hash01(pos.up()) * 3.0;
                if (targetWorldPos.y < minHoverY) {
                    targetWorldPos = new Vec3d(
                            targetWorldPos.x,
                            minHoverY + (minHoverY - targetWorldPos.y) * 0.25,
                            targetWorldPos.z
                    );
                }
                Vec3d fullExpansionVec = targetWorldPos.subtract(Vec3d.ofCenter(pos));

                float hashVal = (float) SpatialMath.hash01(pos);
                // Kích thước Block to rõ hầm hố: 0.90x đến 1.45x
                float scale = 0.90f + hashVal * 0.55f;
                Quaternionf rot = new Quaternionf()
                        .rotateY((float) (hashVal * Math.PI * 2.0))
                        .rotateX((float) Math.toRadians(25.0 + hashVal * 30.0));

                int glowColor = (hashVal > 0.5f) ? 0x00F0FF : 0xA855F7;

                // Bóc khối ở chế độ CUSTOM_SCRIPTED để script kiểm soát 100%
                SpatialDisplacementManager.displaceBlock(
                        world,
                        pos,
                        new Vector3f(0, 0, 0),
                        rot,
                        new Vector3f(scale, scale, scale),
                        135,
                        glowColor,
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
            } else {
                // Các khối bên trong lòng hố chỉ bóc sang AIR không tạo display
                SpatialDisplacementManager.displaceBlock(
                        world,
                        pos,
                        new Vector3f(0, 0, 0),
                        new Quaternionf(),
                        new Vector3f(1.0f, 1.0f, 1.0f),
                        135,
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

        // 3. Audio & Visuals nổ khai cuộc
        world.spawnParticles(ParticleTypes.FLASH, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.0);
        world.spawnParticles(ParticleTypes.SONIC_BOOM, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0.0);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 3, 1.0, 1.0, 1.0, 0.0);
        world.spawnParticles(new DustParticleEffect(new Vector3f(0.0f, 0.95f, 1.0f), 1.8f),
                center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 60, 1.5, 1.5, 1.5, 0.45);

        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 3.5f, 0.6f);
        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 3.0f, 0.8f);
        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.PLAYERS, 2.8f, 0.7f);

        // Đẩy quái vật xung quanh
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

    public static void tick(ServerWorld world) {
        Iterator<SingularityTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            SingularityTask task = it.next();
            if (task.world != world) continue;

            task.ageTicks++;

            // =========================================================================
            // PHA 1: NỨT VỠ & BUNG NĂNG LƯỢNG (Ticks 1..22) - MƯỢT MÀ VỚI CUBIC EASE-OUT
            // =========================================================================
            if (task.ageTicks <= 22) {
                if (task.ageTicks % 2 == 0) {
                    float t = task.ageTicks / 22.0f;
                    double p = 1.0 - Math.pow(1.0 - t, 3.0);
                    updateBlockPositions(task, p, 2, false);
                }
            }

            // =========================================================================
            // PHA 2: LÀM CHẬM THỜI GIAN CỰC HẠN / BULLET-TIME DRIFT & TETHER (Ticks 23..105)
            // =========================================================================
            else if (task.ageTicks <= 105) {
                // Trôi dạt và xoay hỗn loạn (Chaotic Tumble) trong không gian 3D
                if ((task.ageTicks - 22) % 4 == 0) {
                    double p = 1.0 + 0.08 * ((task.ageTicks - 22) / 83.0);
                    updateBlockPositions(task, p, 4, true);
                }

                // Dây năng lượng điện siêu mỏng nối từ tâm orbCenter tới các khối lơ lửng xung quanh
                int tetherBatch = Math.min(14, task.displayedPositions.size());
                for (int k = 0; k < tetherBatch; k++) {
                    int idx = (task.ageTicks * 5 + k * 23) % task.displayedPositions.size();
                    BlockPos pPos = task.displayedPositions.get(idx);
                    DisplacedBlockRecord rec = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pPos);
                    if (rec != null) {
                        Vec3d blockWorld = Vec3d.ofCenter(pPos).add(rec.getCurrentOffset());
                        Vec3d ray = blockWorld.subtract(task.orbCenter);
                        double rayLen = ray.length();
                        if (rayLen > 0.5) {
                            int steps = Math.min(5, (int) (rayLen / 3.0) + 2);
                            for (int s = 1; s < steps; s++) {
                                double frac = (double) s / steps;
                                Vec3d pt = task.orbCenter.add(ray.multiply(frac));
                                if ((k + s) % 2 == 0) {
                                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pt.x, pt.y, pt.z, 1, 0.02, 0.02, 0.02, 0.01);
                                } else {
                                    world.spawnParticles(new DustParticleEffect(new Vector3f(0.15f, 0.92f, 1.0f), 0.5f),
                                            pt.x, pt.y, pt.z, 1, 0.02, 0.02, 0.02, 0.0);
                                }
                            }
                        }
                    }
                }

                // Hiệu ứng ánh sáng ngưng đọng thời gian quanh quả cầu
                if (task.ageTicks % 6 == 0) {
                    world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                            task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, 6, 0.4, 0.4, 0.4, 0.05);
                    world.spawnParticles(ParticleTypes.END_ROD,
                            task.orbCenter.x, task.orbCenter.y, task.orbCenter.z, 2, task.maxRadius * 0.4, 1.2, task.maxRadius * 0.4, 0.01);
                }
            }

            // =========================================================================
            // PHA 3: CO RÚT & THU HỒI NGƯỢC (Ticks 106..132) - NỘI SUY MƯỢT MÀ
            // =========================================================================
            else {
                // Ticks 106..111: Anticipation (Kéo dạt thêm +6% lấy đà)
                if (task.ageTicks == 106) {
                    world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 3.0f, 1.5f);
                    updateBlockPositions(task, 1.10, 5, false);
                }

                // Ticks 112..130: Vacuum Snap-Back (Quả cầu co nhỏ, các khối lướt mượt về 0,0,0 trong 18 ticks)
                if (task.ageTicks == 112 && !task.snappedBack) {
                    task.snappedBack = true;

                    world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                            SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.BLOCKS, 3.5f, 1.6f);
                    world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                            SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 3.0f, 1.8f);

                    // Lướt mượt toàn bộ khối đá về vị trí ban đầu trong 18 ticks
                    for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        float scale = task.blockScaleMap.getOrDefault(pos, 1.0f);
                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(18);
                        display.setTransformation(SpatialMath.createPivotTransform(Vec3d.ZERO, new Quaternionf(), scale));
                    }
                }

                // Tick 132: Dọn dẹp thực thể và khôi phục địa hình 1:1
                if (task.ageTicks >= 132 && !task.restored) {
                    task.restored = true;

                    // Khôi phục 2-pass toàn bộ khối khớp kín miệng hố
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

                    world.playSound(null, task.center, SoundEvents.BLOCK_VAULT_OPEN_SHUTTER, SoundCategory.BLOCKS, 2.5f, 1.2f);
                    it.remove();
                }
            }
        }
    }

    private static void updateBlockPositions(SingularityTask task, double progress, int duration, boolean chaoticTumble) {
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

            if (chaoticTumble) {
                // Chuyển động xoay hỗn loạn (Chaotic Tumble) quanh trục ngẫu nhiên riêng
                float tumbleAngle = (float) (task.ageTicks * 0.05 + SpatialMath.hash01(pos) * 6.28);
                Vector3f tumbleAxis = new Vector3f(
                        (float) (SpatialMath.hash01(pos.north()) - 0.5),
                        (float) (SpatialMath.hash01(pos.up()) - 0.5),
                        (float) (SpatialMath.hash01(pos.east()) - 0.5)
                ).normalize();
                currentRot = new Quaternionf(rot).rotateAxis(tumbleAngle, tumbleAxis.x, tumbleAxis.y, tumbleAxis.z);

                // Dao động nhẹ bất quy tắc
                double bob = Math.sin(task.ageTicks * 0.12 + SpatialMath.hash01(pos) * 6.28) * 0.28;
                curOffset = fullExp.multiply(progress).add(0, bob, 0);
            } else {
                curOffset = fullExp.multiply(progress);
                currentRot = rot;
            }

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
