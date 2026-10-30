package net.godwand.skill;

import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Resumable cylinder; never owns chunks or continues after the visual contracts. */
final class FugaCrater {
    private final BlockPos center;
    private final int radius;
    private java.util.concurrent.CompletableFuture<AtomicTerrainPlanner.Columns> pending;
    private AtomicTerrainPlanner.Columns columns;
    private int column;
    private boolean inColumn;
    private int y;
    FugaCrater(BlockPos center,int radius) {
        this.center=center.immutable();this.radius=Math.max(0,Math.min(64,radius));
        if(this.radius>0)pending=AtomicTerrainPlanner.request(this.radius,false);
    }
    boolean finished() {return radius==0 || columns!=null&&column>=columns.offsets().length;}
    int carve(ServerLevel world,int budget,long deadline,double impactAge) {
        if(!FugaSequence.terrainActive(impactAge) || finished() || budget<=0)return 0;
        if(columns==null) {
            if(pending==null)pending=AtomicTerrainPlanner.request(radius,false);
            if(pending==null||!pending.isDone())return 0;
            columns=pending.join();pending=null;
        }
        int inspected=0;
        var pos=new BlockPos.MutableBlockPos();
        net.minecraft.world.level.chunk.LevelChunk chunk=null;
        while(inspected<budget && !finished()) {
            if((inspected&31)==0 && System.nanoTime()>=deadline)break;
            int packed=columns.offsets()[column];
            int dx=(short)packed,dz=(short)(packed>>16);
            int x=center.getX()+dx,z=center.getZ()+dz;
            inspected++;
            if(!inColumn) {
                if(dx*dx+dz*dz>radius*radius
                        || center.getY()<world.getMinBuildHeight() || center.getY()>=world.getMaxBuildHeight()) {column++;continue;}
                chunk=world.getChunkSource().getChunkNow(x>>4,z>>4);
                if(chunk==null) {column++;continue;}
                y=Math.min(world.getMaxBuildHeight()-1,Math.min(center.getY()+FugaSequence.COLUMN_HEIGHT,
                        chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,x&15,z&15)));
                if(y<world.getMinBuildHeight()) {column++;continue;}
                inColumn=true;
            } else {
                if(chunk==null)chunk=world.getChunkSource().getChunkNow(x>>4,z>>4);
                if(chunk==null) {inColumn=false;column++;continue;}
                pos.set(x,y,z);
                var state=chunk.getBlockState(pos);
                if(SpatialDisplacementManager.canDisplace(world,pos,state)) {
                    world.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS);
                }
                if(--y<world.getMinBuildHeight()) {inColumn=false;column++;}
            }
        }
        return inspected;
    }
}
