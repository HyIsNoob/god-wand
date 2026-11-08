package net.godwand.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SilentGateCancelPayload(long startedAt) implements CustomPacketPayload {
    public static final Type<SilentGateCancelPayload> ID=new Type<>(ResourceLocation.fromNamespaceAndPath("godwand","silent_gate_cancel"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SilentGateCancelPayload> CODEC=new StreamCodec<>() {
        public SilentGateCancelPayload decode(RegistryFriendlyByteBuf buf) { return new SilentGateCancelPayload(buf.readLong()); }
        public void encode(RegistryFriendlyByteBuf buf,SilentGateCancelPayload value) { buf.writeLong(value.startedAt()); }
    };
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
