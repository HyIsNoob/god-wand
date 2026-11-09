package net.godwand.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SilentGatePayload(int casterId,long startedAt,long phaseAt,int phase,double x,double y,double z,float yaw,long seed,int skyCircles,int barrageTicks,int[] shots,double[] hits,int featured) implements CustomPacketPayload {
    public static final int CANCEL=4, MAX_SHOTS=104, NO_FEATURED=-1, SHOT_EXTRA=32;
    /** Without a featured kill target. */
    public SilentGatePayload(int casterId,long startedAt,long phaseAt,int phase,double x,double y,double z,float yaw,long seed,int skyCircles,int barrageTicks,int[] shots,double[] hits) {
        this(casterId,startedAt,phaseAt,phase,x,y,z,yaw,seed,skyCircles,barrageTicks,shots,hits,NO_FEATURED);
    }
    public SilentGatePayload {
        if(casterId<0 || phaseAt<startedAt || phase<0 || phase>CANCEL || skyCircles<8 || skyCircles>256 || barrageTicks<60 || barrageTicks>600
                || featured<NO_FEATURED || shots.length>MAX_SHOTS || hits.length!=3*shots.length)
            throw new IllegalArgumentException("Invalid Silent Gate lifecycle");
        for(int shot:shots)
            if(shot<0 || shot>=skyCircles+SHOT_EXTRA) throw new IllegalArgumentException("Invalid Silent Gate shot");
        for(double coordinate:new double[]{x,y,z})
            if(!Double.isFinite(coordinate) || Math.abs(coordinate)>30_000_000)
                throw new IllegalArgumentException("Invalid Silent Gate coordinate");
        for(double coordinate:hits)
            if(!Double.isFinite(coordinate) || Math.abs(coordinate)>30_000_000)
                throw new IllegalArgumentException("Invalid Silent Gate hit");
    }
    public static final Type<SilentGatePayload> ID=new Type<>(ResourceLocation.fromNamespaceAndPath("godwand","silent_gate"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SilentGatePayload> CODEC=new StreamCodec<>() {
        public SilentGatePayload decode(RegistryFriendlyByteBuf buf) {
            int casterId=buf.readVarInt();long startedAt=buf.readLong(),phaseAt=buf.readLong();int phase=buf.readVarInt();
            double x=buf.readDouble(),y=buf.readDouble(),z=buf.readDouble();float yaw=buf.readFloat();long seed=buf.readLong();
            int skyCircles=buf.readVarInt(),barrageTicks=buf.readVarInt();
            int n=buf.readVarInt();
            if(n<0 || n>MAX_SHOTS) throw new IllegalArgumentException("Invalid Silent Gate shot count");
            int[] shots=new int[n];double[] hits=new double[3*n];
            for(int i=0;i<n;i++) shots[i]=buf.readVarInt();
            for(int i=0;i<hits.length;i++) hits[i]=buf.readDouble();
            return new SilentGatePayload(casterId,startedAt,phaseAt,phase,x,y,z,yaw,seed,skyCircles,barrageTicks,shots,hits,buf.readVarInt()-1);
        }
        public void encode(RegistryFriendlyByteBuf buf,SilentGatePayload v) {
            buf.writeVarInt(v.casterId);buf.writeLong(v.startedAt);buf.writeLong(v.phaseAt);buf.writeVarInt(v.phase);
            buf.writeDouble(v.x);buf.writeDouble(v.y);buf.writeDouble(v.z);buf.writeFloat(v.yaw);buf.writeLong(v.seed);
            buf.writeVarInt(v.skyCircles);buf.writeVarInt(v.barrageTicks);buf.writeVarInt(v.shots.length);
            for(int s:v.shots) buf.writeVarInt(s);
            for(double h:v.hits) buf.writeDouble(h);
            buf.writeVarInt(v.featured+1);
        }
    };
    public Type<? extends CustomPacketPayload> type() {return ID;}
}
