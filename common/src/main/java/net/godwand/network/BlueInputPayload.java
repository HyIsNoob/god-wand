package net.godwand.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → server: left click started (true) or stopped (false) while the wand is on Blue/Red. */
public record BlueInputPayload(boolean held) implements CustomPacketPayload {
    public static final Type<BlueInputPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("godwand", "blue_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueInputPayload> CODEC = new StreamCodec<>() {
        public BlueInputPayload decode(RegistryFriendlyByteBuf buf) { return new BlueInputPayload(buf.readBoolean()); }
        public void encode(RegistryFriendlyByteBuf buf, BlueInputPayload value) { buf.writeBoolean(value.held()); }
    };
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
