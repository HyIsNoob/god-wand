package net.godwand.spatial;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.UUID;

public class DisplacedBlockRecord {
    public final BlockPos pos;
    public final BlockState state;
    public final NbtCompound nbt;
    public UUID displayEntityUuid;
    public int ageTicks;
    public int maxLifetimeTicks;
    public int spawnTickDelay; // 1 tick handshake delay
    public final Vector3f homePos;
    public final Vector3f targetDisplacement;
    public final Quaternionf targetRotation;
    public final boolean isFullCube;
    public boolean transformed;

    public DisplacedBlockRecord(BlockPos pos, BlockState state, NbtCompound nbt,
                                Vector3f targetDisplacement, Quaternionf targetRotation,
                                int maxLifetimeTicks, boolean isFullCube) {
        this.pos = pos;
        this.state = state;
        this.nbt = nbt;
        this.displayEntityUuid = null;
        this.ageTicks = 0;
        this.maxLifetimeTicks = maxLifetimeTicks;
        this.spawnTickDelay = 1;
        this.homePos = new Vector3f(pos.getX(), pos.getY(), pos.getZ());
        this.targetDisplacement = targetDisplacement;
        this.targetRotation = targetRotation;
        this.isFullCube = isFullCube;
        this.transformed = false;
    }
}
