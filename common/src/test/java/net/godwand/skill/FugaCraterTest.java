package net.godwand.skill;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.*;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FugaCraterTest {
 @BeforeAll static void bootstrap() {SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
 @Test void noWorldReadsOrLateWritesBeforeExplosionOrDuringRetraction() {
  var world=mock(ServerLevel.class);var crater=new FugaCrater(BlockPos.ZERO,24);
  for(int age:new int[]{0,39,120,159,160,10000})assertEquals(0,crater.carve(world,32768,Long.MAX_VALUE,age));
  assertEquals(0,crater.carve(world,32768,Long.MIN_VALUE,40));verifyNoInteractions(world);
 }
 @Test void deepCylinderIncludesCenterKeepsBedrockAndNeverLoadsChunks() {
  var world=mock(ServerLevel.class);var chunks=mock(ServerChunkCache.class);var chunk=mock(LevelChunk.class);
  when(world.getChunkSource()).thenReturn(chunks);when(world.getMinBuildHeight()).thenReturn(-64);when(world.getMaxBuildHeight()).thenReturn(320);
  when(chunks.getChunkNow(anyInt(),anyInt())).thenAnswer(c->(int)c.getArgument(0)>=0?chunk:null);
  when(chunk.getHeight(any(),anyInt(),anyInt())).thenReturn(80);
  when(chunk.getBlockState(any())).thenAnswer(c->{BlockPos p=c.getArgument(0);assertTrue(p.getX()>=0);return (p.getY()==-64?Blocks.BEDROCK:Blocks.STONE).defaultBlockState();});
  var writes=new HashSet<BlockPos>();
  when(world.setBlock(any(),any(),anyInt())).thenAnswer(c->{BlockPos p=c.getArgument(0);assertTrue(p.getX()*p.getX()+p.getZ()*p.getZ()<=16);assertTrue(p.getY()>-64);assertTrue(writes.add(p.immutable()));return true;});
  var crater=new FugaCrater(new BlockPos(0,70,0),4);
  for(int slice=0;slice<1000&&!crater.finished();slice++)assertTrue(crater.carve(world,73,Long.MAX_VALUE,40)<=73);
  assertTrue(crater.finished());assertTrue(writes.contains(new BlockPos(0,-63,0)));assertTrue(writes.contains(new BlockPos(0,80,0)));
  verify(world,never()).getChunk(anyInt(),anyInt());
 }
 @Test void emptyColumnsNeverReadBelowMinimumBuildHeight() {
  var world=mock(ServerLevel.class);var chunks=mock(ServerChunkCache.class);var chunk=mock(LevelChunk.class);
  when(world.getChunkSource()).thenReturn(chunks);when(world.getMinBuildHeight()).thenReturn(-64);when(world.getMaxBuildHeight()).thenReturn(320);
  when(chunks.getChunkNow(anyInt(),anyInt())).thenReturn(chunk);when(chunk.getHeight(any(),anyInt(),anyInt())).thenReturn(-65);
  var crater=new FugaCrater(new BlockPos(0,70,0),2);crater.carve(world,100,Long.MAX_VALUE,40);
  assertTrue(crater.finished());verify(chunk,never()).getBlockState(any());
 }
 @Test void resumedColumnSkipsAChunkThatUnloadedBetweenTicks() {
  var world=mock(ServerLevel.class);var chunks=mock(ServerChunkCache.class);var chunk=mock(LevelChunk.class);
  when(world.getChunkSource()).thenReturn(chunks);when(world.getMinBuildHeight()).thenReturn(-64);when(world.getMaxBuildHeight()).thenReturn(320);
  when(chunks.getChunkNow(anyInt(),anyInt())).thenReturn(chunk);when(chunk.getHeight(any(),anyInt(),anyInt())).thenReturn(80);
  var crater=new FugaCrater(new BlockPos(0,70,0),2);assertEquals(1,crater.carve(world,1,Long.MAX_VALUE,40));
  when(chunks.getChunkNow(anyInt(),anyInt())).thenReturn(null);
  crater.carve(world,100,Long.MAX_VALUE,41);verify(chunk,never()).getBlockState(any());verify(world,never()).setBlock(any(),any(),anyInt());
 }
}
