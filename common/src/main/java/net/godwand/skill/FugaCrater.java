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
    private final SupernovaCraterCursor cursor;
    private boolean inColumn;
    private int y;
    FugaCrater(BlockPos center,int radius) {
        this.center=center.immutable();this.radius=Math.max(0,Math.min(64,radius));
        cursor=new SupernovaCraterCursor(this.radius);
    }
    boolean finished() {return radius==0 || cursor.finished();}
    int carve(ServerLevel world,int budget,long deadline,double impactAge) {
        if(!FugaSequence.terrainActive(impactAge) || finished() || budget<=0)return 0;
        int inspected=0;
        var pos=new BlockPos.MutableBlockPos();
        net.minecraft.world.level.chunk.LevelChunk chunk=null;
        while(inspected<budget && !finished()) {
            if((inspected&31)==0 && System.nanoTime()>=deadline)break;
            int x=center.getX()+cursor.x(),z=center.getZ()+cursor.z();
            inspected++;
            if(!inColumn) {
                if(cursor.x()*cursor.x()+cursor.z()*cursor.z()>radius*radius
                        || center.getY()<world.getMinBuildHeight() || center.getY()>=world.getMaxBuildHeight()) {cursor.advance();continue;}
                chunk=world.getChunkSource().getChunkNow(x>>4,z>>4);
                if(chunk==null) {cursor.advance();continue;}
                y=Math.min(world.getMaxBuildHeight()-1,Math.min(center.getY()+FugaSequence.COLUMN_HEIGHT,
                        chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,x&15,z&15)));
                if(y<world.getMinBuildHeight()) {cursor.advance();continue;}
                inColumn=true;
            } else {
                if(chunk==null)chunk=world.getChunkSource().getChunkNow(x>>4,z>>4);
                if(chunk==null) {inColumn=false;cursor.advance();continue;}
                pos.set(x,y,z);
                var state=chunk.getBlockState(pos);
                if(SpatialDisplacementManager.canDisplace(world,pos,state)) {
                    world.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS);
                }
                if(--y<world.getMinBuildHeight()) {inColumn=false;cursor.advance();}
            }
        }
        return inspected;
    }
}
