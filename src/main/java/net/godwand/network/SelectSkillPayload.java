package net.godwand.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.godwand.item.GodWandItem;

public record SelectSkillPayload(int mode, int slot) implements CustomPayload {
    public static final Id<SelectSkillPayload> ID = new Id<>(Identifier.of("godwand", "select_skill"));
    public static final PacketCodec<RegistryByteBuf, SelectSkillPayload> CODEC = new PacketCodec<>() {
        public SelectSkillPayload decode(RegistryByteBuf buf) {
            return new SelectSkillPayload(buf.readVarInt(), buf.readVarInt());
        }
        public void encode(RegistryByteBuf buf, SelectSkillPayload value) {
            buf.writeVarInt(value.mode());
            buf.writeVarInt(value.slot());
        }
    };

    public boolean matchesHeldSlot(int selectedSlot) {
        return mode >= 0 && mode < GodWandItem.TOTAL_MODES
                && (slot == 40 || (slot >= 0 && slot < 9 && slot == selectedSlot));
    }

    public Id<? extends CustomPayload> getId() { return ID; }
}
