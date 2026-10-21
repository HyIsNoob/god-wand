package net.godwand.skill;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.godwand.network.ImpactFxPayload;
import net.godwand.spatial.DisplacedBlockRecord;
import net.godwand.spatial.SpatialDisplacementManager;
import net.godwand.spatial.SpatialMath;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
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
        ServerWorld world = player.getServerWorld();
        if (ACTIVE_PLAYERS.contains(player.getUuid())) {
            ACTIVE_PLAYERS.remove(player.getUuid());
            LAST_GROUND_Y.remove(player.getUuid());
            player.sendMessage(Text.literal("§c§l[✦ SPATIAL PATH: COLLAPSED ✦]"), true);

            // Explosive Deactivation Impact: Sonic boom, flash, and implosion particles
            world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getEyeY(), player.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
            world.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 40, 1.2, 0.8, 1.2, 0.25);
            world.spawnParticles(new DustParticleEffect(new Vector3f(0.25f, 0.88f, 1.0f), 1.2f),
                    player.getX(), player.getY() + 1.0, player.getZ(), 30, 1.5, 0.8, 1.5, 0.2);

            // Layered Heavy Deactivation Audio
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 2.5f, 0.75f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 2.0f, 1.5f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 2.0f, 1.2f);

            // Push nearby entities away with physical shockwave impulse
            for (net.minecraft.entity.Entity e : world.getOtherEntities(player, player.getBoundingBox().expand(5.0))) {
                Vec3d diff = e.getPos().subtract(player.getPos()).normalize().multiply(0.95).add(0, 0.28, 0);
                e.addVelocity(diff.x, diff.y, diff.z);
                e.velocityModified = true;
            }

            // Immediately begin 3-Phase Magnetic Snap Reassembly for all blocks in player's aura
            SpatialDisplacementManager.collapsePlayerAura(player.getUuid());
            SpatialDisplacementManager.collapseNearbyDisplacements(world.getRegistryKey(), player.getBlockPos(), 14.0);

            // Trigger Tactical Collapse Post-FX
            ImpactFxPayload colPayload = new ImpactFxPayload(
                    player.getX(), player.getEyeY(), player.getZ(),
                    ImpactFxPayload.TYPE_COLLAPSE, 8, 1.0f
            );
            for (ServerPlayerEntity p : PlayerLookup.around(world, player.getPos(), 48.0)) {
                ServerPlayNetworking.send(p, colPayload);
            }
        } else {
            ACTIVE_PLAYERS.add(player.getUuid());
            player.sendMessage(Text.literal("§d§l[✦ SPATIAL PATH: §aACTIVE §d§l✦]"), true);

            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.8f, 1.4f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 2.5f, 1.1f);
            world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.5, 0.5, 0.5, 0.15);
            world.spawnParticles(new DustParticleEffect(new Vector3f(0.3f, 0.9f, 1.0f), 1.0f),
                    player.getX(), player.getY() + 1.0, player.getZ(), 25, 0.5, 0.8, 0.5, 0.1);
        }
    }

    public static void deactivate(PlayerEntity player) {
        ACTIVE_PLAYERS.remove(player.getUuid());
        LAST_GROUND_Y.remove(player.getUuid());
        SpatialDisplacementManager.collapsePlayerAura(player.getUuid());
    }

    public static void tickPlayer(ServerPlayerEntity player) {
        if (!isActive(player)) return;

        ServerWorld world = player.getServerWorld();
        Vec3d eyePos = player.getEyePos();
        Vec3d chestPos = player.getPos().add(0, 1.1, 0);
        Vec3d lookVec = player.getRotationVec(1.0f).normalize();

        boolean allowDown = player.getAbilities().flying && player.getPitch() > 45.0f;

        if (player.isOnGround()) {
            LAST_GROUND_Y.put(player.getUuid(), player.getY());
        }
        double baseGroundY = player.isOnGround() ? player.getY() : LAST_GROUND_Y.getOrDefault(player.getUuid(), player.getY());
        int floorY = (int) Math.floor(baseGroundY);

        // Spherical Aura (R = 3.5m) + Forward Cone (Reach = 6.5m)
        double reach = 6.5;
        double auraRadius = 3.5;
        double maxReach = Math.max(auraRadius, reach);

        int minX = MathHelper.floor(player.getX() - maxReach);
        int maxX = MathHelper.floor(player.getX() + maxReach);
        int minY = allowDown ? MathHelper.floor(player.getY() - maxReach) : floorY;
        int maxY = MathHelper.floor(player.getY() + 4.0);
        int minZ = MathHelper.floor(player.getZ() - maxReach);
        int maxZ = MathHelper.floor(player.getZ() + maxReach);

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mutablePos.set(x, y, z);

                    // Absolute Ground Safety Rule: y < floorY is never displaced
                    if (!allowDown && y < floorY) continue;

                    // Footing Protection: never displace directly underneath or immediately around player's feet
                    if (!allowDown && Math.abs(x + 0.5 - player.getX()) <= 0.85 && Math.abs(z + 0.5 - player.getZ()) <= 0.85 && y <= floorY) {
                        continue;
                    }

                    Vec3d blockCenter = new Vec3d(x + 0.5, y + 0.5, z + 0.5);
                    Vec3d toBlock = blockCenter.subtract(chestPos);
                    double distToChest = toBlock.length();

                    // Check Spherical Aura (R <= 3.5m)
                    boolean inAura = distToChest <= auraRadius;

                    // Check Forward Cone (forward reach <= 6.5m)
                    boolean inForwardCone = false;
                    double forwardDist = toBlock.dotProduct(lookVec);
                    double perpDist = 0;
                    if (forwardDist > 0 && forwardDist <= reach) {
                        Vec3d forwardProj = lookVec.multiply(forwardDist);
                        perpDist = toBlock.subtract(forwardProj).length();
                        double coneRadius = 1.8 + 0.38 * forwardDist;
                        if (perpDist <= coneRadius) {
                            inForwardCone = true;
                        }
                    }

                    if (!inAura && !inForwardCone) continue;

                    // Refresh lifetime and hold if already displaced
                    if (SpatialDisplacementManager.isPositionDisplaced(world.getRegistryKey(), mutablePos)) {
                        SpatialDisplacementManager.refreshDisplacement(world.getRegistryKey(), mutablePos, 100);
                        continue;
                    }

                    BlockState state = world.getBlockState(mutablePos);
                    if (!SpatialDisplacementManager.canDisplace(world, mutablePos, state)) continue;

                    // Push direction from player center
                    Vec3d pushDir = toBlock.lengthSquared() > 0.001 ? toBlock.normalize() : new Vec3d(0, 1, 0);

                    // Push distance: inversely proportional to distance, with a strong clear margin
                    double effDist = inAura ? distToChest : perpDist;
                    double pushDist = Math.max(3.6, 5.0 - effDist * 0.45);

                    double phase = SpatialMath.hashCoordinateToPhase(mutablePos.asLong());

                    // Vertical lift Delta Y in [+1.2m, +2.5m]
                    double liftY = 1.4 + 0.8 * Math.sin(phase * 1.6);

                    double lateralX = pushDir.x * pushDist;
                    double lateralZ = pushDir.z * pushDist;

                    // Avoid center lock if block is right in front of chest
                    if (Math.abs(lateralX) < 0.25 && Math.abs(lateralZ) < 0.25) {
                        boolean goRight = ((x + z) & 1) == 0;
                        lateralX = (goRight ? 1 : -1) * 3.6;
                    }

                    Vector3f targetDisp = new Vector3f(
                            (float) lateralX,
                            (float) liftY,
                            (float) lateralZ
                    );

                    // Zero-gravity dynamic tilt / tumbling
                    Quaternionf rotation = new Quaternionf()
                            .rotateY((float) Math.toRadians(26.0 * Math.sin(phase)))
                            .rotateX((float) Math.toRadians(18.0 * Math.cos(phase * 1.3)))
                            .rotateZ((float) Math.toRadians(18.0 * Math.sin(phase * 1.7)));

                    Vector3f scale = new Vector3f(1.02f, 1.02f, 1.02f);

                    SpatialDisplacementManager.displaceBlock(world, mutablePos.toImmutable(), targetDisp, rotation, scale,
                            100, 0x00F5FF, true, DisplacedBlockRecord.DisplacementMode.HYSTERESIS_AURA,
                            player.getUuid(), null);

                    // Subtle ambient shimmer particle
                    if (((x ^ z) & 7) == 0) {
                        world.spawnParticles(ParticleTypes.END_ROD, blockCenter.x, blockCenter.y, blockCenter.z, 1, 0.1, 0.1, 0.1, 0.01);
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
