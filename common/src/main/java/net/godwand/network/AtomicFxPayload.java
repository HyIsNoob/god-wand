package net.godwand.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AtomicFxPayload(int casterId, long startedAt, double x, double y, double z, int phase, long releasedAt) implements CustomPacketPayload {
    public static final int CHARGE = 0, ARMED = 1, BURST = 2, CANCEL = 3, RELEASE = 4;
    public static final Type<AtomicFxPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("godwand", "atomic_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtomicFxPayload> CODEC = new StreamCodec<>() {
        public AtomicFxPayload decode(RegistryFriendlyByteBuf buf) {
            return new AtomicFxPayload(buf.readVarInt(), buf.readLong(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readLong());
        }
        public void encode(RegistryFriendlyByteBuf buf, AtomicFxPayload value) {
            buf.writeVarInt(value.casterId); buf.writeLong(value.startedAt);
            buf.writeDouble(value.x); buf.writeDouble(value.y); buf.writeDouble(value.z); buf.writeVarInt(value.phase); buf.writeLong(value.releasedAt);
        }
    };
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
