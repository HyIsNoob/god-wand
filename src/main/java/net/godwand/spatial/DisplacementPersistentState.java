package net.godwand.spatial;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

public class DisplacementPersistentState extends PersistentState {
    public static final String IDENTIFIER = "godwand_displacements";

    public static final Type<DisplacementPersistentState> TYPE = new Type<>(
            DisplacementPersistentState::new,
            DisplacementPersistentState::createFromNbt,
            null
    );

    public DisplacementPersistentState() {
        super();
    }

    public static DisplacementPersistentState createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        DisplacementPersistentState state = new DisplacementPersistentState();
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        return nbt;
    }

    public static DisplacementPersistentState getServerState(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, IDENTIFIER);
    }
}
