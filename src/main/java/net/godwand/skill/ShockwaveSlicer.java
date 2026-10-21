package net.godwand.skill;

import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
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
        public static final int MAX_DISPLAYS = 450;

        public SlicingTask(ServerWorld world, BlockPos center, double maxRadius) {
            this.world = world;
            this.center = center.toImmutable();
            this.maxRadius = maxRadius;
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

        // Violent Kinetic Blast Wave: Hurl nearby mobs away
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

            // Slices collapse in reverse (perimeter to center): outer shells expire slightly earlier than inner shells
            int lifetime = 100 + (task.totalShells - 1 - task.currentShell) * 2;

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
                        Vec3d dir = dist < 0.1 ? new Vec3d(0, 1, 0) : new Vec3d(x / dist, y / dist, z / dist);

                        // Stratified Display Quota: Inner, Mid, and Outer strata (each capped at 150)
                        boolean spawnDisplay = false;
                        float scale = 1.0f;
                        double targetPush = (task.maxRadius - dist) + 2.0;

                        if (((x ^ y ^ z) & 7) == 0) {
                            if (dist < task.maxRadius * 0.4) {
                                if (task.sampledDisplaysInner < SlicingTask.MAX_DISPLAYS_PER_STRATA) {
                                    spawnDisplay = true;
                                    task.sampledDisplaysInner++;
                                    task.sampledDisplays++;
                                    scale = 1.35f;
                                    targetPush += 3.0;
                                }
                            } else if (dist < task.maxRadius * 0.7) {
                                if (task.sampledDisplaysMid < SlicingTask.MAX_DISPLAYS_PER_STRATA) {
                                    spawnDisplay = true;
                                    task.sampledDisplaysMid++;
                                    task.sampledDisplays++;
                                    scale = 1.15f;
                                    targetPush += 1.5;
                                }
                            } else {
                                if (task.sampledDisplaysOuter < SlicingTask.MAX_DISPLAYS_PER_STRATA) {
                                    spawnDisplay = true;
                                    task.sampledDisplaysOuter++;
                                    task.sampledDisplays++;
                                    scale = 1.0f;
                                }
                            }
                        }

                        Vector3f targetDisp = new Vector3f(
                                (float) (dir.x * targetPush),
                                (float) (dir.y * targetPush + 0.8),
                                (float) (dir.z * targetPush)
                        );

                        float rotAngle = dist < task.maxRadius * 0.4 ? (float) Math.toRadians(55) : (float) Math.toRadians(25);
                        Quaternionf rot = new Quaternionf().rotateAxis(rotAngle, (float) dir.x, (float) dir.y, (float) dir.z);

                        if (spawnDisplay) {
                            SpatialDisplacementManager.displaceBlock(world, mutable.toImmutable(), targetDisp, rot,
                                    new Vector3f(scale, scale, scale), lifetime, 0x00F0FF, true);
                        } else {
                            // Interior blocks hollowed out to AIR without display entity
                            SpatialDisplacementManager.displaceBlock(world, mutable.toImmutable(), new Vector3f(0, 0, 0),
                                    new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f), lifetime, 0, false);
                        }
                    }
                }
            }

            // Spawn shockwave ring particles
            world.spawnParticles(ParticleTypes.FLASH, task.center.getX() + 0.5, task.center.getY() + 0.5, task.center.getZ() + 0.5,
                    5, rMax * 0.5, 0.5, rMax * 0.5, 0.0);

            task.currentShell++;
            if (task.currentShell >= task.totalShells) {
                it.remove();
            }
        }

        // Process Inward Calamity Collapse & Grand Implosion Slam
        Iterator<CollapseTask> colIt = ACTIVE_COLLAPSES.iterator();
        while (colIt.hasNext()) {
            CollapseTask cTask = colIt.next();
            if (cTask.world != world) continue;

            cTask.ticksRemaining--;

            // Gravitational Inward Implosion Vortex (Last 14 ticks before final slam)
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
                // THE GRAND EPICENTER IMPLOSION SLAM
                world.spawnParticles(ParticleTypes.FLASH,
                        cTask.center.getX() + 0.5, cTask.center.getY() + 0.5, cTask.center.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.0);
                world.spawnParticles(ParticleTypes.SONIC_BOOM,
                        cTask.center.getX() + 0.5, cTask.center.getY() + 0.5, cTask.center.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.0);
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                        cTask.center.getX() + 0.5, cTask.center.getY() + 0.5, cTask.center.getZ() + 0.5, 2, 0.5, 0.5, 0.5, 0.0);

                // Expanding radial ground dust ring
                for (int i = 0; i < 32; i++) {
                    double angle = (i / 32.0) * (2.0 * Math.PI);
                    double r = cTask.maxRadius * 0.8;
                    double px = cTask.center.getX() + 0.5 + Math.cos(angle) * r;
                    double pz = cTask.center.getZ() + 0.5 + Math.sin(angle) * r;
                    world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, cTask.center.getY() + 0.2, pz,
                            1, Math.cos(angle) * 0.12, 0.04, Math.sin(angle) * 0.12, 0.02);
                    world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                            px, cTask.center.getY() + 0.2, pz, 3, 0.2, 0.1, 0.2, 0.06);
                }

                // Cataclysmic Implosion Audio Layering
                world.playSound(null, cTask.center.getX(), cTask.center.getY(), cTask.center.getZ(),
                        SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 4.0f, 0.5f);
                world.playSound(null, cTask.center.getX(), cTask.center.getY(), cTask.center.getZ(),
                        SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 3.0f, 0.55f);
                world.playSound(null, cTask.center.getX(), cTask.center.getY(), cTask.center.getZ(),
                        SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.BLOCKS, 3.5f, 0.65f);
                world.playSound(null, cTask.center.getX(), cTask.center.getY(), cTask.center.getZ(),
                        SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.BLOCKS, 3.0f, 1.35f);

                // Ground-shaking tremor kick on nearby entities
                Box tremorBox = new Box(cTask.center).expand(cTask.maxRadius * 1.3);
                for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, tremorBox, ent -> true)) {
                    e.addVelocity(0, 0.52, 0);
                    e.velocityModified = true;
                }

                colIt.remove();
            }
        }
    }
}
