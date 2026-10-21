package net.godwand.spatial;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Clearable;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpatialDisplacementManager {
    private static final Map<DimensionPos, DisplacedBlockRecord> ACTIVE_RECORDS = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockDisplayEntity> ACTIVE_ENTITIES = new ConcurrentHashMap<>();

    public static boolean isPositionDisplaced(RegistryKey<World> worldKey, BlockPos pos) {
        return ACTIVE_RECORDS.containsKey(new DimensionPos(worldKey, pos));
    }

    public static boolean isPositionDisplaced(BlockPos pos) {
        return isPositionDisplaced(World.OVERWORLD, pos);
    }

    public static boolean canDisplace(ServerWorld world, BlockPos pos, BlockState state) {
        BlockPos immutablePos = pos.toImmutable();
        if (state.isAir() || state.getHardness(world, immutablePos) < 0) return false;
        if (state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.END_PORTAL) || state.isOf(Blocks.BARRIER)) return false;
        return !isPositionDisplaced(world.getRegistryKey(), immutablePos);
    }

    public static int getActiveRecordCount() {
        return ACTIVE_RECORDS.size();
    }

    public static int getActiveEntityCount() {
        return ACTIVE_ENTITIES.size();
    }

    public static DisplacedBlockRecord getActiveRecord(RegistryKey<World> worldKey, BlockPos pos) {
        return ACTIVE_RECORDS.get(new DimensionPos(worldKey, pos));
    }

    public static BlockDisplayEntity getActiveEntity(UUID uuid) {
        return uuid != null ? ACTIVE_ENTITIES.get(uuid) : null;
    }

    public static DisplacedBlockRecord removeActiveRecord(RegistryKey<World> worldKey, BlockPos pos) {
        DisplacedBlockRecord rec = ACTIVE_RECORDS.remove(new DimensionPos(worldKey, pos));
        if (rec != null && rec.displayEntityUuid != null) {
            BlockDisplayEntity ent = ACTIVE_ENTITIES.remove(rec.displayEntityUuid);
            if (ent != null && ent.isAlive()) {
                ent.discard();
            }
        }
        return rec;
    }

    public static List<DisplacedBlockRecord> getActiveRecordsForWorld(RegistryKey<World> worldKey) {
        List<DisplacedBlockRecord> list = new ArrayList<>();
        for (DisplacedBlockRecord r : ACTIVE_RECORDS.values()) {
            if (r.worldKey.equals(worldKey)) {
                list.add(r);
            }
        }
        return list;
    }

    public static void refreshDisplacement(RegistryKey<World> worldKey, BlockPos pos, int lifetime) {
        DisplacedBlockRecord record = ACTIVE_RECORDS.get(new DimensionPos(worldKey, pos));
        if (record != null) {
            record.resetRestoreTick();
            if (record.ageTicks > 8) {
                record.ageTicks = 9;
            }
            record.maxLifetimeTicks = lifetime;
        }
    }

    public static BlockPos getMatchingDoubleBlockHalf(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            if (state.contains(DoorBlock.HALF)) {
                DoubleBlockHalf half = state.get(DoorBlock.HALF);
                BlockPos target = half == DoubleBlockHalf.LOWER ? pos.up() : pos.down();
                BlockState targetState = world.getBlockState(target);
                if (targetState.isOf(state.getBlock()) && targetState.contains(DoorBlock.HALF) && targetState.get(DoorBlock.HALF) != half) {
                    return target;
                }
            }
        } else if (state.getBlock() instanceof TallPlantBlock) {
            if (state.contains(TallPlantBlock.HALF)) {
                DoubleBlockHalf half = state.get(TallPlantBlock.HALF);
                BlockPos target = half == DoubleBlockHalf.LOWER ? pos.up() : pos.down();
                BlockState targetState = world.getBlockState(target);
                if (targetState.isOf(state.getBlock()) && targetState.contains(TallPlantBlock.HALF) && targetState.get(TallPlantBlock.HALF) != half) {
                    return target;
                }
            }
        } else if (state.getBlock() instanceof BedBlock) {
            if (state.contains(BedBlock.PART) && state.contains(BedBlock.FACING)) {
                BedPart part = state.get(BedBlock.PART);
                Direction facing = state.get(BedBlock.FACING);
                BlockPos target = part == BedPart.FOOT ? pos.offset(facing) : pos.offset(facing.getOpposite());
                BlockState targetState = world.getBlockState(target);
                if (targetState.isOf(state.getBlock()) && targetState.contains(BedBlock.PART) && targetState.get(BedBlock.PART) != part) {
                    return target;
                }
            }
        }
        return null;
    }

    public static void displaceBlock(ServerWorld world, BlockPos pos, Vector3f targetDisplacement,
                                      Quaternionf rotation, int lifetime, int glowColor) {
        displaceBlock(world, pos, targetDisplacement, rotation, new Vector3f(1.0f, 1.0f, 1.0f), lifetime, glowColor, true);
    }

    public static void displaceBlock(ServerWorld world, BlockPos pos, Vector3f targetDisplacement,
                                      Quaternionf rotation, int lifetime, int glowColor, boolean spawnDisplay) {
        displaceBlock(world, pos, targetDisplacement, rotation, new Vector3f(1.0f, 1.0f, 1.0f), lifetime, glowColor, spawnDisplay);
    }

    public static void displaceBlock(ServerWorld world, BlockPos pos, Vector3f targetDisplacement,
                                      Quaternionf rotation, Vector3f scale, int lifetime, int glowColor, boolean spawnDisplay) {
        displaceBlock(world, pos, targetDisplacement, rotation, scale, lifetime, glowColor, spawnDisplay,
                DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE, null, null);
    }

    public static void displaceBlock(ServerWorld world, BlockPos pos, Vector3f targetDisplacement,
                                      Quaternionf rotation, Vector3f scale, int lifetime, int glowColor,
                                      boolean spawnDisplay, DisplacedBlockRecord.DisplacementMode mode,
                                      UUID boundPlayerUuid, Vector3f vortexCenter) {
        displaceBlockInternal(world, pos, targetDisplacement, rotation, scale, lifetime, glowColor,
                spawnDisplay, mode, boundPlayerUuid, vortexCenter, true);
    }

    private static void displaceBlockInternal(ServerWorld world, BlockPos pos, Vector3f targetDisplacement,
                                              Quaternionf rotation, Vector3f scale, int lifetime, int glowColor,
                                              boolean spawnDisplay, DisplacedBlockRecord.DisplacementMode mode,
                                              UUID boundPlayerUuid, Vector3f vortexCenter, boolean checkDoubleBlock) {
        BlockPos immutablePos = pos.toImmutable();
        BlockState state = world.getBlockState(immutablePos);
        if (!canDisplace(world, immutablePos, state)) return;

        BlockPos otherHalf = checkDoubleBlock ? getMatchingDoubleBlockHalf(world, immutablePos, state) : null;

        // Skip display spawn for pure water or lava
        if (state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA)) {
            spawnDisplay = false;
        }

        BlockEntity be = world.getBlockEntity(immutablePos);
        NbtCompound nbt = null;
        if (be != null) {
            nbt = be.createNbtWithIdentifyingData(world.getRegistryManager());
            Clearable.clear(be);
        }

        boolean isFull = state.isFullCube(world, immutablePos);
        DisplacedBlockRecord record = new DisplacedBlockRecord(
                immutablePos, world.getRegistryKey(), state, nbt, targetDisplacement, rotation, scale,
                lifetime, isFull, mode, boundPlayerUuid);
        record.vortexCenter = vortexCenter;

        // Replace with AIR immediately using safe flags
        world.setBlockState(immutablePos, Blocks.AIR.getDefaultState(), Block.FORCE_STATE | Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);

        if (spawnDisplay) {
            BlockDisplayEntity display = new BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
            display.addCommandTag("godwand_displaced");
            display.setPosition(immutablePos.getX(), immutablePos.getY(), immutablePos.getZ());
            display.setBlockState(state);
            display.setGlowing(false);
            display.setBrightness(new Brightness(15, 15));
            display.setStartInterpolation(0);
            display.setInterpolationDuration(0);
            display.setTransformation(AffineTransformation.identity());

            world.spawnEntity(display);
            record.displayEntityUuid = display.getUuid();
            ACTIVE_ENTITIES.put(display.getUuid(), display);

            // Subtle stone crumbs on initial separation
            if ((immutablePos.asLong() & 3) == 0) {
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                        immutablePos.getX() + 0.5, immutablePos.getY() + 0.5, immutablePos.getZ() + 0.5,
                        3, 0.15, 0.15, 0.15, 0.08);
                world.playSound(null, immutablePos, SoundEvents.BLOCK_BASALT_BREAK, SoundCategory.BLOCKS, 0.6f, 0.85f + 0.25f * (float)Math.random());
            }
        }

        ACTIVE_RECORDS.put(new DimensionPos(world.getRegistryKey(), immutablePos), record);
        DisplacementPersistentState.getServerState(world).markDirty();

        // Displace matching half of double block
        if (otherHalf != null && canDisplace(world, otherHalf, world.getBlockState(otherHalf))) {
            displaceBlockInternal(world, otherHalf, targetDisplacement, rotation, scale, lifetime,
                    glowColor, spawnDisplay, mode, boundPlayerUuid, vortexCenter, false);
        }
    }

    public static void tick(ServerWorld world) {
        List<DisplacedBlockRecord> toRestore = new ArrayList<>();

        for (DisplacedBlockRecord record : ACTIVE_RECORDS.values()) {
            if (!record.worldKey.equals(world.getRegistryKey())) {
                continue;
            }

            record.ageTicks++;
            BlockDisplayEntity display = record.displayEntityUuid != null ? ACTIVE_ENTITIES.get(record.displayEntityUuid) : null;

            // ==========================================
            // 3-PHASE MAGNETIC SNAP REASSEMBLY (RESTORE)
            // ==========================================
            if (record.isRestoring()) {
                record.restoreTick++;

                if (display != null && display.isAlive()) {
                    // Phase 1: Inward Vortex Drag (restoreTick == 1)
                    if (record.restoreTick == 1) {
                        Vector3f preSnapOffset = new Vector3f(record.currentOffset).mul(0.15f);
                        record.currentOffset.set(preSnapOffset);
                        Quaternionf nearZeroRot = new Quaternionf().slerp(record.currentRotation, 0.2f);
                        record.currentRotation.set(nearZeroRot);

                        Vector3f comp = SpatialMath.compensatePivot(preSnapOffset, nearZeroRot);
                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(10); // 10 ticks: pull from far away to 15% distance
                        display.setTransformation(new AffineTransformation(comp, nearZeroRot, record.targetScale, null));
                    }
                    // Phase 2: Magnetic Snap / Latch (restoreTick == 11)
                    else if (record.restoreTick == 11) {
                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(3); // 3 ticks: magnetic violent snap to identity
                        display.setTransformation(new AffineTransformation(
                                new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f), null));

                        // Mechanical vault latch sound
                        world.playSound(null, record.pos, SoundEvents.BLOCK_VAULT_OPEN_SHUTTER, SoundCategory.BLOCKS, 0.25f, 1.4f);
                    }
                }

                // Phase 3: Exact 1:1 Solid restoration at tick 14
                if (record.restoreTick == 14 && record.isFullCube) {
                    toRestore.add(record);
                }
                // Phase 3: Delicate attached blocks (torches, lanterns, signs, doors) at tick 15
                else if (record.restoreTick >= 15) {
                    toRestore.add(record);
                }
                continue; // Skip normal active physics while restoring
            }

            // ==========================================
            // 2-TICK SPAWN HANDSHAKE (ACCELERATION)
            // ==========================================
            if (record.spawnTickDelay > 0) {
                record.spawnTickDelay--;
                if (record.spawnTickDelay == 0 && display != null && display.isAlive()) {
                    Vector3f burstDisp = new Vector3f(
                            record.targetDisplacement.x * 1.12f,
                            record.targetDisplacement.y * 1.12f + 0.2f,
                            record.targetDisplacement.z * 1.12f
                    );
                    float impulseAngle = (float) Math.toRadians(12.0 * (Math.signum(record.targetDisplacement.x) != 0 ? Math.signum(record.targetDisplacement.x) : 1.0));
                    Quaternionf burstRot = new Quaternionf(record.targetRotation).rotateY(impulseAngle).rotateZ(impulseAngle * 0.5f);
                    Vector3f burstScale = new Vector3f(record.targetScale.x * 1.03f, record.targetScale.y * 1.03f, record.targetScale.z * 1.03f);

                    Vector3f comp = SpatialMath.compensatePivot(burstDisp, burstRot);
                    display.setStartInterpolation(0);
                    display.setInterpolationDuration(8); // 8 ticks: natural physical acceleration
                    display.setTransformation(new AffineTransformation(comp, burstRot, burstScale, null));

                    record.currentOffset.set(burstDisp);
                    record.currentRotation.set(burstRot);
                    record.animationStage = 1;
                    record.transformed = true;
                }
                continue;
            }

            // ==========================================
            // SKILL 1: LIVING AURA & DYNAMIC EVASION
            // ==========================================
            if (record.mode == DisplacedBlockRecord.DisplacementMode.HYSTERESIS_AURA) {
                if (record.boundPlayerUuid != null) {
                    ServerPlayerEntity boundPlayer = world.getServer().getPlayerManager().getPlayer(record.boundPlayerUuid);
                    if (boundPlayer != null && boundPlayer.getServerWorld() == world && net.godwand.skill.SpatialPathWalker.isActive(boundPlayer)) {
                        double distToHome = boundPlayer.getPos().distanceTo(Vec3d.ofCenter(record.pos));

                        // Hysteresis Hold: keep open as long as player is within 5.5m
                        if (distToHome <= 5.5) {
                            if (record.isRestoring()) {
                                record.interruptRestoration();
                            }

                            // Dynamic Evasion: every 2 ticks check distance to player's chest
                            if (display != null && display.isAlive() && (record.ageTicks % 2 == 0) && record.animationStage >= 1) {
                                Vec3d playerChest = boundPlayer.getPos().add(0, 1.1, 0);
                                Vec3d currentWorldPos = Vec3d.ofCenter(record.pos).add(
                                        record.currentOffset.x, record.currentOffset.y, record.currentOffset.z
                                );
                                double distToDebris = currentWorldPos.distanceTo(playerChest);

                                if (distToDebris < 2.8) {
                                    double pushStrength = Math.pow(2.8 - distToDebris, 2) * 1.6;
                                    Vec3d evasionDir = distToDebris > 0.05
                                            ? currentWorldPos.subtract(playerChest).normalize().multiply(pushStrength)
                                            : new Vec3d(0, 1, 0).multiply(pushStrength);

                                    float phase = (float) (SpatialMath.hashCoordinateToPhase(record.pos.asLong()) + (record.ageTicks / 8.0));
                                    float bobY = (float) (Math.sin(phase) * 0.12);

                                    Vector3f newOffset = new Vector3f(
                                            (float) (record.baseDisplacement.x + evasionDir.x),
                                            (float) (record.baseDisplacement.y + bobY + evasionDir.y),
                                            (float) (record.baseDisplacement.z + evasionDir.z)
                                    );
                                    record.currentOffset.set(newOffset);

                                    record.hoverYaw += 0.35f;
                                    Quaternionf evasionRot = new Quaternionf(record.targetRotation)
                                            .rotateY(record.hoverYaw)
                                            .rotateX((float) Math.toRadians(15.0 * Math.sin(phase)))
                                            .rotateZ((float) Math.toRadians(15.0 * Math.cos(phase)));
                                    record.currentRotation.set(evasionRot);

                                    Vector3f comp = SpatialMath.compensatePivot(newOffset, evasionRot);
                                    display.setStartInterpolation(0);
                                    display.setInterpolationDuration(3); // 3 ticks reactive evasion
                                    display.setTransformation(new AffineTransformation(comp, evasionRot, record.targetScale, null));
                                } else if (record.currentOffset.distance(record.baseDisplacement) > 0.15f) {
                                    // Settle back to base hover offset
                                    float phase = (float) (SpatialMath.hashCoordinateToPhase(record.pos.asLong()) + (record.ageTicks / 8.0));
                                    float bobY = (float) (Math.sin(phase) * 0.12);

                                    Vector3f returnOffset = new Vector3f(
                                            record.baseDisplacement.x,
                                            record.baseDisplacement.y + bobY,
                                            record.baseDisplacement.z
                                    );
                                    record.currentOffset.set(returnOffset);
                                    record.hoverYaw += 0.05f;

                                    Quaternionf returnRot = new Quaternionf(record.targetRotation)
                                            .rotateY(record.hoverYaw)
                                            .rotateX((float) Math.toRadians(6.0 * Math.sin(phase)))
                                            .rotateZ((float) Math.toRadians(6.0 * Math.cos(phase)));
                                    record.currentRotation.set(returnRot);

                                    Vector3f comp = SpatialMath.compensatePivot(returnOffset, returnRot);
                                    display.setStartInterpolation(0);
                                    display.setInterpolationDuration(5);
                                    display.setTransformation(new AffineTransformation(comp, returnRot, record.targetScale, null));
                                }
                            }
                        } else {
                            // Player moved beyond 5.5m -> begin 3-Phase Restoration
                            record.beginRestoration();
                        }
                    } else {
                        // Player offline or toggled off -> begin restoration
                        record.beginRestoration();
                    }
                }
                continue;
            }

            // ==========================================
            // SKILL 2: UPWARD CANOPY & STAGGERED VORTEX
            // ==========================================
            if (record.mode == DisplacedBlockRecord.DisplacementMode.TIMED_LIFECYCLE) {
                if (record.ageTicks >= record.maxLifetimeTicks) {
                    record.beginRestoration();
                    continue;
                }

                // Staggered Y-axis vortex rotation around vortexCenter
                if (record.vortexCenter != null && display != null && display.isAlive()) {
                    if ((display.getId() + record.ageTicks) % 3 == 0) {
                        // Current world position on air
                        double worldX = record.homePos.x + record.currentOffset.x;
                        double worldZ = record.homePos.z + record.currentOffset.z;

                        double relX = worldX - record.vortexCenter.x;
                        double relZ = worldZ - record.vortexCenter.z;

                        // Rotate 2.5 degrees around Y
                        double angle = Math.toRadians(2.5);
                        double cosA = Math.cos(angle);
                        double sinA = Math.sin(angle);

                        double newRelX = relX * cosA - relZ * sinA;
                        double newRelZ = relX * sinA + relZ * cosA;

                        float newOffsetX = (float) ((record.vortexCenter.x + newRelX) - record.homePos.x);
                        float newOffsetZ = (float) ((record.vortexCenter.z + newRelZ) - record.homePos.z);

                        float phase = (float) (SpatialMath.hashCoordinateToPhase(record.pos.asLong()) + (record.ageTicks / 10.0));
                        float bobY = (float) (Math.sin(phase) * 0.15);
                        float newOffsetY = record.baseDisplacement.y + bobY;

                        record.currentOffset.set(newOffsetX, newOffsetY, newOffsetZ);
                        record.currentRotation.rotateY((float) Math.toRadians(2.5));

                        Vector3f comp = SpatialMath.compensatePivot(record.currentOffset, record.currentRotation);
                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(3); // Continuous seamless rotation
                        display.setTransformation(new AffineTransformation(comp, record.currentRotation, record.targetScale, null));
                    }
                } else if (display != null && display.isAlive()) {
                    // Standard gentle zero-G orbital bobbing for custom scripted / default blocks
                    if (record.ageTicks % 8 == 0 && record.animationStage >= 1) {
                        record.hoverYaw += 0.10f;
                        float phase = (float) (SpatialMath.hashCoordinateToPhase(record.pos.asLong()) + (record.ageTicks / 8.0));
                        float tremorY = (float) (Math.sin(phase) * 0.12);

                        Vector3f hoverDisp = new Vector3f(
                                record.baseDisplacement.x,
                                record.baseDisplacement.y + tremorY,
                                record.baseDisplacement.z
                        );
                        record.currentOffset.set(hoverDisp);
                        Quaternionf hoverRot = new Quaternionf(record.targetRotation)
                                .rotateY(record.hoverYaw)
                                .rotateX((float) Math.toRadians(4.0 * Math.sin(phase * 1.5)))
                                .rotateZ((float) Math.toRadians(4.0 * Math.cos(phase * 1.2)));
                        record.currentRotation.set(hoverRot);

                        Vector3f comp = SpatialMath.compensatePivot(hoverDisp, hoverRot);
                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(8);
                        display.setTransformation(new AffineTransformation(comp, hoverRot, record.targetScale, null));
                    }
                }
            }

            // Tether Trail: Sampled exclusively on 1 in 8 anchor blocks every 4 ticks
            if (record.transformed && ((record.pos.asLong() & 7) == 0) && record.ageTicks % 4 == 0 && !record.isRestoring()) {
                double homeX = record.pos.getX() + 0.5;
                double homeY = record.pos.getY() + 0.5;
                double homeZ = record.pos.getZ() + 0.5;

                Vector3f comp = SpatialMath.compensatePivot(record.currentOffset, record.currentRotation);
                double floatX = homeX + comp.x;
                double floatY = homeY + comp.y;
                double floatZ = homeZ + comp.z;

                double dx = floatX - homeX;
                double dy = floatY - homeY;
                double dz = floatZ - homeZ;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (dist > 0.6) {
                    for (double d = 0.6; d < dist - 0.3; d += 0.85) {
                        double t = d / dist;
                        double px = homeX + dx * t;
                        double py = homeY + dy * t;
                        double pz = homeZ + dz * t;
                        world.spawnParticles(new DustParticleEffect(new Vector3f(0.28f, 0.90f, 1.0f), 0.30f),
                                px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            }
        }

        if (!toRestore.isEmpty()) {
            executeTwoPassRestoration(world, toRestore);
        }
    }

    public static void executeTwoPassRestoration(ServerWorld world, List<DisplacedBlockRecord> records) {
        // Pass 1: Solid base blocks
        for (DisplacedBlockRecord r : records) {
            if (r.isFullCube) {
                restoreSingleBlock(world, r);
            }
        }
        // Pass 2: Attached & delicate blocks (Torches, Lanterns, Signs, Doors, Plants)
        // Sort by Y ascending so lower parts of double blocks are placed before upper parts
        List<DisplacedBlockRecord> delicate = new ArrayList<>();
        for (DisplacedBlockRecord r : records) {
            if (!r.isFullCube) {
                delicate.add(r);
            }
        }
        delicate.sort(Comparator.comparingInt(r -> r.pos.getY()));
        for (DisplacedBlockRecord r : delicate) {
            restoreSingleBlock(world, r);
        }
    }

    private static void restoreSingleBlock(ServerWorld world, DisplacedBlockRecord r) {
        world.setBlockState(r.pos, r.state, Block.FORCE_STATE | Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
        if (r.nbt != null) {
            BlockEntity be = world.getBlockEntity(r.pos);
            if (be != null) {
                be.read(r.nbt, world.getRegistryManager());
                be.markDirty();
            }
        }
        if (r.displayEntityUuid != null) {
            BlockDisplayEntity display = ACTIVE_ENTITIES.remove(r.displayEntityUuid);
            if (display != null) {
                display.discard();
            }
            // Crisp stone impact sound and dust on re-solidification
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, r.state),
                    r.pos.getX() + 0.5, r.pos.getY() + 0.5, r.pos.getZ() + 0.5, 4, 0.15, 0.15, 0.15, 0.05);
            if ((r.pos.asLong() & 3) == 0) {
                world.playSound(null, r.pos, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 0.75f, 1.0f);
            }
        }
        ACTIVE_RECORDS.remove(new DimensionPos(r.worldKey, r.pos));
        DisplacementPersistentState.getServerState(world).markDirty();
    }

    public static void collapsePlayerAura(UUID playerUuid) {
        for (DisplacedBlockRecord record : ACTIVE_RECORDS.values()) {
            if (record.boundPlayerUuid != null && record.boundPlayerUuid.equals(playerUuid)) {
                record.beginRestoration();
            }
        }
    }

    public static void emergencyRestoreAll(ServerWorld world) {
        List<DisplacedBlockRecord> matching = new ArrayList<>();
        for (DisplacedBlockRecord r : ACTIVE_RECORDS.values()) {
            if (r.worldKey.equals(world.getRegistryKey())) {
                matching.add(r);
            }
        }
        executeTwoPassRestoration(world, matching);
    }

    public static void collapseNearbyDisplacements(RegistryKey<World> worldKey, BlockPos center, double radius) {
        double rSq = radius * radius;
        for (DisplacedBlockRecord record : ACTIVE_RECORDS.values()) {
            if (record.worldKey.equals(worldKey)) {
                double dSq = record.pos.getSquaredDistance(center);
                if (dSq <= rSq) {
                    record.beginRestoration();
                }
            }
        }
    }
}
