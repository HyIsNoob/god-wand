package net.godwand.spatial;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Clearable;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpatialDisplacementManager {
    private static final Map<BlockPos, DisplacedBlockRecord> ACTIVE_RECORDS = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockDisplayEntity> ACTIVE_ENTITIES = new ConcurrentHashMap<>();

    public static boolean isPositionDisplaced(BlockPos pos) {
        return ACTIVE_RECORDS.containsKey(pos.toImmutable());
    }

    public static boolean canDisplace(ServerWorld world, BlockPos pos, BlockState state) {
        BlockPos immutablePos = pos.toImmutable();
        if (state.isAir() || state.getHardness(world, immutablePos) < 0) return false;
        if (state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.END_PORTAL) || state.isOf(Blocks.BARRIER)) return false;
        return !isPositionDisplaced(immutablePos);
    }

    public static void displaceBlock(ServerWorld world, BlockPos pos, Vector3f targetDisplacement,
                                      Quaternionf rotation, int lifetime, int glowColor) {
        BlockPos immutablePos = pos.toImmutable();
        BlockState state = world.getBlockState(immutablePos);
        if (!canDisplace(world, immutablePos, state)) return;

        BlockEntity be = world.getBlockEntity(immutablePos);
        NbtCompound nbt = null;
        if (be != null) {
            nbt = be.createNbtWithIdentifyingData(world.getRegistryManager());
            Clearable.clear(be);
        }

        boolean isFull = state.isFullCube(world, immutablePos);
        DisplacedBlockRecord record = new DisplacedBlockRecord(
                immutablePos, world.getRegistryKey(), state, nbt, targetDisplacement, rotation, lifetime, isFull);

        // Replace with AIR immediately
        world.setBlockState(immutablePos, Blocks.AIR.getDefaultState(), Block.FORCE_STATE | Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);

        // Tick T0: Spawn BlockDisplayEntity at pos with Identity matrix
        BlockDisplayEntity display = new BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
        display.setPosition(immutablePos.getX(), immutablePos.getY(), immutablePos.getZ());
        display.setBlockState(state);
        display.setGlowing(true);
        display.setGlowColorOverride(glowColor);
        display.setBrightness(new Brightness(15, 15));
        display.setStartInterpolation(0);
        display.setInterpolationDuration(0);
        display.setTransformation(AffineTransformation.identity());
        
        // Spawn with identity transformation
        world.spawnEntity(display);
        record.displayEntityUuid = display.getUuid();
        ACTIVE_RECORDS.put(immutablePos, record);
        ACTIVE_ENTITIES.put(display.getUuid(), display);
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
                            comp, record.targetRotation, new Vector3f(1.0f, 1.0f, 1.0f), null));
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
                            comp, record.targetRotation, new Vector3f(1.0f, 1.0f, 1.0f), null));
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

    private static void executeTwoPassRestoration(ServerWorld world, List<DisplacedBlockRecord> records) {
        // Pass 1: Solid base blocks
        for (DisplacedBlockRecord r : records) {
            if (r.isFullCube) {
                restoreSingleBlock(world, r);
            }
        }
        // Pass 2: Attached & delicate blocks (Torches, Lanterns, Signs)
        for (DisplacedBlockRecord r : records) {
            if (!r.isFullCube) {
                restoreSingleBlock(world, r);
            }
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
        BlockDisplayEntity display = ACTIVE_ENTITIES.remove(r.displayEntityUuid);
        if (display != null) {
            display.discard();
        }
        ACTIVE_RECORDS.remove(r.pos);
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
