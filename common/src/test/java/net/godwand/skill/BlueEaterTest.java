package net.godwand.skill;

import net.godwand.config.GodWandConfig;
import net.godwand.spatial.SpatialDisplacementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlueEaterTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }

    ServerLevel world;
    LevelChunk chunk;
    ServerChunkCache chunks;
    MockedStatic<SpatialDisplacementManager> displacement;
    final List<BlockPos> eatenAt = new ArrayList<>();
    final List<BlueEater.Eaten> live = new ArrayList<>();
    int shown;
    GodWandConfig.Server previous;
    static final Vec3 CENTER = new Vec3(0.5, 64.5, 0.5);

    @BeforeEach void setUp() {
        previous = GodWandConfig.SERVER; GodWandConfig.SERVER = new GodWandConfig.Server();
        world = mock(ServerLevel.class);
        chunks = mock(ServerChunkCache.class);
        chunk = mock(LevelChunk.class);
        when(world.getChunkSource()).thenReturn(chunks);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(chunk);
        when(world.getMinBuildHeight()).thenReturn(-64);
        when(world.getMaxBuildHeight()).thenReturn(320);
        when(chunk.getBlockState(any(BlockPos.class))).thenReturn(Blocks.STONE.defaultBlockState());
        when(world.setBlock(any(BlockPos.class), any(BlockState.class), anyInt())).thenAnswer(i -> { eatenAt.add(((BlockPos) i.getArgument(0)).immutable()); return true; });
        displacement = mockStatic(SpatialDisplacementManager.class);
        displacement.when(() -> SpatialDisplacementManager.canDisplace(any(), any(), any())).thenReturn(true);
        BlueEater.shower = (level, pos, state) -> { shown++; return mock(Display.BlockDisplay.class); };
    }
    @AfterEach void tearDown() { displacement.close(); GodWandConfig.SERVER = previous; }

    long later() { return System.nanoTime() + 1_000_000_000L; }

    @Test void respectsBudget() {
        int used = BlueEater.eat(world, CENTER, 5, 5, later(), live);
        assertTrue(used <= 5);
        assertTrue(eatenAt.size() <= 5);
    }

    @Test void eatsAFewBlocksPerTickNearestFirst() {
        BlueEater.eat(world, CENTER, 5, 32768, later(), live);
        assertEquals(BlueEater.EAT_PER_TICK, eatenAt.size());
        assertEquals(new BlockPos(0, 64, 0), eatenAt.get(0), "the core goes first");
        for (BlockPos p : eatenAt) assertTrue(Math.sqrt(p.distToCenterSqr(CENTER)) <= 2.0, "nearest cells first: " + p);
    }

    @Test void skipsUnbreakableAndProtected() {
        displacement.when(() -> SpatialDisplacementManager.canDisplace(any(), any(), any())).thenReturn(false);
        BlueEater.eat(world, CENTER, 5, 32768, later(), live);
        assertTrue(eatenAt.isEmpty());
    }

    @Test void skipsAir() {
        when(chunk.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
        assertTrue(BlueEater.eat(world, CENTER, 5, 32768, later(), live) > 0);
        assertTrue(eatenAt.isEmpty());
    }

    @Test void skipsUnloadedChunks() {
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(null);
        BlueEater.eat(world, CENTER, 5, 32768, later(), live);
        assertTrue(eatenAt.isEmpty());
    }

    @Test void capsDisplaysAt48() {
        for (int i = 0; i < BlueEater.MAX_DISPLAYS; i++) live.add(new BlueEater.Eaten(mock(Display.BlockDisplay.class), CENTER));
        BlueEater.eat(world, CENTER, 5, 32768, later(), live);
        assertEquals(0, shown);
        assertEquals(BlueEater.EAT_PER_TICK, eatenAt.size(), "blocks are still eaten without a display");
    }

    @Test void configOffDoesNothing() {
        GodWandConfig.SERVER.blueTerrainDamage = false;
        assertEquals(0, BlueEater.eat(world, CENTER, 5, 32768, later(), live));
        assertTrue(eatenAt.isEmpty());
    }

    @Test void displaysRetireAfterTheirFlight() {
        var display = mock(Display.BlockDisplay.class);
        live.add(new BlueEater.Eaten(display, new Vec3(3, 64, 0)));
        for (int i = 0; i < BlueEater.FLIGHT_TICKS; i++) BlueEater.animate(live, CENTER);
        assertTrue(live.isEmpty());
        verify(display).discard();
    }
}
