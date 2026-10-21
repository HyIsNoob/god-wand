package net.godwand.skill;

import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
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

    private static final Queue<SlicingTask> ACTIVE_TASKS = new ConcurrentLinkedQueue<>();

    public static void queueCalamityDetonation(ServerWorld world, BlockPos center, double maxRadius) {
        ACTIVE_TASKS.add(new SlicingTask(world, center, maxRadius));
        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 2.0f, 1.2f);
        world.playSound(null, center.getX(), center.getY(), center.getZ(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 2.5f, 0.8f);
    }

    public static int getActiveTaskCount() {
        return ACTIVE_TASKS.size();
    }

    public static void clearAllTasks() {
        ACTIVE_TASKS.clear();
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
    }
}
