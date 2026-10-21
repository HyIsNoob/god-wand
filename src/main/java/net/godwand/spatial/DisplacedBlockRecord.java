package net.godwand.spatial;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.UUID;

public class DisplacedBlockRecord {
    public final BlockPos pos;
    public final RegistryKey<World> worldKey;
    public final BlockState state;
    public final NbtCompound nbt;
    public UUID displayEntityUuid;
    public int ageTicks;
    public int maxLifetimeTicks;
    public int spawnTickDelay; // 1 tick handshake delay
    public final Vector3f homePos;
    public final Vector3f targetDisplacement;
    public final Quaternionf targetRotation;
    public final Vector3f targetScale;
    public final boolean isFullCube;
    public boolean transformed;
    public int animationStage; // 0 = SPAWN, 1 = BURST, 2 = SETTLE, 3 = RETURNING
    public float hoverYaw;

    public DisplacedBlockRecord(BlockPos pos, RegistryKey<World> worldKey, BlockState state, NbtCompound nbt,
                                Vector3f targetDisplacement, Quaternionf targetRotation, Vector3f targetScale,
                                int maxLifetimeTicks, boolean isFullCube) {
        this.pos = pos.toImmutable();
        this.worldKey = worldKey;
        this.state = state;
        this.nbt = nbt;
        this.displayEntityUuid = null;
        this.ageTicks = 0;
        this.maxLifetimeTicks = maxLifetimeTicks;
        this.spawnTickDelay = 1;
        this.homePos = new Vector3f(this.pos.getX(), this.pos.getY(), this.pos.getZ());
        this.targetDisplacement = targetDisplacement;
        this.targetRotation = targetRotation;
        this.targetScale = targetScale != null ? targetScale : new Vector3f(1.0f, 1.0f, 1.0f);
        this.isFullCube = isFullCube;
        this.transformed = false;
        this.animationStage = 0;
        this.hoverYaw = 0.0f;
    }

    public DisplacedBlockRecord(BlockPos pos, RegistryKey<World> worldKey, BlockState state, NbtCompound nbt,
                                Vector3f targetDisplacement, Quaternionf targetRotation,
                                int maxLifetimeTicks, boolean isFullCube) {
        this(pos, worldKey, state, nbt, targetDisplacement, targetRotation, new Vector3f(1.0f, 1.0f, 1.0f), maxLifetimeTicks, isFullCube);
    }

    public DisplacedBlockRecord(BlockPos pos, BlockState state, NbtCompound nbt,
                                Vector3f targetDisplacement, Quaternionf targetRotation,
                                int maxLifetimeTicks, boolean isFullCube) {
        this(pos, World.OVERWORLD, state, nbt, targetDisplacement, targetRotation, new Vector3f(1.0f, 1.0f, 1.0f), maxLifetimeTicks, isFullCube);
    }
}
