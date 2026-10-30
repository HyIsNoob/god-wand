package net.godwand.network;

import net.godwand.item.GodWandItem;
import net.godwand.skill.SkillCooldownState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SkillCooldownPayload(int mode,int durationTicks,int remainingTicks,boolean recall) implements CustomPacketPayload {
    public SkillCooldownPayload {
        if(mode<0||mode>=GodWandItem.TOTAL_MODES||durationTicks<0||durationTicks>SkillCooldownState.MAX_TICKS||remainingTicks<0||remainingTicks>durationTicks||recall&&mode!=1)throw new IllegalArgumentException("Invalid skill cooldown packet");
    }
    public static final Type<SkillCooldownPayload> ID=new Type<>(ResourceLocation.fromNamespaceAndPath("godwand","skill_cooldown"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SkillCooldownPayload> CODEC=new StreamCodec<>() {
        public SkillCooldownPayload decode(RegistryFriendlyByteBuf b) {return new SkillCooldownPayload(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean());}
        public void encode(RegistryFriendlyByteBuf b,SkillCooldownPayload p) {b.writeVarInt(p.mode);b.writeVarInt(p.durationTicks);b.writeVarInt(p.remainingTicks);b.writeBoolean(p.recall);}
    };
    public Type<? extends CustomPacketPayload> type(){return ID;}
}
