package net.godwand.network;

import net.godwand.item.GodWandItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SelectSkillPayload(int mode, int slot) implements CustomPacketPayload {
    public static final Type<SelectSkillPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("godwand", "select_skill"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectSkillPayload> CODEC = new StreamCodec<>() {
        public SelectSkillPayload decode(RegistryFriendlyByteBuf buf) {
            return new SelectSkillPayload(buf.readVarInt(), buf.readVarInt());
        }
        public void encode(RegistryFriendlyByteBuf buf, SelectSkillPayload value) {
            buf.writeVarInt(value.mode());
            buf.writeVarInt(value.slot());
        }
    };

    public boolean matchesHeldSlot(int selectedSlot) {
        return mode >= 0 && mode < GodWandItem.TOTAL_MODES
                && (slot == 40 || (slot >= 0 && slot < 9 && slot == selectedSlot));
    }

    public Type<? extends CustomPacketPayload> type() { return ID; }
}
