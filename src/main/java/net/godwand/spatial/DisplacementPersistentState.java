package net.godwand.spatial;

import net.godwand.GodWandMod;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class DisplacementPersistentState extends PersistentState {
    public static final String IDENTIFIER = "godwand_displacements";

    public static final Type<DisplacementPersistentState> TYPE = new Type<>(
            DisplacementPersistentState::new,
            DisplacementPersistentState::createFromNbt,
            null
    );

    private final List<DisplacedBlockRecord> unrestoredRecords = new ArrayList<>();
    private RegistryKey<World> worldKey;

    public DisplacementPersistentState() {
        super();
    }

    public void setWorldKey(RegistryKey<World> worldKey) {
        this.worldKey = worldKey;
    }

    public static DisplacementPersistentState createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        DisplacementPersistentState state = new DisplacementPersistentState();
        if (nbt.contains("displaced_records", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("displaced_records", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound recNbt = list.getCompound(i);
                int x = recNbt.getInt("x");
                int y = recNbt.getInt("y");
                int z = recNbt.getInt("z");
                BlockPos pos = new BlockPos(x, y, z);

                BlockState blockState;
                if (recNbt.contains("state", NbtElement.COMPOUND_TYPE)) {
                    blockState = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), recNbt.getCompound("state"));
                } else {
                    continue;
                }

                NbtCompound beNbt = recNbt.contains("blockEntity", NbtElement.COMPOUND_TYPE) ? recNbt.getCompound("blockEntity") : null;
                String worldKeyStr = recNbt.getString("worldKey");
                RegistryKey<World> wKey = worldKeyStr.isEmpty() ? World.OVERWORLD : RegistryKey.of(RegistryKeys.WORLD, Identifier.of(worldKeyStr));
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
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();

        RegistryKey<World> targetKey = this.worldKey != null ? this.worldKey : World.OVERWORLD;
        List<DisplacedBlockRecord> active = SpatialDisplacementManager.getActiveRecordsForWorld(targetKey);
        for (DisplacedBlockRecord record : active) {
            NbtCompound recNbt = new NbtCompound();
            recNbt.putInt("x", record.pos.getX());
            recNbt.putInt("y", record.pos.getY());
            recNbt.putInt("z", record.pos.getZ());
            recNbt.put("state", NbtHelper.fromBlockState(record.state));
            if (record.nbt != null) {
                recNbt.put("blockEntity", record.nbt);
            }
            recNbt.putString("worldKey", record.worldKey.getValue().toString());
            recNbt.putBoolean("isFullCube", record.isFullCube);
            record.writeBridgeData(recNbt);
            list.add(recNbt);
        }

        for (DisplacedBlockRecord record : this.unrestoredRecords) {
            if (this.worldKey == null || record.worldKey.equals(this.worldKey)) {
                NbtCompound recNbt = new NbtCompound();
                recNbt.putInt("x", record.pos.getX());
                recNbt.putInt("y", record.pos.getY());
                recNbt.putInt("z", record.pos.getZ());
                recNbt.put("state", NbtHelper.fromBlockState(record.state));
                if (record.nbt != null) {
                    recNbt.put("blockEntity", record.nbt);
                }
                recNbt.putString("worldKey", record.worldKey.getValue().toString());
                recNbt.putBoolean("isFullCube", record.isFullCube);
            record.writeBridgeData(recNbt);
                list.add(recNbt);
            }
        }

        nbt.put("displaced_records", list);
        return nbt;
    }

    public static DisplacementPersistentState getServerState(ServerWorld world) {
        DisplacementPersistentState state = world.getPersistentStateManager().getOrCreate(TYPE, IDENTIFIER);
        state.setWorldKey(world.getRegistryKey());
        return state;
    }

    public void restoreAndCleanup(ServerWorld world) {
        if (!unrestoredRecords.isEmpty()) {
            List<DisplacedBlockRecord> toRestore = new ArrayList<>();
            for (DisplacedBlockRecord r : unrestoredRecords) {
                if (r.worldKey.equals(world.getRegistryKey())) {
                    if (r.bridgeDestination != null) SpatialDisplacementManager.recoverBridge(r);
                    else toRestore.add(r);
                }
            }
            if (!toRestore.isEmpty()) {
                SpatialDisplacementManager.executeTwoPassRestoration(world, toRestore);
                unrestoredRecords.removeAll(toRestore);
                markDirty();
                GodWandMod.LOGGER.info("Crash recovery: Restored {} orphaned displaced blocks in {}", toRestore.size(), world.getRegistryKey().getValue());
            }
        }

        unrestoredRecords.removeIf(r -> r.worldKey.equals(world.getRegistryKey()) && r.bridgeDestination != null);
        markDirty();
        List<Entity> toDiscard = new ArrayList<>();
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof BlockDisplayEntity && entity.getCommandTags().contains("godwand_displaced")) {
                toDiscard.add(entity);
            }
        }
        if (!toDiscard.isEmpty()) {
            for (Entity entity : toDiscard) {
                entity.discard();
            }
            GodWandMod.LOGGER.info("Cleaned up {} orphaned godwand_displaced display entities in {}", toDiscard.size(), world.getRegistryKey().getValue());
        }
    }

    public List<DisplacedBlockRecord> getUnrestoredRecords() {
        return unrestoredRecords;
    }
}
