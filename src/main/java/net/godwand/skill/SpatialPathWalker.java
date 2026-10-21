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
    private static final java.util.Map<UUID, Double> LAST_GROUND_Y = new ConcurrentHashMap<>();

    public static boolean isActive(PlayerEntity player) {
        return ACTIVE_PLAYERS.contains(player.getUuid());
    }

    public static void toggle(ServerPlayerEntity player) {
        if (ACTIVE_PLAYERS.contains(player.getUuid())) {
            ACTIVE_PLAYERS.remove(player.getUuid());
            LAST_GROUND_Y.remove(player.getUuid());
            player.sendMessage(Text.literal("§7[✦ SPATIAL PATH: §cOFF§7]"), true);
        } else {
            ACTIVE_PLAYERS.add(player.getUuid());
            player.sendMessage(Text.literal("§d§l[✦ SPATIAL PATH: §aACTIVE §d§l✦]"), true);
        }
    }

    public static void deactivate(PlayerEntity player) {
        ACTIVE_PLAYERS.remove(player.getUuid());
        LAST_GROUND_Y.remove(player.getUuid());
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

        if (player.isOnGround()) {
            LAST_GROUND_Y.put(player.getUuid(), player.getY());
        }
        double baseGroundY = player.isOnGround() ? player.getY() : LAST_GROUND_Y.getOrDefault(player.getUuid(), player.getY());
        int floorY = (int) Math.floor(baseGroundY - 0.05);

        double reach = 7.0;
        double radius = 3.2;

        int minX = MathHelper.floor(player.getX() - radius - reach);
        int maxX = MathHelper.floor(player.getX() + radius + reach);
        int minY = allowDown ? MathHelper.floor(player.getY() - radius - reach) : floorY;
        int maxY = MathHelper.floor(player.getY() + 3.8);
        int minZ = MathHelper.floor(player.getZ() - radius - reach);
        int maxZ = MathHelper.floor(player.getZ() + radius + reach);

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mutablePos.set(x, y, z);

                    // Global Floor Protection: y <= floorY is strictly preserved across the reach
                    if (!allowDown && y <= floorY) continue;

                    // Footing Protection: never displace anything directly under or around player's feet
                    double pX = player.getX();
                    double pZ = player.getZ();
                    if (Math.abs(x + 0.5 - pX) <= 1.2 && Math.abs(z + 0.5 - pZ) <= 1.2 && y <= Math.ceil(player.getY() + 0.01)) continue;

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
                            SpatialDisplacementManager.refreshDisplacement(world.getRegistryKey(), mutablePos, 30);
                            continue;
                        }

                        BlockState state = world.getBlockState(mutablePos);
                        if (!SpatialDisplacementManager.canDisplace(world, mutablePos, state)) continue;

                        // Calculate push direction
                        Vec3d pushDir;
                        if (lateralDist < 0.15) {
                            // Coordinate hash split to avoid center lock
                            boolean goRight = ((x + z) & 1) == 0;
                            pushDir = new Vec3d(-forwardDir.z, 0, forwardDir.x).multiply(goRight ? 1 : -1);
                        } else {
                            pushDir = new Vec3d(perp.x, 0, perp.z).normalize();
                        }

                        double phase = SpatialMath.hashCoordinateToPhase(mutablePos.asLong());

                        // Push distance: wide clearance so the blocks part far away from the player
                        double pushDist = Math.max(3.8, (radius - lateralDist) + 2.8 + 0.6 * Math.sin(phase));

                        // Zero-gravity lift: blocks float upwards into mid-air
                        double lift = 1.6 + 0.9 * Math.sin(phase * 1.5);

                        Vector3f targetDisp = new Vector3f(
                                (float) (pushDir.x * pushDist),
                                (float) lift,
                                (float) (pushDir.z * pushDist)
                        );

                        // Zero-gravity 3D rotation / tumbling
                        Quaternionf rotation = new Quaternionf()
                                .rotateY((float) Math.toRadians(26.0 * Math.sin(phase)))
                                .rotateX((float) Math.toRadians(18.0 * Math.cos(phase * 1.3)))
                                .rotateZ((float) Math.toRadians(18.0 * Math.sin(phase * 1.7)));

                        Vector3f scale = new Vector3f(1.02f, 1.02f, 1.02f);

                        SpatialDisplacementManager.displaceBlock(world, mutablePos.toImmutable(), targetDisp, rotation, scale, 30, 0x00F5FF, true);

                        // Subtle ambient particles near parted blocks
                        world.spawnParticles(ParticleTypes.END_ROD, blockCenter.x, blockCenter.y, blockCenter.z, 2, 0.2, 0.2, 0.2, 0.02);
                        world.spawnParticles(ParticleTypes.GLOW, blockCenter.x, blockCenter.y, blockCenter.z, 1, 0.1, 0.1, 0.1, 0.01);
                    }
                }
            }
        }

        // Arcane footstep aura
        if (player.age % 4 == 0) {
            world.spawnParticles(ParticleTypes.ENCHANT,
                    player.getX(), player.getY() + 0.1, player.getZ(),
                    3, 0.3, 0.1, 0.3, 0.1);
        }
    }
}
