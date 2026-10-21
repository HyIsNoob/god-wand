package net.godwand.skill;

import net.godwand.spatial.SpatialDisplacementManager;
import net.godwand.spatial.SpatialMath;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpatialPathWalker {
    private static final Set<UUID> ACTIVE_PLAYERS = ConcurrentHashMap.newKeySet();

    public static boolean isActive(PlayerEntity player) {
        return ACTIVE_PLAYERS.contains(player.getUuid());
    }

    public static void toggle(ServerPlayerEntity player) {
        if (ACTIVE_PLAYERS.contains(player.getUuid())) {
            ACTIVE_PLAYERS.remove(player.getUuid());
            player.sendMessage(Text.literal("§7[✦ SPATIAL PATH: §cOFF§7]"), true);
        } else {
            ACTIVE_PLAYERS.add(player.getUuid());
            player.sendMessage(Text.literal("§d§l[✦ SPATIAL PATH: §aACTIVE §d§l✦]"), true);
        }
    }

    public static void deactivate(PlayerEntity player) {
        ACTIVE_PLAYERS.remove(player.getUuid());
    }

    public static void tickPlayer(ServerPlayerEntity player) {
        if (!isActive(player)) return;

        ServerWorld world = player.getServerWorld();
        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = player.getRotationVec(1.0f);

        // Horizontal forward vector when on ground
        Vec3d forwardDir = new Vec3d(lookVec.x, 0, lookVec.z);
        if (forwardDir.lengthSquared() < 0.001) {
            forwardDir = new Vec3d(1, 0, 0);
        } else {
            forwardDir = forwardDir.normalize();
        }

        boolean allowDown = player.getAbilities().flying && player.getPitch() > 45.0f;
        int floorY = player.getBlockPos().getY();
        double reach = 6.5;
        double radius = 2.8;

        int minX = MathHelper.floor(player.getX() - radius - reach);
        int maxX = MathHelper.floor(player.getX() + radius + reach);
        int minY = allowDown ? MathHelper.floor(player.getY() - radius - reach) : floorY;
        int maxY = MathHelper.floor(player.getY() + 3.5);
        int minZ = MathHelper.floor(player.getZ() - radius - reach);
        int maxZ = MathHelper.floor(player.getZ() + radius + reach);

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mutablePos.set(x, y, z);

                    // Global Floor Protection: y < player.getBlockPos().getY() is strictly skipped across the reach
                    if (!allowDown && y < floorY) continue;

                    Vec3d blockCenter = new Vec3d(x + 0.5, y + 0.5, z + 0.5);
                    Vec3d toBlock = blockCenter.subtract(eyePos);

                    // Project onto forward direction
                    double forwardDist = toBlock.dotProduct(forwardDir);
                    if (forwardDist < -0.5 || forwardDist > reach) continue;

                    // Perpendicular vector
                    Vec3d proj = forwardDir.multiply(forwardDist);
                    Vec3d perp = toBlock.subtract(proj);
                    double lateralDist = Math.sqrt(perp.x * perp.x + perp.z * perp.z);

                    if (lateralDist <= radius) {
                        // Refresh lifetime of already displaced blocks to prevent strobing & suffocation
                        if (SpatialDisplacementManager.isPositionDisplaced(world.getRegistryKey(), mutablePos)) {
                            SpatialDisplacementManager.refreshDisplacement(world.getRegistryKey(), mutablePos, 20);
                            continue;
                        }

                        BlockState state = world.getBlockState(mutablePos);
                        if (!SpatialDisplacementManager.canDisplace(world, mutablePos, state)) continue;

                        // Calculate push direction
                        Vec3d pushDir;
                        if (lateralDist < 0.1) {
                            // Coordinate hash split to avoid center lock
                            boolean goRight = ((x + z) & 1) == 0;
                            pushDir = new Vec3d(-forwardDir.z, 0, forwardDir.x).multiply(goRight ? 1 : -1);
                        } else {
                            pushDir = new Vec3d(perp.x, 0, perp.z).normalize();
                        }

                        double pushDist = radius - lateralDist + 1.2;
                        double phase = SpatialMath.hashCoordinateToPhase(mutablePos.asLong());
                        double lift = 0.45 + 0.35 * Math.sin(phase);

                        Vector3f targetDisp = new Vector3f(
                                (float) (pushDir.x * pushDist),
                                (float) lift,
                                (float) (pushDir.z * pushDist)
                        );

                        // Subtle tilt rotation
                        Quaternionf tilt = new Quaternionf().rotateY((float) Math.toRadians(10 * Math.sin(phase)))
                                .rotateZ((float) Math.toRadians(8 * Math.cos(phase)));

                        SpatialDisplacementManager.displaceBlock(world, mutablePos.toImmutable(), targetDisp, tilt, 20, 0x9B59B6);

                        // Subtle ambient particles near parted blocks
                        world.spawnParticles(ParticleTypes.PORTAL, blockCenter.x, blockCenter.y, blockCenter.z, 2, 0.15, 0.15, 0.15, 0.05);
                        world.spawnParticles(ParticleTypes.GLOW, blockCenter.x, blockCenter.y, blockCenter.z, 1, 0.1, 0.1, 0.1, 0.01);
                    }
                }
            }
        }
    }
}
