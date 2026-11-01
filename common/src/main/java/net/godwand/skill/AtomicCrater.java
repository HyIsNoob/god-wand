package net.godwand.skill;

import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import it.unimi.dsi.fastutil.longs.LongComparators;

/** Coordinates are planned off-thread; world changes remain resumable, budgeted server work. */
final class AtomicCrater {
    private final BlockPos center;
    private final int radius;
    private java.util.concurrent.CompletableFuture<AtomicTerrainPlanner.Columns> pending;
    private AtomicTerrainPlanner.Columns columns;
    private int column;
    // Highest exposed block first: stopping at the cinematic deadline must not preserve entire hills.
    private final LongHeapPriorityQueue surface = new LongHeapPriorityQueue(0, LongComparators.OPPOSITE_COMPARATOR);

    AtomicCrater(BlockPos center, int radius) {
        this.center = center.immutable();
        this.radius = Math.clamp(radius, 0, AtomicSequence.MAX_RADIUS);
        if (this.radius>0) pending=AtomicTerrainPlanner.request(this.radius);
    }
    static int terrainRadius(int configuredRadius) { return Math.max(0, Math.min(configuredRadius, AtomicSequence.MAX_RADIUS)); }
    boolean finished() { return radius == 0 || columns!=null && column>=columns.offsets().length && surface.isEmpty(); }

    int carve(ServerLevel world,int budget) { return carve(world,budget,radius); }
    int carve(ServerLevel world, int budget, float expandedRadius) { return carve(world,budget,expandedRadius,Long.MAX_VALUE); }
    int carve(ServerLevel world, int budget, float expandedRadius, long deadline) {
        if (radius==0 || budget<=0 || System.nanoTime()>=deadline) return 0;
        if (columns==null) {
            if (pending==null) pending=AtomicTerrainPlanner.request(radius);
            if (pending==null || !pending.isDone()) return 0;
            columns=pending.join(); pending=null;
        }
        if (center.getY()<world.getMinBuildHeight() || center.getY()>=world.getMaxBuildHeight()) {
            column=columns.offsets().length; surface.clear(); return 0;
        }
        int bottom = Math.max(world.getMinBuildHeight(), center.getY() - AtomicSequence.CRATER_DEPTH);
        int ceiling = Math.min(world.getMaxBuildHeight()-1,center.getY()+AtomicSequence.BEAM_HEIGHT);
        int inspected=0;
        var chunks=new Long2ObjectOpenHashMap<LevelChunk>();
        // Discover the newly visible footprint before choosing the highest remaining terrain.
        while (column<columns.offsets().length && inspected<budget) {
            if ((inspected & 31)==0 && System.nanoTime()>=deadline) break;
            if(columns.distances()[column]>expandedRadius*expandedRadius)break;
            int index=column++, packed=columns.offsets()[index];
            int x=center.getX()+(short)packed,z=center.getZ()+(short)(packed>>16);
            inspected++;
            var chunk=loadedChunk(world,chunks,x,z);
            if(chunk==null)continue;
            int y=Math.min(ceiling,chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,x & 15,z & 15));
            if(y>=bottom)surface.enqueue(((long)y<<32)|(index&0xffffffffL));
        }
        var pos=new BlockPos.MutableBlockPos();
        while (!surface.isEmpty() && inspected<budget) {
            if ((inspected & 31)==0 && System.nanoTime()>=deadline) break;
            long cell=surface.firstLong();
            int index=(int)cell,y=(int)(cell>>32);
            if(columns.distances()[index]>expandedRadius*expandedRadius)break;
            surface.dequeueLong(); inspected++;
            int packed=columns.offsets()[index];
            int x=center.getX()+(short)packed,z=center.getZ()+(short)(packed>>16);
            var chunk=loadedChunk(world,chunks,x,z);
            if(chunk==null)continue;
            pos.set(x,y,z);
            var state=chunk.getBlockState(pos);
            if(SpatialDisplacementManager.canDisplace(world,pos,state))
                world.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
            if(--y>=bottom)surface.enqueue(((long)y<<32)|(index&0xffffffffL));
        }
        // shortcut: Depth remains budgeted at retraction; profile chunk batching if high terrain still cannot finish.
        return inspected;
    }
    private static LevelChunk loadedChunk(ServerLevel world, Long2ObjectOpenHashMap<LevelChunk> cache,int x,int z) {
        long key=((long)(x>>4)<<32)|((z>>4)&0xffffffffL);
        var chunk=cache.get(key);
        if(chunk==null) {
            chunk=world.getChunkSource().getChunkNow(x>>4,z>>4);
            if(chunk!=null)cache.put(key,chunk);
        }
        return chunk;
    }
}
