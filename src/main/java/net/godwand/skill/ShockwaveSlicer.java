package net.godwand.skill;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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

        public UUID innerCoreUuid;
        public UUID outerCoreUuid;

        public final List<BlockPos> displayedPositions = new ArrayList<>();
        public final List<BlockPos> interiorPositions = new ArrayList<>();
        public final Map<BlockPos, Vec3d> fullExpansionMap = new HashMap<>();
        public final Map<BlockPos, Quaternionf> baseRotationMap = new HashMap<>();
        public final Map<BlockPos, Float> blockScaleMap = new HashMap<>();
        public final Map<BlockPos, UUID> tetherBeamMap = new ConcurrentHashMap<>();

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
        Vec3d orbCenter = center.toCenterPos().add(0, 2.2 + maxRadius * 0.2, 0);
        SingularityTask task = new SingularityTask(world, center, maxRadius, orbCenter);

        // 1. Quả Cầu Năng Lượng Trung Tâm (2 BlockDisplayEntity lồng nhau)
        // Lõi trong: SEA_LANTERN 1.3f phát sáng Cyan (0x00F0FF)
        BlockDisplayEntity innerCore = new BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
        innerCore.addCommandTag("godwand_displaced");
        innerCore.setPosition(orbCenter.x - 0.5, orbCenter.y - 0.5, orbCenter.z - 0.5);
        innerCore.setBlockState(Blocks.SEA_LANTERN.getDefaultState());
        innerCore.setGlowing(true);
        innerCore.setGlowColorOverride(0x00F0FF);
        innerCore.setBrightness(new Brightness(15, 15));
        innerCore.setStartInterpolation(0);
        innerCore.setInterpolationDuration(0);
        innerCore.setTransformation(SpatialMath.createPivotTransform(Vec3d.ZERO, new Quaternionf(), 1.3f));
        world.spawnEntity(innerCore);
        task.innerCoreUuid = innerCore.getUuid();
        SpatialDisplacementManager.trackEntity(innerCore.getUuid(), innerCore);

        // Vỏ ngoài: PURPLE_STAINED_GLASS 1.9f phát sáng Tím (0xA855F7)
        BlockDisplayEntity outerCore = new BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
        outerCore.addCommandTag("godwand_displaced");
        outerCore.setPosition(orbCenter.x - 0.5, orbCenter.y - 0.5, orbCenter.z - 0.5);
        outerCore.setBlockState(Blocks.PURPLE_STAINED_GLASS.getDefaultState());
        outerCore.setGlowing(true);
        outerCore.setGlowColorOverride(0xA855F7);
        outerCore.setBrightness(new Brightness(15, 15));
        outerCore.setStartInterpolation(0);
        outerCore.setInterpolationDuration(0);
        outerCore.setTransformation(SpatialMath.createPivotTransform(Vec3d.ZERO, new Quaternionf(), 1.9f));
        world.spawnEntity(outerCore);
        task.outerCoreUuid = outerCore.getUuid();
        SpatialDisplacementManager.trackEntity(outerCore.getUuid(), outerCore);

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

        // Trộn ngẫu nhiên trước khi chia tầng bán kính để 180 khối và 45 tia phân bổ đều 360 độ
        Collections.shuffle(candidatePositions, new Random(center.asLong()));
        candidatePositions.sort((a, b) -> {
            int bandA = (int) (Math.sqrt(a.getSquaredDistance(task.center)) / 2.0);
            int bandB = (int) (Math.sqrt(b.getSquaredDistance(task.center)) / 2.0);
            return Integer.compare(bandB, bandA);
        });

        int maxDisplays = Math.min(180, candidatePositions.size());
        int tetherLimit = Math.min(45, maxDisplays);

        for (int i = 0; i < candidatePositions.size(); i++) {
            BlockPos pos = candidatePositions.get(i);
            boolean isDisplay = (i < maxDisplays);

            if (isDisplay) {
                Vec3d radialDir = Vec3d.ofCenter(pos).subtract(orbCenter);
                if (radialDir.lengthSquared() < 0.01) radialDir = new Vec3d(0, 1, 0);
                radialDir = radialDir.normalize();

                double maxExpandDist = maxRadius * (0.45 + SpatialMath.hash01(pos) * 0.55);
                Vec3d fullExpansionVec = new Vec3d(
                        radialDir.x * maxExpandDist,
                        Math.max(1.8, radialDir.y * maxExpandDist + 3.2 + SpatialMath.hash01(pos) * 3.5),
                        radialDir.z * maxExpandDist
                );

                float hashVal = (float) SpatialMath.hash01(pos);
                float scale = 0.55f + hashVal * 0.40f;
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

                // Tạo Tia Năng Lượng Siêu Mỏng 3D (Tether Beam) cho 45 khối đầu tiên
                if (i < tetherLimit) {
                    BlockDisplayEntity beam = new BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
                    beam.addCommandTag("godwand_displaced");
                    beam.setPosition(orbCenter.x, orbCenter.y, orbCenter.z);
                    beam.setBlockState(Blocks.SEA_LANTERN.getDefaultState());
                    beam.setGlowing(true);
                    beam.setGlowColorOverride(0x00F5FF);
                    beam.setBrightness(new Brightness(15, 15));
                    beam.setStartInterpolation(0);
                    beam.setInterpolationDuration(0);
                    beam.setTransformation(SpatialMath.createThinBeamTransform(orbCenter, Vec3d.ofCenter(pos), 0.032f));
                    world.spawnEntity(beam);
                    task.tetherBeamMap.put(pos, beam.getUuid());
                    SpatialDisplacementManager.trackEntity(beam.getUuid(), beam);
                }
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
                        DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE,
                        null,
                        null,
                        0.0
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
                center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                ImpactFxPayload.TYPE_DETONATION, 15, (float) Math.max(1.0, maxRadius / 10.0)
        );
        for (ServerPlayerEntity p : PlayerLookup.around(world, center.toCenterPos(), 96.0)) {
            ServerPlayNetworking.send(p, detPayload);
        }

        ACTIVE_TASKS.add(task);
    }

    public static void tick(ServerWorld world) {
        Iterator<SingularityTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            SingularityTask task = it.next();
            if (task.world != world) continue;

            task.ageTicks++;

            BlockDisplayEntity innerCore = SpatialDisplacementManager.getActiveEntity(task.innerCoreUuid);
            BlockDisplayEntity outerCore = SpatialDisplacementManager.getActiveEntity(task.outerCoreUuid);

            // Xoay 2 lớp quả cầu trung tâm mỗi 2 ticks
            if (task.ageTicks % 2 == 0 && task.ageTicks < 112) {
                float spin = task.ageTicks * 0.05f;
                Quaternionf innerRot = new Quaternionf().rotateY(spin * 1.5f).rotateX(spin);
                Quaternionf outerRot = new Quaternionf().rotateY(-spin * 1.2f).rotateZ(-spin * 0.8f);

                if (innerCore != null && innerCore.isAlive()) {
                    innerCore.setStartInterpolation(0);
                    innerCore.setInterpolationDuration(2);
                    innerCore.setTransformation(SpatialMath.createPivotTransform(Vec3d.ZERO, innerRot, 1.3f));
                }
                if (outerCore != null && outerCore.isAlive()) {
                    outerCore.setStartInterpolation(0);
                    outerCore.setInterpolationDuration(2);
                    outerCore.setTransformation(SpatialMath.createPivotTransform(Vec3d.ZERO, outerRot, 1.9f));
                }
            }

            // =========================================================================
            // PHA 1: NỨT VỠ & BUNG NĂNG LƯỢNG (Ticks 1..18)
            // =========================================================================
            if (task.ageTicks <= 18) {
                if (task.ageTicks % 2 == 0) {
                    double p = 0.65 * Math.sin((task.ageTicks / 18.0) * (Math.PI / 2.0));
                    updatePositionsAndTethers(task, p, 2, 0.032f, 0x00F5FF);
                }
            }

            // =========================================================================
            // PHA 2: LÀM CHẬM THỜI GIAN CỰC HẠN / BULLET-TIME DRIFT (Ticks 19..105)
            // =========================================================================
            else if (task.ageTicks <= 105) {
                // Trôi dạt cực chậm theo hàm p = 0.65 -> 1.00 trong 87 ticks
                if ((task.ageTicks - 18) % 4 == 0) {
                    double p = 0.65 + 0.35 * ((task.ageTicks - 18) / 87.0);
                    updatePositionsAndTethers(task, p, 4, 0.032f, 0x00F5FF);
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
            // PHA 3: CO RÚT & THU HỒI NGƯỢC (Ticks 106..130)
            // =========================================================================
            else {
                // Ticks 106..111: Anticipation (Căng tia năng lượng, kéo dạt thêm +6%)
                if (task.ageTicks == 106) {
                    world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 3.0f, 1.5f);
                    updatePositionsAndTethers(task, 1.06, 5, 0.045f, 0xA855F7);
                }

                // Ticks 112..126: Vacuum Snap-Back (Quả cầu co nhỏ, tia năng lượng kéo giật về 0,0,0)
                if (task.ageTicks == 112 && !task.snappedBack) {
                    task.snappedBack = true;

                    world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                            SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.BLOCKS, 3.5f, 1.6f);
                    world.playSound(null, task.orbCenter.x, task.orbCenter.y, task.orbCenter.z,
                            SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 3.0f, 1.8f);

                    // Co rút quả cầu trung tâm về 0.05f trong 12 ticks
                    if (innerCore != null && innerCore.isAlive()) {
                        innerCore.setStartInterpolation(0);
                        innerCore.setInterpolationDuration(12);
                        innerCore.setTransformation(SpatialMath.createPivotTransform(Vec3d.ZERO, new Quaternionf(), 0.05f));
                    }
                    if (outerCore != null && outerCore.isAlive()) {
                        outerCore.setStartInterpolation(0);
                        outerCore.setInterpolationDuration(12);
                        outerCore.setTransformation(SpatialMath.createPivotTransform(Vec3d.ZERO, new Quaternionf(), 0.05f));
                    }

                    // Giật ngược toàn bộ khối đá và tia laser về vị trí ban đầu trong 14 ticks
                    for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        float scale = task.blockScaleMap.getOrDefault(pos, 1.0f);
                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(14);
                        display.setTransformation(SpatialMath.createPivotTransform(Vec3d.ZERO, new Quaternionf(), scale));

                        UUID beamUuid = task.tetherBeamMap.get(pos);
                        if (beamUuid != null) {
                            BlockDisplayEntity beam = SpatialDisplacementManager.getActiveEntity(beamUuid);
                            if (beam != null && beam.isAlive()) {
                                beam.setStartInterpolation(0);
                                beam.setInterpolationDuration(14);
                                beam.setTransformation(SpatialMath.createThinBeamTransform(task.orbCenter, Vec3d.ofCenter(pos), 0.015f));
                            }
                        }
                    }
                }

                // Tick 128: Dọn dẹp thực thể và khôi phục địa hình 1:1
                if (task.ageTicks >= 128 && !task.restored) {
                    task.restored = true;

                    // Hủy tất cả tia laser
                    for (UUID beamUuid : task.tetherBeamMap.values()) {
                        BlockDisplayEntity b = SpatialDisplacementManager.getActiveEntity(beamUuid);
                        if (b != null && b.isAlive()) {
                            b.discard();
                        }
                        SpatialDisplacementManager.untrackEntity(beamUuid);
                    }
                    task.tetherBeamMap.clear();

                    // Hủy lõi quả cầu
                    if (innerCore != null && innerCore.isAlive()) {
                        innerCore.discard();
                        SpatialDisplacementManager.untrackEntity(task.innerCoreUuid);
                    }
                    if (outerCore != null && outerCore.isAlive()) {
                        outerCore.discard();
                        SpatialDisplacementManager.untrackEntity(task.outerCoreUuid);
                    }

                    // Khôi phục 2-pass toàn bộ khối
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

    private static void updatePositionsAndTethers(SingularityTask task, double progress, int duration, float beamThickness, int beamGlow) {
        for (BlockPos pos : task.displayedPositions) {
            DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(task.world.getRegistryKey(), pos);
            if (record == null || record.displayEntityUuid == null) continue;

            BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
            if (display == null || !display.isAlive()) continue;

            Vec3d fullExp = task.fullExpansionMap.getOrDefault(pos, Vec3d.ZERO);
            Vec3d curOffset = fullExp.multiply(progress);

            Quaternionf rot = task.baseRotationMap.getOrDefault(pos, new Quaternionf());
            float scale = task.blockScaleMap.getOrDefault(pos, 1.0f);

            display.setStartInterpolation(0);
            display.setInterpolationDuration(duration);
            display.setTransformation(SpatialMath.createPivotTransform(curOffset, rot, scale));

            UUID beamUuid = task.tetherBeamMap.get(pos);
            if (beamUuid != null) {
                BlockDisplayEntity beam = SpatialDisplacementManager.getActiveEntity(beamUuid);
                if (beam != null && beam.isAlive()) {
                    Vec3d blockWorldPos = Vec3d.ofCenter(pos).add(curOffset);
                    beam.setGlowColorOverride(beamGlow);
                    beam.setStartInterpolation(0);
                    beam.setInterpolationDuration(duration);
                    beam.setTransformation(SpatialMath.createThinBeamTransform(task.orbCenter, blockWorldPos, beamThickness));
                }
            }
        }
    }

    public static int getActiveTaskCount() {
        return ACTIVE_TASKS.size();
    }

    public static void clearAllTasks() {
        for (SingularityTask task : ACTIVE_TASKS) {
            for (UUID beamUuid : task.tetherBeamMap.values()) {
                BlockDisplayEntity b = SpatialDisplacementManager.getActiveEntity(beamUuid);
                if (b != null && b.isAlive()) {
                    b.discard();
                }
                SpatialDisplacementManager.untrackEntity(beamUuid);
            }
            task.tetherBeamMap.clear();

            if (task.innerCoreUuid != null) {
                BlockDisplayEntity inner = SpatialDisplacementManager.getActiveEntity(task.innerCoreUuid);
                if (inner != null && inner.isAlive()) inner.discard();
                SpatialDisplacementManager.untrackEntity(task.innerCoreUuid);
            }
            if (task.outerCoreUuid != null) {
                BlockDisplayEntity outer = SpatialDisplacementManager.getActiveEntity(task.outerCoreUuid);
                if (outer != null && outer.isAlive()) outer.discard();
                SpatialDisplacementManager.untrackEntity(task.outerCoreUuid);
            }
        }
        ACTIVE_TASKS.clear();
    }
}
