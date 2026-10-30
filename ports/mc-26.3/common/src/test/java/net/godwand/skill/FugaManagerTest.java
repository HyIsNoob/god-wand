package net.godwand.skill;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class FugaManagerTest {
 @Test void diagonalCannotTunnelThroughANarrowUnloadedChunkCorner() {
  var world=mock(ServerLevel.class);var chunks=mock(ServerChunkCache.class);var chunk=mock(LevelChunk.class);
  when(world.getChunkSource()).thenReturn(chunks);when(world.isInWorldBounds(any())).thenReturn(true);
  when(chunks.getChunkNow(anyInt(),anyInt())).thenAnswer(c->(int)c.getArgument(0)==1 && (int)c.getArgument(1)==0?null:chunk);
  Vec3 from=new Vec3(15,70,14.99999),to=new Vec3(17,70,16.99999);
  Vec3 stop=FugaManager.loadedEndpoint(world,from,to);
  assertEquals(16,stop.x,1e-7);assertTrue(stop.x<16);assertTrue(stop.z<16);
 }
 @Test void arrowStopsAtNearestLoadedBoundaryInBothDirections() {
  var world=mock(ServerLevel.class);var chunks=mock(ServerChunkCache.class);var chunk=mock(LevelChunk.class);
  when(world.getChunkSource()).thenReturn(chunks);when(world.isInWorldBounds(any())).thenReturn(true);
  when(chunks.getChunkNow(anyInt(),anyInt())).thenAnswer(c->(int)c.getArgument(0)==0?chunk:null);
  var positive=FugaManager.loadedEndpoint(world,new Vec3(14,70,5),new Vec3(18,70,5));
  assertEquals(16,positive.x,1e-7);assertTrue(positive.x<16);
  var negative=FugaManager.loadedEndpoint(world,new Vec3(2,70,5),new Vec3(-2,70,5));
  assertEquals(0,negative.x,1e-7);assertTrue(negative.x>=0);
  verify(world,never()).getChunk(anyInt(),anyInt());
 }
}
