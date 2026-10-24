package net.godwand.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;

/** Visual references only: existing displays remain authoritative for thread positions. */
public record SingularityThreadsPayload(List<Integer> beamIds, int recallTicks) implements CustomPayload {
    public static final Id<SingularityThreadsPayload> ID = new Id<>(Identifier.of("godwand", "singularity_threads"));
    public SingularityThreadsPayload {
        if (beamIds.size() > 144 || recallTicks < 0 || recallTicks > 20) throw new IllegalArgumentException("Invalid thread effect");
        beamIds = List.copyOf(beamIds);
    }
    public static final PacketCodec<RegistryByteBuf, SingularityThreadsPayload> CODEC = new PacketCodec<>() {
        public SingularityThreadsPayload decode(RegistryByteBuf buf) {
            int count = buf.readVarInt();
            if (count < 0 || count > 144) throw new IllegalArgumentException("Too many thread references");
            var ids = new ArrayList<Integer>(count);
            for (int i = 0; i < count; i++) ids.add(buf.readVarInt());
            return new SingularityThreadsPayload(ids, buf.readVarInt());
        }
        public void encode(RegistryByteBuf buf, SingularityThreadsPayload value) {
            buf.writeVarInt(value.beamIds.size());
            for (int id : value.beamIds) buf.writeVarInt(id);
            buf.writeVarInt(value.recallTicks);
        }
    };
    public Id<? extends CustomPayload> getId() { return ID; }
}
