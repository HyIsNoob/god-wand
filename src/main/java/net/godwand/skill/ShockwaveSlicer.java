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

import java.util.Iterator;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ShockwaveSlicer {
    public static class SlicingTask {
        public final ServerWorld world;
        public final BlockPos center;
        public final double maxRadius;
        public int currentShell = 0;
        public final int totalShells = 5;
        public int sampledDisplays = 0;
        public int sampledDisplaysInner = 0;
        public int sampledDisplaysMid = 0;
        public int sampledDisplaysOuter = 0;
        public static final int MAX_DISPLAYS_PER_STRATA = 150;
        public static final int MAX_DISPLAYS = 320;
        public final int maxDisplays;

        public SlicingTask(ServerWorld world, BlockPos center, double maxRadius) {
            this.world = world;
            this.center = center.toImmutable();
            this.maxRadius = maxRadius;
            this.maxDisplays = 320;
        }
    }

    public static class CollapseTask {
        public final ServerWorld world;
        public final BlockPos center;
        public final double maxRadius;
        public int ticksRemaining;
        public UUID innerCoreUuid;
        public UUID outerCoreUuid;

        public CollapseTask(ServerWorld world, BlockPos center, double maxRadius, int delayTicks) {
            this.world = world;
            this.center = center.toImmutable();
            this.maxRadius = maxRadius;
            this.ticksRemaining = delayTicks;
        }
    }

    private static final Queue<SlicingTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();
    private static final Queue<CollapseTask> ACTIVE_COLLAPSES = new ConcurrentLinkedQueue<>();

    public static void queueCalamityDetonation(ServerWorld world, BlockPos center, double maxRadius) {
        ACTIVE_TASKS.add(new SlicingTask(world, center, maxRadius));

        CollapseTask cTask = new CollapseTask(world, center, maxRadius, 108);

        // Spawn 3D Singularity Core displays at vortex center
        double coreX = center.getX() + 0.5;
        double coreY = center.getY() + 3.2 + maxRadius * 0.35;
        double coreZ = center.getZ() + 0.5;

        // Inner Core: Crying Obsidian (Glowing Amethyst Purple)
        BlockDisplayEntity innerCore = new BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
        innerCore.addCommandTag("godwand_displaced");
        innerCore.setPosition(coreX, coreY, coreZ);
        innerCore.setBlockState(Blocks.CRYING_OBSIDIAN.getDefaultState());
        innerCore.setGlowing(true);
        innerCore.setGlowColorOverride(0x9B59B6);
        innerCore.setBrightness(new Brightness(15, 15));
        innerCore.setStartInterpolation(0);
        innerCore.setInterpolationDuration(0);
        innerCore.setTransformation(SpatialMath.createPivotTransform(Vec3d.ZERO, new Quaternionf(), 1.6f));
        world.spawnEntity(innerCore);
        cTask.innerCoreUuid = innerCore.getUuid();
        SpatialDisplacementManager.trackEntity(innerCore.getUuid(), innerCore);

        // Outer Core: Cyan Stained Glass (Glowing Cyan)
        BlockDisplayEntity outerCore = new BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
        outerCore.addCommandTag("godwand_displaced");
        outerCore.setPosition(coreX, coreY, coreZ);
        outerCore.setBlockState(Blocks.CYAN_STAINED_GLASS.getDefaultState());
        outerCore.setGlowing(true);
        outerCore.setGlowColorOverride(0x00F0FF);
        outerCore.setBrightness(new Brightness(15, 15));
        outerCore.setStartInterpolation(0);
        outerCore.setInterpolationDuration(0);
        outerCore.setTransformation(SpatialMath.createPivotTransform(Vec3d.ZERO, new Quaternionf(), 2.3f));
        world.spawnEntity(outerCore);
        cTask.outerCoreUuid = outerCore.getUuid();
        SpatialDisplacementManager.trackEntity(outerCore.getUuid(), outerCore);

        ACTIVE_COLLAPSES.add(cTask);

        // Cataclysmic Detonation Blast VFX
        world.spawnParticles(ParticleTypes.FLASH, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.0);
        world.spawnParticles(ParticleTypes.SONIC_BOOM, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0.0);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 3, 1.0, 1.0, 1.0, 0.0);
        world.spawnParticles(new DustParticleEffect(new Vector3f(0.0f, 0.95f, 1.0f), 1.8f),
                center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 60, 1.5, 1.5, 1.5, 0.45);

        // Cataclysmic Detonation SFX
        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 3.5f, 0.6f);
        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 3.0f, 0.8f);
        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.PLAYERS, 2.8f, 0.7f);
        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 3.0f, 0.5f);

        // Kinetic Blast Wave
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

        // Trigger Client Post-Processing Shader FX
        ImpactFxPayload detPayload = new ImpactFxPayload(
                center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                ImpactFxPayload.TYPE_DETONATION, 15, (float) Math.max(1.0, maxRadius / 10.0)
        );
        for (ServerPlayerEntity p : PlayerLookup.around(world, center.toCenterPos(), 96.0)) {
            ServerPlayNetworking.send(p, detPayload);
        }
    }

    public static int getActiveTaskCount() {
        return ACTIVE_TASKS.size() + ACTIVE_COLLAPSES.size();
    }

    public static void clearAllTasks() {
        ACTIVE_TASKS.clear();
        for (CollapseTask cTask : ACTIVE_COLLAPSES) {
            BlockDisplayEntity inner = SpatialDisplacementManager.getActiveEntity(cTask.innerCoreUuid);
            if (inner != null && inner.isAlive()) {
                inner.discard();
                SpatialDisplacementManager.untrackEntity(cTask.innerCoreUuid);
            }
            BlockDisplayEntity outer = SpatialDisplacementManager.getActiveEntity(cTask.outerCoreUuid);
            if (outer != null && outer.isAlive()) {
                outer.discard();
                SpatialDisplacementManager.untrackEntity(cTask.outerCoreUuid);
            }
        }
        ACTIVE_COLLAPSES.clear();
    }

    public static void tick(ServerWorld world) {
        Iterator<SlicingTask> it = ACTIVE_TASKS.iterator();
        while (it.hasNext()) {
            SlicingTask task = it.next();
            if (task.world != world) continue;

            double shellStep = task.maxRadius / task.totalShells;
            double rMin = task.currentShell * shellStep;
            double rMax = (task.currentShell + 1) * shellStep;
            double rMinSq = rMin * rMin;
            double rMaxSq = rMax * rMax;

            int intR = (int) Math.ceil(rMax);
            BlockPos.Mutable mutable = new BlockPos.Mutable();
            Vec3d blastCenter = task.center.toCenterPos();

            for (int x = -intR; x <= intR; x++) {
                for (int y = -intR; y <= intR; y++) {
                    int targetY = task.center.getY() + y;
                    if (targetY < world.getBottomY() || targetY >= world.getTopY()) continue;

                    for (int z = -intR; z <= intR; z++) {
                        double dSq = x * x + y * y + z * z;
                        boolean inShell = dSq >= rMinSq && (task.currentShell == task.totalShells - 1 ? dSq <= rMaxSq : dSq < rMaxSq);
                        if (!inShell) continue;

                        mutable.set(task.center.getX() + x, targetY, task.center.getZ() + z);
                        BlockState state = world.getBlockState(mutable);
                        if (!SpatialDisplacementManager.canDisplace(world, mutable, state)) continue;

                        double dist = Math.sqrt(dSq);

                        // Quota decision
                        boolean spawnDisplay = false;
                        float hashVal = (float) SpatialMath.hash01(mutable);
                        float scale = 0.45f + hashVal * 0.48f; // Đa dạng kích thước từ mảnh nhỏ 0.45x đến khối 0.93x

                        if (task.sampledDisplays < 320) {
                            boolean isOuterShell = dist >= task.maxRadius * 0.78;
                            int sampleChance = task.maxRadius <= 6.0 ? 45 : (isOuterShell ? 32 : 12);
                            if (((Math.abs(x * 73 + y * 37 + z * 19)) % 100) < sampleChance) {
                                spawnDisplay = true;
                                task.sampledDisplays++;
                            }
                        }

                        int glowColor = (hashVal > 0.5f) ? 0x00F0FF : 0x9B59B6;

                        // Compute Upward Canopy Vortex offset using SpatialMath
                        Vec3d canopyOffset = SpatialMath.computeCanopyVortexOffset(mutable, blastCenter, task.maxRadius, 0);
                        Vector3f targetDisp = new Vector3f((float) canopyOffset.x, (float) canopyOffset.y, (float) canopyOffset.z);

                        double hashPhase = SpatialMath.hash01(mutable) * Math.PI * 2.0;
                        Quaternionf rot = new Quaternionf()
                                .rotateY((float) hashPhase)
                                .rotateX((float) Math.toRadians(35.0 + 30.0 * Math.sin(hashPhase)));

                        // Concentric restoration wave from bottom of crater up to crater rim
                        double depthFraction = Math.max(0.0, Math.min(1.0, (targetY - (task.center.getY() - task.maxRadius)) / (task.maxRadius * 2.0)));
                        int lifetime = 100 + (int) (depthFraction * 12);

                        if (spawnDisplay) {
                            SpatialDisplacementManager.displaceBlock(
                                    world,
                                    mutable.toImmutable(),
                                    targetDisp,
                                    rot,
                                    new Vector3f(scale, scale, scale),
                                    lifetime,
                                    glowColor,
                                    true,
                                    DisplacedBlockRecord.DisplacementMode.CANOPY_VORTEX,
                                    null,
                                    blastCenter,
                                    task.maxRadius
                            );
                        } else {
                            // Đồng bộ chính xác với lifetime theo độ sâu để đáy hố đầy lên trước, miệng hố đầy sau cùng
                            SpatialDisplacementManager.displaceBlock(
                                    world,
                                    mutable.toImmutable(),
                                    new Vector3f(0, 0, 0),
                                    new Quaternionf(),
                                    new Vector3f(1.0f, 1.0f, 1.0f),
                                    lifetime,
                                    0,
                                    false,
                                    DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE,
                                    null,
                                    null,
                                    0.0
                            );
                        }
                    }
                }
            }

            // Shockwave ring particles
            world.spawnParticles(ParticleTypes.FLASH, task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5,
                    5, rMax * 0.5, 0.5, rMax * 0.5, 0.0);

            task.currentShell++;
            if (task.currentShell >= task.totalShells) {
                it.remove();
            }
        }

        // Process Grand Implosion Slam
        Iterator<CollapseTask> colIt = ACTIVE_COLLAPSES.iterator();
        while (colIt.hasNext()) {
            CollapseTask cTask = colIt.next();
            if (cTask.world != world) continue;

            cTask.ticksRemaining--;

            BlockDisplayEntity innerCore = SpatialDisplacementManager.getActiveEntity(cTask.innerCoreUuid);
            BlockDisplayEntity outerCore = SpatialDisplacementManager.getActiveEntity(cTask.outerCoreUuid);

            // Quả Cầu Lõi Lỗ Đen (Singularity Core) ở chính giữa miệng hố kèm tia năng lượng hút vào tâm
            if (cTask.ticksRemaining > 14) {
                if (cTask.ticksRemaining % 3 == 0) {
                    double cx = cTask.center.getX() + 0.5;
                    double cy = cTask.center.getY() + 3.2 + cTask.maxRadius * 0.35;
                    double cz = cTask.center.getZ() + 0.5;
                    world.spawnParticles(ParticleTypes.REVERSE_PORTAL, cx, cy, cz, 12, 0.6, 0.6, 0.6, 0.15);
                    world.spawnParticles(ParticleTypes.END_ROD, cx, cy, cz, 4, cTask.maxRadius * 0.5, 1.5, cTask.maxRadius * 0.5, 0.02);
                    world.spawnParticles(new DustParticleEffect(new Vector3f(0.58f, 0.20f, 0.92f), 1.6f),
                            cx, cy, cz, 8, 1.2, 1.2, 1.2, 0.0);
                }

                // Xoay 3D lõi lỗ đen ngược chiều nhau mỗi 2 ticks
                if (cTask.ticksRemaining % 2 == 0) {
                    float spin = (108 - cTask.ticksRemaining) * 0.14f;
                    Quaternionf innerRot = new Quaternionf().rotateY(spin * 1.5f).rotateX(spin * 0.9f);
                    Quaternionf outerRot = new Quaternionf().rotateY(-spin * 1.2f).rotateZ(-spin * 0.8f);

                    if (innerCore != null && innerCore.isAlive()) {
                        innerCore.setStartInterpolation(0);
                        innerCore.setInterpolationDuration(2);
                        innerCore.setTransformation(SpatialMath.createPivotTransform(Vec3d.ZERO, innerRot, 1.6f));
                    }
                    if (outerCore != null && outerCore.isAlive()) {
                        outerCore.setStartInterpolation(0);
                        outerCore.setInterpolationDuration(2);
                        outerCore.setTransformation(SpatialMath.createPivotTransform(Vec3d.ZERO, outerRot, 2.3f));
                    }
                }
            } else if (cTask.ticksRemaining == 14) {
                // Thu nhỏ lõi lỗ đen về 0.01f trong 12 ticks trước khi phát nổ
                float spin = (108 - cTask.ticksRemaining) * 0.14f;
                Quaternionf innerRot = new Quaternionf().rotateY(spin * 1.5f).rotateX(spin * 0.9f);
                Quaternionf outerRot = new Quaternionf().rotateY(-spin * 1.2f).rotateZ(-spin * 0.8f);

                if (innerCore != null && innerCore.isAlive()) {
                    innerCore.setStartInterpolation(0);
                    innerCore.setInterpolationDuration(12);
                    innerCore.setTransformation(SpatialMath.createPivotTransform(Vec3d.ZERO, innerRot, 0.01f));
                }
                if (outerCore != null && outerCore.isAlive()) {
                    outerCore.setStartInterpolation(0);
                    outerCore.setInterpolationDuration(12);
                    outerCore.setTransformation(SpatialMath.createPivotTransform(Vec3d.ZERO, outerRot, 0.01f));
                }
            }

            // Gravitational Inward Implosion Vortex (Last 14 ticks)
            if (cTask.ticksRemaining <= 14 && cTask.ticksRemaining > 0) {
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                        cTask.center.getX() + 0.5, cTask.center.getY() + 0.5, cTask.center.getZ() + 0.5,
                        25, cTask.maxRadius * 0.4, cTask.maxRadius * 0.4, cTask.maxRadius * 0.4, 0.4);
                world.spawnParticles(new DustParticleEffect(new Vector3f(0.85f, 0.45f, 1.0f), 0.7f),
                        cTask.center.getX() + 0.5, cTask.center.getY() + 0.5, cTask.center.getZ() + 0.5,
                        15, cTask.maxRadius * 0.3, cTask.maxRadius * 0.3, cTask.maxRadius * 0.3, 0.25);

                if (cTask.ticksRemaining == 10) {
                    world.playSound(null, cTask.center, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 2.5f, 1.4f);
                }
            } else if (cTask.ticksRemaining == 0) {
                // Xóa 2 thực thể lõi
                if (innerCore != null && innerCore.isAlive()) {
                    innerCore.discard();
                    SpatialDisplacementManager.untrackEntity(cTask.innerCoreUuid);
                }
                if (outerCore != null && outerCore.isAlive()) {
                    outerCore.discard();
                    SpatialDisplacementManager.untrackEntity(cTask.outerCoreUuid);
                }

                // Grand Epicenter Implosion Slam
                world.spawnParticles(ParticleTypes.FLASH,
                        cTask.center.getX() + 0.5, cTask.center.getY() + 0.5, cTask.center.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.0);
                world.spawnParticles(ParticleTypes.SONIC_BOOM,
                        cTask.center.getX() + 0.5, cTask.center.getY() + 0.5, cTask.center.getZ() + 0.5, 2, 0, 0, 0, 0);
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                        cTask.center.getX() + 0.5, cTask.center.getY() + 0.5, cTask.center.getZ() + 0.5, 4, 1.0, 1.0, 1.0, 0.0);

                world.playSound(null, cTask.center, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.BLOCKS, 3.5f, 0.75f);
                world.playSound(null, cTask.center, SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.BLOCKS, 3.0f, 0.6f);
                world.playSound(null, cTask.center, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 2.5f, 0.8f);

                // Implosion Post-FX
                ImpactFxPayload impPayload = new ImpactFxPayload(
                        cTask.center.getX() + 0.5, cTask.center.getY() + 0.5, cTask.center.getZ() + 0.5,
                        ImpactFxPayload.TYPE_IMPLOSION, 12, (float) Math.max(1.0, cTask.maxRadius / 10.0)
                );
                for (ServerPlayerEntity p : PlayerLookup.around(world, cTask.center.toCenterPos(), 96.0)) {
                    ServerPlayNetworking.send(p, impPayload);
                }

                colIt.remove();
            }
        }
    }
}
