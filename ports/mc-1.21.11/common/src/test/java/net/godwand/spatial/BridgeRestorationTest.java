package net.godwand.spatial;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.DimensionDataStorage;
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
        var storage = mock(DimensionDataStorage.class);
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

    private DisplacedBlockRecord addRecord(BlockPos pos, DisplacedBlockRecord.DisplacementMode mode, boolean permanent) {
        var added = new DisplacedBlockRecord(pos, Level.OVERWORLD, Blocks.STONE.defaultBlockState(), null,
                new Vector3f(), new Quaternionf(), new Vector3f(1), 200, true, mode, owner);
        added.permanentDestruction = permanent;
        added.displayEntityUuid = UUID.randomUUID();
        SpatialDisplacementManager.recoverBridge(added);
        var display = mock(net.minecraft.world.entity.Display.BlockDisplay.class);
        when(display.isAlive()).thenReturn(true);
        SpatialDisplacementManager.trackEntity(added.displayEntityUuid, display);
        return added;
    }

    @Test void pathCleanupRestoresPathAndBridgeButRetainsCommittedSpellRecordsAndDisplays() {
        var path = addRecord(source.offset(1, 0, 0), DisplacedBlockRecord.DisplacementMode.PATH_WALKER, false);
        var aura = addRecord(source.offset(2, 0, 0), DisplacedBlockRecord.DisplacementMode.HYSTERESIS_AURA, false);
        var temporary = addRecord(source.offset(3, 0, 0), DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED, false);
        var permanent = addRecord(source.offset(4, 0, 0), DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED, true);
        SpatialDisplacementManager.restorePathForPlayer(owner, world);
        assertEquals(Blocks.STONE.defaultBlockState(), blocks.get(source));
        assertFalse(blocks.containsKey(target));
        assertNull(SpatialDisplacementManager.getActiveRecord(Level.OVERWORLD, path.pos));
        assertNull(SpatialDisplacementManager.getActiveRecord(Level.OVERWORLD, aura.pos));
        for (var spell : List.of(temporary, permanent)) {
            assertSame(spell, SpatialDisplacementManager.getActiveRecord(Level.OVERWORLD, spell.pos));
            assertFalse(blocks.containsKey(spell.pos));
            assertEquals(0, spell.restoreTick);
            verify(SpatialDisplacementManager.getActiveEntity(spell.displayEntityUuid), never()).discard();
        }
    }

    @Test void explicitFullCleanupStillRestoresTemporaryAndRemovesPermanentSpellRecords() {
        var temporary = addRecord(source.offset(1, 0, 0), DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED, false);
        var permanent = addRecord(source.offset(2, 0, 0), DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED, true);
        var temporaryDisplay = SpatialDisplacementManager.getActiveEntity(temporary.displayEntityUuid);
        var permanentDisplay = SpatialDisplacementManager.getActiveEntity(permanent.displayEntityUuid);
        SpatialDisplacementManager.restoreAllForPlayer(owner, world);
        assertEquals(Blocks.STONE.defaultBlockState(), blocks.get(temporary.pos));
        assertFalse(blocks.containsKey(permanent.pos));
        assertEquals(0, SpatialDisplacementManager.getActiveRecordCount());
        verify(temporaryDisplay).discard();
        verify(permanentDisplay).discard();
    }

    @Test void auraCollapseDoesNotStartRestoringCommittedSpellBlocks() {
        var path = addRecord(source.offset(1, 0, 0), DisplacedBlockRecord.DisplacementMode.PATH_WALKER, false);
        var spell = addRecord(source.offset(2, 0, 0), DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED, false);
        SpatialDisplacementManager.collapsePlayerAura(owner);
        assertTrue(path.isRestoring());
        assertTrue(record.isRestoring());
        assertFalse(spell.isRestoring());
    }

    @Test void removalFindsReloadedDisplayInsteadOfTheUnloadedInstance() {
        var spell=addRecord(source.offset(3,0,0),DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,true);
        var old=SpatialDisplacementManager.getActiveEntity(spell.displayEntityUuid);
        when(old.isAlive()).thenReturn(false);
        when(old.level()).thenReturn(world);
        var reloaded=mock(net.minecraft.world.entity.Display.BlockDisplay.class);
        when(reloaded.isAlive()).thenReturn(true);
        when(world.getEntity(spell.displayEntityUuid)).thenReturn(reloaded);
        SpatialDisplacementManager.removeActiveRecord(Level.OVERWORLD,spell.pos);
        verify(reloaded).discard();
        assertNull(SpatialDisplacementManager.getActiveRecord(Level.OVERWORLD,spell.pos));
    }

    @Test void managedDisplayReloadReattachesWhileItsSpellRemainsActive() {
        var spell=addRecord(source.offset(3,0,0),DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,true);
        var reloaded=mock(net.minecraft.world.entity.Display.BlockDisplay.class);
        when(reloaded.level()).thenReturn(world);
        when(reloaded.getUUID()).thenReturn(spell.displayEntityUuid);
        when(reloaded.getTags()).thenReturn(Set.of("godwand_managed_displacement"));
        when(reloaded.isAlive()).thenReturn(true);
        SpatialDisplacementManager.tickManagedDisplay(reloaded);
        assertSame(reloaded,SpatialDisplacementManager.getActiveEntity(spell.displayEntityUuid));
        verify(reloaded,never()).discard();
        SpatialDisplacementManager.removeActiveRecord(Level.OVERWORLD,spell.pos);
        verify(reloaded).discard();
    }

    @Test void managedDisplayReloadAfterSpellCompletionDiscardsTheOrphan() {
        var spell=addRecord(source.offset(3,0,0),DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,true);
        SpatialDisplacementManager.removeActiveRecord(Level.OVERWORLD,spell.pos);
        var reloaded=mock(net.minecraft.world.entity.Display.BlockDisplay.class);
        when(reloaded.level()).thenReturn(world);
        when(reloaded.getUUID()).thenReturn(spell.displayEntityUuid);
        when(reloaded.getTags()).thenReturn(Set.of("godwand_managed_displacement"));
        SpatialDisplacementManager.tickManagedDisplay(reloaded);
        verify(reloaded).discard();
        assertNull(SpatialDisplacementManager.getActiveEntity(spell.displayEntityUuid));
    }

    @Test void standaloneBeamAndBridgeDisplaysAreNotTreatedAsOrphans() {
        var beam=mock(net.minecraft.world.entity.Display.BlockDisplay.class);
        when(beam.level()).thenReturn(world);
        when(beam.getTags()).thenReturn(Set.of("godwand_displaced"));
        SpatialDisplacementManager.tickManagedDisplay(beam);
        verify(beam,never()).discard();
    }

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
