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
        public static final int MAX_DISPLAYS = 1400;
        public final int maxDisplays;

        public SlicingTask(ServerWorld world, BlockPos center, double maxRadius) {
            this.world = world;
            this.center = center.toImmutable();
            this.maxRadius = maxRadius;
            // R <= 8: 100% display entities; R > 8: up to 1,400 entities
            this.maxDisplays = maxRadius <= 8.0 ? Integer.MAX_VALUE : 1400;
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

            // Reverse expiration: perimeter shells expire slightly earlier than inner shells
            int lifetime = 100 + (task.totalShells - 1 - task.currentShell) * 2;
            Vec3d blastCenter = task.center.toCenterPos();
            Vector3f vortexCenter = new Vector3f(task.center.getX() + 0.5f, (float) (blastCenter.y + 4.5), task.center.getZ() + 0.5f);

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

                        if (task.maxRadius <= 8.0) {
                            // 100% of blocks become displays
                            spawnDisplay = true;
                            scale = 1.0f;
                            task.sampledDisplays++;
                        } else if (task.sampledDisplays < task.maxDisplays) {
                            if (dist >= task.maxRadius * 0.70) {
                                // Outer crust / surface: 60% sample rate
                                if (((x ^ y ^ z) & 1) == 0) {
                                    spawnDisplay = true;
                                    scale = 1.0f;
                                    task.sampledDisplays++;
                                }
                            } else {
                                // 40% interior: scale = 1.35f to fill volume densely
                                if (((x ^ y ^ z) % 5) < 2) {
                                    spawnDisplay = true;
                                    scale = 1.35f;
                                    task.sampledDisplays++;
                                }
                            }
                        }

                        // Upward Canopy Lift: liftY = (blastCenter.y - pos.getY()) + 3.5 + (hash01 * 5.5)
                        double phase01 = (SpatialMath.hashCoordinateToPhase(mutable.asLong()) / (2.0 * Math.PI));
                        double liftY = (blastCenter.y - targetY) + 3.5 + (phase01 * 5.5);

                        double horizDist = Math.sqrt(x * x + z * z);
                        double horizDirX = horizDist > 0.05 ? (x / horizDist) : 1.0;
                        double horizDirZ = horizDist > 0.05 ? (z / horizDist) : 0.0;

                        // Canopy expands outward horizontally into an asteroid ring above the crater
                        double horizPush = Math.max(horizDist, task.maxRadius * 0.75) + 1.2 + (phase01 * 2.2);
                        double targetOffsetX = (task.center.getX() + 0.5 + horizDirX * horizPush) - (mutable.getX() + 0.5);
                        double targetOffsetZ = (task.center.getZ() + 0.5 + horizDirZ * horizPush) - (mutable.getZ() + 0.5);

                        Vector3f targetDisp = new Vector3f(
                                (float) targetOffsetX,
                                (float) liftY,
                                (float) targetOffsetZ
                        );

                        float rotAngle = (float) Math.toRadians(35.0 + 35.0 * phase01);
                        Quaternionf rot = new Quaternionf()
                                .rotateY((float) (phase01 * 2 * Math.PI))
                                .rotateX(rotAngle);

                        if (spawnDisplay) {
                            SpatialDisplacementManager.displaceBlock(world, mutable.toImmutable(), targetDisp, rot,
                                    new Vector3f(scale, scale, scale), lifetime, 0x00F0FF, true,
                                    DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE, null, vortexCenter);
                        } else {
                            // Non-display interior blocks hollowed out cleanly to AIR
                            SpatialDisplacementManager.displaceBlock(world, mutable.toImmutable(), new Vector3f(0, 0, 0),
                                    new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f), lifetime, 0, false,
                                    DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE, null, null);
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
