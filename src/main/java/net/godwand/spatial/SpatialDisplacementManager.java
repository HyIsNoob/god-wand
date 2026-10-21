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
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
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
            record.ageTicks = 0;
            record.maxLifetimeTicks = lifetime;
            if (record.displayEntityUuid != null && record.transformed) {
                BlockDisplayEntity display = ACTIVE_ENTITIES.get(record.displayEntityUuid);
                if (display != null && display.isAlive()) {
                    display.setStartInterpolation(0);
                    display.setInterpolationDuration(5);
                    Vector3f comp = SpatialMath.compensatePivot(record.targetDisplacement, record.targetRotation);
                    display.setTransformation(new AffineTransformation(
                            comp, record.targetRotation, record.targetScale, null));
                }
            }
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
            display.setGlowing(true);
            display.setGlowColorOverride(glowColor);
            display.setBrightness(new Brightness(15, 15));
            display.setStartInterpolation(0);
            display.setInterpolationDuration(0);
            display.setTransformation(AffineTransformation.identity());

            world.spawnEntity(display);
            record.displayEntityUuid = display.getUuid();
            ACTIVE_ENTITIES.put(display.getUuid(), display);
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
            BlockDisplayEntity display = ACTIVE_ENTITIES.get(record.displayEntityUuid);

            // Tick T1 Handshake: Apply transformation on second tick
            if (record.spawnTickDelay > 0) {
                record.spawnTickDelay--;
                if (record.spawnTickDelay == 0 && display != null && display.isAlive()) {
                    display.setStartInterpolation(0);
                    display.setInterpolationDuration(8);
                    Vector3f comp = SpatialMath.compensatePivot(record.targetDisplacement, record.targetRotation);
                    display.setTransformation(new AffineTransformation(
                            comp, record.targetRotation, record.targetScale, null));
                    record.transformed = true;
                }
            } else if (record.ageTicks < record.maxLifetimeTicks - 12 && display != null) {
                // Idle Bobbing every 10 ticks
                if (record.ageTicks % 10 == 0) {
                    float bob = (float) Math.sin((record.ageTicks / 10.0) + SpatialMath.hashCoordinateToPhase(record.pos.asLong())) * 0.15f;
                    Vector3f bobbedDisp = new Vector3f(record.targetDisplacement.x, record.targetDisplacement.y + bob, record.targetDisplacement.z);
                    Vector3f comp = SpatialMath.compensatePivot(bobbedDisp, record.targetRotation);
                    display.setStartInterpolation(0);
                    display.setInterpolationDuration(10);
                    display.setTransformation(new AffineTransformation(
                            comp, record.targetRotation, record.targetScale, null));
                }
            } else if (record.ageTicks == record.maxLifetimeTicks - 10 && display != null) {
                // Return animation: interpolate back to identity (0,0,0)
                display.setStartInterpolation(0);
                display.setInterpolationDuration(10);
                display.setTransformation(new AffineTransformation(
                        new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f), null));
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
        world.setBlockState(r.pos, r.state, Block.FORCE_STATE | Block.NOTIFY_LISTENERS);
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
}
