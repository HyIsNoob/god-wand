package net.godwand.skill;

import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Coordinates are planned off-thread; world changes remain resumable, budgeted server work. */
final class AtomicCrater {
    private final BlockPos center;
    private final int radius;
    private java.util.concurrent.CompletableFuture<AtomicTerrainPlanner.Columns> pending;
    private AtomicTerrainPlanner.Columns columns;
    private int column;
    private int y, bottom;
    private boolean inColumn;

    AtomicCrater(BlockPos center, int radius) {
        this.center = center.immutable();
        this.radius = Math.clamp(radius, 0, AtomicSequence.MAX_RADIUS);
        if (this.radius>0) pending=AtomicTerrainPlanner.request(this.radius);
    }
    // The terrain limit and expanding front must stay inside the visible column.
    static int terrainRadius(int configuredRadius) { return Math.max(0, Math.min(configuredRadius, AtomicSequence.MAX_RADIUS)); }
    boolean finished() { return radius == 0 || columns!=null && column>=columns.offsets().length; }

    int carve(ServerLevel world,int budget) { return carve(world,budget,radius); }
    int carve(ServerLevel world, int budget, float expandedRadius) { return carve(world,budget,expandedRadius,Long.MAX_VALUE); }
    int carve(ServerLevel world, int budget, float expandedRadius, long deadline) {
        if (radius==0 || budget<=0) return 0;
        if (columns==null) {
            if (pending==null) pending=AtomicTerrainPlanner.request(radius);
            if (pending==null || !pending.isDone()) return 0;
            columns=pending.join(); pending=null;
        }
        int inspected = 0;
        var pos = new BlockPos.MutableBlockPos();
        net.minecraft.world.level.chunk.LevelChunk chunk = null;
        while (inspected < budget && !finished()) {
            if ((inspected & 31)==0 && System.nanoTime()>=deadline) break;
            int packed=columns.offsets()[column];
            int x=center.getX()+(short)packed, z=center.getZ()+(short)(packed>>16);
            int distanceSquared=columns.distances()[column];
            if (!inColumn) {
                inspected++;
                if (center.getY()<world.getMinBuildHeight() || center.getY()>=world.getMaxBuildHeight()
                        || distanceSquared > radius * radius || distanceSquared <= 9) { column++; continue; }
                if(distanceSquared>expandedRadius*expandedRadius)break;
                chunk=world.getChunkSource().getChunkNow(x >> 4,z >> 4);
                if(chunk==null) { column++; continue; }
                y = Math.min(Math.min(world.getMaxBuildHeight()-1,center.getY()+AtomicSequence.BEAM_HEIGHT),
                        chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,x & 15,z & 15));
                bottom = Math.max(world.getMinBuildHeight(), center.getY() - AtomicSequence.BEAM_DEPTH);
                if (y < bottom) { column++; continue; }
                inColumn = true;
            } else {
                inspected++;
                // Reacquire on each resumed slice; never force-load or hold chunks between ticks.
                if(chunk==null)chunk=world.getChunkSource().getChunkNow(x >> 4,z >> 4);
                if(chunk==null) { inColumn=false; column++; continue; }
                pos.set(x, y, z);
                var state = chunk.getBlockState(pos);
                if (SpatialDisplacementManager.canDisplace(world, pos, state))
                    world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                if (--y < bottom) {
                    inColumn=false; column++;
                }
            }
        }
        return inspected;
    }
}
