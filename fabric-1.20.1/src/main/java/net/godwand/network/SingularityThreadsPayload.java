package net.godwand.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record SingularityThreadsPayload(List<Integer> beamIds, int recallTicks) implements GodWandPacket {
    public static final ResourceLocation ID = new ResourceLocation("godwand", "singularity_threads");

    public SingularityThreadsPayload {
        if (beamIds.size() > 144 || recallTicks < 0 || recallTicks > 20) throw new IllegalArgumentException("Invalid thread effect");
        beamIds = List.copyOf(beamIds);
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(beamIds.size());
        for (int id : beamIds) buf.writeVarInt(id);
        buf.writeVarInt(recallTicks);
    }

    public static SingularityThreadsPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > 144) throw new IllegalArgumentException("Too many thread references");
        var ids = new ArrayList<Integer>(count);
        for (int i = 0; i < count; i++) ids.add(buf.readVarInt());
        return new SingularityThreadsPayload(ids, buf.readVarInt());
    }
}
