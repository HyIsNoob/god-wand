package net.godwand.spatial;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

public class DisplacedBlockRecord {
    public enum DisplacementMode {
        PATH_WALKER,
        HYSTERESIS_AURA,
        TIMED_LIFECYCLE,
        CANOPY_VORTEX,
        CUSTOM_SCRIPTED
    }

    public final BlockPos pos;
    public final RegistryKey<World> worldKey;
    public final BlockState originalState;
    public final BlockState state;
    public final NbtCompound blockEntityNbt;
    public final NbtCompound nbt;

    public UUID displayEntityUuid;
    public int ageTicks;
    public int maxLifetimeTicks;
    public int spawnTickDelay; // 1 tick handshake delay
    public final Vector3f homePos;
    public final Vector3f baseDisplacement;
    public final Vector3f targetDisplacement;
    public final Quaternionf targetRotation;
    public final Vector3f targetScale;
    public final boolean isFullCube;
    public boolean transformed;
    public int animationStage; // 0 = SPAWN, 1 = BURST, 2 = HOVER/DYNAMIC, 3 = RETURNING
    public float hoverYaw;

    // Refactor Fields:
    public final DisplacementMode mode;
    public UUID boundPlayerUuid;
    public int restoreTick; // 0 = active, 1..15 = restoring (3-Phase Magnetic Snap)
    public Vector3f currentOffset;
    public Quaternionf currentRotation;
    public Vector3f currentScale;
    public Vec3d blastCenter;
    public double blastRadius;

    public DisplacedBlockRecord(BlockPos pos, RegistryKey<World> worldKey, BlockState state, NbtCompound nbt,
                                Vector3f targetDisplacement, Quaternionf targetRotation, Vector3f targetScale,
                                int maxLifetimeTicks, boolean isFullCube, DisplacementMode mode, UUID boundPlayerUuid) {
        this.pos = pos.toImmutable();
        this.worldKey = worldKey;
        this.originalState = state;
        this.state = state;
        this.blockEntityNbt = nbt;
        this.nbt = nbt;
        this.displayEntityUuid = null;
        this.ageTicks = 0;
        this.maxLifetimeTicks = maxLifetimeTicks;
        this.spawnTickDelay = 1;
        this.homePos = new Vector3f(this.pos.getX(), this.pos.getY(), this.pos.getZ());
        this.baseDisplacement = new Vector3f(targetDisplacement);
        this.targetDisplacement = targetDisplacement;
        this.targetRotation = targetRotation != null ? new Quaternionf(targetRotation) : new Quaternionf();
        this.targetScale = targetScale != null ? new Vector3f(targetScale) : new Vector3f(1.0f, 1.0f, 1.0f);
        this.isFullCube = isFullCube;
        this.transformed = false;
        this.animationStage = 0;
        this.hoverYaw = 0.0f;

        this.mode = mode != null ? mode : DisplacementMode.TIMED_LIFECYCLE;
        this.boundPlayerUuid = boundPlayerUuid;
        this.restoreTick = 0;
        this.currentOffset = new Vector3f(targetDisplacement);
        this.currentRotation = new Quaternionf(this.targetRotation);
        this.currentScale = new Vector3f(this.targetScale);
        this.blastCenter = null;
        this.blastRadius = 0.0;
    }

    public DisplacedBlockRecord(BlockPos pos, RegistryKey<World> worldKey, BlockState state, NbtCompound nbt,
                                Vector3f targetDisplacement, Quaternionf targetRotation, Vector3f targetScale,
                                int maxLifetimeTicks, boolean isFullCube) {
        this(pos, worldKey, state, nbt, targetDisplacement, targetRotation, targetScale, maxLifetimeTicks, isFullCube, DisplacementMode.TIMED_LIFECYCLE, null);
    }

    public DisplacedBlockRecord(BlockPos pos, RegistryKey<World> worldKey, BlockState state, NbtCompound nbt,
                                Vector3f targetDisplacement, Quaternionf targetRotation,
                                int maxLifetimeTicks, boolean isFullCube) {
        this(pos, worldKey, state, nbt, targetDisplacement, targetRotation, new Vector3f(1.0f, 1.0f, 1.0f), maxLifetimeTicks, isFullCube, DisplacementMode.TIMED_LIFECYCLE, null);
    }

    public DisplacedBlockRecord(BlockPos pos, BlockState state, NbtCompound nbt,
                                Vector3f targetDisplacement, Quaternionf targetRotation,
                                int maxLifetimeTicks, boolean isFullCube) {
        this(pos, World.OVERWORLD, state, nbt, targetDisplacement, targetRotation, new Vector3f(1.0f, 1.0f, 1.0f), maxLifetimeTicks, isFullCube, DisplacementMode.TIMED_LIFECYCLE, null);
    }

    public BlockPos pos() {
        return pos;
    }

    public BlockState originalState() {
        return originalState;
    }

    public BlockState state() {
        return state;
    }

    public NbtCompound blockEntityNbt() {
        return blockEntityNbt;
    }

    public NbtCompound nbt() {
        return nbt;
    }

    public Vec3d getCurrentOffset() {
        return new Vec3d(currentOffset.x, currentOffset.y, currentOffset.z);
    }

    public void beginRestoration() {
        if (this.restoreTick == 0) {
            this.restoreTick = 1;
            this.animationStage = 3;
        }
    }

    public void interruptRestoration() {
        if (this.restoreTick > 0) {
            this.restoreTick = 0;
            this.animationStage = 2; // Return to hovering / living aura
        }
    }

    public void resetRestoreTick() {
        this.restoreTick = 0;
    }

    public boolean isRestoring() {
        return this.restoreTick > 0;
    }
}
