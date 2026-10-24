package net.godwand.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public record SpatialAuraPayload(int playerId, boolean active, Vec3d hitNormal) implements CustomPayload {
    public SpatialAuraPayload(int playerId, boolean active) { this(playerId, active, Vec3d.ZERO); }
    public static final Id<SpatialAuraPayload> ID = new Id<>(Identifier.of("godwand", "spatial_aura"));
    public static final PacketCodec<RegistryByteBuf, SpatialAuraPayload> CODEC = new PacketCodec<>() {
        public SpatialAuraPayload decode(RegistryByteBuf buf) { return new SpatialAuraPayload(buf.readVarInt(), buf.readBoolean(), new Vec3d(buf.readFloat(), buf.readFloat(), buf.readFloat())); }
        public void encode(RegistryByteBuf buf, SpatialAuraPayload value) { buf.writeVarInt(value.playerId()); buf.writeBoolean(value.active()); buf.writeFloat((float)value.hitNormal.x); buf.writeFloat((float)value.hitNormal.y); buf.writeFloat((float)value.hitNormal.z); }
    };
    public Id<? extends CustomPayload> getId() { return ID; }
}
