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

            // Explosive Deactivation Impact
            world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getEyeY(), player.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
            world.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 40, 1.2, 0.8, 1.2, 0.25);
            world.spawnParticles(new DustParticleEffect(new Vector3f(0.25f, 0.88f, 1.0f), 1.2f),
                    player.getX(), player.getY() + 1.0, player.getZ(), 30, 1.5, 0.8, 1.5, 0.2);

            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 2.5f, 0.75f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 2.0f, 1.5f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 2.0f, 1.2f);

            // Push nearby entities away
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
        Vec3d playerPos = player.getPos();
        Vec3d chestPos = playerPos.add(0, 1.1, 0);
        Vec3d lookVec = player.getRotationVec(1.0f);
        Vec3d lookDirHoriz = new Vec3d(lookVec.x, 0, lookVec.z);
        if (lookDirHoriz.lengthSquared() < 0.001) {
            lookDirHoriz = new Vec3d(1, 0, 0);
        } else {
            lookDirHoriz = lookDirHoriz.normalize();
        }

        boolean allowDown = player.getAbilities().flying && player.getPitch() > 45.0f;

        if (player.isOnGround()) {
            LAST_GROUND_Y.put(player.getUuid(), player.getY());
        }
        double baseGroundY = player.isOnGround() ? player.getY() : LAST_GROUND_Y.getOrDefault(player.getUuid(), player.getY());
        int floorY = (int) Math.floor(baseGroundY);

        // Spherical Aura (R = 3.8m) + Forward Cone (Reach = 6.5m)
        double auraRadius = 3.8;
        double forwardReach = 6.5;
        double maxRange = Math.max(auraRadius, forwardReach);

        int minX = MathHelper.floor(playerPos.x - maxRange);
        int maxX = MathHelper.floor(playerPos.x + maxRange);
        int minY = allowDown ? MathHelper.floor(playerPos.y - maxRange) : floorY;
        int maxY = MathHelper.floor(playerPos.y + 4.2);
        int minZ = MathHelper.floor(playerPos.z - maxRange);
        int maxZ = MathHelper.floor(playerPos.z + maxRange);

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mutablePos.set(x, y, z);

                    // Absolute Ground Safety Rule
                    if (!allowDown && y < floorY) continue;

                    // Footing Protection
                    if (!allowDown && Math.abs(x + 0.5 - playerPos.x) <= 0.85 && Math.abs(z + 0.5 - playerPos.z) <= 0.85 && y <= floorY) {
                        continue;
                    }

                    Vec3d blockCenter = Vec3d.ofCenter(mutablePos);
                    Vec3d toBlock = blockCenter.subtract(chestPos);
                    double distToChest = toBlock.length();

                    // Check Spherical Aura (R <= 3.8m)
                    boolean inSphericalAura = distToChest <= auraRadius;

                    // Check Forward Cone (forward reach <= 6.5m, radius 2.8m -> 3.8m)
                    boolean inForwardCone = false;
                    double forwardDist = (blockCenter.x - playerPos.x) * lookDirHoriz.x + (blockCenter.z - playerPos.z) * lookDirHoriz.z;
                    if (forwardDist > 0 && forwardDist <= forwardReach) {
                        Vec3d spine = new Vec3d(playerPos.x + lookDirHoriz.x * forwardDist, blockCenter.y, playerPos.z + lookDirHoriz.z * forwardDist);
                        double perpDist = blockCenter.subtract(spine).length();
                        double coneRadius = 2.8 + (forwardDist / forwardReach) * (3.8 - 2.8);
                        if (perpDist <= coneRadius) {
                            inForwardCone = true;
                        }
                    }

                    if (!inSphericalAura && !inForwardCone) continue;

                    // Distance Hysteresis: keep open and reset restore tick if already displaced
                    if (SpatialDisplacementManager.isPositionDisplaced(world.getRegistryKey(), mutablePos)) {
                        SpatialDisplacementManager.refreshDisplacement(world.getRegistryKey(), mutablePos, 100);
                        continue;
                    }

                    BlockState state = world.getBlockState(mutablePos);
                    if (!SpatialDisplacementManager.canDisplace(world, mutablePos, state)) continue;

                    // Vector displacement computed by SpatialMath
                    Vec3d auraDisp = SpatialMath.computeAuraDisplacement(mutablePos, playerPos, lookDirHoriz, auraRadius);
                    Vector3f targetDisp = new Vector3f((float) auraDisp.x, (float) auraDisp.y, (float) auraDisp.z);

                    double hashPhase = SpatialMath.hash01(mutablePos) * Math.PI * 2.0;
                    Quaternionf rotation = new Quaternionf()
                            .rotateY((float) Math.toRadians(25.0 * Math.sin(hashPhase)))
                            .rotateX((float) Math.toRadians(15.0 * Math.cos(hashPhase)))
                            .rotateZ((float) Math.toRadians(15.0 * Math.sin(hashPhase * 1.5)));

                    Vector3f scale = new Vector3f(1.02f, 1.02f, 1.02f);

                    SpatialDisplacementManager.displaceBlock(
                            world,
                            mutablePos.toImmutable(),
                            targetDisp,
                            rotation,
                            scale,
                            100,
                            0x00F5FF,
                            true,
                            DisplacedBlockRecord.DisplacementMode.PATH_WALKER,
                            player.getUuid(),
                            null,
                            0.0
                    );

                    // Ambient shimmer particle
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
