package net.godwand.spatial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ReversibleSkillTerrainTest {
    private ServerLevel world;
    private final Map<BlockPos,BlockState> blocks=new HashMap<>();
    private final BlockPos pos=new BlockPos(4,70,4);
    @BeforeAll static void bootstrap(){ net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @BeforeEach void setup() {
        SpatialDisplacementManager.clearRuntimeState(); world=mock(ServerLevel.class);
        when(world.dimension()).thenReturn(Level.OVERWORLD);
        when(world.registryAccess()).thenReturn(net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        var chunks=mock(ServerChunkCache.class); when(world.getChunkSource()).thenReturn(chunks);
        when(chunks.hasChunk(anyInt(),anyInt())).thenReturn(true);
        var storage=mock(SavedDataStorage.class);when(world.getDataStorage()).thenReturn(storage);
        when(storage.computeIfAbsent(any())).thenReturn(new DisplacementPersistentState());
        when(world.getBlockState(any())).thenAnswer(c -> blocks.getOrDefault(c.getArgument(0),Blocks.AIR.defaultBlockState()));
        when(world.setBlock(any(),any(),anyInt())).thenAnswer(c -> { blocks.put(((BlockPos)c.getArgument(0)).immutable(),c.getArgument(1));return true; });
    }
    @AfterEach void cleanup(){SpatialDisplacementManager.clearRuntimeState();}
    private void lift(boolean permanent) {
        SpatialDisplacementManager.displaceBlock(world,pos,new Vector3f(),new Quaternionf(),new Vector3f(1),200,0,false,
                DisplacedBlockRecord.DisplacementMode.CUSTOM_SCRIPTED,null,null,0,permanent);
    }
    @Test void temporaryLiftRestoresTheExactOriginalStateAndClearsItsJournal() {
        var original=Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,net.minecraft.core.Direction.Axis.X);
        blocks.put(pos,original);lift(false);
        assertTrue(blocks.get(pos).isAir());assertFalse(SpatialDisplacementManager.getActiveRecord(Level.OVERWORLD,pos).permanentDestruction);
        SpatialDisplacementManager.restorePositions(world,List.of(pos));
        assertEquals(original,blocks.get(pos));assertNull(SpatialDisplacementManager.getActiveRecord(Level.OVERWORLD,pos));
    }
    @Test void temporaryContainerLiftRetainsAndReloadsItsSavedNbt() {
        var original=Blocks.CHEST.defaultBlockState();blocks.put(pos,original);
        var container=mock(net.minecraft.world.level.block.entity.BlockEntity.class);
        var nbt=new net.minecraft.nbt.CompoundTag();nbt.putString("CustomName","Protected contents");
        when(world.getBlockEntity(pos)).thenReturn(container);
        when(container.saveWithFullMetadata(any(net.minecraft.core.HolderLookup.Provider.class))).thenReturn(nbt);
        lift(false);
        assertEquals(nbt,SpatialDisplacementManager.getActiveRecord(Level.OVERWORLD,pos).blockEntityNbt);
        SpatialDisplacementManager.restorePositions(world,List.of(pos));
        assertEquals(original,blocks.get(pos));var restored = org.mockito.ArgumentCaptor.forClass(net.minecraft.world.level.storage.ValueInput.class);
        verify(container).loadWithComponents(restored.capture());
        assertEquals("Protected contents", restored.getValue().getStringOr("CustomName", ""));
    }

    @Test void aNewContainerAtTheSourceMustNotHaveItsInventoryOverwritten() {
        var original=Blocks.CHEST.defaultBlockState();blocks.put(pos,original);
        var oldContainer=mock(net.minecraft.world.level.block.entity.BlockEntity.class);
        var nbt=new net.minecraft.nbt.CompoundTag();nbt.putString("CustomName","Old inventory");
        when(world.getBlockEntity(pos)).thenReturn(oldContainer);
        when(oldContainer.saveWithFullMetadata(any(net.minecraft.core.HolderLookup.Provider.class))).thenReturn(nbt);lift(false);
        var newContainer=mock(net.minecraft.world.level.block.entity.BlockEntity.class);
        blocks.put(pos,original);when(world.getBlockEntity(pos)).thenReturn(newContainer);
        SpatialDisplacementManager.restorePositions(world,List.of(pos));
        verify(newContainer,never()).loadWithComponents(any());
        assertNotNull(SpatialDisplacementManager.getActiveRecord(Level.OVERWORLD,pos));
        blocks.remove(pos);when(world.getBlockEntity(pos)).thenReturn(oldContainer);
        SpatialDisplacementManager.restorePositions(world,List.of(pos));
        assertNull(SpatialDisplacementManager.getActiveRecord(Level.OVERWORLD,pos));
    }
    @Test void restorationMustNotReplaceANewBlockAndMustRetryAfterItIsCleared() {
        blocks.put(pos,Blocks.STONE.defaultBlockState());lift(false);
        blocks.put(pos,Blocks.DIAMOND_BLOCK.defaultBlockState());
        SpatialDisplacementManager.restorePositions(world,List.of(pos));
        assertEquals(Blocks.DIAMOND_BLOCK.defaultBlockState(),blocks.get(pos));
        assertNotNull(SpatialDisplacementManager.getActiveRecord(Level.OVERWORLD,pos));
        blocks.remove(pos);SpatialDisplacementManager.restorePositions(world,List.of(pos));
        assertEquals(Blocks.STONE.defaultBlockState(),blocks.get(pos));
    }

    @Test void destructiveModeRemainsExplicitAndIsNeverMistakenForARestorableLift() {
        blocks.put(pos,Blocks.STONE.defaultBlockState());lift(true);
        SpatialDisplacementManager.restorePositions(world,List.of(pos));
        assertTrue(blocks.get(pos).isAir());assertTrue(SpatialDisplacementManager.getActiveRecord(Level.OVERWORLD,pos).permanentDestruction);
    }
    @Test void restoringOneDoorHalfAlsoRestoresItsPairedJournaledHalf() {
        var lower=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER);
        var upper=lower.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER);
        blocks.put(pos,lower);blocks.put(pos.above(),upper);lift(false);
        assertTrue(blocks.get(pos).isAir());assertTrue(blocks.get(pos.above()).isAir());
        SpatialDisplacementManager.restorePositions(world,List.of(pos));
        assertEquals(lower,blocks.get(pos));assertEquals(upper,blocks.get(pos.above()));
        assertEquals(0,SpatialDisplacementManager.getActiveRecordCount());
    }
}
