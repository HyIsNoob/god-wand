package net.godwand.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AtomicSkipPayload(long startedAt) implements CustomPacketPayload {
    public static final Type<AtomicSkipPayload> ID=new Type<>(Identifier.fromNamespaceAndPath("godwand","atomic_skip"));
    public static final StreamCodec<RegistryFriendlyByteBuf,AtomicSkipPayload> CODEC=new StreamCodec<>() {
        public AtomicSkipPayload decode(RegistryFriendlyByteBuf buf) { return new AtomicSkipPayload(buf.readLong()); }
        public void encode(RegistryFriendlyByteBuf buf,AtomicSkipPayload value) { buf.writeLong(value.startedAt()); }
    };
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
