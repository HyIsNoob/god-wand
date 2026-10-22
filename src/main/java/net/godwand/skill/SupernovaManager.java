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
import net.minecraft.entity.decoration.Brightness;
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

    public static class SupernovaTask {
        public final ServerWorld world;
        public final ServerPlayerEntity caster;
        public final BlockPos groundCenter;
        public final Vec3d corePos;
        public final double maxRadius = 36.0; // Calamity scale!

        public int ageTicks = 0;
        public final int maxLifetimeTicks = 250;
        public float swirlAngle = 0.0f;
        public boolean exploded = false;

        // Progressive Peeling & Ring Dispersal (Ticks 1..120)
        public final List<BlockPos> candidatePositions = new ArrayList<>();
        public final Set<BlockPos> peeledPositions = new HashSet<>();
        public final List<BlockPos> displayedPositions = new ArrayList<>();
        public final List<BlockPos> interiorPositions = new ArrayList<>();
        public final Set<BlockPos> swallowedPositions = new HashSet<>();

        public final Map<BlockPos, Vec3d> stage1UpliftMap = new HashMap<>();
        public final Map<BlockPos, Quaternionf> baseRotationMap = new HashMap<>();
        public final Map<BlockPos, Float> blockScaleMap = new HashMap<>();

        // Batched permanent crater carving at Tick 186
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

        // 1. Kích hoạt Shader Hố Đen 3D độc quyền của Chiêu 4 (Accretion Disk + Pulsar Jets)
        ImpactFxPayload blackHolePayload = new ImpactFxPayload(
                corePos.x, corePos.y, corePos.z,
                ImpactFxPayload.TYPE_BLACK_HOLE, 186, 1.0f
        );
        for (ServerPlayerEntity p : PlayerLookup.around(world, corePos, 160.0)) {
            ServerPlayNetworking.send(p, blackHolePayload);
        }

        // 2. Thu thập danh sách khối ứng viên trong bán kính Calamity R = 36.0m
        // QUY TẮC CỐT LÕI: KHÔNG bóc khối nào ở Tick 0 (Chống lỗi ăn chặn phase).
        // Khối sẽ được bóc tách từ từ (Progressive Peel) trong suốt Ticks 1..120.
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

        // Sắp xếp bóc tách theo lớp: Từ tầng trên cùng xuống sâu dần (Y giảm dần), và từ tâm lan ra ngoài
        candidates.sort((a, b) -> {
            int yComp = Integer.compare(b.getY(), a.getY());
            if (yComp != 0) return yComp;
            double distA = a.getSquaredDistance(task.groundCenter.getX(), a.getY(), task.groundCenter.getZ());
            double distB = b.getSquaredDistance(task.groundCenter.getX(), b.getY(), task.groundCenter.getZ());
            return Double.compare(distA, distB);
        });

        task.candidatePositions.addAll(candidates);

        // Âm thanh khởi sinh Hố Đen Tận Thế
        world.playSound(null, corePos.x, corePos.y, corePos.z,
                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 4.0f, 0.5f);
        world.playSound(null, corePos.x, corePos.y, corePos.z,
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 3.5f, 0.6f);
        world.playSound(null, corePos.x, corePos.y, corePos.z,
                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 3.5f, 0.5f);

        world.spawnParticles(ParticleTypes.SONIC_BOOM, corePos.x, corePos.y, corePos.z, 2, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.FLASH, corePos.x, corePos.y, corePos.z, 3, 0.2, 0.2, 0.2, 0.0);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, corePos.x, corePos.y, corePos.z, 80, 2.0, 2.0, 2.0, 0.4);

        ACTIVE_TASKS.add(task);
    }

    public static void tick(ServerWorld world) {
        Iterator<SupernovaTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            SupernovaTask task = it.next();
            if (task.world != world) continue;

            task.ageTicks++;

            // =========================================================================
            // GIAI ĐOẠN 1 & 2: BÓC TÁCH ĐỊA HÌNH THEO THỜI GIAN & 3 VÀNH ĐAI BỒI TỤ (Ticks 1..120)
            // =========================================================================
            if (task.ageTicks <= 120) {
                // Bán kính bóc tách mở rộng từ từ theo thời gian R(t) = maxRadius * (t / 120.0)
                double currentRadius = task.maxRadius * (task.ageTicks / 120.0);
                double currentRadiusSq = currentRadius * currentRadius;

                int peeledThisTick = 0;
                for (BlockPos pos : task.candidatePositions) {
                    if (task.peeledPositions.contains(pos)) continue;

                    double dx = pos.getX() - task.groundCenter.getX();
                    double dz = pos.getZ() - task.groundCenter.getZ();
                    if (dx * dx + dz * dz <= currentRadiusSq) {
                        task.peeledPositions.add(pos);
                        peeledThisTick++;

                        // Tối đa 450 khối BlockDisplayEntity trên 3 vành đai thiên thạch
                        if (task.displayedPositions.size() < 450) {
                            float hash = (float) SpatialMath.hash01(pos);
                            float liftY = 0.5f + hash * 2.8f;
                            Vec3d stage1Lift = new Vec3d((hash - 0.5) * 0.5, liftY, (SpatialMath.hash01(pos.up()) - 0.5) * 0.5);

                            Quaternionf rot = new Quaternionf()
                                    .rotateY((float) (hash * Math.PI * 2.0))
                                    .rotateX((float) Math.toRadians(20.0 + hash * 25.0));
                            float scale = 0.85f + hash * 0.55f; // Khối to rõ 0.85x - 1.40x

                            // KHÔNG DÙNG setGlowing, chỉ dùng Brightness(15, 15)
                            SpatialDisplacementManager.displaceBlock(
                                    world,
                                    pos,
                                    new Vector3f(0, 0, 0),
                                    rot,
                                    new Vector3f(scale, scale, scale),
                                    280,
                                    0, // glowColor = 0 (tắt glowing)
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
                                    280,
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

                // Cập nhật tọa độ nội suy cho các khối hiển thị
                if (task.ageTicks <= 45) {
                    // Ticks 1..45: Nhấc bổng nhẹ khỏi mặt đất
                    if (task.ageTicks % 2 == 0) {
                        for (BlockPos pos : task.displayedPositions) {
                            DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                            if (record == null || record.displayEntityUuid == null) continue;

                            BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                            if (display == null || !display.isAlive()) continue;

                            double distRatio = Math.min(1.0, Math.sqrt(pos.getSquaredDistance(task.groundCenter)) / task.maxRadius);
                            double localProgress = Math.max(0.0, Math.min(1.0, (task.ageTicks / 45.0 - distRatio * 0.4) / 0.6));
                            float u = (float) (localProgress * localProgress);

                            Vec3d lift = task.stage1UpliftMap.getOrDefault(pos, Vec3d.ZERO).multiply(u);
                            Quaternionf rot = task.baseRotationMap.getOrDefault(pos, new Quaternionf());
                            float scale = task.blockScaleMap.getOrDefault(pos, 1.0f);

                            display.setStartInterpolation(0);
                            display.setInterpolationDuration(2);
                            display.setTransformation(SpatialMath.createPivotTransform(lift, rot, scale));
                        }
                    }
                } else {
                    // Ticks 46..120: Cuốn xoáy vào 3 vành đai bồi tụ và tăng tốc độ góc
                    float accelProgress = (task.ageTicks - 45) / 75.0f; // 0.0 -> 1.0
                    float currentAngularSpeed = 0.012f + (accelProgress * accelProgress * 0.28f);
                    task.swirlAngle += currentAngularSpeed;

                    float lerpProgress = Math.min(1.0f, (task.ageTicks - 45) / 30.0f);

                    if ((task.ageTicks - 45) % 2 == 0) {
                        for (BlockPos pos : task.displayedPositions) {
                            DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                            if (record == null || record.displayEntityUuid == null) continue;

                            BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                            if (display == null || !display.isAlive()) continue;

                            Vec3d ringOffset = SpatialMath.computeCanopyVortexOffset(pos, task.corePos, task.maxRadius, task.swirlAngle, 1.0);
                            Vec3d finalOffset;
                            if (lerpProgress < 1.0f) {
                                Vec3d stage1Lift = task.stage1UpliftMap.getOrDefault(pos, Vec3d.ZERO);
                                finalOffset = stage1Lift.lerp(ringOffset, lerpProgress);
                            } else {
                                finalOffset = ringOffset;
                            }

                            Quaternionf rot = new Quaternionf(task.baseRotationMap.getOrDefault(pos, new Quaternionf()))
                                    .rotateY(task.swirlAngle * 0.85f);
                            float scale = task.blockScaleMap.getOrDefault(pos, 1.0f);

                            display.setStartInterpolation(0);
                            display.setInterpolationDuration(2);
                            display.setTransformation(SpatialMath.createPivotTransform(finalOffset, rot, scale));
                        }
                    }
                }

                // Lực hút hấp dẫn kéo thực thể xung quanh vào tâm hố đen
                if (task.ageTicks % 6 == 0) {
                    Box suctionBox = new Box(task.groundCenter).expand(task.maxRadius * 1.35);
                    for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, suctionBox, ent -> ent != task.caster)) {
                        Vec3d pull = task.corePos.subtract(e.getPos()).normalize().multiply(0.28);
                        e.addVelocity(pull.x, pull.y, pull.z);
                        e.velocityModified = true;
                    }
                }

                if (task.ageTicks % 14 == 0) {
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 3.0f, 0.6f + (task.ageTicks / 100.0f));
                }
            }

            // =========================================================================
            // GIAI ĐOẠN 3: SỤP ĐỔ HẤP DẪN & NUỐT CHỬNG VÀO CHÂN TRỜI SỰ KIỆN (Ticks 121..175)
            // =========================================================================
            else if (task.ageTicks <= 175) {
                if (task.ageTicks == 151) {
                    CinematicFxPayload compressCinematic = new CinematicFxPayload(
                            CinematicFxPayload.EVENT_SUPERNOVA_COMPRESS,
                            task.corePos.x, task.corePos.y, task.corePos.z,
                            0.75f, 35
                    );
                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.corePos, 160.0)) {
                        ServerPlayNetworking.send(p, compressCinematic);
                    }
                }

                float shrinkProgress = (task.ageTicks - 120) / 55.0f; // 0.0 -> 1.0
                float radiusScale = Math.max(0.04f, 1.0f - (shrinkProgress * 0.96f)); // Co rút cực hạn về 0.04
                task.swirlAngle += 0.28f;

                if ((task.ageTicks - 120) % 2 == 0) {
                    for (BlockPos pos : task.displayedPositions) {
                        if (task.swallowedPositions.contains(pos)) continue;

                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        Vec3d ringOffset = SpatialMath.computeCanopyVortexOffset(pos, task.corePos, task.maxRadius, task.swirlAngle, radiusScale);
                        Vec3d displayWorldPos = Vec3d.ofCenter(pos).add(ringOffset);
                        double distToCore = displayWorldPos.distanceTo(task.corePos);

                        // Khi chạm vào tâm Chân trời sự kiện (< 4.6m): Nuốt chửng hoàn toàn!
                        if (distToCore < 4.6 || radiusScale < 0.10f) {
                            task.swallowedPositions.add(pos);
                            display.setStartInterpolation(0);
                            display.setInterpolationDuration(2);
                            display.setTransformation(SpatialMath.createPivotTransform(ringOffset, new Quaternionf(), 0.0f));
                            SpatialDisplacementManager.removeActiveRecord(world.getRegistryKey(), pos);
                        } else {
                            Quaternionf rot = new Quaternionf(task.baseRotationMap.getOrDefault(pos, new Quaternionf()))
                                    .rotateY(task.swirlAngle);
                            float scale = task.blockScaleMap.getOrDefault(pos, 1.0f) * Math.max(0.15f, radiusScale);

                            display.setStartInterpolation(0);
                            display.setInterpolationDuration(2);
                            display.setTransformation(SpatialMath.createPivotTransform(ringOffset, rot, scale));
                        }
                    }
                }

                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        task.corePos.x, task.corePos.y, task.corePos.z, 20, 1.8, 1.8, 1.8, 0.4);

                if (task.ageTicks % 8 == 0) {
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 4.0f, 1.8f);
                }
            }

            // =========================================================================
            // GIAI ĐOẠN 3B: KHOẢNG LẶNG CHÂN KHÔNG (VACUUM SILENCE) (Ticks 176..185)
            // =========================================================================
            else if (task.ageTicks <= 185) {
                if (task.ageTicks == 176) {
                    CinematicFxPayload silencePayload = new CinematicFxPayload(
                            CinematicFxPayload.EVENT_VACUUM_SILENCE,
                            task.corePos.x, task.corePos.y, task.corePos.z,
                            1.0f, 10
                    );
                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.corePos, 160.0)) {
                        ServerPlayNetworking.send(p, silencePayload);
                    }
                }

                // Tuyệt đối im lặng, Hố Đen co giật cực mạnh trước khi phát nổ
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                        task.corePos.x, task.corePos.y, task.corePos.z, 25, 0.4, 0.4, 0.4, 0.8);
            }

            // =========================================================================
            // GIAI ĐOẠN 4: ĐẠI VỤ NỔ SIÊU TÂN TINH & PHÁ HỦY BẢN ĐỒ VĨNH VIỄN (Ticks 186..250)
            // =========================================================================
            else {
                // TẠI TICK 186: ĐẠI NỔ SIÊU TÂN TINH CẤP CALAMITY
                if (task.ageTicks == 186 && !task.exploded) {
                    task.exploded = true;

                    // Kích hoạt Shader Siêu Tân Tinh (Quả cầu lửa khổng lồ 32m + 8-10 Anime Impact Frames)
                    ImpactFxPayload novaPayload = new ImpactFxPayload(
                            task.corePos.x, task.corePos.y, task.corePos.z,
                            ImpactFxPayload.TYPE_SUPERNOVA, 45, 2.5f
                    );
                    CinematicFxPayload burstCinematic = new CinematicFxPayload(
                            CinematicFxPayload.EVENT_SUPERNOVA_BURST,
                            task.corePos.x, task.corePos.y, task.corePos.z,
                            1.0f, 45
                    );
                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.corePos, 160.0)) {
                        ServerPlayNetworking.send(p, novaPayload);
                        ServerPlayNetworking.send(p, burstCinematic);
                    }

                    // Âm thanh tận thế đa tầng
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 6.0f, 0.5f);
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 5.5f, 0.55f);
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 5.0f, 0.75f);
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 5.0f, 0.5f);
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.PLAYERS, 5.0f, 0.5f);

                    // VFX vụ nổ chói lòa
                    world.spawnParticles(ParticleTypes.FLASH, task.corePos.x, task.corePos.y, task.corePos.z, 15, 1.2, 1.2, 1.2, 0.0);
                    world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, task.corePos.x, task.corePos.y, task.corePos.z, 10, 2.5, 2.5, 2.5, 0.0);
                    world.spawnParticles(ParticleTypes.SONIC_BOOM, task.corePos.x, task.corePos.y, task.corePos.z, 8, 1.5, 1.5, 1.5, 0.0);
                    world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.65f, 0.15f), 3.0f),
                            task.corePos.x, task.corePos.y, task.corePos.z, 160, 6.0, 6.0, 6.0, 0.8);

                    // Sát thương Calamity hủy diệt (150 - 300 damage) và lực hất văng cực mạnh trong bán kính 60m
                    Box blastBox = new Box(task.groundCenter).expand(60.0);
                    for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, blastBox, ent -> ent != task.caster)) {
                        double dist = e.getPos().distanceTo(task.corePos);
                        float dmg = (float) Math.max(50.0, (1.0 - (dist / 60.0)) * 250.0 + 50.0);
                        e.damage(world.getDamageSources().explosion(null, task.caster), dmg);

                        Vec3d push = e.getPos().subtract(task.corePos).normalize().multiply(3.5).add(0, 1.4, 0);
                        e.addVelocity(push.x, push.y, push.z);
                        e.velocityModified = true;
                    }

                    // XÓA BỎ TOÀN BỘ BẢN GHI KHÔI PHỤC: Không bao giờ phục hồi lại mặt đất!
                    for (BlockPos pos : task.displayedPositions) {
                        SpatialDisplacementManager.removeActiveRecord(world.getRegistryKey(), pos);
                    }
                    for (BlockPos pos : task.interiorPositions) {
                        SpatialDisplacementManager.removeActiveRecord(world.getRegistryKey(), pos);
                    }

                    // CHUẨN BỊ TẠO HỐ SÂU TẬN THẾ THẬT SỰ (Impact Crater bán kính 40m)
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

                    // Phóng 60–80 khối đá nóng chảy và dung nham bay vọt ra bán kính 50m - 60m
                    Random rng = new Random(task.groundCenter.asLong());
                    int fallingBlocksCount = 70;
                    BlockState[] moltenStates = new BlockState[] {
                            Blocks.MAGMA_BLOCK.getDefaultState(),
                            Blocks.OBSIDIAN.getDefaultState(),
                            Blocks.CRYING_OBSIDIAN.getDefaultState(),
                            Blocks.BLACKSTONE.getDefaultState(),
                            Blocks.COBBLESTONE.getDefaultState()
                    };

                    for (int i = 0; i < fallingBlocksCount; i++) {
                        double fAngle = rng.nextDouble() * Math.PI * 2.0;
                        double fSpeed = 1.0 + rng.nextDouble() * 1.5; // Tốc độ phóng cực nhanh
                        double spawnX = task.groundCenter.getX() + 0.5 + Math.cos(fAngle) * 4.0;
                        double spawnY = task.groundCenter.getY() + 3.0;
                        double spawnZ = task.groundCenter.getZ() + 0.5 + Math.sin(fAngle) * 4.0;

                        BlockState flingState = moltenStates[rng.nextInt(moltenStates.length)];
                        FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world,
                                BlockPos.ofFloored(spawnX, spawnY, spawnZ),
                                flingState);
                        falling.setVelocity(Math.cos(fAngle) * fSpeed, 0.8 + rng.nextDouble() * 0.9, Math.sin(fAngle) * fSpeed);
                        falling.velocityModified = true;
                    }
                }

                // Ticks 186..198: Đào hố tận thế chia theo mẻ (Async / Batched Carving trong 12 sub-ticks)
                if (task.ageTicks >= 186 && task.craterIndex < task.craterCarveBatch.size()) {
                    int batchSize = (task.craterCarveBatch.size() / 12) + 1;
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

                // Kết thúc task tại Tick 200: Hiện trường hủy diệt vĩnh viễn, không phục hồi
                if (task.ageTicks >= 200) {
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
