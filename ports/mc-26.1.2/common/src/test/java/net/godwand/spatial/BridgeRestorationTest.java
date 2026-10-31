package net.godwand.spatial;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BridgeRestorationTest {
    private final BlockPos source = new BlockPos(0, 70, 0), target = new BlockPos(160, 70, 0);
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private ServerLevel world;
    private ServerChunkCache chunks;
    private DisplacedBlockRecord record;
    private final UUID owner = UUID.randomUUID();

    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @BeforeEach void setup() {
        SpatialDisplacementManager.clearRuntimeState();
        world = mock(ServerLevel.class);
        chunks = mock(ServerChunkCache.class);
        var storage = mock(SavedDataStorage.class);
        when(world.dimension()).thenReturn(Level.OVERWORLD);
        when(world.getChunkSource()).thenReturn(chunks);
        when(world.getDataStorage()).thenReturn(storage);
        when(storage.computeIfAbsent(any())).thenReturn(new DisplacementPersistentState());
        when(chunks.hasChunk(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockState(any())).thenAnswer(call -> blocks.getOrDefault(call.getArgument(0), Blocks.AIR.defaultBlockState()));
        when(world.removeBlock(any(), eq(false))).thenAnswer(call -> blocks.remove(call.getArgument(0)) != null);
        when(world.setBlock(any(), any(), anyInt())).thenAnswer(call -> {
            blocks.put(((BlockPos)call.getArgument(0)).immutable(), call.getArgument(1));
            return true;
        });
        blocks.put(target, Blocks.STONE.defaultBlockState());
        record = new DisplacedBlockRecord(source, Level.OVERWORLD, Blocks.STONE.defaultBlockState(), null,
                new Vector3f(), new Quaternionf(), 100, true);
        record.boundPlayerUuid = owner;
        SpatialDisplacementManager.recoverBridge(record);
        SpatialDisplacementManager.moveBridge(record, target);
    }

    @AfterEach void cleanup() { SpatialDisplacementManager.clearRuntimeState(); }

    @Test void unloadedDestinationKeepsJournalAndDoesNotRestoreSource() {
        when(chunks.hasChunk(10, 0)).thenReturn(false);
        SpatialDisplacementManager.restoreAllForPlayer(owner, world);
        assertFalse(blocks.containsKey(source));
        assertEquals(Blocks.STONE.defaultBlockState(), blocks.get(target));
        assertSame(record, SpatialDisplacementManager.getActiveRecord(Level.OVERWORLD, source));
        assertEquals(target, record.bridgeDestination);
    }

    @Test void unloadedSourceKeepsDestinationUntilBothChunksCanBeProcessed() {
        when(chunks.hasChunk(0, 0)).thenReturn(false);
        SpatialDisplacementManager.restoreAllForPlayer(owner, world);
        assertFalse(blocks.containsKey(source));
        assertTrue(blocks.containsKey(target));
        assertEquals(target, record.bridgeDestination);
    }

    @Test void loadedBridgeReturnsExactlyOneBlockAndRemovesJournal() {
        SpatialDisplacementManager.restoreAllForPlayer(owner, world);
        assertEquals(Blocks.STONE.defaultBlockState(), blocks.get(source));
        assertFalse(blocks.containsKey(target));
        assertNull(record.bridgeDestination);
        assertEquals(0, SpatialDisplacementManager.getActiveRecordCount());
    }

    @Test void minedBridgeDoesNotRecreateItsBorrowedSourceOnLogout() {
        blocks.remove(target);
        SpatialDisplacementManager.restoreAllForPlayer(owner, world);
        assertFalse(blocks.containsKey(source));
        assertEquals(0, SpatialDisplacementManager.getActiveRecordCount());
    }

    @Test void failedDestinationRemovalKeepsJournalAndSourceMissing() {
        doReturn(false).when(world).removeBlock(target, false);
        SpatialDisplacementManager.restoreAllForPlayer(owner, world);
        assertFalse(blocks.containsKey(source));
        assertSame(record, SpatialDisplacementManager.getActiveRecord(Level.OVERWORLD, source));
        assertEquals(target, record.bridgeDestination);
    }

    @Test void deferredBridgeSurvivesSavingAndRuntimeReset() {
        when(chunks.hasChunk(10, 0)).thenReturn(false);
        SpatialDisplacementManager.restoreAllForPlayer(owner, world);
        var saved = new DisplacementPersistentState();
        saved.setWorldKey(Level.OVERWORLD);
        var journal = saved.save(new net.minecraft.nbt.CompoundTag(), null);
        SpatialDisplacementManager.clearRuntimeState();
        var recovered = DisplacementPersistentState.createFromNbt(journal, null).getUnrestoredRecords();
        assertEquals(1, recovered.size());
        assertEquals(target, recovered.getFirst().bridgeDestination);
        assertEquals(owner, recovered.getFirst().boundPlayerUuid);
        SpatialDisplacementManager.recoverBridge(recovered.getFirst());
        when(chunks.hasChunk(10, 0)).thenReturn(true);
        SpatialDisplacementManager.restoreAllForPlayer(owner, world);
        assertFalse(blocks.containsKey(target));
        assertEquals(Blocks.STONE.defaultBlockState(), blocks.get(source));
        assertEquals(0, SpatialDisplacementManager.getActiveRecordCount());
    }

    @Test void failedSourcePlacementKeepsRecoveryRecord() {
        doReturn(false).when(world).setBlock(eq(source), any(), anyInt());
        SpatialDisplacementManager.restoreAllForPlayer(owner, world);
        assertFalse(blocks.containsKey(source));
        assertFalse(blocks.containsKey(target));
        assertSame(record, SpatialDisplacementManager.getActiveRecord(Level.OVERWORLD, source));
    }

    @Test void failedCrashRecoveryKeepsSavedJournalForRetry() {
        SpatialDisplacementManager.clearRuntimeState();
        record.bridgeDestination = null;
        var saved = new DisplacementPersistentState();
        saved.getUnrestoredRecords().add(record);
        when(world.getAllEntities()).thenReturn(List.of());
        doReturn(false).when(world).setBlock(eq(source), any(), anyInt());
        saved.restoreAndCleanup(world);
        assertSame(record, SpatialDisplacementManager.getActiveRecord(Level.OVERWORLD, source));
        saved.setWorldKey(Level.OVERWORLD);
        var journal = saved.save(new net.minecraft.nbt.CompoundTag(), null);
        assertEquals(1, DisplacementPersistentState.createFromNbt(journal, null).getUnrestoredRecords().size());
        assertFalse(blocks.containsKey(source));
    }
}
