package net.godwand.skill;

import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Resumes inside a column; even a tall column cannot consume a whole tick. */
final class AtomicCrater {
    private final BlockPos center;
    private final int radius;
    private final SupernovaCraterCursor columns;
    private int y, bottom;
    private boolean inColumn;

    AtomicCrater(BlockPos center, int radius) {
        this.center = center.immutable();
        this.radius = Math.clamp(radius, 0, AtomicSequence.MAX_RADIUS);
        columns = new SupernovaCraterCursor(this.radius);
    }
    boolean finished() { return radius == 0 || columns.finished(); }

    int carve(ServerLevel world,int budget) { return carve(world,budget,radius); }
    int carve(ServerLevel world, int budget, float expandedRadius) {
        int inspected = 0;
        var pos = new BlockPos.MutableBlockPos();
        while (inspected < budget && !finished()) {
            int x = center.getX() + columns.x(), z = center.getZ() + columns.z();
            int distanceSquared = columns.x() * columns.x() + columns.z() * columns.z();
            if (!inColumn) {
                inspected++;
                if (center.getY()<world.getMinBuildHeight() || center.getY()>=world.getMaxBuildHeight()
                        || distanceSquared > radius * radius || distanceSquared <= 9
                        || !world.getChunkSource().hasChunk(x >> 4, z >> 4)) { columns.advance(); continue; }
                if(distanceSquared>expandedRadius*expandedRadius)break;
                y = Math.min(Math.min(world.getMaxBuildHeight()-1,center.getY()+AtomicSequence.BEAM_HEIGHT),
                        world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,x,z)-1);
                bottom = Math.max(world.getMinBuildHeight(), center.getY() - (int) (AtomicSequence.CRATER_DEPTH * (1 - distanceSquared / (double)(radius * radius))));
                if (y < bottom) { columns.advance(); continue; }
                inColumn = true;
            } else {
                inspected++;
                if (!world.getChunkSource().hasChunk(x >> 4, z >> 4)) { inColumn = false; columns.advance(); continue; }
                pos.set(x, y, z);
                var state = world.getBlockState(pos);
                // Containers and ongoing spatial spells retain their state and inventories.
                if (!state.hasBlockEntity() && state.getFluidState().isEmpty() && SpatialDisplacementManager.canDisplace(world, pos, state))
                    world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                if (--y < bottom) {
                    inColumn=false; columns.advance();
                }
            }
        }
        return inspected;
    }
}
