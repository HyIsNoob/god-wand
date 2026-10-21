package net.godwand.spatial;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Clearable;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpatialDisplacementManager {
    private static final Map<BlockPos, DisplacedBlockRecord> ACTIVE_RECORDS = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockDisplayEntity> ACTIVE_ENTITIES = new ConcurrentHashMap<>();

    private static final MethodHandle SET_BLOCK_STATE;
    private static final MethodHandle SET_TRANSFORMATION;
    private static final MethodHandle SET_INTERPOLATION_DURATION;
    private static final MethodHandle SET_START_INTERPOLATION;
    private static final MethodHandle SET_BRIGHTNESS;
    private static final MethodHandle SET_GLOW_COLOR_OVERRIDE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            Method mBlockState = findMethod(BlockDisplayEntity.class, "setBlockState", BlockState.class);
            SET_BLOCK_STATE = lookup.unreflect(mBlockState);

            Method mTrans = findMethod(DisplayEntity.class, "setTransformation", AffineTransformation.class);
            SET_TRANSFORMATION = lookup.unreflect(mTrans);

            Method mDuration = findMethod(DisplayEntity.class, "setInterpolationDuration", int.class);
            SET_INTERPOLATION_DURATION = lookup.unreflect(mDuration);

            Method mStart = findMethod(DisplayEntity.class, "setStartInterpolation", int.class);
            SET_START_INTERPOLATION = lookup.unreflect(mStart);

            Method mBrightness = findMethod(DisplayEntity.class, "setBrightness", Brightness.class);
            SET_BRIGHTNESS = lookup.unreflect(mBrightness);

            Method mGlow = findMethod(DisplayEntity.class, "setGlowColorOverride", int.class);
            SET_GLOW_COLOR_OVERRIDE = lookup.unreflect(mGlow);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Method findMethod(Class<?> clazz, String yarnName, Class<?>... paramTypes) {
        try {
            Method m = clazz.getDeclaredMethod(yarnName, paramTypes);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            try {
                for (Method m : clazz.getDeclaredMethods()) {
                    if (Arrays.equals(m.getParameterTypes(), paramTypes)) {
                        m.setAccessible(true);
                        return m;
                    }
                }
            } catch (Throwable ignored) {}
            throw new RuntimeException("Could not find method " + yarnName + " on " + clazz.getName(), e);
        }
    }

    private static void setBlockState(BlockDisplayEntity display, BlockState state) {
        try {
            SET_BLOCK_STATE.invoke(display, state);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void setTransformation(BlockDisplayEntity display, AffineTransformation transformation) {
        try {
            SET_TRANSFORMATION.invoke(display, transformation);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void setInterpolationDuration(BlockDisplayEntity display, int duration) {
        try {
            SET_INTERPOLATION_DURATION.invoke(display, duration);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void setStartInterpolation(BlockDisplayEntity display, int delay) {
        try {
            SET_START_INTERPOLATION.invoke(display, delay);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void setBrightness(BlockDisplayEntity display, Brightness brightness) {
        try {
            SET_BRIGHTNESS.invoke(display, brightness);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void setGlowColorOverride(BlockDisplayEntity display, int color) {
        try {
            SET_GLOW_COLOR_OVERRIDE.invoke(display, color);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static boolean isPositionDisplaced(BlockPos pos) {
        return ACTIVE_RECORDS.containsKey(pos);
    }

    public static boolean canDisplace(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.isAir() || state.getHardness(world, pos) < 0) return false;
        if (state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.END_PORTAL) || state.isOf(Blocks.BARRIER)) return false;
        return !isPositionDisplaced(pos);
    }

    public static void displaceBlock(ServerWorld world, BlockPos pos, Vector3f targetDisplacement,
                                      Quaternionf rotation, int lifetime, int glowColor) {
        BlockState state = world.getBlockState(pos);
        if (!canDisplace(world, pos, state)) return;

        BlockEntity be = world.getBlockEntity(pos);
        NbtCompound nbt = null;
        if (be != null) {
            nbt = be.createNbtWithIdentifyingData(world.getRegistryManager());
            Clearable.clear(be);
        }

        boolean isFull = state.isFullCube(world, pos);
        DisplacedBlockRecord record = new DisplacedBlockRecord(pos, state, nbt, targetDisplacement, rotation, lifetime, isFull);

        // Replace with AIR immediately
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.FORCE_STATE | Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);

        // Tick T0: Spawn BlockDisplayEntity at pos with Identity matrix
        BlockDisplayEntity display = new BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
        display.setPosition(pos.getX(), pos.getY(), pos.getZ());
        setBlockState(display, state);
        display.setGlowing(true);
        setGlowColorOverride(display, glowColor);
        setBrightness(display, new Brightness(15, 15));
        setStartInterpolation(display, 0);
        setInterpolationDuration(display, 0);
        setTransformation(display, AffineTransformation.identity());
        
        // Spawn with identity transformation
        world.spawnEntity(display);
        record.displayEntityUuid = display.getUuid();
        ACTIVE_RECORDS.put(pos, record);
        ACTIVE_ENTITIES.put(display.getUuid(), display);
    }

    public static void tick(ServerWorld world) {
        List<DisplacedBlockRecord> toRestore = new ArrayList<>();

        for (DisplacedBlockRecord record : ACTIVE_RECORDS.values()) {
            BlockDisplayEntity display = ACTIVE_ENTITIES.get(record.displayEntityUuid);
            if (display != null && display.getWorld() != world) {
                continue;
            }

            record.ageTicks++;

            // Tick T1 Handshake: Apply transformation on second tick
            if (record.spawnTickDelay > 0) {
                record.spawnTickDelay--;
                if (record.spawnTickDelay == 0 && display != null && display.isAlive()) {
                    setStartInterpolation(display, 0);
                    setInterpolationDuration(display, 8);
                    Vector3f comp = SpatialMath.compensatePivot(record.targetDisplacement, record.targetRotation);
                    setTransformation(display, new AffineTransformation(
                            comp, record.targetRotation, new Vector3f(1.0f, 1.0f, 1.0f), null));
                    record.transformed = true;
                }
            } else if (record.ageTicks < record.maxLifetimeTicks - 12 && display != null) {
                // Idle Bobbing every 10 ticks
                if (record.ageTicks % 10 == 0) {
                    float bob = (float) Math.sin((record.ageTicks / 10.0) + SpatialMath.hashCoordinateToPhase(record.pos.asLong())) * 0.15f;
                    Vector3f bobbedDisp = new Vector3f(record.targetDisplacement.x, record.targetDisplacement.y + bob, record.targetDisplacement.z);
                    Vector3f comp = SpatialMath.compensatePivot(bobbedDisp, record.targetRotation);
                    setStartInterpolation(display, 0);
                    setInterpolationDuration(display, 10);
                    setTransformation(display, new AffineTransformation(
                            comp, record.targetRotation, new Vector3f(1.0f, 1.0f, 1.0f), null));
                }
            } else if (record.ageTicks == record.maxLifetimeTicks - 10 && display != null) {
                // Return animation: interpolate back to identity (0,0,0)
                setStartInterpolation(display, 0);
                setInterpolationDuration(display, 10);
                setTransformation(display, new AffineTransformation(
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
            BlockDisplayEntity display = ACTIVE_ENTITIES.get(r.displayEntityUuid);
            if (display == null || display.getWorld() == world) {
                matching.add(r);
            }
        }
        executeTwoPassRestoration(world, matching);
    }
}
