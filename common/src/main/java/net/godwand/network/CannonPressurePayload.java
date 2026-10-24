package net.godwand.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record CannonPressurePayload(Vec3 center, Vec3 direction, int level, Vec3 surfaceNormal) implements CustomPacketPayload {
    public CannonPressurePayload(Vec3 center, Vec3 direction, int level) { this(center, direction, level, direction.reverse()); }
    public static final Type<CannonPressurePayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("godwand", "cannon_pressure"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CannonPressurePayload> CODEC = new StreamCodec<>() {
        public CannonPressurePayload decode(RegistryFriendlyByteBuf b) {
            return new CannonPressurePayload(new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                    new Vec3(b.readFloat(), b.readFloat(), b.readFloat()), Math.clamp(b.readVarInt(), 1, 4), new Vec3(b.readFloat(), b.readFloat(), b.readFloat()));
        }
        public void encode(RegistryFriendlyByteBuf b, CannonPressurePayload p) {
            b.writeDouble(p.center.x); b.writeDouble(p.center.y); b.writeDouble(p.center.z);
            b.writeFloat((float)p.direction.x); b.writeFloat((float)p.direction.y); b.writeFloat((float)p.direction.z);
            b.writeVarInt(p.level);
            b.writeFloat((float)p.surfaceNormal.x); b.writeFloat((float)p.surfaceNormal.y); b.writeFloat((float)p.surfaceNormal.z);
        }
    };
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
