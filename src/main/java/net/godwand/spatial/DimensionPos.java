package net.godwand.spatial;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public record DimensionPos(RegistryKey<World> worldKey, BlockPos pos) {
    public DimensionPos {
        pos = pos.toImmutable();
    }
}
