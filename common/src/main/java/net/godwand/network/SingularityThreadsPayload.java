package net.godwand.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Visual references only: existing displays remain authoritative for thread positions. */
public record SingularityThreadsPayload(List<Integer> beamIds, int recallTicks) implements CustomPacketPayload {
    public static final Type<SingularityThreadsPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("godwand", "singularity_threads"));
    public SingularityThreadsPayload {
        if (beamIds.size() > 144 || recallTicks < 0 || recallTicks > 20) throw new IllegalArgumentException("Invalid thread effect");
        beamIds = List.copyOf(beamIds);
    }
    public static final StreamCodec<RegistryFriendlyByteBuf, SingularityThreadsPayload> CODEC = new StreamCodec<>() {
        public SingularityThreadsPayload decode(RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            if (count < 0 || count > 144) throw new IllegalArgumentException("Too many thread references");
            var ids = new ArrayList<Integer>(count);
            for (int i = 0; i < count; i++) ids.add(buf.readVarInt());
            return new SingularityThreadsPayload(ids, buf.readVarInt());
        }
        public void encode(RegistryFriendlyByteBuf buf, SingularityThreadsPayload value) {
            buf.writeVarInt(value.beamIds.size());
            for (int id : value.beamIds) buf.writeVarInt(id);
            buf.writeVarInt(value.recallTicks);
        }
    };
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
