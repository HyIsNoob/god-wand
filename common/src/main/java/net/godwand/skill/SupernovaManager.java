package net.godwand.skill;

import net.godwand.platform.Services;
import net.godwand.network.CinematicFxPayload;
import net.godwand.network.ImpactFxPayload;
import net.godwand.spatial.DisplacedBlockRecord;
import net.godwand.spatial.SpatialDisplacementManager;
import net.godwand.spatial.SpatialMath;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SupernovaManager {
    public static boolean isFoliageOrNonSolid(ServerLevel world, BlockPos pos, BlockState state) {
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS)
                || state.is(BlockTags.SAPLINGS) || state.getBlock() instanceof LeavesBlock
                || !state.isCollisionShapeFullBlock(world, pos) || !state.canOcclude()) {
            return true;
        }
        return false;
    }
    static Vec3 mobPullVelocity(Vec3 current, Vec3 towardCore, double progress) {
        double distance = towardCore.length();
        double charge = Math.clamp(progress, 0, 1);
        double speed = Math.min(distance * 0.18, 0.9 + charge * 2.4);
        Vec3 desired = distance < 0.000001 ? Vec3.ZERO : towardCore.scale(speed / distance);
        Vec3 result = current.lerp(desired, 0.3 + charge * 0.2);
        double limit = Math.min(3.3, distance * 0.65);
        return result.lengthSqr() > limit * limit ? result.normalize().scale(limit) : result;
    }

    public static final int DETONATION_TICK = SupernovaSequence.SKY_RELEASE;
    public static final int IMPACT_TICK = DETONATION_TICK - ImpactFxPayload.SUPERNOVA_IMPACT_TICKS;
    public static final int TERRAIN_START_TICK = DETONATION_TICK + 4;
    public static final int MAX_LIFETIME_TICKS = DETONATION_TICK + SupernovaSequence.BURST_TICKS;
    public static final int MAX_DISPLAYS_CAP = 1800;

    public static class SupernovaTask {
        public final ServerLevel world;
        public final ServerPlayer caster;
        public final BlockPos groundCenter;
        public final Vec3 corePos;
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

        final SupernovaCraterCursor candidateColumns = new SupernovaCraterCursor((int)Math.ceil(maxRadius));

        public SupernovaTask(ServerLevel world, ServerPlayer caster, BlockPos groundCenter, Vec3 corePos) {
            this.world = world;
            this.caster = caster;
            this.groundCenter = groundCenter.immutable();
            this.corePos = corePos;
        }
    }

    private static final Queue<SupernovaTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();
    private static final Map<ServerPlayer, BlockPos> CHARGE_TARGETS = new WeakHashMap<>();

    public static BlockPos findTarget(Player player) {
        var world = player.level();
        HitResult hit = player.pick(180.0, 1.0f, false);
        BlockPos groundCenter;
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bHit) {
            groundCenter = bHit.getBlockPos();
        } else {
            Vec3 forward = player.getEyePosition().add(player.getViewVector(1.0f).scale(120.0));
            groundCenter = BlockPos.containing(forward);
            if (world.isClientSide() && !world.getChunkSource().hasChunk(groundCenter.getX() >> 4, groundCenter.getZ() >> 4)) return null;
            groundCenter = groundCenter.atY(Math.min(groundCenter.getY(), world.getMaxBuildHeight() - 1));
            while (groundCenter.getY() > world.getMinBuildHeight() && world.getBlockState(groundCenter).isAir()) {
                groundCenter = groundCenter.below();
            }
        }
        return groundCenter.immutable();
    }

    public static void beginCharge(ServerPlayer player) { CHARGE_TARGETS.put(player, findTarget(player)); }
    public static void cancelCharge(ServerPlayer player) { CHARGE_TARGETS.remove(player); }

    public static void cast(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        BlockPos groundCenter = CHARGE_TARGETS.remove(player);
        if (groundCenter == null) groundCenter = findTarget(player);

        // Hố đen lơ lửng cách mặt đất 20.0m (Quy mô Calamity)
        Vec3 corePos = groundCenter.getCenter().add(0, 20.0, 0);
        SupernovaTask task = new SupernovaTask(world, player, groundCenter, corePos);

        CinematicFxPayload castShake = new CinematicFxPayload(
                CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                corePos.x, corePos.y, corePos.z,
                0.18f, 8
        );
        Services.NETWORK.sendToNearby(world, corePos, 120.0, castShake);

        ACTIVE_TASKS.add(task);
        // Terrain preparation is spread across the buildup ticks.
        ImpactFxPayload blackHolePayload = new ImpactFxPayload(
                corePos.x, corePos.y, corePos.z,
                ImpactFxPayload.TYPE_BLACK_HOLE, IMPACT_TICK, 1.0f
        );
        Services.NETWORK.sendToNearby(world, corePos, 250.0, blackHolePayload);
        if (player.isAlive()) Services.NETWORK.sendToPlayer(player, blackHolePayload);

    }

    private static void prepareTerrain(SupernovaTask task) {
        ServerLevel world = task.world;
        int groundY = task.groundCenter.getY();
        int maxY = Math.min(world.getMaxBuildHeight() - 1, groundY + 54);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Bound preparation work and reuse the center-out cursor instead of sorting all terrain at cast time.
        for (int columns = 0; columns < 64 && !task.candidateColumns.finished(); columns++) {
            int x = task.candidateColumns.x(), z = task.candidateColumns.z();
            task.candidateColumns.advance();
            double distance = Math.hypot(x, z);
            int colX = task.groundCenter.getX() + x, colZ = task.groundCenter.getZ() + z;
            if (distance > task.maxRadius || !world.getChunkSource().hasChunk(colX >> 4, colZ >> 4)) continue;
            double bowlDepth = 15.0 * Math.pow(1.0 - distance / task.maxRadius, 1.35);
            double noise = (SpatialMath.hash01(new BlockPos(colX * 2, 0, colZ * 2)) - 0.5) * 6.5;
            noise += Math.sin(colX * 0.4) * Math.cos(colZ * 0.4) * 2.5;
            int floorY = Math.max(world.getMinBuildHeight() + 4, (int)Math.round(groundY - bowlDepth + noise));
            int surfaceY = world.getHeight(Heightmap.Types.WORLD_SURFACE, colX, colZ) - 1;
            for (int y = Math.min(maxY, surfaceY + 4); y >= floorY; y--) {
                pos.set(colX, y, colZ);
                BlockState state = world.getBlockState(pos);
                if (SpatialDisplacementManager.canDisplace(world, pos, state)) task.candidatePositions.add(pos.immutable());
            }
        }
    }

    public static void tick(ServerLevel world) {

        Iterator<SupernovaTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            SupernovaTask task = it.next();
            if (task.world != world) continue;

            task.ageTicks++;
            if (task.ageTicks < SupernovaSequence.PEEL_END) prepareTerrain(task);
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
                            SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.6f, 1.35f);
                    world.playSound(null, task.corePos.x, task.corePos.y, task.corePos.z,
                            SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.PLAYERS, 2.0f, 0.7f);
                    world.sendParticles(ParticleTypes.SONIC_BOOM,
                            task.corePos.x, task.corePos.y, task.corePos.z, 1, 0, 0, 0, 0);
                    world.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            task.corePos.x, task.corePos.y, task.corePos.z, 32, 2.5, 1.0, 2.5, 0.35);

                    Services.NETWORK.sendToNearby(world, task.corePos, 200.0, new CinematicFxPayload(
                            CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                            task.corePos.x, task.corePos.y, task.corePos.z,
                            0.22f, 8
                    ));
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
                        if (!world.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
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
                                        task.caster.getUUID(),
                                        null,
                                        0.0
                                );

                                task.displayedPositions.add(pos);
                                var lifted = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
                                var display = lifted == null ? null : SpatialDisplacementManager.getActiveEntity(lifted.displayEntityUuid);
                                if (display != null) {
                                    display.setBrightnessOverride(null);
                                    display.setGlowingTag(false);
                                    display.setGlowColorOverride(-1);
                                }
                                task.baseRotationMap.put(pos, rot);
                                task.blockScaleMap.put(pos, scale);

                                if (peeledThisTick <= 6) {
                                    world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
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
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplay display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        int peelTick = task.peelTickMap.getOrDefault(pos, task.ageTicks);
                        int ticksSincePeel = task.ageTicks - peelTick;
                        float entryProgress = Mth.clamp(ticksSincePeel / 30.0f, 0.0f, 1.0f);

                        // Quỹ đạo xoay quanh hố đen
                        Vec3 orbitOffset = SpatialMath.computeDynamicBlackHoleOrbit(
                                pos, task.corePos, currentPeelRadius, task.swirlAngle, entryProgress, 1.0);

                        // Vector tâm điểm Hố Đen (Singularity Center)
                        Vec3 singularityCenter = task.corePos.subtract(Vec3.atCenterOf(pos));

                        // Khi gần nổ (Ticks 280..340): Các khối ngừng xoay rộng, lao thẳng vào tâm hố đen!
                        Vec3 dynamicOffset = (collapse > 0.0f)
                                ? orbitOffset.lerp(singularityCenter, Math.min(1.0, collapse))
                                : orbitOffset;

                        Quaternionf rot = new Quaternionf(task.baseRotationMap.getOrDefault(pos, new Quaternionf()))
                                .rotateY(task.swirlAngle * 1.3f);

                        // Scale thu nhỏ dần về 0 khi bị nuốt chửng vào tâm điểm kỳ dị Hố Đen
                        float baseScale = task.blockScaleMap.getOrDefault(pos, 1.0f);
                        float scale = (collapse > 0.0f)
                                ? baseScale * (float) Math.max(0.0, 1.0 - collapse)
                                : baseScale;

                        display.setTransformationInterpolationDelay(0);
                        display.setTransformationInterpolationDuration(2);
                        if (collapse > 0 && scale > 0.001f) {
                            Vec3 radial = Vec3.atCenterOf(pos).add(dynamicOffset).subtract(task.corePos).normalize();
                            Quaternionf tidalRotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0),
                                    new Vector3f((float) radial.x, (float) radial.y, (float) radial.z));
                            Vector3f tidalScale = new Vector3f(scale * 0.38f, scale * (1 + collapse * 6), scale * 0.38f);
                            Vector3f pivot = new Vector3f(0.5f).mul(tidalScale).rotate(tidalRotation);
                            Vector3f translation = new Vector3f((float) dynamicOffset.x + 0.5f,
                                    (float) dynamicOffset.y + 0.5f, (float) dynamicOffset.z + 0.5f).sub(pivot);
                            display.setTransformation(new com.mojang.math.Transformation(translation, tidalRotation, tidalScale, null));
                        } else {
                            display.setTransformation(SpatialMath.createPivotTransform(dynamicOffset, rot, scale));
                        }
                    }
                }

                // Lực hút kéo thực thể: Hút TOÀN BỘ sinh vật (trừ caster), và KILL ngay lập tức khi chạm vào tâm hố đen
                AABB suctionBox = new AABB(task.groundCenter).inflate(currentPeelRadius * 1.4);
                for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, suctionBox, ent -> ent != task.caster)) {
                    double distToCoreSq = e.distanceToSqr(task.corePos);
                    if (distToCoreSq <= 12.25) { // Bán kính 3.5m quanh tâm hố đen
                        var source = world.damageSources().fellOutOfWorld();
                        if (e.getMaxHealth() <= 50.0f) {
                            // Tiêu diệt ngay quái thường và động vật dưới 50 máu
                            e.hurt(source, Float.MAX_VALUE);
                            if (e.isAlive()) {
                                e.setHealth(0.0f);
                                e.die(source);
                            }
                            world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, e.getX(), e.getY() + 0.5, e.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                            world.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.2, 0.2, 0.2, 0.1);
                            continue;
                        } else {
                            // Quái máu trâu / Boss (> 50 máu như Rồng Ender, Wither): Sát thương nghiền nát liên tục, không one-shot
                            if (task.ageTicks % 4 == 0) {
                                e.hurt(source, 15.0f);
                            }
                            world.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, e.getX(), e.getY() + 0.5, e.getZ(), 2, 0.2, 0.2, 0.2, 0.05);
                            world.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 0.5, e.getZ(), 3, 0.2, 0.2, 0.2, 0.1);
                            Vec3 swirl = task.corePos.subtract(e.position()).scale(0.25);
                            e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(swirl));
                            e.hurtMarked = true;
                            continue;
                        }
                    }

                    if (e instanceof net.minecraft.world.entity.Mob) {
                        e.setDeltaMovement(mobPullVelocity(e.getDeltaMovement(), task.corePos.subtract(e.position()), totalProgress));
                        e.hurtMarked = true;
                    } else if (task.ageTicks % 2 == 0) {
                        Vec3 pull = task.corePos.subtract(e.position()).normalize().scale(0.40 + totalProgress * 0.70);
                        e.setDeltaMovement(e.getDeltaMovement().lerp(pull, 0.35));
                        e.hurtMarked = true;
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
                Services.NETWORK.sendToNearby(world, task.corePos, 250.0, impact);
                if (task.caster != null && task.caster.isAlive() && task.caster.serverLevel() == world) Services.NETWORK.sendToPlayer(task.caster, impact);
            }
            else if (task.ageTicks == DETONATION_TICK && !task.exploded) {
                task.exploded = true;
                broadcastDetonation(task);

                // 1. Kích hoạt Shader Siêu Tân Tinh khổng lồ 98m (240 ticks / 12 giây phình to & tan biến dần dần)
                // 2. Âm thanh tận thế đa tầng

                world.sendParticles(ParticleTypes.ELECTRIC_SPARK, task.corePos.x, task.corePos.y, task.corePos.z, 28, 8.0, 8.0, 8.0, 0.45);
                world.sendParticles(ParticleTypes.LAVA, task.corePos.x, task.corePos.y, task.corePos.z, 16, 6.0, 6.0, 6.0, 0.55);
                // Mob one-shot damage within the blast radius.
                AABB blastBox = new AABB(task.groundCenter).inflate(task.blastRadius + 10.0);
                for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, blastBox, ent -> ent != task.caster)) {
                    double dist = e.position().distanceTo(task.corePos);
                    if (dist <= task.blastRadius + 8.0) {
                        float dmg = (float) Math.max(80.0, (1.0 - (dist / task.blastRadius)) * 380.0 + 80.0);
                        var source = world.damageSources().explosion(null, task.caster);
                        if (e instanceof net.minecraft.world.entity.Mob && e.getMaxHealth() <= 50.0f) {
                            e.hurt(source, Float.MAX_VALUE);
                            if (e.isAlive()) {
                                e.setHealth(0.0f);
                                e.die(source);
                            }
                        } else {
                            e.hurt(source, dmg);
                        }

                        Vec3 push = e.position().subtract(task.corePos).normalize().scale(5.8).add(0, 2.2, 0);
                        e.push(push.x, push.y, push.z);
                        e.hurtMarked = true;
                    }
                }

                // 5. HIỆU ỨNG VỤ NỔ VẬT LÝ 3D:
                // Giữ lại tối đa 650 BlockDisplayEntity và phóng văng siêu thanh ra bán kính 45m - 95m!
                Random rng = new Random(task.groundCenter.asLong());
                int flingDisplayCount = Math.min(650, task.displayedPositions.size());
                for (int i = 0; i < task.displayedPositions.size(); i++) {
                    BlockPos pos = task.displayedPositions.get(i);
                    if (i < flingDisplayCount) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
                        if (record != null && record.displayEntityUuid != null) {
                            BlockDisplay display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                            if (display != null && display.isAlive()) {
                                double fAngle = rng.nextDouble() * Math.PI * 2.0;
                                double fDist = 95.0 + rng.nextDouble() * 45.0;
                                double targetX = task.corePos.x + Math.cos(fAngle) * fDist;
                                double targetY = task.groundCenter.getY() + 2.0 + rng.nextDouble() * 16.0;
                                double targetZ = task.corePos.z + Math.sin(fAngle) * fDist;

                                Vec3 flingDisp = new Vec3(targetX, targetY, targetZ).subtract(Vec3.atCenterOf(pos));
                                Quaternionf flingRot = new Quaternionf()
                                        .rotateY((float) (rng.nextDouble() * Math.PI * 2.0))
                                        .rotateX((float) (rng.nextDouble() * Math.PI));

                                display.setTransformationInterpolationDelay(0);
                                display.setTransformationInterpolationDuration(44);
                                display.setTransformation(SpatialMath.createPivotTransform(flingDisp, flingRot, 0.9f));

                                task.blastFlingPositions.add(pos);
                                continue;
                            }
                        }
                    }
                    SpatialDisplacementManager.removeActiveRecord(world.dimension(), pos);
                }

                for (BlockPos pos : task.interiorPositions) {
                    SpatialDisplacementManager.removeActiveRecord(world.dimension(), pos);
                }

                // Terrain follows the visible burst; flying debris remains visual only.


            }

            // =========================================================================
            // GIAI ĐOẠN 3: AFTERMATH, SÓNG THẦN ĐẤT ĐÁ CUỘN TRẢI DÀI (Ticks 341..500)
            // =========================================================================
            else if (task.ageTicks > DETONATION_TICK) {
                // Rung chấn mặt đất cuồn cuộn kéo dài sau đại nổ (Ticks 341..368)
                if (task.ageTicks <= DETONATION_TICK + 12 && task.ageTicks % 4 == 0) {
                    Services.NETWORK.sendToNearby(world, task.corePos, 160.0, new CinematicFxPayload(
                            CinematicFxPayload.EVENT_TRAUMA_SHAKE,
                            task.corePos.x, task.corePos.y, task.corePos.z,
                            0.16f, 8
                    ));
                }

                // Tại Tick 355: Dọn dẹp các khối hiển thị văng sau khi hoàn tất animation siêu thanh
                if (task.ageTicks == DETONATION_TICK + 48) {
                    for (BlockPos pos : task.blastFlingPositions) {
                        SpatialDisplacementManager.removeActiveRecord(world.dimension(), pos);
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
        Services.NETWORK.sendToNearby(task.world, task.corePos, 250.0, novaPayload);
        Services.NETWORK.sendToNearby(task.world, task.corePos, 250.0, burstCinematic);
        if (task.caster != null && task.caster.isAlive() && task.caster.serverLevel() == task.world) {
            Services.NETWORK.sendToPlayer(task.caster, novaPayload);
            Services.NETWORK.sendToPlayer(task.caster, burstCinematic);
        }
    }

    private static void carveSphericalCraterShell(SupernovaTask task, int step, int totalSteps) {
        ServerLevel world = task.world;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double maxR = task.terrainRadius;
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
                if (!world.getChunkSource().hasChunk(wx >> 4, wz >> 4)) continue;

                double dist = Math.sqrt(d2Sq);
                double normDist = dist / maxR;
                double depth = maxDepth * Math.sqrt(Math.max(0.0, 1.0 - normDist * normDist));
                var chunk = world.getChunk(wx >> 4, wz >> 4);
                int topY = Math.min(world.getMaxBuildHeight() - 1, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, wx & 15, wz & 15));
                int surfaceY = topY;
                int floorY = Math.max(world.getMinBuildHeight() + 2, (int) Math.round(Math.min(groundY, surfaceY) - depth));

                for (int y = topY; y >= floorY; y--) {
                    pos.set(wx, y, wz);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir() || state.getDestroySpeed(world, pos) < 0) continue;

                    var displaced = SpatialDisplacementManager.getActiveRecord(world.dimension(), pos);
                    if (displaced != null) {
                        SpatialDisplacementManager.removeActiveRecord(world.dimension(), pos);
                        removedRestorable = true;
                    }
                    net.minecraft.world.level.block.entity.BlockEntity craterBe = world.getBlockEntity(pos);
                    if (craterBe != null) {
                        try {
                            net.minecraft.world.Clearable.tryClear(craterBe);
                        } catch (Throwable ignored) {
                        }
                        try {
                            world.removeBlockEntity(pos);
                        } catch (Throwable ignored) {
                        }
                    }
                    world.setBlock(pos, Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }
        if (removedRestorable) net.godwand.spatial.DisplacementPersistentState.getServerState(world).setDirty();
    }

    public static int getActiveTaskCount() {
        return ACTIVE_TASKS.size();
    }

    public static void clearAllTasks() {
        ACTIVE_TASKS.clear();
        CHARGE_TARGETS.clear();
    }
}
