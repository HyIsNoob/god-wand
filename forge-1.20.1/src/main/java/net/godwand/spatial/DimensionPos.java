package net.godwand.spatial;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record DimensionPos(ResourceKey<Level> worldKey, BlockPos pos) {
    public DimensionPos {
        pos = pos.immutable();
    }
}
