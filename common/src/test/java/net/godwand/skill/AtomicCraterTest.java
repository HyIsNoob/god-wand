package net.godwand.skill;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.*;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AtomicCraterTest {
    private static AtomicCrater readyCrater(BlockPos center,int radius) {
        var crater=new AtomicCrater(center,radius);
        try {
            var field=AtomicCrater.class.getDeclaredField("pending"); field.setAccessible(true);
            var future=(java.util.concurrent.CompletableFuture<?>)field.get(crater);
            if(future!=null)future.get(5,java.util.concurrent.TimeUnit.SECONDS);
            return crater;
        } catch(Exception error) { throw new AssertionError("Coordinate planner did not finish",error); }
    }
    @Test void expiredTerrainSliceLeavesTheWorldUntouchedAndCanResumeNextTick() {
        var world=mock(ServerLevel.class);
        var crater=readyCrater(BlockPos.ZERO,16);
        assertEquals(0,crater.carve(world,8192,16,Long.MIN_VALUE));
        assertFalse(crater.finished());
        verifyNoInteractions(world);
    }

    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void craterRemovesWaterAndLavaButKeepsProtectedBlocks() {
        var world=mock(ServerLevel.class);
        var chunks=mock(ServerChunkCache.class);
        when(world.getChunkSource()).thenReturn(chunks);
        var loaded=mock(net.minecraft.world.level.chunk.LevelChunk.class);
        when(loaded.getHeight(any(),anyInt(),anyInt())).thenAnswer(call -> world.getHeight(call.getArgument(0),call.getArgument(1),call.getArgument(2))-1);
        when(chunks.getChunkNow(anyInt(),anyInt())).thenAnswer(call -> chunks.hasChunk(call.getArgument(0),call.getArgument(1))?loaded:null);
        when(loaded.getBlockState(any())).thenAnswer(call -> world.getBlockState(call.getArgument(0)));
        when(chunks.hasChunk(anyInt(),anyInt())).thenReturn(true);
        when(world.dimension()).thenReturn(net.minecraft.world.level.Level.OVERWORLD);
        when(world.getMinBuildHeight()).thenReturn(-64);
        when(world.getMaxBuildHeight()).thenReturn(320);
        when(world.getHeight(any(),anyInt(),anyInt())).thenReturn(72);
        var wet=net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;
        var states=new net.minecraft.world.level.block.state.BlockState[]{
                Blocks.WATER.defaultBlockState(),
                Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL,4),
                Blocks.OAK_STAIRS.defaultBlockState().setValue(wet,true),
                Blocks.SEAGRASS.defaultBlockState(),
                Blocks.LAVA.defaultBlockState(),
                Blocks.LAVA.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL,4),
                Blocks.CAMPFIRE.defaultBlockState(),
                Blocks.SOUL_CAMPFIRE.defaultBlockState(),
                Blocks.WHITE_BANNER.defaultBlockState(),
                Blocks.WHITE_WALL_BANNER.defaultBlockState(),
                Blocks.CHEST.defaultBlockState().setValue(wet,true),
                Blocks.FURNACE.defaultBlockState(),
                Blocks.BEDROCK.defaultBlockState()
        };
        when(world.getBlockState(any())).thenAnswer(call -> states[Math.floorMod(((BlockPos)call.getArgument(0)).getX(),states.length)]);
        var removed=new HashSet<Integer>();
        when(world.setBlock(any(),any(),anyInt())).thenAnswer(call -> {
            BlockPos pos=call.getArgument(0);int kind=Math.floorMod(pos.getX(),states.length);
            assertTrue(kind<12,"Only the unbreakable fixture must stay protected");
            assertTrue(pos.getX()*pos.getX()+pos.getZ()*pos.getZ()>9);
            assertTrue(((net.minecraft.world.level.block.state.BlockState)call.getArgument(1)).isAir());
            removed.add(kind);return true;
        });
        var crater=readyCrater(new BlockPos(0,70,0),8);
        for(int tick=0;tick<1000&&!crater.finished();tick++)assertTrue(crater.carve(world,73)<=73);
        assertTrue(crater.finished());
        assertEquals(java.util.Set.of(0,1,2,3,4,5,6,7,8,9,10,11),removed,"Fluids, decorations and storage blocks must all be removed");
    }

    @Test void craterResumesInsideColumnsWithABoundedBudgetAndSkipsUnloadedChunks() {
        var world=mock(ServerLevel.class);
        var chunks=mock(ServerChunkCache.class);
        when(world.getChunkSource()).thenReturn(chunks);
        var loaded=mock(net.minecraft.world.level.chunk.LevelChunk.class);
        when(loaded.getHeight(any(),anyInt(),anyInt())).thenAnswer(call -> world.getHeight(call.getArgument(0),call.getArgument(1),call.getArgument(2))-1);
        when(chunks.getChunkNow(anyInt(),anyInt())).thenAnswer(call -> chunks.hasChunk(call.getArgument(0),call.getArgument(1))?loaded:null);
        when(loaded.getBlockState(any())).thenAnswer(call -> world.getBlockState(call.getArgument(0)));
        when(chunks.hasChunk(anyInt(),anyInt())).thenAnswer(call -> (int)call.getArgument(0)>=0);
        when(world.getMinBuildHeight()).thenReturn(-64);
        when(world.getMaxBuildHeight()).thenReturn(320);
        when(world.getHeight(any(),anyInt(),anyInt())).thenReturn(81);
        var writes=new HashSet<BlockPos>();
        when(world.getBlockState(any())).thenAnswer(call -> {
            BlockPos p=call.getArgument(0);
            assertTrue(p.getX()>=0,"Read an unloaded chunk");
            return p.getZ()==0?Blocks.CHEST.defaultBlockState():Blocks.STONE.defaultBlockState();
        });
        when(world.setBlock(any(),any(),anyInt())).thenAnswer(call -> {
            BlockPos p=call.getArgument(0);
            assertTrue(p.getX()*p.getX()+p.getZ()*p.getZ()<=256);
            assertTrue(p.getX()*p.getX()+p.getZ()*p.getZ()>9,"Caster pedestal was carved");
            assertTrue(writes.add(p.immutable()),"A block was carved twice");
            return true;
        });
        var crater=readyCrater(new BlockPos(0,70,0),16);
        for(int tick=0;!crater.finished()&&tick<2000;tick++) {
            int before=writes.size();
            assertTrue(crater.carve(world,73)<=73);
            assertTrue(writes.size()-before<=73);
        }
        assertTrue(crater.finished());
        assertTrue(writes.size()>1000,"The crater must reach the outer columns");
        assertTrue(writes.stream().anyMatch(p -> p.getZ()==0),"Storage blocks in the blast must also be removed");
        long lookups=mockingDetails(chunks).getInvocations().stream().filter(call -> call.getMethod().getName().equals("getChunkNow")).count();
        assertTrue(lookups<writes.size()/5,"Chunk lookup must be shared across a column, not repeated per block");
        verify(world,never()).getChunk(anyInt(),anyInt());
        assertTrue(writes.stream().mapToInt(BlockPos::getY).min().orElseThrow()<50,"The deeper crater should extend more than twenty blocks beneath its center");
    }

    @Test void carvingWaitsForTheColumnWithoutLeavingPermanentScarBlocks() {
        var world=mock(ServerLevel.class);
        var chunks=mock(ServerChunkCache.class);
        when(world.getChunkSource()).thenReturn(chunks);
        var loaded=mock(net.minecraft.world.level.chunk.LevelChunk.class);
        when(loaded.getHeight(any(),anyInt(),anyInt())).thenAnswer(call -> world.getHeight(call.getArgument(0),call.getArgument(1),call.getArgument(2))-1);
        when(chunks.getChunkNow(anyInt(),anyInt())).thenAnswer(call -> chunks.hasChunk(call.getArgument(0),call.getArgument(1))?loaded:null);
        when(loaded.getBlockState(any())).thenAnswer(call -> world.getBlockState(call.getArgument(0)));
        when(chunks.hasChunk(anyInt(),anyInt())).thenReturn(true);
        when(world.getMinBuildHeight()).thenReturn(-64);
        when(world.getMaxBuildHeight()).thenReturn(320);
        when(world.getHeight(any(),anyInt(),anyInt())).thenReturn(100);
        when(world.getBlockState(any())).thenReturn(Blocks.STONE.defaultBlockState());
        var front=new float[]{6};
        when(world.setBlock(any(),any(),anyInt())).thenAnswer(call -> {
            BlockPos pos=call.getArgument(0);
            assertTrue(pos.getX()*pos.getX()+pos.getZ()*pos.getZ()<=front[0]*front[0]);
            net.minecraft.world.level.block.state.BlockState state=call.getArgument(1);
            assertTrue(state.isAir(),"Temporary scar visuals must not replace the foundation with permanent blocks");
            return true;
        });
        var crater=readyCrater(new BlockPos(0,70,0),16);
        for(int tick=0;tick<30;tick++)crater.carve(world,97,front[0]);
        assertFalse(crater.finished());
        front[0]=16;
        for(int tick=0;tick<2000&&!crater.finished();tick++)assertTrue(crater.carve(world,97,front[0])<=97);
        assertTrue(crater.finished());
    }

    @Test void terrainReachNeverExceedsVisibleColumnRadius() {
        assertEquals(98, AtomicCrater.terrainRadius(98));
        assertEquals(32, AtomicCrater.terrainRadius(32));
        assertEquals(0, AtomicCrater.terrainRadius(0));
        assertEquals(98, AtomicSequence.MAX_RADIUS);
    }

    @Test void zeroRadiusLeavesTheWorldUntouchedAndDamageFallsOffOutsideTheBlast() {
        var world=mock(ServerLevel.class);
        assertEquals(0,readyCrater(BlockPos.ZERO,0).carve(world,2048));
        verifyNoInteractions(world);
        assertEquals(9999,AtomicManager.damage(0));
        assertEquals(4999.5f,AtomicManager.damage(49));
        assertEquals(0,AtomicManager.damage(98));
        assertEquals(0,AtomicManager.damage(100));
    }

    @Test void primaryDamageReadsTheServerConfigWithoutChangingItsRadius() {
        var previous=net.godwand.config.GodWandConfig.SERVER;
        try {
            var custom=new net.godwand.config.GodWandConfig.Server();
            custom.atomicDamage=500;
            net.godwand.config.GodWandConfig.SERVER=custom;
            assertEquals(500,AtomicManager.damage(0)); assertEquals(250,AtomicManager.damage(49));
            assertEquals(0,AtomicManager.damage(98));
            custom.damageMultiplier=2; assertEquals(1000,AtomicManager.damage(0));
            custom.damageMultiplier=0; assertEquals(0,AtomicManager.damage(0));
            custom.damageMultiplier=1; custom.atomicDamage=0; assertEquals(0,AtomicManager.damage(10));
        } finally { net.godwand.config.GodWandConfig.SERVER=previous; }
    }

    @Test void blastsOutsideBuildHeightNeverCarveRemoteCeilingsOrTheVoid() {
        var world=mock(ServerLevel.class);
        var chunks=mock(ServerChunkCache.class);
        when(world.getChunkSource()).thenReturn(chunks);
        var loaded=mock(net.minecraft.world.level.chunk.LevelChunk.class);
        when(loaded.getHeight(any(),anyInt(),anyInt())).thenAnswer(call -> world.getHeight(call.getArgument(0),call.getArgument(1),call.getArgument(2))-1);
        when(chunks.getChunkNow(anyInt(),anyInt())).thenAnswer(call -> chunks.hasChunk(call.getArgument(0),call.getArgument(1))?loaded:null);
        when(loaded.getBlockState(any())).thenAnswer(call -> world.getBlockState(call.getArgument(0)));
        when(chunks.hasChunk(anyInt(),anyInt())).thenReturn(true);
        when(world.getMinBuildHeight()).thenReturn(-64);
        when(world.getMaxBuildHeight()).thenReturn(320);
        when(world.getHeight(any(),anyInt(),anyInt())).thenReturn(81);
        when(world.getBlockState(any())).thenThrow(new AssertionError("Out-of-height blast read terrain"));
        for(int height:new int[]{400,-100}) {
            var crater=readyCrater(new BlockPos(0,height,0),16);
            while(!crater.finished())crater.carve(world,2048);
        }
    }
}
