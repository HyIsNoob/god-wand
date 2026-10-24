package net.godwand.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public record CannonPressurePayload(Vec3d center, Vec3d direction, int level, Vec3d surfaceNormal) implements CustomPayload {
    public CannonPressurePayload(Vec3d center, Vec3d direction, int level) { this(center, direction, level, direction.negate()); }
    public static final Id<CannonPressurePayload> ID = new Id<>(Identifier.of("godwand", "cannon_pressure"));
    public static final PacketCodec<RegistryByteBuf, CannonPressurePayload> CODEC = new PacketCodec<>() {
        public CannonPressurePayload decode(RegistryByteBuf b) {
            return new CannonPressurePayload(new Vec3d(b.readDouble(), b.readDouble(), b.readDouble()),
                    new Vec3d(b.readFloat(), b.readFloat(), b.readFloat()), Math.clamp(b.readVarInt(), 1, 4), new Vec3d(b.readFloat(), b.readFloat(), b.readFloat()));
        }
        public void encode(RegistryByteBuf b, CannonPressurePayload p) {
            b.writeDouble(p.center.x); b.writeDouble(p.center.y); b.writeDouble(p.center.z);
            b.writeFloat((float)p.direction.x); b.writeFloat((float)p.direction.y); b.writeFloat((float)p.direction.z);
            b.writeVarInt(p.level);
            b.writeFloat((float)p.surfaceNormal.x); b.writeFloat((float)p.surfaceNormal.y); b.writeFloat((float)p.surfaceNormal.z);
        }
    };
    public Id<? extends CustomPayload> getId() { return ID; }
}
