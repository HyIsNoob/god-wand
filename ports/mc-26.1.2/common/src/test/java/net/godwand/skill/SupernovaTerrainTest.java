package net.godwand.skill;

import net.godwand.config.GodWandConfig;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupernovaTerrainTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void preparationResumesAcrossTicksAndNeverReadsUnloadedColumns() throws Exception {
        var world = mock(ServerLevel.class);
        var chunks = mock(ServerChunkCache.class);
        when(world.getChunkSource()).thenReturn(chunks);
        when(chunks.hasChunk(anyInt(), anyInt())).thenAnswer(call -> (int)call.getArgument(0) >= 0);
        when(world.getMinY()).thenReturn(-64);
        when(world.getMaxY()).thenReturn(319);
        var columns = new java.util.HashSet<BlockPos>();
        when(world.getHeight(any(), anyInt(), anyInt())).thenAnswer(call -> {
            int x = call.getArgument(1), z = call.getArgument(2);
            assertTrue(x >= 0, "Preparation read an unloaded chunk");
            assertTrue(columns.add(new BlockPos(x, 0, z)), "A column was scanned twice");
            return 71;
        });
        when(world.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        var task = new SupernovaManager.SupernovaTask(world, null, new BlockPos(0, 70, 0), Vec3.ZERO);
        var prepare = assertDoesNotThrow(() -> SupernovaManager.class.getDeclaredMethod("prepareTerrain", SupernovaManager.SupernovaTask.class));
        prepare.setAccessible(true);
        prepare.invoke(null, task);
        assertTrue(columns.size() > 0 && columns.size() <= 64);
        int firstTick = columns.size();
        for (int tick = 1; tick < 250; tick++) {
            int before = columns.size();
            prepare.invoke(null, task);
            assertTrue(columns.size() - before <= 64);
        }
        assertTrue(columns.size() > firstTick);
        assertTrue(columns.contains(new BlockPos(55, 0, 0)), "The outer edge must be reached before peeling ends");
    }

    @Test void craterNeverWritesOutsideConfiguredRadius() throws Exception {
        var previous = GodWandConfig.SERVER;
        try {
            GodWandConfig.SERVER = new GodWandConfig.Server();
            GodWandConfig.SERVER.supernovaTerrainRadius = 16;
            var world = mock(ServerLevel.class);
            var chunks = mock(ServerChunkCache.class);
            var chunk = mock(LevelChunk.class);
            when(world.getChunkSource()).thenReturn(chunks);
            when(chunks.hasChunk(anyInt(), anyInt())).thenReturn(true);
            when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(chunk);
            when(world.getMinY()).thenReturn(64);
            when(world.getMaxY()).thenReturn(79);
            when(chunk.getHeight(any(), anyInt(), anyInt())).thenReturn(70);
            when(chunk.getBlockState(any(BlockPos.class))).thenReturn(Blocks.STONE.defaultBlockState());
            int[] writes = {0};
            when(world.setBlock(any(), any(), anyInt())).thenAnswer(call -> {
                BlockPos pos = call.getArgument(0);
                assertTrue(pos.getX() * pos.getX() + pos.getZ() * pos.getZ() <= 256,
                        "Crater wrote outside configured radius at " + pos);
                writes[0]++;
                return true;
            });
            var task = new SupernovaManager.SupernovaTask(world, null, new BlockPos(0, 70, 0), Vec3.ZERO);
            var carve = SupernovaManager.class.getDeclaredMethod("carveSphericalCraterShell", SupernovaManager.SupernovaTask.class, int.class, int.class);
            carve.setAccessible(true);
            carve.invoke(null, task, 59, 60);
            assertTrue(writes[0] > 0, "The outer shell must actually be carved");
            verify(world,never()).getChunk(anyInt(),anyInt());
            clearInvocations(world);
            when(chunks.getChunkNow(anyInt(),anyInt())).thenReturn(null);
            carve.invoke(null,task,59,60);
            verify(world,never()).setBlock(any(),any(),anyInt());
        } finally { GodWandConfig.SERVER = previous; }
    }
}
