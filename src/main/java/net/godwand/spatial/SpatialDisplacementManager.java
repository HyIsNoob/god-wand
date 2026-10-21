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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Clearable;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
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
            // Keep block in hovering/tumbling stage while player stays in the corridor
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
        displaceBlockInternal(world, pos, targetDisplacement, rotation, scale, lifetime, glowColor, spawnDisplay, true);
    }

    private static void displaceBlockInternal(ServerWorld world, BlockPos pos, Vector3f targetDisplacement,
                                              Quaternionf rotation, Vector3f scale, int lifetime, int glowColor, boolean spawnDisplay,
                                              boolean checkDoubleBlock) {
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
                immutablePos, world.getRegistryKey(), state, nbt, targetDisplacement, rotation, scale, lifetime, isFull);

        // Replace with AIR immediately
        world.setBlockState(immutablePos, Blocks.AIR.getDefaultState(), Block.FORCE_STATE | Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);

        if (spawnDisplay) {
            BlockDisplayEntity display = new BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
            display.addCommandTag("godwand_displaced");
            display.setPosition(immutablePos.getX(), immutablePos.getY(), immutablePos.getZ());
            display.setBlockState(state);
            display.setGlowing(false); // Clean visual: no obstructive spectral outline
            display.setBrightness(new Brightness(15, 15)); // Vibrant texture even inside mountains
            display.setStartInterpolation(0);
            display.setInterpolationDuration(0);
            display.setTransformation(AffineTransformation.identity());

            world.spawnEntity(display);
            record.displayEntityUuid = display.getUuid();
            ACTIVE_ENTITIES.put(display.getUuid(), display);

            // Kinetic physical impact: Subtle stone crumbs (throttled to 1 in 4 blocks to prevent lag)
            if ((immutablePos.asLong() & 3) == 0) {
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                        immutablePos.getX() + 0.5, immutablePos.getY() + 0.5, immutablePos.getZ() + 0.5,
                        3, 0.15, 0.15, 0.15, 0.08);
                world.playSound(null, immutablePos, SoundEvents.BLOCK_BASALT_BREAK, SoundCategory.BLOCKS, 0.6f, 0.85f + 0.25f * (float)Math.random());
            }
        }

        ACTIVE_RECORDS.put(new DimensionPos(world.getRegistryKey(), immutablePos), record);
        DisplacementPersistentState.getServerState(world).markDirty();

        // Displace the other half of double blocks simultaneously
        if (otherHalf != null && canDisplace(world, otherHalf, world.getBlockState(otherHalf))) {
            displaceBlockInternal(world, otherHalf, targetDisplacement, rotation, scale, lifetime, glowColor, spawnDisplay, false);
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

            // Phase 1: Tick T1 Handshake - Physical Acceleration Wave (7 ticks duration for natural mass & momentum)
            if (record.spawnTickDelay > 0) {
                record.spawnTickDelay--;
                if (record.spawnTickDelay == 0 && display != null && display.isAlive()) {
                    // Physical acceleration: 1.15x overshoot + 0.25m lift + dynamic tilt
                    Vector3f burstDisp = new Vector3f(
                            record.targetDisplacement.x * 1.15f,
                            record.targetDisplacement.y * 1.15f + 0.25f,
                            record.targetDisplacement.z * 1.15f
                    );
                    float impulseAngle = (float) Math.toRadians(12.0 * (Math.signum(record.targetDisplacement.x) != 0 ? Math.signum(record.targetDisplacement.x) : 1.0));
                    Quaternionf burstRot = new Quaternionf(record.targetRotation).rotateY(impulseAngle).rotateZ(impulseAngle * 0.5f);
                    Vector3f burstScale = new Vector3f(record.targetScale.x * 1.04f, record.targetScale.y * 1.04f, record.targetScale.z * 1.04f);

                    Vector3f comp = SpatialMath.compensatePivot(burstDisp, burstRot);
                    display.setStartInterpolation(0);
                    display.setInterpolationDuration(7); // 7 ticks (350ms): heavy physical acceleration into air!
                    display.setTransformation(new AffineTransformation(comp, burstRot, burstScale, null));
                    record.animationStage = 1;
                    record.transformed = true;
                }
            } else if (display != null && display.isAlive()) {
                // Phase 2: Damped Gravity Settle (Tick 8 -> 14)
                if (record.animationStage == 1 && record.ageTicks >= 8) {
                    Vector3f comp = SpatialMath.compensatePivot(record.targetDisplacement, record.targetRotation);
                    display.setStartInterpolation(0);
                    display.setInterpolationDuration(7); // 7 ticks (350ms) smooth deceleration settling into hover
                    display.setTransformation(new AffineTransformation(
                            comp, record.targetRotation, record.targetScale, null));
                    record.animationStage = 2;
                }
                // Phase 3: Zero-G Orbital Rotation & Micro-Tremor (Tick 15 to maxLifetime - 10)
                else if (record.animationStage == 2 && record.ageTicks < record.maxLifetimeTicks - 10) {
                    // Every 8 ticks: advance gentle orbital rotation and apply organic micro-tremor
                    if ((record.ageTicks - 15) % 8 == 0) {
                        record.hoverYaw += 0.12f; // slow continuous tumble
                        float phase = (float) (SpatialMath.hashCoordinateToPhase(record.pos.asLong()) + (record.ageTicks / 8.0));
                        float tremorY = (float) (Math.sin(phase) * 0.12 + Math.cos(phase * 2.3) * 0.04);
                        float tremorX = (float) (Math.cos(phase * 1.7) * 0.05);
                        float tremorZ = (float) (Math.sin(phase * 1.4) * 0.05);

                        Vector3f hoverDisp = new Vector3f(
                                record.targetDisplacement.x + tremorX,
                                record.targetDisplacement.y + tremorY,
                                record.targetDisplacement.z + tremorZ
                        );
                        Quaternionf hoverRot = new Quaternionf(record.targetRotation)
                                .rotateY(record.hoverYaw)
                                .rotateX((float) Math.toRadians(3.5 * Math.sin(phase * 1.9)))
                                .rotateZ((float) Math.toRadians(3.5 * Math.cos(phase * 1.5)));

                        Vector3f comp = SpatialMath.compensatePivot(hoverDisp, hoverRot);
                        display.setStartInterpolation(0);
                        display.setInterpolationDuration(8); // exactly matches 8-tick interval for seamless 60/144fps interpolation
                        display.setTransformation(new AffineTransformation(comp, hoverRot, record.targetScale, null));
                    }
                }
                // Phase 4: Gravitational Snapback (Last 10 ticks: Accelerates with gravity back into socket)
                else if (record.ageTicks == record.maxLifetimeTicks - 10) {
                    display.setStartInterpolation(0);
                    display.setInterpolationDuration(10); // 10 ticks: natural gravitational snapback
                    display.setTransformation(new AffineTransformation(
                            new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f), null));
                    record.animationStage = 3;
                }

                // Tether Trail: Sampled exclusively on 1 in 8 anchor blocks every 4 ticks to eliminate particle lag
                if (record.transformed && ((record.pos.asLong() & 7) == 0) && record.ageTicks % 4 == 0 && record.animationStage < 3) {
                    double homeX = record.pos.getX() + 0.5;
                    double homeY = record.pos.getY() + 0.5;
                    double homeZ = record.pos.getZ() + 0.5;

                    Vector3f comp = SpatialMath.compensatePivot(record.targetDisplacement, record.targetRotation);
                    double floatX = homeX + comp.x;
                    double floatY = homeY + comp.y;
                    double floatZ = homeZ + comp.z;

                    double dx = floatX - homeX;
                    double dy = floatY - homeY;
                    double dz = floatZ - homeZ;
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    if (dist > 0.6) {
                        // Light, spaced-out starlight micro-filaments (0.85m step)
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

            if (record.ageTicks >= record.maxLifetimeTicks) {
                toRestore.add(record);
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
                    r.pos.getX() + 0.5, r.pos.getY() + 0.5, r.pos.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.05);
            world.spawnParticles(ParticleTypes.POOF,
                    r.pos.getX() + 0.5, r.pos.getY() + 0.5, r.pos.getZ() + 0.5, 1, 0.05, 0.05, 0.05, 0.01);
            if ((r.pos.asLong() & 3) == 0) {
                world.playSound(null, r.pos, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 0.75f, 1.0f);
            }
        }
        ACTIVE_RECORDS.remove(new DimensionPos(r.worldKey, r.pos));
        DisplacementPersistentState.getServerState(world).markDirty();
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
                    if (record.maxLifetimeTicks > record.ageTicks + 8) {
                        record.maxLifetimeTicks = record.ageTicks + 8;
                    }
                }
            }
        }
    }
}
