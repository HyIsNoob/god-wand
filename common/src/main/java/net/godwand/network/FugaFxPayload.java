package net.godwand.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FugaFxPayload(int casterId,long startedAt,double x,double y,double z,double tx,double ty,double tz,int phase,long phaseAt) implements CustomPacketPayload {
    public static final int CHARGE=0, FIRE=1, IMPACT=2, CANCEL=3, END=4;
    public FugaFxPayload {
        if(casterId<0 || startedAt<0 || phaseAt<startedAt || phase<CHARGE || phase>END
                || (phase==FIRE || phase==IMPACT || phase==END) && phaseAt-startedAt<net.godwand.skill.FugaSequence.CHARGE_TICKS)
            throw new IllegalArgumentException("Invalid Fuga lifecycle");
        for(double coordinate:new double[]{x,y,z,tx,ty,tz})
            if(!Double.isFinite(coordinate) || Math.abs(coordinate)>30_000_000)
                throw new IllegalArgumentException("Invalid Fuga coordinate");
    }
    public static final Type<FugaFxPayload> ID=new Type<>(ResourceLocation.fromNamespaceAndPath("godwand","fuga_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf,FugaFxPayload> CODEC=new StreamCodec<>() {
        public FugaFxPayload decode(RegistryFriendlyByteBuf buf) {
            return new FugaFxPayload(buf.readVarInt(),buf.readLong(),buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readVarInt(),buf.readLong());
        }
        public void encode(RegistryFriendlyByteBuf buf,FugaFxPayload value) {
            buf.writeVarInt(value.casterId);buf.writeLong(value.startedAt);
            buf.writeDouble(value.x);buf.writeDouble(value.y);buf.writeDouble(value.z);
            buf.writeDouble(value.tx);buf.writeDouble(value.ty);buf.writeDouble(value.tz);
            buf.writeVarInt(value.phase);buf.writeLong(value.phaseAt);
        }
    };
    public Type<? extends CustomPacketPayload> type() {return ID;}
}
