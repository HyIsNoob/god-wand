package net.godwand.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
public record AtomicFxPayload(int casterId,long startedAt,double x,double y,double z,int phase,long releasedAt) implements GodWandPacket {
 public static final int CHARGE=0, ARMED=1, BURST=2, CANCEL=3, RELEASE=4;
 public static final ResourceLocation ID=new ResourceLocation("godwand","atomic_fx");
 public ResourceLocation getId(){return ID;}
 public void write(FriendlyByteBuf buf){buf.writeVarInt(casterId);buf.writeLong(startedAt);buf.writeDouble(x);buf.writeDouble(y);buf.writeDouble(z);buf.writeVarInt(phase);buf.writeLong(releasedAt);}
 public static AtomicFxPayload read(FriendlyByteBuf buf){return new AtomicFxPayload(buf.readVarInt(),buf.readLong(),buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readVarInt(),buf.readLong());}
}
