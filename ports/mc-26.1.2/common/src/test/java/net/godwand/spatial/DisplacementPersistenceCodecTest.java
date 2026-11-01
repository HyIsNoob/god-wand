package net.godwand.spatial;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.ChestBlock;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DisplacementPersistenceCodecTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @AfterEach void cleanup() { SpatialDisplacementManager.clearRuntimeState(); }

    @Test void savedDataCodecRetainsDimensionBlockPropertiesContainerDataAndBridgeOwner() {
        SpatialDisplacementManager.clearRuntimeState();
        var contents = new CompoundTag();
        contents.putString("id", "minecraft:chest");
        contents.putString("CustomName", "Protected inventory");
        var item = new CompoundTag();
        item.putString("id", "minecraft:diamond"); item.putInt("count", 37);
        var items = new net.minecraft.nbt.ListTag(); items.add(item); contents.put("Items", items);
        var state = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.LEFT);
        var record = new DisplacedBlockRecord(new BlockPos(-29, 83, 16), Level.NETHER, state, contents,
                new Vector3f(), new Quaternionf(), 100, false);
        record.bridgeDestination = new BlockPos(-17, 83, 16);
        record.boundPlayerUuid = UUID.randomUUID();
        var journal = new DisplacementPersistentState(); journal.setWorldKey(Level.NETHER);
        journal.getUnrestoredRecords().add(record);
        var encoded = DisplacementPersistentState.TYPE.codec().encodeStart(NbtOps.INSTANCE, journal).getOrThrow();
        var decoded = DisplacementPersistentState.TYPE.codec().parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(1, decoded.getUnrestoredRecords().size());
        var restored = decoded.getUnrestoredRecords().getFirst();
        assertEquals(record.pos, restored.pos);
        assertEquals(Level.NETHER, restored.worldKey);
        assertEquals(state, restored.originalState);
        assertEquals(contents, restored.blockEntityNbt);
        assertEquals(record.bridgeDestination, restored.bridgeDestination);
        assertEquals(record.boundPlayerUuid, restored.boundPlayerUuid);
        assertFalse(restored.isFullCube);
    }
}
