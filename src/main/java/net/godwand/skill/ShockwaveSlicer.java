package net.godwand.skill;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.network.ImpactFxPayload;
import net.godwand.spatial.DisplacedBlockRecord;
import net.godwand.spatial.SpatialDisplacementManager;
import net.godwand.spatial.SpatialMath;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
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
        public static final int MAX_DISPLAYS = 2000;
        public final int maxDisplays;

        public SlicingTask(ServerWorld world, BlockPos center, double maxRadius) {
            this.world = world;
            this.center = center.toImmutable();
            this.maxRadius = maxRadius;
            // R <= 10: 100% displays; R > 10: up to 2,000 displays as per spec
            this.maxDisplays = maxRadius <= 10.0 ? Integer.MAX_VALUE : 2000;
        }
    }

    public static class CollapseTask {
        public final ServerWorld world;
        public final BlockPos center;
        public final double maxRadius;
        public int ticksRemaining;

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
        ACTIVE_COLLAPSES.add(new CollapseTask(world, center, maxRadius, 108));

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
                        float scale = 1.0f;

                        if (task.maxRadius <= 10.0) {
                            // Cấp 1 & 2: 100% số block bị bóc tách thành display
                            spawnDisplay = true;
                            scale = 1.0f;
                            task.sampledDisplays++;
                        } else if (task.sampledDisplays < task.maxDisplays) {
                            // Cấp 3 (R = 16): 100% bề mặt / vách hố (dist >= 0.65R)
                            if (dist >= task.maxRadius * 0.65) {
                                spawnDisplay = true;
                                scale = 1.0f;
                                task.sampledDisplays++;
                            } else {
                                // 50% sample rate cho phần ruột với scale = 1.35f
                                if (((Math.abs(x * 31 + y * 17 + z)) % 100) < 50) {
                                    spawnDisplay = true;
                                    scale = 1.35f;
                                    task.sampledDisplays++;
                                }
                            }
                        }

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
                                    0x00F0FF,
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
