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
import net.minecraft.world.Heightmap;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SupernovaManager {

    public static final int DETONATION_TICK = 340;
    public static final int MAX_LIFETIME_TICKS = 500;
    public static final int MAX_DISPLAYS_CAP = 1800;

    public static class SupernovaTask {
        public final ServerWorld world;
        public final ServerPlayerEntity caster;
        public final BlockPos groundCenter;
        public final Vec3d corePos;
        public final double maxRadius = 55.0; // Bán kính hút đất dạng cầu gồ ghề mở rộng (Suction Cavity)
        public final double blastRadius = 125.0; // Bán kính vụ nổ tận thế hủy diệt thế giới cực đại gấp đôi (Detonation Blast: 125m)

        public int ageTicks = 0;
        public float swirlAngle = 0.0f;
        public boolean exploded = false;

        // Progressive Peeling & Accretion Escalation (Ticks 1..340)
        public final List<BlockPos> candidatePositions = new ArrayList<>();
        public final Set<BlockPos> peeledPositions = new HashSet<>();
        public final Map<BlockPos, Integer> peelTickMap = new HashMap<>();

        public final List<BlockPos> displayedPositions = new ArrayList<>();
        public final List<BlockPos> interiorPositions = new ArrayList<>();
        public final List<BlockPos> blastFlingPositions = new ArrayList<>();

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

        HitResult hit = player.raycast(180.0, 0.0f, false);
        BlockPos groundCenter;
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bHit) {
            groundCenter = bHit.getBlockPos();
        } else {
            Vec3d forward = player.getEyePos().add(player.getRotationVec(1.0f).multiply(120.0));
            groundCenter = BlockPos.ofFloored(forward);
            while (groundCenter.getY() > world.getBottomY() && world.getBlockState(groundCenter).isAir()) {
                groundCenter = groundCenter.down();
            }
        }

        // Hố đen lơ lửng cách mặt đất 20.0m (Quy mô Calamity)
        Vec3d corePos = groundCenter.toCenterPos().add(0, 20.0, 0);
        SupernovaTask task = new SupernovaTask(world, player, groundCenter, corePos);

        // 1. Kích hoạt Shader Hố Đen 3D độc quyền của Chiêu 4 (340 ticks tích tụ đơn điệu)
        ImpactFxPayload blackHolePayload = new ImpactFxPayload(
                corePos.x, corePos.y, corePos.z,
                ImpactFxPayload.TYPE_BLACK_HOLE, DETONATION_TICK, 1.0f
        );
        for (ServerPlayerEntity p : PlayerLookup.around(world, corePos, 250.0)) {
            ServerPlayNetworking.send(p, blackHolePayload);
        }
        if (player.isAlive()) {
            ServerPlayNetworking.send(player, blackHolePayload);
        }

        // 2. Thu thập danh sách khối ứng viên trong bán kính hút R = 34.0m
        // QUÉT CẢ VÙNG NÚI NON PHÍA TRÊN & TẠO LÒNG CHẢO HÌNH CẦU/PARABOL GỒ GHỀ (KHÔNG PHẲNG LÌ)
        int intR = (int) Math.ceil(task.maxRadius);
        double maxRadiusSq = task.maxRadius * task.maxRadius;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        List<BlockPos> surfaceCandidates = new ArrayList<>();
        List<BlockPos> subSurfaceCandidates = new ArrayList<>();
        int groundY = groundCenter.getY();
        int maxY = Math.min(world.getTopY() - 1, groundY + 54);

        for (int x = -intR; x <= intR; x++) {
            for (int z = -intR; z <= intR; z++) {
                double dSq2D = x * x + z * z;
                if (dSq2D > maxRadiusSq) continue;

                double dist2D = Math.sqrt(dSq2D);
                int colX = groundCenter.getX() + x;
                int colZ = groundCenter.getZ() + z;

                // Độ sâu hố hút hình cầu / lòng chảo parabol tự nhiên (Curved Spherical Cavity)
                // Tâm hố sâu 14-16 blocks, thoải dần theo hình cầu ra mép 34m
                double normR = dist2D / task.maxRadius;
                double bowlDepth = 15.0 * Math.pow(1.0 - normR, 1.35);

                // Fractal noise lởm chởm gập ghềnh tự nhiên (tuyệt đối không phẳng lì!)
                double noise = (SpatialMath.hash01(new BlockPos(colX * 2, 0, colZ * 2)) - 0.5) * 6.5;
                noise += Math.sin(colX * 0.4) * Math.cos(colZ * 0.4) * 2.5;

                int colMinY = Math.max(world.getBottomY() + 4, (int) Math.round(groundY - bowlDepth + noise));
                int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, colX, colZ) - 1;
                int solidCount = 0;

                for (int y = Math.min(maxY, surfaceY + 4); y >= colMinY; y--) {
                    mutable.set(colX, y, colZ);
                    BlockState state = world.getBlockState(mutable);
                    if (state.isAir() || state.getBlock() == Blocks.BEDROCK) continue;
                    if (!SpatialDisplacementManager.canDisplace(world, mutable, state)) continue;

                    solidCount++;
                    if (solidCount <= 3) {
                        surfaceCandidates.add(mutable.toImmutable());
                    } else {
                        subSurfaceCandidates.add(mutable.toImmutable());
                    }
                }
            }
        }

        // Sắp xếp bóc tách: Ưu tiên khối bề mặt/núi non trên cao bóc trước, và từ tâm lan dần ra
        Comparator<BlockPos> radialComp = (a, b) -> {
            double distA = Math.sqrt(a.getSquaredDistance(task.groundCenter.getX(), a.getY(), task.groundCenter.getZ()));
            double distB = Math.sqrt(b.getSquaredDistance(task.groundCenter.getX(), b.getY(), task.groundCenter.getZ()));
            double scoreA = distA * 0.7 - (a.getY() - groundY) * 0.45;
            double scoreB = distB * 0.7 - (b.getY() - groundY) * 0.45;
            return Double.compare(scoreA, scoreB);
        };

        surfaceCandidates.sort(radialComp);
        subSurfaceCandidates.sort(radialComp);

        task.candidatePositions.addAll(surfaceCandidates);
        task.candidatePositions.addAll(subSurfaceCandidates);

        // Âm thanh khởi sinh Hố Đen Tận Thế (Trầm bổng, ngân vang)
        world.playSound(null, corePos.x, corePos.y, corePos.z,
                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 4.0f, 0.45f);
        world.playSound(null, corePos.x, corePos.y, corePos.z,
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 3.5f, 0.5f);
        world.playSound(null, corePos.x, corePos.y, corePos.z,
                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 3.5f, 0.45f);

        world.spawnParticles(ParticleTypes.SONIC_BOOM, corePos.x, corePos.y, corePos.z, 2, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, corePos.x, corePos.y, corePos.z, 25, 0.4, 0.4, 0.4, 0.1);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, corePos.x, corePos.y, corePos.z, 80, 1.5, 1.5, 1.5, 0.35);

        // Rung chấn dữ dội ngay khi kích hoạt tạo Hố Đen
        CinematicFxPayload castShake = new CinematicFxPayload(
                CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                corePos.x, corePos.y, corePos.z,
                0.85f, 28
        );
        for (ServerPlayerEntity p : PlayerLookup.around(world, corePos, 120.0)) {
            ServerPlayNetworking.send(p, castShake);
        }

        ACTIVE_TASKS.add(task);
    }

    public static void tick(ServerWorld world) {
        Iterator<SupernovaTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            SupernovaTask task = it.next();
            if (task.world != world) continue;

            task.ageTicks++;

            // =========================================================================
            // GIAI ĐOẠN 1: BÓC ĐẤT NÚI TỶ LỆ THUẬN (Ticks 1..279)
            // TỪ TICK 280 TRỞ ĐI: DỪNG BÓC MẶT ĐẤT, BƯỚC VÀO PHA CO RÚT SỤP ĐỔ
            // =========================================================================
            if (task.ageTicks < DETONATION_TICK) {
                double currentPeelRadius = 2.5;

                // 1. Chỉ bóc tách đất và bứt núi non đến trước Tick 280
                if (task.ageTicks < 280) {
                    double progress = task.ageTicks / 280.0; // 0.0 -> 1.0

                    // Bán kính vùng đất núi bị hút mở rộng dần theo tỷ lệ thuận (2.5m -> 34m)
                    double bhScale = Math.pow(progress, 1.25);
                    currentPeelRadius = 2.5 + (task.maxRadius - 2.5) * bhScale;
                    double currentRadiusSq = currentPeelRadius * currentPeelRadius;

                    // Hạn ngạch số khối BlockDisplayEntity tối đa trên bầu trời tăng dần lên 1800 khối
                    int maxDisplaysNow = 35 + (int) (1765 * Math.pow(progress, 1.35));

                    int peeledThisTick = 0;
                    for (BlockPos pos : task.candidatePositions) {
                        if (task.peeledPositions.contains(pos)) continue;

                        double dx = pos.getX() - task.groundCenter.getX();
                        double dz = pos.getZ() - task.groundCenter.getZ();
                        if (dx * dx + dz * dz <= currentRadiusSq) {
                            task.peeledPositions.add(pos);
                            task.peelTickMap.put(pos, task.ageTicks);
                            peeledThisTick++;

                            if (task.displayedPositions.size() < maxDisplaysNow && task.displayedPositions.size() < MAX_DISPLAYS_CAP) {
                                float hash = (float) SpatialMath.hash01(pos);

                                Quaternionf rot = new Quaternionf()
                                        .rotateY((float) (hash * Math.PI * 2.0))
                                        .rotateX((float) Math.toRadians(20.0 + hash * 25.0));
                                float scale = 0.85f + hash * 0.55f;

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
                                task.baseRotationMap.put(pos, rot);
                                task.blockScaleMap.put(pos, scale);

                                if (peeledThisTick <= 6) {
                                    world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                                            1, 0, 0.08, 0, 0.02);
                                }
                            } else {
                                // Các khối lòng đất sâu/phần dư: Bóc thành AIR không tốn Entity
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
                } else {
                    currentPeelRadius = task.maxRadius;
                }

                // 2. Tốc độ góc tăng dần liên tục lên cực hạn
                double totalProgress = task.ageTicks / (double) DETONATION_TICK;
                float angularSpeed = 0.008f + (float) Math.pow(totalProgress, 2.2) * 0.25f;
                task.swirlAngle += angularSpeed;

                // 3. GIAI ĐOẠN CO RÚT SỤP ĐỔ VÀO TÂM HỐ ĐEN (Ticks 280..340, 60 ticks = 3.0 giây)
                // Các khối ngừng xoay rộng, bị hút sụp đổ trực diện vào tâm điểm Hố Đen và thu nhỏ kích thước về 0
                float collapseProg = 0.0f;
                if (task.ageTicks >= 280) {
                    collapseProg = (task.ageTicks - 280) / 60.0f; // 0.0 -> 1.0
                }
                float smoothCollapse = collapseProg * collapseProg * (3.0f - 2.0f * collapseProg);

                // 4. Cập nhật chuyển động 3D cho toàn bộ khối hiển thị
                if (task.ageTicks % 2 == 0) {
                    for (BlockPos pos : task.displayedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        int peelTick = task.peelTickMap.getOrDefault(pos, task.ageTicks);
                        int ticksSincePeel = task.ageTicks - peelTick;
                        float entryProgress = MathHelper.clamp(ticksSincePeel / 30.0f, 0.0f, 1.0f);

                        // Quỹ đạo xoay quanh hố đen
                        Vec3d orbitOffset = SpatialMath.computeDynamicBlackHoleOrbit(
                                pos, task.corePos, currentPeelRadius, task.swirlAngle, entryProgress, 1.0);

                        // Vector tâm điểm Hố Đen (Singularity Center)
                        Vec3d singularityCenter = task.corePos.subtract(Vec3d.ofCenter(pos));

                        // Khi gần nổ (Ticks 280..340): Các khối ngừng xoay rộng, lao thẳng vào tâm hố đen!
                        Vec3d dynamicOffset = (collapseProg > 0.0f)
                                ? orbitOffset.lerp(singularityCenter, Math.min(1.0, smoothCollapse * 1.25))
                                : orbitOffset;

                        Quaternionf rot = new Quaternionf(task.baseRotationMap.getOrDefault(pos, new Quaternionf()))
                                .rotateY(task.swirlAngle * 1.3f);

                        // Scale thu nhỏ dần về 0 khi bị nuốt chửng vào tâm điểm kỳ dị Hố Đen
                        float baseScale = task.blockScaleMap.getOrDefault(pos, 1.0f);
                        float scale = (collapseProg > 0.0f)
                                ? baseScale * (float) Math.max(0.0, 1.0 - smoothCollapse * 1.10)
                                : baseScale;

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(2);
                        display.setTransformation(SpatialMath.createPivotTransform(dynamicOffset, rot, scale));
                    }
                }

                // Lực hút kéo thực thể
                if (task.ageTicks % 6 == 0) {
                    Box suctionBox = new Box(task.groundCenter).expand(currentPeelRadius * 1.4);
                    for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, suctionBox, ent -> ent != task.caster)) {
                        Vec3d pull = task.corePos.subtract(e.getPos()).normalize().multiply(0.22 + totalProgress * 0.35);
                        e.addVelocity(pull.x, pull.y, pull.z);
                        e.velocityModified = true;
                    }
                }

                // Âm thanh Warden & Beacon tăng dần tần số và cao độ
                if (task.ageTicks % 16 == 0) {
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 3.2f, 0.5f + (float) totalProgress * 1.1f);
                }
                if (task.ageTicks % 36 == 0) {
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 3.5f, 0.6f + (float) totalProgress * 1.0f);
                }

                // Cột hạt ánh sáng Relativistic Pulsar Jet mỏng, sắc nét dọc trục Y (không che khuất tầm nhìn)
                if (task.ageTicks >= 160 && task.ageTicks % 2 == 0) {
                    float jetScale = (float) Math.min(1.0, (task.ageTicks - 160) / 150.0);
                    for (int j = 0; j < 6; j++) {
                        double beamY = task.corePos.y + (world.random.nextDouble() - 0.3) * 85.0;
                        double rBeam = (0.08 + world.random.nextDouble() * 0.42) * jetScale;
                        double aBeam = world.random.nextDouble() * Math.PI * 2.0;
                        world.spawnParticles(ParticleTypes.END_ROD,
                                task.corePos.x + Math.cos(aBeam) * rBeam, beamY, task.corePos.z + Math.sin(aBeam) * rBeam,
                                1, 0, 0.25, 0, 0.05);
                        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                                task.corePos.x + Math.cos(aBeam) * (rBeam * 1.3), beamY, task.corePos.z + Math.sin(aBeam) * (rBeam * 1.3),
                                1, 0, 0.1, 0, 0.04);
                    }
                }

                // Tiền chấn dồn dập trước vụ nổ (Ticks 260..309): Báo hiệu khoảnh khắc tận thế
                if (task.ageTicks >= 260 && task.ageTicks < 310 && task.ageTicks % 8 == 0) {
                    float preRumble = (task.ageTicks - 260) / 50.0f;
                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.corePos, 120.0)) {
                        ServerPlayNetworking.send(p, new CinematicFxPayload(
                                CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                                task.corePos.x, task.corePos.y, task.corePos.z,
                                0.22f + preRumble * 0.45f, 9
                        ));
                    }
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.PLAYERS, 2.2f + preRumble * 1.5f, 0.6f + preRumble * 1.2f);
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 2.5f, 0.8f + preRumble * 0.8f);
                }

                // Sóng âm thanh chân không im lặng tại Tick 310 (Chuẩn bị điểm nổ cao trào)
                if (task.ageTicks == 310) {
                    CinematicFxPayload silencePayload = new CinematicFxPayload(
                            CinematicFxPayload.EVENT_VACUUM_SILENCE,
                            task.corePos.x, task.corePos.y, task.corePos.z,
                            1.0f, 30
                    );
                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.corePos, 250.0)) {
                        ServerPlayNetworking.send(p, silencePayload);
                    }
                    if (task.caster != null && task.caster.isAlive() && task.caster.getServerWorld() == world) {
                        ServerPlayNetworking.send(task.caster, silencePayload);
                    }
                }
            }

            // =========================================================================
            // GIAI ĐOẠN 2: ĐẠI VỤ NỔ SIÊU TÂN TINH NGAY ĐỈNH CAO (TICK 340)
            // =========================================================================
            else if (task.ageTicks == DETONATION_TICK && !task.exploded) {
                task.exploded = true;

                // 1. Kích hoạt Shader Siêu Tân Tinh khổng lồ 98m (240 ticks / 12 giây phình to & tan biến dần dần)
                ImpactFxPayload novaPayload = new ImpactFxPayload(
                        task.corePos.x, task.corePos.y, task.corePos.z,
                        ImpactFxPayload.TYPE_SUPERNOVA, 240, 3.5f
                );
                CinematicFxPayload burstCinematic = new CinematicFxPayload(
                        CinematicFxPayload.EVENT_SUPERNOVA_BURST,
                        task.corePos.x, task.corePos.y, task.corePos.z,
                        1.0f, 110
                );
                for (ServerPlayerEntity p : PlayerLookup.around(world, task.corePos, 250.0)) {
                    ServerPlayNetworking.send(p, novaPayload);
                    ServerPlayNetworking.send(p, burstCinematic);
                }
                if (task.caster != null && task.caster.isAlive() && task.caster.getServerWorld() == world) {
                    ServerPlayNetworking.send(task.caster, novaPayload);
                    ServerPlayNetworking.send(task.caster, burstCinematic);
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

                // 3. VFX vụ nổ chói lòa & Quả cầu lửa 3D phóng đa hướng quy mô cực đại
                // BỎ ParticleTypes.FLASH theo yêu cầu! Thay bằng tia sét hồ quang và tàn nham rực cháy
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, task.corePos.x, task.corePos.y, task.corePos.z, 120, 8.0, 8.0, 8.0, 0.45);
                world.spawnParticles(ParticleTypes.LAVA, task.corePos.x, task.corePos.y, task.corePos.z, 90, 6.0, 6.0, 6.0, 0.55);
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, task.corePos.x, task.corePos.y, task.corePos.z, 50, 8.0, 8.0, 8.0, 0.0);
                world.spawnParticles(ParticleTypes.SONIC_BOOM, task.corePos.x, task.corePos.y, task.corePos.z, 24, 6.0, 6.0, 6.0, 0.0);
                world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.65f, 0.15f), 4.0f),
                        task.corePos.x, task.corePos.y, task.corePos.z, 450, 18.0, 18.0, 18.0, 1.2);

                Random blastRng = new Random(task.groundCenter.asLong());
                for (int b = 0; b < 220; b++) {
                    double bTheta = blastRng.nextDouble() * Math.PI * 2.0;
                    double bPhi = (blastRng.nextDouble() - 0.2) * Math.PI * 0.5; // Bán cầu 3D bung nở
                    double bSpd = 2.0 + blastRng.nextDouble() * 4.2;
                    double vx = Math.cos(bPhi) * Math.cos(bTheta) * bSpd;
                    double vy = Math.sin(bPhi) * bSpd + 0.8;
                    double vz = Math.cos(bPhi) * Math.sin(bTheta) * bSpd;
                    world.spawnParticles(ParticleTypes.FLAME, task.corePos.x, task.corePos.y, task.corePos.z, 1, vx, vy, vz, 0.4);
                    world.spawnParticles(ParticleTypes.LAVA, task.corePos.x, task.corePos.y, task.corePos.z, 1, vx * 0.8, vy * 0.8, vz * 0.8, 0.25);
                }

                // 4. Sát thương Calamity hủy diệt cực đại (80 - 460 damage) bao trùm toàn bộ bán kính vụ nổ (125m)
                Box blastBox = new Box(task.groundCenter).expand(task.blastRadius + 10.0);
                for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, blastBox, ent -> ent != task.caster)) {
                    double dist = e.getPos().distanceTo(task.corePos);
                    if (dist <= task.blastRadius + 8.0) {
                        float dmg = (float) Math.max(80.0, (1.0 - (dist / task.blastRadius)) * 380.0 + 80.0);
                        e.damage(world.getDamageSources().explosion(null, task.caster), dmg);

                        Vec3d push = e.getPos().subtract(task.corePos).normalize().multiply(5.8).add(0, 2.2, 0);
                        e.addVelocity(push.x, push.y, push.z);
                        e.velocityModified = true;
                    }
                }

                // 5. HIỆU ỨNG VỤ NỔ VẬT LÝ 3D:
                // Giữ lại tối đa 650 BlockDisplayEntity và phóng văng siêu thanh ra bán kính 45m - 95m!
                Random rng = new Random(task.groundCenter.asLong());
                int flingDisplayCount = Math.min(650, task.displayedPositions.size());
                for (int i = 0; i < task.displayedPositions.size(); i++) {
                    BlockPos pos = task.displayedPositions.get(i);
                    if (i < flingDisplayCount) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record != null && record.displayEntityUuid != null) {
                            BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                            if (display != null && display.isAlive()) {
                                double fAngle = rng.nextDouble() * Math.PI * 2.0;
                                double fDist = 45.0 + rng.nextDouble() * 45.0;
                                double targetX = task.corePos.x + Math.cos(fAngle) * fDist;
                                double targetY = task.groundCenter.getY() + 2.0 + rng.nextDouble() * 16.0;
                                double targetZ = task.corePos.z + Math.sin(fAngle) * fDist;

                                Vec3d flingDisp = new Vec3d(targetX, targetY, targetZ).subtract(Vec3d.ofCenter(pos));
                                Quaternionf flingRot = new Quaternionf()
                                        .rotateY((float) (rng.nextDouble() * Math.PI * 2.0))
                                        .rotateX((float) (rng.nextDouble() * Math.PI));

                                display.setStartInterpolation(0);
                                display.setInterpolationDuration(12);
                                display.setTransformation(SpatialMath.createPivotTransform(flingDisp, flingRot, 0.9f));

                                task.blastFlingPositions.add(pos);
                                continue;
                            }
                        }
                    }
                    SpatialDisplacementManager.removeActiveRecord(world.getRegistryKey(), pos);
                }

                for (BlockPos pos : task.interiorPositions) {
                    SpatialDisplacementManager.removeActiveRecord(world.getRegistryKey(), pos);
                }

                // 6. Chuẩn bị tạo Hố sâu tận thế Parabol gồ ghề & San phẳng núi non khổng lồ (GẤP ĐÔI BÁN KÍNH: 125m, sâu -65m)
                int craterR = (int) task.blastRadius;
                BlockPos.Mutable cMut = new BlockPos.Mutable();
                int centerGroundY = task.groundCenter.getY();

                for (int cx = -craterR; cx <= craterR; cx++) {
                    for (int cz = -craterR; cz <= craterR; cz++) {
                        double distSq = cx * cx + cz * cz;
                        if (distSq > craterR * craterR) continue;

                        double dist2D = Math.sqrt(distSq);
                        double angle = Math.atan2(cz, cx);
                        double ridgeNoise = Math.sin(angle * 7.0) * 8.5 + Math.cos(angle * 13.0) * 4.8;
                        double effectiveR = craterR + ridgeNoise;
                        if (dist2D > effectiveR) continue;

                        double normDist = dist2D / effectiveR;
                        // Chiều sâu Parabol sâu nhất ở tâm (-65 blocks) và thoải dần ra vành ngoài 125m
                        double depthCurve = Math.pow(1.0 - normDist, 1.45);
                        double calculatedDepth = depthCurve * 65.0;

                        // Fractal 3D noise gồ ghề lởm chởm cực mạnh (tuyệt đối không phẳng lì!)
                        int wx = task.groundCenter.getX() + cx;
                        int wz = task.groundCenter.getZ() + cz;
                        double floorNoise = (SpatialMath.hash01(new BlockPos(wx * 2, 0, wz * 2)) - 0.5) * 10.0;
                        floorNoise += Math.sin(wx * 0.28) * Math.cos(wz * 0.28) * 4.5;
                        floorNoise += Math.sin(wx * 0.7 + wz * 0.5) * 2.2;

                        int realSurfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, wx, wz);
                        int topCutY = Math.min(world.getTopY() - 1, realSurfaceY + 1);
                        int floorY = Math.max(world.getBottomY() + 3, (int) Math.round(centerGroundY - calculatedDepth + floorNoise));

                        if (topCutY >= floorY) {
                            for (int y = floorY; y <= topCutY; y++) {
                                cMut.set(wx, y, wz);
                                if (cMut.getY() < world.getBottomY() || cMut.getY() >= world.getTopY()) continue;
                                task.craterCarveBatch.add(cMut.toImmutable());
                            }
                        }
                    }
                }

                // 7. Phóng 200 FallingBlockEntity đá nóng chảy và dung nham bay vọt ra bán kính 70m
                int fallingBlocksCount = 200;
                BlockState[] moltenStates = new BlockState[] {
                        Blocks.MAGMA_BLOCK.getDefaultState(),
                        Blocks.OBSIDIAN.getDefaultState(),
                        Blocks.CRYING_OBSIDIAN.getDefaultState(),
                        Blocks.BLACKSTONE.getDefaultState(),
                        Blocks.COBBLESTONE.getDefaultState(),
                        Blocks.DEEPSLATE.getDefaultState()
                };

                for (int i = 0; i < fallingBlocksCount; i++) {
                    double fAngle = rng.nextDouble() * Math.PI * 2.0;
                    double fSpeed = 1.2 + rng.nextDouble() * 2.2;
                    double spawnX = task.groundCenter.getX() + 0.5 + Math.cos(fAngle) * 5.0;
                    double spawnY = task.groundCenter.getY() + 3.5;
                    double spawnZ = task.groundCenter.getZ() + 0.5 + Math.sin(fAngle) * 5.0;

                    BlockState flingState = moltenStates[rng.nextInt(moltenStates.length)];
                    FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world,
                            BlockPos.ofFloored(spawnX, spawnY, spawnZ),
                            flingState);
                    falling.setVelocity(Math.cos(fAngle) * fSpeed, 0.9 + rng.nextDouble() * 1.4, Math.sin(fAngle) * fSpeed);
                    falling.velocityModified = true;
                }
            }

            // =========================================================================
            // GIAI ĐOẠN 3: AFTERMATH, SÓNG THẦN ĐẤT ĐÁ CUỘN TRẢI DÀI (Ticks 341..500)
            // =========================================================================
            else if (task.ageTicks > DETONATION_TICK) {
                // Rung chấn mặt đất cuồn cuộn kéo dài sau đại nổ (Ticks 341..368)
                if (task.ageTicks <= DETONATION_TICK + 28 && task.ageTicks % 4 == 0) {
                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.corePos, 160.0)) {
                        ServerPlayNetworking.send(p, new CinematicFxPayload(
                                CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                                task.corePos.x, task.corePos.y, task.corePos.z,
                                0.75f, 16
                        ));
                    }
                }

                // Tại Tick 355: Dọn dẹp các khối hiển thị văng sau khi hoàn tất animation siêu thanh
                if (task.ageTicks == 355) {
                    for (BlockPos pos : task.blastFlingPositions) {
                        SpatialDisplacementManager.removeActiveRecord(world.getRegistryKey(), pos);
                    }
                    task.blastFlingPositions.clear();
                }

                // 1. Đào hố tận thế và nung chảy địa hình gồ ghề chia theo mẻ (35 sub-ticks)
                if (task.craterIndex < task.craterCarveBatch.size()) {
                    int batchSize = (task.craterCarveBatch.size() / 35) + 1;
                    int endIndex = Math.min(task.craterCarveBatch.size(), task.craterIndex + batchSize);
                    Random rng = new Random();

                    for (int i = task.craterIndex; i < endIndex; i++) {
                        BlockPos pos = task.craterCarveBatch.get(i);
                        BlockState currentState = world.getBlockState(pos);
                        if (currentState.getBlock() == Blocks.BEDROCK) continue;

                        double dist2D = Math.sqrt(pos.getSquaredDistance(task.groundCenter.getX(), pos.getY(), task.groundCenter.getZ()));
                        double angle = Math.atan2(pos.getZ() - task.groundCenter.getZ(), pos.getX() - task.groundCenter.getX());
                        double ridgeNoise = Math.sin(angle * 7.0) * 8.5 + Math.cos(angle * 13.0) * 4.8;
                        double effectiveR = task.blastRadius + ridgeNoise;
                        double normDist = Math.min(1.0, dist2D / effectiveR);
                        double depthCurve = Math.pow(1.0 - normDist, 1.45);
                        double calculatedDepth = depthCurve * 65.0;

                        double floorNoise = (SpatialMath.hash01(new BlockPos(pos.getX() * 2, 0, pos.getZ() * 2)) - 0.5) * 10.0;
                        floorNoise += Math.sin(pos.getX() * 0.28) * Math.cos(pos.getZ() * 0.28) * 4.5;
                        floorNoise += Math.sin(pos.getX() * 0.7 + pos.getZ() * 0.5) * 2.2;
                        int floorY = Math.max(world.getBottomY() + 3, (int) Math.round(task.groundCenter.getY() - calculatedDepth + floorNoise));

                        // TẤT CẢ các khối trên floorY (bao gồm cả nguyên quả núi phía trên) đều bị bốc hơi thành AIR!
                        if (pos.getY() > floorY) {
                            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
                        }
                        // Tại đúng đáy floorY: LUÔN LUÔN Nung chảy thành địa hình dung nham nứt nẻ gồ ghề (kể cả trước đó là AIR)
                        else if (pos.getY() == floorY) {
                            float rVal = rng.nextFloat();
                            BlockState scorchState;
                            if (dist2D <= 16.0) {
                                // Vực tâm nổ: Hồ dung nham sôi sùng sục xen kẽ Magma và Crying Obsidian
                                if (rVal < 0.38f) {
                                    scorchState = Blocks.LAVA.getDefaultState();
                                } else if (rVal < 0.68f) {
                                    scorchState = Blocks.MAGMA_BLOCK.getDefaultState();
                                } else if (rVal < 0.85f) {
                                    scorchState = Blocks.CRYING_OBSIDIAN.getDefaultState();
                                } else {
                                    scorchState = Blocks.OBSIDIAN.getDefaultState();
                                }
                            } else if (dist2D <= 36.0) {
                                // Lòng chảo giữa: Vành đai đá nóng chảy, nứt toác
                                if (rVal < 0.35f) {
                                    scorchState = Blocks.MAGMA_BLOCK.getDefaultState();
                                } else if (rVal < 0.60f) {
                                    scorchState = Blocks.CRYING_OBSIDIAN.getDefaultState();
                                } else if (rVal < 0.82f) {
                                    scorchState = Blocks.OBSIDIAN.getDefaultState();
                                } else {
                                    scorchState = Blocks.BLACKSTONE.getDefaultState();
                                }
                            } else {
                                // Vành ngoài sạt lở: Đá đen, đá vụn, deepslate nứt nẻ
                                if (rVal < 0.40f) {
                                    scorchState = Blocks.BLACKSTONE.getDefaultState();
                                } else if (rVal < 0.70f) {
                                    scorchState = Blocks.COBBLESTONE.getDefaultState();
                                } else if (rVal < 0.90f) {
                                    scorchState = Blocks.DEEPSLATE.getDefaultState();
                                } else {
                                    scorchState = Blocks.MAGMA_BLOCK.getDefaultState();
                                }
                            }
                            world.setBlockState(pos, scorchState, Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
                        }
                    }
                    task.craterIndex = endIndex;
                }

                // 2. SÓNG THẦN ĐẤT ĐÁ & BÃO KHÓI BỤI CUỘN TRẢI DÀI TRÊN MẶT ĐẤT (Rolling Pyroclastic Surge):
                // Lan tỏa từ tâm 4m ra 65m quét dọc theo bề mặt thực tế của địa hình
                Random rng = new Random();
                double surgeProgress = Math.min(1.0, (task.ageTicks - DETONATION_TICK) / 90.0);
                double surgeRadius = 4.0 + surgeProgress * 62.0;

                for (int s = 0; s < 24; s++) {
                    double ang = rng.nextDouble() * Math.PI * 2.0;
                    double r = surgeRadius + (rng.nextDouble() - 0.5) * 8.0;
                    double px = task.groundCenter.getX() + 0.5 + Math.cos(ang) * r;
                    double pz = task.groundCenter.getZ() + 0.5 + Math.sin(ang) * r;
                    int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) px, (int) pz);
                    double vx = Math.cos(ang) * 0.45;
                    double vz = Math.sin(ang) * 0.45;

                    world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, surfaceY + 0.8, pz, 2, vx, 0.12, vz, 0.08);
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE, px, surfaceY + 0.5, pz, 2, vx, 0.08, vz, 0.05);
                    world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.COBBLESTONE.getDefaultState()),
                            px, surfaceY + 1.0, pz, 5, 0.8, 0.5, 0.8, 0.25);
                }

                // 3. Cột Nấm & Đỉnh Nấm Anvil Khổng Lồ 3D (Towering Volumetric Mushroom Cloud)
                // Cột nấm bốc cao cuồn cuộn từ đáy hố lên cao 75m (35 hạt/tick)
                for (int k = 0; k < 35; k++) {
                    double stemH = rng.nextDouble() * 75.0;
                    double stemR = (1.5 + (stemH / 75.0) * 5.0) * (0.6 + rng.nextDouble() * 0.4);
                    double sAngle = rng.nextDouble() * Math.PI * 2.0;
                    double sx = task.groundCenter.getX() + 0.5 + Math.cos(sAngle) * stemR;
                    double sy = task.groundCenter.getY() + stemH;
                    double sz = task.groundCenter.getZ() + 0.5 + Math.sin(sAngle) * stemR;

                    double upVy = 0.25 + (1.0 - stemH / 75.0) * 0.35;
                    if (stemH < 22.0) {
                        world.spawnParticles(ParticleTypes.LAVA, sx, sy, sz, 1, 0, 0.15, 0, 0.05);
                        world.spawnParticles(ParticleTypes.FLAME, sx, sy, sz, 1, 0, 0.25, 0, 0.08);
                    }
                    world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, sx, sy, sz, 1, 0, upVy, 0, 0.04);
                    if (k % 3 == 0) {
                        world.spawnParticles(ParticleTypes.LARGE_SMOKE, sx, sy, sz, 1, 0, upVy * 0.8, 0, 0.03);
                    }
                    if (k % 5 == 0) {
                        world.spawnParticles(ParticleTypes.SQUID_INK, sx, sy, sz, 1, 0, upVy * 0.5, 0, 0.02);
                    }
                }

                // Đỉnh nấm xòe ngang (Anvil Cap) ở độ cao 55m..75m bung rộng ra bán kính 14m..38m (25 hạt/tick)
                double capAge = Math.min(1.0, (task.ageTicks - DETONATION_TICK) / 100.0);
                double capMaxR = 14.0 + capAge * 24.0;
                for (int c = 0; c < 25; c++) {
                    double cAng = rng.nextDouble() * Math.PI * 2.0;
                    double cR = capMaxR * (0.35 + rng.nextDouble() * 0.65);
                    double cx = task.groundCenter.getX() + 0.5 + Math.cos(cAng) * cR;
                    double cy = task.groundCenter.getY() + 55.0 + (rng.nextDouble() - 0.2) * 18.0;
                    double cz = task.groundCenter.getZ() + 0.5 + Math.sin(cAng) * cR;

                    // Chuyển động đối lưu cuộn vành ngoài xẹp xuống (Torus vortex rolling)
                    double outVx = Math.cos(cAng) * 0.22;
                    double outVz = Math.sin(cAng) * 0.22;
                    double rollVy = -0.04 - (cR / capMaxR) * 0.08;

                    world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, cy, cz, 2, outVx, rollVy, outVz, 0.03);
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE, cx, cy, cz, 1, outVx * 0.7, rollVy, outVz * 0.7, 0.02);
                    if (rng.nextFloat() < 0.25f) {
                        world.spawnParticles(ParticleTypes.ASH, cx, cy, cz, 2, outVx * 0.5, 0.02, outVz * 0.5, 0.01);
                    }
                    if (rng.nextFloat() < 0.15f) {
                        world.spawnParticles(ParticleTypes.FLAME, cx, cy, cz, 1, outVx * 0.3, 0.02, outVz * 0.3, 0.02);
                    }
                }

                // 4. Âm thanh sấm rền và tiếng gầm vũ trụ vang vọng xa dần trong 7 giây
                float capProgress = Math.min(1.0f, (task.ageTicks - DETONATION_TICK) / 140.0f);
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
