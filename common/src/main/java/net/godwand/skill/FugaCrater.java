package net.godwand.skill;

import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Resumable bowl with a ragged circular rim; never owns chunks or continues after the visual contracts. */
final class FugaCrater {
    private final BlockPos center;
    private final int radius;
    private java.util.concurrent.CompletableFuture<AtomicTerrainPlanner.Columns> pending;
    private AtomicTerrainPlanner.Columns columns;
    private int column;
    private boolean inColumn, blastColumn;
    private int y, bottom;
    FugaCrater(BlockPos center,int radius) {
        this.center=center.immutable();this.radius=Math.max(0,Math.min(64,radius));
        if(this.radius>0)pending=AtomicTerrainPlanner.request(reach(this.radius),false);
    }
    boolean finished() {return radius==0 || columns!=null&&column>=columns.offsets().length;}
    /** Blocks removed below the impact surface; 0 outside the ragged rim. */
    static int depth(int radius,int dx,int dz) {
        double angle=Math.atan2(dz,dx);
        double rim=radius*(.90+.05*Math.sin(angle*5+1.3)+.035*Math.sin(angle*11+.4)+.015*Math.sin(angle*23));
        double r=Math.sqrt(dx*dx+dz*dz)/rim;
        if(r>1)return 0;
        double grain=((dx*73856093^dz*19349663)&255)/255.0;
        return 1+(int)(radius*.6*Math.sqrt(1-r*r)*(.86+.14*grain));
    }
    /** The blast ring past the rim: trees and plants are blown away and the ground is scorched, terrain stays. */
    static int reach(int radius) {return Math.min(AtomicSequence.MAX_RADIUS,radius*3/2);}
    static boolean blast(int radius,int dx,int dz) {return depth(radius,dx,dz)==0 && dx*dx+dz*dz<=reach(radius)*reach(radius);}
    /** Blocks the blast ring gouges out of the ground: ragged, 1-4 deep near the rim and fading toward its edge. */
    static int scour(int radius,int dx,int dz) {
        double out=(Math.sqrt(dx*dx+dz*dz)-radius*.9)/Math.max(1,reach(radius)-radius*.9);
        double grain=((dx*19349663^dz*83492791)&255)/255.0;
        return grain<.2?0:(int)Math.round(Math.max(0,1-out)*4*grain);
    }
    int carve(ServerLevel world,int budget,long deadline,double impactAge) {
        if(!FugaSequence.terrainActive(impactAge) || finished() || budget<=0)return 0;
        if(columns==null) {
            if(pending==null)pending=AtomicTerrainPlanner.request(reach(radius),false);
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
                int depth=depth(radius,dx,dz);
                blastColumn=depth==0&&blast(radius,dx,dz);
                if(depth==0&&!blastColumn
                        || center.getY()<world.getMinBuildHeight() || center.getY()>=world.getMaxBuildHeight()) {column++;continue;}
                chunk=world.getChunkSource().getChunkNow(x>>4,z>>4);
                if(chunk==null) {column++;continue;}
                y=Math.min(world.getMaxBuildHeight()-1,Math.min(center.getY()+FugaSequence.COLUMN_HEIGHT,
                        chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,x&15,z&15)));
                bottom=Math.max(world.getMinBuildHeight(),center.getY()-(blastColumn?12:depth));
                if(y<bottom) {column++;continue;}
                inColumn=true;
            } else {
                if(chunk==null)chunk=world.getChunkSource().getChunkNow(x>>4,z>>4);
                if(chunk==null) {inColumn=false;column++;continue;}
                pos.set(x,y,z);
                var state=chunk.getBlockState(pos);
                if(blastColumn) {
                    // Strip vegetation down to the first ground block, scorch it and stop; fluids end the column untouched.
                    boolean plant=state.is(net.minecraft.tags.BlockTags.LOGS)||state.is(net.minecraft.tags.BlockTags.LEAVES)||state.canBeReplaced();
                    if(!state.isAir() && (!plant || !state.getFluidState().isEmpty())) {
                        // The ground itself is torn up a few blocks, then carved like the bowl and scorched.
                        int gouge=state.getFluidState().isEmpty()?scour(radius,dx,dz):0;
                        if(gouge>0) { blastColumn=false; bottom=Math.max(bottom,y-gouge+1); continue; }
                        // Scorch thins out toward the edge of the blast ring: fewer writes, a ragged burn line.
                        double out=(Math.sqrt(dx*dx+dz*dz)-radius*.9)/Math.max(1,reach(radius)-radius*.9);
                        if(state.getFluidState().isEmpty() && ((dx*73856093^dz*19349663)&255)/255.0>out)scorch(world,chunk,pos,dx,dz);
                        inColumn=false;column++;continue;
                    }
                    if(!state.isAir() && SpatialDisplacementManager.canDisplace(world,pos,state))
                        world.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS);
                    if(--y<bottom) {inColumn=false;column++;}
                    continue;
                }
                if(SpatialDisplacementManager.canDisplace(world,pos,state)) {
                    world.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS);
                }
                if(--y<bottom) {inColumn=false;column++;scorch(world,chunk,pos.set(x,y,z),dx,dz);}
            }
        }
        return inspected;
    }
    static final int SPIRES=16;
    private final int[] raised=new int[SPIRES];
    /** Spire {@code i}: offset from the impact, final height, base radius and the impact tick it starts rising at. */
    static int[] spire(int radius,int i) {
        int h=i*374761393+radius*668265263;h=(h^(h>>>13))*1274126177;h^=h>>>16;
        double a=(h&1023)/1023.0*Math.PI*2,r=radius*(.25+.5*((h>>>10)&255)/255.0);
        return new int[]{(int)Math.round(Math.cos(a)*r),(int)Math.round(Math.sin(a)*r),6+((h>>>18)&15)*2/3,1+((h>>>22)&1),12+((h>>>23)&31)};
    }
    /** As the mass bears down the ground gives way: jagged rock spires thrust up inside the future bowl, layer by layer,
     *  leaning outward; the crater then tears them out. Loaded chunks only, protected blocks respected, budgeted. */
    int upheave(ServerLevel world,int budget,double impactAge) {
        if(radius<8||budget<=0)return 0;
        int used=0;
        var pos=new BlockPos.MutableBlockPos();
        for(int i=0;i<SPIRES&&used<budget;i++) {
            int[] s=spire(radius,i);
            int target=(int)Math.floor(s[2]*Math.min(1,Math.max(0,(impactAge-s[4])/8.0)));
            int x0=center.getX()+s[0],z0=center.getZ()+s[1];
            var chunk=world.getChunkSource().getChunkNow(x0>>4,z0>>4);
            if(chunk==null) {raised[i]=s[2];continue;}
            int ground=chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,x0&15,z0&15);
            double out=Math.atan2(s[1],s[0]);
            while(raised[i]<target&&used<budget) {
                int layer=raised[i]++,y=ground+layer;
                if(world.isOutsideBuildHeight(y))break;
                // Tapers toward the tip and leans away from the impact.
                double w=s[3]*(1-layer/(double)s[2])+.6,lean=layer*.3;
                int cx=x0+(int)Math.round(Math.cos(out)*lean),cz=z0+(int)Math.round(Math.sin(out)*lean);
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
                    if(dx*dx+dz*dz>w*w)continue;
                    pos.set(cx+dx,y,cz+dz);used++;
                    var at=world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
                    if(at==null)continue;
                    var state=at.getBlockState(pos);
                    if(!state.canBeReplaced()||!SpatialDisplacementManager.canDisplace(world,pos,state))continue;
                    int roll=((cx+dx)*73856093^y*19349663^(cz+dz)*83492791)&15;
                    var rock=roll<2?Blocks.MAGMA_BLOCK:roll<9?Blocks.BLACKSTONE:Blocks.BASALT;
                    world.setBlock(pos,rock.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
        return used;
    }
    // Static scorched floor instead of fire blocks: no spread, ticking or lingering server work.
    private void scorch(ServerLevel world,net.minecraft.world.level.chunk.LevelChunk chunk,BlockPos.MutableBlockPos pos,int dx,int dz) {
        if(pos.getY()<world.getMinBuildHeight())return;
        var state=chunk.getBlockState(pos);
        if(state.isAir() || !SpatialDisplacementManager.canDisplace(world,pos,state))return;
        int roll=dx*374761393+dz*668265263;roll=(roll^(roll>>>13))*1274126177;roll=(roll^(roll>>>16))&127;
        var burnt=roll<13?Blocks.MAGMA_BLOCK:roll<52?Blocks.BLACKSTONE:roll<70?Blocks.BASALT:null;
        if(burnt!=null)world.setBlock(pos,burnt.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS);
    }
}
