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
 private static FugaCrater readyCrater(BlockPos center,int radius) {
  var crater=new FugaCrater(center,radius);
  try {
   var field=FugaCrater.class.getDeclaredField("pending");field.setAccessible(true);
   var job=(java.util.concurrent.CompletableFuture<?>)field.get(crater);
   if(job!=null)job.get(5,java.util.concurrent.TimeUnit.SECONDS);
  }catch(Exception e){throw new AssertionError(e);}
  return crater;
 }
 @Test void fugaPlanIncludesCenterAndEveryPartialSliceAdvancesCircularly() {
  var plan=AtomicTerrainPlanner.plan(48,false);
  assertEquals(0,plan.distances()[0]);
  for(int i=1;i<plan.distances().length;i++)assertTrue(plan.distances()[i]>=plan.distances()[i-1]);
  for(int i=0;i<plan.offsets().length;i++) {
   int x=(short)plan.offsets()[i],z=(short)(plan.offsets()[i]>>16);
   assertEquals(x*x+z*z,plan.distances()[i]);assertTrue(x*x+z*z<=48*48);
  }
 }

 @BeforeAll static void bootstrap() {SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
 @Test void noWorldReadsOrLateWritesBeforeExplosionOrDuringRetraction() {
  var world=mock(ServerLevel.class);var crater=readyCrater(BlockPos.ZERO,24);
  for(int age:new int[]{0,71,595,10000})assertEquals(0,crater.carve(world,32768,Long.MAX_VALUE,age));
  assertEquals(0,crater.carve(world,32768,Long.MIN_VALUE,80));verifyNoInteractions(world);
 }
 @Test void spiresRiseInsideTheBowlAndFinishBeforeItIsCarved() {
  for(int i=0;i<FugaCrater.SPIRES;i++) {
   int[] s=FugaCrater.spire(48,i);
   // Tip lean included, every spire stands where the bowl will tear it out again.
   assertTrue(Math.hypot(s[0],s[1])+s[2]*.3<48*.9,"spire "+i+" outside the bowl");
   assertTrue(s[2]>=6&&s[2]<=16&&s[3]>=1&&s[3]<=2);
   assertTrue(s[4]+8<FugaSequence.ORB_TICKS-8,"spire "+i+" still rising when the crater starts");
  }
 }
 @Test void raggedBowlIsCircularAndShallowerAtTheRim() {
  assertEquals(0,FugaCrater.depth(48,34,34));assertEquals(0,FugaCrater.depth(48,0,48));
  assertTrue(FugaCrater.depth(48,0,0)>=14);assertTrue(FugaCrater.depth(48,38,0)>0);
  assertTrue(FugaCrater.depth(48,30,0)<FugaCrater.depth(48,0,0));
  var rim=new HashSet<Integer>();for(int a=0;a<64;a++){int r=0;while(FugaCrater.depth(48,(int)Math.round(Math.cos(a*.098)*(r+1)),(int)Math.round(Math.sin(a*.098)*(r+1)))>0)r++;rim.add(r);}
  assertTrue(rim.size()>2,"rim should be ragged, not a perfect circle");
 }
 @Test void bowlIncludesCenterKeepsBedrockAndNeverLoadsChunks() {
  var world=mock(ServerLevel.class);var chunks=mock(ServerChunkCache.class);var chunk=mock(LevelChunk.class);
  when(world.getChunkSource()).thenReturn(chunks);when(world.getMinBuildHeight()).thenReturn(-64);when(world.getMaxBuildHeight()).thenReturn(320);
  when(chunks.getChunkNow(anyInt(),anyInt())).thenAnswer(c->(int)c.getArgument(0)>=0?chunk:null);
  when(chunk.getHeight(any(),anyInt(),anyInt())).thenReturn(80);
  when(chunk.getBlockState(any())).thenAnswer(c->{BlockPos p=c.getArgument(0);assertTrue(p.getX()>=0);return (p.getY()==-64?Blocks.BEDROCK:Blocks.STONE).defaultBlockState();});
  var writes=new HashSet<BlockPos>();
  when(world.setBlock(any(),any(),anyInt())).thenAnswer(c->{BlockPos p=c.getArgument(0);assertTrue(p.getX()*p.getX()+p.getZ()*p.getZ()<=36);assertTrue(p.getY()>-64);assertTrue(writes.add(p.immutable()));return true;});
  var crater=readyCrater(new BlockPos(0,70,0),4);
  for(int slice=0;slice<1000&&!crater.finished();slice++)assertTrue(crater.carve(world,73,Long.MAX_VALUE,80)<=73);
  assertTrue(crater.finished());assertTrue(writes.contains(new BlockPos(0,70-FugaCrater.depth(4,0,0),0)));assertTrue(writes.contains(new BlockPos(0,80,0)));
  for(var p:writes)assertTrue(p.getY()>=69-FugaCrater.depth(4,p.getX(),p.getZ()));
  verify(world,never()).getChunk(anyInt(),anyInt());
 }
 @Test void emptyColumnsNeverReadBelowMinimumBuildHeight() {
  var world=mock(ServerLevel.class);var chunks=mock(ServerChunkCache.class);var chunk=mock(LevelChunk.class);
  when(world.getChunkSource()).thenReturn(chunks);when(world.getMinBuildHeight()).thenReturn(-64);when(world.getMaxBuildHeight()).thenReturn(320);
  when(chunks.getChunkNow(anyInt(),anyInt())).thenReturn(chunk);when(chunk.getHeight(any(),anyInt(),anyInt())).thenReturn(-65);
  var crater=readyCrater(new BlockPos(0,70,0),2);crater.carve(world,100,Long.MAX_VALUE,80);
  assertTrue(crater.finished());verify(chunk,never()).getBlockState(any());
 }
 @Test void resumedColumnSkipsAChunkThatUnloadedBetweenTicks() {
  var world=mock(ServerLevel.class);var chunks=mock(ServerChunkCache.class);var chunk=mock(LevelChunk.class);
  when(world.getChunkSource()).thenReturn(chunks);when(world.getMinBuildHeight()).thenReturn(-64);when(world.getMaxBuildHeight()).thenReturn(320);
  when(chunks.getChunkNow(anyInt(),anyInt())).thenReturn(chunk);when(chunk.getHeight(any(),anyInt(),anyInt())).thenReturn(80);
  var crater=readyCrater(new BlockPos(0,70,0),2);assertEquals(1,crater.carve(world,1,Long.MAX_VALUE,80));
  when(chunks.getChunkNow(anyInt(),anyInt())).thenReturn(null);
  crater.carve(world,100,Long.MAX_VALUE,81);verify(chunk,never()).getBlockState(any());verify(world,never()).setBlock(any(),any(),anyInt());
 }
}
