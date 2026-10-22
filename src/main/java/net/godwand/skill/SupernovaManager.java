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
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SupernovaManager {

    public static final int DETONATION_TICK = 360;
    public static final int MAX_LIFETIME_TICKS = 500;

    public static class SupernovaTask {
        public final ServerWorld world;
        public final ServerPlayerEntity caster;
        public final BlockPos groundCenter;
        public final Vec3d corePos;
        public final double maxRadius = 38.0; // Calamity scale!

        public int ageTicks = 0;
        public float swirlAngle = 0.0f;
        public boolean exploded = false;

        // Progressive Peeling & Accretion Escalation (Ticks 1..280)
        public final List<BlockPos> candidatePositions = new ArrayList<>();
        public final Set<BlockPos> peeledPositions = new HashSet<>();
        public final Map<BlockPos, Integer> peelTickMap = new HashMap<>();

        public final List<BlockPos> displayedPositions = new ArrayList<>();
        public final List<BlockPos> interiorPositions = new ArrayList<>();
        public final Set<BlockPos> swallowedPositions = new HashSet<>();

        public final Map<BlockPos, Vec3d> stage1UpliftMap = new HashMap<>();
        public final Map<BlockPos, Quaternionf> baseRotationMap = new HashMap<>();
        public final Map<BlockPos, Float> blockScaleMap = new HashMap<>();

        // Batched permanent crater carving & aftermath
        public final List<BlockPos> craterCarveBatch = new ArrayList<>();
        public int craterIndex = 0;

        public SupernovaTask(ServerWorld world, ServerPlayerEntity caster, BlockPos groundCenter, Vec3d corePos) {
            this.world = world;
            this.caster = caster;
            this.groundCenter = groundCenter.toImmutable();
            this.corePos = corePos;
        }
    }

    private static final Queue<SupernovaTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();

    public static void cast(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        HitResult hit = player.raycast(45.0, 0.0f, false);
        BlockPos groundCenter;
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bHit) {
            groundCenter = bHit.getBlockPos();
        } else {
            Vec3d forward = player.getEyePos().add(player.getRotationVec(1.0f).multiply(24.0));
            groundCenter = BlockPos.ofFloored(forward);
            while (groundCenter.getY() > world.getBottomY() && world.getBlockState(groundCenter).isAir()) {
                groundCenter = groundCenter.down();
            }
        }

        // Hố đen lơ lửng cách mặt đất 20.0m (Quy mô Calamity)
        Vec3d corePos = groundCenter.toCenterPos().add(0, 20.0, 0);
        SupernovaTask task = new SupernovaTask(world, player, groundCenter, corePos);

        // 1. Kích hoạt Shader Hố Đen 3D độc quyền của Chiêu 4 (Thời lượng 360 ticks = 18 giây tích tụ)
        // Bắt đầu từ 1 chấm siêu nhỏ (0.15m) nở to dần đơn điệu liên tục đến lúc nổ!
        ImpactFxPayload blackHolePayload = new ImpactFxPayload(
                corePos.x, corePos.y, corePos.z,
                ImpactFxPayload.TYPE_BLACK_HOLE, DETONATION_TICK, 1.0f
        );
        for (ServerPlayerEntity p : PlayerLookup.around(world, corePos, 160.0)) {
            ServerPlayNetworking.send(p, blackHolePayload);
        }

        // 2. Thu thập danh sách khối ứng viên trong bán kính Calamity R = 38.0m
        // QUY TẮC CỐT LÕI: KHÔNG bóc khối nào ở Tick 0. Khối sẽ bóc từ từ từ tâm lan ra ngoài (từ vài khối -> vài chục -> vài trăm)
        int intR = (int) Math.ceil(task.maxRadius);
        double maxRadiusSq = task.maxRadius * task.maxRadius;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        List<BlockPos> candidates = new ArrayList<>();
        int groundY = groundCenter.getY();

        for (int x = -intR; x <= intR; x++) {
            for (int z = -intR; z <= intR; z++) {
                double dSq2D = x * x + z * z;
                if (dSq2D > maxRadiusSq) continue;
                double dist2D = Math.sqrt(dSq2D);
                int maxDepth = (int) Math.round((1.0 - (dist2D / task.maxRadius)) * 14.0);
                int minY = Math.max(world.getBottomY(), groundY - maxDepth);
                int maxY = Math.min(world.getTopY() - 1, groundY + 4);

                for (int y = maxY; y >= minY; y--) {
                    mutable.set(groundCenter.getX() + x, y, groundCenter.getZ() + z);
                    BlockState state = world.getBlockState(mutable);
                    if (state.isAir() || state.getBlock() == Blocks.BEDROCK) continue;
                    if (!SpatialDisplacementManager.canDisplace(world, mutable, state)) continue;
                    candidates.add(mutable.toImmutable());
                }
            }
        }

        // Sắp xếp bóc tách: Từ TÂM lan dần ra ngoài (khoảng cách 2D tăng dần), lớp mặt trên bóc trước
        candidates.sort((a, b) -> {
            double distA = a.getSquaredDistance(task.groundCenter.getX(), a.getY(), task.groundCenter.getZ());
            double distB = b.getSquaredDistance(task.groundCenter.getX(), b.getY(), task.groundCenter.getZ());
            int distComp = Double.compare(distA, distB);
            if (distComp != 0) return distComp;
            return Integer.compare(b.getY(), a.getY());
        });

        task.candidatePositions.addAll(candidates);

        // Âm thanh khởi sinh Hố Đen Tận Thế (Trầm bổng, ngân vang)
        world.playSound(null, corePos.x, corePos.y, corePos.z,
                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 4.0f, 0.45f);
        world.playSound(null, corePos.x, corePos.y, corePos.z,
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 3.5f, 0.5f);
        world.playSound(null, corePos.x, corePos.y, corePos.z,
                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 3.5f, 0.45f);

        world.spawnParticles(ParticleTypes.SONIC_BOOM, corePos.x, corePos.y, corePos.z, 2, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.FLASH, corePos.x, corePos.y, corePos.z, 3, 0.2, 0.2, 0.2, 0.0);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, corePos.x, corePos.y, corePos.z, 80, 1.5, 1.5, 1.5, 0.35);

        ACTIVE_TASKS.add(task);
    }

    public static void tick(ServerWorld world) {
        Iterator<SupernovaTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            SupernovaTask task = it.next();
            if (task.world != world) continue;

            task.ageTicks++;

            // =========================================================================
            // GIAI ĐOẠN 1 & 2: BÓC ĐẤT LŨY TIẾN & 3 VÀNH ĐAI BỒI TỤ (Ticks 1..280 / 14s)
            // Từ 1 vài block -> vài chục block -> vài trăm block, tốc độ tăng dần mượt mà
            // =========================================================================
            if (task.ageTicks <= 280) {
                // Bán kính bóc tách mở rộng theo đường cong lũy thừa:
                // Ticks 1..60: Bán kính nhỏ (1m - 4m) -> Chỉ vài block nâng lên nhẹ
                // Ticks 61..180: Bán kính trung (5m - 20m) -> Vài chục block
                // Ticks 181..280: Bán kính đại (20m - 38m) -> Vài trăm block
                double progress = task.ageTicks / 280.0;
                double currentRadius = task.maxRadius * Math.pow(progress, 1.4);
                double currentRadiusSq = currentRadius * currentRadius;

                int peeledThisTick = 0;
                for (BlockPos pos : task.candidatePositions) {
                    if (task.peeledPositions.contains(pos)) continue;

                    double dx = pos.getX() - task.groundCenter.getX();
                    double dz = pos.getZ() - task.groundCenter.getZ();
                    if (dx * dx + dz * dz <= currentRadiusSq) {
                        task.peeledPositions.add(pos);
                        task.peelTickMap.put(pos, task.ageTicks);
                        peeledThisTick++;

                        // Tối đa 450 khối hiển thị BlockDisplayEntity
                        if (task.displayedPositions.size() < 450) {
                            float hash = (float) SpatialMath.hash01(pos);
                            float liftY = 0.6f + hash * 2.8f;
                            Vec3d stage1Lift = new Vec3d((hash - 0.5) * 0.5, liftY, (SpatialMath.hash01(pos.up()) - 0.5) * 0.5);

                            Quaternionf rot = new Quaternionf()
                                    .rotateY((float) (hash * Math.PI * 2.0))
                                    .rotateX((float) Math.toRadians(20.0 + hash * 25.0));
                            float scale = 0.85f + hash * 0.55f;

                            // KHÔNG DÙNG setGlowing, chỉ dùng Brightness(15, 15)
                            SpatialDisplacementManager.displaceBlock(
                                    world,
                                    pos,
                                    new Vector3f(0, 0, 0),
                                    rot,
                                    new Vector3f(scale, scale, scale),
                                    550,
                                    0,
                                    true,
                                    DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
                                    task.caster.getUuid(),
                                    null,
                                    0.0
                            );

                            DisplacedBlockRecord rec = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                            if (rec != null) {
                                rec.permanentDestruction = true;
                            }

                            task.displayedPositions.add(pos);
                            task.stage1UpliftMap.put(pos, stage1Lift);
                            task.baseRotationMap.put(pos, rot);
                            task.blockScaleMap.put(pos, scale);

                            if (peeledThisTick <= 6) {
                                world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                        pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                                        1, 0, 0.06, 0, 0.02);
                            }
                        } else {
                            // Các khối lòng đất bên trong: Đào thành AIR không tốn Entity
                            SpatialDisplacementManager.displaceBlock(
                                    world,
                                    pos,
                                    new Vector3f(0, 0, 0),
                                    new Quaternionf(),
                                    new Vector3f(1.0f, 1.0f, 1.0f),
                                    550,
                                    0,
                                    false,
                                    DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,
                                    null,
                                    null,
                                    0.0
                            );

                            DisplacedBlockRecord rec = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                            if (rec != null) {
                                rec.permanentDestruction = true;
                            }

                            task.interiorPositions.add(pos);
                        }
                    }
                }

                // Tốc độ góc tăng dần mượt mà theo hàm thời gian (không giật đột ngột)
                float overallProgress = task.ageTicks / 340.0f;
                float angularSpeed = 0.004f + (float) Math.pow(overallProgress, 2.0) * 0.075f;
                task.swirlAngle += angularSpeed;

                // Cập nhật chuyển động nội suy cho toàn bộ khối hiển thị mỗi 2 ticks
                if (task.ageTicks % 2 == 0) {
                    for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        int peelTick = task.peelTickMap.getOrDefault(pos, 0);
                        int ticksSincePeel = task.ageTicks - peelTick;

                        Vec3d stage1Lift = task.stage1UpliftMap.getOrDefault(pos, Vec3d.ZERO);
                        Vec3d ringOffset = SpatialMath.computeCanopyVortexOffset(pos, task.corePos, task.maxRadius, task.swirlAngle, 1.0);

                        Vec3d finalOffset;
                        Quaternionf rot = new Quaternionf(task.baseRotationMap.getOrDefault(pos, new Quaternionf()));

                        if (ticksSincePeel < 35) {
                            // 35 ticks đầu sau khi bóc: Nhấc bổng từ tốn khỏi mặt đất
                            float liftProgress = ticksSincePeel / 35.0f;
                            float u = liftProgress * liftProgress;
                            finalOffset = stage1Lift.multiply(u);
                        } else {
                            // Sau 35 ticks: Hòa mượt vào 3 vành đai bồi tụ
                            float mergeProgress = Math.min(1.0f, (ticksSincePeel - 35) / 45.0f);
                            finalOffset = stage1Lift.lerp(ringOffset, mergeProgress);
                            rot.rotateY(task.swirlAngle * 0.85f);
                        }

                        float scale = task.blockScaleMap.getOrDefault(pos, 1.0f);
                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(2);
                        display.setTransformation(SpatialMath.createPivotTransform(finalOffset, rot, scale));
                    }
                }

                // Lực hút hấp dẫn kéo thực thể xung quanh vào tâm hố đen
                if (task.ageTicks % 6 == 0) {
                    Box suctionBox = new Box(task.groundCenter).expand(task.maxRadius * 1.35);
                    for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, suctionBox, ent -> ent != task.caster)) {
                        Vec3d pull = task.corePos.subtract(e.getPos()).normalize().multiply(0.25 + progress * 0.20);
                        e.addVelocity(pull.x, pull.y, pull.z);
                        e.velocityModified = true;
                    }
                }

                // Âm thanh nhịp đập & cộng hưởng tăng dần cao độ theo thời gian
                if (task.ageTicks % 16 == 0) {
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 3.0f, 0.5f + (float) progress * 1.0f);
                }
                if (task.ageTicks % 40 == 0) {
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 3.0f, 0.6f + (float) progress * 0.8f);
                }
            }

            // =========================================================================
            // GIAI ĐOẠN 3: SỤP ĐỔ HẤP DẪN & NUỐT CHỬNG SIÊU MƯỢT (Ticks 281..340 / 3s)
            // Hố đen giữ nguyên kích thước cực đại (KHÔNG co rút, KHÔNG dựt dựt)
            // Toàn bộ khối đá xoắn ốc lao thẳng vào chân trời sự kiện và thu nhỏ dần
            // =========================================================================
            else if (task.ageTicks <= 340) {
                if (task.ageTicks == 285) {
                    CinematicFxPayload compressCinematic = new CinematicFxPayload(
                            CinematicFxPayload.EVENT_SUPERNOVA_COMPRESS,
                            task.corePos.x, task.corePos.y, task.corePos.z,
                            0.75f, 50
                    );
                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.corePos, 160.0)) {
                        ServerPlayNetworking.send(p, compressCinematic);
                    }
                }

                float collapseProgress = (task.ageTicks - 280) / 60.0f; // 0.0 -> 1.0
                double radiusScale = Math.max(0.04, 1.0 - Math.pow(collapseProgress, 1.4) * 0.96);
                task.swirlAngle += 0.12f; // Xoáy cực nhanh

                if ((task.ageTicks - 280) % 2 == 0) {
                    for (BlockPos pos : task.displayedPositions) {
                        if (task.swallowedPositions.contains(pos)) continue;

                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        Vec3d ringOffset = SpatialMath.computeCanopyVortexOffset(pos, task.corePos, task.maxRadius, task.swirlAngle, radiusScale);
                        Vec3d displayWorldPos = Vec3d.ofCenter(pos).add(ringOffset);
                        double distToCore = displayWorldPos.distanceTo(task.corePos);

                        Quaternionf rot = new Quaternionf(task.baseRotationMap.getOrDefault(pos, new Quaternionf()))
                                .rotateY(task.swirlAngle);

                        // Khi chạm vào rìa Chân trời sự kiện (< 4.8m): Thu nhỏ mượt mà về 0 (Spaghettification)
                        if (distToCore < 4.8 || collapseProgress > 0.90f) {
                            task.swallowedPositions.add(pos);
                            display.setStartInterpolation(0);
                            display.setInterpolationDuration(4);
                            display.setTransformation(SpatialMath.createPivotTransform(task.corePos.subtract(Vec3d.ofCenter(pos)), rot, 0.0f));
                        } else {
                            float scale = task.blockScaleMap.getOrDefault(pos, 1.0f) * (float) Math.max(0.12, radiusScale);
                            display.setStartInterpolation(0);
                            display.setInterpolationDuration(2);
                            display.setTransformation(SpatialMath.createPivotTransform(ringOffset, rot, scale));
                        }
                    }
                }

                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        task.corePos.x, task.corePos.y, task.corePos.z, 25, 2.0, 2.0, 2.0, 0.4);

                if (task.ageTicks % 8 == 0) {
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 4.0f, 1.8f);
                }
            }

            // =========================================================================
            // GIAI ĐOẠN 4: KHOẢNG LẶNG CHÂN KHÔNG (VACUUM SILENCE) (Ticks 341..359 / 1s)
            // =========================================================================
            else if (task.ageTicks < DETONATION_TICK) {
                if (task.ageTicks == 341) {
                    CinematicFxPayload silencePayload = new CinematicFxPayload(
                            CinematicFxPayload.EVENT_VACUUM_SILENCE,
                            task.corePos.x, task.corePos.y, task.corePos.z,
                            1.0f, 20
                    );
                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.corePos, 160.0)) {
                        ServerPlayNetworking.send(p, silencePayload);
                    }
                }

                // Tuyệt đối im lặng, Hố Đen tỏa hào quang chói lòa trước khi nổ
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                        task.corePos.x, task.corePos.y, task.corePos.z, 30, 0.5, 0.5, 0.5, 0.9);
            }

            // =========================================================================
            // GIAI ĐOẠN 5: ĐẠI VỤ NỔ SIÊU TÂN TINH TẠI TICK 360
            // =========================================================================
            else if (task.ageTicks == DETONATION_TICK && !task.exploded) {
                task.exploded = true;

                // 1. Kích hoạt Shader Siêu Tân Tinh (Kéo dài 140 ticks / 7 giây Aftermath với Cột Nấm & Quả Cầu Lửa)
                ImpactFxPayload novaPayload = new ImpactFxPayload(
                        task.corePos.x, task.corePos.y, task.corePos.z,
                        ImpactFxPayload.TYPE_SUPERNOVA, 140, 2.8f
                );
                CinematicFxPayload burstCinematic = new CinematicFxPayload(
                        CinematicFxPayload.EVENT_SUPERNOVA_BURST,
                        task.corePos.x, task.corePos.y, task.corePos.z,
                        1.0f, 60
                );
                for (ServerPlayerEntity p : PlayerLookup.around(world, task.corePos, 160.0)) {
                    ServerPlayNetworking.send(p, novaPayload);
                    ServerPlayNetworking.send(p, burstCinematic);
                }

                // 2. Âm thanh tận thế đa tầng
                world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                        SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 6.0f, 0.5f);
                world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                        SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 5.5f, 0.5f);
                world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                        SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 5.0f, 0.75f);
                world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                        SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 5.0f, 0.5f);
                world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                        SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.PLAYERS, 5.0f, 0.5f);

                // 3. VFX vụ nổ chói lòa
                world.spawnParticles(ParticleTypes.FLASH, task.corePos.x, task.corePos.y, task.corePos.z, 20, 1.5, 1.5, 1.5, 0.0);
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, task.corePos.x, task.corePos.y, task.corePos.z, 15, 3.0, 3.0, 3.0, 0.0);
                world.spawnParticles(ParticleTypes.SONIC_BOOM, task.corePos.x, task.corePos.y, task.corePos.z, 10, 2.0, 2.0, 2.0, 0.0);
                world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.65f, 0.15f), 3.5f),
                        task.corePos.x, task.corePos.y, task.corePos.z, 200, 8.0, 8.0, 8.0, 0.9);

                // 4. Sát thương Calamity hủy diệt (150 - 300 damage) và lực hất văng cực mạnh trong bán kính 60m
                Box blastBox = new Box(task.groundCenter).expand(60.0);
                for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, blastBox, ent -> ent != task.caster)) {
                    double dist = e.getPos().distanceTo(task.corePos);
                    float dmg = (float) Math.max(50.0, (1.0 - (dist / 60.0)) * 250.0 + 50.0);
                    e.damage(world.getDamageSources().explosion(null, task.caster), dmg);

                    Vec3d push = e.getPos().subtract(task.corePos).normalize().multiply(3.8).add(0, 1.5, 0);
                    e.addVelocity(push.x, push.y, push.z);
                    e.velocityModified = true;
                }

                // 5. Xóa bỏ hoàn toàn bản ghi để không bao giờ khôi phục lại mặt đất
                for (BlockPos pos : task.displayedPositions) {
                    SpatialDisplacementManager.removeActiveRecord(world.getRegistryKey(), pos);
                }
                for (BlockPos pos : task.interiorPositions) {
                    SpatialDisplacementManager.removeActiveRecord(world.getRegistryKey(), pos);
                }

                // 6. Chuẩn bị tạo Hố sâu tận thế (Impact Crater bán kính 40m)
                int craterR = 40;
                BlockPos.Mutable cMut = new BlockPos.Mutable();
                int centerGroundY = task.groundCenter.getY();

                for (int cx = -craterR; cx <= craterR; cx++) {
                    for (int cz = -craterR; cz <= craterR; cz++) {
                        double distSq = cx * cx + cz * cz;
                        if (distSq > craterR * craterR) continue;

                        double dist2D = Math.sqrt(distSq);
                        int depth = (int) Math.round((1.0 - (dist2D / craterR)) * 14.0);

                        for (int cy = -depth; cy <= 3; cy++) {
                            cMut.set(task.groundCenter.getX() + cx, centerGroundY + cy, task.groundCenter.getZ() + cz);
                            if (cMut.getY() < world.getBottomY() || cMut.getY() >= world.getTopY()) continue;
                            task.craterCarveBatch.add(cMut.toImmutable());
                        }
                    }
                }

                // 7. Phóng 80 khối đá nóng chảy và dung nham bay vọt ra bán kính 50m - 60m
                Random rng = new Random(task.groundCenter.asLong());
                int fallingBlocksCount = 80;
                BlockState[] moltenStates = new BlockState[] {
                        Blocks.MAGMA_BLOCK.getDefaultState(),
                        Blocks.OBSIDIAN.getDefaultState(),
                        Blocks.CRYING_OBSIDIAN.getDefaultState(),
                        Blocks.BLACKSTONE.getDefaultState(),
                        Blocks.COBBLESTONE.getDefaultState()
                };

                for (int i = 0; i < fallingBlocksCount; i++) {
                    double fAngle = rng.nextDouble() * Math.PI * 2.0;
                    double fSpeed = 1.0 + rng.nextDouble() * 1.6;
                    double spawnX = task.groundCenter.getX() + 0.5 + Math.cos(fAngle) * 4.0;
                    double spawnY = task.groundCenter.getY() + 3.0;
                    double spawnZ = task.groundCenter.getZ() + 0.5 + Math.sin(fAngle) * 4.0;

                    BlockState flingState = moltenStates[rng.nextInt(moltenStates.length)];
                    FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world,
                            BlockPos.ofFloored(spawnX, spawnY, spawnZ),
                            flingState);
                    falling.setVelocity(Math.cos(fAngle) * fSpeed, 0.8 + rng.nextDouble() * 1.0, Math.sin(fAngle) * fSpeed);
                    falling.velocityModified = true;
                }
            }

            // =========================================================================
            // GIAI ĐOẠN 6: AFTERMATH & CỘT NẤM KHỔNG LỒ (Ticks 361..500 / 7 GIÂY SAU NỔ)
            // =========================================================================
            else if (task.ageTicks > DETONATION_TICK) {
                // 1. Đào hố tận thế chia theo mẻ (Async / Batched Carving trong 20 sub-ticks)
                if (task.craterIndex < task.craterCarveBatch.size()) {
                    int batchSize = (task.craterCarveBatch.size() / 20) + 1;
                    int endIndex = Math.min(task.craterCarveBatch.size(), task.craterIndex + batchSize);
                    Random rng = new Random();

                    for (int i = task.craterIndex; i < endIndex; i++) {
                        BlockPos pos = task.craterCarveBatch.get(i);
                        BlockState currentState = world.getBlockState(pos);
                        if (currentState.getBlock() == Blocks.BEDROCK) continue;

                        double dist2D = Math.sqrt(pos.getSquaredDistance(task.groundCenter.getX(), pos.getY(), task.groundCenter.getZ()));
                        int depth = (int) Math.round((1.0 - (dist2D / 40.0)) * 14.0);
                        int floorY = task.groundCenter.getY() - depth;

                        if (pos.getY() <= floorY + 1) {
                            // Đáy hố bị nung chảy thành Magma, Obsidian, Crying Obsidian, Blackstone
                            BlockState scorchState = (rng.nextFloat() < 0.40f) ? Blocks.MAGMA_BLOCK.getDefaultState() :
                                    (rng.nextFloat() < 0.70f) ? Blocks.OBSIDIAN.getDefaultState() :
                                            (rng.nextFloat() < 0.85f) ? Blocks.CRYING_OBSIDIAN.getDefaultState() :
                                                    Blocks.BLACKSTONE.getDefaultState();
                            world.setBlockState(pos, scorchState, Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
                        } else {
                            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
                        }
                    }
                    task.craterIndex = endIndex;
                }

                // 2. VỤ NỔ HÌNH NẤM 3D (3D MUSHROOM CLOUD PARTICLE SYSTEM):
                // A. Thân Nấm (Mushroom Stem): Cột khói & lửa bốc thẳng cuồn cuộn từ lòng hố lên cao 50m
                Random rng = new Random();
                for (int k = 0; k < 5; k++) {
                    double stemH = rng.nextDouble() * 48.0;
                    double stemR = 1.0 + (stemH / 48.0) * 3.5;
                    double sAngle = rng.nextDouble() * Math.PI * 2.0;
                    double sx = task.groundCenter.getX() + 0.5 + Math.cos(sAngle) * (rng.nextDouble() * stemR);
                    double sy = task.groundCenter.getY() + stemH;
                    double sz = task.groundCenter.getZ() + 0.5 + Math.sin(sAngle) * (rng.nextDouble() * stemR);

                    if (stemH < 12.0) {
                        world.spawnParticles(ParticleTypes.LAVA, sx, sy, sz, 1, 0, 0.1, 0, 0.02);
                        world.spawnParticles(ParticleTypes.FLAME, sx, sy, sz, 1, 0, 0.15, 0, 0.05);
                    }
                    world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, sx, sy, sz, 1, 0, 0.35, 0, 0.02);
                }

                // B. Tán Nấm (Mushroom Cap): Đám mây nấm khổng lồ bung tỏa hình vành xuyến ở độ cao 45m - 55m
                float capProgress = Math.min(1.0f, (task.ageTicks - DETONATION_TICK) / 140.0f);
                double capRadius = 6.0 + capProgress * 24.0; // Bán kính tán nấm xòe rộng từ 6m ra 30m!

                for (int j = 0; j < 8; j++) {
                    double cAngle = rng.nextDouble() * Math.PI * 2.0;
                    double rOffset = (rng.nextDouble() - 0.5) * 5.0;
                    double r = capRadius + rOffset;
                    double cx = task.groundCenter.getX() + 0.5 + Math.cos(cAngle) * r;
                    double cy = task.groundCenter.getY() + 48.0 + (rng.nextDouble() - 0.5) * 6.0;
                    double cz = task.groundCenter.getZ() + 0.5 + Math.sin(cAngle) * r;

                    double outVx = Math.cos(cAngle) * 0.18;
                    double outVz = Math.sin(cAngle) * 0.18;

                    world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, cy, cz, 1, outVx, 0.04, outVz, 0.02);
                    if (capProgress < 0.45f && rng.nextFloat() < 0.35f) {
                        world.spawnParticles(ParticleTypes.EXPLOSION, cx, cy, cz, 1, outVx, 0.02, outVz, 0.0);
                    }
                }

                // C. Âm thanh sấm rền và tiếng gầm vũ trụ vang vọng xa dần trong 7 giây
                if ((task.ageTicks - DETONATION_TICK) % 25 == 0) {
                    float decayVol = Math.max(0.6f, 3.5f * (1.0f - capProgress));
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, decayVol, 0.5f);
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, decayVol * 0.8f, 0.45f);
                }

                // Kết thúc toàn bộ kịch bản tại Tick 500 (25 giây trọn vẹn)
                if (task.ageTicks >= MAX_LIFETIME_TICKS) {
                    it.remove();
                }
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
