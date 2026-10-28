package net.godwand.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public record SpatialAuraPayload(int playerId, boolean active, Vec3 hitNormal) implements CustomPacketPayload {
    public SpatialAuraPayload(int playerId, boolean active) { this(playerId, active, Vec3.ZERO); }
    public static final Type<SpatialAuraPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("godwand", "spatial_aura"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpatialAuraPayload> CODEC = new StreamCodec<>() {
        public SpatialAuraPayload decode(RegistryFriendlyByteBuf buf) { return new SpatialAuraPayload(buf.readVarInt(), buf.readBoolean(), new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat())); }
        public void encode(RegistryFriendlyByteBuf buf, SpatialAuraPayload value) { buf.writeVarInt(value.playerId()); buf.writeBoolean(value.active()); buf.writeFloat((float)value.hitNormal.x); buf.writeFloat((float)value.hitNormal.y); buf.writeFloat((float)value.hitNormal.z); }
    };
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
