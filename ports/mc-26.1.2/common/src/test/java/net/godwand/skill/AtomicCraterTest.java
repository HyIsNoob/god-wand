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
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void craterRemovesWaterAndWaterloggedBlocksButKeepsProtectedBlocksAndLava() {
        var world=mock(ServerLevel.class);
        var chunks=mock(ServerChunkCache.class);
        when(world.getChunkSource()).thenReturn(chunks);
        when(chunks.hasChunk(anyInt(),anyInt())).thenReturn(true);
        when(world.dimension()).thenReturn(net.minecraft.world.level.Level.OVERWORLD);
        when(world.getMinY()).thenReturn(-64);
        when(world.getMaxY()).thenReturn(319);
        when(world.getHeight(any(),anyInt(),anyInt())).thenReturn(72);
        var wet=net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;
        var states=new net.minecraft.world.level.block.state.BlockState[]{
                Blocks.WATER.defaultBlockState(),
                Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL,4),
                Blocks.OAK_STAIRS.defaultBlockState().setValue(wet,true),
                Blocks.SEAGRASS.defaultBlockState(),
                Blocks.LAVA.defaultBlockState(),
                Blocks.CHEST.defaultBlockState().setValue(wet,true),
                Blocks.BEDROCK.defaultBlockState()
        };
        when(world.getBlockState(any())).thenAnswer(call -> states[Math.floorMod(((BlockPos)call.getArgument(0)).getX(),7)]);
        var removed=new HashSet<Integer>();
        when(world.setBlock(any(),any(),anyInt())).thenAnswer(call -> {
            BlockPos pos=call.getArgument(0);int kind=Math.floorMod(pos.getX(),7);
            assertTrue(kind<4,"Lava, inventories and unbreakable blocks must stay protected");
            assertTrue(pos.getX()*pos.getX()+pos.getZ()*pos.getZ()>9);
            assertTrue(((net.minecraft.world.level.block.state.BlockState)call.getArgument(1)).isAir());
            removed.add(kind);return true;
        });
        var crater=new AtomicCrater(new BlockPos(0,70,0),8);
        for(int tick=0;tick<1000&&!crater.finished();tick++)assertTrue(crater.carve(world,73)<=73);
        assertTrue(crater.finished());
        assertEquals(java.util.Set.of(0,1,2,3),removed,"Source water, flowing water, waterlogged blocks and underwater plants must all be removed");
    }

    @Test void craterResumesInsideColumnsWithABoundedBudgetAndSkipsUnloadedChunks() {
        var world=mock(ServerLevel.class);
        var chunks=mock(ServerChunkCache.class);
        when(world.getChunkSource()).thenReturn(chunks);
        when(chunks.hasChunk(anyInt(),anyInt())).thenAnswer(call -> (int)call.getArgument(0)>=0);
        when(world.getMinY()).thenReturn(-64);
        when(world.getMaxY()).thenReturn(319);
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
            assertNotEquals(0,p.getZ(),"A container was destroyed");
            assertTrue(writes.add(p.immutable()),"A block was carved twice");
            return true;
        });
        var crater=new AtomicCrater(new BlockPos(0,70,0),16);
        for(int tick=0;!crater.finished()&&tick<2000;tick++) {
            int before=writes.size();
            assertTrue(crater.carve(world,73)<=73);
            assertTrue(writes.size()-before<=73);
        }
        assertTrue(crater.finished());
        assertTrue(writes.size()>1000,"The crater must reach the outer columns");
        assertTrue(writes.stream().mapToInt(BlockPos::getY).min().orElseThrow()<50,"The deeper crater should extend more than twenty blocks beneath its center");
    }

    @Test void carvingWaitsForTheColumnWithoutLeavingPermanentScarBlocks() {
        var world=mock(ServerLevel.class);
        var chunks=mock(ServerChunkCache.class);
        when(world.getChunkSource()).thenReturn(chunks);
        when(chunks.hasChunk(anyInt(),anyInt())).thenReturn(true);
        when(world.getMinY()).thenReturn(-64);
        when(world.getMaxY()).thenReturn(319);
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
        var crater=new AtomicCrater(new BlockPos(0,70,0),16);
        for(int tick=0;tick<30;tick++)crater.carve(world,97,front[0]);
        assertFalse(crater.finished());
        front[0]=16;
        for(int tick=0;tick<2000&&!crater.finished();tick++)assertTrue(crater.carve(world,97,front[0])<=97);
        assertTrue(crater.finished());
    }

    @Test void zeroRadiusLeavesTheWorldUntouchedAndDamageFallsOffOutsideTheBlast() {
        var world=mock(ServerLevel.class);
        assertEquals(0,new AtomicCrater(BlockPos.ZERO,0).carve(world,2048));
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
        when(chunks.hasChunk(anyInt(),anyInt())).thenReturn(true);
        when(world.getMinY()).thenReturn(-64);
        when(world.getMaxY()).thenReturn(319);
        when(world.getHeight(any(),anyInt(),anyInt())).thenReturn(81);
        when(world.getBlockState(any())).thenThrow(new AssertionError("Out-of-height blast read terrain"));
        for(int height:new int[]{400,-100}) {
            var crater=new AtomicCrater(new BlockPos(0,height,0),16);
            while(!crater.finished())crater.carve(world,2048);
        }
    }
    @Test void inclusiveBuildCeilingRemainsCarvableWithoutWritingAboveIt() {
        var world=mock(ServerLevel.class);
        var chunks=mock(ServerChunkCache.class);
        when(world.getChunkSource()).thenReturn(chunks);
        when(chunks.hasChunk(anyInt(),anyInt())).thenReturn(true);
        when(world.dimension()).thenReturn(net.minecraft.world.level.Level.OVERWORLD);
        when(world.getMinY()).thenReturn(-64);
        when(world.getMaxY()).thenReturn(319);
        when(world.getHeight(any(),anyInt(),anyInt())).thenReturn(320);
        when(world.getBlockState(any())).thenReturn(Blocks.STONE.defaultBlockState());
        var writes=new HashSet<BlockPos>();
        when(world.setBlock(any(),any(),anyInt())).thenAnswer(call -> {
            BlockPos pos=call.getArgument(0);
            assertTrue(pos.getY()>=-64 && pos.getY()<=319);
            writes.add(pos.immutable()); return true;
        });
        var crater=new AtomicCrater(new BlockPos(0,319,0),4);
        for(int tick=0;tick<100&&!crater.finished();tick++)crater.carve(world,128);
        assertTrue(crater.finished());
        assertTrue(writes.stream().anyMatch(pos -> pos.getY()==319), "26.3's inclusive ceiling must not lose its top block");
    }

}
