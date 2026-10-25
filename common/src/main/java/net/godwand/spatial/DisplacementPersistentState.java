package net.godwand.spatial;

import net.godwand.GodWandMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display.BlockDisplay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class DisplacementPersistentState extends SavedData {
    public static final String IDENTIFIER = "godwand_displacements";

    public static final Factory<DisplacementPersistentState> TYPE = new Factory<>(
            DisplacementPersistentState::new,
            DisplacementPersistentState::createFromNbt,
            null
    );

    private final List<DisplacedBlockRecord> unrestoredRecords = new ArrayList<>();
    private ResourceKey<Level> worldKey;

    public DisplacementPersistentState() {
        super();
    }

    public void setWorldKey(ResourceKey<Level> worldKey) {
        this.worldKey = worldKey;
    }

    public static DisplacementPersistentState createFromNbt(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        DisplacementPersistentState state = new DisplacementPersistentState();
        if (nbt.contains("displaced_records", Tag.TAG_LIST)) {
            ListTag list = nbt.getList("displaced_records", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag recNbt = list.getCompound(i);
                int x = recNbt.getInt("x");
                int y = recNbt.getInt("y");
                int z = recNbt.getInt("z");
                BlockPos pos = new BlockPos(x, y, z);

                BlockState blockState;
                if (recNbt.contains("state", Tag.TAG_COMPOUND)) {
                    blockState = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), recNbt.getCompound("state"));
                } else {
                    continue;
                }

                CompoundTag beNbt = recNbt.contains("blockEntity", Tag.TAG_COMPOUND) ? recNbt.getCompound("blockEntity") : null;
                String worldKeyStr = recNbt.getString("worldKey");
                ResourceKey<Level> wKey = worldKeyStr.isEmpty() ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(worldKeyStr));
                boolean isFullCube = recNbt.getBoolean("isFullCube");

                DisplacedBlockRecord record = new DisplacedBlockRecord(
                        pos, wKey, blockState, beNbt,
                        new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f),
                        0, isFullCube
                );
                record.readBridgeData(recNbt);
                state.unrestoredRecords.add(record);
            }
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        ListTag list = new ListTag();

        ResourceKey<Level> targetKey = this.worldKey != null ? this.worldKey : Level.OVERWORLD;
        List<DisplacedBlockRecord> active = SpatialDisplacementManager.getActiveRecordsForWorld(targetKey);
        for (DisplacedBlockRecord record : active) {
            CompoundTag recNbt = new CompoundTag();
            recNbt.putInt("x", record.pos.getX());
            recNbt.putInt("y", record.pos.getY());
            recNbt.putInt("z", record.pos.getZ());
            recNbt.put("state", NbtUtils.writeBlockState(record.state));
            if (record.nbt != null) {
                recNbt.put("blockEntity", record.nbt);
            }
            recNbt.putString("worldKey", record.worldKey.location().toString());
            recNbt.putBoolean("isFullCube", record.isFullCube);
            record.writeBridgeData(recNbt);
            list.add(recNbt);
        }

        for (DisplacedBlockRecord record : this.unrestoredRecords) {
            if (this.worldKey == null || record.worldKey.equals(this.worldKey)) {
                CompoundTag recNbt = new CompoundTag();
                recNbt.putInt("x", record.pos.getX());
                recNbt.putInt("y", record.pos.getY());
                recNbt.putInt("z", record.pos.getZ());
                recNbt.put("state", NbtUtils.writeBlockState(record.state));
                if (record.nbt != null) {
                    recNbt.put("blockEntity", record.nbt);
                }
                recNbt.putString("worldKey", record.worldKey.location().toString());
                recNbt.putBoolean("isFullCube", record.isFullCube);
            record.writeBridgeData(recNbt);
                list.add(recNbt);
            }
        }

        nbt.put("displaced_records", list);
        return nbt;
    }

    public static DisplacementPersistentState getServerState(ServerLevel world) {
        DisplacementPersistentState state = world.getDataStorage().computeIfAbsent(TYPE, IDENTIFIER);
        state.setWorldKey(world.dimension());
        return state;
    }

    public void restoreAndCleanup(ServerLevel world) {
        if (!unrestoredRecords.isEmpty()) {
            List<DisplacedBlockRecord> toRestore = new ArrayList<>();
            for (DisplacedBlockRecord r : unrestoredRecords) {
                if (r.worldKey.equals(world.dimension())) {
                    // Failed restores must remain tracked for ticking and the next save.
                    SpatialDisplacementManager.recoverBridge(r);
                    if (r.bridgeDestination == null) toRestore.add(r);
                }
            }
            if (!toRestore.isEmpty()) {
                SpatialDisplacementManager.executeTwoPassRestoration(world, toRestore);
                unrestoredRecords.removeAll(toRestore);
                setDirty();
                GodWandMod.LOGGER.info("Crash recovery: Attempted restoration of {} orphaned displaced blocks in {}", toRestore.size(), world.dimension().location());
            }
        }

        unrestoredRecords.removeIf(r -> r.worldKey.equals(world.dimension()) && r.bridgeDestination != null);
        setDirty();
        List<Entity> toDiscard = new ArrayList<>();
        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof BlockDisplay && entity.getTags().contains("godwand_displaced")) {
                toDiscard.add(entity);
            }
        }
        if (!toDiscard.isEmpty()) {
            for (Entity entity : toDiscard) {
                entity.discard();
            }
            GodWandMod.LOGGER.info("Cleaned up {} orphaned godwand_displaced display entities in {}", toDiscard.size(), world.dimension().location());
        }
    }

    public List<DisplacedBlockRecord> getUnrestoredRecords() {
        return unrestoredRecords;
    }
}
