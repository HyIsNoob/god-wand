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
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
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
    public static boolean isFoliageOrNonSolid(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.FLOWERS) || state.isIn(BlockTags.CROPS)
                || state.isIn(BlockTags.SAPLINGS) || state.getBlock() instanceof LeavesBlock
                || state.getBlock() instanceof PlantBlock || state.getBlock() instanceof VineBlock
                || !state.isFullCube(world, pos) || !state.isOpaque()) {
            return true;
        }
        return false;
    }

    static Vec3d mobPullVelocity(Vec3d current, Vec3d towardCore, double progress) {
        double distance = towardCore.length();
        double charge = Math.clamp(progress, 0, 1);
        double speed = Math.min(distance * 0.18, 0.9 + charge * 2.4);
        Vec3d desired = distance < 0.000001 ? Vec3d.ZERO : towardCore.multiply(speed / distance);
        Vec3d result = current.lerp(desired, 0.3 + charge * 0.2);
        double limit = Math.min(3.3, distance * 0.65);
        return result.lengthSquared() > limit * limit ? result.normalize().multiply(limit) : result;
    }

    public static final int DETONATION_TICK = SupernovaSequence.SKY_RELEASE;
    public static final int IMPACT_TICK = DETONATION_TICK - ImpactFxPayload.SUPERNOVA_IMPACT_TICKS;
    public static final int TERRAIN_START_TICK = DETONATION_TICK + 4;
    public static final int MAX_LIFETIME_TICKS = DETONATION_TICK + SupernovaSequence.BURST_TICKS;
    public static final int MAX_DISPLAYS_CAP = 1800;

    public static class SupernovaTask {
        public final ServerWorld world;
        public final ServerPlayerEntity caster;
        public final BlockPos groundCenter;
        public final Vec3d corePos;
        public final double terrainRadius = net.godwand.config.GodWandConfig.SERVER.supernovaTerrainRadius;
        public final int displayLimit = net.godwand.config.GodWandConfig.SERVER.supernovaMaxDisplays;
        public final double maxRadius = Math.min(55.0, terrainRadius); // Bán kính hút đất dạng cầu gồ ghề mở rộng (Suction Cavity)
        public final double blastRadius = 96.0; // Terrain and damage radius; visuals retain their full scale.

        public int ageTicks = 0;
        public float swirlAngle = 0.0f;
        public boolean exploded = false;

        // Progressive Peeling & Accretion Escalation (Ticks 1..340)
        public final List<BlockPos> candidatePositions = new ArrayList<>();
        public final Set<BlockPos> peeledPositions = new HashSet<>();
        public final Map<BlockPos, Integer> peelTickMap = new HashMap<>();
        public int candidateScanIndex = 0;

        public final List<BlockPos> displayedPositions = new ArrayList<>();
        public final List<BlockPos> interiorPositions = new ArrayList<>();
        public final List<BlockPos> blastFlingPositions = new ArrayList<>();

        public final Map<BlockPos, Quaternionf> baseRotationMap = new HashMap<>();
        public final Map<BlockPos, Float> blockScaleMap = new HashMap<>();

        final SupernovaCraterCursor crater = new SupernovaCraterCursor((int)terrainRadius);

        public SupernovaTask(ServerWorld world, ServerPlayerEntity caster, BlockPos groundCenter, Vec3d corePos) {
            this.world = world;
            this.caster = caster;
            this.groundCenter = groundCenter.toImmutable();
            this.corePos = corePos;
        }
    }

    private static final Queue<SupernovaTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();
    private static final Map<ServerPlayerEntity, BlockPos> CHARGE_TARGETS = new WeakHashMap<>();

    public static BlockPos findTarget(PlayerEntity player) {
        var world = player.getWorld();
        HitResult hit = player.raycast(180.0, 1.0f, false);
        BlockPos groundCenter;
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bHit) {
            groundCenter = bHit.getBlockPos();
        } else {
            Vec3d forward = player.getEyePos().add(player.getRotationVec(1.0f).multiply(120.0));
            groundCenter = BlockPos.ofFloored(forward);
            if (world.isClient() && !world.getChunkManager().isChunkLoaded(groundCenter.getX() >> 4, groundCenter.getZ() >> 4)) return null;
            groundCenter = groundCenter.withY(Math.min(groundCenter.getY(), world.getTopY() - 1));
            while (groundCenter.getY() > world.getBottomY() && world.getBlockState(groundCenter).isAir()) {
                groundCenter = groundCenter.down();
            }
        }
        return groundCenter.toImmutable();
    }

    public static void beginCharge(ServerPlayerEntity player) { CHARGE_TARGETS.put(player, findTarget(player)); }
    public static void cancelCharge(ServerPlayerEntity player) { CHARGE_TARGETS.remove(player); }

    public static void cast(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos groundCenter = CHARGE_TARGETS.remove(player);
        if (groundCenter == null) groundCenter = findTarget(player);

        // Hố đen lơ lửng cách mặt đất 20.0m (Quy mô Calamity)
        Vec3d corePos = groundCenter.toCenterPos().add(0, 20.0, 0);
        SupernovaTask task = new SupernovaTask(world, player, groundCenter, corePos);

        // 1. Kích hoạt Shader Hố Đen 3D độc quyền của Chiêu 4 (340 ticks tích tụ đơn điệu)

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
                    if (isFoliageOrNonSolid(world, mutable, state)) {
                        subSurfaceCandidates.add(mutable.toImmutable());
                    } else if (solidCount <= 3) {
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

        CinematicFxPayload castShake = new CinematicFxPayload(
                CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                corePos.x, corePos.y, corePos.z,
                0.18f, 8
        );
        for (ServerPlayerEntity p : PlayerLookup.around(world, corePos, 120.0)) {
            ServerPlayNetworking.send(p, castShake);
        }

        ACTIVE_TASKS.add(task);
        // Start the client clock only after preparing the server task.
        ImpactFxPayload blackHolePayload = new ImpactFxPayload(
                corePos.x, corePos.y, corePos.z,
                ImpactFxPayload.TYPE_BLACK_HOLE, IMPACT_TICK, 1.0f
        );
        Set<ServerPlayerEntity> viewers = new HashSet<>(PlayerLookup.around(world, corePos, 250.0));
        if (player.isAlive()) viewers.add(player);
        for (ServerPlayerEntity viewer : viewers) ServerPlayNetworking.send(viewer, blackHolePayload);

    }

    public static void tick(ServerWorld world) {

        Iterator<SupernovaTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            SupernovaTask task = it.next();
            if (task.world != world) continue;

            task.ageTicks++;
            if (task.ageTicks >= DETONATION_TICK && task.ageTicks < DETONATION_TICK + 60) {
                carveSphericalCraterShell(task, task.ageTicks - DETONATION_TICK, 60);
            }

            // =========================================================================
            // GIAI ĐOẠN 1: BÓC ĐẤT NÚI TỶ LỆ THUẬN (Ticks 1..279)
            // TỪ TICK 280 TRỞ ĐI: DỪNG BÓC MẶT ĐẤT, BƯỚC VÀO PHA CO RÚT SỤP ĐỔ
            // =========================================================================
            if (task.ageTicks < IMPACT_TICK) {
                // Hiệu ứng shockwave tản ra trước khi đâm xuống tầm 0.5 giây (Tick 330 = IMPACT_TICK - 10)
                if (task.ageTicks == IMPACT_TICK - 10) {
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 2.6f, 1.35f);
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE, SoundCategory.PLAYERS, 2.0f, 0.7f);
                    world.spawnParticles(ParticleTypes.SONIC_BOOM,
                            task.corePos.x, task.corePos.y, task.corePos.z, 1, 0, 0, 0, 0);
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                            task.corePos.x, task.corePos.y, task.corePos.z, 32, 2.5, 1.0, 2.5, 0.35);

                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.corePos, 200.0)) {
                        ServerPlayNetworking.send(p, new CinematicFxPayload(
                                CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                                task.corePos.x, task.corePos.y, task.corePos.z,
                                0.22f, 8
                        ));
                    }
                }

                double currentPeelRadius = 2.5;

                // 1. Chỉ bóc tách đất và bứt núi non đến trước Tick 280
                if (task.ageTicks < SupernovaSequence.PEEL_END) {
                    double progress = task.ageTicks / (double) SupernovaSequence.PEEL_END; // 0.0 -> 1.0

                    // Bán kính vùng đất núi bị hút mở rộng dần theo tỷ lệ thuận (2.5m -> 34m)
                    double bhScale = Math.pow(progress, 1.25);
                    currentPeelRadius = 2.5 + (task.maxRadius - 2.5) * bhScale;
                    double currentRadiusSq = currentPeelRadius * currentPeelRadius;

                    // Hạn ngạch số khối BlockDisplayEntity tối đa trên bầu trời tăng dần lên 1800 khối
                    int seedDisplays = Math.min(35, task.displayLimit);
                    int maxDisplaysNow = seedDisplays + (int)((task.displayLimit - seedDisplays) * Math.pow(progress, 1.35));

                    int peeledThisTick = 0;
                    int maxPeelPerTick = 80;
                    int size = task.candidatePositions.size();
                    for (int i = task.candidateScanIndex; i < size; i++) {
                        BlockPos pos = task.candidatePositions.get(i);
                        if (task.peeledPositions.contains(pos)) {
                            if (i == task.candidateScanIndex) task.candidateScanIndex++;
                            continue;
                        }

                        double dx = pos.getX() - task.groundCenter.getX();
                        double dz = pos.getZ() - task.groundCenter.getZ();
                        if (dx * dx + dz * dz <= currentRadiusSq) {
                            task.peeledPositions.add(pos);
                            task.peelTickMap.put(pos, task.ageTicks);
                            peeledThisTick++;

                            BlockState state = world.getBlockState(pos);
                            boolean canSpawn = !isFoliageOrNonSolid(world, pos, state);

                            if (canSpawn && task.displayedPositions.size() < maxDisplaysNow && task.displayedPositions.size() < MAX_DISPLAYS_CAP) {
                                float hash = (float) SpatialMath.hash01(pos);

                                Quaternionf rot = new Quaternionf()
                                        .rotateY((float) (hash * Math.PI * 2.0))
                                        .rotateX((float) Math.toRadians(20.0 + hash * 25.0));
                                float scale = 0.85f + hash * 0.55f;

                                SpatialDisplacementManager.displaceBlockPermanently(
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

                                task.displayedPositions.add(pos);
                                var lifted = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                                var display = lifted == null ? null : SpatialDisplacementManager.getActiveEntity(lifted.displayEntityUuid);
                                if (display != null) {
                                    display.setBrightness(null);
                                    display.setGlowing(false);
                                    display.setGlowColorOverride(-1);
                                }
                                task.baseRotationMap.put(pos, rot);
                                task.blockScaleMap.put(pos, scale);

                                if (peeledThisTick <= 6) {
                                    world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                                            1, 0, 0.08, 0, 0.02);
                                }
                            } else {
                                // Các khối lòng đất sâu/phần dư: Bóc thành AIR không tốn Entity
                                SpatialDisplacementManager.displaceBlockPermanently(
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

                                task.interiorPositions.add(pos);
                            }

                            if (peeledThisTick >= maxPeelPerTick) break;
                        }
                    }
                } else {
                    currentPeelRadius = task.maxRadius;
                }

                // 2. Tốc độ góc tăng dần liên tục lên cực hạn
                double totalProgress = task.ageTicks / (double) IMPACT_TICK;
                float angularSpeed = 0.008f + (float) Math.pow(totalProgress, 2.2) * 0.38f;
                task.swirlAngle += angularSpeed;

                // 3. GIAI ĐOẠN CO RÚT SỤP ĐỔ VÀO TÂM HỐ ĐEN (Ticks 280..340, 60 ticks = 3.0 giây)
                // Các khối ngừng xoay rộng, bị hút sụp đổ trực diện vào tâm điểm Hố Đen và thu nhỏ kích thước về 0
                if (task.ageTicks % 2 == 0) {
                    for (BlockPos pos : task.displayedPositions) {
                        float collapse = SupernovaSequence.swallow(task.ageTicks, SpatialMath.hash01(pos));
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
                        Vec3d dynamicOffset = (collapse > 0.0f)
                                ? orbitOffset.lerp(singularityCenter, Math.min(1.0, collapse))
                                : orbitOffset;

                        Quaternionf rot = new Quaternionf(task.baseRotationMap.getOrDefault(pos, new Quaternionf()))
                                .rotateY(task.swirlAngle * 1.3f);

                        // Scale thu nhỏ dần về 0 khi bị nuốt chửng vào tâm điểm kỳ dị Hố Đen
                        float baseScale = task.blockScaleMap.getOrDefault(pos, 1.0f);
                        float scale = (collapse > 0.0f)
                                ? baseScale * (float) Math.max(0.0, 1.0 - collapse)
                                : baseScale;

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(2);
                        if (collapse > 0 && scale > 0.001f) {
                            Vec3d radial = Vec3d.ofCenter(pos).add(dynamicOffset).subtract(task.corePos).normalize();
                            Quaternionf tidalRotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0),
                                    new Vector3f((float) radial.x, (float) radial.y, (float) radial.z));
                            Vector3f tidalScale = new Vector3f(scale * 0.38f, scale * (1 + collapse * 6), scale * 0.38f);
                            Vector3f pivot = new Vector3f(0.5f).mul(tidalScale).rotate(tidalRotation);
                            Vector3f translation = new Vector3f((float) dynamicOffset.x + 0.5f,
                                    (float) dynamicOffset.y + 0.5f, (float) dynamicOffset.z + 0.5f).sub(pivot);
                            display.setTransformation(new net.minecraft.util.math.AffineTransformation(translation, tidalRotation, tidalScale, null));
                        } else {
                            display.setTransformation(SpatialMath.createPivotTransform(dynamicOffset, rot, scale));
                        }
                    }
                }

                // Lực hút kéo thực thể: Hút TOÀN BỘ sinh vật (trừ caster), và KILL ngay lập tức khi chạm vào tâm hố đen
                Box suctionBox = new Box(task.groundCenter).expand(currentPeelRadius * 1.4);
                for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, suctionBox, ent -> ent != task.caster)) {
                    double distToCoreSq = e.squaredDistanceTo(task.corePos);
                    if (distToCoreSq <= 12.25) { // Bán kính 3.5m quanh tâm hố đen
                        var source = world.getDamageSources().outOfWorld();
                        if (e.getMaxHealth() <= 50.0f) {
                            // Tiêu diệt ngay quái thường và động vật dưới 50 máu
                            e.damage(source, Float.MAX_VALUE);
                            if (e.isAlive()) {
                                e.setHealth(0.0f);
                                e.onDeath(source);
                            }
                            world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, e.getX(), e.getY() + 0.5, e.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.2, 0.2, 0.2, 0.1);
                            continue;
                        } else {
                            // Quái máu trâu / Boss (> 50 máu như Rồng Ender, Wither): Sát thương nghiền nát liên tục, không one-shot
                            if (task.ageTicks % 4 == 0) {
                                e.damage(source, 15.0f);
                            }
                            world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, e.getX(), e.getY() + 0.5, e.getZ(), 2, 0.2, 0.2, 0.2, 0.05);
                            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 0.5, e.getZ(), 3, 0.2, 0.2, 0.2, 0.1);
                            Vec3d swirl = task.corePos.subtract(e.getPos()).multiply(0.25);
                            e.setVelocity(e.getVelocity().multiply(0.6).add(swirl));
                            e.velocityModified = true;
                            continue;
                        }
                    }

                    if (e instanceof net.minecraft.entity.mob.MobEntity) {
                        e.setVelocity(mobPullVelocity(e.getVelocity(), task.corePos.subtract(e.getPos()), totalProgress));
                        e.velocityModified = true;
                    } else if (task.ageTicks % 2 == 0) {
                        Vec3d pull = task.corePos.subtract(e.getPos()).normalize().multiply(0.40 + totalProgress * 0.70);
                        e.setVelocity(e.getVelocity().lerp(pull, 0.35));
                        e.velocityModified = true;
                    }
                }

                // Âm thanh Warden & Beacon tăng dần tần số và cao độ

            }

            // =========================================================================
            // GIAI ĐOẠN 2: ĐẠI VỤ NỔ SIÊU TÂN TINH NGAY ĐỈNH CAO (TICK 340)
            // =========================================================================
            else if (task.ageTicks == IMPACT_TICK) {
                var impact = new ImpactFxPayload(task.corePos.x, task.corePos.y, task.corePos.z,
                        ImpactFxPayload.TYPE_SUPERNOVA_IMPACT, ImpactFxPayload.SUPERNOVA_IMPACT_TICKS, 1);
                Set<ServerPlayerEntity> viewers = new HashSet<>(PlayerLookup.around(world, task.corePos, 250));
                if (task.caster != null && task.caster.isAlive() && task.caster.getServerWorld() == world) viewers.add(task.caster);
                for (ServerPlayerEntity viewer : viewers) ServerPlayNetworking.send(viewer, impact);
            }
            else if (task.ageTicks == DETONATION_TICK && !task.exploded) {
                task.exploded = true;
                broadcastDetonation(task);

                // 1. Kích hoạt Shader Siêu Tân Tinh khổng lồ 98m (240 ticks / 12 giây phình to & tan biến dần dần)
                // 2. Âm thanh tận thế đa tầng

                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, task.corePos.x, task.corePos.y, task.corePos.z, 28, 8.0, 8.0, 8.0, 0.45);
                world.spawnParticles(ParticleTypes.LAVA, task.corePos.x, task.corePos.y, task.corePos.z, 16, 6.0, 6.0, 6.0, 0.55);
                // Mob one-shot damage within the blast radius.
                Box blastBox = new Box(task.groundCenter).expand(task.blastRadius + 10.0);
                for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, blastBox, ent -> ent != task.caster)) {
                    double dist = e.getPos().distanceTo(task.corePos);
                    if (dist <= task.blastRadius + 8.0) {
                        float dmg = (float) Math.max(80.0, (1.0 - (dist / task.blastRadius)) * 380.0 + 80.0);
                        var source = world.getDamageSources().explosion(null, task.caster);
                        if (e instanceof net.minecraft.entity.mob.MobEntity && e.getMaxHealth() <= 50.0f) {
                            e.damage(source, Float.MAX_VALUE);
                            if (e.isAlive()) {
                                e.setHealth(0.0f);
                                e.onDeath(source);
                            }
                        } else {
                            e.damage(source, dmg);
                        }

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
                                double fDist = 95.0 + rng.nextDouble() * 45.0;
                                double targetX = task.corePos.x + Math.cos(fAngle) * fDist;
                                double targetY = task.groundCenter.getY() + 2.0 + rng.nextDouble() * 16.0;
                                double targetZ = task.corePos.z + Math.sin(fAngle) * fDist;

                                Vec3d flingDisp = new Vec3d(targetX, targetY, targetZ).subtract(Vec3d.ofCenter(pos));
                                Quaternionf flingRot = new Quaternionf()
                                        .rotateY((float) (rng.nextDouble() * Math.PI * 2.0))
                                        .rotateX((float) (rng.nextDouble() * Math.PI));

                                display.setStartInterpolation(0);
                                display.setInterpolationDuration(44);
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

                // Terrain follows the visible burst; flying debris remains visual only.


            }

            // =========================================================================
            // GIAI ĐOẠN 3: AFTERMATH, SÓNG THẦN ĐẤT ĐÁ CUỘN TRẢI DÀI (Ticks 341..500)
            // =========================================================================
            else if (task.ageTicks > DETONATION_TICK) {
                // Rung chấn mặt đất cuồn cuộn kéo dài sau đại nổ (Ticks 341..368)
                if (task.ageTicks <= DETONATION_TICK + 12 && task.ageTicks % 4 == 0) {
                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.corePos, 160.0)) {
                        ServerPlayNetworking.send(p, new CinematicFxPayload(
                                CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                                task.corePos.x, task.corePos.y, task.corePos.z,
                                0.16f, 8
                        ));
                    }
                }

                // Tại Tick 355: Dọn dẹp các khối hiển thị văng sau khi hoàn tất animation siêu thanh
                if (task.ageTicks == DETONATION_TICK + 48) {
                    for (BlockPos pos : task.blastFlingPositions) {
                        SpatialDisplacementManager.removeActiveRecord(world.getRegistryKey(), pos);
                    }
                    task.blastFlingPositions.clear();
                }


                if (task.ageTicks >= MAX_LIFETIME_TICKS) {
                    it.remove();
                    continue;
                }

                // 2. SÓNG THẦN ĐẤT ĐÁ & BÃO KHÓI BỤI CUỘN TRẢI DÀI TRÊN MẶT ĐẤT (Rolling Pyroclastic Surge):
                // Lan tỏa từ tâm 4m ra 65m quét dọc theo bề mặt thực tế của địa hình

            }
        }
    }

    private static void broadcastDetonation(SupernovaTask task) {
        ImpactFxPayload novaPayload = new ImpactFxPayload(task.corePos.x, task.corePos.y, task.corePos.z,
                ImpactFxPayload.TYPE_SUPERNOVA, SupernovaSequence.BURST_TICKS, 3.5f);
        CinematicFxPayload burstCinematic = new CinematicFxPayload(CinematicFxPayload.EVENT_SUPERNOVA_BURST,
                task.corePos.x, task.corePos.y, task.corePos.z, 1.0f, 110);
        Set<ServerPlayerEntity> viewers = new HashSet<>(PlayerLookup.around(task.world, task.corePos, 250.0));
        if (task.caster != null && task.caster.isAlive() && task.caster.getServerWorld() == task.world) {
            viewers.add(task.caster);
        }
        for (ServerPlayerEntity viewer : viewers) {
            ServerPlayNetworking.send(viewer, novaPayload);
            ServerPlayNetworking.send(viewer, burstCinematic);
        }
    }

    private static void carveSphericalCraterShell(SupernovaTask task, int step, int totalSteps) {
        ServerWorld world = task.world;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        double maxR = Math.min(180.0, Math.max(120.0, task.terrainRadius));
        double maxDepth = 52.0;
        double rMin = step == 0 ? 0.0 : maxR * ((double) step / totalSteps);
        double rMax = maxR * ((double) (step + 1) / totalSteps);
        double rMinSq = rMin * rMin;
        double rMaxSq = rMax * rMax;

        int cx = task.groundCenter.getX();
        int groundY = task.groundCenter.getY();
        int cz = task.groundCenter.getZ();

        int intR = (int) Math.ceil(rMax);
        boolean removedRestorable = false;
        for (int x = -intR; x <= intR; x++) {
            for (int z = -intR; z <= intR; z++) {
                double d2Sq = x * x + z * z;
                if (d2Sq < rMinSq || d2Sq > rMaxSq) continue;
                int wx = cx + x;
                int wz = cz + z;
                if (!world.getChunkManager().isChunkLoaded(wx >> 4, wz >> 4)) continue;

                double dist = Math.sqrt(d2Sq);
                double normDist = dist / maxR;
                double depth = maxDepth * Math.sqrt(Math.max(0.0, 1.0 - normDist * normDist));
                var chunk = world.getChunk(wx >> 4, wz >> 4);
                int topY = Math.min(world.getTopY() - 1, chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, wx & 15, wz & 15));
                int surfaceY = topY;
                int floorY = Math.max(world.getBottomY() + 2, (int) Math.round(Math.min(groundY, surfaceY) - depth));

                for (int y = topY; y >= floorY; y--) {
                    pos.set(wx, y, wz);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir() || state.getHardness(world, pos) < 0) continue;

                    var displaced = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                    if (displaced != null) {
                        SpatialDisplacementManager.removeActiveRecord(world.getRegistryKey(), pos);
                        removedRestorable = true;
                    }
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(),
                            Block.FORCE_STATE | Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
                }
            }
        }
        if (removedRestorable) net.godwand.spatial.DisplacementPersistentState.getServerState(world).markDirty();
    }

    public static int getActiveTaskCount() {
        return ACTIVE_TASKS.size();
    }

    public static void clearAllTasks() {
        ACTIVE_TASKS.clear();
        CHARGE_TARGETS.clear();
    }
}
