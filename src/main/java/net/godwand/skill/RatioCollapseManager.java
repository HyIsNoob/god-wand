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
        public final double radius;
        public final double boulderRadius;
        public final int maxBlocks;
        public final int craterRadius;
        public final int craterDepth;
        public final float damageMult;
        public int ageTicks = 0;
        public final int maxLifetimeTicks = 95;
        public boolean slammed = false;
        public boolean restored = false;

        public final List<BlockPos> displacedPositions = new ArrayList<>();
        public final Map<BlockPos, Float> apexHeight = new HashMap<>();
        public final Map<BlockPos, Vec3d> phase1Displacement = new HashMap<>();
        public final Map<BlockPos, Vec3d> clusterOffset = new HashMap<>();
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
            default -> 8.5;
        };

        double boulderRadius = switch (chargeLevel) {
            case 2 -> 3.8;
            case 3 -> 5.5;
            default -> 2.4;
        };

        int maxBlocks = switch (chargeLevel) {
            case 2 -> 180;
            case 3 -> 270;
            default -> 110;
        };

        int craterRadius = switch (chargeLevel) {
            case 2 -> 14;
            case 3 -> 21;
            default -> 9;
        };

        int craterDepth = switch (chargeLevel) {
            case 2 -> 7;
            case 3 -> 11;
            default -> 4;
        };

        float damageMult = switch (chargeLevel) {
            case 2 -> 1.6f;
            case 3 -> 2.5f;
            default -> 1.0f;
        };

        launch(world, player, targetPos, radius, boulderRadius, maxBlocks, craterRadius, craterDepth, damageMult);
    }

    public static void launch(ServerWorld world, ServerPlayerEntity player, BlockPos center, double radius) {
        launch(world, player, center, radius, 2.4, 110, 9, 4, 1.0f);
    }

    public static void launch(ServerWorld world, ServerPlayerEntity player, BlockPos center,
                              double radius, double boulderRadius, int maxBlocks,
                              int craterRadius, int craterDepth, float damageMult) {
        RatioCollapseTask task = new RatioCollapseTask(world, player, center, radius, boulderRadius, maxBlocks, craterRadius, craterDepth, damageMult);

        // Âm thanh nứt gãy địa chấn ban đầu
        world.playSound(null, center, SoundEvents.BLOCK_HEAVY_CORE_BREAK, SoundCategory.BLOCKS, 3.5f, 0.75f);
        world.playSound(null, center, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.BLOCKS, 2.5f, 1.25f);
        world.playSound(null, center, SoundEvents.ITEM_MACE_SMASH_GROUND, SoundCategory.BLOCKS, 3.0f, 0.8f);

        world.spawnParticles(ParticleTypes.SONIC_BOOM, center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.FLASH, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.0);
        world.spawnParticles(new DustParticleEffect(new Vector3f(0.15f, 0.90f, 1.0f), 1.5f),
                center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 40, 1.5, 0.3, 1.5, 0.25);

        // Bóc tối đa task.maxBlocks khối đất đá trong bán kính task.radius
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

        // Chọn tối đa task.maxBlocks khối
        Collections.shuffle(validPositions, new Random(center.asLong()));
        int count = Math.min(task.maxBlocks, validPositions.size());

        for (int i = 0; i < count; i++) {
            BlockPos pos = validPositions.get(i);
            float hash = (float) SpatialMath.hashCoordinateToPhase(pos.asLong());
            float apexY = 5.5f + (float) (task.boulderRadius * 0.8) + 3.0f * (float) Math.abs(Math.sin(hash));

            // Góc xoay ngẫu nhiên
            Quaternionf rot = new Quaternionf()
                    .rotateX((float) Math.toRadians(25.0 * Math.sin(hash * 2.1)))
                    .rotateY((float) Math.toRadians(45.0 * Math.cos(hash * 1.7)))
                    .rotateZ((float) Math.toRadians(30.0 * Math.sin(hash * 3.3)));

            // Bóc tách ở chế độ CUSTOM_SCRIPTED với lifetime 105
            SpatialDisplacementManager.displaceBlock(
                    world,
                    pos,
                    new Vector3f(0, 0, 0),
                    rot,
                    new Vector3f(1.0f, 1.0f, 1.0f),
                    105,
                    0x00F0FF,
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

            task.displacedPositions.add(pos);
            task.apexHeight.put(pos, apexY);
            task.baseRotation.put(pos, rot);

            // Vector đỉnh nâng ở Pha 1
            double dx = pos.getX() - center.getX();
            double dz = pos.getZ() - center.getZ();
            Vec3d p1Disp = new Vec3d(-dx * 0.15, apexY, -dz * 0.15);
            task.phase1Displacement.put(pos, p1Disp);

            // Tọa độ Fibonacci Sphere trong tảng thiên thạch bán kính task.boulderRadius
            Vec3d clusterOff = computeBoulderClusterOffset(i, count, task.boulderRadius);
            task.clusterOffset.put(pos, clusterOff);
        }

        // Đẩy quái vật xung quanh lên trời
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

    public static void tick(ServerWorld world) {
        Iterator<RatioCollapseTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            RatioCollapseTask task = it.next();
            if (task.world != world) continue;

            task.ageTicks++;
            Vec3d meteorCore = task.center.toCenterPos().add(0, 11.0 + task.boulderRadius * 1.35, 0);

            // =========================================================================
            // PHA 1: CẠY TUNG MẶT ĐẤT CÓ SỨC NẶNG (Ticks 1..28) - NỘI SUY SINUSOIDAL MƯỢT MÀ
            // =========================================================================
            if (task.ageTicks <= 28) {
                if (task.ageTicks % 2 == 0) {
                    float t = MathHelper.clamp(task.ageTicks / 28.0f, 0.0f, 1.0f);
                    float u = 0.5f - 0.5f * (float) Math.cos(t * Math.PI); // Cosine ease-in-out S-curve êm ái
                    for (BlockPos pos : task.displacedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        Vec3d p1Disp = task.phase1Displacement.getOrDefault(pos, Vec3d.ZERO).multiply(u);
                        Quaternionf rot = task.baseRotation.getOrDefault(pos, new Quaternionf());

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(2);
                        display.setTransformation(SpatialMath.createPivotTransform(p1Disp, rot, 1.0f));
                    }

                    if (task.ageTicks % 6 == 0) {
                        world.playSound(null, task.center.up(4), SoundEvents.BLOCK_BASALT_BREAK, SoundCategory.BLOCKS, 1.8f, 0.7f);
                    }
                }
            }

            // =========================================================================
            // PHA 2: HỢP NHẤT THÀNH 1 TẢNG THIÊN THẠCH KHỔNG LỒ (Ticks 29..68)
            // =========================================================================
            else if (task.ageTicks <= 68) {
                // Ticks 29..58 (30 ticks): Ép chặt các khối vào khối cầu meteorCore với nội suy mượt mà
                if (task.ageTicks <= 58) {
                    float cp = (task.ageTicks - 28) / 30.0f;
                    float smoothCp = 0.5f - 0.5f * (float) Math.cos(cp * Math.PI);
                    float scaleVal = 1.0f + 0.28f * smoothCp; // Phóng to 1.0x -> 1.28x để các khối đan khít vào nhau

                    if ((task.ageTicks - 28) % 2 == 0) {
                        for (BlockPos pos : task.displacedPositions) {
                            DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                            if (record == null || record.displayEntityUuid == null) continue;

                            BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                            if (display == null || !display.isAlive()) continue;

                            Vec3d p1Disp = task.phase1Displacement.getOrDefault(pos, Vec3d.ZERO);
                            Vec3d clusterOff = task.clusterOffset.getOrDefault(pos, Vec3d.ZERO);
                            Vec3d finalClusterWorldPos = meteorCore.add(clusterOff);
                            Vec3d finalDisp = finalClusterWorldPos.subtract(Vec3d.ofCenter(pos));

                            Vec3d curDisp = p1Disp.lerp(finalDisp, smoothCp);
                            Quaternionf rot = task.baseRotation.getOrDefault(pos, new Quaternionf());

                            display.setStartInterpolation(0);
                            display.setInterpolationDuration(2);
                            display.setTransformation(SpatialMath.createPivotTransform(curDisp, rot, scaleVal));
                        }
                    }

                    // Hút các thực thể xung quanh lên bầu trời
                    if (task.ageTicks % 6 == 0) {
                        Box suctionBox = new Box(task.center).expand(task.radius * 1.2);
                        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, suctionBox, ent -> ent != task.caster)) {
                            Vec3d pull = meteorCore.subtract(e.getPos()).normalize().multiply(0.22);
                            e.addVelocity(pull.x, pull.y, pull.z);
                            e.velocityModified = true;
                        }
                    }
                }
                // Ticks 59..68 (10 ticks nén lực): Tảng thiên thạch khổng lồ rung lắc dữ dội tại chỗ
                else {
                    for (BlockPos pos : task.displacedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        Vec3d clusterOff = task.clusterOffset.getOrDefault(pos, Vec3d.ZERO);
                        double jitterX = (Math.random() - 0.5) * 0.28;
                        double jitterY = (Math.random() - 0.5) * 0.28;
                        double jitterZ = (Math.random() - 0.5) * 0.28;

                        Vec3d vibDisp = meteorCore.add(clusterOff).add(jitterX, jitterY, jitterZ).subtract(Vec3d.ofCenter(pos));
                        Quaternionf rot = task.baseRotation.getOrDefault(pos, new Quaternionf());

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(2);
                        display.setTransformation(SpatialMath.createPivotTransform(vibDisp, rot, 1.28f));
                    }

                    // Tia sét và chấn động âm thanh
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                            meteorCore.x, meteorCore.y, meteorCore.z, 8, 1.8, 1.8, 1.8, 0.2);
                    world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.15f, 0.15f), 1.6f),
                            meteorCore.x, meteorCore.y, meteorCore.z, 6, 1.5, 1.5, 1.5, 0.1);

                    if (task.ageTicks == 59) {
                        CinematicFxPayload focusPayload = new CinematicFxPayload(
                                CinematicFxPayload.EVENT_METEOR_FOCUS,
                                meteorCore.x, meteorCore.y, meteorCore.z,
                                0.88f, 10
                        );
                        for (ServerPlayerEntity p : PlayerLookup.around(world, meteorCore, 96.0)) {
                            ServerPlayNetworking.send(p, focusPayload);
                        }
                    }

                    if (task.ageTicks == 60) {
                        world.playSound(null, meteorCore.x, meteorCore.y, meteorCore.z,
                                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.BLOCKS, 3.5f, 0.65f);
                    }
                    if (task.ageTicks == 65) {
                        world.playSound(null, meteorCore.x, meteorCore.y, meteorCore.z,
                                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 3.5f, 1.8f);
                    }
                }
            }

            // =========================================================================
            // PHA 3: GIÁNG SẬP THIÊN THẠCH (Ticks 69..75)
            // =========================================================================
            else if (task.ageTicks <= 75) {
                // Tại Tick 69: CẢ KHỐI THIÊN THẠCH KHỔNG LỒ CẮM THẲNG XUỐNG ĐẤT NHƯ 1 KHỐI DUY NHẤT (6 ticks)
                if (task.ageTicks == 69) {
                    Vec3d groundCenterTarget = task.center.toCenterPos().add(0, 1.2, 0);

                    for (BlockPos pos : task.displacedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.getActiveRecord(world.getRegistryKey(), pos);
                        if (record == null || record.displayEntityUuid == null) continue;

                        BlockDisplayEntity display = SpatialDisplacementManager.getActiveEntity(record.displayEntityUuid);
                        if (display == null || !display.isAlive()) continue;

                        Vec3d clusterOff = task.clusterOffset.getOrDefault(pos, Vec3d.ZERO);
                        Vec3d impactWorldPos = groundCenterTarget.add(clusterOff);
                        Vec3d slamDisp = impactWorldPos.subtract(Vec3d.ofCenter(pos));
                        Quaternionf rot = task.baseRotation.getOrDefault(pos, new Quaternionf());

                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(6); // Rơi với tốc độ tên lửa trong 6 ticks
                        display.setTransformation(SpatialMath.createPivotTransform(slamDisp, rot, 1.28f));
                    }

                    world.playSound(null, task.center, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.BLOCKS, 3.0f, 0.5f);
                }

                // Tại Tick 75: Vụ va chạm mặt đất (Ground Zero Impact)
                if (task.ageTicks == 75 && !task.slammed) {
                    task.slammed = true;

                    // Âm thanh va đập tầng lớp
                    world.playSound(null, task.center, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 4.5f, 0.55f);
                    world.playSound(null, task.center, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.BLOCKS, 4.0f, 0.65f);
                    world.playSound(null, task.center, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 3.5f, 0.5f);
                    world.playSound(null, task.center, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.BLOCKS, 3.5f, 1.1f);

                    // Sóng xung kích VFX
                    world.spawnParticles(ParticleTypes.FLASH,
                            task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.0);
                    world.spawnParticles(ParticleTypes.SONIC_BOOM,
                            task.center.getX() + 0.5, task.center.getY() + 0.8, task.center.getZ() + 0.5, 3, 0.1, 0.1, 0.1, 0.0);
                    world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                            task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5, 4, 0.8, 0.8, 0.8, 0.0);

                    for (int i = 0; i < 32; i++) {
                        double angle = (i / 32.0) * (2.0 * Math.PI);
                        double r = task.radius * 0.95;
                        double px = task.center.getX() + 0.5 + Math.cos(angle) * r;
                        double pz = task.center.getZ() + 0.5 + Math.sin(angle) * r;
                        world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, task.center.getY() + 0.2, pz,
                                1, Math.cos(angle) * 0.18, 0.06, Math.sin(angle) * 0.18, 0.03);
                        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                                px, task.center.getY() + 0.2, pz, 5, 0.2, 0.2, 0.2, 0.1);
                    }

                    // Sát thương va đập và đẩy văng
                    Box slamBox = new Box(task.center).expand(task.radius * 1.4);
                    for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, slamBox, ent -> ent != task.caster)) {
                        double dist = e.getPos().distanceTo(task.center.toCenterPos());
                        float dmg = (float) Math.max(16.0, ((1.0 - (dist / (task.radius * 1.4))) * 42.0 + 10.0) * task.damageMult);
                        e.damage(world.getDamageSources().fallingBlock(task.caster), dmg);

                        Vec3d diff = e.getPos().subtract(task.center.toCenterPos()).normalize().multiply(1.6 * task.damageMult).add(0, 0.65, 0);
                        e.addVelocity(diff.x, diff.y, diff.z);
                        e.velocityModified = true;
                    }

                    // Shader Anime Impact Frame & Ground Seismic Shockwave (Ground Zero)
                    ImpactFxPayload slamPayload = new ImpactFxPayload(
                            task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5,
                            ImpactFxPayload.TYPE_SEISMIC_SLAM, 20, 2.0f * task.damageMult
                    );
                    CinematicFxPayload slamCinematic = new CinematicFxPayload(
                            CinematicFxPayload.EVENT_METEOR_SLAM,
                            task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5,
                            Math.min(1.0f, 0.95f * task.damageMult), 24
                    );
                    for (ServerPlayerEntity p : PlayerLookup.around(world, task.center.toCenterPos(), 96.0)) {
                        ServerPlayNetworking.send(p, slamPayload);
                        ServerPlayNetworking.send(p, slamCinematic);
                    }

                    // TẠO HỐ VA CHẠM THIÊN THẠCH THỰC SỰ - KHÔNG HOÀN TRẢ
                    int craterR = task.craterRadius;
                    int maxD = task.craterDepth;
                    BlockPos.Mutable cMut = new BlockPos.Mutable();
                    Random rng = new Random(task.center.asLong());

                    for (int cx = -craterR; cx <= craterR; cx++) {
                        for (int cz = -craterR; cz <= craterR; cz++) {
                            double distSq = cx * cx + cz * cz;
                            if (distSq > craterR * craterR) continue;

                            double dist2D = Math.sqrt(distSq);
                            int depth = (int) Math.round((1.0 - (dist2D / craterR)) * maxD);

                            for (int cy = -depth; cy <= 1; cy++) {
                                cMut.set(task.center.getX() + cx, task.center.getY() + cy, task.center.getZ() + cz);
                                if (cMut.getY() < world.getBottomY() || cMut.getY() >= world.getTopY()) continue;

                                BlockState bState = world.getBlockState(cMut);
                                if (bState.isAir() || bState.getBlock() == Blocks.BEDROCK) continue;

                                if (cy < -depth + 1) {
                                    BlockState floorState = (rng.nextFloat() < 0.40f) ? Blocks.MAGMA_BLOCK.getDefaultState() :
                                            (rng.nextFloat() < 0.70f) ? Blocks.COBBLESTONE.getDefaultState() :
                                                    Blocks.CRACKED_STONE_BRICKS.getDefaultState();
                                    world.setBlockState(cMut, floorState, Block.NOTIFY_ALL);
                                } else {
                                    world.setBlockState(cMut, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                                }
                            }
                        }
                    }

                    // Khối của tảng thiên thạch văng tung tóe ra xung quanh rìa hố thành FallingBlockEntity
                    int flingCount = 0;
                    int maxFling = Math.min(65, task.maxBlocks / 3);
                    for (BlockPos pos : task.displacedPositions) {
                        DisplacedBlockRecord record = SpatialDisplacementManager.removeActiveRecord(world.getRegistryKey(), pos);
                        if (record != null && flingCount < maxFling && rng.nextFloat() < 0.55f) {
                            flingCount++;
                            double fAngle = rng.nextDouble() * Math.PI * 2.0;
                            double fSpeed = 0.45 + rng.nextDouble() * 0.75;
                            double spawnX = task.center.getX() + 0.5 + Math.cos(fAngle) * 3.5;
                            double spawnY = task.center.getY() + 2.0;
                            double spawnZ = task.center.getZ() + 0.5 + Math.sin(fAngle) * 3.5;

                            FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world,
                                     BlockPos.ofFloored(spawnX, spawnY, spawnZ),
                                     record.originalState);
                            falling.setVelocity(Math.cos(fAngle) * fSpeed, 0.45 + rng.nextDouble() * 0.45, Math.sin(fAngle) * fSpeed);
                            falling.velocityModified = true;
                        }
                    }
                }
            }

            // =========================================================================
            // PHA 4: HIỆN TRƯỜNG HOANG TÀN & KẾT THÚC (Ticks 76..85)
            // =========================================================================
            else {
                // Kết thúc task tại Tick 78 sau khi khói bụi đã tan bớt
                if (task.ageTicks >= 78) {
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
